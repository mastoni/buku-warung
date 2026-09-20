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

    @Query("SELECT * FROM sale_return_transactions WHERE business_id = :businessId ORDER BY return_date DESC")
    fun getAllReturns(businessId: String): Flow<List<SaleReturnTransactionEntity>>

    @Query("SELECT * FROM sale_return_transactions WHERE business_id = :businessId ORDER BY return_date DESC")
    suspend fun getAllReturnsList(businessId: String): List<SaleReturnTransactionEntity>

    @Query("SELECT * FROM sale_return_transactions WHERE id = :id AND business_id = :businessId")
    suspend fun getReturnById(id: Long, businessId: String): SaleReturnTransactionEntity?

    @Query("SELECT * FROM sale_return_transactions WHERE uuid = :uuid AND business_id = :businessId LIMIT 1")
    suspend fun getReturnByUuid(uuid: String, businessId: String): SaleReturnTransactionEntity?

    @Query("SELECT * FROM sale_return_transactions WHERE business_id = :businessId AND sale_transaction_id = :saleTransactionId ORDER BY return_date DESC")
    fun getReturnsForSale(businessId: String, saleTransactionId: Long): Flow<List<SaleReturnTransactionEntity>>

    @Query("SELECT * FROM sale_return_transactions WHERE business_id = :businessId AND sale_transaction_id = :saleTransactionId ORDER BY return_date DESC")
    suspend fun getReturnsListForSale(businessId: String, saleTransactionId: Long): List<SaleReturnTransactionEntity>

    @Query("SELECT * FROM sale_return_items WHERE return_transaction_id = :returnTransactionId AND business_id = :businessId")
    suspend fun getItemsForReturn(businessId: String, returnTransactionId: Long): List<SaleReturnItemEntity>

    @Query("SELECT SUM(quantity) FROM sale_return_items WHERE sale_item_id = :saleItemId AND business_id = :businessId")
    suspend fun getReturnedQuantityForSaleItem(saleItemId: Long, businessId: String): Double?

    @Query("""
        SELECT sri.*
        FROM sale_return_items sri
        JOIN sale_return_transactions srt ON sri.return_transaction_id = srt.id
        WHERE srt.sale_transaction_id = :saleId AND srt.business_id = :businessId
    """)
    suspend fun getReturnItemsForSale(saleId: Long, businessId: String): List<SaleReturnItemEntity>

    @Query("SELECT SUM(total_refund_amount) FROM sale_return_transactions WHERE business_id = :businessId AND sale_transaction_id = :saleTransactionId")
    suspend fun getTotalReturnedForSale(businessId: String, saleTransactionId: Long): Long?

    @Query("SELECT SUM(total_refund_amount) FROM sale_return_transactions WHERE business_id = :businessId AND return_date >= :startDate AND return_date <= :endDate")
    fun getSalesReturnTotal(businessId: String, startDate: Long, endDate: Long): Flow<Long?>

    @Query("SELECT SUM(quantity) FROM sale_return_items JOIN sale_return_transactions ON sale_return_items.return_transaction_id = sale_return_transactions.id WHERE sale_return_transactions.business_id = :businessId AND return_date >= :startDate AND return_date <= :endDate")
    fun getItemsReturnedTotal(businessId: String, startDate: Long, endDate: Long): Flow<Double?>

    @Query("SELECT COUNT(*) FROM sale_return_transactions WHERE business_id = :businessId AND return_date >= :startDate AND return_date <= :endDate")
    fun getReturnCount(businessId: String, startDate: Long, endDate: Long): Flow<Int>

    @Query("SELECT SUM(CAST(quantity * purchase_price AS INTEGER)) FROM sale_return_items JOIN sale_return_transactions ON sale_return_items.return_transaction_id = sale_return_transactions.id WHERE sale_return_transactions.business_id = :businessId AND return_date >= :startDate AND return_date <= :endDate")
    fun getReturnCogsTotal(businessId: String, startDate: Long, endDate: Long): Flow<Long?>

    @Query("SELECT SUM(taxable_base_snapshot) FROM sale_return_transactions WHERE business_id = :businessId AND return_date >= :startDate AND return_date <= :endDate")
    fun getReturnsTaxableBaseTotal(businessId: String, startDate: Long, endDate: Long): Flow<Long?>

    @Query("SELECT SUM(tax_amount_snapshot) FROM sale_return_transactions WHERE business_id = :businessId AND return_date >= :startDate AND return_date <= :endDate")
    fun getReturnsTaxAmountTotal(businessId: String, startDate: Long, endDate: Long): Flow<Long?>

    @Query("""
        SELECT 
            COALESCE(NULLIF(product_uuid, ''), CAST(product_id AS TEXT)) AS productUuid,
            product_id AS productId,
            product_name AS productName,
            SUM(quantity) AS returnedQuantity,
            SUM(subtotal) AS returnedRevenue,
            SUM(CAST(quantity * purchase_price AS INTEGER)) AS returnedCogs
        FROM sale_return_items
        JOIN sale_return_transactions ON sale_return_items.return_transaction_id = sale_return_transactions.id
        WHERE sale_return_transactions.business_id = :businessId AND return_date >= :startDate AND return_date <= :endDate
        GROUP BY COALESCE(NULLIF(product_uuid, ''), CAST(product_id AS TEXT)), product_id, product_name
    """)
    fun getProductReturnsSummaryByDateRange(businessId: String, startDate: Long, endDate: Long): Flow<List<ProductReturnSummaryItem>>
}

data class ProductReturnSummaryItem(
    val productUuid: String,
    val productId: Long,
    val productName: String,
    val returnedQuantity: Double,
    val returnedRevenue: Long,
    val returnedCogs: Long
)
