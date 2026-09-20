package id.skmnetwork.bukuwarung.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseOrderEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseOrderItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.SupplierEntity
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import id.skmnetwork.bukuwarung.data.repository.SupplierRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PurchaseOrderBackupTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var mockTransport: MockSheetsTransport
    private lateinit var backupRestoreManager: BackupRestoreManager

    private lateinit var productRepository: ProductRepository
    private lateinit var supplierRepository: SupplierRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        userPreferencesRepository = UserPreferencesRepository(context)
        mockTransport = MockSheetsTransport()
        backupRestoreManager = BackupRestoreManager(
            database = database,
            userPreferencesRepository = userPreferencesRepository,
            transport = mockTransport
        )

        productRepository = ProductRepository(database, "TEST_BIZ")
        supplierRepository = SupplierRepository(database, "TEST_BIZ")
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testPurchaseOrderRoundTrip() = runBlocking {
        userPreferencesRepository.setBusinessId("TEST_BIZ")
        userPreferencesRepository.updateBusinessProfile(
            primaryType = "WARUNG_SEMBAKO",
            secondaryActivities = setOf("ACTIVITY_GOODS_SELLING", "ACTIVITY_WHOLESALE_PURCHASE")
        )

        val cat = productRepository.createCategory("Bumbu").getOrThrow()
        val product1Id = productRepository.insertProductWithCategory(
            name = "Garam 1kg",
            categoryName = "Bumbu",
            purchasePrice = 5000,
            sellingPrice = 7000,
            stock = 50.0,
            minimumStock = 10.0,
            unit = "kg",
            categoryId = cat.id
        )
        val product2Id = productRepository.insertProductWithCategory(
            name = "Merica 100g",
            categoryName = "Bumbu",
            purchasePrice = 8000,
            sellingPrice = 12000,
            stock = 30.0,
            minimumStock = 5.0,
            unit = "pack",
            categoryId = cat.id
        )

        val supplierId = supplierRepository.saveSupplier("PT. Bumbu Sejahtera", "0215550001", "Jakarta")
        val product1 = database.productDao().getProductById(product1Id, "TEST_BIZ")!!
        val product2 = database.productDao().getProductById(product2Id, "TEST_BIZ")!!

        val poUuid = UUID.randomUUID().toString()
        val now = 2000000L
        val poEntity = PurchaseOrderEntity(
            businessId = "TEST_BIZ",
            deviceId = "DEV_1",
            orderNumber = "PO-2000001",
            supplierId = supplierId,
            supplierNameSnapshot = "PT. Bumbu Sejahtera",
            supplierPhoneSnapshot = "0215550001",
            status = "ORDERED",
            totalEstimatedAmount = 13000,
            notes = "Penting",
            createdAt = now,
            updatedAt = now + 100L,
            sentAt = now + 50L,
            receivedAt = null,
            finalPurchaseId = null
        )
        val poId = database.purchaseOrderDao().insertPurchaseOrder(poEntity)

        val poi1 = PurchaseOrderItemEntity(
            purchaseOrderId = poId,
            poUuid = poUuid,
            productId = product1.id,
            productUuid = product1.uuid,
            productName = product1.name,
            orderedQuantity = 10.0,
            unit = product1.unit,
            estimatedPrice = 5000,
            estimatedSubtotal = 50000,
            receivedQuantity = 0.0
        )
        val poi2 = PurchaseOrderItemEntity(
            purchaseOrderId = poId,
            poUuid = poUuid,
            productId = product2.id,
            productUuid = product2.uuid,
            productName = product2.name,
            orderedQuantity = 5.0,
            unit = product2.unit,
            estimatedPrice = 8000,
            estimatedSubtotal = 40000,
            receivedQuantity = 0.0
        )
        database.purchaseOrderDao().insertPurchaseOrderItems(listOf(poi1, poi2))

        val snapshot = backupRestoreManager.exportSnapshot()
        val poTab = snapshot.getTab("20_PurchaseOrders")
        val poiTab = snapshot.getTab("21_PurchaseOrderItems")
        assertNotNull("PurchaseOrders tab must be present for ACTIVITY_WHOLESALE_PURCHASE", poTab)
        assertNotNull("PurchaseOrderItems tab must be present for ACTIVITY_WHOLESALE_PURCHASE", poiTab)

        val poRow = poTab!!.rows.first()
        assertEquals(poEntity.uuid, poRow[0])
        assertEquals("TEST_BIZ", poRow[1])
        assertEquals("DEV_1", poRow[2])
        assertEquals("PO-2000001", poRow[3])
        val supplier = database.supplierDao().getSupplierById(supplierId, "TEST_BIZ")
        assertEquals(supplier!!.uuid, poRow[4])
        assertEquals("PT. Bumbu Sejahtera", poRow[5])
        assertEquals("0215550001", poRow[6])
        assertEquals("ORDERED", poRow[7])
        assertEquals("13000", poRow[8])
        assertEquals("Penting", poRow[9])
        assertEquals(now.toString(), poRow[10])
        assertEquals((now + 100).toString(), poRow[11])
        assertEquals((now + 50).toString(), poRow[12])
        assertEquals("", poRow[13])
        assertEquals("", poRow[14])

        assertEquals(2, poiTab!!.rows.size)
        val poiRow1 = poiTab.rows.find { it[3] == product1.uuid }!!
        assertEquals(poi1.uuid, poiRow1[0])
        assertEquals("TEST_BIZ", poiRow1[1])
        assertEquals(poUuid, poiRow1[2])
        assertEquals(product1.uuid, poiRow1[3])
        assertEquals("Garam 1kg", poiRow1[4])
        assertEquals("10.0", poiRow1[5])
        assertEquals("kg", poiRow1[6])
        assertEquals("5000", poiRow1[7])
        assertEquals("50000", poiRow1[8])
        assertEquals("0.0", poiRow1[9])
        assertEquals("", poiRow1[10])

        val spreadsheetId = "PO_ROUNDTRIP_1"
        val backupResult = backupRestoreManager.performBackup(spreadsheetId)
        assertTrue(backupResult.isSuccess)

        database.openHelper.writableDatabase.execSQL("DELETE FROM purchase_order_items")
        database.openHelper.writableDatabase.execSQL("DELETE FROM purchase_orders")
        database.openHelper.writableDatabase.execSQL("DELETE FROM products")
        database.openHelper.writableDatabase.execSQL("DELETE FROM categories")
        database.openHelper.writableDatabase.execSQL("DELETE FROM suppliers")

        val restoreResult = backupRestoreManager.performRestore(spreadsheetId, "TEST_BIZ")
        assertTrue(restoreResult.isSuccess)

        val restoredSupplier = database.supplierDao().getSupplierById(supplierId, "TEST_BIZ")
        assertNotNull("Supplier must be restored", restoredSupplier)
        assertEquals("PT. Bumbu Sejahtera", restoredSupplier!!.name)

        val restoredProduct1 = database.productDao().getProductByUuid(product1.uuid, "TEST_BIZ")
        assertNotNull("Product must be restored", restoredProduct1)
        assertEquals("Garam 1kg", restoredProduct1!!.name)

        val restoredPOs = database.purchaseOrderDao().getAllPurchaseOrdersList("TEST_BIZ")
        assertEquals(1, restoredPOs.size)
        val restoredPO = restoredPOs.first()
        assertEquals(poEntity.uuid, restoredPO.uuid)
        assertEquals("TEST_BIZ", restoredPO.businessId)
        assertEquals("DEV_1", restoredPO.deviceId)
        assertEquals("PO-2000001", restoredPO.orderNumber)
        assertEquals(restoredSupplier.id, restoredPO.supplierId)
        assertEquals("PT. Bumbu Sejahtera", restoredPO.supplierNameSnapshot)
        assertEquals("0215550001", restoredPO.supplierPhoneSnapshot)
        assertEquals("ORDERED", restoredPO.status)
        assertEquals(13000, restoredPO.totalEstimatedAmount)
        assertEquals("Penting", restoredPO.notes)
        assertEquals(now, restoredPO.createdAt)
        assertEquals(now + 100, restoredPO.updatedAt)
        assertEquals(now + 50, restoredPO.sentAt)
        assertEquals(null, restoredPO.receivedAt)
        assertEquals(null, restoredPO.finalPurchaseId)

        val restoredItems = database.purchaseOrderDao().getItemsForPurchaseOrderByUuid("TEST_BIZ", poUuid)
        assertEquals(2, restoredItems.size)
        val restoredItem1 = restoredItems.find { it.productUuid == product1.uuid }!!
        assertEquals(poi1.uuid, restoredItem1.uuid)
        assertEquals(restoredPO.id, restoredItem1.purchaseOrderId)
        assertEquals(poUuid, restoredItem1.poUuid)
        assertEquals(restoredProduct1.id, restoredItem1.productId)
        assertEquals(product1.uuid, restoredItem1.productUuid)
        assertEquals("Garam 1kg", restoredItem1.productName)
        assertEquals(10.0, restoredItem1.orderedQuantity, 0.001)
        assertEquals("kg", restoredItem1.unit)
        assertEquals(5000, restoredItem1.estimatedPrice)
        assertEquals(50000, restoredItem1.estimatedSubtotal)
        assertEquals(0.0, restoredItem1.receivedQuantity, 0.001)
    }

    @Test
    fun testPurchaseOrderNotExportedWithoutCapability() = runBlocking {
        userPreferencesRepository.setBusinessId("TEST_BIZ_NO_PO")
        userPreferencesRepository.updateBusinessProfile(
            primaryType = "WARUNG_BENGKEL",
            secondaryActivities = emptySet()
        )

        val cat = productRepository.createCategory("Oli").getOrThrow()
        productRepository.insertProductWithCategory(
            name = "Oli Motor",
            categoryName = "Oli",
            purchasePrice = 25000,
            sellingPrice = 30000,
            stock = 10.0,
            minimumStock = 2.0,
            unit = "liter",
            categoryId = cat.id
        )

        val snapshot = backupRestoreManager.exportSnapshot()
        assertNull("PurchaseOrders tab must be absent when ACTIVITY_WHOLESALE_PURCHASE is not active", snapshot.getTab("20_PurchaseOrders"))
        assertNull("PurchaseOrderItems tab must be absent when ACTIVITY_WHOLESALE_PURCHASE is not active", snapshot.getTab("21_PurchaseOrderItems"))
    }
}
