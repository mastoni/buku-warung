package id.skmnetwork.bukuwarung.license

import id.skmnetwork.bukuwarung.BuildConfig
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository

/**
 * Gate H.5.1 - license lifecycle state model.
 *
 * H.5.0 established that the persisted string "ACTIVE" was the sole runtime authority and that
 * `lastValidatedAt` was written but never read. These types make freshness a first-class,
 * evaluated runtime state instead of an implicit consequence of a stored string.
 */

/**
 * The runtime phase of the commercial licence.
 *
 * Only [FRESH_ACTIVE], [STALE_ACTIVE] and a [TRANSIENT_ERROR] whose fallback phase grants access
 * may enter the application. Everything else is gated.
 */
enum class LicensePhase {
    /** No decision has been produced yet (process start). */
    CHECKING,

    /** A successful server validation exists and is younger than the TTL. */
    FRESH_ACTIVE,

    /** A successful server validation exists but is older than the TTL and still inside the grace window. */
    STALE_ACTIVE,

    /** Never activated, or the grace window elapsed, or definitively rejected and cleared. */
    UNLICENSED,

    /** A definitive server rejection. Access is denied and the reason is retained for the UI. */
    BLOCKED,

    /**
     * The most recent validation attempt could not reach a verdict (network / server / HTTP
     * infrastructure). Local entitlement and credential are preserved; [LicenseRuntimeState.fallbackPhase]
     * carries the freshness underneath, so a stale licence is never displayed as fresh.
     */
    TRANSIENT_ERROR
}

/** Why access is currently denied. Persisted so the gate can explain itself after a process restart. */
enum class LicenseBlockReason {
    NONE,
    NEVER_ACTIVATED,
    GRACE_EXPIRED,
    REVOKED,
    DEVICE_MISMATCH,
    EMAIL_MISMATCH,
    INVALID
}

/**
 * A failure that carries NO authoritative licence verdict.
 *
 * Gate H.5.1 rule: a transient failure must never delete the entitlement, must never delete the
 * stored licence credential, and must never turn ACTIVE into UNLICENSED.
 */
enum class TransientReason {
    NETWORK_UNAVAILABLE,
    TIMEOUT,
    DNS_FAILURE,
    HTTP_BAD_REQUEST,
    HTTP_UNAUTHORIZED,
    HTTP_FORBIDDEN,
    HTTP_NOT_FOUND,
    HTTP_TOO_MANY_REQUESTS,
    HTTP_SERVER_ERROR,
    HTTP_UNEXPECTED,
    MALFORMED_RESPONSE,
    CREDENTIAL_UNAVAILABLE,
    CLIENT_UNAVAILABLE,

    /**
     * Gate H.5.3 (H.5.2-P1-1): the local store could not be read or written.
     *
     * This is NOT a licence verdict and it is NOT a pass: the runtime state it produces does not
     * grant access, because a licence whose freshness cannot be established must not be presented as
     * usable. It is transient, so it deletes neither the entitlement nor the credential, and it
     * resolves on its own as soon as local storage works again.
     */
    LOCAL_STORAGE_UNAVAILABLE,

    /**
     * Gate H.5.3 (H.5.2-P2-2): an activation succeeded on the server but a newer attempt had already
     * decided the local state, so the activation was not applied. The merchant is asked to retry.
     */
    ACTIVATION_SUPERSEDED
}

/**
 * Freshness policy.
 *
 * The values below are deliberately explicit and constructor-injectable so every boundary in this
 * file is testable without touching wall-clock time.
 *
 * Why these numbers:
 *  - FRESH_TTL = 7 days. Buku Warung is a daily point-of-sale tool used by a merchant who is
 *    frequently on prepaid data with intermittent connectivity. One week of connectivity is the
 *    shortest window that still lets a shop open every morning without a network round trip, and it
 *    is also the longest window in which a genuinely revoked licence may remain usable. Seven days
 *    is the smallest "weekly business rhythm" value, so it is the chosen bound.
 *  - GRACE_WINDOW = 30 days, applied AFTER the TTL. Together this yields 37 days of cumulative
 *    offline tolerance, i.e. one full billing month plus a week. A shop that is offline for more
 *    than a month has effectively lost the device, which is exactly the condition in which the
 *    merchant should be forced back through activation rather than continuing indefinitely.
 *
 * A stale licence is NOT a bypass: it is a bounded, visible, self-expiring state.
 */
