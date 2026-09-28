package id.skmnetwork.bukuwarung.license

import androidx.datastore.preferences.core.emptyPreferences
import id.skmnetwork.bukuwarung.BuildConfig
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.coroutines.cancellation.CancellationException

/**
 * Gate H.5.1 - licence lifecycle enforcement regression suite.
 *
 * These tests cover the 30 scenarios enumerated in the gate. They are all JVM unit tests using an
 * injected [LicenseHttpTransport] and an in-memory DataStore. NO test here contacts a real server,
 * a real device, or a real AndroidKeyStore, and none of them may be presented as such.
 */
class LicenseLifecycleH51Test {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val licenseCode = "BW-H51-TEST-0001"
    private val ownerEmail = "owner@h51.test"
    private val baseUrl = "https://license.skmnetwork.com"

    private var now: Long = 1_700_000_000_000L
    private val policy = LicensePolicy(freshTtlMillis = 1_000L, graceWindowMillis = 5_000L)

    private lateinit var transport: ScriptedTransport
    private lateinit var apiClient: LicenseApiClient
    private lateinit var repo: UserPreferencesRepository

    // ------------------------------------------------------------------ fixtures

    /** Fully controllable transport: status, body, thrown failure and an optional release gate. */
    private class ScriptedTransport : LicenseHttpTransport {
        val calls = AtomicInteger(0)
        val urls = mutableListOf<String>()
        val payloads = mutableListOf<String>()

        @Volatile var status: Int = 200
        @Volatile var body: String = """{"success":true,"status":"VALID"}"""
        @Volatile var throwable: Throwable? = null
        @Volatile var gate: CompletableDeferred<Unit>? = null

        override suspend fun post(
            url: String,
            jsonPayload: String,
            connectTimeoutMs: Int,
            readTimeoutMs: Int
        ): LicenseHttpResponse {
            calls.incrementAndGet()
            synchronized(urls) {
                urls.add(url)
                payloads.add(jsonPayload)
            }
            throwable?.let { throw it }
            gate?.await()
            return LicenseHttpResponse(status, body)
        }
    }

    private inner class TestManager(
        private val storedCode: String = licenseCode,
        val cleared: AtomicReference<Boolean> = AtomicReference(false),
        val minInterval: Long = 0L
    ) : LicenseManager(
        userPreferencesRepository = repo,
        apiClient = apiClient,
        context = null,
        scope = CoroutineScope(Dispatchers.IO),
        policy = policy,
        clock = { now },
        minValidationIntervalMillis = minInterval
    ) {
        override fun getStoredLicenseCode(): String = storedCode

        /** Exposes the protected credential accessor for assertions. */
        fun readStoredCode(): String = getStoredLicenseCode()

        override fun clearStoredLicenseCode() {
            cleared.set(true)
        }
    }

    private fun validBody(status: String) = """{"success":${status == "VALID"},"status":"$status"}"""

    /**
     * Seeds an active commercial entitlement.
     *
     * [activatedAt] defaults to `now` so a seeded licence is never instantly past the grace window:
     * the manager's background refresh persists a lapsed grace window, and a fixture that started
     * out already expired would be silently converted to UNLICENSED underneath the test.
     */
    private suspend fun seedActive(lastValidatedAt: Long = now, activatedAt: Long = now) {
        repo.saveLicenseEntitlement(
            status = "ACTIVE",
            ownerEmail = ownerEmail,
            activatedAt = activatedAt,
            lastValidatedAt = lastValidatedAt
        )
    }

    @Before
    fun setUp() {
        transport = ScriptedTransport()
        apiClient = LicenseApiClient(baseUrl = baseUrl, transport = transport)
        repo = UserPreferencesRepository(
            context = null,
            dataStore = TestPreferencesDataStore(emptyPreferences())
        )
    }

    // =================================================================
    // 1. Cold start validation
    // =================================================================

    @Test
    fun h51_01_coldStart_issuesExactlyOneValidation() = runBlocking {
        seedActive(lastValidatedAt = 0L)
        val manager = TestManager()

        manager.triggerColdStartValidation()

        assertEquals(1, transport.calls.get())
        assertTrue(transport.urls.single().endsWith("/v1/license/validate"))
    }

