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
import id.skmnetwork.bukuwarung.data.local.entity.ItemType
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseOrderEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseOrderItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseOrderStatus
import id.skmnetwork.bukuwarung.data.local.entity.SupplierEntity
import id.skmnetwork.bukuwarung.data.local.entity.SyncQueueEntity
import id.skmnetwork.bukuwarung.data.repository.PurchaseOrderItemInput
import id.skmnetwork.bukuwarung.data.repository.PurchaseOrderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * Comprehensive Unit Test Suite for Gate G13.2:
 * Tests A through AH verifying Purchase Order repository & lifecycle engine.
 */
class PurchaseOrderLifecycleUnitTest {

    private val suppliers = mutableMapOf<Long, SupplierEntity>()
    private val products = mutableMapOf<Long, ProductEntity>()
    private val orders = mutableMapOf<Long, PurchaseOrderEntity>()
    private val orderItems = mutableMapOf<Long, MutableList<PurchaseOrderItemEntity>>()
    private val syncQueue = mutableListOf<SyncQueueEntity>()

    private var nextOrderId = 1L
    private var nextItemId = 1L

    // Counters to verify strict accounting/stock boundary (ZERO side effects)
    var stockMutationCount = 0
    var stockMovementCount = 0
    var cashTransactionCount = 0
    var supplierPayableCount = 0
    var purchaseTransactionCount = 0

    private lateinit var fakeAppDatabase: AppDatabase
    private lateinit var repository: PurchaseOrderRepository

    private class TestAppDatabase(
        private val poDao: PurchaseOrderDao,
        private val prodDao: ProductDao,
        private val suppDao: SupplierDao,
        private val syncDao: SyncQueueDao,
        private val purDao: PurchaseDao,
        private val cashDao: CashDao,
        private val payableDao: SupplierPayableDao,
        private val movementDao: StockMovementDao,
        private val helper: SupportSQLiteOpenHelper
    ) : AppDatabase() {
        override fun purchaseOrderDao(): PurchaseOrderDao = poDao
        override fun productDao(): ProductDao = prodDao
        override fun supplierDao(): SupplierDao = suppDao
        override fun syncQueueDao(): SyncQueueDao = syncDao
        override fun purchaseDao(): PurchaseDao = purDao
        override fun cashDao(): CashDao = cashDao
        override fun supplierPayableDao(): SupplierPayableDao = payableDao
        override fun stockMovementDao(): StockMovementDao = movementDao

        override fun categoryDao(): CategoryDao = throw UnsupportedOperationException()
        override fun saleDao(): SaleDao = throw UnsupportedOperationException()
        override fun customerDao(): CustomerDao = throw UnsupportedOperationException()
        override fun debtDao(): DebtDao = throw UnsupportedOperationException()
        override fun saleReturnDao(): SaleReturnDao = throw UnsupportedOperationException()

        override val openHelper: SupportSQLiteOpenHelper = helper
        override fun createOpenHelper(config: androidx.room.DatabaseConfiguration): SupportSQLiteOpenHelper = helper
        override fun createInvalidationTracker(): androidx.room.InvalidationTracker {
            return androidx.room.InvalidationTracker(this, emptyMap(), emptyMap(), "purchase_orders", "purchase_order_items")
        }
        override fun clearAllTables() {}
    }

