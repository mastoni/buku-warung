package id.skmnetwork.bukuwarung.sync

import id.skmnetwork.bukuwarung.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MockSyncProvider(
    override val providerName: String = "MOCK_SYNC_PROVIDER"
) : SyncProvider {

    private val mutex = Mutex()
    private val _processedItems = mutableListOf<SyncQueueEntity>()

    var simulateFailure: Boolean = false
    var failureErrorMessage: String = "Simulated Sync Provider Failure"
    var processingDelayMillis: Long = 0L

    val processedItems: List<SyncQueueEntity>
        get() = _processedItems.toList()

    val totalProcessed: Int
        get() = _processedItems.size

    override suspend fun processItem(item: SyncQueueEntity): Result<Unit> = mutex.withLock {
        if (processingDelayMillis > 0) {
            kotlinx.coroutines.delay(processingDelayMillis)
        }

        if (simulateFailure) {
            Result.failure(IllegalStateException(failureErrorMessage))
        } else {
            _processedItems.add(item)
            Result.success(Unit)
        }
    }

    fun clear() {
        _processedItems.clear()
        simulateFailure = false
        failureErrorMessage = "Simulated Sync Provider Failure"
        processingDelayMillis = 0L
    }
}
