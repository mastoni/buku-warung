package id.skmnetwork.bukuwarung.license

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
}

/**
 * Result of POST /v1/license/validate
 */
sealed class ValidationResult {
    data class Valid(val message: String = "Lisensi aktif dan valid") : ValidationResult()
    data class EmailMismatch(val message: String = "Email lisensi tidak cocok") : ValidationResult()
    data class DeviceMismatch(val message: String = "Perangkat tidak cocok dengan lisensi aktif") : ValidationResult()
    data class Revoked(val message: String = "Lisensi tidak aktif / telah dicabut") : ValidationResult()
    data class Invalid(val message: String = "Lisensi tidak ditemukan atau belum diaktifkan") : ValidationResult()
    data class NetworkError(val message: String = "Tidak dapat terhubung ke server") : ValidationResult()
    data class ServerError(val message: String = "Terjadi kesalahan pada server") : ValidationResult()
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