    @Test
    fun h51_01b_coldStart_isIdempotentForRepeatedTriggers() = runBlocking {
        seedActive(lastValidatedAt = 0L)
        val manager = TestManager()

        manager.triggerColdStartValidation()
        manager.triggerColdStartValidation()
        manager.triggerColdStartValidation()

        assertEquals("Repeated cold-start triggers must not re-validate", 1, transport.calls.get())
    }

    @Test
    fun h51_01c_coldStart_neverValidatesWhenThereIsNoEntitlement() = runBlocking {
        // Fresh install: nothing stored, nothing to validate.
        val manager = TestManager()

        assertNull(manager.triggerColdStartValidation())

        assertEquals(0, transport.calls.get())
    }

    @Test
    fun h51_01d_coldStart_neverValidatesWhenTheCredentialIsMissing() = runBlocking {
        seedActive(lastValidatedAt = 0L)
        val manager = TestManager(storedCode = "")

        assertNull(manager.triggerColdStartValidation())

        assertEquals(0, transport.calls.get())
    }

    @Test
    fun h51_01e_coldStart_neverValidatesWhenTheLicenceIsAlreadyBlocked() = runBlocking {
        seedActive(lastValidatedAt = now)
        repo.blockLicenseEntitlement(UserPreferencesRepository.BLOCK_REASON_DEVICE_MISMATCH)
        val manager = TestManager()

        assertNull(manager.triggerColdStartValidation())

        assertEquals(0, transport.calls.get())
    }

    // =================================================================
    // 2. / 3. Foreground validation
    // =================================================================

    @Test
    fun h51_02_foreground_isSilentWhileTheLastValidationIsInsideTheTtl() = runBlocking {
        seedActive(lastValidatedAt = 0L)
        val manager = TestManager()
        manager.triggerColdStartValidation()
        assertEquals(LicensePhase.FRESH_ACTIVE, manager.licenseState.value.phase)

        now += policy.freshTtlMillis - 1L
        assertNull("A fresh licence must not re-validate on foreground", manager.triggerForegroundValidation())

        assertEquals(1, transport.calls.get())
    }

    @Test
    fun h51_03_foreground_validatesOnceAfterTheTtlExpires() = runBlocking {
        seedActive(lastValidatedAt = 0L)
        val manager = TestManager()
        manager.triggerColdStartValidation()
        assertEquals(1, transport.calls.get())

        now += policy.freshTtlMillis + 1L
        val result = manager.triggerForegroundValidation()

        assertNotNull(result)
        assertEquals(2, transport.calls.get())
        assertEquals(LicensePhase.FRESH_ACTIVE, manager.licenseState.value.phase)
    }

    @Test
    fun h51_03b_foreground_honoursTheMinimumIntervalBetweenRequests() = runBlocking {
        val floor = 60_000L
        seedActive(lastValidatedAt = 0L)
        val manager = TestManager(minInterval = floor)
        manager.triggerColdStartValidation()
        assertEquals(1, transport.calls.get())

        // A transient failure leaves the state non-fresh, so the next foreground would want to
        // retry. The minimum interval must still hold, so the first attempt is released far enough
        // into the future to pass it, and the second one must be suppressed.
        transport.throwable = IOException("offline")
        now += floor + policy.freshTtlMillis + 1L
        assertNotNull(manager.triggerForegroundValidation())
        assertEquals(2, transport.calls.get())

        now += 1_000L
        assertNull("Too soon after the previous attempt", manager.triggerForegroundValidation())
        assertEquals(2, transport.calls.get())
    }

    @Test
    fun h51_03c_foreground_neverValidatesWithoutAnEntitlement() = runBlocking {
        val manager = TestManager()

        assertNull(manager.triggerForegroundValidation())

        assertEquals(0, transport.calls.get())
    }

    // =================================================================
    // 4. / 26. Single flight
    // =================================================================

