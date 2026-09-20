package id.skmnetwork.bukuwarung.data.repository

import androidx.room.withTransaction
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CashTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.DebtEntity
import id.skmnetwork.bukuwarung.data.local.entity.FulfillmentMode
import id.skmnetwork.bukuwarung.data.local.entity.ItemType
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.StockMovementEntity
import id.skmnetwork.bukuwarung.data.local.entity.SyncQueueEntity
import id.skmnetwork.bukuwarung.domain.checkout.CartLineRequest
import id.skmnetwork.bukuwarung.domain.checkout.SaleCommitResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.UUID

import id.skmnetwork.bukuwarung.data.local.entity.SaleReturnItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleReturnTransactionEntity

class SaleRepository(
    private val appDatabase: AppDatabase,
    private val businessId: String
) {
    private val saleDao = appDatabase.saleDao()
    private val productDao = appDatabase.productDao()
    private val customerDao = appDatabase.customerDao()
    private val debtDao = appDatabase.debtDao()
    private val cashDao = appDatabase.cashDao()
    private val stockMovementDao = appDatabase.stockMovementDao()
    private val syncQueueDao = appDatabase.syncQueueDao()
    private val saleReturnDao = appDatabase.saleReturnDao()

    val allTransactions: Flow<List<SaleTransactionEntity>> = saleDao.getAllTransactions(businessId)
    val allReturns: Flow<List<SaleReturnTransactionEntity>> = saleReturnDao.getAllReturns(businessId)

    fun getReturnsForSale(saleTransactionId: Long): Flow<List<SaleReturnTransactionEntity>> {
        return saleReturnDao.getReturnsForSale(businessId, saleTransactionId)
    }

    suspend fun getReturnById(id: Long): SaleReturnTransactionEntity? = withContext(Dispatchers.IO) {
        saleReturnDao.getReturnById(id, businessId)
    }

    suspend fun getItemsForReturn(returnTransactionId: Long): List<SaleReturnItemEntity> = withContext(Dispatchers.IO) {
        saleReturnDao.getItemsForReturn(businessId, returnTransactionId)
    }

    suspend fun getReturnsListForSale(saleTransactionId: Long): List<SaleReturnTransactionEntity> = withContext(Dispatchers.IO) {
        saleReturnDao.getReturnsListForSale(businessId, saleTransactionId)
    }

    suspend fun getReturnedQuantityForSaleItem(saleItemId: Long): Double = withContext(Dispatchers.IO) {
        saleReturnDao.getReturnedQuantityForSaleItem(saleItemId, businessId) ?: 0.0
    }

    suspend fun getReturnableQuantitiesForSale(saleTransactionId: Long): Map<Long, Double> = withContext(Dispatchers.IO) {
        val saleItems = saleDao.getItemsForTransaction(saleTransactionId, businessId)
        val result = mutableMapOf<Long, Double>()
        for (item in saleItems) {
            val returnedQty = saleReturnDao.getReturnedQuantityForSaleItem(item.id, businessId) ?: 0.0
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

        return saleDao.getTodaySalesTotal(businessId, startOfDay, endOfDay)
    }

    suspend fun getTransactionById(id: Long): SaleTransactionEntity? = withContext(Dispatchers.IO) {
        saleDao.getTransactionById(id, businessId)
    }

    suspend fun getItemsForTransaction(transactionId: Long): List<SaleItemEntity> = withContext(Dispatchers.IO) {
        saleDao.getItemsForTransaction(transactionId, businessId)
    }

    suspend fun completeSale(
        cartItems: Map<Long, Double>,
        paymentMethod: String = "CASH",
        customerId: Long? = null,
        discountAmount: Long = 0L,
        now: Long = System.currentTimeMillis()
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            appDatabase.withTransaction {
                completeSaleInTransaction(
                    cartItems = cartItems.map { (productId, quantity) ->
                        CartLineRequest(productId = productId, quantity = quantity)
                    },
                    paymentMethod = paymentMethod,
                    customerId = customerId,
                    discountAmount = discountAmount,
                    now = now
                ).saleId
            }
        }
    }

    suspend fun completeSale(
        cartItems: List<CartLineRequest>,
        paymentMethod: String = "CASH",
        customerId: Long? = null,
        discountAmount: Long = 0L,
        now: Long = System.currentTimeMillis()
    ): Result<Long> = completeSaleRequests(
        cartLineRequests = cartItems,
        paymentMethod = paymentMethod,
        customerId = customerId,
        discountAmount = discountAmount,
        now = now
    )

    suspend fun completeSaleRequests(
        cartLineRequests: List<CartLineRequest>,
        paymentMethod: String = "CASH",
        customerId: Long? = null,
        discountAmount: Long = 0L,
        now: Long = System.currentTimeMillis()
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            appDatabase.withTransaction {
                completeSaleInTransaction(
                    cartItems = cartLineRequests,
                    paymentMethod = paymentMethod,
                    customerId = customerId,
                    discountAmount = discountAmount,
                    now = now
                ).saleId
            }
        }
    }

    suspend fun completeSaleInTransaction(
        cartItems: List<CartLineRequest>,
        paymentMethod: String = "CASH",
        customerId: Long? = null,
        discountAmount: Long = 0L,
        now: Long = System.currentTimeMillis()
    ): SaleCommitResult {
        require(cartItems.isNotEmpty()) { "Keranjang kosong" }
        require(paymentMethod.uppercase() in setOf("CASH", "QRIS", "CREDIT")) {
            "Metode pembayaran tidak didukung"
        }
        require(paymentMethod.uppercase() != "CREDIT" || customerId != null) {
            "Pelanggan wajib dipilih untuk transaksi kredit"
        }

        val methodUpper = paymentMethod.uppercase()
        val customer = customerId?.let { id ->
            customerDao.getCustomerById(id, businessId)
                ?: if (methodUpper == "CREDIT") throw IllegalStateException("Pelanggan tidak ditemukan") else null
        }
        val productMap = linkedMapOf<Long, ProductEntity>()
        cartItems.forEach { item ->
            require(item.quantity.isFinite() && item.quantity > 0.0) {
                "Jumlah item harus lebih besar dari 0"
            }
            val product = productDao.getProductById(item.productId, businessId)
                ?: throw IllegalStateException("Produk tidak ditemukan")
            if (product.itemType == ItemType.DIGITAL.name &&
                product.fulfillmentMode == FulfillmentMode.PROVIDER.name
            ) {
                require(!product.digitalProviderId.isNullOrBlank()) {
                    "Provider digital produk belum dikonfigurasi"
                }
                require(!product.digitalProductCode.isNullOrBlank()) {
                    "Kode produk digital belum dikonfigurasi"
                }
                require(!item.destinationNumber.isNullOrBlank()) {
                    "Nomor tujuan digital wajib diisi"
                }
            }
            if (ItemType.isStockable(product.itemType) && product.stock < item.quantity) {
                throw IllegalStateException("Stok ${product.name} tidak mencukupi")
            }
            productMap[item.productId] = product
        }

        val grossSubtotal = cartItems.sumOf { item ->
            productMap.getValue(item.productId).sellingPrice * item.quantity.toLong()
        }
        require(grossSubtotal > 0L) { "Total transaksi harus lebih dari 0" }

        val discountResult = id.skmnetwork.bukuwarung.domain.discount.DiscountCalculator.calculateFixed(
            grossSubtotal = grossSubtotal,
            fixedAmount = discountAmount
        )
        val safeDiscount = discountResult.discountAmount
        val netTotal = discountResult.netTotal
        val trxNumber = "TRX-$now"
        val trxUuid = UUID.randomUUID().toString()
        val saleTransaction = SaleTransactionEntity(
            uuid = trxUuid,
            transactionNumber = trxNumber,
            transactionDate = now,
            totalAmount = netTotal,
            discountAmount = safeDiscount,
            paymentMethod = methodUpper,
            customerId = customer?.id,
            businessId = businessId,
            createdAt = now
        )
        val saleId = saleDao.insertTransaction(saleTransaction)

        val saleItems = cartItems.map { item ->
            val product = productMap.getValue(item.productId)
            SaleItemEntity(
                saleUuid = trxUuid,
                productUuid = product.uuid,
                transactionId = saleId,
                productId = item.productId,
                productName = product.name,
                quantity = item.quantity,
                price = product.sellingPrice,
                purchasePrice = product.purchasePrice,
                subtotal = product.sellingPrice * item.quantity.toLong(),
                businessId = businessId
            )
        }
        val saleItemIds = saleDao.insertSaleItems(saleItems)
        require(saleItemIds.size == cartItems.size) {
            "Jumlah item penjualan tidak sesuai dengan keranjang"
        }

        cartItems.forEach { item ->
            val product = productMap.getValue(item.productId)
            if (ItemType.isStockable(product.itemType)) {
                val newStock = product.stock - item.quantity
                productDao.deductProductStock(item.productId, item.quantity, now, businessId)
                stockMovementDao.insertMovement(
                    StockMovementEntity(
                        businessId = businessId,
                        productUuid = product.uuid,
                        movementType = "SALE",
                        deltaQuantity = -item.quantity,
                        currentStockSnapshot = newStock,
                        referenceUuid = trxUuid,
                        note = "Penjualan $trxNumber",
                        createdAt = now
                    )
                )
            }
        }

        when (methodUpper) {
            "CASH" -> {
                if (netTotal > 0L) {
                    cashDao.insertCashTransaction(
                        CashTransactionEntity(
                            type = "INCOME",
                            amount = netTotal,
                            description = "Penjualan $trxNumber",
                            refId = saleId,
                            refUuid = trxUuid,
                            businessId = businessId,
                            createdAt = now
                        )
                    )
                }
            }
            "CREDIT" -> {
                debtDao.insertDebt(
                    DebtEntity(
                        uuid = UUID.randomUUID().toString(),
                        customerId = customer!!.id,
                        saleTransactionId = saleId,
                        customerUuid = customer.uuid,
                        saleUuid = trxUuid,
                        totalDebt = netTotal,
                        paidAmount = 0L,
                        status = if (netTotal == 0L) "PAID" else "OPEN",
                        businessId = businessId,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
            "QRIS" -> Unit
        }

        syncQueueDao.insert(
            SyncQueueEntity(
                businessId = businessId,
                deviceId = saleTransaction.deviceId,
                entityType = "SALE",
                entityUuid = trxUuid,
                operation = "INSERT",
                createdAt = now,
                updatedAt = now
            )
        )

        return SaleCommitResult(saleId = saleId, saleItemIds = saleItemIds)
    }

    /**
     * Executes atomic sale return for partial or full items.
     * Guaranteed invariant preservation:
     * - Physical items returned increment stock & record StockMovement(RETURN, +qty)
     * - Refund amount strictly <= amount actually paid (net total)
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
                val sale = saleDao.getTransactionById(saleId, businessId)
                    ?: throw IllegalStateException("Transaksi penjualan tidak ditemukan")

                // 2. Validate requested items belong to this sale and within returnable limit
                val originalSaleItems = saleDao.getItemsForTransaction(saleId, businessId)
                val saleItemMap = originalSaleItems.associateBy { it.id }

                var calculatedItemRefund = 0L
                val validatedReturnItems = mutableListOf<Pair<SaleItemEntity, Double>>()

                for ((saleItemId, returnQty) in itemsToReturn) {
                    if (returnQty <= 0.0) {
                        throw IllegalArgumentException("Kuantiti retur harus lebih dari 0")
                    }
                    val saleItem = saleItemMap[saleItemId]
                        ?: throw IllegalArgumentException("Item penjualan ID $saleItemId tidak ditemukan pada transaksi ini")

                    val alreadyReturned = saleReturnDao.getReturnedQuantityForSaleItem(saleItemId, businessId) ?: 0.0
                    val remainingReturnable = saleItem.quantity - alreadyReturned
                    if (returnQty > remainingReturnable) {
                        throw IllegalArgumentException(
                            "Kuantiti retur untuk ${saleItem.productName} ($returnQty) melebihi batas yang dapat diretur ($remainingReturnable)"
                        )
                    }

                    val itemRefund = (saleItem.price * returnQty).toLong()
                    calculatedItemRefund += itemRefund
                    validatedReturnItems.add(Pair(saleItem, returnQty))
                }

                // Invariant: refund <= amount actually paid (net total of sale)
                val previousReturns = saleReturnDao.getReturnsListForSale(businessId, saleId)
                val totalAlreadyRefunded = previousReturns.sumOf { it.totalRefundAmount }
                val maxRemainingRefundable = (sale.totalAmount - totalAlreadyRefunded).coerceAtLeast(0L)
                val totalRefundAmount = calculatedItemRefund.coerceAtMost(maxRemainingRefundable)

                if (totalRefundAmount <= 0L) {
                    throw IllegalStateException("Total pengembalian dana harus lebih dari 0 atau sudah mencapai batas maksimal")
                }

                val returnNumber = "RET-$now"
                val returnUuid = UUID.randomUUID().toString()
                val customer = sale.customerId?.let { customerDao.getCustomerById(it, businessId) }

                val cleanedReason = reason?.trim()?.ifEmpty { null }
                val cleanedNotes = notes?.trim()?.ifEmpty { null }

                // 3. Create Return Transaction Record
                val returnTransaction = SaleReturnTransactionEntity(
                    uuid = returnUuid,
                    businessId = businessId,
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
                        businessId = businessId,
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

                // 5. Restock Physical & Fuel Products & append StockMovement RETURN
                for ((saleItem, returnQty) in validatedReturnItems) {
                    val product = productDao.getProductByIdRaw(saleItem.productId)
                    if (product != null && ItemType.isStockable(product.itemType)) {
                        productDao.addProductStock(product.id, returnQty, now, businessId)
                        val newStock = product.stock + returnQty
                        stockMovementDao.insertMovement(
                            StockMovementEntity(
                                businessId = businessId,
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
                            businessId = businessId,
                            createdAt = now
                        )
                        cashDao.insertCashTransaction(cashExpense)
                    }
                    "CREDIT" -> {
                        val linkedDebt = debtDao.getDebtBySaleId(sale.id, businessId)
                            ?: (if (sale.uuid.isNotBlank()) debtDao.getDebtBySaleUuid(sale.uuid, businessId) else null)

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
                                        businessId = businessId,
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
                                businessId = businessId,
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
        val sale = saleDao.getTransactionById(saleId, businessId) ?: return@withContext null
        val items = saleDao.getItemsForTransaction(saleId, businessId)
        val customer = sale.customerId?.let { customerDao.getCustomerById(it, businessId) }
        val debt = if (sale.paymentMethod == "CREDIT" && sale.customerId != null) {
            debtDao.getOpenDebtsForCustomerList(sale.customerId, businessId).find { it.saleTransactionId == saleId }
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
