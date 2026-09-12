package id.skmnetwork.bukuwarung.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import id.skmnetwork.bukuwarung.data.local.entity.SupplierPayableEntity
import id.skmnetwork.bukuwarung.data.local.entity.SupplierPaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SupplierPayableDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSupplierPayable(payable: SupplierPayableEntity): Long

    @Update
    suspend fun updateSupplierPayable(payable: SupplierPayableEntity)

    @Query("SELECT * FROM supplier_payables WHERE id = :id")
    suspend fun getSupplierPayableById(id: Long): SupplierPayableEntity?

    @Query("SELECT * FROM supplier_payables WHERE supplier_id = :supplierId ORDER BY created_at DESC")
    fun getPayablesForSupplier(supplierId: Long): Flow<List<SupplierPayableEntity>>

    @Query("SELECT * FROM supplier_payables WHERE supplier_id = :supplierId AND status = 'OPEN' ORDER BY created_at ASC")
    suspend fun getOpenPayablesForSupplierList(supplierId: Long): List<SupplierPayableEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSupplierPayment(payment: SupplierPaymentEntity): Long

    @Query("SELECT * FROM supplier_payments WHERE payable_id = :payableId ORDER BY payment_date DESC")
    fun getPaymentsForPayable(payableId: Long): Flow<List<SupplierPaymentEntity>>

    @Query("SELECT * FROM supplier_payments WHERE payable_id = :payableId ORDER BY payment_date DESC")
    suspend fun getPaymentsListForPayable(payableId: Long): List<SupplierPaymentEntity>

    @Query("SELECT SUM(total_debt - paid_amount) FROM supplier_payables WHERE supplier_id = :supplierId AND status = 'OPEN'")
    fun getTotalOutstandingForSupplier(supplierId: Long): Flow<Long?>

    @Query("SELECT SUM(total_debt - paid_amount) FROM supplier_payables WHERE status = 'OPEN'")
    fun getTotalOutstandingPayable(): Flow<Long?>

    @Query("SELECT SUM(paid_amount) FROM supplier_payables")
    fun getTotalPaidPayable(): Flow<Long?>

    @Query("SELECT SUM(total_debt) FROM supplier_payables")
    fun getTotalPayableCreated(): Flow<Long?>

    @Query("SELECT COUNT(*) FROM supplier_payables WHERE status = 'OPEN'")
    fun getOpenPayablesCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM supplier_payables WHERE status = 'PAID'")
    fun getPaidPayablesCount(): Flow<Int>
}
