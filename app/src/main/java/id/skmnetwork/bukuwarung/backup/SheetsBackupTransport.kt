package id.skmnetwork.bukuwarung.backup

/**
 * Foundation Step 5 — Google Sheets Backup Transport SPI
 * Abstract SPI interface for external transport (e.g. Mock, Google Sheets API).
 */
interface SheetsBackupTransport {
    suspend fun writeBackup(spreadsheetId: String, snapshot: BackupSnapshot): Result<Unit>
    suspend fun readBackup(spreadsheetId: String): Result<BackupSnapshot>
}
