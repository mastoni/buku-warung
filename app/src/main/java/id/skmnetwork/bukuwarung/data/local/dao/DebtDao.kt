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
}
