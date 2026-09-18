package id.skmnetwork.bukuwarung.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import id.skmnetwork.bukuwarung.data.local.entity.DigitalTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DigitalTransactionDao {
    @Insert
    suspend fun insert(transaction: DigitalTransactionEntity): Long

    @Update
    suspend fun update(transaction: DigitalTransactionEntity)

    @Query("SELECT * FROM digital_transactions WHERE id = :id")
    suspend fun getById(id: Long): DigitalTransactionEntity?

    @Query("SELECT * FROM digital_transactions WHERE sale_item_id = :saleItemId LIMIT 1")
    suspend fun getBySaleItemId(saleItemId: Long): DigitalTransactionEntity?

    @Query("SELECT * FROM digital_transactions WHERE status = :status")
    fun getByStatus(status: String): Flow<List<DigitalTransactionEntity>>
}
