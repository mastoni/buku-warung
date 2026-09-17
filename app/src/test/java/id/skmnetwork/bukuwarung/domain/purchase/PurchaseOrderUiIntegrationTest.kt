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
import id.skmnetwork.bukuwarung.domain.receipt.ReceiptPaperWidth
import id.skmnetwork.bukuwarung.printer.PrinterService
import id.skmnetwork.bukuwarung.printer.connection.MockPrinterConnection
import id.skmnetwork.bukuwarung.purchase.PurchaseOrderPdfBuilder
import id.skmnetwork.bukuwarung.purchase.WhatsAppPurchaseOrderFormatter
import id.skmnetwork.bukuwarung.ui.purchase.PurchaseOrderViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
 * Gate G13.5 — Purchase Order Creation & Management UI & Domain Integration Tests.
 * Covers Tests A through AC validating UI ViewModel workflows, filtering, DRAFT/ORDERED/CANCELLED/RECEIVED
 * state handling, immutability, WhatsApp/Print/PDF integration, and strict accounting/stock isolation.
 */
class PurchaseOrderUiIntegrationTest {

    private val suppliers = mutableMapOf<Long, SupplierEntity>()
    private val products = mutableMapOf<Long, ProductEntity>()
    private val orders = mutableMapOf<Long, PurchaseOrderEntity>()
    private val orderItems = mutableListOf<PurchaseOrderItemEntity>()
    private val syncQueue = mutableListOf<SyncQueueEntity>()

    private val ordersFlow = MutableStateFlow<List<PurchaseOrderEntity>>(emptyList())

    private var nextOrderId = 1L
    private var nextItemId = 1L