data class LicensePolicy(
    val freshTtlMillis: Long = DEFAULT_FRESH_TTL_MILLIS,
    val graceWindowMillis: Long = DEFAULT_GRACE_WINDOW_MILLIS
) {
    init {
        require(freshTtlMillis >= 0) { "freshTtlMillis must not be negative" }
        require(graceWindowMillis >= 0) { "graceWindowMillis must not be negative" }
    }

    /** Age at which a licence leaves STALE_ACTIVE and becomes UNLICENSED. */
    val totalToleranceMillis: Long = freshTtlMillis + graceWindowMillis

    companion object {
        const val DEFAULT_FRESH_TTL_MILLIS: Long = 7L * 24 * 60 * 60 * 1000
        const val DEFAULT_GRACE_WINDOW_MILLIS: Long = 30L * 24 * 60 * 60 * 1000

        val DEFAULT = LicensePolicy()
    }
}

/**
 * The authoritative runtime state of the licence.
 *
 * [phase] is what happened last. [fallbackPhase] is the freshness that the persisted record still
 * supports, used only while [phase] is [LicensePhase.TRANSIENT_ERROR]. [grantsAccess] always
 * resolves against the effective phase, so a transient failure can never present a stale licence as
 * a fresh one, and can never silently drop a licence that is still inside the grace window.
 */
data class LicenseRuntimeState(
    val phase: LicensePhase,
    val blockReason: LicenseBlockReason = LicenseBlockReason.NONE,
    val transientReason: TransientReason? = null,
    val fallbackPhase: LicensePhase = LicensePhase.UNLICENSED,
    val activatedAt: Long = 0L,
    val lastValidatedAt: Long = 0L,
    val validationAgeMillis: Long = 0L,
    val freshTtlMillis: Long = 0L,
    val graceWindowMillis: Long = 0L,
    val graceRemainingMillis: Long = 0L
) {
    /** The phase that actually governs access. */
    val effectivePhase: LicensePhase
        get() = if (phase == LicensePhase.TRANSIENT_ERROR) fallbackPhase else phase

    /** The single authority for "may this device enter the application". */
    val grantsAccess: Boolean
        get() = effectivePhase == LicensePhase.FRESH_ACTIVE || effectivePhase == LicensePhase.STALE_ACTIVE

    /** True when the licence is usable but its successful validation is older than the TTL. */
    val isStale: Boolean
        get() = effectivePhase == LicensePhase.STALE_ACTIVE

    /** True when the last attempt failed for infrastructure reasons and the verdict is unknown. */
    val isTransientError: Boolean
        get() = phase == LicensePhase.TRANSIENT_ERROR

    /** True when the licence has never produced a single successful validation. */
    val neverValidated: Boolean
        get() = lastValidatedAt == 0L

    /** UI-facing coarse status, kept for the existing screens and the H.5.0 test contract. */
    val status: LicenseStatus
        get() = when (phase) {
            LicensePhase.CHECKING -> LicenseStatus.CHECKING
            LicensePhase.FRESH_ACTIVE -> LicenseStatus.ACTIVE
            LicensePhase.STALE_ACTIVE -> LicenseStatus.STALE_ACTIVE
            LicensePhase.UNLICENSED -> LicenseStatus.UNLICENSED
            LicensePhase.BLOCKED -> LicenseStatus.BLOCKED
            LicensePhase.TRANSIENT_ERROR -> LicenseStatus.TRANSIENT_ERROR
        }

    companion object {
        fun checking() = LicenseRuntimeState(phase = LicensePhase.CHECKING)
    }
}

/**
 * The persisted inputs to [LicenseRuntimeState]. Deliberately separate from the runtime state so
 * that "what is stored" and "what the user may do" are never the same object.
 */
