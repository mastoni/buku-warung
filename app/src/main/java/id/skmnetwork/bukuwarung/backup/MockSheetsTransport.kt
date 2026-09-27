package id.skmnetwork.bukuwarung.backup

import kotlinx.coroutines.delay
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Foundation Step 5 — Mock Sheets Transport
 * In-memory thread-safe transport for deterministic, offline testing.
 *
 * Gate H.4.2-CODE-FIX.1 - this class is a TEST DOUBLE and must never be the production backup
 * transport. `BackupRestoreManager` no longer defaults its `transport` parameter, so reaching this
 * class in a release build requires someone to construct it explicitly.
 *
 * `createSpreadsheet` returns a clearly-fake, self-describing id rather than a wall-clock value
 * that could be mistaken for a Google spreadsheet id. It is a test seam, not a production path.
 */
class MockSheetsTransport : SheetsBackupTransport {

    private val storage = ConcurrentHashMap<String, BackupSnapshot>()

    var simulateNetworkFailure: Boolean = false
    var simulatedLatencyMs: Long = 0L

    override suspend fun createSpreadsheet(title: String): Result<String> {
        if (simulatedLatencyMs > 0) {
            delay(simulatedLatencyMs)
        }
        if (simulateNetworkFailure) {
            return Result.failure(IOException("Simulated network failure during createSpreadsheet"))
        }
        val id = MOCK_SPREADSHEET_ID_PREFIX + "MOCK" + (storage.size + 1)
        storage[id] = BackupSnapshot(
            metadata = BackupMetadata(businessId = MOCK_BUSINESS_ID, checksum = ""),
            tabs = emptyMap()
        )
        return Result.success(id)
    }

    companion object {
        /** Obvious marker so a mock id can never be mistaken for a real Google spreadsheet id. */
        const val MOCK_SPREADSHEET_ID_PREFIX = "MOCK_SPREADSHEET_"
        const val MOCK_BUSINESS_ID = "MOCK_BUSINESS"
    }

    override suspend fun writeBackup(spreadsheetId: String, snapshot: BackupSnapshot): Result<Unit> {
        if (simulatedLatencyMs > 0) {
            delay(simulatedLatencyMs)
        }
        if (simulateNetworkFailure) {
            return Result.failure(IOException("Simulated network failure during writeBackup to $spreadsheetId"))
        }
        storage[spreadsheetId] = snapshot
        return Result.success(Unit)
    }

    override suspend fun readBackup(spreadsheetId: String): Result<BackupSnapshot> {
        if (simulatedLatencyMs > 0) {
            delay(simulatedLatencyMs)
        }
        if (simulateNetworkFailure) {
            return Result.failure(IOException("Simulated network failure during readBackup from $spreadsheetId"))
        }
        val snapshot = storage[spreadsheetId]
            ?: return Result.failure(IOException("Spreadsheet '$spreadsheetId' not found in MockSheetsTransport"))
        return Result.success(snapshot)
    }

    fun putSnapshotDirectly(spreadsheetId: String, snapshot: BackupSnapshot) {
        storage[spreadsheetId] = snapshot
    }

    fun getSnapshotDirectly(spreadsheetId: String): BackupSnapshot? {
        return storage[spreadsheetId]
    }

    fun clear() {
        storage.clear()
        simulateNetworkFailure = false
        simulatedLatencyMs = 0L
    }
}
