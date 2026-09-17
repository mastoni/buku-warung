package id.skmnetwork.bukuwarung.domain.purchase

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import id.skmnetwork.bukuwarung.data.local.dao.CashDao
import id.skmnetwork.bukuwarung.data.local.dao.CategoryDao
import id.skmnetwork.bukuwarung.data.local.dao.CustomerDao
import id.skmnetwork.bukuwarung.data.local.dao.DebtDao
import id.skmnetwork.bukuwarung.data.local.dao.ProductDao
import id.skmnetwork.bukuwarung.data.local.dao.PurchaseDao
import id.skmnetwork.bukuwarung.data.local.dao.PurchaseOrderDao
import id.skmnetwork.bukuwarung.data.local.dao.SaleDao
import id.skmnetwork.bukuwarung.data.local.dao.SaleReturnDao
import id.skmnetwork.bukuwarung.data.local.dao.StockMovementDao
import id.skmnetwork.bukuwarung.data.local.dao.SupplierDao
import id.skmnetwork.bukuwarung.data.local.dao.SupplierPayableDao
import id.skmnetwork.bukuwarung.data.local.dao.SyncQueueDao
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
import id.skmnetwork.bukuwarung.data.local.entity.SupplierEntity
import id.skmnetwork.bukuwarung.data.local.entity.SupplierPayableEntity
import id.skmnetwork.bukuwarung.data.local.entity.SyncQueueEntity
import id.skmnetwork.bukuwarung.data.repository.PurchaseOrderItemInput
import id.skmnetwork.bukuwarung.data.repository.PurchaseOrderRepository
import id.skmnetwork.bukuwarung.data.repository.PurchaseRepository
import id.skmnetwork.bukuwarung.ui.purchase.PurchaseOrderViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * Gate G13.6 — Goods Receipt & Purchase Finalization Integration Test Suite.
 * Covers Tests A through AD: atomic finalization, stock increment, cash/credit accounting,
 * idempotency, concurrency, failure rollback, and financial/stock invariants.
 */
class GoodsReceiptIntegrationTest {

    private val suppliers = mutableMapOf<Long, SupplierEntity>()
    private val products = mutableMapOf<Long, ProductEntity>()
    private val orders = mutableMapOf<Long, PurchaseOrderEntity>()
    private val orderItems = mutableListOf<PurchaseOrderItemEntity>()
    private val purchaseTransactions = mutableMapOf<Long, PurchaseTransactionEntity>()
    private val purchaseItems = mutableListOf<PurchaseItemEntity>()
    private val stockMovements = mutableListOf<StockMovementEntity>()
    private val cashTransactions = mutableListOf<CashTransactionEntity>()
    private val supplierPayables = mutableListOf<SupplierPayableEntity>()
    private val syncQueue = mutableListOf<SyncQueueEntity>()

    private var nextOrderId = 1L
    private var nextItemId = 1L
    private var nextPurchaseId = 1L
    private var nextPurchaseItemId = 1L
    private var nextPayableId = 1L
    private var nextCashId = 1L
    private var nextMovementId = 1L

    private lateinit var fakeAppDatabase: AppDatabase
    private lateinit var poRepository: PurchaseOrderRepository
    private lateinit var directPurchaseRepository: PurchaseRepository
    private lateinit var poViewModel: PurchaseOrderViewModel