data class PersistedLicenseState(
    val entitlement: LicenseEntitlementData = LicenseEntitlementData(),
    val blocked: Boolean = false,
    val blockReason: LicenseBlockReason = LicenseBlockReason.NONE
)

/**
 * Pure evaluation of a persisted record against a policy. No I/O, no clock of its own.
 *
 * Gate H.5.1: the persisted "ACTIVE" string is an INPUT, never the runtime authority. Authority
 * comes from the validation anchor plus the policy.
 */
object LicenseStateEvaluator {

    /**
     * The instant from which the current validation verdict is measured.
     *
     * `lastValidatedAt` is the anchor once the licence has actually been validated. Gate H.5.1
     * requires activation to leave `lastValidatedAt = 0`, so a never-validated licence falls back to
     * `activatedAt` and is therefore immediately STALE rather than falsely FRESH.
     */
    fun validationAnchor(state: PersistedLicenseState): Long {
        val validated = state.entitlement.lastValidatedAt
        return if (validated > 0L) validated else state.entitlement.activatedAt
    }

    /** Freshness implied by the persisted record alone, ignoring any transient overlay. */
    fun evaluateFreshness(
        state: PersistedLicenseState,
        now: Long,
        policy: LicensePolicy
    ): LicenseRuntimeState {
        val tolerance = policy.totalToleranceMillis
        val age = if (state.blocked) 0L else (now - validationAnchor(state)).coerceAtLeast(0L)

        val base = LicenseRuntimeState(
            phase = LicensePhase.UNLICENSED,
            blockReason = state.blockReason,
            activatedAt = state.entitlement.activatedAt,
            lastValidatedAt = state.entitlement.lastValidatedAt,
            validationAgeMillis = age,
            freshTtlMillis = policy.freshTtlMillis,
            graceWindowMillis = policy.graceWindowMillis,
            graceRemainingMillis = (tolerance - age).coerceAtLeast(0L)
        )

        if (state.blocked) {
            return base.copy(phase = LicensePhase.BLOCKED)
        }
        if (!state.entitlement.isEntitled) {
            return base.copy(
                phase = LicensePhase.UNLICENSED,
                blockReason = if (state.blockReason != LicenseBlockReason.NONE) {
                    state.blockReason
                } else {
                    LicenseBlockReason.NEVER_ACTIVATED
                }
            )
        }
        if (validationAnchor(state) == 0L) {
            // An ACTIVE record with no timestamp at all cannot be produced by this app's writers.
            // Fail closed rather than invent a freshness we cannot justify.
            return base.copy(phase = LicensePhase.UNLICENSED, blockReason = LicenseBlockReason.NEVER_ACTIVATED)
        }
        // Gate H.5.1 section 7: a licence that has never produced a single successful validation is
        // never FRESH, however recent the activation was. FRESH is reserved for a real server verdict,
        // so the next lifecycle trigger is guaranteed to actually ask the server.
        val neverValidated = state.entitlement.lastValidatedAt <= 0L
        return when {
            age <= policy.freshTtlMillis && !neverValidated -> base.copy(phase = LicensePhase.FRESH_ACTIVE)
            age <= tolerance -> base.copy(phase = LicensePhase.STALE_ACTIVE)
            else -> base.copy(phase = LicensePhase.UNLICENSED, blockReason = LicenseBlockReason.GRACE_EXPIRED)
        }
    }
}

/**
 * Result of POST /v1/license/activate
 */
sealed class ActivationResult {
    data class Active(val ownerEmail: String, val message: String = "Aktivasi berhasil") : ActivationResult()
    data class EmailMismatch(val message: String = "Email lisensi tidak cocok") : ActivationResult()
    data class DeviceMismatch(val message: String = "Lisensi ini sudah aktif pada perangkat lain") : ActivationResult()
    data class Revoked(val message: String = "Lisensi tidak aktif / telah dicabut") : ActivationResult()
    data class Invalid(val message: String = "Kode lisensi tidak valid") : ActivationResult()
    data class NetworkError(val message: String = "Tidak dapat terhubung ke server lisensi") : ActivationResult()
    data class ServerError(val message: String = "Terjadi kesalahan pada server") : ActivationResult()

