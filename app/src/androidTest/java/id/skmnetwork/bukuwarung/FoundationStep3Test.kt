package id.skmnetwork.bukuwarung

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.preferences.UserSettings
import id.skmnetwork.bukuwarung.data.repository.CashRepository
import id.skmnetwork.bukuwarung.data.repository.CustomerRepository
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import id.skmnetwork.bukuwarung.data.repository.SaleRepository
import id.skmnetwork.bukuwarung.data.repository.StockRepository
import id.skmnetwork.bukuwarung.domain.receipt.ReceiptData
import id.skmnetwork.bukuwarung.domain.receipt.ReceiptItem
import id.skmnetwork.bukuwarung.domain.receipt.ReceiptMapper
import id.skmnetwork.bukuwarung.domain.receipt.ReceiptPaperWidth
import id.skmnetwork.bukuwarung.domain.receipt.ReceiptPaymentInfo
import id.skmnetwork.bukuwarung.domain.receipt.ShopProfile
import id.skmnetwork.bukuwarung.printer.PrinterService
import id.skmnetwork.bukuwarung.printer.connection.MockPrinterConnection
import id.skmnetwork.bukuwarung.printer.escpos.EscPosCommands
import id.skmnetwork.bukuwarung.printer.escpos.EscPosReceiptFormatter
import id.skmnetwork.bukuwarung.printer.escpos.PlainTextReceiptFormatter
import id.skmnetwork.bukuwarung.printer.escpos.ReceiptTextFormatterUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoundationStep3Test {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var productRepository: ProductRepository
    private lateinit var stockRepository: StockRepository
    private lateinit var saleRepository: SaleRepository
    private lateinit var cashRepository: CashRepository
    private lateinit var customerRepository: CustomerRepository

    private val escPosFormatter = EscPosReceiptFormatter()
    private val plainTextFormatter = PlainTextReceiptFormatter()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        productRepository = ProductRepository(database)
        stockRepository = StockRepository(database)
        saleRepository = SaleRepository(database)
        cashRepository = CashRepository(database)
        customerRepository = CustomerRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testReceiptData_mappingFromCommittedSale() = runBlocking {
        val prodId = productRepository.insertProductWithCategory(
            name = "Indomie Goreng",
            categoryName = "Makanan",
            purchasePrice = 2500,
            sellingPrice = 3500,
            stock = 20.0,
            minimumStock = 5.0,
            unit = "bks"
        )

        val saleId = saleRepository.completeSale(
            cartItems = mapOf(prodId to 3.0),
            paymentMethod = "CASH"
        ).getOrThrow()

        val sale = saleRepository.getTransactionById(saleId)!!
        val items = saleRepository.getItemsForTransaction(saleId)

        val userSettings = UserSettings(
            shopName = "Warung Berkah",
            phone = "08123456789",
            address = "Jl. Sudirman No. 10",
            receiptFooterText = "Matur Nuwun!"
        )

        val receiptData = ReceiptMapper.mapFromSale(
            sale = sale,
            items = items,
            userSettings = userSettings,
            cashGiven = 15000L
        )

        assertEquals("Warung Berkah", receiptData.shopProfile.shopName)
        assertEquals("08123456789", receiptData.shopProfile.phone)
        assertEquals("Jl. Sudirman No. 10", receiptData.shopProfile.address)
        assertEquals("Matur Nuwun!", receiptData.shopProfile.footerMessage)
        assertEquals(sale.transactionNumber, receiptData.receiptNumber)
        assertEquals(sale.uuid, receiptData.transactionUuid)
        assertEquals(1, receiptData.items.size)
        assertEquals("Indomie Goreng", receiptData.items[0].name)
        assertEquals(3.0, receiptData.items[0].quantity, 0.001)
        assertEquals(3500L, receiptData.items[0].price)
        assertEquals(10500L, receiptData.items[0].subtotal)
        assertEquals("CASH", receiptData.paymentInfo.method)
        assertEquals(10500L, receiptData.paymentInfo.totalAmount)
        assertEquals(15000L, receiptData.paymentInfo.payAmount)
        assertEquals(4500L, receiptData.paymentInfo.changeAmount)
    }

    @Test
    fun testEscPosFormatter_58mm_generatesValidEscPosBytes() {
        val receiptData = ReceiptData(
            receiptNumber = "TRX-1001",
            transactionUuid = "UUID-1001",
            dateTimeMillis = 1773295200000L,
            shopProfile = ShopProfile(shopName = "Warung Kopi Jaya", address = "Pasar Tradisional"),
            cashierName = "Budi",
            items = listOf(
                ReceiptItem(name = "Kopi Hitam", quantity = 2.0, unit = "gelas", price = 3000L, subtotal = 6000L)
            ),
            paymentInfo = ReceiptPaymentInfo(method = "CASH", totalAmount = 6000L, payAmount = 10000L, changeAmount = 4000L)
        )

        val bytes = escPosFormatter.format(receiptData, ReceiptPaperWidth.WIDTH_58MM)
        assertTrue(bytes.isNotEmpty())

        // Check ESC @ init (0x1B, 0x40)
        assertEquals(EscPosCommands.ESC, bytes[0])
        assertEquals(0x40.toByte(), bytes[1])

        // Check paper cut at the end
        val lastCutIdx = bytes.size - 2
        assertEquals(EscPosCommands.GS, bytes[lastCutIdx - 1])
        assertEquals(0x56.toByte(), bytes[lastCutIdx])
    }

    @Test
    fun testEscPosFormatter_80mm_generatesValidEscPosBytes() {
        val receiptData = ReceiptData(
            receiptNumber = "TRX-2001",
            transactionUuid = "UUID-2001",
            dateTimeMillis = 1773295200000L,
            shopProfile = ShopProfile(shopName = "Super Mart 80mm", address = "Jl. Pemuda No. 45"),
            cashierName = "Siti",
            items = listOf(
                ReceiptItem(name = "Beras Premium 5kg", quantity = 1.0, unit = "sak", price = 65000L, subtotal = 65000L)
            ),
            paymentInfo = ReceiptPaymentInfo(method = "CASH", totalAmount = 65000L, payAmount = 100000L, changeAmount = 35000L)
        )

        val bytes = escPosFormatter.format(receiptData, ReceiptPaperWidth.WIDTH_80MM)
        assertTrue(bytes.isNotEmpty())
        assertTrue(bytes.size > 50)
    }

    @Test
    fun testPlainTextFormatter_58mm_matchesExpectedColumns() {
        val receiptData = ReceiptData(
            receiptNumber = "TRX-3001",
            transactionUuid = "UUID-3001",
            dateTimeMillis = 1773295200000L,
            shopProfile = ShopProfile(shopName = "Warung 58mm"),
            cashierName = "Andi",
            items = listOf(
                ReceiptItem(name = "Gula Pasir 1kg", quantity = 2.0, unit = "kg", price = 15000L, subtotal = 30000L)
            ),
            paymentInfo = ReceiptPaymentInfo(method = "CASH", totalAmount = 30000L, payAmount = 50000L, changeAmount = 20000L)
        )

        val text = plainTextFormatter.format(receiptData, ReceiptPaperWidth.WIDTH_58MM)
        val lines = text.lines().filter { it.isNotEmpty() }

        // Assert all formatted lines do not exceed 32 columns
        lines.forEach { line ->
            assertTrue("Line exceeds 32 chars: '$line' (length: ${line.length})", line.length <= 32)
        }
        assertTrue(text.contains("Warung 58mm"))
        assertTrue(text.contains("Gula Pasir 1kg"))
        assertTrue(text.contains("TOTAL"))
    }

    @Test
    fun testPlainTextFormatter_80mm_matchesExpectedColumns() {
        val receiptData = ReceiptData(
            receiptNumber = "TRX-4001",
            transactionUuid = "UUID-4001",
            dateTimeMillis = 1773295200000L,
            shopProfile = ShopProfile(shopName = "Warung 80mm"),
            cashierName = "Andi",
            items = listOf(
                ReceiptItem(name = "Minyak Goreng Sawit 2L", quantity = 3.0, unit = "pouch", price = 32000L, subtotal = 96000L)
            ),
            paymentInfo = ReceiptPaymentInfo(method = "CASH", totalAmount = 96000L, payAmount = 100000L, changeAmount = 4000L)
        )

        val text = plainTextFormatter.format(receiptData, ReceiptPaperWidth.WIDTH_80MM)
        val lines = text.lines().filter { it.isNotEmpty() }

        lines.forEach { line ->
            assertTrue("Line exceeds 48 chars: '$line' (length: ${line.length})", line.length <= 48)
        }
        assertTrue(text.contains("Warung 80mm"))
        assertTrue(text.contains("Minyak Goreng Sawit 2L"))
    }

    @Test
    fun testLongProductName_wrapsWithoutOverflowOrAlignmentBreak() {
        val longName = "Kecap Manis Cap Bango Botol Sedang Kemasan Ekonomis 550ml Ekstra Gurih"
        val wrappedLines = ReceiptTextFormatterUtils.wrapText(longName, 32)

        assertTrue(wrappedLines.size > 1)
        wrappedLines.forEach { line ->
            assertTrue(line.length <= 32)
        }

        val receiptData = ReceiptData(
            receiptNumber = "TRX-LONG",
            transactionUuid = "UUID-LONG",
            dateTimeMillis = 1773295200000L,
            shopProfile = ShopProfile(shopName = "Toko Lengkap"),
            cashierName = "Kasir",
            items = listOf(
                ReceiptItem(name = longName, quantity = 1.0, unit = "btl", price = 25000L, subtotal = 25000L)
            ),
            paymentInfo = ReceiptPaymentInfo(method = "CASH", totalAmount = 25000L, payAmount = 50000L, changeAmount = 25000L)
        )

        val preview = plainTextFormatter.format(receiptData, ReceiptPaperWidth.WIDTH_58MM)
        val lines = preview.lines().filter { it.isNotEmpty() }
        lines.forEach { line ->
            assertTrue("Wrapped receipt line exceeds 32 chars: '$line'", line.length <= 32)
        }
    }

    @Test
    fun testCashReceipt_containsGivenAndChangeAmount() {
        val receiptData = ReceiptData(
            receiptNumber = "TRX-CASH",
            transactionUuid = "UUID-CASH",
            dateTimeMillis = 1773295200000L,
            shopProfile = ShopProfile(shopName = "Warung Tunai"),
            cashierName = "Budi",
            items = listOf(
                ReceiptItem(name = "Teh Manis", quantity = 2.0, unit = "gelas", price = 4000L, subtotal = 8000L)
            ),
            paymentInfo = ReceiptPaymentInfo(method = "CASH", totalAmount = 8000L, payAmount = 10000L, changeAmount = 2000L)
        )

        val text = plainTextFormatter.format(receiptData, ReceiptPaperWidth.WIDTH_58MM)
        assertTrue(text.contains("CASH"))
        assertTrue(text.contains("Bayar Tunai"))
        assertTrue(text.contains("Kembali"))
    }

    @Test
    fun testQrisReceipt_containsQrisPaymentMethod() {
        val receiptData = ReceiptData(
            receiptNumber = "TRX-QRIS",
            transactionUuid = "UUID-QRIS",
            dateTimeMillis = 1773295200000L,
            shopProfile = ShopProfile(shopName = "Warung Digital"),
            cashierName = "Budi",
            items = listOf(
                ReceiptItem(name = "Paket Data 10GB", quantity = 1.0, unit = "trx", price = 35000L, subtotal = 35000L)
            ),
            paymentInfo = ReceiptPaymentInfo(method = "QRIS", totalAmount = 35000L)
        )

        val text = plainTextFormatter.format(receiptData, ReceiptPaperWidth.WIDTH_58MM)
        assertTrue(text.contains("QRIS"))
        assertFalse(text.contains("Kembali"))
    }

    @Test
    fun testCreditReceipt_containsCustomerAndDebtInformation() {
        val receiptData = ReceiptData(
            receiptNumber = "TRX-CREDIT",
            transactionUuid = "UUID-CREDIT",
            dateTimeMillis = 1773295200000L,
            shopProfile = ShopProfile(shopName = "Warung Kasbon"),
            cashierName = "Budi",
            items = listOf(
                ReceiptItem(name = "Beras 10kg", quantity = 1.0, unit = "karung", price = 130000L, subtotal = 130000L)
            ),
            paymentInfo = ReceiptPaymentInfo(
                method = "CREDIT",
                totalAmount = 130000L,
                customerName = "Pak Slamet",
                remainingDebt = 130000L
            )
        )

        val text = plainTextFormatter.format(receiptData, ReceiptPaperWidth.WIDTH_58MM)
        assertTrue(text.contains("CREDIT"))
        assertTrue(text.contains("Pak Slamet"))
        assertTrue(text.contains("Sisa Hutang"))
    }

    @Test
    fun testMockPrinterConnection_lifecycleAndByteDelivery() = runBlocking {
        val mockConnection = MockPrinterConnection()
        val printerService = PrinterService(mockConnection)

        assertFalse(printerService.isConnected)

        val connectResult = printerService.connect()
        assertTrue(connectResult.isSuccess)
        assertTrue(printerService.isConnected)

        val receiptData = ReceiptData(
            receiptNumber = "TRX-MOCK",
            transactionUuid = "UUID-MOCK",
            dateTimeMillis = 1773295200000L,
            shopProfile = ShopProfile(shopName = "Warung Mock"),
            cashierName = "Test",
            items = listOf(ReceiptItem(name = "Kopi", quantity = 1.0, unit = "gelas", price = 3000L, subtotal = 3000L)),
            paymentInfo = ReceiptPaymentInfo(method = "CASH", totalAmount = 3000L, payAmount = 5000L, changeAmount = 2000L)
        )

        val printResult = printerService.printReceipt(receiptData)
        assertTrue(printResult.isSuccess)

        assertTrue(mockConnection.receivedByteChunks.isNotEmpty())
        assertTrue(mockConnection.totalBytesReceived > 0)
        assertTrue(mockConnection.fullPrintedText.contains("Warung Mock"))

        printerService.disconnect()
        assertFalse(printerService.isConnected)
    }

    @Test
    fun testPrinterFailure_returnsFailureWithoutCrashing() = runBlocking {
        val mockConnection = MockPrinterConnection()
        mockConnection.simulateErrorOnSend = true

        val printerService = PrinterService(mockConnection)
        printerService.connect()

        val receiptData = ReceiptData(
            receiptNumber = "TRX-FAIL",
            transactionUuid = "UUID-FAIL",
            dateTimeMillis = 1773295200000L,
            shopProfile = ShopProfile(shopName = "Warung Fail"),
            cashierName = "Test",
            items = listOf(ReceiptItem(name = "Teh", quantity = 1.0, unit = "gelas", price = 2000L, subtotal = 2000L)),
            paymentInfo = ReceiptPaymentInfo(method = "CASH", totalAmount = 2000L)
        )

        val printResult = printerService.printReceipt(receiptData)
        assertTrue(printResult.isFailure)
        assertNotNull(printResult.exceptionOrNull())
    }

    @Test
    fun testTransactionCommit_survivesPrinterFailure() = runBlocking {
        // 1. Setup product
        val prodId = productRepository.insertProductWithCategory(
            name = "Rokok Filter",
            categoryName = "Rokok",
            purchasePrice = 20000,
            sellingPrice = 25000,
            stock = 10.0,
            minimumStock = 2.0,
            unit = "bks"
        )
        val product = productRepository.getProductById(prodId)!!

        // 2. Setup failing printer service
        val mockConnection = MockPrinterConnection()
        mockConnection.simulateErrorOnSend = true
        val printerService = PrinterService(mockConnection)

        // 3. Complete sale in database (Commit First!)
        val saleResult = saleRepository.completeSale(
            cartItems = mapOf(prodId to 2.0),
            paymentMethod = "CASH"
        )
        assertTrue(saleResult.isSuccess)
        val saleId = saleResult.getOrThrow()

        // 4. Attempt to print receipt and verify printer failure
        val sale = saleRepository.getTransactionById(saleId)!!
        val items = saleRepository.getItemsForTransaction(saleId)
        val receiptData = ReceiptMapper.mapFromSale(sale, items)
        val printResult = printerService.printReceipt(receiptData)

        assertTrue("Printer failed as expected", printResult.isFailure)

        // 5. CRITICAL INVARIANT: Database transaction, stock, cash MUST remain 100% intact!
        val afterSaleProd = productRepository.getProductById(prodId)!!
        assertEquals(8.0, afterSaleProd.stock, 0.001)

        val movements = stockRepository.getStockMovementList(product.uuid)
        assertEquals(2, movements.size) // INITIAL + SALE

        val cashBalance = cashRepository.totalCashBalance.first()
        assertEquals(50000L, cashBalance)

        val committedSale = saleRepository.getTransactionById(saleId)
        assertNotNull(committedSale)
        assertEquals(50000L, committedSale!!.totalAmount)
    }
}