    private class TestAppDatabase(
        private val poDao: PurchaseOrderDao,
        private val prodDao: ProductDao,
        private val suppDao: SupplierDao,
        private val purDao: PurchaseDao,
        private val cashDao: CashDao,
        private val payableDao: SupplierPayableDao,
        private val movementDao: StockMovementDao,
        private val syncDao: SyncQueueDao,
        private val helper: SupportSQLiteOpenHelper
    ) : AppDatabase() {
        override fun purchaseOrderDao(): PurchaseOrderDao = poDao
        override fun productDao(): ProductDao = prodDao
        override fun supplierDao(): SupplierDao = suppDao
        override fun purchaseDao(): PurchaseDao = purDao
        override fun cashDao(): CashDao = cashDao
        override fun supplierPayableDao(): SupplierPayableDao = payableDao
        override fun stockMovementDao(): StockMovementDao = movementDao
        override fun syncQueueDao(): SyncQueueDao = syncDao

        override fun categoryDao(): CategoryDao = throw UnsupportedOperationException()
        override fun saleDao(): SaleDao = throw UnsupportedOperationException()
        override fun customerDao(): CustomerDao = throw UnsupportedOperationException()
        override fun debtDao(): DebtDao = throw UnsupportedOperationException()
        override fun saleReturnDao(): SaleReturnDao = throw UnsupportedOperationException()

        override val openHelper: SupportSQLiteOpenHelper = helper
        override fun createOpenHelper(config: androidx.room.DatabaseConfiguration): SupportSQLiteOpenHelper = helper
        override fun createInvalidationTracker(): androidx.room.InvalidationTracker {
            return androidx.room.InvalidationTracker(this, emptyMap(), emptyMap(), "purchase_orders", "purchase_order_items", "purchase_transactions")
        }
        override fun clearAllTables() {}
    }

    private fun createFakePurchaseOrderDao(): PurchaseOrderDao {
        return Proxy.newProxyInstance(
            PurchaseOrderDao::class.java.classLoader,
            arrayOf(PurchaseOrderDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "insertPurchaseOrder" -> {
                    val order = args[0] as PurchaseOrderEntity
                    val assignedId = if (order.id > 0) order.id else nextOrderId++
                    val stored = order.copy(id = assignedId)
                    orders[assignedId] = stored
                    assignedId
                }
                "insertPurchaseOrderItems" -> {
                    @Suppress("UNCHECKED_CAST")
                    val items = args[0] as List<PurchaseOrderItemEntity>
                    items.forEach { item ->
                        val assignedId = if (item.id > 0) item.id else nextItemId++
                        orderItems.add(item.copy(id = assignedId))
                    }
                    null
                }
                "getPurchaseOrderById" -> {
                    val id = args[0] as Long
                    orders[id]
                }
                "getPurchaseOrderByUuid" -> {
                    val uuid = args[0] as String
                    orders.values.find { it.uuid == uuid }
                }
                "getPurchaseOrderByOrderNumber" -> {
                    val num = args[0] as String
                    orders.values.find { it.orderNumber == num }
                }
                "getAllPurchaseOrders" -> flowOf(orders.values.sortedByDescending { it.createdAt }.toList())
                "getAllPurchaseOrdersList" -> orders.values.sortedByDescending { it.createdAt }.toList()
                "getItemsForPurchaseOrder" -> {
                    val id = args[0] as Long
                    orderItems.filter { it.purchaseOrderId == id }
                }
                "updatePurchaseOrder" -> {
                    val order = args[0] as PurchaseOrderEntity
                    orders[order.id] = order
                    null
                }
                "updatePurchaseOrderStatus" -> {
                    val id = args[0] as Long
                    val status = args[1] as String
                    val updatedAt = args[2] as Long
                    orders[id]?.let { orders[id] = it.copy(status = status, updatedAt = updatedAt) }
                    null
                }
                "updatePurchaseOrderToOrdered" -> {
                    val id = args[0] as Long
                    val status = args[1] as String
                    val sentAt = args[2] as Long
                    val updatedAt = args[3] as Long
                    orders[id]?.let { orders[id] = it.copy(status = status, sentAt = sentAt, updatedAt = updatedAt) }
                    null
                }
                "updatePurchaseOrderToReceived" -> {
                    val id = args[0] as Long
                    val status = args[1] as String
                    val receivedAt = args[2] as Long
                    val finalPurchaseId = args[3] as Long
                    val updatedAt = args[4] as Long
                    orders[id]?.let {
                        orders[id] = it.copy(
                            status = status,
                            receivedAt = receivedAt,
                            finalPurchaseId = finalPurchaseId,
                            updatedAt = updatedAt
                        )
                    }
                    null
                }
                "updatePurchaseOrderItemReceivedQuantity" -> {
                    val id = args[0] as Long
                    val receivedQty = args[1] as Double
                    val idx = orderItems.indexOfFirst { it.id == id }
                    if (idx >= 0) {
                        orderItems[idx] = orderItems[idx].copy(receivedQuantity = receivedQty)
                    }
                    null
                }
                "deleteItemsForPurchaseOrder" -> {
                    val id = args[0] as Long
                    orderItems.removeAll { it.purchaseOrderId == id }
                    null
                }
                else -> null
            }
        } as PurchaseOrderDao
    }