    @Test
    fun h51_26_concurrentValidationTriggersProduceExactlyOneHttpCall() = runBlocking {
        seedActive(lastValidatedAt = 0L)
        val manager = TestManager()
        val gate = CompletableDeferred<Unit>()
        transport.gate = gate

        val a = async(Dispatchers.Default) { manager.validateOnline() }
        val b = async(Dispatchers.Default) { manager.validateOnline() }
        val c = async(Dispatchers.Default) { manager.triggerColdStartValidation() }

        withTimeout(10_000) { while (transport.calls.get() == 0) delay(5) }
        // Let every caller reach the single-flight guard before releasing the response.
        delay(200)
        gate.complete(Unit)

        val results = listOf(a.await(), b.await(), c.await())
        assertEquals("Three simultaneous triggers must collapse to one HTTP call", 1, transport.calls.get())
        assertTrue(results.all { it is ValidationResult.Valid })
    }

    // =================================================================
    // 5 - 9. Definitive server verdicts
    // =================================================================

    @Test
    fun h51_05_valid_refreshesTheEntitlement() = runBlocking {
        seedActive(lastValidatedAt = 0L)
        val manager = TestManager()

        val result = manager.validateOnline()

        assertTrue(result is ValidationResult.Valid)
        assertTrue(repo.getLicenseEntitlement().isEntitled)
        assertEquals(LicensePhase.FRESH_ACTIVE, manager.licenseState.value.phase)
        assertFalse(manager.licenseState.value.neverValidated)
    }

    @Test
    fun h51_06_revoked_isDefinitiveAndBlocksAccess() = runBlocking {
        seedActive(lastValidatedAt = now)
        transport.status = 200
        transport.body = validBody("REVOKED")
        val manager = TestManager()

        val result = manager.validateOnline()

        assertTrue(result is ValidationResult.Revoked)
        assertFalse(repo.getLicenseEntitlement().isEntitled)
        assertTrue("A revoked licence must delete the stored code", manager.cleared.get())
        assertFalse(manager.licenseState.value.grantsAccess)
        assertEquals(LicensePhase.UNLICENSED, manager.licenseState.value.phase)
    }

    @Test
    fun h51_07_deviceMismatch_blocksAccessButRetainsTheCredential() = runBlocking {
        seedActive(lastValidatedAt = now)
        transport.status = 200
        transport.body = validBody("DEVICE_MISMATCH")
        val manager = TestManager()

        val result = manager.validateOnline()

        assertTrue(result is ValidationResult.DeviceMismatch)
        assertFalse("Access must be blocked", manager.licenseState.value.grantsAccess)
        assertEquals(LicensePhase.BLOCKED, manager.licenseState.value.phase)
        assertEquals(LicenseBlockReason.DEVICE_MISMATCH, manager.licenseState.value.blockReason)
        assertFalse("H.5.1 section 6: the credential must survive", manager.cleared.get())
        assertEquals("The owner email must survive so recovery stays reachable", ownerEmail,
            repo.getLicenseEntitlement().ownerEmail)
    }

    @Test
    fun h51_08_emailMismatch_isDefinitiveAndBlocksAccess() = runBlocking {
        seedActive(lastValidatedAt = now)
        transport.body = validBody("EMAIL_MISMATCH")
        val manager = TestManager()

        val result = manager.validateOnline()

        assertTrue(result is ValidationResult.EmailMismatch)
        assertFalse(manager.licenseState.value.grantsAccess)
        assertTrue(manager.cleared.get())
    }

    @Test
    fun h51_09_invalid_isDefinitiveAndBlocksAccess() = runBlocking {
        seedActive(lastValidatedAt = now)
        transport.body = validBody("INVALID")
        val manager = TestManager()

        val result = manager.validateOnline()

        assertTrue(result is ValidationResult.Invalid)
        assertFalse(manager.licenseState.value.grantsAccess)
        assertTrue(manager.cleared.get())
    }

    // =================================================================
    // 10 - 19. Transient failures must never de-license
    // =================================================================

    private suspend fun assertTransientPreservesEverything(
        result: ValidationResult,
        expected: TransientReason
    ) {
        assertTrue("Expected a transient outcome, got $result", result is ValidationResult.Transient)
        assertEquals(expected, (result as ValidationResult.Transient).reason)
        val entitlement = repo.getLicenseEntitlement()
        assertTrue("Transient failure must NOT delete the entitlement", entitlement.isEntitled)
        assertEquals(ownerEmail, entitlement.ownerEmail)
    }

