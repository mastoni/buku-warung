package id.skmnetwork.bukuwarung.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPurchaseTransaction(purchase: PurchaseTransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPurchaseItems(items: List<PurchaseItemEntity>)

    @Query("SELECT * FROM purchase_transactions ORDER BY transaction_date DESC")
    fun getAllPurchaseTransactions(): Flow<List<PurchaseTransactionEntity>>

    @Query("SELECT * FROM purchase_transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): PurchaseTransactionEntity?

    @Query("SELECT * FROM purchase_items WHERE transaction_id = :transactionId")
    suspend fun getItemsForPurchase(transactionId: Long): List<PurchaseItemEntity>

    @Query("SELECT SUM(total_amount) FROM purchase_transactions WHERE transaction_date >= :startDate AND transaction_date <= :endDate")
    fun getPurchaseTotal(startDate: Long, endDate: Long): Flow<Long?>

    @Query("SELECT COUNT(*) FROM purchase_transactions WHERE transaction_date >= :startDate AND transaction_date <= :endDate")
    fun getPurchaseCount(startDate: Long, endDate: Long): Flow<Int>

    @Query("SELECT SUM(quantity) FROM purchase_items JOIN purchase_transactions ON purchase_items.transaction_id = purchase_transactions.id WHERE transaction_date >= :startDate AND transaction_date <= :endDate")
    fun getItemsPurchasedTotal(startDate: Long, endDate: Long): Flow<Double?>
}
