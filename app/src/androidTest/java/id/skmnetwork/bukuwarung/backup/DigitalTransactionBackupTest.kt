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
class DigitalTransactionBackupTest {

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

    @Test
    fun testDigitalTransactionRoundTrip() = runBlocking {
        userPreferencesRepository.setBusinessId("TEST_BIZ")
        userPreferencesRepository.updateBusinessProfile(
            primaryType = "WARUNG_SEMBAKO",
            secondaryActivities = setOf("ACTIVITY_GOODS_SELLING")
        )

        val cat = productRepository.createCategory("Pulsa").getOrThrow()
        val productId = productRepository.insertProductWithCategory(
            name = "Pulsa 50K",
            categoryName = "Pulsa",
            purchasePrice = 48000,
            sellingPrice = 50000,
            stock = 100.0,
            minimumStock = 10.0,
            unit = "pcs",
            categoryId = cat.id
        )

        val customerId = customerRepository.saveCustomer("Budi", "0812345", "Jl. Test")
        val saleResult = saleRepository.completeSale(
            cartItems = mapOf(productId to 2.0),
            paymentMethod = "CASH",
            customerId = customerId
        )
        assertTrue(saleResult.isSuccess)

        val sale = database.saleDao().getAllTransactions("TEST_BIZ").first().first()
        val saleItem = database.saleDao().getItemsForTransaction(sale.id, "TEST_BIZ").first()

        val digitalDao = database.digitalTransactionDao()
        val digitalTx = DigitalTransactionEntity(
            businessId = "TEST_BIZ",
            saleItemId = saleItem.id,
            providerId = "PROVIDER_A",
            providerProductCode = "PULSA_50K",
            destinationNumber = "081234567890",
            sellingPrice = 50000,
            actualPurchasePrice = 48000,
            status = "SUCCESS",
            providerReferenceId = "REF_123",
            snToken = "TOKEN_ABC",
            failureReason = null,
            createdAt = 1000000L,
            updatedAt = 1000100L
        )
        val digitalId = digitalDao.insert(digitalTx)
        val insertedDigital = digitalDao.getById(digitalId, "TEST_BIZ")!!
        assertEquals(digitalTx.uuid, insertedDigital.uuid)
        assertEquals(digitalTx.businessId, insertedDigital.businessId)
        assertEquals(digitalTx.saleItemId, insertedDigital.saleItemId)

        val snapshot = backupRestoreManager.exportSnapshot()
        val digitalTab = snapshot.getTab("19_DigitalTransactions")
        assertNotNull("DigitalTransactions tab must be present for CAP_DIGITAL_ITEMS", digitalTab)
        val row = digitalTab!!.rows.first()
        assertEquals(insertedDigital.uuid, row[0])
        assertEquals("TEST_BIZ", row[1])
        assertEquals(saleItem.uuid, row[2])
        assertEquals("PROVIDER_A", row[3])
        assertEquals("PULSA_50K", row[4])
        assertEquals("081234567890", row[5])
        assertEquals("50000", row[6])
        assertEquals("48000", row[7])
        assertEquals("SUCCESS", row[8])
        assertEquals("REF_123", row[9])
        assertEquals("TOKEN_ABC", row[10])
        assertEquals("", row[11])
        assertEquals("1000000", row[12])
        assertEquals("1000100", row[13])

        val spreadsheetId = "DIGITAL_ROUNDTRIP_1"
        val backupResult = backupRestoreManager.performBackup(spreadsheetId)
        assertTrue(backupResult.isSuccess)

        database.openHelper.writableDatabase.execSQL("DELETE FROM digital_transactions")
        database.openHelper.writableDatabase.execSQL("DELETE FROM sale_items")
        database.openHelper.writableDatabase.execSQL("DELETE FROM sales_transactions")
        database.openHelper.writableDatabase.execSQL("DELETE FROM products")
        database.openHelper.writableDatabase.execSQL("DELETE FROM categories")
        database.openHelper.writableDatabase.execSQL("DELETE FROM customers")

        val restoreResult = backupRestoreManager.performRestore(spreadsheetId, "TEST_BIZ")
        assertTrue(restoreResult.isSuccess)

        val restoredDigital = digitalDao.getByUuid(insertedDigital.uuid, "TEST_BIZ")
        assertNotNull("Digital transaction must be restored", restoredDigital)
        assertEquals(insertedDigital.uuid, restoredDigital!!.uuid)
        assertEquals("TEST_BIZ", restoredDigital.businessId)
        assertEquals(saleItem.id, restoredDigital.saleItemId)
        assertEquals("PROVIDER_A", restoredDigital.providerId)
        assertEquals("PULSA_50K", restoredDigital.providerProductCode)
        assertEquals("081234567890", restoredDigital.destinationNumber)
        assertEquals(50000, restoredDigital.sellingPrice)
        assertEquals(48000, restoredDigital.actualPurchasePrice)
        assertEquals("SUCCESS", restoredDigital.status)
        assertEquals("REF_123", restoredDigital.providerReferenceId)
        assertEquals("TOKEN_ABC", restoredDigital.snToken)
        assertEquals(null, restoredDigital.failureReason)
        assertEquals(1000000L, restoredDigital.createdAt)
        assertEquals(1000100L, restoredDigital.updatedAt)

        val restoredSaleItem = database.saleDao().getItemsForTransaction(sale.id, "TEST_BIZ").firstOrNull()
        assertNotNull("Sale item must be restored for relationship", restoredSaleItem)
        assertEquals(saleItem.uuid, restoredSaleItem!!.uuid)
    }

    @Test
    fun testDigitalTransactionNotExportedWithoutCapability() = runBlocking {
        userPreferencesRepository.setBusinessId("TEST_BIZ_NO_DIGITAL")
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
        assertNull("DigitalTransactions tab must be absent when CAP_DIGITAL_ITEMS is not active", snapshot.getTab("19_DigitalTransactions"))
    }
}