    @Test
    fun h51_10_http400WithoutLicenceStatusIsTransient() = runBlocking {
        seedActive(lastValidatedAt = now)
        transport.status = 400
        transport.body = """{"statusCode":400,"error":"Bad Request","message":"body must NOT have fewer than 3 characters"}"""
        val manager = TestManager()

        val result = manager.validateOnline()

        assertTransientPreservesEverything(result, TransientReason.HTTP_BAD_REQUEST)
        assertFalse(manager.cleared.get())
    }

    @Test
    fun h51_11_http401IsTransient() = runBlocking {
        seedActive(lastValidatedAt = now)
        transport.status = 401
        transport.body = """{"statusCode":401,"error":"Unauthorized"}"""
        val manager = TestManager()

        val result = manager.validateOnline()

        assertTransientPreservesEverything(result, TransientReason.HTTP_UNAUTHORIZED)
        assertFalse(manager.cleared.get())
    }

    @Test
    fun h51_12_http403IsTransient() = runBlocking {
        seedActive(lastValidatedAt = now)
        transport.status = 403
        transport.body = """{"statusCode":403,"error":"Forbidden"}"""
        val manager = TestManager()

        val result = manager.validateOnline()

        assertTransientPreservesEverything(result, TransientReason.HTTP_FORBIDDEN)
        assertFalse(manager.cleared.get())
    }

    @Test
    fun h51_13_http404IsTransient() = runBlocking {
        seedActive(lastValidatedAt = now)
        transport.status = 404
        transport.body = """{"statusCode":404,"error":"Not Found"}"""
        val manager = TestManager()

        val result = manager.validateOnline()

        assertTransientPreservesEverything(result, TransientReason.HTTP_NOT_FOUND)
        assertFalse(manager.cleared.get())
    }

    @Test
    fun h51_14_http429IsTransient() = runBlocking {
        seedActive(lastValidatedAt = now)
        transport.status = 429
        transport.body = """{"statusCode":429,"error":"Too Many Requests"}"""
        val manager = TestManager()

        val result = manager.validateOnline()

        assertTransientPreservesEverything(result, TransientReason.HTTP_TOO_MANY_REQUESTS)
        assertFalse(manager.cleared.get())
    }

    @Test
    fun h51_15_malformedSuccessBodyIsTransient() = runBlocking {
        seedActive(lastValidatedAt = now)
        transport.status = 200
        transport.body = "<html><body>captive portal login</body></html>"
        val manager = TestManager()

        val result = manager.validateOnline()

        assertTransientPreservesEverything(result, TransientReason.MALFORMED_RESPONSE)
        assertFalse(manager.cleared.get())
    }

    @Test
    fun h51_15b_emptySuccessBodyIsTransient() = runBlocking {
        seedActive(lastValidatedAt = now)
        transport.status = 200
        transport.body = ""
        val manager = TestManager()

        val result = manager.validateOnline()

        assertTransientPreservesEverything(result, TransientReason.MALFORMED_RESPONSE)
    }

    @Test
    fun h51_15c_unrecognisedStatusIsTransient() = runBlocking {
        seedActive(lastValidatedAt = now)
        transport.status = 200
        transport.body = """{"success":true,"status":"SOMETHING_NEW"}"""
        val manager = TestManager()

        val result = manager.validateOnline()

        assertTransientPreservesEverything(result, TransientReason.HTTP_UNEXPECTED)
    }

    @Test
    fun h51_16_http5xxIsTransient() = runBlocking {
        seedActive(lastValidatedAt = now)
        transport.status = 503
        transport.body = """{"error":"Service Unavailable"}"""
        val manager = TestManager()

        val result = manager.validateOnline()

        assertTransientPreservesEverything(result, TransientReason.HTTP_SERVER_ERROR)
        assertFalse(manager.cleared.get())
    }

    @Test
    fun h51_17_ioExceptionIsTransient() = runBlocking {
        seedActive(lastValidatedAt = now)
        transport.throwable = IOException("connection refused")
        val manager = TestManager()

        val result = manager.validateOnline()

        assertTransientPreservesEverything(result, TransientReason.NETWORK_UNAVAILABLE)
    }

