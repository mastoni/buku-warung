package id.skmnetwork.bukuwarung.data.repository

import androidx.room.withTransaction
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CashTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.ItemType
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseOrderEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseOrderItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseOrderStatus
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.StockMovementEntity
import id.skmnetwork.bukuwarung.data.local.entity.SupplierPayableEntity
import id.skmnetwork.bukuwarung.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

data class PurchaseOrderItemInput(
    val productId: Long,
    val quantity: Double,
    val notes: String? = null,
    val estimatedPrice: Long? = null
)

class PurchaseOrderRepository(
    private val appDatabase: AppDatabase,
    private val transactionRunner: (suspend (suspend () -> Any?) -> Any?)? = null
) {
    private val purchaseOrderDao = appDatabase.purchaseOrderDao()
    private val productDao = appDatabase.productDao()
    private val supplierDao = appDatabase.supplierDao()
    private val syncQueueDao = appDatabase.syncQueueDao()
    private val purchaseDao = appDatabase.purchaseDao()
    private val cashDao = appDatabase.cashDao()
    private val supplierPayableDao = appDatabase.supplierPayableDao()
    private val stockMovementDao = appDatabase.stockMovementDao()

    val allOrders: Flow<List<PurchaseOrderEntity>> = purchaseOrderDao.getAllPurchaseOrders()

    fun getOrdersByStatus(status: String): Flow<List<PurchaseOrderEntity>> {
        return purchaseOrderDao.getPurchaseOrdersByStatus(status)
    }

    fun getOrdersBySupplier(supplierId: Long): Flow<List<PurchaseOrderEntity>> {
        return purchaseOrderDao.getPurchaseOrdersBySupplier(supplierId)
    }

    suspend fun getOrderById(id: Long): PurchaseOrderEntity? = withContext(Dispatchers.IO) {
        purchaseOrderDao.getPurchaseOrderById(id)
    }

    suspend fun getOrderByUuid(uuid: String): PurchaseOrderEntity? = withContext(Dispatchers.IO) {
        purchaseOrderDao.getPurchaseOrderByUuid(uuid)
    }

    suspend fun getItemsForOrder(orderId: Long): List<PurchaseOrderItemEntity> = withContext(Dispatchers.IO) {
        purchaseOrderDao.getItemsForPurchaseOrder(orderId)
    }

    private suspend fun <T> runInTransaction(block: suspend () -> T): T {
        return if (transactionRunner != null) {
            @Suppress("UNCHECKED_CAST")
            transactionRunner.invoke { block() } as T
        } else {
            appDatabase.withTransaction { block() }
        }
    }

    suspend fun createOrder(
        supplierId: Long,
        items: List<PurchaseOrderItemInput>,
        notes: String? = null,
        now: Long = System.currentTimeMillis()
    ): Result<Long> = withContext(Dispatchers.IO) {
        if (items.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Daftar pesanan (item) tidak boleh kosong"))
        }

        runCatching {
            runInTransaction {
                // 1. Validate supplier
                val supplier = supplierDao.getSupplierById(supplierId)
                    ?: throw IllegalStateException("Supplier tidak ditemukan")

                // 2. Validate products & quantities
                val productMap = mutableMapOf<Long, ProductEntity>()
                for (item in items) {
                    if (item.quantity <= 0.0) {
                        throw IllegalArgumentException("Jumlah kuantitas harus lebih dari 0")
                    }
                    val product = productDao.getProductById(item.productId)
                        ?: throw IllegalStateException("Produk dengan ID ${item.productId} tidak ditemukan")
                    productMap[item.productId] = product
                }

                // 3. Prepare item entities and calculate total estimated amount
                val poUuid = UUID.randomUUID().toString()
                val poNumber = "PO-$now"

                var totalEstimated = 0L
                val orderItems = items.map { itemInput ->
                    val product = productMap[itemInput.productId]!!
                    val unitPrice = itemInput.estimatedPrice ?: product.purchasePrice
                    val subtotal = (itemInput.quantity * unitPrice).toLong()
                    totalEstimated += subtotal

                    PurchaseOrderItemEntity(
                        uuid = UUID.randomUUID().toString(),
                        purchaseOrderId = 0L,
                        poUuid = poUuid,
                        productId = product.id,
                        productUuid = product.uuid,
                        productName = product.name,
                        orderedQuantity = itemInput.quantity,
                        unit = product.unit,
                        estimatedPrice = unitPrice,
                        estimatedSubtotal = subtotal,
                        receivedQuantity = 0.0,
                        notes = itemInput.notes
                    )
                }

                // 4. Create Purchase Order Entity
                val poEntity = PurchaseOrderEntity(
                    uuid = poUuid,
                    orderNumber = poNumber,
                    supplierId = supplier.id,
                    supplierNameSnapshot = supplier.name,
                    supplierPhoneSnapshot = supplier.phone,
                    status = PurchaseOrderStatus.DRAFT.name,
                    totalEstimatedAmount = totalEstimated,
                    notes = notes,
                    createdAt = now,
                    updatedAt = now,
                    sentAt = null,
                    receivedAt = null,
                    finalPurchaseId = null
                )

                val orderId = purchaseOrderDao.insertPurchaseOrder(poEntity)

                // 5. Insert order items with resolved purchaseOrderId
                val finalizedItems = orderItems.map { it.copy(purchaseOrderId = orderId) }
                purchaseOrderDao.insertPurchaseOrderItems(finalizedItems)

                // 6. Enqueue sync event
                syncQueueDao.insert(
                    SyncQueueEntity(
                        businessId = poEntity.businessId,
                        deviceId = poEntity.deviceId,
                        entityType = "PURCHASE_ORDER",
                        entityUuid = poUuid,
                        operation = "INSERT",
                        createdAt = now,
                        updatedAt = now
                    )
                )

                orderId
            }
        }
    }

    suspend fun createOrder(
        supplierId: Long,
        itemsMap: Map<Long, Double>,
        notes: String? = null,
        now: Long = System.currentTimeMillis()
    ): Result<Long> {
        val itemList = itemsMap.map { (prodId, qty) ->
            PurchaseOrderItemInput(productId = prodId, quantity = qty)
        }
        return createOrder(supplierId, itemList, notes, now)
    }

    suspend fun updateOrder(
        orderId: Long,
        supplierId: Long,
        items: List<PurchaseOrderItemInput>,
        notes: String? = null,
        now: Long = System.currentTimeMillis()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (items.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Daftar pesanan (item) tidak boleh kosong"))
        }

        runCatching {
            runInTransaction {
                val existingOrder = purchaseOrderDao.getPurchaseOrderById(orderId)
                    ?: throw IllegalStateException("Purchase Order tidak ditemukan")

                if (existingOrder.status != PurchaseOrderStatus.DRAFT.name) {
                    throw IllegalStateException("Hanya Purchase Order berstatus DRAFT yang dapat diedit (Status saat ini: ${existingOrder.status})")
                }

                // Validate supplier & refresh snapshot
                val supplier = supplierDao.getSupplierById(supplierId)
                    ?: throw IllegalStateException("Supplier tidak ditemukan")

                // Validate products & calculate totals
                val productMap = mutableMapOf<Long, ProductEntity>()
                for (item in items) {
                    if (item.quantity <= 0.0) {
                        throw IllegalArgumentException("Jumlah kuantitas harus lebih dari 0")
                    }
                    val product = productDao.getProductById(item.productId)
                        ?: throw IllegalStateException("Produk dengan ID ${item.productId} tidak ditemukan")
                    productMap[item.productId] = product
                }

                var totalEstimated = 0L
                val orderItems = items.map { itemInput ->
                    val product = productMap[itemInput.productId]!!
                    val unitPrice = itemInput.estimatedPrice ?: product.purchasePrice
                    val subtotal = (itemInput.quantity * unitPrice).toLong()
                    totalEstimated += subtotal

                    PurchaseOrderItemEntity(
                        uuid = UUID.randomUUID().toString(),
                        purchaseOrderId = orderId,
                        poUuid = existingOrder.uuid,
                        productId = product.id,
                        productUuid = product.uuid,
                        productName = product.name,
                        orderedQuantity = itemInput.quantity,
                        unit = product.unit,
                        estimatedPrice = unitPrice,
                        estimatedSubtotal = subtotal,
                        receivedQuantity = 0.0,
                        notes = itemInput.notes
                    )
                }

                val updatedPo = existingOrder.copy(
                    supplierId = supplier.id,
                    supplierNameSnapshot = supplier.name,
                    supplierPhoneSnapshot = supplier.phone,
                    totalEstimatedAmount = totalEstimated,
                    notes = notes,
                    updatedAt = now
                )

                purchaseOrderDao.updatePurchaseOrder(updatedPo)
                purchaseOrderDao.deleteItemsForPurchaseOrder(orderId)
                purchaseOrderDao.insertPurchaseOrderItems(orderItems)

                syncQueueDao.insert(
                    SyncQueueEntity(
                        businessId = updatedPo.businessId,
                        deviceId = updatedPo.deviceId,
                        entityType = "PURCHASE_ORDER",
                        entityUuid = existingOrder.uuid,
                        operation = "UPDATE",
                        createdAt = now,
                        updatedAt = now
                    )
                )
                Unit
            }
        }
    }

    suspend fun markOrderOrdered(
        orderId: Long,
        now: Long = System.currentTimeMillis()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            runInTransaction {
                val order = purchaseOrderDao.getPurchaseOrderById(orderId)
                    ?: throw IllegalStateException("Purchase Order tidak ditemukan")

                if (order.status != PurchaseOrderStatus.DRAFT.name) {
                    throw IllegalStateException("Hanya Purchase Order berstatus DRAFT yang dapat ditandai ORDERED (Status saat ini: ${order.status})")
                }

                val items = purchaseOrderDao.getItemsForPurchaseOrder(orderId)
                if (items.isEmpty()) {
                    throw IllegalStateException("Purchase Order tidak memiliki item")
                }

                purchaseOrderDao.updatePurchaseOrderToOrdered(
                    id = orderId,
                    status = PurchaseOrderStatus.ORDERED.name,
                    sentAt = now,
                    updatedAt = now
                )

                syncQueueDao.insert(
                    SyncQueueEntity(
                        businessId = order.businessId,
                        deviceId = order.deviceId,
                        entityType = "PURCHASE_ORDER",
                        entityUuid = order.uuid,
                        operation = "UPDATE",
                        createdAt = now,
                        updatedAt = now
                    )
                )
                Unit
            }
        }
    }

    suspend fun cancelOrder(
        orderId: Long,
        now: Long = System.currentTimeMillis()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            runInTransaction {
                val order = purchaseOrderDao.getPurchaseOrderById(orderId)
                    ?: throw IllegalStateException("Purchase Order tidak ditemukan")

                if (order.status == PurchaseOrderStatus.RECEIVED.name) {
                    throw IllegalStateException("Purchase Order yang sudah diterima tidak dapat dibatalkan")
                }

                if (order.status == PurchaseOrderStatus.CANCELLED.name) {
                    return@runInTransaction
                }

                purchaseOrderDao.updatePurchaseOrderStatus(
                    id = orderId,
                    status = PurchaseOrderStatus.CANCELLED.name,
                    updatedAt = now
                )

                syncQueueDao.insert(
                    SyncQueueEntity(
                        businessId = order.businessId,
                        deviceId = order.deviceId,
                        entityType = "PURCHASE_ORDER",
                        entityUuid = order.uuid,
                        operation = "UPDATE",
                        createdAt = now,
                        updatedAt = now
                    )
                )
                Unit
            }
        }
    }

    /**
     * Gate G13.6 — Goods Receipt & Purchase Finalization Engine.
     * Atomically converts an ORDERED Purchase Order into a finalized Purchase Transaction
     * with exact stock increments, stock movements, and cash/credit accounting writes.
     */
    suspend fun receiveOrder(
        orderId: Long,
        paymentMethod: String = "CASH",
        now: Long = System.currentTimeMillis()
    ): Result<Long> = withContext(Dispatchers.IO) {
        val methodUpper = paymentMethod.uppercase()
        if (methodUpper != "CASH" && methodUpper != "CREDIT") {
            return@withContext Result.failure(IllegalArgumentException("Metode pembayaran tidak valid: $paymentMethod. Hanya CASH dan CREDIT yang didukung."))
        }

        runCatching {
            runInTransaction {
                // 1. Authoritative check: PO exists, status == ORDERED, finalPurchaseId == null
                val po = purchaseOrderDao.getPurchaseOrderById(orderId)
                    ?: throw IllegalStateException("Purchase Order tidak ditemukan")

                if (po.status == PurchaseOrderStatus.RECEIVED.name || po.finalPurchaseId != null) {
                    throw IllegalStateException("Pesanan sudah diterima sebelumnya")
                }

                if (po.status != PurchaseOrderStatus.ORDERED.name) {
                    throw IllegalStateException("Hanya Purchase Order berstatus ORDERED (Dipesan) yang dapat diterima (Status saat ini: ${po.status})")
                }

                // 2. Validate Supplier exists
                val supplier = supplierDao.getSupplierById(po.supplierId)
                    ?: throw IllegalStateException("Supplier tidak ditemukan")

                // 3. Validate items exist
                val poItems = purchaseOrderDao.getItemsForPurchaseOrder(orderId)
                if (poItems.isEmpty()) {
                    throw IllegalStateException("Purchase Order tidak memiliki item")
                }

                // 4. Validate all products exist, not soft-deleted, and quantity > 0
                val productMap = mutableMapOf<Long, ProductEntity>()
                for (item in poItems) {
                    if (item.orderedQuantity <= 0.0) {
                        throw IllegalArgumentException("Jumlah kuantitas harus lebih dari 0")
                    }
                    val product = productDao.getProductById(item.productId)
                        ?: throw IllegalStateException("Produk ${item.productName} tidak ditemukan")
                    productMap[item.productId] = product
                }

                // 5. Calculate total amount
                var totalAmount = 0L
                val calculatedItems = poItems.map { item ->
                    val product = productMap[item.productId]!!
                    val unitPrice = if (item.estimatedPrice > 0L) item.estimatedPrice else product.purchasePrice
                    if (unitPrice < 0L) {
                        throw IllegalArgumentException("Harga beli tidak boleh negatif")
                    }
                    val subtotal = (item.orderedQuantity * unitPrice).toLong()
                    totalAmount += subtotal
                    Triple(item, product, unitPrice to subtotal)
                }

                if (totalAmount <= 0L) {
                    throw IllegalStateException("Total transaksi pembelian harus lebih dari 0")
                }

                val purNumber = "PUR-$now"
                val purUuid = UUID.randomUUID().toString()

                // 6. Create PurchaseTransactionEntity
                val purchaseTransaction = PurchaseTransactionEntity(
                    uuid = purUuid,
                    businessId = po.businessId,
                    deviceId = po.deviceId,
                    transactionNumber = purNumber,
                    transactionDate = now,
                    totalAmount = totalAmount,
                    paymentMethod = methodUpper,
                    supplierId = supplier.id,
                    createdAt = now
                )
                val purchaseId = purchaseDao.insertPurchaseTransaction(purchaseTransaction)

                // 7. Create PurchaseItemEntity records
                val purchaseItemsToInsert = calculatedItems.map { (poItem, product, pricing) ->
                    val (unitPrice, subtotal) = pricing
                    PurchaseItemEntity(
                        uuid = UUID.randomUUID().toString(),
                        businessId = po.businessId,
                        purchaseUuid = purUuid,
                        productUuid = product.uuid,
                        transactionId = purchaseId,
                        productId = product.id,
                        productName = product.name,
                        quantity = poItem.orderedQuantity,
                        purchasePrice = unitPrice,
                        subtotal = subtotal
                    )
                }
                purchaseDao.insertPurchaseItems(purchaseItemsToInsert)

                // 8. Mutate stock & record StockMovement for stockable items; update PO item receivedQuantity
                for ((poItem, product, _) in calculatedItems) {
                    val qty = poItem.orderedQuantity
                    if (ItemType.isStockable(product.itemType)) {
                        val newStock = product.stock + qty
                        productDao.addProductStock(product.id, qty, now)
                        stockMovementDao.insertMovement(
                            StockMovementEntity(
                                uuid = UUID.randomUUID().toString(),
                                businessId = po.businessId,
                                deviceId = po.deviceId,
                                productUuid = product.uuid,
                                movementType = "PURCHASE",
                                deltaQuantity = qty,
                                currentStockSnapshot = newStock,
                                referenceUuid = purUuid,
                                note = "Penerimaan Pesanan #${po.orderNumber}",
                                createdAt = now
                            )
                        )
                    }
                    purchaseOrderDao.updatePurchaseOrderItemReceivedQuantity(poItem.id, qty)
                }

                // 9. Payment routing
                when (methodUpper) {
                    "CASH" -> {
                        val cashExpense = CashTransactionEntity(
                            uuid = UUID.randomUUID().toString(),
                            businessId = po.businessId,
                            deviceId = po.deviceId,
                            type = "EXPENSE",
                            amount = totalAmount,
                            description = "Pembayaran Pesanan #${po.orderNumber}",
                            refId = purchaseId,
                            refUuid = purUuid,
                            createdAt = now
                        )
                        cashDao.insertCashTransaction(cashExpense)
                    }
                    "CREDIT" -> {
                        val payable = SupplierPayableEntity(
                            uuid = UUID.randomUUID().toString(),
                            businessId = po.businessId,
                            supplierId = supplier.id,
                            purchaseTransactionId = purchaseId,
                            supplierUuid = supplier.uuid,
                            purchaseUuid = purUuid,
                            totalDebt = totalAmount,
                            paidAmount = 0L,
                            status = "OPEN",
                            createdAt = now,
                            updatedAt = now
                        )
                        supplierPayableDao.insertSupplierPayable(payable)
                    }
                }

                // 10. Update PO status to RECEIVED
                purchaseOrderDao.updatePurchaseOrderToReceived(
                    id = orderId,
                    status = PurchaseOrderStatus.RECEIVED.name,
                    receivedAt = now,
                    finalPurchaseId = purchaseId,
                    updatedAt = now
                )

                // 11. Sync Queue entries
                syncQueueDao.insert(
                    SyncQueueEntity(
                        businessId = purchaseTransaction.businessId,
                        deviceId = purchaseTransaction.deviceId,
                        entityType = "PURCHASE",
                        entityUuid = purUuid,
                        operation = "INSERT",
                        createdAt = now,
                        updatedAt = now
                    )
                )

                syncQueueDao.insert(
                    SyncQueueEntity(
                        businessId = po.businessId,
                        deviceId = po.deviceId,
                        entityType = "PURCHASE_ORDER",
                        entityUuid = po.uuid,
                        operation = "UPDATE",
                        createdAt = now,
                        updatedAt = now
                    )
                )

                purchaseId
            }
        }
    }
}
