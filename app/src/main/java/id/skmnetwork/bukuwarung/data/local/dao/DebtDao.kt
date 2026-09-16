package id.skmnetwork.bukuwarung.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import id.skmnetwork.bukuwarung.data.local.entity.DebtEntity
import id.skmnetwork.bukuwarung.data.local.entity.DebtPaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DebtDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDebt(debt: DebtEntity): Long

    @Update
    suspend fun updateDebt(debt: DebtEntity)

    @Query("SELECT * FROM debts WHERE id = :id")
    suspend fun getDebtById(id: Long): DebtEntity?

    @Query("SELECT * FROM debts WHERE sale_transaction_id = :saleId LIMIT 1")
    suspend fun getDebtBySaleId(saleId: Long): DebtEntity?

    @Query("SELECT * FROM debts WHERE sale_uuid = :saleUuid LIMIT 1")
    suspend fun getDebtBySaleUuid(saleUuid: String): DebtEntity?

    @Query("SELECT * FROM debts WHERE customer_id = :customerId ORDER BY created_at DESC")
    fun getDebtsForCustomer(customerId: Long): Flow<List<DebtEntity>>

    @Query("SELECT * FROM debts WHERE customer_id = :customerId AND status = 'OPEN' ORDER BY created_at ASC")
    suspend fun getOpenDebtsForCustomerList(customerId: Long): List<DebtEntity>

    @Query("SELECT * FROM debts WHERE status = 'OPEN' ORDER BY created_at DESC")
    fun getAllOpenDebts(): Flow<List<DebtEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDebtPayment(payment: DebtPaymentEntity): Long

    @Query("SELECT * FROM debt_payments WHERE debt_id = :debtId ORDER BY payment_date DESC")
    fun getPaymentsForDebt(debtId: Long): Flow<List<DebtPaymentEntity>>

    @Query("SELECT * FROM debt_payments WHERE debt_id = :debtId ORDER BY payment_date DESC")
    suspend fun getPaymentsListForDebt(debtId: Long): List<DebtPaymentEntity>

    @Query("SELECT SUM(total_debt - paid_amount) FROM debts WHERE customer_id = :customerId AND status = 'OPEN'")
    fun getTotalOutstandingForCustomer(customerId: Long): Flow<Long?>

    @Query("SELECT SUM(total_debt - paid_amount) FROM debts WHERE status = 'OPEN'")
    fun getTotalOutstandingDebt(): Flow<Long?>

    @Query("SELECT SUM(paid_amount) FROM debts")
    fun getTotalPaidDebt(): Flow<Long?>

    @Query("SELECT SUM(total_debt) FROM debts")
    fun getTotalDebtCreated(): Flow<Long?>

    @Query("SELECT COUNT(*) FROM debts WHERE status = 'OPEN'")
    fun getOpenDebtsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM debts WHERE status = 'PAID'")
    fun getPaidDebtsCount(): Flow<Int>

    @Query("""
        SELECT 
            d.id AS debtId,
            d.uuid AS uuid,
            d.customer_id AS customerId,
            d.sale_transaction_id AS saleTransactionId,
            c.name AS customerName,
            c.phone AS customerPhone,
            c.address AS customerAddress,
            st.transaction_number AS transactionNumber,
            d.total_debt AS totalDebt,
            d.paid_amount AS paidAmount,
            d.status AS status,
            d.created_at AS createdAt,
            d.updated_at AS updatedAt
        FROM debts d
        LEFT JOIN customers c ON d.customer_id = c.id
        LEFT JOIN sales_transactions st ON d.sale_transaction_id = st.id
        WHERE d.status = 'OPEN'
        ORDER BY d.created_at DESC, d.id DESC
    """)
    fun getOpenDebtsWithCustomer(): Flow<List<DebtWithCustomerItem>>

    @Query("""
        SELECT 
            d.id AS debtId,
            d.uuid AS uuid,
            d.customer_id AS customerId,
            d.sale_transaction_id AS saleTransactionId,
            c.name AS customerName,
            c.phone AS customerPhone,
            c.address AS customerAddress,
            st.transaction_number AS transactionNumber,
            d.total_debt AS totalDebt,
            d.paid_amount AS paidAmount,
            d.status AS status,
            d.created_at AS createdAt,
            d.updated_at AS updatedAt
        FROM debts d
        LEFT JOIN customers c ON d.customer_id = c.id
        LEFT JOIN sales_transactions st ON d.sale_transaction_id = st.id
        WHERE d.created_at >= :startDate AND d.created_at <= :endDate
        ORDER BY d.created_at DESC, d.id DESC
    """)
    fun getDebtsWithCustomerByDateRange(startDate: Long, endDate: Long): Flow<List<DebtWithCustomerItem>>
}

data class DebtWithCustomerItem(
    val debtId: Long,
    val uuid: String,
    val customerId: Long,
    val saleTransactionId: Long?,
    val customerName: String?,
    val customerPhone: String?,
    val customerAddress: String?,
    val transactionNumber: String?,
    val totalDebt: Long,
    val paidAmount: Long,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long
)