    @Test
    fun h51_18_timeoutIsTransient() = runBlocking {
        seedActive(lastValidatedAt = now)
        transport.throwable = SocketTimeoutException("read timed out")
        val manager = TestManager()

        val result = manager.validateOnline()

        assertTransientPreservesEverything(result, TransientReason.TIMEOUT)
    }

    @Test
    fun h51_19_dnsFailureIsTransient() = runBlocking {
        seedActive(lastValidatedAt = now)
        transport.throwable = UnknownHostException("license.skmnetwork.com")
        val manager = TestManager()

        val result = manager.validateOnline()

        assertTransientPreservesEverything(result, TransientReason.DNS_FAILURE)
    }

    @Test
    fun h51_19b_transientNeverDisguisesStaleAsFresh() = runBlocking {
        // The successful validation is older than the TTL but inside the grace window.
        seedActive(lastValidatedAt = now - (policy.freshTtlMillis + 1L))
        val manager = TestManager()
        manager.refreshLicense()
        assertEquals(LicensePhase.STALE_ACTIVE, manager.licenseState.value.phase)

        transport.throwable = IOException("offline")
        manager.validateOnline()

        val state = manager.licenseState.value
        assertEquals(LicensePhase.TRANSIENT_ERROR, state.phase)
        assertEquals(TransientReason.NETWORK_UNAVAILABLE, state.transientReason)
        assertEquals(LicensePhase.STALE_ACTIVE, state.effectivePhase)
        assertTrue(state.isStale)
        assertTrue(state.grantsAccess)
    }

    @Test
    fun h51_19c_transientOnAFreshLicenceStaysInsideTheTtl() = runBlocking {
        seedActive(lastValidatedAt = now)
        val manager = TestManager()
        manager.refreshLicense()

        transport.throwable = IOException("offline")
        manager.validateOnline()

        val state = manager.licenseState.value
        assertEquals(LicensePhase.TRANSIENT_ERROR, state.phase)
        assertEquals(LicensePhase.FRESH_ACTIVE, state.effectivePhase)
        assertTrue(state.grantsAccess)
    }

    // =================================================================
    // 20 / 21. Activation is not a validation
    // =================================================================

    @Test
    fun h51_20_activationLeavesLastValidatedAtAtZero() = runBlocking {
        val manager = TestManager()
        transport.status = 200
        transport.body = """{"success":true,"status":"ACTIVE"}"""

        val result = manager.activateLicense(licenseCode, ownerEmail)

        assertTrue(result is ActivationResult.Active)
        val entitlement = repo.getLicenseEntitlement()
        assertEquals(0L, entitlement.lastValidatedAt)
        assertTrue(entitlement.activatedAt > 0L)
        assertTrue(manager.licenseState.value.neverValidated)
        assertEquals(
            "An activation must never present as a fresh validation",
            LicensePhase.STALE_ACTIVE,
            manager.licenseState.value.phase
        )
    }

    @Test
    fun h51_20b_activationDoesNotIssueAValidateRequest() = runBlocking {
        val manager = TestManager()
        transport.status = 200
        transport.body = """{"success":true,"status":"ACTIVE"}"""

        manager.activateLicense(licenseCode, ownerEmail)

        assertEquals(1, transport.calls.get())
        assertTrue(transport.urls.single().endsWith("/v1/license/activate"))
    }

    @Test
    fun h51_21_successfulValidationUpdatesLastValidatedAt() = runBlocking {
        seedActive(lastValidatedAt = 0L)
        val manager = TestManager()
        val validatedAt = now

        manager.validateOnline()

        assertEquals(validatedAt, repo.getLicenseEntitlement().lastValidatedAt)
    }

    // =================================================================
    // 22 / 23 / 24. TTL and grace boundaries
    // =================================================================

    /** Freshness for a licence whose last successful validation was exactly [age] millis ago. */
    private fun freshnessAt(age: Long): LicensePhase {
        val state = PersistedLicenseState(
            entitlement = LicenseEntitlementData(
                status = "ACTIVE",
                ownerEmail = ownerEmail,
                activatedAt = now,
                lastValidatedAt = now
            )
        )
        return LicenseStateEvaluator.evaluateFreshness(
            state,
            now = now + age,
            policy = policy
        ).phase
    }

