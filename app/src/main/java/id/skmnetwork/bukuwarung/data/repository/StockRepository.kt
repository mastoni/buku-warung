package id.skmnetwork.bukuwarung.data.repository

import androidx.room.withTransaction
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.StockMovementEntity
import id.skmnetwork.bukuwarung.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

class StockRepository(
    private val appDatabase: AppDatabase,
    private val businessId: String
) {
    private val stockMovementDao = appDatabase.stockMovementDao()
    private val productDao = appDatabase.productDao()
    private val syncQueueDao = appDatabase.syncQueueDao()

    fun getStockMovementHistory(productUuid: String): Flow<List<StockMovementEntity>> {
        return stockMovementDao.getMovementsForProduct(businessId, productUuid)
    }

    suspend fun getStockMovementList(productUuid: String): List<StockMovementEntity> = withContext(Dispatchers.IO) {
        stockMovementDao.getMovementsListForProduct(businessId, productUuid)
    }

    fun getAllStockMovements(): Flow<List<StockMovementEntity>> {
        return stockMovementDao.getAllMovements(businessId)
    }

    suspend fun getStockBalanceFromLedger(productUuid: String): Double = withContext(Dispatchers.IO) {
        stockMovementDao.getCalculatedStockForProduct(businessId, productUuid)
    }

    suspend fun getProductStock(productId: Long): Double? = withContext(Dispatchers.IO) {
        productDao.getProductById(productId, businessId)?.stock
    }

    suspend fun recordSaleMovement(
        productUuid: String,
        deltaQuantity: Double,
        currentStockSnapshot: Double,
        saleUuid: String,
        note: String? = null,
        createdAt: Long = System.currentTimeMillis()
    ): Long = withContext(Dispatchers.IO) {
        val movement = StockMovementEntity(
            businessId = businessId,
            productUuid = productUuid,
            movementType = "SALE",
            deltaQuantity = if (deltaQuantity > 0) -deltaQuantity else deltaQuantity,
            currentStockSnapshot = currentStockSnapshot,
            referenceUuid = saleUuid,
            note = note ?: "Penjualan",
            createdAt = createdAt
        )
        stockMovementDao.insertMovement(movement)
    }

    suspend fun recordPurchaseMovement(
        productUuid: String,
        deltaQuantity: Double,
        currentStockSnapshot: Double,
        purchaseUuid: String,
        note: String? = null,
        createdAt: Long = System.currentTimeMillis()
    ): Long = withContext(Dispatchers.IO) {
        val movement = StockMovementEntity(
            businessId = businessId,
            productUuid = productUuid,
            movementType = "PURCHASE",
            deltaQuantity = if (deltaQuantity < 0) -deltaQuantity else deltaQuantity,
            currentStockSnapshot = currentStockSnapshot,
            referenceUuid = purchaseUuid,
            note = note ?: "Pembelian",
            createdAt = createdAt
        )
        stockMovementDao.insertMovement(movement)
    }

    suspend fun recordAdjustment(
        productUuid: String,
        deltaQuantity: Double,
        currentStockSnapshot: Double,
        note: String? = null,
        createdAt: Long = System.currentTimeMillis()
    ): Long = withContext(Dispatchers.IO) {
        val movementUuid = UUID.randomUUID().toString()
        val movement = StockMovementEntity(
            uuid = movementUuid,
            businessId = businessId,
            productUuid = productUuid,
            movementType = "ADJUSTMENT",
            deltaQuantity = deltaQuantity,
            currentStockSnapshot = currentStockSnapshot,
            referenceUuid = null,
            note = note ?: "Penyesuaian stok",
            createdAt = createdAt
        )
        appDatabase.withTransaction {
            val id = stockMovementDao.insertMovement(movement)
            syncQueueDao.insert(
                SyncQueueEntity(
                    businessId = businessId,
                    deviceId = "LEGACY_DEVICE",
                    entityType = "STOCK_ADJUSTMENT",
                    entityUuid = movementUuid,
                    operation = "INSERT",
                    createdAt = createdAt,
                    updatedAt = createdAt
                )
            )
            id
        }
    }

    suspend fun recordInitial(
        productUuid: String,
        stock: Double,
        createdAt: Long = System.currentTimeMillis()
    ): Long = withContext(Dispatchers.IO) {
        val movement = StockMovementEntity(
            businessId = businessId,
            productUuid = productUuid,
            movementType = "INITIAL",
            deltaQuantity = stock,
            currentStockSnapshot = stock,
            referenceUuid = null,
            note = "Initial stock",
            createdAt = createdAt
        )
        stockMovementDao.insertMovement(movement)
    }
}
