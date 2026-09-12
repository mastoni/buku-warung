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

    @Query("SELECT * FROM sync_queue ORDER BY created_at ASC")
    fun getAllItems(): Flow<List<SyncQueueEntity>>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'PENDING'")
    fun getPendingCount(): Flow<Int>

    @Query("SELECT * FROM sync_queue WHERE sync_id = :syncId")
    suspend fun getItemBySyncId(syncId: String): SyncQueueEntity?

    @Query("SELECT * FROM sync_queue WHERE id = :id")
    suspend fun getItemById(id: Long): SyncQueueEntity?

    @Query(
        """
        SELECT * FROM sync_queue 
        WHERE status = 'PENDING' AND next_attempt_at <= :currentTime 
        ORDER BY created_at ASC 
        LIMIT :limit
        """
    )
    suspend fun getPendingItems(currentTime: Long, limit: Int = 20): List<SyncQueueEntity>

    @Query(
        """
        UPDATE sync_queue 
        SET status = 'PROCESSING', updated_at = :claimTime 
        WHERE id = :id AND status = 'PENDING' AND next_attempt_at <= :claimTime
        """
    )
    suspend fun claimSingleItem(id: Long, claimTime: Long): Int

    @Query(
        """
        UPDATE sync_queue 
        SET status = 'PROCESSING', updated_at = :claimTime 
        WHERE id IN (
            SELECT id FROM sync_queue 
            WHERE status = 'PENDING' AND next_attempt_at <= :claimTime 
            ORDER BY created_at ASC 
            LIMIT :limit
        )
        """
    )
    suspend fun claimPendingBatch(claimTime: Long, limit: Int = 20): Int

    @Query("SELECT * FROM sync_queue WHERE status = 'PROCESSING' AND updated_at = :claimTime ORDER BY created_at ASC")
    suspend fun getClaimedBatch(claimTime: Long): List<SyncQueueEntity>

    @Query("UPDATE sync_queue SET status = 'SYNCED', updated_at = :updatedAt WHERE id = :id")
    suspend fun markSynced(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query(
        """
        UPDATE sync_queue 
        SET status = 'FAILED', last_error = :error, attempt_count = :attemptCount, next_attempt_at = :nextAttemptAt, updated_at = :updatedAt 
        WHERE id = :id
        """
    )
    suspend fun markFailed(
        id: Long,
        error: String,
        attemptCount: Int,
        nextAttemptAt: Long,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query(
        """
        UPDATE sync_queue 
        SET status = 'PENDING', next_attempt_at = :nextAttemptAt, updated_at = :updatedAt 
        WHERE id = :id
        """
    )
    suspend fun resetFailedToPending(
        id: Long,
        nextAttemptAt: Long = System.currentTimeMillis(),
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM sync_queue WHERE status = 'SYNCED' AND updated_at < :olderThanMillis")
    suspend fun deleteSyncedItems(olderThanMillis: Long): Int
}
