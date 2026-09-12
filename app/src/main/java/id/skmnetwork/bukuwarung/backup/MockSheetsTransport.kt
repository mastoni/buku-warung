package id.skmnetwork.bukuwarung.backup

import kotlinx.coroutines.delay
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Foundation Step 5 — Mock Sheets Transport
 * In-memory thread-safe transport for deterministic, offline testing.
 */
class MockSheetsTransport : SheetsBackupTransport {

    private val storage = ConcurrentHashMap<String, BackupSnapshot>()

    var simulateNetworkFailure: Boolean = false
    var simulatedLatencyMs: Long = 0L

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
