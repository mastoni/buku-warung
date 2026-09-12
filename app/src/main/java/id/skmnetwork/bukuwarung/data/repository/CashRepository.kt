package id.skmnetwork.bukuwarung.data.repository

import androidx.room.withTransaction
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CashTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.UUID

class CashRepository(
    private val appDatabase: AppDatabase
) {
    private val cashDao = appDatabase.cashDao()
    private val syncQueueDao = appDatabase.syncQueueDao()

    val totalCashBalance: Flow<Long?> = cashDao.getTotalCashBalance()
    val allCashTransactions: Flow<List<CashTransactionEntity>> = cashDao.getAllCashTransactions()

    fun getTodayExpenseTotalFlow(): Flow<Long?> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis

        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfDay = calendar.timeInMillis

        return cashDao.getCashExpenseTotal(startOfDay, endOfDay)
    }

    fun getTodayIncomeTotalFlow(): Flow<Long?> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis

        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfDay = calendar.timeInMillis

        return cashDao.getCashIncomeTotal(startOfDay, endOfDay)
    }

    suspend fun recordManualIncome(
        amount: Long,
        description: String,
        createdAt: Long = System.currentTimeMillis()
    ): Result<Long> = withContext(Dispatchers.IO) {
        if (amount <= 0) {
            return@withContext Result.failure(IllegalArgumentException("Jumlah pemasukan harus lebih dari 0"))
        }
        val cleanDesc = description.trim()
        if (cleanDesc.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Keterangan pemasukan wajib diisi"))
        }

        runCatching {
            val txUuid = UUID.randomUUID().toString()
            val tx = CashTransactionEntity(
                uuid = txUuid,
                type = "INCOME",
                amount = amount,
                description = cleanDesc,
                refId = null,
                refUuid = null,
                createdAt = createdAt
            )
            appDatabase.withTransaction {
                val id = cashDao.insertCashTransaction(tx)
                syncQueueDao.insert(
                    SyncQueueEntity(
                        businessId = tx.businessId,
                        deviceId = "LEGACY_DEVICE",
                        entityType = "CASH_TRANSACTION",
                        entityUuid = txUuid,
                        operation = "INSERT",
                        createdAt = createdAt,
                        updatedAt = createdAt
                    )
                )
                id
            }
        }
    }

    suspend fun recordManualExpense(
        amount: Long,
        description: String,
        createdAt: Long = System.currentTimeMillis()
    ): Result<Long> = withContext(Dispatchers.IO) {
        if (amount <= 0) {
            return@withContext Result.failure(IllegalArgumentException("Jumlah pengeluaran harus lebih dari 0"))
        }
        val cleanDesc = description.trim()
        if (cleanDesc.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Keterangan pengeluaran wajib diisi"))
        }

        runCatching {
            val txUuid = UUID.randomUUID().toString()
            val tx = CashTransactionEntity(
                uuid = txUuid,
                type = "EXPENSE",
                amount = amount,
                description = cleanDesc,
                refId = null,
                refUuid = null,
                createdAt = createdAt
            )
            appDatabase.withTransaction {
                val id = cashDao.insertCashTransaction(tx)
                syncQueueDao.insert(
                    SyncQueueEntity(
                        businessId = tx.businessId,
                        deviceId = "LEGACY_DEVICE",
                        entityType = "CASH_TRANSACTION",
                        entityUuid = txUuid,
                        operation = "INSERT",
                        createdAt = createdAt,
                        updatedAt = createdAt
                    )
                )
                id
            }
        }
    }

    suspend fun recordDebtPaymentIncome(
        amount: Long,
        description: String,
        debtId: Long,
        debtPaymentUuid: String,
        createdAt: Long = System.currentTimeMillis()
    ): Long = withContext(Dispatchers.IO) {
        val tx = CashTransactionEntity(
            type = "INCOME",
            amount = amount,
            description = description,
            refId = debtId,
            refUuid = debtPaymentUuid,
            createdAt = createdAt
        )
        cashDao.insertCashTransaction(tx)
    }

    suspend fun recordSupplierPaymentExpense(
        amount: Long,
        description: String,
        payableId: Long,
        supplierPaymentUuid: String,
        createdAt: Long = System.currentTimeMillis()
    ): Long = withContext(Dispatchers.IO) {
        val tx = CashTransactionEntity(
            type = "EXPENSE",
            amount = amount,
            description = description,
            refId = payableId,
            refUuid = supplierPaymentUuid,
            createdAt = createdAt
        )
        cashDao.insertCashTransaction(tx)
    }
}