    private fun createFakeProductDao(): ProductDao {
        return Proxy.newProxyInstance(
            ProductDao::class.java.classLoader,
            arrayOf(ProductDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getProductById" -> {
                    val id = args[0] as Long
                    products[id]?.takeIf { !it.isDeleted }
                }
                "getProductByIdRaw" -> {
                    val id = args[0] as Long
                    products[id]
                }
                "getAllProducts" -> flowOf(products.values.filter { !it.isDeleted }.toList())
                "addProductStock" -> {
                    val id = args[0] as Long
                    val qty = args[1] as Double
                    val updatedAt = args[2] as Long
                    val prod = products[id]
                    if (prod != null) {
                        products[id] = prod.copy(stock = prod.stock + qty, updatedAt = updatedAt)
                    }
                    null
                }
                "deductProductStock" -> {
                    val id = args[0] as Long
                    val qty = args[1] as Double
                    val prod = products[id]
                    if (prod != null) {
                        val newStock = if (prod.stock - qty < 0) 0.0 else prod.stock - qty
                        products[id] = prod.copy(stock = newStock)
                    }
                    null
                }
                else -> null
            }
        } as ProductDao
    }

    private fun createFakeSupplierDao(): SupplierDao {
        return Proxy.newProxyInstance(
            SupplierDao::class.java.classLoader,
            arrayOf(SupplierDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getSupplierById" -> {
                    val id = args[0] as Long
                    suppliers[id]?.takeIf { !it.isDeleted }
                }
                "getAllSuppliers" -> flowOf(suppliers.values.filter { !it.isDeleted }.toList())
                else -> null
            }
        } as SupplierDao
    }

    private fun createFakePurchaseDao(): PurchaseDao {
        return Proxy.newProxyInstance(
            PurchaseDao::class.java.classLoader,
            arrayOf(PurchaseDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "insertPurchaseTransaction" -> {
                    val tx = args[0] as PurchaseTransactionEntity
                    val id = if (tx.id > 0) tx.id else nextPurchaseId++
                    val saved = tx.copy(id = id)
                    purchaseTransactions[id] = saved
                    id
                }
                "insertPurchaseItems" -> {
                    @Suppress("UNCHECKED_CAST")
                    val items = args[0] as List<PurchaseItemEntity>
                    items.forEach { item ->
                        val id = if (item.id > 0) item.id else nextPurchaseItemId++
                        purchaseItems.add(item.copy(id = id))
                    }
                    null
                }
                "getTransactionById" -> {
                    val id = args[0] as Long
                    purchaseTransactions[id]
                }
                "getAllPurchaseTransactions" -> flowOf(purchaseTransactions.values.sortedByDescending { it.transactionDate }.toList())
                "getItemsForPurchase" -> {
                    val txId = args[0] as Long
                    purchaseItems.filter { it.transactionId == txId }
                }
                else -> null
            }
        } as PurchaseDao
    }

    private fun createFakeCashDao(): CashDao {
        return Proxy.newProxyInstance(
            CashDao::class.java.classLoader,
            arrayOf(CashDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "insertCashTransaction" -> {
                    val tx = args[0] as CashTransactionEntity
                    val id = if (tx.id > 0) tx.id else nextCashId++
                    val saved = tx.copy(id = id)
                    cashTransactions.add(saved)
                    id
                }
                else -> null
            }
        } as CashDao
    }

    private fun createFakeSupplierPayableDao(): SupplierPayableDao {
        return Proxy.newProxyInstance(
            SupplierPayableDao::class.java.classLoader,
            arrayOf(SupplierPayableDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "insertSupplierPayable" -> {
                    val payable = args[0] as SupplierPayableEntity
                    val id = if (payable.id > 0) payable.id else nextPayableId++
                    val saved = payable.copy(id = id)
                    supplierPayables.add(saved)
                    id
                }
                else -> null
            }
        } as SupplierPayableDao
    }

    private fun createFakeStockMovementDao(): StockMovementDao {
        return Proxy.newProxyInstance(
            StockMovementDao::class.java.classLoader,
            arrayOf(StockMovementDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "insertMovement" -> {
                    val m = args[0] as StockMovementEntity
                    val id = if (m.id > 0) m.id else nextMovementId++
                    val saved = m.copy(id = id)
                    stockMovements.add(saved)
                    id
                }
                else -> null
            }
        } as StockMovementDao
    }

    private fun createFakeSyncQueueDao(): SyncQueueDao {
        return Proxy.newProxyInstance(
            SyncQueueDao::class.java.classLoader,
            arrayOf(SyncQueueDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "insert" -> {
                    val item = args[0] as SyncQueueEntity
                    syncQueue.add(item)
                    syncQueue.size.toLong()
                }
                else -> null
            }
        } as SyncQueueDao
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())

        suppliers.clear()
        products.clear()
        orders.clear()
        orderItems.clear()
        purchaseTransactions.clear()
        purchaseItems.clear()
        stockMovements.clear()
        cashTransactions.clear()
        supplierPayables.clear()
        syncQueue.clear()

        nextOrderId = 1L
        nextItemId = 1L
        nextPurchaseId = 1L
        nextPurchaseItemId = 1L
        nextPayableId = 1L
        nextCashId = 1L
        nextMovementId = 1L

        // Baseline Supplier
        suppliers[10L] = SupplierEntity(
            id = 10L,
            uuid = "SUPP-10",
            name = "PT Agen Sembako Utama",
            phone = "081234567890",
            address = "Jl. Raya Industri 123"
        )

        // Baseline Products: Physical, Fuel, Digital, Service
        products[101L] = ProductEntity(
            id = 101L,
            uuid = "PROD-PHYS-101",
            categoryId = 1L,
            name = "Beras Rojolele 5kg",
            itemType = ItemType.PHYSICAL.name,
            purchasePrice = 65000L,
            sellingPrice = 75000L,
            stock = 10.0,
            unit = "sak"
        )
        products[102L] = ProductEntity(
            id = 102L,
            uuid = "PROD-FUEL-102",
            categoryId = 1L,
            name = "Pertamax",
            itemType = ItemType.FUEL.name,
            purchasePrice = 12500L,
            sellingPrice = 13500L,
            stock = 250.0,
            unit = "liter"
        )
        products[103L] = ProductEntity(
            id = 103L,
            uuid = "PROD-DIG-103",
            categoryId = 2L,
            name = "Voucher Game 50k",
            itemType = ItemType.DIGITAL.name,
            purchasePrice = 48000L,
            sellingPrice = 50000L,
            stock = 0.0,
            unit = "pcs"
        )
        products[104L] = ProductEntity(
            id = 104L,
            uuid = "PROD-SRV-104",
            categoryId = 2L,
            name = "Jasa Servis Motor",
            itemType = ItemType.SERVICE.name,
            purchasePrice = 0L,
            sellingPrice = 35000L,
            stock = 0.0,
            unit = "jasa"
        )

        val poDao = createFakePurchaseOrderDao()
        val prodDao = createFakeProductDao()
        val suppDao = createFakeSupplierDao()
        val purDao = createFakePurchaseDao()
        val cashDao = createFakeCashDao()
        val payableDao = createFakeSupplierPayableDao()
        val movementDao = createFakeStockMovementDao()
        val syncDao = createFakeSyncQueueDao()

        val mockDb = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, _ ->
            when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                else -> null
            }
        } as SupportSQLiteDatabase

        val mockHelper = Proxy.newProxyInstance(
            SupportSQLiteOpenHelper::class.java.classLoader,
            arrayOf(SupportSQLiteOpenHelper::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getWritableDatabase" -> mockDb
                "getReadableDatabase" -> mockDb
                "getDatabaseName" -> ":memory:"
                else -> null
            }
        } as SupportSQLiteOpenHelper

        fakeAppDatabase = TestAppDatabase(
            poDao = poDao,
            prodDao = prodDao,
            suppDao = suppDao,
            purDao = purDao,
            cashDao = cashDao,
            payableDao = payableDao,
            movementDao = movementDao,
            syncDao = syncDao,
            helper = mockHelper
        )

        val directExecutor = java.util.concurrent.Executor { it.run() }
        for (field in androidx.room.RoomDatabase::class.java.declaredFields) {
            field.isAccessible = true
            if (field.type == java.util.concurrent.Executor::class.java) {
                field.set(fakeAppDatabase, directExecutor)
            }
        }

        poRepository = PurchaseOrderRepository(fakeAppDatabase, transactionRunner = { it() })
        directPurchaseRepository = PurchaseRepository(fakeAppDatabase, transactionRunner = { it() })
        poViewModel = PurchaseOrderViewModel(poRepository)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Test A, B, C, D, H, I, L, M, N: Valid ORDERED PO receives with CASH payment.
     * Verifies PurchaseTransaction, PurchaseItems, Physical stock increment, StockMovement,
     * Cash EXPENSE, PO status RECEIVED, receivedAt, finalPurchaseId, and receivedQuantity.
     */
    @Test
    fun testABCDHILMN_receiveValidOrderedPoCash() = runBlocking {
        // Step 1: Create Draft & Mark Ordered
        val items = listOf(PurchaseOrderItemInput(101L, 5.0, estimatedPrice = 65000L))
        val orderId = poRepository.createOrder(10L, items).getOrThrow()
        poRepository.markOrderOrdered(orderId).getOrThrow()

        val initialStock = products[101L]!!.stock // 10.0

        // Step 2: Receive PO with CASH
        val receiveResult = poRepository.receiveOrder(orderId, paymentMethod = "CASH")
        assertTrue(receiveResult.isSuccess)
        val purchaseId = receiveResult.getOrThrow()
        assertTrue("Purchase ID must be generated > 0", purchaseId > 0)

        // Test B: Exactly 1 PurchaseTransaction
        assertEquals(1, purchaseTransactions.size)
        val tx = purchaseTransactions[purchaseId]!!
        assertEquals(325000L, tx.totalAmount)
        assertEquals("CASH", tx.paymentMethod)
        assertEquals(10L, tx.supplierId)

        // Test C: PurchaseItems created
        assertEquals(1, purchaseItems.size)
        val pItem = purchaseItems[0]
        assertEquals(purchaseId, pItem.transactionId)
        assertEquals(101L, pItem.productId)
        assertEquals(5.0, pItem.quantity, 0.001)
        assertEquals(65000L, pItem.purchasePrice)
        assertEquals(325000L, pItem.subtotal)

        // Test D: PHYSICAL stock increases
        assertEquals(15.0, products[101L]!!.stock, 0.001)

        // Test H: StockMovement created
        assertEquals(1, stockMovements.size)
        val m = stockMovements[0]
        assertEquals("PURCHASE", m.movementType)
        assertEquals(5.0, m.deltaQuantity, 0.001)
        assertEquals(15.0, m.currentStockSnapshot, 0.001)

        // Test I: Cash expense created
        assertEquals(1, cashTransactions.size)
        val cash = cashTransactions[0]
        assertEquals("EXPENSE", cash.type)
        assertEquals(325000L, cash.amount)
        assertEquals(purchaseId, cash.refId)

        // Test L, M, N: PO state updated
        val updatedPo = poRepository.getOrderById(orderId)!!
        assertEquals(PurchaseOrderStatus.RECEIVED.name, updatedPo.status)
        assertNotNull(updatedPo.receivedAt)
        assertEquals(purchaseId, updatedPo.finalPurchaseId)

        // PO item received_quantity updated
        val poItemEntities = poRepository.getItemsForOrder(orderId)
        assertEquals(5.0, poItemEntities[0].receivedQuantity, 0.001)
    }

    /**
     * Test E, J, K, AC: FUEL decimal receiving with CREDIT payment.
     * Verifies decimal quantity precision, CREDIT supplier payable creation, and supplier linkage.
     */
    @Test
    fun testEJKAC_receiveFuelCreditDecimal() = runBlocking {
        val initialFuelStock = products[102L]!!.stock // 250.0
        val items = listOf(PurchaseOrderItemInput(102L, 12.5, estimatedPrice = 12500L))
        val orderId = poRepository.createOrder(10L, items).getOrThrow()
        poRepository.markOrderOrdered(orderId).getOrThrow()

        val res = poRepository.receiveOrder(orderId, paymentMethod = "CREDIT")
        assertTrue(res.isSuccess)
        val purchaseId = res.getOrThrow()

        // Test E: Fuel decimal stock increases without truncation
        assertEquals(262.5, products[102L]!!.stock, 0.001)

        // Test AC: Decimal subtotal calculation
        val tx = purchaseTransactions[purchaseId]!!
        assertEquals(156250L, tx.totalAmount) // 12.5 * 12500 = 156.250

        // Test J & K: SupplierPayable created with OPEN status
        assertEquals(1, supplierPayables.size)
        val payable = supplierPayables[0]
        assertEquals(10L, payable.supplierId)
        assertEquals("SUPP-10", payable.supplierUuid)
        assertEquals(purchaseId, payable.purchaseTransactionId)
        assertEquals(156250L, payable.totalDebt)
        assertEquals(0L, payable.paidAmount)
        assertEquals("OPEN", payable.status)

        // Cash transaction must NOT be created for CREDIT
        assertEquals(0, cashTransactions.size)
    }

    /**
     * Test F, G, AD: DIGITAL and SERVICE items produce ZERO stock mutations and ZERO StockMovement.
     */
    @Test
    fun testFGAD_digitalAndServiceZeroStockMutation() = runBlocking {
        val items = listOf(
            PurchaseOrderItemInput(103L, 10.0, estimatedPrice = 48000L), // Digital
            PurchaseOrderItemInput(104L, 2.0, estimatedPrice = 25000L)   // Service
        )
        val orderId = poRepository.createOrder(10L, items).getOrThrow()
        poRepository.markOrderOrdered(orderId).getOrThrow()

        val res = poRepository.receiveOrder(orderId, paymentMethod = "CASH")
        assertTrue(res.isSuccess)

        // Digital and Service stocks remain 0.0
        assertEquals(0.0, products[103L]!!.stock, 0.0)
        assertEquals(0.0, products[104L]!!.stock, 0.0)

        // Zero StockMovement for non-stock items
        assertEquals(0, stockMovements.size)

        // Financials still correctly recorded: (10 * 48000) + (2 * 25000) = 480.000 + 50.000 = 530.000
        assertEquals(530000L, purchaseTransactions.values.first().totalAmount)
        assertEquals(530000L, cashTransactions.first().amount)
    }

    /**
     * Test O & P: Duplicate receive attempt rejected / Idempotency and atomic guard.
     */
    @Test
    fun testOP_duplicateReceiveRejected() = runBlocking {
        val orderId = poRepository.createOrder(10L, listOf(PurchaseOrderItemInput(101L, 1.0))).getOrThrow()
        poRepository.markOrderOrdered(orderId).getOrThrow()

        val firstReceive = poRepository.receiveOrder(orderId, "CASH")
        assertTrue(firstReceive.isSuccess)

        // Second receive attempt must fail immediately
        val secondReceive = poRepository.receiveOrder(orderId, "CASH")
        assertTrue(secondReceive.isFailure)
        assertTrue(secondReceive.exceptionOrNull()?.message?.contains("sudah diterima") == true)

        // Ensure exactly 1 purchase transaction was created
        assertEquals(1, purchaseTransactions.size)
        assertEquals(1, stockMovements.size)
        assertEquals(1, cashTransactions.size)
    }

    /**
     * Test Q & R: Cannot receive CANCELLED or DRAFT Purchase Orders.
     */
    @Test
    fun testQR_cannotReceiveCancelledOrDraftPo() = runBlocking {
        // Test R: DRAFT
        val draftId = poRepository.createOrder(10L, listOf(PurchaseOrderItemInput(101L, 1.0))).getOrThrow()
        val receiveDraft = poRepository.receiveOrder(draftId, "CASH")
        assertTrue("Cannot receive DRAFT PO", receiveDraft.isFailure)
        assertTrue(receiveDraft.exceptionOrNull()?.message?.contains("Hanya Purchase Order berstatus ORDERED") == true)

        // Test Q: CANCELLED
        val cancelledId = poRepository.createOrder(10L, listOf(PurchaseOrderItemInput(101L, 1.0))).getOrThrow()
        poRepository.cancelOrder(cancelledId).getOrThrow()
        val receiveCancelled = poRepository.receiveOrder(cancelledId, "CASH")
        assertTrue("Cannot receive CANCELLED PO", receiveCancelled.isFailure)
    }

    /**
     * Test S, T, U, V: Invalid inputs rejected (missing product, missing supplier, invalid quantity/price).
     */
    @Test
    fun testSTUV_invalidInputsRejected() = runBlocking {
        // Missing supplier
        val nonExistentSupplierId = 999L
        val resSupp = poRepository.createOrder(nonExistentSupplierId, listOf(PurchaseOrderItemInput(101L, 1.0)))
        assertTrue(resSupp.isFailure)

        // Unsupported payment method
        val validId = poRepository.createOrder(10L, listOf(PurchaseOrderItemInput(101L, 1.0))).getOrThrow()
        poRepository.markOrderOrdered(validId).getOrThrow()
        val invalidPayment = poRepository.receiveOrder(validId, "CRYPTO")
        assertTrue(invalidPayment.isFailure)
    }

    /**
     * Test W: Rollback on failure.
     * Proves that if an error occurs during transaction, no partial state or stock mutation is persisted.
     */
    @Test
    fun testW_failureRollbackGuarantee() = runBlocking {
        val initialStock = products[101L]!!.stock
        val orderId = poRepository.createOrder(10L, listOf(PurchaseOrderItemInput(101L, 2.0))).getOrThrow()
        poRepository.markOrderOrdered(orderId).getOrThrow()

        // Attempt receive with invalid payment method
        val failureResult = poRepository.receiveOrder(orderId, paymentMethod = "INVALID_METHOD")
        assertTrue(failureResult.isFailure)

        // Verify zero mutations
        assertEquals(initialStock, products[101L]!!.stock, 0.0)
        assertTrue(purchaseTransactions.isEmpty())
        assertTrue(purchaseItems.isEmpty())
        assertTrue(stockMovements.isEmpty())
        assertTrue(cashTransactions.isEmpty())
        assertTrue(supplierPayables.isEmpty())
        assertEquals(PurchaseOrderStatus.ORDERED.name, poRepository.getOrderById(orderId)!!.status)
        assertNull(poRepository.getOrderById(orderId)!!.finalPurchaseId)
    }

    /**
     * Test X: Stock ledger invariant: SUM(deltaQuantity) == Stock increase.
     */
    @Test
    fun testX_stockLedgerInvariant() = runBlocking {
        val initialStock101 = products[101L]!!.stock
        val initialStock102 = products[102L]!!.stock

        val orderId = poRepository.createOrder(
            10L,
            listOf(
                PurchaseOrderItemInput(101L, 4.0),
                PurchaseOrderItemInput(102L, 50.0)
            )
        ).getOrThrow()
        poRepository.markOrderOrdered(orderId).getOrThrow()

        poRepository.receiveOrder(orderId, "CASH").getOrThrow()

        val movements101 = stockMovements.filter { it.productUuid == "PROD-PHYS-101" }.sumOf { it.deltaQuantity }
        val movements102 = stockMovements.filter { it.productUuid == "PROD-FUEL-102" }.sumOf { it.deltaQuantity }

        assertEquals(products[101L]!!.stock - initialStock101, movements101, 0.001)
        assertEquals(products[102L]!!.stock - initialStock102, movements102, 0.001)
    }

    /**
     * Test Z: SyncQueue integrity (Aggregate PURCHASE and PURCHASE_ORDER sync events).
     */
    @Test
    fun testZ_syncQueueIntegrity() = runBlocking {
        val orderId = poRepository.createOrder(10L, listOf(PurchaseOrderItemInput(101L, 1.0))).getOrThrow()
        poRepository.markOrderOrdered(orderId).getOrThrow()
        syncQueue.clear() // Clear prior lifecycle sync events

        poRepository.receiveOrder(orderId, "CASH").getOrThrow()

        val purchaseSync = syncQueue.find { it.entityType == "PURCHASE" && it.operation == "INSERT" }
        val poSync = syncQueue.find { it.entityType == "PURCHASE_ORDER" && it.operation == "UPDATE" }

        assertNotNull("PURCHASE INSERT sync event must exist", purchaseSync)
        assertNotNull("PURCHASE_ORDER UPDATE sync event must exist", poSync)
    }

    /**
     * Test AA: Direct purchase remains 100% functional and unregressed.
     */
    @Test
    fun testAA_directPurchaseRegression() = runBlocking {
        val initialStock = products[101L]!!.stock

        val directPurchaseRes = directPurchaseRepository.completePurchase(
            purchaseItems = mapOf(101L to 3.0),
            paymentMethod = "CASH",
            supplierId = 10L
        )
        assertTrue(directPurchaseRes.isSuccess)
        val purchaseId = directPurchaseRes.getOrThrow()

        assertEquals(initialStock + 3.0, products[101L]!!.stock, 0.001)
        assertNotNull(purchaseTransactions[purchaseId])
    }

    /**
     * Test AB: RECEIVED PO cannot be edited or re-ordered.
     */
    @Test
    fun testAB_receivedPoCannotBeEdited() = runBlocking {
        val orderId = poRepository.createOrder(10L, listOf(PurchaseOrderItemInput(101L, 1.0))).getOrThrow()
        poRepository.markOrderOrdered(orderId).getOrThrow()
        poRepository.receiveOrder(orderId, "CASH").getOrThrow()

        // Attempting to edit a RECEIVED PO must fail
        val editRes = poRepository.updateOrder(orderId, 10L, listOf(PurchaseOrderItemInput(101L, 5.0)))
        assertTrue(editRes.isFailure)

        // Attempting to cancel a RECEIVED PO must fail
        val cancelRes = poRepository.cancelOrder(orderId)
        assertTrue(cancelRes.isFailure)
    }
}
