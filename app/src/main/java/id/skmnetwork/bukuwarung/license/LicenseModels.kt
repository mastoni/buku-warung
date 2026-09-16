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
