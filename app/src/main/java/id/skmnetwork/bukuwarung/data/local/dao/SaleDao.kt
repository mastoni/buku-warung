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
    suspend fun insertSaleItems(items: List<SaleItemEntity>): List<Long>

    @Query("SELECT * FROM sales_transactions WHERE business_id = :businessId ORDER BY transaction_date DESC")
    fun getAllTransactions(businessId: String): Flow<List<SaleTransactionEntity>>

    @Query("""
        SELECT 
            s.id AS id,
            s.uuid AS uuid,
            s.business_id AS businessId,
            s.device_id AS deviceId,
            s.transaction_number AS transactionNumber,
            s.transaction_date AS transactionDate,
            s.total_amount AS totalAmount,
            s.payment_method AS paymentMethod,
            s.discount_amount AS discountAmount,
            s.taxable_base_snapshot AS taxableBaseSnapshot,
            s.tax_amount_snapshot AS taxAmountSnapshot,
            s.created_at AS createdAt,
            s.customer_id AS customerId,
            c.name AS customerName
        FROM sales_transactions s
        LEFT JOIN customers c ON s.customer_id = c.id
        WHERE s.business_id = :businessId AND s.transaction_date >= :startDate AND s.transaction_date <= :endDate
        ORDER BY s.transaction_date DESC
    """)
    fun getSalesWithCustomerByDateRange(businessId: String, startDate: Long, endDate: Long): Flow<List<SaleWithCustomerItem>>

    @Query("SELECT * FROM sales_transactions WHERE id = :id AND business_id = :businessId")
    suspend fun getTransactionById(id: Long, businessId: String): SaleTransactionEntity?

    @Query("SELECT * FROM sale_items WHERE transaction_id = :transactionId AND business_id = :businessId")
    suspend fun getItemsForTransaction(transactionId: Long, businessId: String): List<SaleItemEntity>

    @Query("SELECT SUM(total_amount) FROM sales_transactions WHERE business_id = :businessId AND transaction_date >= :startOfDay AND transaction_date <= :endOfDay")
    fun getTodaySalesTotal(businessId: String, startOfDay: Long, endOfDay: Long): Flow<Long?>

    @Query("SELECT SUM(total_amount) FROM sales_transactions WHERE business_id = :businessId AND transaction_date >= :startDate AND transaction_date <= :endDate")
    fun getSalesTotal(businessId: String, startDate: Long, endDate: Long): Flow<Long?>

    @Query("SELECT SUM(taxable_base_snapshot) FROM sales_transactions WHERE business_id = :businessId AND transaction_date >= :startDate AND transaction_date <= :endDate")
    fun getSalesTaxableBaseTotal(businessId: String, startDate: Long, endDate: Long): Flow<Long?>

    @Query("SELECT SUM(tax_amount_snapshot) FROM sales_transactions WHERE business_id = :businessId AND transaction_date >= :startDate AND transaction_date <= :endDate")
    fun getSalesTaxAmountTotal(businessId: String, startDate: Long, endDate: Long): Flow<Long?>

    @Query("SELECT COUNT(*) FROM sales_transactions WHERE business_id = :businessId AND transaction_date >= :startDate AND transaction_date <= :endDate")
    fun getSalesCount(businessId: String, startDate: Long, endDate: Long): Flow<Int>

    @Query("SELECT SUM(quantity) FROM sale_items JOIN sales_transactions ON sale_items.transaction_id = sales_transactions.id WHERE sales_transactions.business_id = :businessId AND transaction_date >= :startDate AND transaction_date <= :endDate")
    fun getItemsSoldTotal(businessId: String, startDate: Long, endDate: Long): Flow<Double?>

    @Query("SELECT SUM(total_amount) FROM sales_transactions WHERE business_id = :businessId AND payment_method = 'CASH' AND transaction_date >= :startDate AND transaction_date <= :endDate")
    fun getCashSalesTotal(businessId: String, startDate: Long, endDate: Long): Flow<Long?>

    @Query("SELECT SUM(total_amount) FROM sales_transactions WHERE business_id = :businessId AND payment_method = 'CREDIT' AND transaction_date >= :startDate AND transaction_date <= :endDate")
    fun getCreditSalesTotal(businessId: String, startDate: Long, endDate: Long): Flow<Long?>

    @Query("SELECT SUM(CAST(quantity * purchase_price AS INTEGER)) FROM sale_items JOIN sales_transactions ON sale_items.transaction_id = sales_transactions.id WHERE sales_transactions.business_id = :businessId AND transaction_date >= :startDate AND transaction_date <= :endDate")
    fun getSaleCogsTotal(businessId: String, startDate: Long, endDate: Long): Flow<Long?>

    @Query("""
        SELECT 
            product_name AS productName, 
            SUM(quantity) AS totalQuantity, 
            SUM(subtotal) AS totalRevenue 
        FROM sale_items 
        JOIN sales_transactions ON sale_items.transaction_id = sales_transactions.id 
        WHERE sales_transactions.business_id = :businessId AND transaction_date >= :startDate AND transaction_date <= :endDate 
        GROUP BY product_id, product_name 
        ORDER BY totalQuantity DESC 
        LIMIT :limit
    """)
    fun getTopSellingProducts(businessId: String, startDate: Long, endDate: Long, limit: Int = 5): Flow<List<TopProductSummary>>

    @Query("""
        SELECT 
            COALESCE(NULLIF(product_uuid, ''), CAST(product_id AS TEXT)) AS productUuid,
            product_id AS productId,
            product_name AS productName,
            SUM(quantity) AS totalQuantity,
            SUM(subtotal) AS totalRevenue,
            SUM(CAST(quantity * purchase_price AS INTEGER)) AS totalCogs
        FROM sale_items
        JOIN sales_transactions ON sale_items.transaction_id = sales_transactions.id
        WHERE sales_transactions.business_id = :businessId AND transaction_date >= :startDate AND transaction_date <= :endDate
        GROUP BY COALESCE(NULLIF(product_uuid, ''), CAST(product_id AS TEXT)), product_id, product_name
    """)
    fun getProductSalesSummaryByDateRange(businessId: String, startDate: Long, endDate: Long): Flow<List<ProductSalesSummaryItem>>
}

data class ProductSalesSummaryItem(
    val productUuid: String,
    val productId: Long,
    val productName: String,
    val totalQuantity: Double,
    val totalRevenue: Long,
    val totalCogs: Long
)

data class TopProductSummary(
    val productName: String,
    val totalQuantity: Double,
    val totalRevenue: Long
)

data class SaleWithCustomerItem(
    val id: Long,
    val uuid: String,
    val businessId: String,
    val deviceId: String,
    val transactionNumber: String,
    val transactionDate: Long,
    val totalAmount: Long,
    val paymentMethod: String,
    val discountAmount: Long = 0L,
    val taxableBaseSnapshot: Long,
    val taxAmountSnapshot: Long,
    val createdAt: Long,
    val customerId: Long?,
    val customerName: String?
)
