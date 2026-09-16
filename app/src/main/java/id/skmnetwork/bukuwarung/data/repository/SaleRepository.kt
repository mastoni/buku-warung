package id.skmnetwork.bukuwarung.data.repository

import androidx.room.withTransaction
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CashTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.CustomerEntity
import id.skmnetwork.bukuwarung.data.local.entity.DebtEntity
import id.skmnetwork.bukuwarung.data.local.entity.ItemType
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.StockMovementEntity
import id.skmnetwork.bukuwarung.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.UUID

import id.skmnetwork.bukuwarung.data.local.entity.SaleReturnItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleReturnTransactionEntity

class SaleRepository(
    private val appDatabase: AppDatabase
) {
    private val saleDao = appDatabase.saleDao()
    private val productDao = appDatabase.productDao()
    private val customerDao = appDatabase.customerDao()
    private val debtDao = appDatabase.debtDao()
    private val cashDao = appDatabase.cashDao()
    private val stockMovementDao = appDatabase.stockMovementDao()
    private val syncQueueDao = appDatabase.syncQueueDao()
    private val saleReturnDao = appDatabase.saleReturnDao()

    val allTransactions: Flow<List<SaleTransactionEntity>> = saleDao.getAllTransactions()
    val allReturns: Flow<List<SaleReturnTransactionEntity>> = saleReturnDao.getAllReturns()

    fun getReturnsForSale(saleTransactionId: Long): Flow<List<SaleReturnTransactionEntity>> {
        return saleReturnDao.getReturnsForSale(saleTransactionId)
    }

    suspend fun getReturnById(id: Long): SaleReturnTransactionEntity? = withContext(Dispatchers.IO) {
        saleReturnDao.getReturnById(id)
    }

    suspend fun getItemsForReturn(returnTransactionId: Long): List<SaleReturnItemEntity> = withContext(Dispatchers.IO) {
        saleReturnDao.getItemsForReturn(returnTransactionId)
    }

    suspend fun getReturnsListForSale(saleTransactionId: Long): List<SaleReturnTransactionEntity> = withContext(Dispatchers.IO) {
        saleReturnDao.getReturnsListForSale(saleTransactionId)
    }

    suspend fun getReturnedQuantityForSaleItem(saleItemId: Long): Double = withContext(Dispatchers.IO) {
        saleReturnDao.getReturnedQuantityForSaleItem(saleItemId) ?: 0.0
    }

    suspend fun getReturnableQuantitiesForSale(saleTransactionId: Long): Map<Long, Double> = withContext(Dispatchers.IO) {
        val saleItems = saleDao.getItemsForTransaction(saleTransactionId)
        val result = mutableMapOf<Long, Double>()
        for (item in saleItems) {
            val returnedQty = saleReturnDao.getReturnedQuantityForSaleItem(item.id) ?: 0.0
            val remaining = (item.quantity - returnedQty).coerceAtLeast(0.0)
            result[item.id] = remaining
        }
        result
    }

    fun getTodaySalesTotalFlow(): Flow<Long?> {
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

        return saleDao.getTodaySalesTotal(startOfDay, endOfDay)
    }

    suspend fun getTransactionById(id: Long): SaleTransactionEntity? = withContext(Dispatchers.IO) {
        saleDao.getTransactionById(id)
    }

    suspend fun getItemsForTransaction(transactionId: Long): List<SaleItemEntity> = withContext(Dispatchers.IO) {
        saleDao.getItemsForTransaction(transactionId)
    }

    suspend fun completeSale(
        cartItems: Map<Long, Double>,
        paymentMethod: String = "CASH",
        customerId: Long? = null,
        now: Long = System.currentTimeMillis()
    ): Result<Long> = withContext(Dispatchers.IO) {
        if (cartItems.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Keranjang kosong"))
        }

        val methodUpper = paymentMethod.uppercase()
        if (methodUpper == "CREDIT" && customerId == null) {
            return@withContext Result.failure(IllegalArgumentException("Pelanggan wajib dipilih untuk transaksi kredit"))
        }

        runCatching {
            appDatabase.withTransaction {
                // 1. Validate customer if provided or credit sale
                val customer: CustomerEntity? = if (customerId != null) {
                    customerDao.getCustomerById(customerId)
                        ?: if (methodUpper == "CREDIT") throw IllegalStateException("Pelanggan tidak ditemukan") else null
                } else null

                // 2. Validate products and stock availability
                val productMap = mutableMapOf<Long, ProductEntity>()
                for ((productId, qty) in cartItems) {
                    if (qty <= 0) {
                        throw IllegalArgumentException("Jumlah item harus lebih dari 0")
                    }
                    val product = productDao.getProductById(productId)
                        ?: throw IllegalStateException("Produk tidak ditemukan")
                    if (product.itemType == ItemType.PHYSICAL.name && product.stock < qty) {
                        throw IllegalStateException("Stok ${product.name} tidak mencukupi")
                    }
                    productMap[productId] = product
                }

                // 3. Calculate Total Amount
                val totalAmount = cartItems.entries.sumOf { (prodId, qty) ->
                    val prod = productMap[prodId]!!
                    prod.sellingPrice * qty.toLong()
                }

                if (totalAmount <= 0) {
                    throw IllegalStateException("Total transaksi harus lebih dari 0")
                }

                val trxNumber = "TRX-$now"
                val trxUuid = UUID.randomUUID().toString()

                // 4. Create Sale Transaction Record
                val saleTransaction = SaleTransactionEntity(
                    uuid = trxUuid,
                    transactionNumber = trxNumber,
                    transactionDate = now,
                    totalAmount = totalAmount,
                    paymentMethod = methodUpper,
                    customerId = customer?.id,
                    createdAt = now
                )
                val saleId = saleDao.insertTransaction(saleTransaction)

                // 5. Create Sale Items Records
                val saleItems = cartItems.map { (prodId, qty) ->
                    val prod = productMap[prodId]!!
                    SaleItemEntity(
                        saleUuid = trxUuid,
                        productUuid = prod.uuid,
                        transactionId = saleId,
                        productId = prodId,
                        productName = prod.name,
                        quantity = qty,
                        price = prod.sellingPrice,
                        purchasePrice = prod.purchasePrice,
                        subtotal = prod.sellingPrice * qty.toLong()
                    )
                }
                saleDao.insertSaleItems(saleItems)

                // 6. Mutate Stock & Append Stock Movement ONLY for PHYSICAL products
                for ((prodId, qty) in cartItems) {
                    val prod = productMap[prodId]!!
                    if (prod.itemType == ItemType.PHYSICAL.name) {
                        val newStock = if (prod.stock - qty < 0) 0.0 else prod.stock - qty
                        productDao.deductProductStock(prodId, qty, now)
                        stockMovementDao.insertMovement(
                            StockMovementEntity(
                                productUuid = prod.uuid,
                                movementType = "SALE",
                                deltaQuantity = -qty,
                                currentStockSnapshot = newStock,
                                referenceUuid = trxUuid,
                                note = "Penjualan $trxNumber",
                                createdAt = now
                            )
                        )
                    }
                }

                // 7. Payment method routing
                when (methodUpper) {
                    "CASH" -> {
                        val cashIncome = CashTransactionEntity(
                            type = "INCOME",
                            amount = totalAmount,
                            description = "Penjualan $trxNumber",
                            refId = saleId,
                            refUuid = trxUuid,
                            createdAt = now
                        )
                        cashDao.insertCashTransaction(cashIncome)
                    }
                    "CREDIT" -> {
                        val debt = DebtEntity(
                            uuid = UUID.randomUUID().toString(),
                            customerId = customer!!.id,
                            saleTransactionId = saleId,
                            customerUuid = customer.uuid,
                            saleUuid = trxUuid,
                            totalDebt = totalAmount,
                            paidAmount = 0L,
                            status = "OPEN",
                            createdAt = now,
                            updatedAt = now
                        )
                        debtDao.insertDebt(debt)
                    }
                    "QRIS" -> {
                        // QRIS does not generate CashTransaction entry
                    }
                }

                // 8. Atomic Sync Queue Enqueue (Aggregate SALE event)
                syncQueueDao.insert(
                    SyncQueueEntity(
                        businessId = saleTransaction.businessId,
                        deviceId = saleTransaction.deviceId,
                        entityType = "SALE",
                        entityUuid = trxUuid,
                        operation = "INSERT",
                        createdAt = now,
                        updatedAt = now
                    )
                )

                saleId
            }
        }
    }

    /**
     * Executes atomic sale return for partial or full items.
     * Guaranteed invariant preservation:
     * - Physical items returned increment stock & record StockMovement(RETURN, +qty)
     * - CASH and QRIS sales refund CASH EXPENSE
     * - CREDIT sales reduce debt; overpaid portion refunded in CASH EXPENSE
     * - SyncQueue aggregate RETURN event
     */
    suspend fun processSaleReturn(
        saleId: Long,
        itemsToReturn: Map<Long, Double>, // Map of saleItemId -> returnQuantity
        reason: String? = null,
        notes: String? = null,
        now: Long = System.currentTimeMillis()
    ): Result<Long> = withContext(Dispatchers.IO) {
        if (itemsToReturn.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Item retur tidak boleh kosong"))
        }

        runCatching {
            appDatabase.withTransaction {
                // 1. Validate original sale
                val sale = saleDao.getTransactionById(saleId)
                    ?: throw IllegalStateException("Transaksi penjualan tidak ditemukan")

                // 2. Validate requested items belong to this sale and within returnable limit
                val originalSaleItems = saleDao.getItemsForTransaction(saleId)
                val saleItemMap = originalSaleItems.associateBy { it.id }

                var totalRefundAmount = 0L
                val validatedReturnItems = mutableListOf<Pair<SaleItemEntity, Double>>()

                for ((saleItemId, returnQty) in itemsToReturn) {
                    if (returnQty <= 0.0) {
                        throw IllegalArgumentException("Kuantiti retur harus lebih dari 0")
                    }
                    val saleItem = saleItemMap[saleItemId]
                        ?: throw IllegalArgumentException("Item penjualan ID $saleItemId tidak ditemukan pada transaksi ini")

                    val alreadyReturned = saleReturnDao.getReturnedQuantityForSaleItem(saleItemId) ?: 0.0
                    val remainingReturnable = saleItem.quantity - alreadyReturned
                    if (returnQty > remainingReturnable) {
                        throw IllegalArgumentException(
                            "Kuantiti retur untuk ${saleItem.productName} ($returnQty) melebihi batas yang dapat diretur ($remainingReturnable)"
                        )
                    }

                    val itemRefund = (saleItem.price * returnQty).toLong()
                    totalRefundAmount += itemRefund
                    validatedReturnItems.add(Pair(saleItem, returnQty))
                }

                if (totalRefundAmount <= 0L) {
                    throw IllegalStateException("Total pengembalian dana harus lebih dari 0")
                }

                val returnNumber = "RET-$now"
                val returnUuid = UUID.randomUUID().toString()
                val customer = sale.customerId?.let { customerDao.getCustomerById(it) }

                val cleanedReason = reason?.trim()?.ifEmpty { null }
                val cleanedNotes = notes?.trim()?.ifEmpty { null }

                // 3. Create Return Transaction Record
                val returnTransaction = SaleReturnTransactionEntity(
                    uuid = returnUuid,
                    businessId = sale.businessId,
                    deviceId = sale.deviceId,
                    returnNumber = returnNumber,
                    returnDate = now,
                    saleTransactionId = sale.id,
                    saleUuid = sale.uuid,
                    customerId = sale.customerId,
                    customerUuid = customer?.uuid,
                    totalRefundAmount = totalRefundAmount,
                    refundMethod = if (sale.paymentMethod.equals("CREDIT", ignoreCase = true)) "CREDIT" else "CASH",
                    reason = cleanedReason,
                    notes = cleanedNotes,
                    createdAt = now
                )
                val returnId = saleReturnDao.insertReturnTransaction(returnTransaction)

                // 4. Create Return Items Records
                val returnEntities = validatedReturnItems.map { (saleItem, returnQty) ->
                    val subtotal = (saleItem.price * returnQty).toLong()
                    SaleReturnItemEntity(
                        uuid = UUID.randomUUID().toString(),
                        businessId = sale.businessId,
                        returnUuid = returnUuid,
                        saleItemUuid = saleItem.uuid,
                        productUuid = saleItem.productUuid,
                        returnTransactionId = returnId,
                        saleItemId = saleItem.id,
                        productId = saleItem.productId,
                        productName = saleItem.productName,
                        quantity = returnQty,
                        price = saleItem.price,
                        purchasePrice = saleItem.purchasePrice,
                        subtotal = subtotal,
                        createdAt = now
                    )
                }
                saleReturnDao.insertReturnItems(returnEntities)

                // 5. Restock Physical Products & append StockMovement RETURN
                for ((saleItem, returnQty) in validatedReturnItems) {
                    val product = productDao.getProductByIdRaw(saleItem.productId)
                    if (product != null && product.itemType == ItemType.PHYSICAL.name) {
                        productDao.addProductStock(product.id, returnQty, now)
                        val newStock = product.stock + returnQty
                        stockMovementDao.insertMovement(
                            StockMovementEntity(
                                productUuid = product.uuid,
                                movementType = "RETURN",
                                deltaQuantity = returnQty,
                                currentStockSnapshot = newStock,
                                referenceUuid = returnUuid,
                                note = "Retur Penjualan $returnNumber",
                                createdAt = now
                            )
                        )
                    }
                }

                // 6. Financial Refund Routing
                val methodUpper = sale.paymentMethod.uppercase()
                when (methodUpper) {
                    "CASH", "QRIS" -> {
                        // Original CASH and QRIS sales are refunded physically as CASH EXPENSE
                        val cashExpense = CashTransactionEntity(
                            type = "EXPENSE",
                            amount = totalRefundAmount,
                            description = "Refund Retur Penjualan $returnNumber",
                            refId = returnId,
                            refUuid = returnUuid,
                            createdAt = now
                        )
                        cashDao.insertCashTransaction(cashExpense)
                    }
                    "CREDIT" -> {
                        val linkedDebt = debtDao.getDebtBySaleId(sale.id)
                            ?: (if (sale.uuid.isNotBlank()) debtDao.getDebtBySaleUuid(sale.uuid) else null)

                        if (linkedDebt != null) {
                            val currentUnpaidDebt = (linkedDebt.totalDebt - linkedDebt.paidAmount).coerceAtLeast(0L)
                            if (totalRefundAmount <= currentUnpaidDebt) {
                                // Entire return is absorbed by reducing outstanding unpaid debt
                                val newTotalDebt = linkedDebt.totalDebt - totalRefundAmount
                                val newStatus = if (newTotalDebt <= linkedDebt.paidAmount) "PAID" else "OPEN"
                                debtDao.updateDebt(
                                    linkedDebt.copy(
                                        totalDebt = newTotalDebt,
                                        status = newStatus,
                                        updatedAt = now
                                    )
                                )
                            } else {
                                // Return amount exceeds remaining unpaid debt -> overpayment refunded in CASH
                                val debtReduction = currentUnpaidDebt
                                val cashOverpaymentRefund = totalRefundAmount - currentUnpaidDebt
                                val newTotalDebt = linkedDebt.totalDebt - debtReduction // equals linkedDebt.paidAmount
                                debtDao.updateDebt(
                                    linkedDebt.copy(
                                        totalDebt = newTotalDebt,
                                        status = "PAID",
                                        updatedAt = now
                                    )
                                )
                                if (cashOverpaymentRefund > 0L) {
                                    val cashExpense = CashTransactionEntity(
                                        type = "EXPENSE",
                                        amount = cashOverpaymentRefund,
                                        description = "Refund Kelebihan Bayar Retur $returnNumber",
                                        refId = returnId,
                                        refUuid = returnUuid,
                                        createdAt = now
                                    )
                                    cashDao.insertCashTransaction(cashExpense)
                                }
                            }
                        } else {
                            // Fallback if no debt record exists
                            val cashExpense = CashTransactionEntity(
                                type = "EXPENSE",
                                amount = totalRefundAmount,
                                description = "Refund Retur Penjualan $returnNumber",
                                refId = returnId,
                                refUuid = returnUuid,
                                createdAt = now
                            )
                            cashDao.insertCashTransaction(cashExpense)
                        }
                    }
                }

                // 7. Atomic Sync Queue Enqueue (Aggregate RETURN event)
                syncQueueDao.insert(
                    SyncQueueEntity(
                        businessId = sale.businessId,
                        deviceId = sale.deviceId,
                        entityType = "RETURN",
                        entityUuid = returnUuid,
                        operation = "INSERT",
                        createdAt = now,
                        updatedAt = now
                    )
                )

                returnId
            }
        }
    }

    suspend fun getReceiptData(
        saleId: Long,
        userSettings: id.skmnetwork.bukuwarung.data.preferences.UserSettings? = null,
        cashGiven: Long? = null
    ): id.skmnetwork.bukuwarung.domain.receipt.ReceiptData? = withContext(Dispatchers.IO) {
        val sale = saleDao.getTransactionById(saleId) ?: return@withContext null
        val items = saleDao.getItemsForTransaction(saleId)
        val customer = sale.customerId?.let { customerDao.getCustomerById(it) }
        val debt = if (sale.paymentMethod == "CREDIT" && sale.customerId != null) {
            debtDao.getOpenDebtsForCustomerList(sale.customerId).find { it.saleTransactionId == saleId }
        } else null

        id.skmnetwork.bukuwarung.domain.receipt.ReceiptMapper.mapFromSale(
            sale = sale,
            items = items,
            customer = customer,
            debt = debt,
            userSettings = userSettings,
            cashGiven = cashGiven
        )
    }
}
