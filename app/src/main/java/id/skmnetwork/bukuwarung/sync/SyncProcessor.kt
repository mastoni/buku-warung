package id.skmnetwork.bukuwarung.sync

import id.skmnetwork.bukuwarung.data.local.dao.SyncQueueDao
import id.skmnetwork.bukuwarung.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SyncProcessResult(
    val claimedCount: Int,
    val successCount: Int,
    val failedCount: Int
)

class SyncProcessor(
    private val syncQueueDao: SyncQueueDao,
    private val provider: SyncProvider,
    private val retryPolicy: RetryPolicy = LinearBackoffRetryPolicy()
) {

    suspend fun processBatch(
        batchSize: Int = 20,
        now: Long = System.currentTimeMillis()
    ): SyncProcessResult = withContext(Dispatchers.IO) {
        val affected = syncQueueDao.claimPendingBatch(claimTime = now, limit = batchSize)
        if (affected <= 0) {
            return@withContext SyncProcessResult(0, 0, 0)
        }

        val claimedItems = syncQueueDao.getClaimedBatch(claimTime = now)
        var successCount = 0
        var failedCount = 0

        for (item in claimedItems) {
            val result = processSingleClaimedItem(item, now)
            if (result) {
                successCount++
            } else {
                failedCount++
            }
        }

        SyncProcessResult(
            claimedCount = claimedItems.size,
            successCount = successCount,
            failedCount = failedCount
        )
    }

    suspend fun processSingleItemById(
        id: Long,
        now: Long = System.currentTimeMillis()
    ): Boolean = withContext(Dispatchers.IO) {
        val affected = syncQueueDao.claimSingleItem(id = id, claimTime = now)
        if (affected <= 0) {
            return@withContext false
        }

        val item = syncQueueDao.getItemById(id) ?: return@withContext false
        processSingleClaimedItem(item, now)
    }

    private suspend fun processSingleClaimedItem(
        item: SyncQueueEntity,
        currentTime: Long
    ): Boolean {
        return try {
            val result = provider.processItem(item)
            val now = System.currentTimeMillis()

            if (result.isSuccess) {
                syncQueueDao.markSynced(id = item.id, updatedAt = now)
                true
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Unknown sync error"
                val newAttemptCount = item.attemptCount + 1
                val nextAttempt = retryPolicy.calculateNextAttempt(newAttemptCount, now)
                syncQueueDao.markFailed(
                    id = item.id,
                    error = errorMsg,
                    attemptCount = newAttemptCount,
                    nextAttemptAt = nextAttempt,
                    updatedAt = now
                )
                false
            }
        } catch (e: Exception) {
            val now = System.currentTimeMillis()
            val errorMsg = e.message ?: "Exception during sync processing"
            val newAttemptCount = item.attemptCount + 1
            val nextAttempt = retryPolicy.calculateNextAttempt(newAttemptCount, now)
            syncQueueDao.markFailed(
                id = item.id,
                error = errorMsg,
                attemptCount = newAttemptCount,
                nextAttemptAt = nextAttempt,
                updatedAt = now
            )
            false
        }
    }
}
