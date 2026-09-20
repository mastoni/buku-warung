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

    @Query("SELECT * FROM purchase_transactions WHERE business_id = :businessId ORDER BY transaction_date DESC")
    fun getAllPurchaseTransactions(businessId: String): Flow<List<PurchaseTransactionEntity>>

    @Query("SELECT * FROM purchase_transactions WHERE id = :id AND business_id = :businessId")
    suspend fun getTransactionById(id: Long, businessId: String): PurchaseTransactionEntity?

    @Query("SELECT * FROM purchase_items WHERE transaction_id = :transactionId AND business_id = :businessId")
    suspend fun getItemsForPurchase(transactionId: Long, businessId: String): List<PurchaseItemEntity>

    @Query("SELECT SUM(total_amount) FROM purchase_transactions WHERE business_id = :businessId AND transaction_date >= :startDate AND transaction_date <= :endDate")
    fun getPurchaseTotal(businessId: String, startDate: Long, endDate: Long): Flow<Long?>

    @Query("SELECT SUM(taxable_base_snapshot) FROM purchase_transactions WHERE business_id = :businessId AND transaction_date >= :startDate AND transaction_date <= :endDate")
    fun getPurchasesTaxableBaseTotal(businessId: String, startDate: Long, endDate: Long): Flow<Long?>

    @Query("SELECT SUM(tax_amount_snapshot) FROM purchase_transactions WHERE business_id = :businessId AND transaction_date >= :startDate AND transaction_date <= :endDate")
    fun getPurchasesTaxAmountTotal(businessId: String, startDate: Long, endDate: Long): Flow<Long?>

    @Query("SELECT COUNT(*) FROM purchase_transactions WHERE business_id = :businessId AND transaction_date >= :startDate AND transaction_date <= :endDate")
    fun getPurchaseCount(businessId: String, startDate: Long, endDate: Long): Flow<Int>

    @Query("SELECT SUM(quantity) FROM purchase_items JOIN purchase_transactions ON purchase_items.transaction_id = purchase_transactions.id WHERE purchase_transactions.business_id = :businessId AND transaction_date >= :startDate AND transaction_date <= :endDate")
    fun getItemsPurchasedTotal(businessId: String, startDate: Long, endDate: Long): Flow<Double?>

    @Query("""
        SELECT 
            p.id AS id,
            p.uuid AS uuid,
            p.transaction_number AS transactionNumber,
            p.transaction_date AS transactionDate,
            p.total_amount AS totalAmount,
            p.payment_method AS paymentMethod,
            p.taxable_base_snapshot AS taxableBaseSnapshot,
            p.tax_amount_snapshot AS taxAmountSnapshot,
            p.supplier_id AS supplierId,
            s.name AS supplierName
        FROM purchase_transactions p
        LEFT JOIN suppliers s ON p.supplier_id = s.id
        WHERE p.business_id = :businessId AND p.transaction_date >= :startDate AND p.transaction_date <= :endDate
        ORDER BY p.transaction_date DESC
    """)
    fun getPurchasesWithSupplierByDateRange(businessId: String, startDate: Long, endDate: Long): Flow<List<PurchaseWithSupplierItem>>
}

data class PurchaseWithSupplierItem(
    val id: Long,
    val uuid: String,
    val transactionNumber: String,
    val transactionDate: Long,
    val totalAmount: Long,
    val paymentMethod: String,
    val taxableBaseSnapshot: Long,
    val taxAmountSnapshot: Long,
    val supplierId: Long?,
    val supplierName: String?
)
