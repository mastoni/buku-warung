package id.skmnetwork.bukuwarung.data.repository

import androidx.room.withTransaction
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseOrderEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseOrderItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseOrderStatus
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
}