    /**
     * Gate H.5.1: no licence verdict was obtained. The response was unparseable, or the HTTP status
     * carried no recognised backend error code. Never present this as "invalid licence code".
     */
    data class Transient(
        val reason: TransientReason = TransientReason.HTTP_UNEXPECTED,
        val httpStatus: Int = 0
    ) : ActivationResult()
}

/**
 * Result of POST /v1/license/validate
 *
 * Gate H.5.1: only a recognised server `status` value may produce a definitive outcome. Everything
 * else is a [Transient], which by contract preserves both the local entitlement and the stored
 * licence credential.
 */
sealed class ValidationResult {

    /** HTTP 200 + status VALID. The only outcome that refreshes `lastValidatedAt`. */
    data class Valid(val message: String = "Lisensi aktif dan valid") : ValidationResult()

    data class EmailMismatch(val message: String = "Email lisensi tidak cocok") : ValidationResult()

    /**
     * HTTP 200 + status DEVICE_MISMATCH.
     *
     * Gate H.5.1: definitive and blocking, but the stored licence code is deliberately RETAINED so
     * the merchant can still re-activate or run the Task 7A recovery flow from the gate.
     */
    data class DeviceMismatch(val message: String = "Perangkat tidak cocok dengan lisensi aktif") : ValidationResult()

    data class Revoked(val message: String = "Lisensi tidak aktif / telah dicabut") : ValidationResult()
    data class Invalid(val message: String = "Lisensi tidak ditemukan atau belum diaktifkan") : ValidationResult()

    /**
     * The local credential could not be read back (for example the AndroidKeyStore key became
     * unavailable). This is a local availability problem, not a server verdict, so it must not
     * clear the entitlement either.
     */
    data class CredentialUnavailable(
        val message: String = "Kredensial lisensi tidak dapat dibaca di perangkat ini"
    ) : ValidationResult()

    /**
     * No authoritative verdict could be obtained. Every subclass MUST leave the local entitlement
     * and the stored credential untouched.
     */
    sealed class Transient(open val reason: TransientReason) : ValidationResult() {
        abstract val message: String

        data class Timeout(override val message: String = "Server lisensi tidak merespons") :
            Transient(TransientReason.TIMEOUT)

        data class NetworkError(
            override val reason: TransientReason = TransientReason.NETWORK_UNAVAILABLE,
            override val message: String = "Tidak dapat terhubung ke server"
        ) : Transient(reason)

        data class ServerError(
            override val reason: TransientReason = TransientReason.HTTP_SERVER_ERROR,
            override val message: String = "Terjadi kesalahan pada server"
        ) : Transient(reason)

        data class MalformedResponse(
            override val message: String = "Respons server lisensi tidak dapat dibaca"
        ) : Transient(TransientReason.MALFORMED_RESPONSE)

        /** Any HTTP status that carried no recognised licence status. Never a licence decision. */
        data class HttpRejection(
            val httpStatus: Int,
            override val reason: TransientReason,
            override val message: String = "Server lisensi menolak permintaan sementara"
        ) : Transient(reason)
    }
}

/**
 * Result of POST /v1/license/recover (customer-initiated device recovery).
 *
 * RecoveryPending means the request was RECORDED ONLY. No device binding changes,
 * no licence becomes active, and an authorised admin rebind is still required.
 */
sealed class RecoveryResult {
    /** Backend success: HTTP 200 with status RECOVERY_PENDING. */
    data class RecoveryPending(val message: String = "Permintaan pemulihan perangkat dikirim") : RecoveryResult()

    /** Backend code INVALID_REQUEST. */
    data class InvalidRequest(val message: String = "Data pemulihan belum lengkap atau tidak valid") : RecoveryResult()

    /** Backend code LICENSE_NOT_FOUND. */
    data class LicenseNotFound(val message: String = "Lisensi tidak ditemukan") : RecoveryResult()

    /** Backend code LICENSE_REVOKED. */
    data class LicenseRevoked(val message: String = "Lisensi ini sudah tidak aktif") : RecoveryResult()

