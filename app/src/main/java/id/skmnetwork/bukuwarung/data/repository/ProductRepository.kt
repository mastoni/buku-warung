package id.skmnetwork.bukuwarung.data.repository

import androidx.room.withTransaction
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CashTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.local.entity.FulfillmentMode
import id.skmnetwork.bukuwarung.data.local.entity.ItemType
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.StockMovementEntity
import id.skmnetwork.bukuwarung.data.local.entity.SyncQueueEntity
import id.skmnetwork.bukuwarung.data.repository.DigitalTransactionRepository
import id.skmnetwork.bukuwarung.domain.checkout.CartLine
import id.skmnetwork.bukuwarung.domain.checkout.CartLineRequest
import id.skmnetwork.bukuwarung.domain.checkout.toRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.UUID

class ProductRepository(
    private val appDatabase: AppDatabase,
    private val businessId: String
) {
    private val categoryDao = appDatabase.categoryDao()
    private val productDao = appDatabase.productDao()
    private val saleDao = appDatabase.saleDao()
    private val cashDao = appDatabase.cashDao()
    private val purchaseDao = appDatabase.purchaseDao()
    private val stockMovementDao = appDatabase.stockMovementDao()
    private val syncQueueDao = appDatabase.syncQueueDao()

    val allProducts: Flow<List<ProductEntity>> = productDao.getAllProducts(businessId)
    val allCategories: Flow<List<CategoryEntity>> = categoryDao.getAllCategories(businessId)
    val totalCashBalance: Flow<Long?> = cashDao.getTotalCashBalance(businessId)
    val allCashTransactions: Flow<List<CashTransactionEntity>> = cashDao.getAllCashTransactions(businessId)
    val allPurchases: Flow<List<PurchaseTransactionEntity>> = purchaseDao.getAllPurchaseTransactions(businessId)

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

        return cashDao.getCashExpenseTotal(businessId, startOfDay, endOfDay)
    }

    suspend fun getProductById(id: Long): ProductEntity? = withContext(Dispatchers.IO) {
        productDao.getProductById(id, businessId)
    }

    suspend fun getProductByBarcode(barcode: String): ProductEntity? = withContext(Dispatchers.IO) {
        val trimmed = barcode.trim()
        if (trimmed.isEmpty()) null else productDao.getProductByBarcode(trimmed, businessId)
    }

    suspend fun getCategoryById(id: Long): CategoryEntity? = withContext(Dispatchers.IO) {
        categoryDao.getCategoryById(id, businessId)
    }

    suspend fun createCategory(name: String): Result<CategoryEntity> = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Nama kategori tidak boleh kosong"))
        }
        val existing = categoryDao.getCategoryByNameIgnoreCase(trimmed, businessId)
        if (existing != null) {
            return@withContext Result.success(existing)
        }
        val newCategory = CategoryEntity(name = trimmed, businessId = businessId)
        val id = categoryDao.insertCategory(newCategory)
        Result.success(newCategory.copy(id = id))
    }

    suspend fun insertProductWithCategory(
        name: String,
        categoryName: String,
        purchasePrice: Long,
        sellingPrice: Long,
        stock: Double,
        minimumStock: Double,
        unit: String,
        barcode: String? = null,
        imageUri: String? = null,
        itemType: ItemType = ItemType.PHYSICAL,
        categoryId: Long? = null,
        fulfillmentMode: FulfillmentMode = FulfillmentMode.MANUAL,
        digitalProviderId: String? = null,
        digitalProductCode: String? = null
    ): Long = withContext(Dispatchers.IO) {
        val resolvedCategoryId = if (categoryId != null && categoryId > 0 && categoryDao.getCategoryById(categoryId, businessId) != null) {
            categoryId
        } else {
            val finalCategoryName = categoryName.trim().ifEmpty { "Umum" }
            val existingCategory = categoryDao.getCategoryByNameIgnoreCase(finalCategoryName, businessId)
            existingCategory?.id ?: categoryDao.insertCategory(
                CategoryEntity(name = finalCategoryName, businessId = businessId)
            )
        }

        val prodUuid = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val product = ProductEntity(
            uuid = prodUuid,
            businessId = businessId,
            categoryId = resolvedCategoryId,
            name = name.trim(),
            purchasePrice = purchasePrice,
            sellingPrice = sellingPrice,
            stock = stock,
            minimumStock = minimumStock,
            unit = unit.trim().ifEmpty { "pcs" },
            itemType = itemType.name,
            fulfillmentMode = fulfillmentMode.name,
            digitalProviderId = digitalProviderId?.trim()?.ifEmpty { null },
            digitalProductCode = digitalProductCode?.trim()?.ifEmpty { null },
            barcode = barcode?.trim()?.ifEmpty { null },
            imageUri = imageUri?.trim()?.ifEmpty { null },
            createdAt = now,
            updatedAt = now
        )

        appDatabase.withTransaction {
            val prodId = productDao.insertProduct(product)
            if (itemType.isStockable && stock != 0.0) {
                stockMovementDao.insertMovement(
                    StockMovementEntity(
                        businessId = businessId,
                        productUuid = prodUuid,
                        movementType = "INITIAL",
                        deltaQuantity = stock,
                        currentStockSnapshot = stock,
                        note = "Initial stock saat penambahan produk",
                        createdAt = now
                    )
                )
            }
            syncQueueDao.insert(
                SyncQueueEntity(
                    businessId = businessId,
                    deviceId = "LEGACY_DEVICE",
                    entityType = "PRODUCT",
                    entityUuid = prodUuid,
                    operation = "INSERT",
                    createdAt = now,
                    updatedAt = now
                )
            )
            prodId
        }
    }

    suspend fun updateProductWithCategory(
        productId: Long,
        name: String,
        categoryName: String,
        purchasePrice: Long,
        sellingPrice: Long,
        stock: Double,
        minimumStock: Double,
        unit: String,
        barcode: String? = null,
        imageUri: String? = null,
        categoryId: Long? = null,
        itemType: ItemType? = null,
        fulfillmentMode: FulfillmentMode? = null,
        digitalProviderId: String? = null,
        digitalProductCode: String? = null
    ) = withContext(Dispatchers.IO) {
        val resolvedCategoryId = if (categoryId != null && categoryId > 0 && categoryDao.getCategoryById(categoryId, businessId) != null) {
            categoryId
        } else {
            val finalCategoryName = categoryName.trim().ifEmpty { "Umum" }
            val existingCategory = categoryDao.getCategoryByNameIgnoreCase(finalCategoryName, businessId)
            existingCategory?.id ?: categoryDao.insertCategory(
                CategoryEntity(name = finalCategoryName, businessId = businessId)
            )
        }

        val existingProduct = productDao.getProductById(productId, businessId)
            ?: throw Exception("Produk tidak ditemukan")

        val resolvedItemType = itemType?.name ?: existingProduct.itemType
        val resolvedFulfillmentMode = fulfillmentMode?.name ?: existingProduct.fulfillmentMode
        val isDigitalProvider = resolvedItemType == ItemType.DIGITAL.name &&
                resolvedFulfillmentMode == FulfillmentMode.PROVIDER.name
        val resolvedProviderId = digitalProviderId?.trim()?.ifEmpty { null }
            ?: if (isDigitalProvider) existingProduct.digitalProviderId else null
        val resolvedProductCode = digitalProductCode?.trim()?.ifEmpty { null }
            ?: if (isDigitalProvider) existingProduct.digitalProductCode else null

        val now = System.currentTimeMillis()
        val product = existingProduct.copy(
            categoryId = resolvedCategoryId,
            name = name.trim(),
            purchasePrice = purchasePrice,
            sellingPrice = sellingPrice,
            stock = stock,
            minimumStock = minimumStock,
            unit = unit.trim().ifEmpty { "pcs" },
            itemType = resolvedItemType,
            fulfillmentMode = resolvedFulfillmentMode,
            digitalProviderId = resolvedProviderId,
            digitalProductCode = resolvedProductCode,
            barcode = barcode?.trim()?.ifEmpty { null },
            imageUri = imageUri?.trim()?.ifEmpty { null },
            updatedAt = now
        )

        appDatabase.withTransaction {
            val stockDiff = stock - existingProduct.stock
            if (stockDiff != 0.0 && ItemType.isStockable(existingProduct.itemType)) {
                stockMovementDao.insertMovement(
                    StockMovementEntity(
                        businessId = businessId,
                        productUuid = existingProduct.uuid,
                        movementType = "ADJUSTMENT",
                        deltaQuantity = stockDiff,
                        currentStockSnapshot = stock,
                        note = "Penyesuaian stok saat update produk",
                        createdAt = now
                    )
                )
            }
            productDao.updateProduct(product)
            syncQueueDao.insert(
                SyncQueueEntity(
                    businessId = businessId,
                    deviceId = "LEGACY_DEVICE",
                    entityType = "PRODUCT",
                    entityUuid = existingProduct.uuid,
                    operation = "UPDATE",
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    suspend fun deleteProductById(productId: Long) = withContext(Dispatchers.IO) {
        val existingProduct = productDao.getProductById(productId, businessId)
            ?: throw Exception("Produk tidak ditemukan")
        val now = System.currentTimeMillis()

        appDatabase.withTransaction {
            productDao.softDeleteProduct(productId, now, businessId)
            syncQueueDao.insert(
                SyncQueueEntity(
                    businessId = businessId,
                    deviceId = "LEGACY_DEVICE",
                    entityType = "PRODUCT",
                    entityUuid = existingProduct.uuid,
                    operation = "DELETE",
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    private val saleRepository = SaleRepository(appDatabase, businessId)
    private val digitalTransactionRepository = DigitalTransactionRepository(appDatabase.digitalTransactionDao())
    private val purchaseRepository = PurchaseRepository(appDatabase, businessId)
    private val cashRepository = CashRepository(appDatabase, businessId)
    private val stockRepository = StockRepository(appDatabase, businessId)

    suspend fun processAtomicCheckout(
        cartItems: Map<Long, Double>,
        paymentMethod: String = "CASH",
        discountAmount: Long = 0L
    ): Result<Long> = withContext(Dispatchers.IO) {
        saleRepository.completeSale(
            cartItems = cartItems.map { (productId, quantity) ->
                CartLineRequest(productId = productId, quantity = quantity)
            },
            paymentMethod = paymentMethod,
            discountAmount = discountAmount
        )
    }

    suspend fun processAtomicCheckout(
        cartLines: List<CartLine>,
        paymentMethod: String = "CASH",
        discountAmount: Long = 0L
    ): Result<Long> = withContext(Dispatchers.IO) {
        saleRepository.completeSaleRequests(
            cartLineRequests = cartLines.map { it.toRequest() },
            paymentMethod = paymentMethod,
            discountAmount = discountAmount
        )
    }

    suspend fun getReceiptData(
        saleId: Long,
        userSettings: id.skmnetwork.bukuwarung.data.preferences.UserSettings? = null,
        cashGiven: Long? = null
    ): id.skmnetwork.bukuwarung.domain.receipt.ReceiptData? = saleRepository.getReceiptData(saleId, userSettings, cashGiven)

    suspend fun getPurchaseItems(transactionId: Long): List<PurchaseItemEntity> = withContext(Dispatchers.IO) {
        purchaseDao.getItemsForPurchase(transactionId, businessId)
    }

    suspend fun processAtomicPurchase(
        purchaseItems: Map<Long, Double>,
        supplierId: Long? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        purchaseRepository.completePurchase(
            purchaseItems = purchaseItems,
            paymentMethod = "CASH",
            supplierId = supplierId
        ).map { }
    }

    val allSales: Flow<List<SaleTransactionEntity>> = saleRepository.allTransactions
    val allReturns: Flow<List<id.skmnetwork.bukuwarung.data.local.entity.SaleReturnTransactionEntity>> = saleRepository.allReturns

    suspend fun getSaleItems(transactionId: Long): List<SaleItemEntity> = withContext(Dispatchers.IO) {
        saleRepository.getItemsForTransaction(transactionId)
    }

    suspend fun getDigitalTransactionsBySaleItemIds(saleItemIds: List<Long>): List<id.skmnetwork.bukuwarung.data.local.entity.DigitalTransactionEntity> = withContext(Dispatchers.IO) {
        digitalTransactionRepository.getBySaleItemIds(saleItemIds)
    }

    suspend fun checkDigitalTransactionStatus(saleItemId: Long): id.skmnetwork.bukuwarung.data.local.entity.DigitalTransactionEntity? = withContext(Dispatchers.IO) {
        val digitalTxs = digitalTransactionRepository.getBySaleItemIds(listOf(saleItemId))
        if (digitalTxs.isEmpty()) return@withContext null
        val digitalTx = digitalTxs.first()
        digitalTransactionRepository.checkStatus(digitalTx.id)
        digitalTransactionRepository.getBySaleItemIds(listOf(saleItemId)).firstOrNull()
    }

    suspend fun getReturnsForSale(saleId: Long): List<id.skmnetwork.bukuwarung.data.local.entity.SaleReturnTransactionEntity> = withContext(Dispatchers.IO) {
        saleRepository.getReturnsListForSale(saleId)
    }

    suspend fun getReturnItems(returnId: Long): List<id.skmnetwork.bukuwarung.data.local.entity.SaleReturnItemEntity> = withContext(Dispatchers.IO) {
        saleRepository.getItemsForReturn(returnId)
    }

    suspend fun getReturnableQuantities(saleId: Long): Map<Long, Double> = withContext(Dispatchers.IO) {
        saleRepository.getReturnableQuantitiesForSale(saleId)
    }

    suspend fun getReturnedQuantityForSaleItem(saleItemId: Long): Double = withContext(Dispatchers.IO) {
        saleRepository.getReturnedQuantityForSaleItem(saleItemId)
    }

    suspend fun processSaleReturn(
        saleId: Long,
        itemsToReturn: Map<Long, Double>,
        reason: String? = null,
        notes: String? = null
    ): Result<Long> = withContext(Dispatchers.IO) {
        saleRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = itemsToReturn,
            reason = reason,
            notes = notes
        )
    }

    suspend fun addManualCashTransaction(
        type: String,
        amount: Long,
        description: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (type.uppercase() == "INCOME") {
            cashRepository.recordManualIncome(amount, description).map { }
        } else {
            cashRepository.recordManualExpense(amount, description).map { }
        }
    }
}
