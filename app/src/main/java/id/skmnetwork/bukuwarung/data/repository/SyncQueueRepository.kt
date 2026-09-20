package id.skmnetwork.bukuwarung.data.repository

import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

class SyncQueueRepository(
    private val appDatabase: AppDatabase,
    private val businessId: String
) {
    private val syncQueueDao = appDatabase.syncQueueDao()

    val allQueueItems: Flow<List<SyncQueueEntity>> = syncQueueDao.getAllItems(businessId)
    val pendingCount: Flow<Int> = syncQueueDao.getPendingCount(businessId)

    suspend fun getQueueItemBySyncId(syncId: String): SyncQueueEntity? = withContext(Dispatchers.IO) {
        syncQueueDao.getItemBySyncId(syncId, businessId)
    }

    suspend fun getQueueItemById(id: Long): SyncQueueEntity? = withContext(Dispatchers.IO) {
        syncQueueDao.getItemById(id, businessId)
    }

    suspend fun getPendingItems(currentTime: Long = System.currentTimeMillis(), limit: Int = 20): List<SyncQueueEntity> = withContext(Dispatchers.IO) {
        syncQueueDao.getPendingItems(businessId, currentTime, limit)
    }

    suspend fun enqueueEvent(
        entityType: String,
        entityUuid: String,
        operation: String,
        businessId: String = this.businessId,
        deviceId: String = "LEGACY_DEVICE",
        payloadJson: String? = null,
        syncId: String = UUID.randomUUID().toString(),
        now: Long = System.currentTimeMillis()
    ): Long = withContext(Dispatchers.IO) {
        val event = SyncQueueEntity(
            syncId = syncId,
            businessId = businessId,
            deviceId = deviceId,
            entityType = entityType,
            entityUuid = entityUuid,
            operation = operation,
            payloadJson = payloadJson,
            status = "PENDING",
            attemptCount = 0,
            lastError = null,
            nextAttemptAt = now,
            createdAt = now,
            updatedAt = now
        )
        syncQueueDao.insert(event)
    }

    suspend fun retryFailedItem(id: Long, now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        syncQueueDao.resetFailedToPending(id = id, nextAttemptAt = now, updatedAt = now, businessId = businessId)
    }

    suspend fun deleteSynced(olderThanMillis: Long): Int = withContext(Dispatchers.IO) {
        syncQueueDao.deleteSyncedItems(businessId, olderThanMillis)
    }
}
