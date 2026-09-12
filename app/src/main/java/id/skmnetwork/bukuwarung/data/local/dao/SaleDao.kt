package id.skmnetwork.bukuwarung.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import id.skmnetwork.bukuwarung.data.local.entity.SaleItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SaleDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransaction(transaction: SaleTransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSaleItems(items: List<SaleItemEntity>)

    @Query("SELECT * FROM sales_transactions ORDER BY transaction_date DESC")
    fun getAllTransactions(): Flow<List<SaleTransactionEntity>>

    @Query("SELECT * FROM sales_transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): SaleTransactionEntity?

    @Query("SELECT * FROM sale_items WHERE transaction_id = :transactionId")
    suspend fun getItemsForTransaction(transactionId: Long): List<SaleItemEntity>

    @Query("SELECT SUM(total_amount) FROM sales_transactions WHERE transaction_date >= :startOfDay AND transaction_date <= :endOfDay")
    fun getTodaySalesTotal(startOfDay: Long, endOfDay: Long): Flow<Long?>

    @Query("SELECT SUM(total_amount) FROM sales_transactions WHERE transaction_date >= :startDate AND transaction_date <= :endDate")
    fun getSalesTotal(startDate: Long, endDate: Long): Flow<Long?>

    @Query("SELECT COUNT(*) FROM sales_transactions WHERE transaction_date >= :startDate AND transaction_date <= :endDate")
    fun getSalesCount(startDate: Long, endDate: Long): Flow<Int>

    @Query("SELECT SUM(quantity) FROM sale_items JOIN sales_transactions ON sale_items.transaction_id = sales_transactions.id WHERE transaction_date >= :startDate AND transaction_date <= :endDate")
    fun getItemsSoldTotal(startDate: Long, endDate: Long): Flow<Double?>

    @Query("SELECT SUM(total_amount) FROM sales_transactions WHERE payment_method = 'CASH' AND transaction_date >= :startDate AND transaction_date <= :endDate")
    fun getCashSalesTotal(startDate: Long, endDate: Long): Flow<Long?>

    @Query("SELECT SUM(total_amount) FROM sales_transactions WHERE payment_method = 'CREDIT' AND transaction_date >= :startDate AND transaction_date <= :endDate")
    fun getCreditSalesTotal(startDate: Long, endDate: Long): Flow<Long?>

    @Query("SELECT SUM(CAST(quantity * purchase_price AS INTEGER)) FROM sale_items JOIN sales_transactions ON sale_items.transaction_id = sales_transactions.id WHERE transaction_date >= :startDate AND transaction_date <= :endDate")
    fun getSaleCogsTotal(startDate: Long, endDate: Long): Flow<Long?>

    @Query("""
        SELECT 
            product_name AS productName, 
            SUM(quantity) AS totalQuantity, 
            SUM(subtotal) AS totalRevenue 
        FROM sale_items 
        JOIN sales_transactions ON sale_items.transaction_id = sales_transactions.id 
        WHERE transaction_date >= :startDate AND transaction_date <= :endDate 
        GROUP BY product_id, product_name 
        ORDER BY totalQuantity DESC 
        LIMIT :limit
    """)
    fun getTopSellingProducts(startDate: Long, endDate: Long, limit: Int = 5): Flow<List<TopProductSummary>>
}

data class TopProductSummary(
    val productName: String,
    val totalQuantity: Double,
    val totalRevenue: Long
)
