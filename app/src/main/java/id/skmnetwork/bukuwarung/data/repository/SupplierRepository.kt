package id.skmnetwork.bukuwarung.data.repository

import androidx.room.withTransaction
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CashTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.StockMovementEntity
import id.skmnetwork.bukuwarung.data.local.entity.SupplierEntity
import id.skmnetwork.bukuwarung.data.local.entity.SupplierPayableEntity
import id.skmnetwork.bukuwarung.data.local.entity.SupplierPaymentEntity
import id.skmnetwork.bukuwarung.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

class SupplierRepository(
    private val appDatabase: AppDatabase,
    private val businessId: String
) {
    private val supplierDao = appDatabase.supplierDao()
    private val supplierPayableDao = appDatabase.supplierPayableDao()
    private val productDao = appDatabase.productDao()
    private val purchaseDao = appDatabase.purchaseDao()
    private val cashDao = appDatabase.cashDao()
    private val stockMovementDao = appDatabase.stockMovementDao()
    private val syncQueueDao = appDatabase.syncQueueDao()

    val allSuppliers: Flow<List<SupplierEntity>> = supplierDao.getAllSuppliers(businessId)

    fun searchSuppliers(query: String): Flow<List<SupplierEntity>> {
        return if (query.trim().isEmpty()) {
            supplierDao.getAllSuppliers(businessId)
        } else {
            supplierDao.searchSuppliers(query.trim(), businessId)
        }
    }

    suspend fun getSupplierById(id: Long): SupplierEntity? = withContext(Dispatchers.IO) {
        supplierDao.getSupplierById(id, businessId)
    }

    fun getPayablesForSupplier(supplierId: Long): Flow<List<SupplierPayableEntity>> {
        return supplierPayableDao.getPayablesForSupplier(supplierId, businessId)
    }

    fun getPaymentsForPayable(payableId: Long): Flow<List<SupplierPaymentEntity>> {
        return supplierPayableDao.getPaymentsForPayable(payableId, businessId)
    }

    fun getTotalOutstandingForSupplier(supplierId: Long): Flow<Long?> {
        return supplierPayableDao.getTotalOutstandingForSupplier(supplierId, businessId)
    }

    suspend fun saveSupplier(
        name: String,
        phone: String?,
        address: String?
    ): Long = withContext(Dispatchers.IO) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            throw IllegalArgumentException("Nama supplier wajib diisi")
        }

        val now = System.currentTimeMillis()
        val supplier = SupplierEntity(
            uuid = UUID.randomUUID().toString(),
            businessId = businessId,
            name = trimmedName,
            phone = phone?.trim()?.ifEmpty { null },
            address = address?.trim()?.ifEmpty { null },
            createdAt = now,
            updatedAt = now
        )
        appDatabase.withTransaction {
            val id = supplierDao.insertSupplier(supplier)
            syncQueueDao.insert(
                SyncQueueEntity(
                    businessId = businessId,
                    deviceId = "LEGACY_DEVICE",
                    entityType = "SUPPLIER",
                    entityUuid = supplier.uuid,
                    operation = "INSERT",
                    createdAt = now,
                    updatedAt = now
                )
            )
            id
        }
    }

    suspend fun updateSupplier(
        id: Long,
        name: String,
        phone: String?,
        address: String?
    ) = withContext(Dispatchers.IO) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            throw IllegalArgumentException("Nama supplier wajib diisi")
        }

        val existing = supplierDao.getSupplierById(id, businessId)
            ?: throw Exception("Supplier tidak ditemukan")

        val now = System.currentTimeMillis()
        val updated = existing.copy(
            name = trimmedName,
            phone = phone?.trim()?.ifEmpty { null },
            address = address?.trim()?.ifEmpty { null },
            updatedAt = now
        )
        appDatabase.withTransaction {
            supplierDao.updateSupplier(updated)
            syncQueueDao.insert(
                SyncQueueEntity(
                    businessId = businessId,
                    deviceId = "LEGACY_DEVICE",
                    entityType = "SUPPLIER",
                    entityUuid = updated.uuid,
                    operation = "UPDATE",
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    suspend fun deleteSupplier(id: Long) = withContext(Dispatchers.IO) {
        val existing = supplierDao.getSupplierById(id, businessId)
            ?: throw Exception("Supplier tidak ditemukan")
        val now = System.currentTimeMillis()

        appDatabase.withTransaction {
            supplierDao.softDeleteSupplier(id, now, businessId)
            syncQueueDao.insert(
                SyncQueueEntity(
                    businessId = businessId,
                    deviceId = "LEGACY_DEVICE",
                    entityType = "SUPPLIER",
                    entityUuid = existing.uuid,
                    operation = "DELETE",
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    private val purchaseRepository = PurchaseRepository(appDatabase, businessId)

    suspend fun processAtomicCreditPurchase(
        purchaseItems: Map<Long, Double>,
        supplierId: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        purchaseRepository.completePurchase(
            purchaseItems = purchaseItems,
            paymentMethod = "CREDIT",
            supplierId = supplierId
        ).map { }
    }

    suspend fun processAtomicSupplierPayment(
        payableId: Long,
        amount: Long,
        note: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            appDatabase.withTransaction {
                val payable = supplierPayableDao.getSupplierPayableById(payableId, businessId)
                    ?: throw Exception("Data hutang supplier tidak ditemukan")

                if (payable.status == "PAID") {
                    throw Exception("Hutang supplier ini sudah lunas")
                }

                val outstanding = payable.totalDebt - payable.paidAmount
                if (amount <= 0) {
                    throw IllegalArgumentException("Jumlah pembayaran harus lebih dari 0")
                }
                if (amount > outstanding) {
                    throw IllegalArgumentException("Jumlah pembayaran melebihi sisa hutang supplier")
                }

                val now = System.currentTimeMillis()
                val paymentUuid = UUID.randomUUID().toString()

                // 1. Insert Supplier Payment
                val payment = SupplierPaymentEntity(
                    uuid = paymentUuid,
                    businessId = businessId,
                    payableId = payable.id,
                    payableUuid = payable.uuid,
                    amount = amount,
                    paymentDate = now,
                    note = note?.trim()?.ifEmpty { null },
                    createdAt = now
                )
                val paymentId = supplierPayableDao.insertSupplierPayment(payment)

                // 2. Update Supplier Payable paidAmount & status
                val newPaidAmount = payable.paidAmount + amount
                val newStatus = if (newPaidAmount >= payable.totalDebt) "PAID" else "OPEN"

                val updatedPayable = payable.copy(
                    paidAmount = newPaidAmount,
                    status = newStatus,
                    updatedAt = now
                )
                supplierPayableDao.updateSupplierPayable(updatedPayable)

                // 3. Record Cash Expense Transaction
                val supplier = supplierDao.getSupplierById(payable.supplierId, businessId)
                val supplierName = supplier?.name ?: "Supplier"

                val cashExpense = CashTransactionEntity(
                    businessId = businessId,
                    type = "EXPENSE",
                    amount = amount,
                    description = "Pembayaran Hutang Supplier $supplierName",
                    refId = paymentId,
                    refUuid = paymentUuid,
                    createdAt = now
                )
                cashDao.insertCashTransaction(cashExpense)

                // 4. Enqueue SUPPLIER_PAYMENT sync event
                syncQueueDao.insert(
                    SyncQueueEntity(
                        businessId = businessId,
                        deviceId = "LEGACY_DEVICE",
                        entityType = "SUPPLIER_PAYMENT",
                        entityUuid = paymentUuid,
                        operation = "INSERT",
                        createdAt = now,
                        updatedAt = now
                    )
                )

                Unit
            }
        }
    }
}
