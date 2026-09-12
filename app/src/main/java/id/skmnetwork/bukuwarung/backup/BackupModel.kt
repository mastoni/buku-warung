package id.skmnetwork.bukuwarung.backup

/**
 * Foundation Step 5 — Google Sheets Backup & Restore Models
 * Pure domain models representing backup snapshot and tab structure.
 */

data class BackupMetadata(
    val backupFormatVersion: String = "1.0",
    val roomSchemaVersion: Int = 9,
    val exportedAt: Long = System.currentTimeMillis(),
    val businessId: String = "",
    val deviceId: String = "",
    val appVersion: String = "1.0.0",
    val totalRecords: Int = 0,
    val checksum: String = ""
)

data class SheetTab(
    val name: String,
    val headers: List<String>,
    val rows: List<List<String>>
)

data class BackupSnapshot(
    val metadata: BackupMetadata,
    val tabs: Map<String, SheetTab>
) {
    fun getTab(name: String): SheetTab? = tabs[name]
}

data class BusinessProfileBackup(
    val businessId: String,
    val shopName: String,
    val ownerName: String,
    val phone: String,
    val address: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class DeviceProfileBackup(
    val deviceId: String,
    val businessId: String,
    val deviceName: String,
    val deviceRole: String,
    val appVersion: String
)

sealed class BackupValidationResult {
    object Valid : BackupValidationResult()
    data class Invalid(val reason: String, val cause: Throwable? = null) : BackupValidationResult()
}

open class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)
class IncompatibleBackupFormatException(message: String) : BackupException(message)
class IncompatibleSchemaVersionException(message: String) : BackupException(message)
class ChecksumMismatchException(message: String) : BackupException(message)
class CorruptedBackupException(message: String) : BackupException(message)
