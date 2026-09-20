package id.skmnetwork.bukuwarung

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.SyncQueueEntity
import id.skmnetwork.bukuwarung.data.repository.CashRepository
import id.skmnetwork.bukuwarung.data.repository.CustomerRepository
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import id.skmnetwork.bukuwarung.data.repository.PurchaseRepository
import id.skmnetwork.bukuwarung.data.repository.SaleRepository
import id.skmnetwork.bukuwarung.data.repository.StockRepository
import id.skmnetwork.bukuwarung.data.repository.SupplierRepository
import id.skmnetwork.bukuwarung.sync.LinearBackoffRetryPolicy
import id.skmnetwork.bukuwarung.sync.MockSyncProvider
import id.skmnetwork.bukuwarung.sync.SyncProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FoundationStep4Test {

    private lateinit var database: AppDatabase
    private lateinit var context: Context

    private lateinit var productRepository: ProductRepository
    private lateinit var saleRepository: SaleRepository
    private lateinit var purchaseRepository: PurchaseRepository
    private lateinit var customerRepository: CustomerRepository
    private lateinit var supplierRepository: SupplierRepository
    private lateinit var cashRepository: CashRepository
    private lateinit var stockRepository: StockRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        productRepository = ProductRepository(database, "LEGACY_BUSINESS")
        saleRepository = SaleRepository(database, "LEGACY_BUSINESS")
        purchaseRepository = PurchaseRepository(database, "LEGACY_BUSINESS")
        customerRepository = CustomerRepository(database, "LEGACY_BUSINESS")
        supplierRepository = SupplierRepository(database, "LEGACY_BUSINESS")
        cashRepository = CashRepository(database, "LEGACY_BUSINESS")
        stockRepository = StockRepository(database, "LEGACY_BUSINESS")
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testSyncQueueEntityInsertionAndUniqueness() = runBlocking {
        val dao = database.syncQueueDao()
        val syncId = UUID.randomUUID().toString()

        val item1 = SyncQueueEntity(
            syncId = syncId,
            businessId = "BIZ-01",
            deviceId = "DEV-01",
            entityType = "PRODUCT",
            entityUuid = "PROD-01",
            operation = "INSERT",
            status = "PENDING"
        )
        val id1 = dao.insert(item1)
        assertTrue(id1 > 0)

        // Attempt duplicate sync_id insert -> must fail (SQLiteConstraintException)
        val itemDuplicate = SyncQueueEntity(
            syncId = syncId,
            businessId = "BIZ-01",
            deviceId = "DEV-01",
            entityType = "PRODUCT",
            entityUuid = "PROD-01",
            operation = "INSERT",
            status = "PENDING"
        )
        try {
            dao.insert(itemDuplicate)
            fail("Expected SQLiteConstraintException on duplicate sync_id")
        } catch (e: Exception) {
            assertTrue(e is android.database.sqlite.SQLiteConstraintException || e.message?.contains("UNIQUE") == true)
        }
    }

    @Test
    fun testAtomicBatchClaim() = runBlocking {
        val dao = database.syncQueueDao()
        val now = 1000000L

        // Insert 3 items: 2 eligible (next_attempt_at <= now), 1 future (next_attempt_at > now)
        dao.insert(
            SyncQueueEntity(
                syncId = "S1",
                businessId = "LEGACY_BUSINESS",
                deviceId = "D1",
                entityType = "PRODUCT",
                entityUuid = "P1",
                operation = "INSERT",
                status = "PENDING",
                nextAttemptAt = now - 100
            )
        )
        dao.insert(
            SyncQueueEntity(
                syncId = "S2",
                businessId = "LEGACY_BUSINESS",
                deviceId = "D1",
                entityType = "PRODUCT",
                entityUuid = "P2",
                operation = "INSERT",
                status = "PENDING",
                nextAttemptAt = now
            )
        )
        dao.insert(
            SyncQueueEntity(
                syncId = "S3",
                businessId = "LEGACY_BUSINESS",
                deviceId = "D1",
                entityType = "PRODUCT",
                entityUuid = "P3",
                operation = "INSERT",
                status = "PENDING",
                nextAttemptAt = now + 5000 // Future item
            )
        )

        // Atomic claim batch
        val affected = dao.claimPendingBatch(claimTime = now, limit = 10, businessId = "LEGACY_BUSINESS")
        assertEquals(2, affected)

        val claimed = dao.getClaimedBatch(claimTime = now, businessId = "LEGACY_BUSINESS")
        assertEquals(2, claimed.size)
        assertEquals("S1", claimed[0].syncId)
        assertEquals("PROCESSING", claimed[0].status)
        assertEquals("S2", claimed[1].syncId)
        assertEquals("PROCESSING", claimed[1].status)

        // S3 must remain PENDING
        val s3 = dao.getItemBySyncId("S3", "LEGACY_BUSINESS")
        assertNotNull(s3)
        assertEquals("PENDING", s3?.status)
    }

    @Test
    fun testConcurrentClaimingRaceCondition() = runBlocking {
        val dao = database.syncQueueDao()
        val now = 2000000L

        // Insert 50 PENDING items
        for (i in 1..50) {
            dao.insert(
                SyncQueueEntity(
                    syncId = "CONCUR-$i",
                    businessId = "LEGACY_BUSINESS",
                    deviceId = "D1",
                    entityType = "PRODUCT",
                    entityUuid = "P-$i",
                    operation = "INSERT",
                    status = "PENDING",
                    nextAttemptAt = now
                )
            )
        }

        // Run 5 concurrent claims with different timestamps to distinguish batches
        val results = (1..5).map { workerId ->
            async(Dispatchers.IO) {
                val claimTime = now + workerId
                val affected = dao.claimPendingBatch(claimTime = claimTime, limit = 10, businessId = "LEGACY_BUSINESS")
                val items = if (affected > 0) dao.getClaimedBatch(claimTime = claimTime, businessId = "LEGACY_BUSINESS") else emptyList()
                items.map { it.syncId }
            }
        }.awaitAll()

        val allClaimedSyncIds = results.flatten()
        val uniqueClaimedSyncIds = allClaimedSyncIds.toSet()

        // Invariant: No duplicate claims across concurrent workers
        assertEquals("Each claimed item must be unique", allClaimedSyncIds.size, uniqueClaimedSyncIds.size)
        assertEquals(50, allClaimedSyncIds.size)
    }

    @Test
    fun testSyncProcessorSuccessWorkflow() = runBlocking {
        val dao = database.syncQueueDao()
        val mockProvider = MockSyncProvider()
        val processor = SyncProcessor(dao, mockProvider, LinearBackoffRetryPolicy(), "LEGACY_BUSINESS")

        val now = 3000000L
        dao.insert(
            SyncQueueEntity(
                syncId = "PROC-SUCCESS-1",
                businessId = "LEGACY_BUSINESS",
                deviceId = "D1",
                entityType = "SALE",
                entityUuid = "SALE-UUID-1",
                operation = "INSERT",
                status = "PENDING",
                nextAttemptAt = now
            )
        )

        val result = processor.processBatch(batchSize = 10, now = now)
        assertEquals(1, result.claimedCount)
        assertEquals(1, result.successCount)
        assertEquals(0, result.failedCount)

        val item = dao.getItemBySyncId("PROC-SUCCESS-1", "LEGACY_BUSINESS")
        assertNotNull(item)
        assertEquals("SYNCED", item?.status)
        assertEquals(1, mockProvider.totalProcessed)
    }

    @Test
    fun testSyncProcessorFailureAndRetryBackoff() = runBlocking {
        val dao = database.syncQueueDao()
        val mockProvider = MockSyncProvider().apply {
            simulateFailure = true
            failureErrorMessage = "Network timeout"
        }
        val retryPolicy = LinearBackoffRetryPolicy(initialDelayMillis = 5000L, maxAttempts = 3)
        val processor = SyncProcessor(dao, mockProvider, retryPolicy, "LEGACY_BUSINESS")

        val now = 4000000L
        dao.insert(
            SyncQueueEntity(
                syncId = "PROC-FAIL-1",
                businessId = "LEGACY_BUSINESS",
                deviceId = "D1",
                entityType = "PURCHASE",
                entityUuid = "PUR-UUID-1",
                operation = "INSERT",
                status = "PENDING",
                nextAttemptAt = now,
                attemptCount = 0
            )
        )

        val result = processor.processBatch(batchSize = 10, now = now)
        assertEquals(1, result.claimedCount)
        assertEquals(0, result.successCount)
        assertEquals(1, result.failedCount)

        val item = dao.getItemBySyncId("PROC-FAIL-1", "LEGACY_BUSINESS")
        assertNotNull(item)
        assertEquals("FAILED", item?.status)
        assertEquals("Network timeout", item?.lastError)
        assertEquals(1, item?.attemptCount)
        assertTrue("nextAttemptAt must be >= now + 5000", (item?.nextAttemptAt ?: 0) >= now + 5000L)
    }

    @Test
    fun testSaleRepositoryAtomicEnqueue() = runBlocking {
        val pId = productRepository.insertProductWithCategory(
            name = "Kopi Susu",
            categoryName = "Minuman",
            purchasePrice = 5000,
            sellingPrice = 10000,
            stock = 20.0,
            minimumStock = 2.0,
            unit = "cup"
        )
        val product = productRepository.getProductById(pId)!!

        val initialQueueCount = database.syncQueueDao().getAllItems("LEGACY_BUSINESS").first().size

        // Complete sale
        val saleResult = saleRepository.completeSale(
            cartItems = mapOf(pId to 2.0),
            paymentMethod = "CASH"
        )
        assertTrue(saleResult.isSuccess)
        val saleId = saleResult.getOrThrow()
        val sale = saleRepository.getTransactionById(saleId)!!

        val allQueue = database.syncQueueDao().getAllItems("LEGACY_BUSINESS").first()
        val newQueueItems = allQueue.subList(initialQueueCount, allQueue.size)

        // Must emit single aggregate SALE event
        assertEquals(1, newQueueItems.size)
        val saleEvent = newQueueItems[0]
        assertEquals("SALE", saleEvent.entityType)
        assertEquals(sale.uuid, saleEvent.entityUuid)
        assertEquals("INSERT", saleEvent.operation)
        assertEquals("PENDING", saleEvent.status)
    }

    @Test
    fun testPurchaseRepositoryAtomicEnqueue() = runBlocking {
        val pId = productRepository.insertProductWithCategory(
            name = "Gula Pasir",
            categoryName = "Sembako",
            purchasePrice = 12000,
            sellingPrice = 15000,
            stock = 10.0,
            minimumStock = 5.0,
            unit = "kg"
        )

        val initialQueueCount = database.syncQueueDao().getAllItems("LEGACY_BUSINESS").first().size

        val purchaseResult = purchaseRepository.completePurchase(
            purchaseItems = mapOf(pId to 10.0),
            paymentMethod = "CASH"
        )
        assertTrue(purchaseResult.isSuccess)
        val purchaseId = purchaseResult.getOrThrow()
        val purchase = purchaseRepository.getTransactionById(purchaseId)!!

        val allQueue = database.syncQueueDao().getAllItems("LEGACY_BUSINESS").first()
        val newQueueItems = allQueue.subList(initialQueueCount, allQueue.size)

        // Must emit single aggregate PURCHASE event
        assertEquals(1, newQueueItems.size)
        val purEvent = newQueueItems[0]
        assertEquals("PURCHASE", purEvent.entityType)
        assertEquals(purchase.uuid, purEvent.entityUuid)
        assertEquals("INSERT", purEvent.operation)
        assertEquals("PENDING", purEvent.status)
    }

    @Test
    fun testProductRepositoryAtomicEnqueue() = runBlocking {
        // Insert
        val pId = productRepository.insertProductWithCategory(
            name = "Sabun Mandi",
            categoryName = "Kebersihan",
            purchasePrice = 3000,
            sellingPrice = 4500,
            stock = 15.0,
            minimumStock = 2.0,
            unit = "pcs"
        )
        val prod = productRepository.getProductById(pId)!!

        var queue = database.syncQueueDao().getAllItems("LEGACY_BUSINESS").first()
        val insertEvent = queue.find { it.entityType == "PRODUCT" && it.entityUuid == prod.uuid && it.operation == "INSERT" }
        assertNotNull(insertEvent)

        // Update
        productRepository.updateProductWithCategory(
            productId = pId,
            name = "Sabun Mandi Wangi",
            categoryName = "Kebersihan",
            purchasePrice = 3200,
            sellingPrice = 5000,
            stock = 20.0,
            minimumStock = 2.0,
            unit = "pcs"
        )

        queue = database.syncQueueDao().getAllItems("LEGACY_BUSINESS").first()
        val updateEvent = queue.find { it.entityType == "PRODUCT" && it.entityUuid == prod.uuid && it.operation == "UPDATE" }
        assertNotNull(updateEvent)

        // Delete
        productRepository.deleteProductById(pId)
        queue = database.syncQueueDao().getAllItems("LEGACY_BUSINESS").first()
        val deleteEvent = queue.find { it.entityType == "PRODUCT" && it.entityUuid == prod.uuid && it.operation == "DELETE" }
        assertNotNull(deleteEvent)
    }

    @Test
    fun testCustomerAndDebtPaymentAtomicEnqueue() = runBlocking {
        // 1. Customer Insert
        val customerId = customerRepository.saveCustomer(
            name = "Pak Budi",
            phone = "081234567890",
            address = "Jl. Melati 5"
        )
        val customer = customerRepository.getCustomerById(customerId)!!

        var queue = database.syncQueueDao().getAllItems("LEGACY_BUSINESS").first()
        val custInsertEvent = queue.find { it.entityType == "CUSTOMER" && it.entityUuid == customer.uuid && it.operation == "INSERT" }
        assertNotNull(custInsertEvent)

        // 2. Customer Update
        customerRepository.updateCustomer(
            id = customerId,
            name = "Pak Budi Santoso",
            phone = "081234567890",
            address = "Jl. Melati 5"
        )
        queue = database.syncQueueDao().getAllItems("LEGACY_BUSINESS").first()
        val custUpdateEvent = queue.find { it.entityType == "CUSTOMER" && it.entityUuid == customer.uuid && it.operation == "UPDATE" }
        assertNotNull(custUpdateEvent)

        // 3. Credit sale & Debt payment
        val pId = productRepository.insertProductWithCategory(
            name = "Beras Rojolele",
            categoryName = "Sembako",
            purchasePrice = 50000,
            sellingPrice = 60000,
            stock = 10.0,
            minimumStock = 1.0,
            unit = "karung"
        )
        saleRepository.completeSale(
            cartItems = mapOf(pId to 1.0),
            paymentMethod = "CREDIT",
            customerId = customerId
        )
        val debts = customerRepository.getDebtsForCustomer(customerId).first()
        assertEquals(1, debts.size)
        val debt = debts[0]

        // Record debt payment
        val payResult = customerRepository.processAtomicDebtPayment(debt.id, 30000L, "Cicilan 1")
        assertTrue(payResult.isSuccess)

        queue = database.syncQueueDao().getAllItems("LEGACY_BUSINESS").first()
        val debtPayEvent = queue.find { it.entityType == "DEBT_PAYMENT" && it.operation == "INSERT" }
        assertNotNull(debtPayEvent)
    }

    @Test
    fun testSupplierAndSupplierPaymentAtomicEnqueue() = runBlocking {
        // 1. Supplier Insert
        val supplierId = supplierRepository.saveSupplier(
            name = "Distributor Sembako",
            phone = "089876543210",
            address = "Komp. Pergudangan"
        )
        val supplier = supplierRepository.getSupplierById(supplierId)!!

        var queue = database.syncQueueDao().getAllItems("LEGACY_BUSINESS").first()
        val supInsertEvent = queue.find { it.entityType == "SUPPLIER" && it.entityUuid == supplier.uuid && it.operation == "INSERT" }
        assertNotNull(supInsertEvent)

        // 2. Supplier Update
        supplierRepository.updateSupplier(
            id = supplierId,
            name = "Distributor Sembako Makmur",
            phone = "089876543210",
            address = "Komp. Pergudangan"
        )
        queue = database.syncQueueDao().getAllItems("LEGACY_BUSINESS").first()
        val supUpdateEvent = queue.find { it.entityType == "SUPPLIER" && it.entityUuid == supplier.uuid && it.operation == "UPDATE" }
        assertNotNull(supUpdateEvent)

        // 3. Credit purchase & Supplier payment
        val pId = productRepository.insertProductWithCategory(
            name = "Minyak Goreng 2L",
            categoryName = "Sembako",
            purchasePrice = 28000,
            sellingPrice = 33000,
            stock = 5.0,
            minimumStock = 1.0,
            unit = "pouch"
        )
        purchaseRepository.completePurchase(
            purchaseItems = mapOf(pId to 5.0),
            paymentMethod = "CREDIT",
            supplierId = supplierId
        )
        val payables = supplierRepository.getPayablesForSupplier(supplierId).first()
        assertEquals(1, payables.size)
        val payable = payables[0]

        // Record supplier payment
        val payResult = supplierRepository.processAtomicSupplierPayment(payable.id, 70000L, "Bayar DP")
        assertTrue(payResult.isSuccess)

        queue = database.syncQueueDao().getAllItems("LEGACY_BUSINESS").first()
        val supPayEvent = queue.find { it.entityType == "SUPPLIER_PAYMENT" && it.operation == "INSERT" }
        assertNotNull(supPayEvent)
    }

    @Test
    fun testCashRepositoryAtomicEnqueue() = runBlocking {
        val incResult = cashRepository.recordManualIncome(100000L, "Modal Kas Awal")
        assertTrue(incResult.isSuccess)

        val expResult = cashRepository.recordManualExpense(25000L, "Beli Alat Tulis")
        assertTrue(expResult.isSuccess)

        val queue = database.syncQueueDao().getAllItems("LEGACY_BUSINESS").first()
        val cashEvents = queue.filter { it.entityType == "CASH_TRANSACTION" }
        assertEquals(2, cashEvents.size)
        assertEquals("INSERT", cashEvents[0].operation)
        assertEquals("INSERT", cashEvents[1].operation)
    }

    @Test
    fun testStockRepositoryAtomicEnqueue() = runBlocking {
        val pId = productRepository.insertProductWithCategory(
            name = "Mie Instan",
            categoryName = "Makanan",
            purchasePrice = 2500,
            sellingPrice = 3500,
            stock = 40.0,
            minimumStock = 5.0,
            unit = "bungkus"
        )
        val prod = productRepository.getProductById(pId)!!

        val adjId = stockRepository.recordAdjustment(
            productUuid = prod.uuid,
            deltaQuantity = -2.0,
            currentStockSnapshot = 38.0,
            note = "Barang rusak"
        )
        assertTrue(adjId > 0)

        val queue = database.syncQueueDao().getAllItems("LEGACY_BUSINESS").first()
        val stockAdjEvent = queue.find { it.entityType == "STOCK_ADJUSTMENT" }
        assertNotNull(stockAdjEvent)
        assertEquals("INSERT", stockAdjEvent?.operation)
    }

    @Test
    fun testCommitFirstInvariant() = runBlocking {
        // Business transaction commits first; sync failure downstream NEVER rolls back DB data
        val pId = productRepository.insertProductWithCategory(
            name = "Rokok Filter",
            categoryName = "Rokok",
            purchasePrice = 20000,
            sellingPrice = 25000,
            stock = 10.0,
            minimumStock = 2.0,
            unit = "bungkus"
        )

        val saleResult = saleRepository.completeSale(
            cartItems = mapOf(pId to 1.0),
            paymentMethod = "CASH"
        )
        assertTrue(saleResult.isSuccess)
        val saleId = saleResult.getOrThrow()

        // Sync processor with failing provider
        val mockFailingProvider = MockSyncProvider().apply {
            simulateFailure = true
            failureErrorMessage = "Cloud unreachable"
        }
        val processor = SyncProcessor(database.syncQueueDao(), mockFailingProvider, LinearBackoffRetryPolicy(), "LEGACY_BUSINESS")

        val syncResult = processor.processBatch(batchSize = 10)
        assertEquals(0, syncResult.successCount)
        assertTrue(syncResult.failedCount > 0)

        // Invariant: Business sale transaction and stock in DB MUST REMAIN INTACT
        val verifiedSale = saleRepository.getTransactionById(saleId)
        assertNotNull("Sale must still exist in DB", verifiedSale)

        val verifiedItems = saleRepository.getItemsForTransaction(saleId)
        assertEquals(1, verifiedItems.size)

        val verifiedProd = productRepository.getProductById(pId)
        assertEquals(9.0, verifiedProd?.stock ?: 0.0, 0.001)
    }

    @Test
    fun testSoftDeletePreservesDatabaseRow() = runBlocking {
        val pId = productRepository.insertProductWithCategory(
            name = "Teh Celup",
            categoryName = "Minuman",
            purchasePrice = 5000,
            sellingPrice = 7000,
            stock = 10.0,
            minimumStock = 2.0,
            unit = "kotak"
        )
        val prod = database.productDao().getProductById(pId, "LEGACY_BUSINESS")!!

        // Soft delete
        productRepository.deleteProductById(pId)

        // UI query filters out soft-deleted items
        val activeProd = productRepository.getProductById(pId)
        org.junit.Assert.assertNull("Active product query should return null", activeProd)

        // Raw SQL verification: Row must still exist physically with is_deleted = 1
        val cursor = database.openHelper.writableDatabase.query("SELECT is_deleted, deleted_at FROM products WHERE id = $pId")
        cursor.use {
            assertTrue("Product row must still exist physically in DB", it.moveToFirst())
            val isDeletedIdx = it.getColumnIndex("is_deleted")
            val isDeletedVal = it.getInt(isDeletedIdx)
            assertEquals("is_deleted must be 1 in physical DB", 1, isDeletedVal)
        }

        // Sync queue must contain DELETE operation
        val queue = database.syncQueueDao().getAllItems("LEGACY_BUSINESS").first()
        val deleteEvent = queue.find { it.entityType == "PRODUCT" && it.entityUuid == prod.uuid && it.operation == "DELETE" }
        assertNotNull("SyncQueue must have DELETE event", deleteEvent)
    }
}







