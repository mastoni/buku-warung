package id.skmnetwork.bukuwarung.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import id.skmnetwork.bukuwarung.data.local.entity.CashTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CashDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCashTransaction(cashTransaction: CashTransactionEntity): Long

    @Query("SELECT * FROM cash_transactions WHERE business_id = :businessId ORDER BY created_at DESC")
    fun getAllCashTransactions(businessId: String): Flow<List<CashTransactionEntity>>

    @Query("SELECT SUM(CASE WHEN type = 'INCOME' THEN amount ELSE -amount END) FROM cash_transactions WHERE business_id = :businessId")
    fun getTotalCashBalance(businessId: String): Flow<Long?>

    @Query("SELECT SUM(amount) FROM cash_transactions WHERE business_id = :businessId AND type = 'INCOME' AND created_at >= :startDate AND created_at <= :endDate")
    fun getCashIncomeTotal(businessId: String, startDate: Long, endDate: Long): Flow<Long?>

    @Query("SELECT SUM(amount) FROM cash_transactions WHERE business_id = :businessId AND type = 'EXPENSE' AND created_at >= :startDate AND created_at <= :endDate")
    fun getCashExpenseTotal(businessId: String, startDate: Long, endDate: Long): Flow<Long?>

    @Query("SELECT SUM(amount) FROM cash_transactions WHERE business_id = :businessId AND type = 'EXPENSE' AND ref_id IS NULL AND ref_uuid IS NULL AND created_at >= :startDate AND created_at <= :endDate")
    fun getOperatingExpenseTotal(businessId: String, startDate: Long, endDate: Long): Flow<Long?>
}
