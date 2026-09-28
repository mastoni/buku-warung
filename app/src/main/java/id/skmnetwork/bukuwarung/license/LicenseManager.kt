package id.skmnetwork.bukuwarung.license

import android.content.Context
import id.skmnetwork.bukuwarung.BuildConfig
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/**
 * Gate H.5.1 - licence lifecycle enforcement.
 *
 * H.5.0 proved that the persisted string "ACTIVE" was the only runtime authority and that
 * `lastValidatedAt` was written but never read, so a revocation or an admin rebind could never reach
 * a device. This class makes three things true:
 *
 *  1. AUTHORITY. The runtime authority is [LicenseRuntimeState], produced by the pure
 *     [LicenseStateEvaluator] from the persisted record plus an explicit [LicensePolicy]. The stored
 *     string is an input, never the decision.
 *  2. LIFECYCLE. A validation is issued on cold start and, TTL-gated, on every foreground
 *     transition. [triggerColdStartValidation] and [triggerForegroundValidation] are the only two
 *     automatic entry points, and both are idempotent.
 *  3. SAFETY OF THE TRANSIENT PATH. Only a recognised server status may change the entitlement.
 *     Everything else is transient and, by contract, deletes neither the entitlement nor the stored
 *     licence credential.
 */
open class LicenseManager(
    private val userPreferencesRepository: UserPreferencesRepository? = null,
    private val provider: LicenseProvider? = null,
    private val apiClient: LicenseApiClient = LicenseApiClient(),
    private val secureStorage: SecureLicenseStorage = SecureLicenseStorage(),
    private val context: Context? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val policy: LicensePolicy = LicensePolicy.DEFAULT,
    private val clock: () -> Long = System::currentTimeMillis,
    private val minValidationIntervalMillis: Long = DEFAULT_MIN_VALIDATION_INTERVAL_MILLIS
) {
    // Production default is CHECKING - NEVER HARDCODED ACTIVE
    private val _licenseState = MutableStateFlow(LicenseRuntimeState.checking())
    val licenseState: StateFlow<LicenseRuntimeState> = _licenseState.asStateFlow()

    /** Coarse status retained for the existing screens and the pre-H.5.1 test contract. */
    private val _licenseStatus = MutableStateFlow(LicenseStatus.CHECKING)
    val licenseStatus: StateFlow<LicenseStatus> = _licenseStatus.asStateFlow()

    private val _licenseTier = MutableStateFlow(LicenseTier.WARUNG)
    val licenseTier: StateFlow<LicenseTier> = _licenseTier.asStateFlow()

    /**
     * Gate H.5.1 section 4 - single-flight.
     *
     * Every validation path (cold start, foreground, manual) funnels through [validateOnline], so two
     * simultaneous triggers share ONE HTTP call. The deferred is created and started while the mutex
     * is held, which closes the window in which two callers could both observe "nothing in flight".
     */
    private val singleFlightMutex = Mutex()
    private var inFlight: Deferred<ValidationResult>? = null

    /**
     * Gate H.5.1 section 9 - ordering.
     *
     * Each validation attempt takes a monotonically increasing sequence number. A response may only
     * mutate persisted state and the published state if its sequence is at least the highest one
     * already applied, so a slow earlier response can never resurrect a verdict that a later
     * response has already superseded.
     */
    private val orderingMutex = Mutex()
    private val sequenceIssuer = AtomicLong(0)
    private val latestAppliedSequence = AtomicLong(0)

    /** Gate H.5.1 section 2 - at most one cold-start validation per process. */
    private val coldStartMutex = Mutex()
    private var coldStartHandled = false

    @Volatile
    private var lastValidationStartedAt = 0L

    init {
        scope.launch {
            // Gate H.5.3 (H.5.2-P1-1): this scope is a SupervisorJob with no CoroutineExceptionHandler,
            // so any escaping exception here would terminate the process. refreshLicense() is already
            // guarded internally; this is a second line of defence for the launch site itself.
            try {
                refreshLicense()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                publish(localStorageUnavailableState())
            }
        }
    }

    // ------------------------------------------------------------------ local storage safety (H.5.3 / H.5.2-P1-1)

    /**
     * Gate H.5.3 (H.5.2-P1-1): the outcome of a local licence read.
     *
     * A failed read is a first-class result, never an exception that escapes. It resolves into a
     * NON-GRANTING state, because a licence whose freshness cannot be established must not be
     * presented as usable, and it never deletes the entitlement or the credential.
     */
    private sealed interface LocalRead {
        data class Ok(val state: PersistedLicenseState) : LocalRead
        data class Failed(val cause: Throwable) : LocalRead
    }

    /**
     * Reads the persisted licence, converting a local storage failure into [LocalRead.Failed].
     *
     * `CancellationException` is rethrown, never converted, so structured concurrency keeps working
     * and a cancelled lifecycle scope still releases its resources.
     */
    private suspend fun readLocalState(): LocalRead {
        return try {
            LocalRead.Ok(readPersisted())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LocalRead.Failed(e)
        }
    }

    /**
     * The state published when local storage cannot be read.
     *
     * `fallbackPhase` is deliberately [LicensePhase.UNLICENSED] so [LicenseRuntimeState.grantsAccess]
     * is false: this is fail-CLOSED, never a silent permanent ACTIVE. The last known
     * `lastValidatedAt` is carried forward so the foreground TTL gate keeps behaving, and no storage
     * mutation is performed anywhere on this path.
     */
    private fun localStorageUnavailableState(): LicenseRuntimeState =
        LicenseRuntimeState(
            phase = LicensePhase.TRANSIENT_ERROR,
            transientReason = TransientReason.LOCAL_STORAGE_UNAVAILABLE,
            fallbackPhase = LicensePhase.UNLICENSED,
            lastValidatedAt = _licenseState.value.lastValidatedAt,
            activatedAt = _licenseState.value.activatedAt,
            freshTtlMillis = policy.freshTtlMillis,
            graceWindowMillis = policy.graceWindowMillis
        )

    // ------------------------------------------------------------------ state evaluation

    /**
     * Recomputes the runtime state from persisted data only. Performs NO network I/O.
     *
     * Deliberately does NOT persist a lapsed grace window. A passive local evaluation is a view, not
     * a verdict: persisting it would gate every installation that has not been validated since its
     * activation - which is precisely the H.5.0 population, since the old build wrote
     * `lastValidatedAt` at activation time - before the cold-start validation had a chance to ask
     * the server whether those licences are in fact still valid. Leaving the record intact also
     * means a lapsed-but-still-valid licence keeps being validated on every cold start and
     * foreground transition, which is how a permanently offline device recovers the moment it
     * reconnects.
     */
    suspend fun refreshLicense() {
        _licenseTier.value = provider?.getLicenseTier() ?: LicenseTier.WARUNG
        evaluateAndPublish()
    }

    private suspend fun evaluateAndPublish() {
        // Legacy injection seam: a caller that supplies an explicit provider keeps full control.
        if (provider != null) {
            publish(fromLegacyStatus(provider.checkLicense()))
            return
        }

        if (ownerTestActive()) {
            publish(
                LicenseRuntimeState(
                    phase = LicensePhase.FRESH_ACTIVE,
                    lastValidatedAt = clock(),
                    freshTtlMillis = policy.freshTtlMillis,
                    graceWindowMillis = policy.graceWindowMillis
                )
            )
            return
        }

        // Gate H.5.3 (H.5.2-P1-1): a local read failure publishes a non-granting transient state
        // instead of throwing out of the init launch.
        when (val read = readLocalState()) {
            is LocalRead.Failed -> publish(localStorageUnavailableState())
            is LocalRead.Ok -> publish(
                LicenseStateEvaluator.evaluateFreshness(read.state, clock(), policy)
            )
        }
    }

    private suspend fun evaluateWithTransient(transientReason: TransientReason?): LicenseRuntimeState {
        return when (val read = readLocalState()) {
            is LocalRead.Failed -> localStorageUnavailableState()
            is LocalRead.Ok -> {
                val base = LicenseStateEvaluator.evaluateFreshness(read.state, clock(), policy)
                if (transientReason != null && base.grantsAccess) {
                    // A transient failure must never present a stale licence as a fresh one, and must
                    // never hide the fact that the verdict is unknown. The freshness underneath is
                    // carried along.
                    base.copy(
                        phase = LicensePhase.TRANSIENT_ERROR,
                        transientReason = transientReason,
                        fallbackPhase = base.phase
                    )
                } else {
                    base.copy(transientReason = transientReason)
                }
            }
        }
    }

    private suspend fun readPersisted(): PersistedLicenseState {
        val repo = userPreferencesRepository ?: return PersistedLicenseState()
        return PersistedLicenseState(
            entitlement = repo.getLicenseEntitlement(),
            blocked = repo.isLicenseBlocked(),
            blockReason = parseBlockReason(repo.getLicenseBlockReason())
        )
    }

    private fun publish(state: LicenseRuntimeState) {
        _licenseState.value = state
        _licenseStatus.value = state.status
    }

    // Gate H.5.3 (H.5.2-P1-1): a local storage failure must never be read as an owner-test
    // activation, and must never throw out of the evaluation path.
    private suspend fun ownerTestActive(): Boolean {
        if (!BuildConfig.ENABLE_OWNER_TEST) return false
        return try {
            userPreferencesRepository?.isOwnerTestActivated() == true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    private fun fromLegacyStatus(status: LicenseStatus): LicenseRuntimeState = when (status) {
        LicenseStatus.ACTIVE -> LicenseRuntimeState(phase = LicensePhase.FRESH_ACTIVE, lastValidatedAt = clock())
        LicenseStatus.STALE_ACTIVE -> LicenseRuntimeState(phase = LicensePhase.STALE_ACTIVE, lastValidatedAt = clock())
        LicenseStatus.TRANSIENT_ERROR, LicenseStatus.ERROR -> LicenseRuntimeState(
            phase = LicensePhase.TRANSIENT_ERROR,
            transientReason = TransientReason.NETWORK_UNAVAILABLE,
            fallbackPhase = LicensePhase.FRESH_ACTIVE,
            lastValidatedAt = clock()
        )
        LicenseStatus.BLOCKED -> LicenseRuntimeState(
            phase = LicensePhase.BLOCKED,
            blockReason = LicenseBlockReason.INVALID
        )
        LicenseStatus.CHECKING, LicenseStatus.UNKNOWN ->
            LicenseRuntimeState(phase = LicensePhase.UNLICENSED, blockReason = LicenseBlockReason.NEVER_ACTIVATED)
        LicenseStatus.UNLICENSED ->
            LicenseRuntimeState(phase = LicensePhase.UNLICENSED, blockReason = LicenseBlockReason.NEVER_ACTIVATED)
    }

    private fun parseBlockReason(raw: String): LicenseBlockReason =
        runCatching { LicenseBlockReason.valueOf(raw) }.getOrDefault(LicenseBlockReason.NONE)

    // ------------------------------------------------------------------ lifecycle triggers

    /**
     * Gate H.5.1 section 2 - cold start.
     *
     * Issues EXACTLY ONE validation per process, and only when there is something to validate: a
     * non-blocked entitlement AND a readable credential. A fresh install, a cleared app, or a
     * permanently blocked licence performs no HTTP call at all. Repeated calls - for example from a
     * recomposition or a configuration change that re-runs the effect - are no-ops.
     */
    suspend fun triggerColdStartValidation(): ValidationResult? {
        val firstCall = coldStartMutex.withLock {
            if (coldStartHandled) {
                false
            } else {
                coldStartHandled = true
                true
            }
        }
        if (!firstCall) return null

        if (!hasValidatableEntitlement()) {
            evaluateAndPublish()
            return null
        }
        return validateOnline(ValidationTrigger.COLD_START)
    }

    /**
     * Gate H.5.1 section 3 - foreground.
     *
     * At most one validation per foreground transition, and none at all while the last SUCCESSFUL
     * validation is still inside the TTL. The gate is measured against the recorded
     * `lastValidatedAt`, not against the current phase: a phase check would keep returning "fresh"
     * forever for a process that validated once at launch and then stayed alive, which is exactly
     * the window a revoked licence would slip through.
     *
     * A transient failure never sets `lastValidatedAt`, so a licence that could not be verified is
     * retried on the next transition, subject to the minimum interval below.
     */
    suspend fun triggerForegroundValidation(): ValidationResult? {
        if (userPreferencesRepository == null) return null

        val nowMillis = clock()
        val lastSuccessfulValidation = _licenseState.value.lastValidatedAt
        if (lastSuccessfulValidation > 0L &&
            nowMillis - lastSuccessfulValidation < policy.freshTtlMillis
        ) {
            return null
        }

        if (!hasValidatableEntitlement()) {
            evaluateAndPublish()
            return null
        }

        val startedAt = lastValidationStartedAt
        if (startedAt > 0L && nowMillis - startedAt < minValidationIntervalMillis) return null

        return validateOnline(ValidationTrigger.FOREGROUND)
    }

    /**
     * A licence is validatable when it is entitled, not blocked, and its credential can be read.
     * Checking this before entering the single-flight keeps validation from being issued for an
     * installation that has nothing to validate, and prevents any trigger/validation feedback loop.
     *
     * Gate H.5.3 (H.5.2-P1-1): a local storage failure answers "nothing to validate" instead of
     * throwing. The caller then falls through to [evaluateAndPublish], which publishes the
     * non-granting local-storage state, and the lifecycle coroutine stays usable for the next trigger.
     */
    private suspend fun hasValidatableEntitlement(): Boolean {
        val repo = userPreferencesRepository ?: return false
        return try {
            if (repo.isLicenseBlocked()) return false
            if (!repo.getLicenseEntitlement().isEntitled) return false
            getStoredLicenseCode().isNotBlank()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    // ------------------------------------------------------------------ validation

    /**
     * Performs a server validation, collapsing concurrent callers onto a single HTTP call.
     *
     * The single-flight deferred runs on the manager scope, not on the caller's: a caller that is
     * cancelled (a composable leaving composition, a lifecycle teardown) stops waiting but must not
     * abort a validation that other triggers are also waiting on. Cancellation of the caller
     * propagates as [CancellationException] and nothing else - it is never converted into a
     * fabricated [ValidationResult] and never changes the entitlement.
     */
    suspend fun validateOnline(trigger: ValidationTrigger = ValidationTrigger.MANUAL): ValidationResult {
        val deferred = singleFlightMutex.withLock {
            inFlight?.takeIf { it.isActive }
                ?: scope.async(start = CoroutineStart.LAZY) { performAndCommit(trigger) }
                    .also { created ->
                        inFlight = created
                        created.start()
                    }
        }
        return try {
            deferred.await()
        } finally {
            singleFlightMutex.withLock {
                if (inFlight === deferred) inFlight = null
            }
        }
    }

    private suspend fun performAndCommit(trigger: ValidationTrigger): ValidationResult {
        val sequence = sequenceIssuer.incrementAndGet()
        val result = performValidation(trigger)
        commitValidationOutcome(sequence, result)
        return result
    }

    private suspend fun performValidation(trigger: ValidationTrigger): ValidationResult {
        val repo = userPreferencesRepository
            ?: return ValidationResult.Transient.HttpRejection(0, TransientReason.CLIENT_UNAVAILABLE)

        // Gate H.5.3 (H.5.2-P1-1): a local read failure becomes a transient result, so a corrupt
        // DataStore can neither crash the process nor be mistaken for a licence verdict.
        val persisted = when (val read = readLocalState()) {
            is LocalRead.Failed ->
                return ValidationResult.Transient.HttpRejection(
                    0,
                    TransientReason.LOCAL_STORAGE_UNAVAILABLE
                )
            is LocalRead.Ok -> read.state
        }

        if (persisted.blocked) {
            return when (persisted.blockReason) {
                LicenseBlockReason.DEVICE_MISMATCH -> ValidationResult.DeviceMismatch()
                LicenseBlockReason.REVOKED -> ValidationResult.Revoked()
                LicenseBlockReason.EMAIL_MISMATCH -> ValidationResult.EmailMismatch()
                else -> ValidationResult.Invalid()
            }
        }

        val entitlement = persisted.entitlement
        if (!entitlement.isEntitled) {
            return ValidationResult.Invalid("Aplikasi belum diaktivasi")
        }

        val deviceId = try {
            repo.getOrCreateDeviceId()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return ValidationResult.Transient.HttpRejection(
                0,
                TransientReason.LOCAL_STORAGE_UNAVAILABLE
            )
        }

        val licenseCode = getStoredLicenseCode()
        if (licenseCode.isBlank()) {
            // Local availability problem, not a server verdict. The entitlement and the block reason
            // must both survive so the merchant is not silently de-licensed by a KeyStore fault.
            return ValidationResult.CredentialUnavailable()
        }

        lastValidationStartedAt = clock()
        return apiClient.validateLicense(
            licenseCode = licenseCode,
            ownerEmail = entitlement.ownerEmail,
            deviceBinding = deviceId
        )
    }

    /**
     * Applies a validation verdict, guarded by the sequence ordering of Gate H.5.1 section 9 and by
     * local storage safety from Gate H.5.3.
     *
     * Returns false when the verdict was discarded because a newer one had already been applied.
     * A [ValidationResult.Transient] and a [ValidationResult.CredentialUnavailable] never delete the
     * entitlement and never delete the credential.
     *
     * The ordering watermark is advanced ONLY by a verdict that actually mutates persisted state. A
     * transient outcome writes nothing, so letting it advance the watermark would let a failed
     * attempt suppress a legitimate activation (H.5.3 / H.5.2-P2-2), and letting an older real
     * verdict through after a newer transient is harmless because the transient changed nothing.
     */
    suspend fun commitValidationOutcome(sequence: Long, result: ValidationResult): Boolean =
        orderingMutex.withLock {
            if (sequence < latestAppliedSequence.get()) {
                // A late response from an earlier attempt. It must not overwrite a newer verdict.
                return@withLock false
            }

            val repo = userPreferencesRepository
            var transient: TransientReason? = null
            var mutatesState = false
            var storageWriteFailed = false
            if (repo != null) {
                try {
                    when (result) {
                        is ValidationResult.Valid -> {
                            repo.markLicenseValidated(clock()); mutatesState = true
                        }

                        // Definitive and blocking, but the credential is deliberately RETAINED so the
                        // merchant can re-activate or run the Task 7A recovery flow from the gate.
                        is ValidationResult.DeviceMismatch -> {
                            repo.blockLicenseEntitlement(
                                blockReason = UserPreferencesRepository.BLOCK_REASON_DEVICE_MISMATCH
                            )
                            mutatesState = true
                        }

                        is ValidationResult.Revoked -> {
                            repo.clearLicenseEntitlement()
                            clearStoredLicenseCode()
                            mutatesState = true
                        }

                        is ValidationResult.EmailMismatch -> {
                            repo.clearLicenseEntitlement()
                            clearStoredLicenseCode()
                            mutatesState = true
                        }

                        is ValidationResult.Invalid -> {
                            repo.clearLicenseEntitlement()
                            clearStoredLicenseCode()
                            mutatesState = true
                        }

                        is ValidationResult.CredentialUnavailable -> Unit
                        is ValidationResult.Transient -> transient = result.reason
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Gate H.5.3 (H.5.2-P1-1): a failed write must not escape, must not advance the
                    // ordering watermark, and must not be reported as a licence decision.
                    storageWriteFailed = true
                }
            }

            if (mutatesState) latestAppliedSequence.set(sequence)

            publish(
                when {
                    storageWriteFailed -> localStorageUnavailableState()
                    else -> evaluateWithTransient(transient)
                }
            )
            true
        }

    /** Gate H.5.1 section 9 test seam: the next monotonic validation sequence number. */
    fun nextValidationSequence(): Long = sequenceIssuer.incrementAndGet()

    // ------------------------------------------------------------------ activation

    /**
     * Activates a license using owner email and license code.
     * Binds to this device's persistent UUID.
     *
     * Gate H.5.1 section 7: activation is NOT a validation. `lastValidatedAt` is written as 0 and the
     * runtime state is therefore never FRESH until a real POST /v1/license/validate has succeeded.
     *
     * Gate H.5.3 (H.5.2-P2-2): activation participates in the same ordering invariant as validation.
     * It takes a sequence number BEFORE the request and applies its write under [orderingMutex], so:
     *  - a validation response that was issued earlier can no longer overwrite the activation;
     *  - a validation issued later can still apply a definitive rejection on top of it;
     *  - it never takes [singleFlightMutex] and never waits for an in-flight validation, so there is
     *    no deadlock and no unnecessary serialisation.
     */
    suspend fun activateLicense(
        licenseCode: String,
        ownerEmail: String
    ): ActivationResult {
        val repo = userPreferencesRepository
            ?: return ActivationResult.ServerError("Preferences repository tidak tersedia")

        val sequence = sequenceIssuer.incrementAndGet()

        val deviceId = try {
            repo.getOrCreateDeviceId()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return ActivationResult.Transient(TransientReason.LOCAL_STORAGE_UNAVAILABLE)
        }

        val result = apiClient.activateLicense(
            licenseCode = licenseCode,
            ownerEmail = ownerEmail,
            deviceBinding = deviceId
        )

        if (result is ActivationResult.Active) {
            // Persist the encrypted credential if a Context is available. A storage failure is not
            // fatal: the server-side binding already succeeded, and the resulting
            // CredentialUnavailable is transient, so the merchant can simply re-activate.
            context?.let { ctx ->
                secureStorage.saveEncryptedLicenseCode(ctx, licenseCode)
            }

            val normalisedEmail = ownerEmail.trim().lowercase()
            val applied = try {
                orderingMutex.withLock {
                    if (sequence < latestAppliedSequence.get()) {
                        false
                    } else {
                        repo.saveLicenseEntitlement(
                            status = "ACTIVE",
                            ownerEmail = normalisedEmail,
                            activatedAt = clock(),
                            lastValidatedAt = NEVER_VALIDATED
                        )
                        latestAppliedSequence.set(sequence)
                        true
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                evaluateAndPublish()
                return ActivationResult.Transient(TransientReason.LOCAL_STORAGE_UNAVAILABLE)
            }

            evaluateAndPublish()
            if (!applied) {
                return ActivationResult.Transient(TransientReason.ACTIVATION_SUPERSEDED)
            }
        }

        return result
    }

    /** Retrieves the stored license code. Overridable for unit testing without Android Context. */
    protected open fun getStoredLicenseCode(): String {
        return context?.let { secureStorage.getEncryptedLicenseCode(it) } ?: ""
    }

    /** Clears the stored license code. Overridable for unit testing without Android Context. */
    protected open fun clearStoredLicenseCode() {
        context?.let { secureStorage.clearLicenseCode(it) }
    }

    // ------------------------------------------------------------------ recovery (Task 7A, unchanged)

    /**
     * Requests a device recovery for an existing licence.
     *
     * The device binding is deliberately NOT a parameter: it is read from the repository so that
     * this method can only ever submit the per-install UUID created by
     * UserPreferencesRepository.getOrCreateDeviceId(). No new UUID is generated, and no hardware
     * identifier is involved, so a customer can neither supply nor influence the value.
     *
     * This never clears credentials, never calls validateOnline(), never calls the admin rebind
     * endpoint, and never mutates the local licence status. A successful result means the server
     * recorded a PENDING recovery request and nothing more.
     */
    suspend fun requestDeviceRecovery(
        licenseCode: String,
        ownerEmail: String
    ): RecoveryResult {
        val repository = userPreferencesRepository
            ?: return RecoveryResult.UnexpectedError()

        val cleanCode = licenseCode.trim()
        val cleanEmail = ownerEmail.trim().lowercase()
        if (cleanCode.isEmpty() || cleanEmail.isEmpty()) {
            return RecoveryResult.InvalidRequest()
        }

        val deviceId = repository.getOrCreateDeviceId()

        return apiClient.recoverLicense(
            licenseCode = cleanCode,
            ownerEmail = cleanEmail,
            newDeviceBinding = deviceId,
            reason = null
        )
    }

    /**
     * Explicitly records that the licence aged past the grace window.
     *
     * NOT invoked by the passive evaluator on purpose - see [refreshLicense]. This exists for the
     * case where a definite decision to re-activate is taken, and keeps the stored credential so
     * the merchant can re-activate with the same code.
     */
    suspend fun expireLicenseEntitlement() {
        try {
            userPreferencesRepository?.expireLicenseEntitlement()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Gate H.5.3 (H.5.2-P1-1): a failed write must not escape.
        }
        evaluateAndPublish()
    }

    // ------------------------------------------------------------------ misc

    /**
     * Gate H.5.3 (H.5.2-P1-1): a local read failure yields the default record instead of throwing, so
     * a caller's LaunchedEffect cannot be torn down by a corrupt DataStore.
     */
    suspend fun getEntitlementInfo(): LicenseEntitlementData {
        return try {
            userPreferencesRepository?.getLicenseEntitlement() ?: LicenseEntitlementData()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            LicenseEntitlementData()
        }
    }

    suspend fun activateOwnerTest(): Boolean {
        if (!BuildConfig.ENABLE_OWNER_TEST) {
            return false
        }
        try {
            userPreferencesRepository?.setOwnerTestActivated(true)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return false
        }
        evaluateAndPublish()
        return _licenseState.value.grantsAccess
    }

    fun isProFeatureEnabled(featureName: String): Boolean {
        return _licenseState.value.grantsAccess && _licenseTier.value == LicenseTier.WARUNG_PRO
    }

    /**
     * Non-blocking, offline-safe reporting of marketing/conversion funnel events.
     *
     * Every app-originated event now carries [installationId], a random app-scoped identifier
     * persisted in DataStore. This is what allows the server to count unique installations
     * instead of raw event rows. The previous behaviour minted a fresh lead token per request,
     * which made event count and unique user count indistinguishable.
     */
    fun trackMarketingEvent(
        eventType: String,
        utmSource: String? = "app_license_gate",
        utmMedium: String? = "in_app",
        utmCampaign: String? = "buku_warung_v020",
        utmContent: String? = null,
        leadToken: String? = null
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                val installationId = userPreferencesRepository?.getOrCreateInstallationId()
                apiClient.trackFunnelEvent(
                    eventType = eventType,
                    utmSource = utmSource,
                    utmMedium = utmMedium,
                    utmCampaign = utmCampaign,
                    utmContent = utmContent,
                    leadToken = leadToken,
                    installationId = installationId
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Non-blocking offline-first: fail silently without error
            }
        }
    }

    /**
     * Claims the one-and-only LICENSE_GATE_VIEWED entitlement for this installation.
     * Returns true only for the caller that won the claim; every later call returns false.
     *
     * Gate H.5.3 (H.5.2-P1-1): guarded because this is called from a LaunchedEffect on the gate
     * screen, which is exactly where a merchant with unreadable local state ends up. A telemetry
     * claim must never be able to tear down that composition.
     */
    suspend fun claimLicenseGateView(): Boolean = try {
        userPreferencesRepository?.claimLicenseGateViewRecorded() ?: false
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    /**
     * Claims the one-and-only APP_FIRST_OPEN entitlement for this installation.
     * Returns true only for the caller that won the claim.
     */
    suspend fun claimAppFirstOpen(): Boolean = try {
        userPreferencesRepository?.claimAppFirstOpenRecorded() ?: false
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    companion object {
        /**
         * Marker written to `lastValidatedAt` at activation. Gate H.5.1 section 7: activation is not
         * a validation, so it must not look like one.
         */
        const val NEVER_VALIDATED: Long = 0L

        /**
         * Floor between two automatically issued validations, applied to the foreground trigger only.
         *
         * A merchant bouncing in and out of the app dozens of times a day must not be able to turn
         * that into dozens of requests, and a licence that is persistently failing must not be
         * retried in a tight loop. One minute is long enough to absorb normal app switching and short
         * enough that a genuine transition is validated promptly. Explicit user-driven validation from
         * Settings is deliberately NOT subject to this floor.
         */
        const val DEFAULT_MIN_VALIDATION_INTERVAL_MILLIS: Long = 60_000L
    }
}
