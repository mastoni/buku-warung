package id.skmnetwork.bukuwarung.data.repository

import androidx.room.withTransaction
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CashTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.ItemType
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.StockMovementEntity
import id.skmnetwork.bukuwarung.data.local.entity.SupplierEntity
import id.skmnetwork.bukuwarung.data.local.entity.SupplierPayableEntity
import id.skmnetwork.bukuwarung.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

class PurchaseRepository(
    private val appDatabase: AppDatabase,
    private val businessId: String,
    private val transactionRunner: (suspend (suspend () -> Any?) -> Any?)? = null
) {
    private val purchaseDao = appDatabase.purchaseDao()
    private val productDao = appDatabase.productDao()
    private val supplierDao = appDatabase.supplierDao()
    private val supplierPayableDao = appDatabase.supplierPayableDao()
    private val cashDao = appDatabase.cashDao()
    private val stockMovementDao = appDatabase.stockMovementDao()
    private val syncQueueDao = appDatabase.syncQueueDao()

    val allTransactions: Flow<List<PurchaseTransactionEntity>> = purchaseDao.getAllPurchaseTransactions(businessId)

    private suspend fun <T> runInTransaction(block: suspend () -> T): T {
        return if (transactionRunner != null) {
            @Suppress("UNCHECKED_CAST")
            transactionRunner.invoke { block() } as T
        } else {
            appDatabase.withTransaction { block() }
        }
    }

    suspend fun getTransactionById(id: Long): PurchaseTransactionEntity? = withContext(Dispatchers.IO) {
        purchaseDao.getTransactionById(id, businessId)
    }

    suspend fun getItemsForTransaction(transactionId: Long): List<PurchaseItemEntity> = withContext(Dispatchers.IO) {
        purchaseDao.getItemsForPurchase(transactionId, businessId)
    }

    suspend fun completePurchase(
        purchaseItems: Map<Long, Double>,
        paymentMethod: String = "CASH",
        supplierId: Long? = null,
        now: Long = System.currentTimeMillis()
    ): Result<Long> = withContext(Dispatchers.IO) {
        if (purchaseItems.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Keranjang belanja kosong"))
        }

        val methodUpper = paymentMethod.uppercase()
        if (methodUpper == "CREDIT" && supplierId == null) {
            return@withContext Result.failure(IllegalArgumentException("Supplier wajib dipilih untuk transaksi hutang/kredit"))
        }

        runCatching {
            runInTransaction {
                // 1. Validate supplier if provided or if credit purchase
                val supplier = if (supplierId != null) {
                    supplierDao.getSupplierById(supplierId, businessId)
                        ?: throw IllegalStateException("Supplier tidak ditemukan")
                } else null

                if (methodUpper == "CREDIT" && supplier == null) {
                    throw IllegalStateException("Supplier wajib dipilih untuk transaksi hutang/kredit")
                }

                // 2. Validate all products exist & qty > 0
                val productMap = mutableMapOf<Long, ProductEntity>()
                for ((productId, qty) in purchaseItems) {
                    if (qty <= 0) {
                        throw IllegalArgumentException("Jumlah kuantitas harus lebih dari 0")
                    }
                    val product = productDao.getProductById(productId, businessId)
                        ?: throw IllegalStateException("Produk tidak ditemukan")
                    productMap[productId] = product
                }

                // 3. Calculate Total Amount
                val totalAmount = purchaseItems.entries.sumOf { (prodId, qty) ->
                    val prod = productMap[prodId]!!
                    prod.purchasePrice * qty.toLong()
                }

                if (totalAmount <= 0) {
                    throw IllegalStateException("Total transaksi belanja harus lebih dari 0")
                }

                val purNumber = "PUR-$now"
                val purUuid = UUID.randomUUID().toString()

                // 4. Create Purchase Transaction Record
                val purchaseTransaction = PurchaseTransactionEntity(
                    uuid = purUuid,
                    businessId = businessId,
                    transactionNumber = purNumber,
                    transactionDate = now,
                    totalAmount = totalAmount,
                    paymentMethod = methodUpper,
                    supplierId = supplier?.id,
                    createdAt = now
                )
                val purchaseId = purchaseDao.insertPurchaseTransaction(purchaseTransaction)

                // 5. Create Purchase Items Records
                val itemsList = purchaseItems.map { (prodId, qty) ->
                    val prod = productMap[prodId]!!
                    PurchaseItemEntity(
                        uuid = UUID.randomUUID().toString(),
                        businessId = businessId,
                        purchaseUuid = purUuid,
                        productUuid = prod.uuid,
                        transactionId = purchaseId,
                        productId = prodId,
                        productName = prod.name,
                        quantity = qty,
                        purchasePrice = prod.purchasePrice,
                        subtotal = prod.purchasePrice * qty.toLong()
                    )
                }
                purchaseDao.insertPurchaseItems(itemsList)

                // 6. Add Product Stock in Room & Record StockMovement for stockable products (PHYSICAL and FUEL)
                for ((prodId, qty) in purchaseItems) {
                    val prod = productMap[prodId]!!
                    if (ItemType.isStockable(prod.itemType)) {
                        val newStock = prod.stock + qty
                        productDao.addProductStock(prodId, qty, now, businessId)
                        stockMovementDao.insertMovement(
                            StockMovementEntity(
                                businessId = businessId,
                                productUuid = prod.uuid,
                                movementType = "PURCHASE",
                                deltaQuantity = qty,
                                currentStockSnapshot = newStock,
                                referenceUuid = purUuid,
                                note = "Belanja Barang $purNumber",
                                createdAt = now
                            )
                        )
                    }
                }

                // 7. Payment method routing
                when (methodUpper) {
                    "CASH" -> {
                        val cashExpense = CashTransactionEntity(
                            businessId = businessId,
                            type = "EXPENSE",
                            amount = totalAmount,
                            description = "Belanja Barang $purNumber",
                            refId = purchaseId,
                            refUuid = purUuid,
                            createdAt = now
                        )
                        cashDao.insertCashTransaction(cashExpense)
                    }
                    "CREDIT" -> {
                        val payable = SupplierPayableEntity(
                            uuid = UUID.randomUUID().toString(),
                            businessId = businessId,
                            supplierId = supplier!!.id,
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

                // 8. Atomic Sync Queue Enqueue (Aggregate PURCHASE event)
                syncQueueDao.insert(
                    SyncQueueEntity(
                        businessId = businessId,
                        deviceId = "LEGACY_DEVICE",
                        entityType = "PURCHASE",
                        entityUuid = purUuid,
                        operation = "INSERT",
                        createdAt = now,
                        updatedAt = now
                    )
                )

                purchaseId
            }
        }
    }
}
