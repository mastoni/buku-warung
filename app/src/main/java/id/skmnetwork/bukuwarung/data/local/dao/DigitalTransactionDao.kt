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

    @Query("SELECT * FROM digital_transactions WHERE id = :id AND business_id = :businessId")
    suspend fun getById(id: Long, businessId: String): DigitalTransactionEntity?

    @Query("SELECT * FROM digital_transactions WHERE sale_item_id = :saleItemId AND business_id = :businessId LIMIT 1")
    suspend fun getBySaleItemId(saleItemId: Long, businessId: String): DigitalTransactionEntity?

    @Query("SELECT * FROM digital_transactions WHERE sale_item_id IN (:saleItemIds) AND business_id = :businessId")
    suspend fun getBySaleItemIds(saleItemIds: List<Long>, businessId: String): List<DigitalTransactionEntity>

    @Query("SELECT * FROM digital_transactions WHERE business_id = :businessId ORDER BY created_at DESC")
    fun getByBusinessId(businessId: String): Flow<List<DigitalTransactionEntity>>

    @Query("SELECT * FROM digital_transactions WHERE sale_item_id IS NULL AND business_id = :businessId ORDER BY created_at DESC")
    fun getStandaloneTransactions(businessId: String): Flow<List<DigitalTransactionEntity>>

    @Query("SELECT * FROM digital_transactions WHERE transaction_number = :transactionNumber AND business_id = :businessId LIMIT 1")
    suspend fun getByTransactionNumber(transactionNumber: String, businessId: String): DigitalTransactionEntity?

    @Query("SELECT * FROM digital_transactions WHERE customer_id = :customerId AND business_id = :businessId ORDER BY created_at DESC")
    fun getByCustomerId(customerId: Long, businessId: String): Flow<List<DigitalTransactionEntity>>

    @Query("SELECT * FROM digital_transactions WHERE status = :status AND business_id = :businessId ORDER BY created_at DESC")
    fun getByStatus(status: String, businessId: String): Flow<List<DigitalTransactionEntity>>

    @Query("SELECT * FROM digital_transactions WHERE uuid = :uuid AND business_id = :businessId")
    suspend fun getByUuid(uuid: String, businessId: String): DigitalTransactionEntity?
}