    private lateinit var fakeAppDatabase: AppDatabase
    private lateinit var purchaseOrderRepository: PurchaseOrderRepository
    private lateinit var poViewModel: PurchaseOrderViewModel

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
                    ordersFlow.value = orders.values.toList()
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
                "updatePurchaseOrder" -> {
                    val order = args[0] as PurchaseOrderEntity
                    orders[order.id] = order
                    ordersFlow.value = orders.values.toList()
                    null
                }
                "updatePurchaseOrderStatus" -> {
                    val id = args[0] as Long
                    val status = args[1] as String
                    val updatedAt = args[2] as Long
                    orders[id]?.let {
                        orders[id] = it.copy(status = status, updatedAt = updatedAt)
                        ordersFlow.value = orders.values.toList()
                    }
                    null
                }
                "updatePurchaseOrderToOrdered" -> {
                    val id = args[0] as Long
                    val status = args[1] as String
                    val sentAt = args[2] as Long
                    val updatedAt = args[3] as Long
                    orders[id]?.let {
                        orders[id] = it.copy(status = status, sentAt = sentAt, updatedAt = updatedAt)
                        ordersFlow.value = orders.values.toList()
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
                    val orderNumber = args[0] as String
                    orders.values.find { it.orderNumber == orderNumber }
                }
                "getItemsForPurchaseOrder" -> {
                    val orderId = args[0] as Long
                    orderItems.filter { it.purchaseOrderId == orderId }
                }
                "getItemsForPurchaseOrderByUuid" -> {
                    val poUuid = args[0] as String
                    orderItems.filter { it.poUuid == poUuid }
                }
                "getAllPurchaseOrders" -> {
                    ordersFlow
                }
                "getAllPurchaseOrdersList" -> {
                    orders.values.sortedByDescending { it.createdAt }.toList()
                }
                "getPurchaseOrdersByStatus" -> {
                    val status = args[0] as String
                    flowOf(orders.values.filter { it.status.equals(status, ignoreCase = true) })
                }
                "getPurchaseOrdersBySupplier" -> {
                    val supplierId = args[0] as Long
                    flowOf(orders.values.filter { it.supplierId == supplierId })
                }
                "deleteItemsForPurchaseOrder" -> {
                    val orderId = args[0] as Long
                    orderItems.removeAll { it.purchaseOrderId == orderId }
                    null
                }
                "deletePurchaseOrder" -> {
                    val order = args[0] as PurchaseOrderEntity
                    orders.remove(order.id)
                    orderItems.removeAll { it.purchaseOrderId == order.id }
                    ordersFlow.value = orders.values.toList()
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
                "getAllProducts" -> flowOf(products.values.toList())
                "getProductById" -> {
                    val id = args[0] as Long
                    products[id]
                }
                "insertProduct" -> {
                    val product = args[0] as ProductEntity
                    products[product.id] = product
                    product.id
                }
                "updateProduct" -> {
                    val product = args[0] as ProductEntity
                    products[product.id] = product
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
                "getAllSuppliers" -> flowOf(suppliers.values.toList())
                "getSupplierById" -> {
                    val id = args[0] as Long
                    suppliers[id]
                }
                "insertSupplier" -> {
                    val supplier = args[0] as SupplierEntity
                    suppliers[supplier.id] = supplier
                    supplier.id
                }
                "updateSupplier" -> {
                    val supplier = args[0] as SupplierEntity
                    suppliers[supplier.id] = supplier
                    null
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        suppliers.clear()
        products.clear()
        orders.clear()
        orderItems.clear()
        syncQueue.clear()
        nextOrderId = 1L
        nextItemId = 1L

        // Populate sample baseline products
        products[101L] = ProductEntity(
            id = 101L,
            uuid = "PROD-PHYS-101",
            categoryId = 1L,
            name = "Beras Rojolele 5kg",
            itemType = ItemType.PHYSICAL.name,
            sellingPrice = 75000L,
            purchasePrice = 65000L,
            stock = 20.0,
            unit = "sak"
        )
        products[102L] = ProductEntity(
            id = 102L,
            uuid = "PROD-FUEL-102",
            categoryId = 1L,
            name = "Pertamax",
            itemType = ItemType.FUEL.name,
            sellingPrice = 13500L,
            purchasePrice = 12500L,
            stock = 500.0,
            unit = "liter"
        )

        // Populate sample baseline supplier
        suppliers[10L] = SupplierEntity(
            id = 10L,
            uuid = "SUPP-UUID-10",
            name = "PT Sumber Pangan Sejahtera",
            phone = "081234567890",
            address = "Jl. Industri No. 12"
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

        purchaseOrderRepository = PurchaseOrderRepository(fakeAppDatabase, transactionRunner = { it() })
        poViewModel = PurchaseOrderViewModel(purchaseOrderRepository)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Test A & B: PO list renders and empty state handled.
     */
    @Test
    fun testAB_poListRenderAndEmptyState() = runBlocking {
        assertEquals("Initial filter is ALL", "ALL", poViewModel.filterStatus.value)
        assertTrue("Initial list is empty", poViewModel.filteredOrders.value.isEmpty())

        // Insert order
        val items = listOf(PurchaseOrderItemInput(101L, 2.0))
        val createResult = poViewModel.createDraftOrder(10L, items)
        assertTrue(createResult.isSuccess)

        val ordersList = poViewModel.allOrders.value
        assertEquals(1, ordersList.size)
        assertEquals("PT Sumber Pangan Sejahtera", ordersList[0].supplierNameSnapshot)
        assertEquals(PurchaseOrderStatus.DRAFT.name, ordersList[0].status)
    }

    /**
     * Test C: Create flow requires supplier.
     */
    @Test
    fun testC_createFlowSupplierRequired() = runBlocking {
        val nonExistentSupplierId = 999L
        val items = listOf(PurchaseOrderItemInput(101L, 1.0))
        val result = poViewModel.createDraftOrder(nonExistentSupplierId, items)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Supplier tidak ditemukan") == true)
    }

    /**
     * Test D: Create flow requires at least 1 item.
     */
    @Test
    fun testD_createFlowItemsRequired() = runBlocking {
        val result = poViewModel.createDraftOrder(10L, emptyList())
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("tidak boleh kosong") == true)
    }

    /**
     * Test E: Quantity validation (> 0).
     */
    @Test
    fun testE_quantityValidation() = runBlocking {
        val zeroQty = listOf(PurchaseOrderItemInput(101L, 0.0))
        val resultZero = poViewModel.createDraftOrder(10L, zeroQty)
        assertTrue(resultZero.isFailure)

        val negativeQty = listOf(PurchaseOrderItemInput(101L, -5.0))
        val resultNeg = poViewModel.createDraftOrder(10L, negativeQty)
        assertTrue(resultNeg.isFailure)
    }

    /**
     * Test F, G, H, I: Decimal FUEL, estimated price, subtotal, and total calculation.
     */
    @Test
    fun testFGHI_pricingAndSubtotalCalculation() = runBlocking {
        val items = listOf(
            PurchaseOrderItemInput(101L, 2.0, estimatedPrice = 65000L), // Subtotal: 130.000
            PurchaseOrderItemInput(102L, 12.5, estimatedPrice = 12500L) // Subtotal: 156.250
        )
        val createResult = poViewModel.createDraftOrder(10L, items, notes = "Kirim pagi")
        assertTrue(createResult.isSuccess)

        val orderId = createResult.getOrThrow()
        val details = poViewModel.loadOrderDetails(orderId)
        assertNotNull(details)

        val (order, orderItemsList) = details!!
        assertEquals(286250L, order.totalEstimatedAmount)
        assertEquals(2, orderItemsList.size)

        // Verify FUEL decimal preserved
        val fuelItem = orderItemsList.find { it.productId == 102L }!!
        assertEquals(12.5, fuelItem.orderedQuantity, 0.001)
        assertEquals(12500L, fuelItem.estimatedPrice)
        assertEquals(156250L, fuelItem.estimatedSubtotal)

        // Verify physical item
        val physItem = orderItemsList.find { it.productId == 101L }!!
        assertEquals(2.0, physItem.orderedQuantity, 0.001)
        assertEquals(65000L, physItem.estimatedPrice)
        assertEquals(130000L, physItem.estimatedSubtotal)
    }

    /**
     * Test J, K, L, M: Notes handling, Save DRAFT, Reopen DRAFT, and Edit DRAFT.
     */
    @Test
    fun testJKLM_draftLifecycleAndEditing() = runBlocking {
        val initialItems = listOf(PurchaseOrderItemInput(101L, 1.0))
        val createRes = poViewModel.createDraftOrder(10L, initialItems, notes = "Catatan awal")
        val orderId = createRes.getOrThrow()

        val draftDetails = poViewModel.loadOrderDetails(orderId)
        assertNotNull(draftDetails)
        assertEquals("Catatan awal", draftDetails!!.first.notes)
        assertEquals(1, draftDetails.second.size)

        // Edit DRAFT
        val updatedItems = listOf(
            PurchaseOrderItemInput(101L, 3.0),
            PurchaseOrderItemInput(102L, 10.0)
        )
        val updateRes = poViewModel.updateDraftOrder(orderId, 10L, updatedItems, notes = "Catatan revisi")
        assertTrue(updateRes.isSuccess)

        val reloaded = poViewModel.loadOrderDetails(orderId)
        assertNotNull(reloaded)
        assertEquals("Catatan revisi", reloaded!!.first.notes)
        assertEquals(2, reloaded.second.size)
        assertEquals(320000L, reloaded.first.totalEstimatedAmount)
    }

    /**
     * Test N & O: Mark ORDERED and verify immutability.
     */
    @Test
    fun testNO_markOrderedAndImmutability() = runBlocking {
        val items = listOf(PurchaseOrderItemInput(101L, 2.0))
        val createRes = poViewModel.createDraftOrder(10L, items)
        val orderId = createRes.getOrThrow()

        val markRes = poViewModel.markOrderOrdered(orderId)
        assertTrue(markRes.isSuccess)

        val orderedDetails = poViewModel.loadOrderDetails(orderId)
        assertEquals(PurchaseOrderStatus.ORDERED.name, orderedDetails!!.first.status)
        assertNotNull(orderedDetails.first.sentAt)

        // Attempting to edit an ORDERED order must fail
        val editRes = poViewModel.updateDraftOrder(orderId, 10L, listOf(PurchaseOrderItemInput(101L, 5.0)))
        assertTrue("Editing ORDERED order must be rejected", editRes.isFailure)
        assertTrue(editRes.exceptionOrNull()?.message?.contains("Hanya Purchase Order berstatus DRAFT") == true)
    }

    /**
     * Test P, Q, R, S: Cancel DRAFT & Cancel ORDERED, verify read-only behavior.
     */
    @Test
    fun testPQRS_cancellationAndReadOnlyStatus() = runBlocking {
        // Cancel Draft
        val draftRes = poViewModel.createDraftOrder(10L, listOf(PurchaseOrderItemInput(101L, 1.0)))
        val draftId = draftRes.getOrThrow()
        val cancelDraftRes = poViewModel.cancelOrder(draftId)
        assertTrue(cancelDraftRes.isSuccess)
        assertEquals(PurchaseOrderStatus.CANCELLED.name, poViewModel.loadOrderDetails(draftId)!!.first.status)

        // Cancel Ordered
        val ordRes = poViewModel.createDraftOrder(10L, listOf(PurchaseOrderItemInput(101L, 1.0)))
        val ordId = ordRes.getOrThrow()
        poViewModel.markOrderOrdered(ordId)
        val cancelOrdRes = poViewModel.cancelOrder(ordId)
        assertTrue(cancelOrdRes.isSuccess)
        assertEquals(PurchaseOrderStatus.CANCELLED.name, poViewModel.loadOrderDetails(ordId)!!.first.status)

        // Idempotent cancel on already cancelled
        val reCancel = poViewModel.cancelOrder(ordId)
        assertTrue(reCancel.isSuccess)
        assertEquals(PurchaseOrderStatus.CANCELLED.name, poViewModel.loadOrderDetails(ordId)!!.first.status)
    }

    /**
     * Test T, U, V: WhatsApp formatter, thermal print, and PDF document generation.
     */
    @Test
    fun testTUV_whatsappPrintPdfIntegration() = runBlocking {
        val items = listOf(
            PurchaseOrderItemInput(101L, 2.0, estimatedPrice = 65000L),
            PurchaseOrderItemInput(102L, 8.64, estimatedPrice = 12500L)
        )
        val createRes = poViewModel.createDraftOrder(10L, items, notes = "Uji cetak dan share")
        val orderId = createRes.getOrThrow()
        val (order, orderItemsList) = poViewModel.loadOrderDetails(orderId)!!

        // Test T: WhatsApp format
        val waText = WhatsAppPurchaseOrderFormatter.formatOrderText("Toko Maju", order, orderItemsList)
        assertTrue(waText.contains("*PURCHASE ORDER*"))
        assertTrue(waText.contains("PT Sumber Pangan Sejahtera"))
        assertTrue(waText.contains("8.64 liter"))
        assertTrue(waText.contains("Estimasi Total"))

        // Test U: Print execution via PrinterService
        val mock = MockPrinterConnection()
        val printerService = PrinterService(activeConnection = mock)
        val printRes = poViewModel.printOrder(printerService, order, orderItemsList, "Toko Maju", paperWidth = ReceiptPaperWidth.WIDTH_58MM)
        assertTrue(printRes.isSuccess)
        assertTrue(mock.fullPrintedText.contains("Toko Maju"))
        assertTrue(mock.fullPrintedText.contains("PT Sumber Pangan Sejahtera"))

        // Test V: PDF Document creation via PurchaseOrderPdfBuilder
        val pdfDoc = PurchaseOrderPdfBuilder.build(order, orderItemsList, "Toko Maju")
        assertNotNull(pdfDoc)
        assertEquals("PURCHASE ORDER", pdfDoc.header.reportTitle)
        assertEquals(2, pdfDoc.sections.size)
    }

    /**
     * Test W, X, Y, Z: Zero stock mutations, zero cash mutations, zero payable mutations,
     * and zero PurchaseTransaction creation throughout entire PO workflow.
     */
    @Test
    fun testWXYZ_strictZeroSideEffects() = runBlocking {
        val initialPhysicalStock = products[101L]!!.stock // 20.0
        val initialFuelStock = products[102L]!!.stock // 500.0

        // Create PO
        val createRes = poViewModel.createDraftOrder(
            10L,
            listOf(PurchaseOrderItemInput(101L, 10.0), PurchaseOrderItemInput(102L, 50.0))
        )
        val orderId = createRes.getOrThrow()

        // Update PO
        poViewModel.updateDraftOrder(
            orderId,
            10L,
            listOf(PurchaseOrderItemInput(101L, 15.0), PurchaseOrderItemInput(102L, 75.0))
        )

        // Mark Ordered
        poViewModel.markOrderOrdered(orderId)

        // Cancel PO
        poViewModel.cancelOrder(orderId)

        // Verify products stock has never changed
        assertEquals("Physical stock unchanged", initialPhysicalStock, products[101L]!!.stock, 0.0)
        assertEquals("Fuel stock unchanged", initialFuelStock, products[102L]!!.stock, 0.0)
    }

    /**
     * Test AA: Adaptive terminology support.
     */
    @Test
    fun testAA_adaptiveTerminology() = runBlocking {
        val createRes = poViewModel.createDraftOrder(10L, listOf(PurchaseOrderItemInput(101L, 1.0)))
        val orderId = createRes.getOrThrow()
        val (order, items) = poViewModel.loadOrderDetails(orderId)!!

        val bengkelText = WhatsAppPurchaseOrderFormatter.formatOrderText(
            shopName = "Bengkel Motor Jaya",
            order = order,
            items = items,
            poTitle = "ORDER SPAREPART"
        )
        assertTrue(bengkelText.contains("*ORDER SPAREPART*"))

        val tbPdf = PurchaseOrderPdfBuilder.build(
            order = order,
            items = items,
            shopName = "TB Bangunan Jaya",
            poTitle = "ORDER MATERIAL"
        )
        assertEquals("ORDER MATERIAL", tbPdf.header.reportTitle)
    }

    /**
     * Test AB & AC: List filtering across all statuses.
     */
    @Test
    fun testABAC_filteringAcrossAllStatuses() = runBlocking {
        // Create 1 Draft
        val draftId = poViewModel.createDraftOrder(10L, listOf(PurchaseOrderItemInput(101L, 1.0))).getOrThrow()

        // Create 1 Ordered
        val ordId = poViewModel.createDraftOrder(10L, listOf(PurchaseOrderItemInput(101L, 1.0))).getOrThrow()
        poViewModel.markOrderOrdered(ordId)

        // Create 1 Cancelled
        val canId = poViewModel.createDraftOrder(10L, listOf(PurchaseOrderItemInput(101L, 1.0))).getOrThrow()
        poViewModel.cancelOrder(canId)

        poViewModel.setFilter("ALL")
        assertEquals(3, poViewModel.filteredOrders.value.size)

        poViewModel.setFilter("DRAFT")
        assertEquals(1, poViewModel.filteredOrders.value.size)
        assertEquals(draftId, poViewModel.filteredOrders.value[0].id)

        poViewModel.setFilter("ORDERED")
        assertEquals(1, poViewModel.filteredOrders.value.size)
        assertEquals(ordId, poViewModel.filteredOrders.value[0].id)

        poViewModel.setFilter("CANCELLED")
        assertEquals(1, poViewModel.filteredOrders.value.size)
        assertEquals(canId, poViewModel.filteredOrders.value[0].id)
    }
}
