package id.skmnetwork.bukuwarung

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import id.skmnetwork.bukuwarung.data.preferences.UserSettings
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import id.skmnetwork.bukuwarung.data.repository.SaleRepository
import id.skmnetwork.bukuwarung.domain.receipt.ReceiptPaperWidth
import id.skmnetwork.bukuwarung.printer.PrinterService
import id.skmnetwork.bukuwarung.printer.connection.MockPrinterConnection
import id.skmnetwork.bukuwarung.printer.escpos.EscPosReceiptFormatter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductionPrinterIntegrationTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var saleRepository: SaleRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        userPreferencesRepository = UserPreferencesRepository(context)
        productRepository = ProductRepository(database, "LEGACY_BUSINESS")
        saleRepository = SaleRepository(database, "LEGACY_BUSINESS")
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun testPrinterDataStorePersistenceAndRestoration() = runBlocking {
        // 1. Save Bluetooth printer config to DataStore
        userPreferencesRepository.updatePrinterConfig(
            printerType = "BLUETOOTH",
            printerDeviceName = "RPP02N-Thermal",
            printerAddress = "00:11:22:33:44:55",
            printerPaperWidth = "58MM",
            printerAutoConnect = true
        )

        val settings = userPreferencesRepository.userSettings.first()
        assertEquals("BLUETOOTH", settings.printerType)
        assertEquals("RPP02N-Thermal", settings.printerDeviceName)
        assertEquals("00:11:22:33:44:55", settings.printerAddress)
        assertEquals("58MM", settings.printerPaperWidth)
        assertTrue(settings.printerAutoConnect)

        // 2. Configure PrinterService and verify state
        val printerService = PrinterService()
        assertNull("Default PrinterService must have null connection in production", printerService.getConnection())
        assertFalse(printerService.isConnected)

        val mockConnection = MockPrinterConnection()
        printerService.setConnection(mockConnection)
        printerService.setPaperWidth(ReceiptPaperWidth.WIDTH_58MM)

        assertEquals(mockConnection, printerService.getConnection())
        assertEquals(ReceiptPaperWidth.WIDTH_58MM, printerService.getPaperWidth())

        // Connect
        val connectRes = printerService.connect()
        assertTrue("Connect must succeed", connectRes.isSuccess)
        assertTrue(printerService.isConnected)

        // 3. Clear configuration
        userPreferencesRepository.clearPrinterConfig()
        val clearedSettings = userPreferencesRepository.userSettings.first()
        assertEquals("NONE", clearedSettings.printerType)
        assertEquals("", clearedSettings.printerAddress)
    }

    @Test
    fun testTestPrintEscPosOutput() = runBlocking {
        val mockConnection = MockPrinterConnection()
        val printerService = PrinterService(
            activeConnection = mockConnection,
            paperWidth = ReceiptPaperWidth.WIDTH_58MM
        )

        val testPrintRes = printerService.printTestReceipt(
            shopName = "Warung Berkah Jaya",
            width = ReceiptPaperWidth.WIDTH_58MM
        )
        assertTrue("Test print must succeed", testPrintRes.isSuccess)
        assertTrue("Bytes must be sent", mockConnection.receivedByteChunks.isNotEmpty())
        assertTrue("Sent bytes must be non-empty", mockConnection.totalBytesReceived > 20)

        // Verify content formatting through EscPosReceiptFormatter
        val formatter = EscPosReceiptFormatter()
        val testBytes = formatter.formatTestReceipt("Warung Berkah Jaya", ReceiptPaperWidth.WIDTH_58MM)
        val testText = String(testBytes, Charsets.ISO_8859_1)

        assertTrue(testText.contains("Warung Berkah Jaya"))
        assertTrue(testText.contains("TEST PRINT"))
        assertTrue(testText.contains("--- TEST PRINT BERHASIL ---"))
    }

    @Test
    fun testPosCheckoutAndPrinterFailureIsolation() = runBlocking {
        val catId = database.categoryDao().insertCategory(CategoryEntity(name = "Makanan"))
        val prodId = database.productDao().insertProduct(
            ProductEntity(
                name = "Kopi Susu",
                categoryId = catId,
                purchasePrice = 3000L,
                sellingPrice = 5000L,
                stock = 10.0,
                unit = "cup",
                itemType = "PHYSICAL"
            )
        )

        // Setup failing printer connection
        val failingPrinter = MockPrinterConnection()
        failingPrinter.simulateErrorOnSend = true
        val printerService = PrinterService(activeConnection = failingPrinter)

        // Perform POS Checkout
        val checkoutResult = productRepository.processAtomicCheckout(
            cartItems = mapOf(prodId to 2.0),
            paymentMethod = "CASH"
        )
        assertTrue("POS Checkout must succeed despite printer state", checkoutResult.isSuccess)
        val saleId = checkoutResult.getOrThrow()

        // Verify DB state
        val sale = saleRepository.getTransactionById(saleId)
        assertNotNull(sale)
        assertEquals(10000L, sale!!.totalAmount)
        assertEquals(8.0, database.productDao().getProductById(prodId, "LEGACY_BUSINESS")!!.stock, 0.001)

        // Asynchronous post-commit print attempt fails safely
        val receiptData = saleRepository.getReceiptData(
            saleId = saleId,
            userSettings = UserSettings(shopName = "Warung Kopi")
        )
        assertNotNull(receiptData)

        val printResult = printerService.printReceipt(receiptData!!)
        assertTrue("Printer failure must be reported", printResult.isFailure)

        // Verify DB is untouched by printer error
        val postPrintSale = saleRepository.getTransactionById(saleId)
        assertNotNull(postPrintSale)
        assertEquals(8.0, database.productDao().getProductById(prodId, "LEGACY_BUSINESS")!!.stock, 0.001)
    }

    @Test
    fun testPrinter80mmPaperWidthSupport() = runBlocking {
        val mockConnection = MockPrinterConnection()
        val printerService = PrinterService(
            activeConnection = mockConnection,
            paperWidth = ReceiptPaperWidth.WIDTH_80MM
        )

        assertEquals(ReceiptPaperWidth.WIDTH_80MM, printerService.getPaperWidth())
        assertEquals(48, printerService.getPaperWidth().columns)

        val testPrintRes = printerService.printTestReceipt(
            shopName = "Toko Grosir 80mm",
            width = ReceiptPaperWidth.WIDTH_80MM
        )
        assertTrue(testPrintRes.isSuccess)
    }

    @Test
    fun testConnectAndSavePreservesActiveConnectionInstance() = runBlocking {
        val printerService = PrinterService()
        val conn = MockPrinterConnection(mockAddress = "66:32:B1:B2:D6:55")

        // 1. Connect
        val connectRes = conn.connect()
        assertTrue(connectRes.isSuccess)
        printerService.setConnection(conn)
        assertTrue("Printer must be connected", printerService.isConnected)
        assertEquals("Active connection must be exact instance", conn, printerService.getConnection())

        // 2. Simulate Save action: Matching check
        val savedType = "MOCK"
        val savedAddress = "66:32:B1:B2:D6:55"
        val isMatching = printerService.isMatchingConnection(savedType, savedAddress)
        assertTrue("Saved config must match active connection", isMatching)

        // Ensure instance reference is NOT replaced
        val connectionAfterSave = printerService.getConnection()
        assertTrue("Instance must remain identical", conn === connectionAfterSave)
    }

    @Test
    fun testDisconnectClearsActiveConnection() = runBlocking {
        val printerService = PrinterService()
        val conn = MockPrinterConnection()
        conn.connect()
        printerService.setConnection(conn)
        assertTrue(printerService.isConnected)

        // Clear / Putuskan
        printerService.clearConnection()
        assertNull("activeConnection must be null after clearConnection", printerService.getConnection())
        assertFalse("isConnected must be false", printerService.isConnected)
    }

    @Test
    fun testPrintWithoutConfigurationReturnsDescriptiveError() = runBlocking {
        val printerService = PrinterService()
        assertNull(printerService.getConnection())

        val result = printerService.printTestReceipt(shopName = "Warung Test")
        assertTrue(result.isFailure)
        assertEquals("Printer belum dikonfigurasi", result.exceptionOrNull()?.message)
    }

    @Test
    fun testPrintWithDisconnectedPrinterAttemptsAutoConnect() = runBlocking {
        val conn = MockPrinterConnection()
        assertFalse(conn.isConnected)

        val printerService = PrinterService(activeConnection = conn)
        // Auto connect is true by default
        val result = printerService.printTestReceipt(shopName = "Warung Test", autoConnect = true)
        assertTrue("Print with autoConnect should succeed", result.isSuccess)
        assertTrue("Connection should now be connected", conn.isConnected)
    }
}