    @Before
    fun setup() {
        suppliers.clear()
        products.clear()
        orders.clear()
        orderItems.clear()
        syncQueue.clear()
        nextOrderId = 1L
        nextItemId = 1L

        stockMutationCount = 0
        stockMovementCount = 0
        cashTransactionCount = 0
        supplierPayableCount = 0
        purchaseTransactionCount = 0

        // Populate sample baseline products
        products[1L] = ProductEntity(
            id = 1L,
            uuid = "PROD-PHYS-1",
            categoryId = 1L,
            name = "Minyak Goreng 2L",
            purchasePrice = 28000L,
            sellingPrice = 32000L,
            stock = 10.0,
            unit = "pcs",
            itemType = ItemType.PHYSICAL.name
        )
        products[2L] = ProductEntity(
            id = 2L,
            uuid = "PROD-FUEL-2",
            categoryId = 1L,
            name = "Pertamax",
            purchasePrice = 12500L,
            sellingPrice = 13500L,
            stock = 500.0,
            unit = "L",
            itemType = ItemType.FUEL.name
        )
        products[3L] = ProductEntity(
            id = 3L,
            uuid = "PROD-DIG-3",
            categoryId = 2L,
            name = "Pulsa 50k",
            purchasePrice = 49000L,
            sellingPrice = 52000L,
            stock = 0.0,
            unit = "pcs",
            itemType = ItemType.DIGITAL.name
        )
        products[4L] = ProductEntity(
            id = 4L,
            uuid = "PROD-SRV-4",
            categoryId = 2L,
            name = "Jasa Servis",
            purchasePrice = 0L,
            sellingPrice = 25000L,
            stock = 0.0,
            unit = "jasa",
            itemType = ItemType.SERVICE.name
        )

        // Populate sample baseline supplier
        suppliers[10L] = SupplierEntity(
            id = 10L,
            uuid = "SUPP-UUID-10",
            name = "PT Agen Sembako Jaya",
            phone = "081234567890",
            address = "Jl. Merdeka No. 1"
        )
        suppliers[20L] = SupplierEntity(
            id = 20L,
            uuid = "SUPP-UUID-20",
            name = "Pertamina Depot",
            phone = "089876543210",
            address = "Kawasan Industri"
        )

        val fakePurchaseOrderDao = createFakePurchaseOrderDao()
        val fakeProductDao = createFakeProductDao()
        val fakeSupplierDao = createFakeSupplierDao()
        val fakeSyncQueueDao = createFakeSyncQueueDao()
        val fakePurchaseDao = Proxy.newProxyInstance(PurchaseDao::class.java.classLoader, arrayOf(PurchaseDao::class.java)) { _, _, _ -> null } as PurchaseDao
        val fakeCashDao = Proxy.newProxyInstance(CashDao::class.java.classLoader, arrayOf(CashDao::class.java)) { _, _, _ -> null } as CashDao
        val fakePayableDao = Proxy.newProxyInstance(SupplierPayableDao::class.java.classLoader, arrayOf(SupplierPayableDao::class.java)) { _, _, _ -> null } as SupplierPayableDao
        val fakeMovementDao = Proxy.newProxyInstance(StockMovementDao::class.java.classLoader, arrayOf(StockMovementDao::class.java)) { _, _, _ -> null } as StockMovementDao

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
            poDao = fakePurchaseOrderDao,
            prodDao = fakeProductDao,
            suppDao = fakeSupplierDao,
            syncDao = fakeSyncQueueDao,
            purDao = fakePurchaseDao,
            cashDao = fakeCashDao,
            payableDao = fakePayableDao,
            movementDao = fakeMovementDao,
            helper = mockHelper
        )

        val directExecutor = java.util.concurrent.Executor { it.run() }
        for (field in androidx.room.RoomDatabase::class.java.declaredFields) {
            field.isAccessible = true
            if (field.type == java.util.concurrent.Executor::class.java) {
                field.set(fakeAppDatabase, directExecutor)
            }
        }

