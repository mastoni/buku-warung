package id.skmnetwork.bukuwarung.backup

/**
 * Foundation Step 5 — Google Sheets Backup Transport SPI
 * Abstract SPI interface for external transport (e.g. Mock, Google Sheets API).
 *
 * Gate H.4.2-CODE-FIX.1 - `createSpreadsheet` is part of the contract.
 *
 * It was previously absent, which forced `BackupRestoreManager.ensureSpreadsheetCreated` to
 * downcast to the concrete `GoogleSheetsApiTransport` and, for anything else, return
 * `Result.success("SPREADSHEET_<wall-clock-millis>")`. That fabricated identifier is a fake
 * success: a backup then reported "Data warung berhasil dicadangkan ke Google Sheets." while
 * nothing existed in Google Drive. With creation on the interface the manager talks only to the
 * abstraction, and every implementation has to answer honestly.
 */
interface SheetsBackupTransport {
    /**
     * Creates the backup spreadsheet and returns its Google spreadsheet id.
     *
     * Implementations must fail rather than invent an id. A transport that cannot create a real
     * remote spreadsheet has to return a failure the caller can show to the merchant.
     */
    suspend fun createSpreadsheet(title: String): Result<String>

    suspend fun writeBackup(spreadsheetId: String, snapshot: BackupSnapshot): Result<Unit>
    suspend fun readBackup(spreadsheetId: String): Result<BackupSnapshot>
}
