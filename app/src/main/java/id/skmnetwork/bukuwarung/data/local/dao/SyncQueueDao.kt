package id.skmnetwork.bukuwarung.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import id.skmnetwork.bukuwarung.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: SyncQueueEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(items: List<SyncQueueEntity>)

    @Query("SELECT * FROM sync_queue WHERE business_id = :businessId ORDER BY created_at ASC")
    fun getAllItems(businessId: String): Flow<List<SyncQueueEntity>>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE business_id = :businessId AND status = 'PENDING'")
    fun getPendingCount(businessId: String): Flow<Int>

    @Query("SELECT * FROM sync_queue WHERE sync_id = :syncId AND business_id = :businessId")
    suspend fun getItemBySyncId(syncId: String, businessId: String): SyncQueueEntity?

    @Query("SELECT * FROM sync_queue WHERE id = :id AND business_id = :businessId")
    suspend fun getItemById(id: Long, businessId: String): SyncQueueEntity?

    @Query(
        """
        SELECT * FROM sync_queue 
        WHERE business_id = :businessId AND status = 'PENDING' AND next_attempt_at <= :currentTime 
        ORDER BY created_at ASC 
        LIMIT :limit
        """
    )
    suspend fun getPendingItems(businessId: String, currentTime: Long, limit: Int = 20): List<SyncQueueEntity>

    @Query(
        """
        UPDATE sync_queue 
        SET status = 'PROCESSING', updated_at = :claimTime 
        WHERE id = :id AND business_id = :businessId AND status = 'PENDING' AND next_attempt_at <= :claimTime
        """
    )
    suspend fun claimSingleItem(id: Long, claimTime: Long, businessId: String): Int

    @Query(
        """
        UPDATE sync_queue 
        SET status = 'PROCESSING', updated_at = :claimTime 
        WHERE id IN (
            SELECT id FROM sync_queue 
            WHERE business_id = :businessId AND status = 'PENDING' AND next_attempt_at <= :claimTime 
            ORDER BY created_at ASC 
            LIMIT :limit
        )
        """
    )
    suspend fun claimPendingBatch(claimTime: Long, limit: Int = 20, businessId: String): Int

    @Query("SELECT * FROM sync_queue WHERE business_id = :businessId AND status = 'PROCESSING' AND updated_at = :claimTime ORDER BY created_at ASC")
    suspend fun getClaimedBatch(businessId: String, claimTime: Long): List<SyncQueueEntity>

    @Query("UPDATE sync_queue SET status = 'SYNCED', updated_at = :updatedAt WHERE id = :id AND business_id = :businessId")
    suspend fun markSynced(id: Long, updatedAt: Long = System.currentTimeMillis(), businessId: String)

    @Query(
        """
        UPDATE sync_queue 
        SET status = 'FAILED', last_error = :error, attempt_count = :attemptCount, next_attempt_at = :nextAttemptAt, updated_at = :updatedAt 
        WHERE id = :id AND business_id = :businessId
        """
    )
    suspend fun markFailed(
        id: Long,
        error: String,
        attemptCount: Int,
        nextAttemptAt: Long,
        updatedAt: Long = System.currentTimeMillis(),
        businessId: String
    )

    @Query(
        """
        UPDATE sync_queue 
        SET status = 'PENDING', next_attempt_at = :nextAttemptAt, updated_at = :updatedAt 
        WHERE id = :id AND business_id = :businessId
        """
    )
    suspend fun resetFailedToPending(
        id: Long,
        nextAttemptAt: Long = System.currentTimeMillis(),
        updatedAt: Long = System.currentTimeMillis(),
        businessId: String
    )

    @Query("DELETE FROM sync_queue WHERE business_id = :businessId AND status = 'SYNCED' AND updated_at < :olderThanMillis")
    suspend fun deleteSyncedItems(businessId: String, olderThanMillis: Long): Int
}