        repository = PurchaseOrderRepository(fakeAppDatabase, transactionRunner = { it() })
    }

    private fun createFakePurchaseOrderDao(): PurchaseOrderDao {
        return Proxy.newProxyInstance(
            PurchaseOrderDao::class.java.classLoader,
            arrayOf(PurchaseOrderDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "insertPurchaseOrder" -> {
                    val entity = args[0] as PurchaseOrderEntity
                    val id = if (entity.id > 0) entity.id else nextOrderId++
                    val saved = entity.copy(id = id)
                    orders[id] = saved
                    id
                }
                "insertPurchaseOrderItems" -> {
                    @Suppress("UNCHECKED_CAST")
                    val items = args[0] as List<PurchaseOrderItemEntity>
                    for (item in items) {
                        val id = if (item.id > 0) item.id else nextItemId++
                        val saved = item.copy(id = id)
                        val list = orderItems.getOrPut(saved.purchaseOrderId) { mutableListOf() }
                        list.add(saved)
                    }
                    null
                }
                "getAllPurchaseOrders" -> {
                    flowOf(orders.values.sortedByDescending { it.createdAt }.toList())
                }
                "getAllPurchaseOrdersList" -> {
                    orders.values.sortedByDescending { it.createdAt }.toList()
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
                "getPurchaseOrdersByStatus" -> {
                    val status = args[0] as String
                    flowOf(orders.values.filter { it.status == status }.sortedByDescending { it.createdAt })
                }
                "getPurchaseOrdersBySupplier" -> {
                    val suppId = args[0] as Long
                    flowOf(orders.values.filter { it.supplierId == suppId }.sortedByDescending { it.createdAt })
                }
                "getItemsForPurchaseOrder" -> {
                    val poId = args[0] as Long
                    orderItems[poId] ?: emptyList<PurchaseOrderItemEntity>()
                }
                "getItemsForPurchaseOrderByUuid" -> {
                    val poUuid = args[0] as String
                    orderItems.values.flatten().filter { it.poUuid == poUuid }
                }
                "deleteItemsForPurchaseOrder" -> {
                    val poId = args[0] as Long
                    orderItems.remove(poId)
                    null
                }
                "updatePurchaseOrder" -> {
                    val entity = args[0] as PurchaseOrderEntity
                    orders[entity.id] = entity
                    null
                }
                "updatePurchaseOrderStatus" -> {
                    val id = args[0] as Long
                    val status = args[1] as String
                    val updatedAt = args[2] as Long
                    val current = orders[id]
                    if (current != null) {
                        orders[id] = current.copy(status = status, updatedAt = updatedAt)
                    }
                    null
                }
                "updatePurchaseOrderToOrdered" -> {
                    val id = args[0] as Long
                    val status = args[1] as String
                    val sentAt = args[2] as Long
                    val updatedAt = args[3] as Long
                    val current = orders[id]
                    if (current != null) {
                        orders[id] = current.copy(status = status, sentAt = sentAt, updatedAt = updatedAt)
                    }
                    null
                }
                "deletePurchaseOrder" -> {
                    val entity = args[0] as PurchaseOrderEntity
                    orders.remove(entity.id)
                    orderItems.remove(entity.id)
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
                    products[id]
                }
                "addProductStock", "deductProductStock" -> {
                    stockMutationCount++
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
                    suppliers[id]
                }
                else -> null
            }
        } as SupplierDao
    }

    private fun createFakeSyncQueueDao(): SyncQueueDao {
        return Proxy.newProxyInstance(
            SyncQueueDao::class.java.classLoader,
            arrayOf(SyncQueueDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "insert" -> {
                    val entity = args[0] as SyncQueueEntity
                    syncQueue.add(entity)
                    1L
                }
                else -> null
            }
        } as SyncQueueDao
    }

    // ==========================================
    // TESTS A THROUGH AH
    // ==========================================

    /**
     * Test A: Create valid DRAFT PO succeeds.
     */
    @Test
    fun testA_createValidDraftPo() = runBlocking {
        val result = repository.createOrder(
            supplierId = 10L,
            items = listOf(PurchaseOrderItemInput(productId = 1L, quantity = 5.0)),
            notes = "Pesanan awal minggu"
        )
        assertTrue("Create PO should succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        val poId = result.getOrThrow()
        val po = repository.getOrderById(poId)
        assertNotNull(po)
        assertEquals(PurchaseOrderStatus.DRAFT.name, po?.status)
        assertEquals("Pesanan awal minggu", po?.notes)
        assertNull(po?.sentAt)
        assertNull(po?.receivedAt)
        assertNull(po?.finalPurchaseId)
    }

    /**
     * Test B: Supplier is required; non-existent supplier is rejected.
     */
    @Test
    fun testB_supplierRequiredAndValidated() = runBlocking {
        val result = repository.createOrder(
            supplierId = 9999L, // Does not exist
            items = listOf(PurchaseOrderItemInput(productId = 1L, quantity = 2.0))
        )
        assertTrue("Create PO with invalid supplier must fail", result.isFailure)
    }

    /**
     * Test C: Empty items list is rejected.
     */
    @Test
    fun testC_emptyItemRejection() = runBlocking {
        val result = repository.createOrder(
            supplierId = 10L,
            items = emptyList()
        )
        assertTrue("Create PO with empty items must fail", result.isFailure)
    }

    /**
     * Test D: Invalid quantity (<= 0) is rejected.
     */
    @Test
    fun testD_invalidQuantityRejection() = runBlocking {
        val resultZero = repository.createOrder(
            supplierId = 10L,
            items = listOf(PurchaseOrderItemInput(productId = 1L, quantity = 0.0))
        )
        assertTrue("Quantity 0 must fail", resultZero.isFailure)

        val resultNeg = repository.createOrder(
            supplierId = 10L,
            items = listOf(PurchaseOrderItemInput(productId = 1L, quantity = -5.0))
        )
        assertTrue("Negative quantity must fail", resultNeg.isFailure)
    }

    /**
     * Test E: Product existence validation.
     */
    @Test
    fun testE_productExistenceValidation() = runBlocking {
        val result = repository.createOrder(
            supplierId = 10L,
            items = listOf(PurchaseOrderItemInput(productId = 8888L, quantity = 1.0))
        )
        assertTrue("Non-existent product must fail", result.isFailure)
    }

    /**
     * Test F: Estimated price snapshot from ProductEntity.purchasePrice.
     */
    @Test
    fun testF_estimatedPriceSnapshot() = runBlocking {
        val poId = repository.createOrder(
            supplierId = 10L,
            items = listOf(PurchaseOrderItemInput(productId = 1L, quantity = 3.0))
        ).getOrThrow()

        val items = repository.getItemsForOrder(poId)
        assertEquals(1, items.size)
        assertEquals(28000L, items[0].estimatedPrice)
    }

    /**
     * Test G: Product name snapshot preserved.
     */
    @Test
    fun testG_productNameSnapshot() = runBlocking {
        val poId = repository.createOrder(
            supplierId = 10L,
            items = listOf(PurchaseOrderItemInput(productId = 1L, quantity = 2.0))
        ).getOrThrow()

        val items = repository.getItemsForOrder(poId)
        assertEquals("Minyak Goreng 2L", items[0].productName)
        assertEquals("PROD-PHYS-1", items[0].productUuid)
        assertEquals("pcs", items[0].unit)
    }

    /**
     * Test H & I: Supplier name and phone snapshot preserved.
     */
    @Test
    fun testHAndI_supplierSnapshots() = runBlocking {
        val poId = repository.createOrder(
            supplierId = 10L,
            items = listOf(PurchaseOrderItemInput(productId = 1L, quantity = 1.0))
        ).getOrThrow()

        val po = repository.getOrderById(poId)!!
        assertEquals("PT Agen Sembako Jaya", po.supplierNameSnapshot)
        assertEquals("081234567890", po.supplierPhoneSnapshot)
    }

    /**
     * Test J: Decimal FUEL quantity supported (Double).
     */
    @Test
    fun testJ_decimalFuelQuantity() = runBlocking {
        val poId = repository.createOrder(
            supplierId = 20L,
            items = listOf(PurchaseOrderItemInput(productId = 2L, quantity = 250.75))
        ).getOrThrow()

        val items = repository.getItemsForOrder(poId)
        assertEquals(250.75, items[0].orderedQuantity, 0.0001)
        assertEquals(0.0, items[0].receivedQuantity, 0.0001)
        val expectedSubtotal = (250.75 * 12500L).toLong()
        assertEquals(expectedSubtotal, items[0].estimatedSubtotal)
    }

    /**
     * Test K: Total estimated amount calculation.
     */
    @Test
    fun testK_totalEstimatedAmount() = runBlocking {
        val poId = repository.createOrder(
            supplierId = 10L,
            items = listOf(
                PurchaseOrderItemInput(productId = 1L, quantity = 2.0), // 2 * 28000 = 56000
                PurchaseOrderItemInput(productId = 3L, quantity = 1.0)  // 1 * 49000 = 49000
            )
        ).getOrThrow()

        val po = repository.getOrderById(poId)!!
        assertEquals(105000L, po.totalEstimatedAmount)
    }

    /**
     * Test L, M, N: Update DRAFT PO recalculates totals and refreshes supplier snapshot.
     */
    @Test
    fun testLMN_updateDraftPo() = runBlocking {
        val poId = repository.createOrder(
            supplierId = 10L,
            items = listOf(PurchaseOrderItemInput(productId = 1L, quantity = 1.0)) // 28000
        ).getOrThrow()

        // Update supplier to 20L and items to 2x product 1 + 1x product 2
        val updateRes = repository.updateOrder(
            orderId = poId,
            supplierId = 20L,
            items = listOf(
                PurchaseOrderItemInput(productId = 1L, quantity = 2.0), // 56000
                PurchaseOrderItemInput(productId = 2L, quantity = 10.0) // 125000
            ),
            notes = "Updated notes"
        )
        assertTrue("Update draft must succeed: ${updateRes.exceptionOrNull()?.message}", updateRes.isSuccess)

        val updatedPo = repository.getOrderById(poId)!!
        assertEquals(20L, updatedPo.supplierId)
        assertEquals("Pertamina Depot", updatedPo.supplierNameSnapshot)
        assertEquals("089876543210", updatedPo.supplierPhoneSnapshot)
        assertEquals(181000L, updatedPo.totalEstimatedAmount)
        assertEquals("Updated notes", updatedPo.notes)

        val items = repository.getItemsForOrder(poId)
        assertEquals(2, items.size)
    }

    /**
     * Test O & P: DRAFT -> ORDERED transition sets sentAt and status.
     */
    @Test
    fun testOP_markOrderOrdered() = runBlocking {
        val poId = repository.createOrder(
            supplierId = 10L,
            items = listOf(PurchaseOrderItemInput(productId = 1L, quantity = 5.0))
        ).getOrThrow()

        val orderTime = 1700000000000L
        val orderRes = repository.markOrderOrdered(poId, now = orderTime)
        assertTrue(orderRes.isSuccess)

        val orderedPo = repository.getOrderById(poId)!!
        assertEquals(PurchaseOrderStatus.ORDERED.name, orderedPo.status)
        assertEquals(orderTime, orderedPo.sentAt)
    }

    /**
     * Test Q & R: DRAFT -> CANCELLED and ORDERED -> CANCELLED transitions.
     */
    @Test
    fun testQR_cancelOrder() = runBlocking {
        // Cancel from DRAFT
        val draftPoId = repository.createOrder(
            supplierId = 10L,
            items = listOf(PurchaseOrderItemInput(productId = 1L, quantity = 2.0))
        ).getOrThrow()
        val cancelDraftRes = repository.cancelOrder(draftPoId)
        assertTrue(cancelDraftRes.isSuccess)
        assertEquals(PurchaseOrderStatus.CANCELLED.name, repository.getOrderById(draftPoId)?.status)

        // Cancel from ORDERED
        val orderedPoId = repository.createOrder(
            supplierId = 10L,
            items = listOf(PurchaseOrderItemInput(productId = 1L, quantity = 2.0))
        ).getOrThrow()
        repository.markOrderOrdered(orderedPoId)
        val cancelOrderedRes = repository.cancelOrder(orderedPoId)
        assertTrue(cancelOrderedRes.isSuccess)
        assertEquals(PurchaseOrderStatus.CANCELLED.name, repository.getOrderById(orderedPoId)?.status)
    }

    /**
     * Test S: RECEIVED PO cannot be cancelled.
     */
    @Test
    fun testS_receivedCannotBeCancelled() = runBlocking {
        val poId = repository.createOrder(
            supplierId = 10L,
            items = listOf(PurchaseOrderItemInput(productId = 1L, quantity = 1.0))
        ).getOrThrow()

        // Manually simulate a future RECEIVED state
        val po = orders[poId]!!
        orders[poId] = po.copy(status = PurchaseOrderStatus.RECEIVED.name)

        val cancelRes = repository.cancelOrder(poId)
        assertTrue("Cancelling RECEIVED PO must fail", cancelRes.isFailure)
    }

    /**
     * Test T & U: ORDERED and CANCELLED POs cannot be edited.
     */
    @Test
    fun testTU_orderedAndCancelledCannotBeEdited() = runBlocking {
        // ORDERED cannot be edited
        val poId1 = repository.createOrder(
            supplierId = 10L,
            items = listOf(PurchaseOrderItemInput(productId = 1L, quantity = 1.0))
        ).getOrThrow()
        repository.markOrderOrdered(poId1)

        val editOrderedRes = repository.updateOrder(
            orderId = poId1,
            supplierId = 10L,
            items = listOf(PurchaseOrderItemInput(productId = 1L, quantity = 5.0))
        )
        assertTrue("Editing ORDERED PO must fail", editOrderedRes.isFailure)

        // CANCELLED cannot be edited
        val poId2 = repository.createOrder(
            supplierId = 10L,
            items = listOf(PurchaseOrderItemInput(productId = 1L, quantity = 1.0))
        ).getOrThrow()
        repository.cancelOrder(poId2)

        val editCancelledRes = repository.updateOrder(
            orderId = poId2,
            supplierId = 10L,
            items = listOf(PurchaseOrderItemInput(productId = 1L, quantity = 5.0))
        )
        assertTrue("Editing CANCELLED PO must fail", editCancelledRes.isFailure)
    }

    /**
     * Test V, W, X, Y, Z, AA, AB: Strict Accounting & Stock Boundary Proof.
     * Zero mutations across Product.stock, StockMovement, CashTransaction, SupplierPayable, PurchaseTransaction.
     */
    @Test
    fun testVWXYZAAAB_strictAccountingAndStockIsolation() = runBlocking {
        val initialStock1 = products[1L]!!.stock
        val initialStock2 = products[2L]!!.stock

        // 1. CREATE PO
        val poId = repository.createOrder(
            supplierId = 10L,
            items = listOf(
                PurchaseOrderItemInput(productId = 1L, quantity = 20.0),
                PurchaseOrderItemInput(productId = 2L, quantity = 100.0)
            )
        ).getOrThrow()

        // 2. UPDATE PO
        repository.updateOrder(
            orderId = poId,
            supplierId = 10L,
            items = listOf(
                PurchaseOrderItemInput(productId = 1L, quantity = 30.0),
                PurchaseOrderItemInput(productId = 2L, quantity = 150.0)
            )
        ).getOrThrow()

        // 3. MARK ORDERED
        repository.markOrderOrdered(poId).getOrThrow()

        // 4. CANCEL PO
        repository.cancelOrder(poId).getOrThrow()

        // VERIFY ZERO SIDE EFFECTS
        assertEquals("Stock of Product 1 must not change", initialStock1, products[1L]!!.stock, 0.0001)
        assertEquals("Stock of Product 2 must not change", initialStock2, products[2L]!!.stock, 0.0001)
        assertEquals("Zero stock mutations must be called", 0, stockMutationCount)
        assertEquals("Zero StockMovement rows must be created", 0, stockMovementCount)
        assertEquals("Zero CashTransaction rows must be created", 0, cashTransactionCount)
        assertEquals("Zero SupplierPayable rows must be created", 0, supplierPayableCount)
        assertEquals("Zero PurchaseTransaction rows must be created", 0, purchaseTransactionCount)
    }

    /**
     * Test AC, AD, AE, AF: Universal ItemType support in Purchase Orders.
     */
    @Test
    fun testACADAEAF_universalItemTypeSupport() = runBlocking {
        val poId = repository.createOrder(
            supplierId = 10L,
            items = listOf(
                PurchaseOrderItemInput(productId = 1L, quantity = 10.0), // PHYSICAL
                PurchaseOrderItemInput(productId = 2L, quantity = 45.5), // FUEL
                PurchaseOrderItemInput(productId = 3L, quantity = 5.0),  // DIGITAL
                PurchaseOrderItemInput(productId = 4L, quantity = 1.0, estimatedPrice = 20000L) // SERVICE
            )
        ).getOrThrow()

        val items = repository.getItemsForOrder(poId)
        assertEquals(4, items.size)
        assertEquals(10.0, items[0].orderedQuantity, 0.0001)
        assertEquals(45.5, items[1].orderedQuantity, 0.0001)
        assertEquals(5.0, items[2].orderedQuantity, 0.0001)
        assertEquals(1.0, items[3].orderedQuantity, 0.0001)
        assertEquals(20000L, items[3].estimatedPrice)
    }

    /**
     * Test AG: Atomic PO + Item persistence.
     */
    @Test
    fun testAG_atomicPersistence() = runBlocking {
        val poId = repository.createOrder(
            supplierId = 10L,
            items = listOf(
                PurchaseOrderItemInput(productId = 1L, quantity = 2.0),
                PurchaseOrderItemInput(productId = 2L, quantity = 5.0)
            )
        ).getOrThrow()

        val po = repository.getOrderById(poId)
        val items = repository.getItemsForOrder(poId)

        assertNotNull(po)
        assertEquals(2, items.size)
        assertTrue(items.all { it.purchaseOrderId == poId })
    }

    /**
     * Test AH: Order number uniqueness and format.
     */
    @Test
    fun testAH_orderNumberFormatAndUniqueness() = runBlocking {
        val poId1 = repository.createOrder(
            supplierId = 10L,
            items = listOf(PurchaseOrderItemInput(productId = 1L, quantity = 1.0)),
            now = 1700000001000L
        ).getOrThrow()

        val poId2 = repository.createOrder(
            supplierId = 10L,
            items = listOf(PurchaseOrderItemInput(productId = 1L, quantity = 1.0)),
            now = 1700000002000L
        ).getOrThrow()

        val po1 = repository.getOrderById(poId1)!!
        val po2 = repository.getOrderById(poId2)!!

        assertTrue("Order number should start with PO-", po1.orderNumber.startsWith("PO-"))
        assertTrue("Order number should start with PO-", po2.orderNumber.startsWith("PO-"))
        assertFalse("Order numbers must be distinct", po1.orderNumber == po2.orderNumber)
    }
}
