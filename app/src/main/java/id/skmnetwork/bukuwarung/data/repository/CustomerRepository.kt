package id.skmnetwork.bukuwarung.data.repository

import androidx.room.withTransaction
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CashTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.CustomerEntity
import id.skmnetwork.bukuwarung.data.local.entity.DebtEntity
import id.skmnetwork.bukuwarung.data.local.entity.DebtPaymentEntity
import id.skmnetwork.bukuwarung.data.local.entity.ItemType
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.StockMovementEntity
import id.skmnetwork.bukuwarung.data.local.entity.SyncQueueEntity
import id.skmnetwork.bukuwarung.domain.checkout.CartLine
import id.skmnetwork.bukuwarung.domain.checkout.toRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

class CustomerRepository(
    private val appDatabase: AppDatabase,
    private val businessId: String
) {
    private val customerDao = appDatabase.customerDao()
    private val debtDao = appDatabase.debtDao()
    private val productDao = appDatabase.productDao()
    private val saleDao = appDatabase.saleDao()
    private val cashDao = appDatabase.cashDao()
    private val stockMovementDao = appDatabase.stockMovementDao()
    private val syncQueueDao = appDatabase.syncQueueDao()

    val allCustomers: Flow<List<CustomerEntity>> = customerDao.getAllCustomers(businessId)

    fun searchCustomers(query: String): Flow<List<CustomerEntity>> {
        return if (query.trim().isEmpty()) {
            customerDao.getAllCustomers(businessId)
        } else {
            customerDao.searchCustomers(query.trim(), businessId)
        }
    }

    suspend fun getCustomerById(id: Long): CustomerEntity? = withContext(Dispatchers.IO) {
        customerDao.getCustomerById(id, businessId)
    }

    fun getDebtsForCustomer(customerId: Long): Flow<List<DebtEntity>> {
        return debtDao.getDebtsForCustomer(customerId, businessId)
    }

    fun getPaymentsForDebt(debtId: Long): Flow<List<DebtPaymentEntity>> {
        return debtDao.getPaymentsForDebt(debtId, businessId)
    }

    fun getTotalOutstandingForCustomer(customerId: Long): Flow<Long?> {
        return debtDao.getTotalOutstandingForCustomer(customerId, businessId)
    }

    suspend fun saveCustomer(
        name: String,
        phone: String?,
        address: String?
    ): Long = withContext(Dispatchers.IO) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            throw IllegalArgumentException("Nama pelanggan wajib diisi")
        }

        val now = System.currentTimeMillis()
        val customer = CustomerEntity(
            uuid = UUID.randomUUID().toString(),
            businessId = businessId,
            name = trimmedName,
            phone = phone?.trim()?.ifEmpty { null },
            address = address?.trim()?.ifEmpty { null },
            createdAt = now,
            updatedAt = now
        )
        appDatabase.withTransaction {
            val id = customerDao.insertCustomer(customer)
            syncQueueDao.insert(
                SyncQueueEntity(
                    businessId = customer.businessId,
                    deviceId = "LEGACY_DEVICE",
                    entityType = "CUSTOMER",
                    entityUuid = customer.uuid,
                    operation = "INSERT",
                    createdAt = now,
                    updatedAt = now
                )
            )
            id
        }
    }

    suspend fun updateCustomer(
        id: Long,
        name: String,
        phone: String?,
        address: String?
    ) = withContext(Dispatchers.IO) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            throw IllegalArgumentException("Nama pelanggan wajib diisi")
        }

        val existing = customerDao.getCustomerById(id, businessId)
            ?: throw Exception("Pelanggan tidak ditemukan")

        val now = System.currentTimeMillis()
        val updated = existing.copy(
            name = trimmedName,
            phone = phone?.trim()?.ifEmpty { null },
            address = address?.trim()?.ifEmpty { null },
            updatedAt = now
        )
        appDatabase.withTransaction {
            customerDao.updateCustomer(updated)
            syncQueueDao.insert(
                SyncQueueEntity(
                    businessId = updated.businessId,
                    deviceId = "LEGACY_DEVICE",
                    entityType = "CUSTOMER",
                    entityUuid = updated.uuid,
                    operation = "UPDATE",
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    suspend fun deleteCustomer(id: Long) = withContext(Dispatchers.IO) {
        val existing = customerDao.getCustomerById(id, businessId)
            ?: throw Exception("Pelanggan tidak ditemukan")
        val now = System.currentTimeMillis()

        appDatabase.withTransaction {
            customerDao.softDeleteCustomer(id, now, businessId)
            syncQueueDao.insert(
                SyncQueueEntity(
                    businessId = existing.businessId,
                    deviceId = "LEGACY_DEVICE",
                    entityType = "CUSTOMER",
                    entityUuid = existing.uuid,
                    operation = "DELETE",
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    private val saleRepository = SaleRepository(appDatabase, businessId)

    suspend fun processAtomicCreditCheckout(
        cartItems: Map<Long, Double>,
        customerId: Long,
        discountAmount: Long = 0L
    ): Result<Long> = withContext(Dispatchers.IO) {
        saleRepository.completeSale(
            cartItems = cartItems.map { (productId, quantity) ->
                id.skmnetwork.bukuwarung.domain.checkout.CartLineRequest(
                    productId = productId,
                    quantity = quantity
                )
            },
            paymentMethod = "CREDIT",
            customerId = customerId,
            discountAmount = discountAmount
        )
    }

    suspend fun processAtomicCreditCheckout(
        cartLines: List<CartLine>,
        customerId: Long,
        discountAmount: Long = 0L
    ): Result<Long> = withContext(Dispatchers.IO) {
        saleRepository.completeSaleRequests(
            cartLineRequests = cartLines.map { it.toRequest() },
            paymentMethod = "CREDIT",
            customerId = customerId,
            discountAmount = discountAmount
        )
    }

    suspend fun getReceiptData(
        saleId: Long,
        userSettings: id.skmnetwork.bukuwarung.data.preferences.UserSettings? = null
    ): id.skmnetwork.bukuwarung.domain.receipt.ReceiptData? = saleRepository.getReceiptData(saleId, userSettings)

    suspend fun processAtomicDebtPayment(
        debtId: Long,
        amount: Long,
        note: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            appDatabase.withTransaction {
                val debt = debtDao.getDebtById(debtId, businessId)
                    ?: throw Exception("Data hutang tidak ditemukan")

                if (debt.status == "PAID") {
                    throw Exception("Hutang ini sudah lunas")
                }

                val outstanding = debt.totalDebt - debt.paidAmount
                if (amount <= 0) {
                    throw IllegalArgumentException("Jumlah pembayaran harus lebih dari 0")
                }
                if (amount > outstanding) {
                    throw IllegalArgumentException("Jumlah pembayaran melebihi sisa hutang")
                }

                val now = System.currentTimeMillis()
                val paymentUuid = UUID.randomUUID().toString()

                // 1. Insert Debt Payment
                val payment = DebtPaymentEntity(
                    uuid = paymentUuid,
                    debtId = debt.id,
                    debtUuid = debt.uuid,
                    amount = amount,
                    paymentDate = now,
                    note = note?.trim()?.ifEmpty { null },
                    createdAt = now
                )
                val paymentId = debtDao.insertDebtPayment(payment)

                // 2. Update Debt paidAmount & status
                val newPaidAmount = debt.paidAmount + amount
                val newStatus = if (newPaidAmount >= debt.totalDebt) "PAID" else "OPEN"

                val updatedDebt = debt.copy(
                    paidAmount = newPaidAmount,
                    status = newStatus,
                    updatedAt = now
                )
                debtDao.updateDebt(updatedDebt)

                // 3. Record Cash Income Transaction
                val customer = customerDao.getCustomerById(debt.customerId, businessId)
                val customerName = customer?.name ?: "Pelanggan"

                val cashIncome = CashTransactionEntity(
                    type = "INCOME",
                    amount = amount,
                    description = "Pembayaran Hutang $customerName",
                    refId = paymentId,
                    refUuid = paymentUuid,
                    createdAt = now
                )
                cashDao.insertCashTransaction(cashIncome)

                // 4. Enqueue DEBT_PAYMENT sync event
                syncQueueDao.insert(
                    SyncQueueEntity(
                        businessId = debt.businessId,
                        deviceId = "LEGACY_DEVICE",
                        entityType = "DEBT_PAYMENT",
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