    @Test
    fun h51_22_ttlBoundaryIsInclusive() {
        assertEquals(LicensePhase.FRESH_ACTIVE, freshnessAt(policy.freshTtlMillis - 1L))
        assertEquals(LicensePhase.FRESH_ACTIVE, freshnessAt(policy.freshTtlMillis))
        assertEquals(LicensePhase.STALE_ACTIVE, freshnessAt(policy.freshTtlMillis + 1L))
    }

    @Test
    fun h51_23_graceBoundaryIsInclusive() {
        val tolerance = policy.freshTtlMillis + policy.graceWindowMillis
        assertEquals(LicensePhase.STALE_ACTIVE, freshnessAt(tolerance - 1L))
        assertEquals(LicensePhase.STALE_ACTIVE, freshnessAt(tolerance))
    }

    @Test
    fun h51_24_beyondTheGraceWindowTheLicenceIsUnlicensed() = runBlocking {
        val tolerance = policy.freshTtlMillis + policy.graceWindowMillis
        seedActive(lastValidatedAt = now - (tolerance + 1L))
        val manager = TestManager()

        manager.refreshLicense()

        val state = manager.licenseState.value
        assertEquals(LicensePhase.UNLICENSED, state.phase)
        assertEquals(LicenseBlockReason.GRACE_EXPIRED, state.blockReason)
        assertFalse(state.grantsAccess)
        // The credential survives a lapsed grace window so the merchant can re-activate.
        assertFalse(manager.cleared.get())
    }

    @Test
    fun h51_24b_neverValidatedIsNeverFresh() {
        val state = PersistedLicenseState(
            entitlement = LicenseEntitlementData(
                status = "ACTIVE",
                ownerEmail = ownerEmail,
                activatedAt = now,
                lastValidatedAt = 0L
            )
        )
        val evaluated = LicenseStateEvaluator.evaluateFreshness(state, now, policy)
        assertEquals(LicensePhase.STALE_ACTIVE, evaluated.phase)
    }

    @Test
    fun h51_24c_anUpgradeFromTheOldBuildIsNotLockedOutBeforeTheFirstValidation() = runBlocking {
        // The H.5.0 build wrote lastValidatedAt at ACTIVATION time, so an installation that has
        // never pressed the Settings button carries a timestamp far older than the grace window. A
        // passive local evaluation must therefore NOT persist the lapse: the cold-start validation
        // has to run first, or every pre-existing customer would be gated before the server was
        // ever asked whether their licence is still valid.
        val longAgo = now - (policy.freshTtlMillis + policy.graceWindowMillis + 1L)
        seedActive(lastValidatedAt = longAgo, activatedAt = longAgo)
        val manager = TestManager()
        manager.refreshLicense()
        assertEquals(LicensePhase.UNLICENSED, manager.licenseState.value.phase)

        // The stored record is still intact, so the lifecycle triggers still validate.
        assertTrue(repo.getLicenseEntitlement().isEntitled)
        assertNotNull(manager.triggerColdStartValidation())
        assertEquals(1, transport.calls.get())

        // The server said VALID, so the merchant is back inside the app with a fresh verdict.
        assertEquals(LicensePhase.FRESH_ACTIVE, manager.licenseState.value.phase)
        assertEquals(now, repo.getLicenseEntitlement().lastValidatedAt)
    }

    // =================================================================
    // 25. Response ordering
    // =================================================================

    @Test
    fun h51_25_aLateOlderResponseCannotOverwriteANewerRejection() = runBlocking {
        seedActive(lastValidatedAt = now)
        val manager = TestManager()

        // Validation B (the newer attempt) reports REVOKED and lands first.
        val sequenceA = manager.nextValidationSequence()
        val sequenceB = sequenceA + 1
        assertTrue(manager.commitValidationOutcome(sequenceB, ValidationResult.Revoked()))
        assertFalse(repo.getLicenseEntitlement().isEntitled)

        // Validation A (the older attempt) reports VALID and lands afterwards. It must be discarded.
        val applied = manager.commitValidationOutcome(sequenceA, ValidationResult.Valid())

        assertFalse("A stale sequence must be rejected", applied)
        assertFalse("Access must stay blocked", repo.getLicenseEntitlement().isEntitled)
        assertFalse(manager.licenseState.value.grantsAccess)
    }

