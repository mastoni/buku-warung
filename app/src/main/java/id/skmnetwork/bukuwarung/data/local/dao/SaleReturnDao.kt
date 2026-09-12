package id.skmnetwork.bukuwarung.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import id.skmnetwork.bukuwarung.data.local.entity.SaleReturnItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleReturnTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SaleReturnDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReturnTransaction(transaction: SaleReturnTransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertReturnItems(items: List<SaleReturnItemEntity>)

    @Query("SELECT * FROM sale_return_transactions ORDER BY return_date DESC")
    fun getAllReturns(): Flow<List<SaleReturnTransactionEntity>>

    @Query("SELECT * FROM sale_return_transactions ORDER BY return_date DESC")
    suspend fun getAllReturnsList(): List<SaleReturnTransactionEntity>

    @Query("SELECT * FROM sale_return_transactions WHERE id = :id")
    suspend fun getReturnById(id: Long): SaleReturnTransactionEntity?

    @Query("SELECT * FROM sale_return_transactions WHERE uuid = :uuid LIMIT 1")
    suspend fun getReturnByUuid(uuid: String): SaleReturnTransactionEntity?

    @Query("SELECT * FROM sale_return_transactions WHERE sale_transaction_id = :saleTransactionId ORDER BY return_date DESC")
    fun getReturnsForSale(saleTransactionId: Long): Flow<List<SaleReturnTransactionEntity>>

    @Query("SELECT * FROM sale_return_transactions WHERE sale_transaction_id = :saleTransactionId ORDER BY return_date DESC")
    suspend fun getReturnsListForSale(saleTransactionId: Long): List<SaleReturnTransactionEntity>

    @Query("SELECT * FROM sale_return_items WHERE return_transaction_id = :returnTransactionId")
    suspend fun getItemsForReturn(returnTransactionId: Long): List<SaleReturnItemEntity>

    @Query("SELECT SUM(quantity) FROM sale_return_items WHERE sale_item_id = :saleItemId")
    suspend fun getReturnedQuantityForSaleItem(saleItemId: Long): Double?

    @Query("SELECT SUM(total_refund_amount) FROM sale_return_transactions WHERE sale_transaction_id = :saleTransactionId")
    suspend fun getTotalReturnedForSale(saleTransactionId: Long): Long?

    @Query("SELECT SUM(total_refund_amount) FROM sale_return_transactions WHERE return_date >= :startDate AND return_date <= :endDate")
    fun getSalesReturnTotal(startDate: Long, endDate: Long): Flow<Long?>

    @Query("SELECT SUM(quantity) FROM sale_return_items JOIN sale_return_transactions ON sale_return_items.return_transaction_id = sale_return_transactions.id WHERE return_date >= :startDate AND return_date <= :endDate")
    fun getItemsReturnedTotal(startDate: Long, endDate: Long): Flow<Double?>

    @Query("SELECT COUNT(*) FROM sale_return_transactions WHERE return_date >= :startDate AND return_date <= :endDate")
    fun getReturnCount(startDate: Long, endDate: Long): Flow<Int>

    @Query("SELECT SUM(CAST(quantity * purchase_price AS INTEGER)) FROM sale_return_items JOIN sale_return_transactions ON sale_return_items.return_transaction_id = sale_return_transactions.id WHERE return_date >= :startDate AND return_date <= :endDate")
    fun getReturnCogsTotal(startDate: Long, endDate: Long): Flow<Long?>
}
