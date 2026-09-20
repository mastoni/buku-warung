package id.skmnetwork.bukuwarung.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.local.entity.CustomerEntity
import id.skmnetwork.bukuwarung.data.local.entity.DigitalTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseOrderEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseOrderItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.SupplierEntity
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import id.skmnetwork.bukuwarung.data.repository.CustomerRepository
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import id.skmnetwork.bukuwarung.data.repository.SaleRepository
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
class AdaptiveBackupTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var mockTransport: MockSheetsTransport
    private lateinit var backupRestoreManager: BackupRestoreManager

    private lateinit var productRepository: ProductRepository
    private lateinit var saleRepository: SaleRepository
    private lateinit var customerRepository: CustomerRepository
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
        saleRepository = SaleRepository(database, "TEST_BIZ")
        customerRepository = CustomerRepository(database, "TEST_BIZ")
        supplierRepository = SupplierRepository(database, "TEST_BIZ")
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun seedCommonData(businessId: String) {
        userPreferencesRepository.setBusinessId(businessId)
        val cat = productRepository.createCategory("Umum").getOrThrow()
        productRepository.insertProductWithCategory(
            name = "Produk A",
            categoryName = "Umum",
            purchasePrice = 10000,
            sellingPrice = 15000,
            stock = 20.0,
            minimumStock = 5.0,
            unit = "pcs",
            categoryId = cat.id
        )
        val customerId = customerRepository.saveCustomer("Customer A", "0811111", "Alamat A")
        val productId = database.productDao().getAllProducts(businessId).first().first().id
        val saleResult = saleRepository.completeSale(
            cartItems = mapOf(productId to 1.0),
            paymentMethod = "CASH",
            customerId = customerId
        )
        assertTrue(saleResult.isSuccess)
    }

    @Test
    fun testDigitalTransactionsSheetPresentWhenCapabilityActive() = runBlocking {
        userPreferencesRepository.updateBusinessProfile(
            primaryType = "WARUNG_SEMBAKO",
            secondaryActivities = setOf("ACTIVITY_GOODS_SELLING")
        )
        seedCommonData("TEST_BIZ_DIGITAL")

        val product = database.productDao().getAllProducts("TEST_BIZ_DIGITAL").first().first()
        val sale = database.saleDao().getAllTransactions("TEST_BIZ_DIGITAL").first().first()
        val saleItem = database.saleDao().getItemsForTransaction(sale.id, "TEST_BIZ_DIGITAL").first()

        database.digitalTransactionDao().insert(
            DigitalTransactionEntity(
                businessId = "TEST_BIZ_DIGITAL",
                saleItemId = saleItem.id,
                providerId = "P1",
                providerProductCode = "PD1",
                destinationNumber = "081234567890",
                sellingPrice = 15000,
                actualPurchasePrice = 10000,
                status = "SUCCESS"
            )
        )

        val snapshot = backupRestoreManager.exportSnapshot()
        assertNotNull("19_DigitalTransactions must exist when CAP_DIGITAL_ITEMS is active", snapshot.getTab("19_DigitalTransactions"))
        for (tabName in CanonicalSerializer.DATA_TAB_NAMES) {
            if (tabName != "19_DigitalTransactions") {
                assertTrue("Universal tab $tabName must always exist", snapshot.getTab(tabName) != null)
            }
        }
    }

    @Test
    fun testDigitalTransactionsSheetAbsentWhenCapabilityInactive() = runBlocking {
        userPreferencesRepository.updateBusinessProfile(
            primaryType = "WARUNG_BENGKEL",
            secondaryActivities = emptySet()
        )
        seedCommonData("TEST_BIZ_NO_DIGITAL")

        val snapshot = backupRestoreManager.exportSnapshot()
        assertNull("19_DigitalTransactions must be absent when CAP_DIGITAL_ITEMS is inactive", snapshot.getTab("19_DigitalTransactions"))
        for (tabName in CanonicalSerializer.DATA_TAB_NAMES) {
            if (tabName != "19_DigitalTransactions" && tabName != "20_PurchaseOrders" && tabName != "21_PurchaseOrderItems") {
                assertTrue("Universal tab $tabName must always exist", snapshot.getTab(tabName) != null)
            }
        }
    }

    @Test
    fun testPurchaseOrderSheetsPresentWhenActivityActive() = runBlocking {
        userPreferencesRepository.updateBusinessProfile(
            primaryType = "WARUNG_SEMBAKO",
            secondaryActivities = setOf("ACTIVITY_GOODS_SELLING", "ACTIVITY_WHOLESALE_PURCHASE")
        )
        seedCommonData("TEST_BIZ_PO")

        val supplierId = supplierRepository.saveSupplier("PT. Supplier", "0215550002", "Bandung")
        val product = database.productDao().getAllProducts("TEST_BIZ_PO").first().first()
        val poUuid = UUID.randomUUID().toString()
        val poId = database.purchaseOrderDao().insertPurchaseOrder(
            PurchaseOrderEntity(
                businessId = "TEST_BIZ_PO",
                deviceId = "DEV_1",
                orderNumber = "PO-2000002",
                supplierId = supplierId,
                supplierNameSnapshot = "PT. Supplier",
                status = "DRAFT",
                totalEstimatedAmount = 15000,
                createdAt = 3000000L,
                updatedAt = 3000100L
            )
        )
        database.purchaseOrderDao().insertPurchaseOrderItems(
            listOf(
                PurchaseOrderItemEntity(
                    purchaseOrderId = poId,
                    poUuid = poUuid,
                    productId = product.id,
                    productUuid = product.uuid,
                    productName = product.name,
                    orderedQuantity = 3.0,
                    unit = product.unit,
                    estimatedPrice = 5000,
                    estimatedSubtotal = 15000
                )
            )
        )

        val snapshot = backupRestoreManager.exportSnapshot()
        assertNotNull("20_PurchaseOrders must exist when ACTIVITY_WHOLESALE_PURCHASE is active", snapshot.getTab("20_PurchaseOrders"))
        assertNotNull("21_PurchaseOrderItems must exist when ACTIVITY_WHOLESALE_PURCHASE is active", snapshot.getTab("21_PurchaseOrderItems"))
    }

    @Test
    fun testPurchaseOrderSheetsAbsentWhenActivityInactive() = runBlocking {
        userPreferencesRepository.updateBusinessProfile(
            primaryType = "WARUNG_BENGKEL",
            secondaryActivities = emptySet()
        )
        seedCommonData("TEST_BIZ_NO_PO")

        val snapshot = backupRestoreManager.exportSnapshot()
        assertNull("20_PurchaseOrders must be absent when ACTIVITY_WHOLESALE_PURCHASE is inactive", snapshot.getTab("20_PurchaseOrders"))
        assertNull("21_PurchaseOrderItems must be absent when ACTIVITY_WHOLESALE_PURCHASE is inactive", snapshot.getTab("21_PurchaseOrderItems"))
    }

    @Test
    fun testUniversalSheetsAlwaysPresentRegardlessOfCapability() = runBlocking {
        userPreferencesRepository.updateBusinessProfile(
            primaryType = "WARUNG_BENGKEL",
            secondaryActivities = emptySet()
        )
        seedCommonData("TEST_BIZ_UNIVERSAL")

        val snapshot = backupRestoreManager.exportSnapshot()
        val universalTabs = listOf(
            "01_Business", "02_Device", "03_Categories", "04_Products",
            "05_Customers", "06_Sales", "07_SaleItems", "08_Purchases",
            "09_PurchaseItems", "10_Debts", "11_DebtPayments", "12_Suppliers",
            "13_SupplierPayables", "14_SupplierPayments", "15_CashTransactions",
            "16_StockMovements", "17_SaleReturns", "18_SaleReturnItems"
        )
        for (tabName in universalTabs) {
            assertNotNull("Universal tab $tabName must always be present", snapshot.getTab(tabName))
        }
    }
}