    @Test
    fun h51_25b_aStaleValidCannotUndoANewerDeviceMismatch() = runBlocking {
        seedActive(lastValidatedAt = now)
        val manager = TestManager()

        val stale = manager.nextValidationSequence()
        val fresh = stale + 1

        assertTrue(manager.commitValidationOutcome(fresh, ValidationResult.DeviceMismatch()))
        assertFalse(manager.commitValidationOutcome(stale, ValidationResult.Valid()))

        assertEquals(LicensePhase.BLOCKED, manager.licenseState.value.phase)
        assertEquals(LicenseBlockReason.DEVICE_MISMATCH, manager.licenseState.value.blockReason)
        assertFalse(repo.getLicenseEntitlement().isEntitled)
        assertFalse(manager.licenseState.value.grantsAccess)
    }

    // =================================================================
    // 27. Cancellation
    // =================================================================

    @Test
    fun h51_27_cancellationExceptionIsNeverConvertedIntoAResult() = runBlocking {
        transport.throwable = CancellationException("cancelled mid-flight")

        var caught: Throwable? = null
        try {
            apiClient.validateLicense(licenseCode, ownerEmail, "device-1")
        } catch (e: Throwable) {
            caught = e
        }

        assertTrue("CancellationException must propagate, got $caught", caught is CancellationException)
        assertTrue("The calling coroutine must stay active", coroutineContext.isActive)
    }

    @Test
    fun h51_27b_cancellingACallerDoesNotDeLicenseTheMerchant() = runBlocking {
        seedActive(lastValidatedAt = now)
        val manager = TestManager()
        val gate = CompletableDeferred<Unit>()
        transport.gate = gate

        val waiting = async(Dispatchers.Default) { manager.validateOnline() }
        withTimeout(10_000) { while (transport.calls.get() == 0) delay(5) }
        waiting.cancel()

        var observed: Throwable? = null
        try {
            waiting.await()
        } catch (e: Throwable) {
            observed = e
        }
        assertTrue("A cancelled caller must see CancellationException, got $observed",
            observed is CancellationException)

        gate.complete(Unit)
        delay(200)

        assertTrue("A cancellation must not clear the entitlement",
            repo.getLicenseEntitlement().isEntitled)
        assertFalse(manager.cleared.get())
    }

    // =================================================================
    // 28. DEVICE_MISMATCH keeps the credential usable
    // =================================================================

    @Test
    fun h51_28_afterDeviceMismatchTheMerchantCanStillReachRecovery() = runBlocking {
        seedActive(lastValidatedAt = now)
        transport.body = validBody("DEVICE_MISMATCH")
        val manager = TestManager()

        manager.validateOnline()

        // The stored code is still readable, so a re-activation or a recovery request remains possible.
        assertEquals(licenseCode, manager.readStoredCode())
        val entitlement = repo.getLicenseEntitlement()
        assertFalse(entitlement.isEntitled)
        assertEquals(ownerEmail, entitlement.ownerEmail)

        // And a fresh activation attempt on this same device reaches the DEVICE_MISMATCH branch the
        // gate uses to reveal the Task 7A recovery action.
        transport.status = 400
        transport.body = """{"success":false,"error":{"code":"DEVICE_MISMATCH","message":"mismatch"}}"""
        val activation = manager.activateLicense(licenseCode, ownerEmail)
        assertTrue(activation is ActivationResult.DeviceMismatch)
    }

    // =================================================================
    // 29. SecureLicenseStorage fails closed
    // =================================================================