    /** Backend code EMAIL_MISMATCH. */
    data class EmailMismatch(val message: String = "Email pemilik tidak sesuai dengan lisensi") : RecoveryResult()

    /** Client-side fallback: transport failure. Not a backend code. */
    data class NetworkError(val message: String = "Jaringan bermasalah. Silakan coba lagi") : RecoveryResult()

    /** Client-side fallback: unrecognised status code or malformed body. Not a backend code. */
    data class UnexpectedError(val message: String = "Pemulihan perangkat gagal. Silakan coba lagi") : RecoveryResult()
}

/**
 * Local non-sensitive license entitlement data (DataStore persisted)
 */
data class LicenseEntitlementData(
    val status: String = "UNLICENSED",
    val ownerEmail: String = "",
    val activatedAt: Long = 0L,
    val lastValidatedAt: Long = 0L
) {
    val isEntitled: Boolean
        get() = status == "ACTIVE"
}

/** Tier reported by the licence layer. */
enum class LicenseTier(val label: String) {
    WARUNG("Buku Warung (Satu Perangkat)"),
    WARUNG_PRO("Buku Warung Pro (Multi Perangkat)")
}

/** Where a validation was requested from. Used for logging-free diagnostics and test assertions. */
enum class ValidationTrigger {
    /** Process start, when a local entitlement and credential exist. */
    COLD_START,

    /** The process returned to the foreground and the TTL no longer covers the last success. */
    FOREGROUND,

    /** The user pressed "Periksa / Pulihkan Lisensi" in Settings. */
    MANUAL
}

/** Coarse status consumed by the existing UI surfaces. */
enum class LicenseStatus {
    UNKNOWN,
    CHECKING,
    ACTIVE,
    STALE_ACTIVE,
    UNLICENSED,
    BLOCKED,
    TRANSIENT_ERROR,
    ERROR
}

interface LicenseProvider {
    suspend fun checkLicense(): LicenseStatus
    suspend fun getLicenseTier(): LicenseTier
}

/**
 * DebugLicenseProvider:
 * Bypasses license check ONLY when isDebug is explicitly true (development/debug build).
 * In release builds or when isDebug is false, it returns UNLICENSED.
 *
 * Gate H.5.1: retained for compatibility only. It is never constructed by main source; the
 * runtime authority is LicenseStateEvaluator driven by LicenseManager.
 */
class DebugLicenseProvider(
    private val isDebug: Boolean = BuildConfig.DEBUG
) : LicenseProvider {
    override suspend fun checkLicense(): LicenseStatus {
        return if (isDebug) {
            LicenseStatus.ACTIVE
        } else {
            LicenseStatus.UNLICENSED
        }
    }

    override suspend fun getLicenseTier(): LicenseTier {
        return LicenseTier.WARUNG
    }
}

/**
 * ProductionLicenseProvider:
 * Reads persistent local entitlement from UserPreferencesRepository. It performs NO network I/O.
 *
 * Gate H.5.1: retained as a test seam. LicenseManager no longer delegates its authority to this
 * class; see LicenseStateEvaluator for the authority.
 */
class ProductionLicenseProvider(
    private val userPreferencesRepository: UserPreferencesRepository? = null,
    private val enableOwnerTest: Boolean = BuildConfig.ENABLE_OWNER_TEST
) : LicenseProvider {
    override suspend fun checkLicense(): LicenseStatus {
        // 1. Check Commercial License Entitlement in DataStore
        val entitlement = userPreferencesRepository?.getLicenseEntitlement()
        if (entitlement != null && entitlement.isEntitled) {
            return LicenseStatus.ACTIVE
        }

        // 2. Check Owner Test Entitlement (if enabled in build)
        if (enableOwnerTest && userPreferencesRepository?.isOwnerTestActivated() == true) {
            return LicenseStatus.ACTIVE
        }

        return LicenseStatus.UNLICENSED
    }

    override suspend fun getLicenseTier(): LicenseTier {
        return LicenseTier.WARUNG
    }
}