    @Test
    fun h51_29_keyStoreFailureFailsClosedAndWritesNothing() {
        val file = File(tempFolder.newFolder(), "bukuwarung_sec_lic.dat")
        val storage = object : SecureLicenseStorage() {
            override fun obtainSecretKey(): SecretKey? = null
        }

        val saved = storage.saveEncryptedLicenseCode(file, licenseCode)

        assertFalse("A KeyStore failure must fail closed", saved)
        assertFalse("No file may be written when no key is available", file.exists())
        assertNull("No plaintext may ever be read back", storage.getEncryptedLicenseCode(file))
    }

    @Test
    fun h51_29b_keyStoreFailureOnReadReturnsNullInsteadOfPlaintext() {
        val folder = tempFolder.newFolder()
        val file = File(folder, "bukuwarung_sec_lic.dat")

        // A legacy plaintext blob from an older build must not be resurrected by the new code.
        file.writeText("FALLBACK:$licenseCode")

        val broken = object : SecureLicenseStorage() {
            override fun obtainSecretKey(): SecretKey? = null
        }
        assertNull("A broken KeyStore must never yield the plaintext code", broken.getEncryptedLicenseCode(file))

        val working = object : SecureLicenseStorage() {
            override fun obtainSecretKey(): SecretKey =
                KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        }
        assertNull("The legacy plaintext fallback must be ignored entirely", working.getEncryptedLicenseCode(file))
    }

    @Test
    fun h51_29c_secureRoundTripDoesNotStoreThePlaintextCode() {
        val file = File(tempFolder.newFolder(), "bukuwarung_sec_lic.dat")
        // One stable key, exactly as the AndroidKeyStore alias would behave across calls.
        val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val storage = object : SecureLicenseStorage() {
            override fun obtainSecretKey(): SecretKey = key
        }

        assertTrue(storage.saveEncryptedLicenseCode(file, licenseCode))
        assertEquals(licenseCode, storage.getEncryptedLicenseCode(file))

        val raw = String(file.readBytes(), Charsets.ISO_8859_1)
        assertFalse("The credential must not be readable on disk", raw.contains(licenseCode))
        assertFalse("No plaintext fallback marker may be written", raw.contains("FALLBACK:"))

        storage.clearLicenseCode(file)
        assertNull(storage.getEncryptedLicenseCode(file))
    }

    // =================================================================
    // 30. Release security boundary is untouched
    // =================================================================

    @Test
    fun h51_30_releaseBuildConfigBoundaryIsIntact() {
        val fields = BuildConfig::class.java.declaredFields.map { it.name }
        assertFalse("ADMIN_API_KEY must not exist in BuildConfig", fields.contains("ADMIN_API_KEY"))
        assertFalse("ADMIN_KEY must not exist in BuildConfig", fields.contains("ADMIN_KEY"))
        assertFalse("SERVER_PEPPER must not exist in BuildConfig", fields.contains("SERVER_PEPPER"))
        assertFalse("The owner-test bypass must be off in the release configuration",
            BuildConfig.ENABLE_OWNER_TEST)
        assertFalse("These tests run against the release configuration", BuildConfig.DEBUG)
    }

    @Test
    fun h51_30b_lifecycleTriggersAreWiredIntoTheNavigationHost() {
        val navigation = File("src/main/java/id/skmnetwork/bukuwarung/ui/navigation/AppNavigation.kt")
        assertTrue("AppNavigation source file must exist", navigation.exists())
        val source = navigation.readText()

        assertTrue("Cold-start validation must be wired", source.contains("triggerColdStartValidation()"))
        assertTrue("Foreground validation must be wired",
            source.contains("ForegroundLicenseValidationObserver"))
        assertTrue("The observer must attach to the process lifecycle",
            source.contains("ProcessLifecycleOwner.get()"))
        // Gate H.5.3 (H.5.2-P2-3) renamed the handle captured for disposal, because the acquisition
        // is now guarded and must only remove a listener it actually attached.
        assertTrue("The observer must be removed on dispose",
            source.contains("removeObserver(observerRef)"))
        assertTrue("Access must be decided by the evaluated state",
            source.contains("licenseState.grantsAccess"))
    }

    @Test
    fun h51_30c_noWorkmanagerAndNoServerSchemaChange() {
        val gradle = File("build.gradle.kts").readText()
        assertFalse("Gate H.5.1 explicitly excludes WorkManager", gradle.contains("androidx.work"))
    }
}
