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
import id.skmnetwork.bukuwarung.domain.receipt.ReceiptPaperWidth
import id.skmnetwork.bukuwarung.printer.PrinterService
import id.skmnetwork.bukuwarung.printer.connection.MockPrinterConnection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PostFoundationIntegration1Test {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var productRepository: ProductRepository
    private lateinit var stockRepository: StockRepository
    private lateinit var saleRepository: SaleRepository
    private lateinit var cashRepository: CashRepository
    private lateinit var customerRepository: CustomerRepository

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
    fun test1_checkoutCommitsAndPrinterSucceeds() = runBlocking {
        val prodId = productRepository.insertProductWithCategory(
            name = "Minyak Goreng 1L",
            categoryName = "Sembako",
            purchasePrice = 14000,
            sellingPrice = 17000,
            stock = 10.0,
            minimumStock = 2.0,
            unit = "pch"
        )

        val userSettings = UserSettings(
            shopName = "Toko Berkah",
            phone = "081234567890",
            address = "Jl. Merdeka No. 1"
        )

        val mockPrinter = MockPrinterConnection()
        val printerService = PrinterService(activeConnection = mockPrinter)

        // 1. Checkout transaction
        val checkoutResult = productRepository.processAtomicCheckout(
            cartItems = mapOf(prodId to 2.0),
            paymentMethod = "CASH"
        )
        assertTrue(checkoutResult.isSuccess)
        val saleId = checkoutResult.getOrThrow()

        // 2. Build receipt from persisted transaction
        val receiptData = productRepository.getReceiptData(saleId, userSettings, cashGiven = 50000L)
        assertNotNull(receiptData)
        assertEquals("Toko Berkah", receiptData!!.shopProfile.shopName)
        assertEquals(34000L, receiptData.paymentInfo.totalAmount)
        assertEquals(50000L, receiptData.paymentInfo.payAmount)
        assertEquals(16000L, receiptData.paymentInfo.changeAmount)

        // 3. Print receipt
        val printResult = printerService.printReceipt(receiptData)
        assertTrue(printResult.isSuccess)
        assertEquals(1, mockPrinter.receivedByteChunks.size)
        assertTrue(mockPrinter.isConnected)
    }

    @Test
    fun test2_checkoutCommitsAndPrinterFails() = runBlocking {
        val prodId = productRepository.insertProductWithCategory(
            name = "Beras Rojolele 5kg",
            categoryName = "Sembako",
            purchasePrice = 60000,
            sellingPrice = 72000,
            stock = 5.0,
            minimumStock = 1.0,
            unit = "sak"
        )

        val mockPrinter = MockPrinterConnection()
        mockPrinter.simulateErrorOnSend = true
        val printerService = PrinterService(activeConnection = mockPrinter)

        // 1. Checkout
        val checkoutResult = productRepository.processAtomicCheckout(
            cartItems = mapOf(prodId to 1.0),
            paymentMethod = "CASH"
        )
        assertTrue("Checkout must succeed", checkoutResult.isSuccess)
        val saleId = checkoutResult.getOrThrow()

        // 2. Receipt construction
        val receiptData = productRepository.getReceiptData(saleId, UserSettings(), cashGiven = 100000L)
        assertNotNull(receiptData)

        // 3. Print attempt fails
        val printResult = printerService.printReceipt(receiptData!!)
        assertTrue("Printer must fail gracefully", printResult.isFailure)

        // 4. Verify DB transaction remains fully intact
        val sale = saleRepository.getTransactionById(saleId)
        assertNotNull("Sale transaction must remain persisted", sale)
        val stock = database.productDao().getProductById(prodId)!!.stock
        assertEquals(4.0, stock, 0.001)
    }

    @Test
    fun test3_checkoutCommitsWithNoPrinterConfigured() = runBlocking {
        val prodId = productRepository.insertProductWithCategory(
            name = "Gula Pasir 1kg",
            categoryName = "Sembako",
            purchasePrice = 12000,
            sellingPrice = 15000,
            stock = 20.0,
            minimumStock = 5.0,
            unit = "kg"
        )

        val printerService = PrinterService(activeConnection = null)

        val checkoutResult = productRepository.processAtomicCheckout(
            cartItems = mapOf(prodId to 3.0),
            paymentMethod = "QRIS"
        )
        assertTrue(checkoutResult.isSuccess)
        val saleId = checkoutResult.getOrThrow()

        val receiptData = productRepository.getReceiptData(saleId, UserSettings())
        assertNotNull(receiptData)

        val printResult = printerService.printReceipt(receiptData!!)
        assertTrue("Print without configured connection must return failure", printResult.isFailure)
        assertEquals("Printer belum dikonfigurasi", printResult.exceptionOrNull()?.message)

        // DB remains persisted
        val sale = saleRepository.getTransactionById(saleId)
        assertNotNull(sale)
        assertEquals(45000L, sale!!.totalAmount)
    }

    @Test
    fun test4_printerFailureDoesNotRollbackSale() = runBlocking {
        val prodId = productRepository.insertProductWithCategory(
            name = "Kecap Manis 550ml",
            categoryName = "Bumbu",
            purchasePrice = 18000,
            sellingPrice = 22000,
            stock = 10.0,
            minimumStock = 2.0,
            unit = "btl"
        )

        val mockPrinter = MockPrinterConnection()
        mockPrinter.simulateErrorOnConnect = true
        val printerService = PrinterService(activeConnection = mockPrinter)

        val checkoutResult = productRepository.processAtomicCheckout(
            cartItems = mapOf(prodId to 2.0),
            paymentMethod = "CASH"
        )
        assertTrue(checkoutResult.isSuccess)
        val saleId = checkoutResult.getOrThrow()

        val receiptData = productRepository.getReceiptData(saleId, UserSettings(), cashGiven = 50000L)
        val printResult = printerService.printReceipt(receiptData!!)
        assertTrue(printResult.isFailure)

        // Verify Sale & Sale Items are intact
        val sale = saleRepository.getTransactionById(saleId)
        assertNotNull(sale)
        assertEquals(44000L, sale!!.totalAmount)

        val items = saleRepository.getItemsForTransaction(saleId)
        assertEquals(1, items.size)
        assertEquals("Kecap Manis 550ml", items[0].productName)
        assertEquals(2.0, items[0].quantity, 0.001)
    }

    @Test
    fun test5_printerFailureDoesNotRollbackStock() = runBlocking {
        val prodId = productRepository.insertProductWithCategory(
            name = "Teh Celup Kotak",
            categoryName = "Minuman",
            purchasePrice = 5000,
            sellingPrice = 7000,
            stock = 15.0,
            minimumStock = 3.0,
            unit = "ktk"
        )

        val mockPrinter = MockPrinterConnection()
        mockPrinter.simulateErrorOnSend = true
        val printerService = PrinterService(activeConnection = mockPrinter)

        val checkoutResult = productRepository.processAtomicCheckout(
            cartItems = mapOf(prodId to 5.0),
            paymentMethod = "CASH"
        )
        val saleId = checkoutResult.getOrThrow()

        val receiptData = productRepository.getReceiptData(saleId, UserSettings())
        val printResult = printerService.printReceipt(receiptData!!)
        assertTrue(printResult.isFailure)

        // Verify Product Stock
        val product = database.productDao().getProductById(prodId)!!
        assertEquals(10.0, product.stock, 0.001)

        // Verify Stock Ledger Movement
        val movements = stockRepository.getStockMovementList(product.uuid)
        assertEquals(2, movements.size) // INITIAL + SALE
        val saleMovement = movements.find { it.movementType == "SALE" }
        assertNotNull(saleMovement)
        assertEquals(-5.0, saleMovement!!.deltaQuantity, 0.001)
        assertEquals(10.0, saleMovement.currentStockSnapshot, 0.001)
    }

    @Test
    fun test6_printerFailureDoesNotRollbackCash() = runBlocking {
        val prodId = productRepository.insertProductWithCategory(
            name = "Kopi Bubuk 250g",
            categoryName = "Minuman",
            purchasePrice = 10000,
            sellingPrice = 13000,
            stock = 10.0,
            minimumStock = 2.0,
            unit = "bks"
        )

        val mockPrinter = MockPrinterConnection()
        mockPrinter.simulateErrorOnConnect = true
        val printerService = PrinterService(activeConnection = mockPrinter)

        val checkoutResult = productRepository.processAtomicCheckout(
            cartItems = mapOf(prodId to 3.0),
            paymentMethod = "CASH"
        )
        val saleId = checkoutResult.getOrThrow()

        val receiptData = productRepository.getReceiptData(saleId, UserSettings(), cashGiven = 40000L)
        val printResult = printerService.printReceipt(receiptData!!)
        assertTrue(printResult.isFailure)

        // Verify Cash Transaction
        val cashTxs = database.cashDao().getAllCashTransactions().first()
        val saleCashTx = cashTxs.find { it.refId == saleId }
        assertNotNull("Cash transaction for sale must exist", saleCashTx)
        assertEquals(39000L, saleCashTx!!.amount)
        assertEquals("INCOME", saleCashTx.type)
    }

    @Test
    fun test7_printerFailureDoesNotRollbackDebt() = runBlocking {
        val prodId = productRepository.insertProductWithCategory(
            name = "Sabun Mandi Batang",
            categoryName = "Perlengkapan",
            purchasePrice = 3000,
            sellingPrice = 4500,
            stock = 20.0,
            minimumStock = 5.0,
            unit = "pcs"
        )

        val custId = customerRepository.saveCustomer(
            name = "Pak Bambang",
            phone = "081987654321",
            address = "Gang Melati No. 4"
        )

        val mockPrinter = MockPrinterConnection()
        mockPrinter.simulateErrorOnSend = true
        val printerService = PrinterService(activeConnection = mockPrinter)

        val checkoutResult = customerRepository.processAtomicCreditCheckout(
            cartItems = mapOf(prodId to 4.0),
            customerId = custId
        )
        assertTrue(checkoutResult.isSuccess)
        val saleId = checkoutResult.getOrThrow()

        val receiptData = customerRepository.getReceiptData(saleId, UserSettings())
        assertNotNull(receiptData)
        assertEquals("CREDIT", receiptData!!.paymentInfo.method)
        assertEquals("Pak Bambang", receiptData.paymentInfo.customerName)
        assertEquals(18000L, receiptData.paymentInfo.remainingDebt)

        val printResult = printerService.printReceipt(receiptData)
        assertTrue(printResult.isFailure)

        // Verify Debt entity in Room
        val openDebts = customerRepository.getDebtsForCustomer(custId).first()
        assertEquals(1, openDebts.size)
        assertEquals(18000L, openDebts[0].totalDebt)
        assertEquals(0L, openDebts[0].paidAmount)
        assertEquals("OPEN", openDebts[0].status)
    }

    @Test
    fun test8_receiptDataMapsFromPersistedTransaction() = runBlocking {
        val prodId = productRepository.insertProductWithCategory(
            name = "Susu UHT 200ml",
            categoryName = "Minuman",
            purchasePrice = 4000,
            sellingPrice = 6000,
            stock = 30.0,
            minimumStock = 5.0,
            unit = "ktk"
        )

        val userSettings = UserSettings(
            shopName = "Warung Madura 24 Jam",
            ownerName = "H. Mahmud",
            phone = "081122334455",
            address = "Jl. Kemang Raya No. 99",
            receiptFooterText = "Terima kasih atas kunjungannya!"
        )

        val checkoutResult = productRepository.processAtomicCheckout(
            cartItems = mapOf(prodId to 2.0),
            paymentMethod = "CASH"
        )
        val saleId = checkoutResult.getOrThrow()

        val receipt = productRepository.getReceiptData(saleId, userSettings, cashGiven = 20000L)
        assertNotNull(receipt)
        assertEquals("Warung Madura 24 Jam", receipt!!.shopProfile.shopName)
        assertEquals("081122334455", receipt.shopProfile.phone)
        assertEquals("Jl. Kemang Raya No. 99", receipt.shopProfile.address)
        assertEquals("Terima kasih atas kunjungannya!", receipt.shopProfile.footerMessage)
        assertEquals(1, receipt.items.size)
        assertEquals("Susu UHT 200ml", receipt.items[0].name)
        assertEquals(2.0, receipt.items[0].quantity, 0.001)
        assertEquals(6000L, receipt.items[0].price)
        assertEquals(12000L, receipt.items[0].subtotal)
        assertEquals("CASH", receipt.paymentInfo.method)
        assertEquals(12000L, receipt.paymentInfo.totalAmount)
        assertEquals(20000L, receipt.paymentInfo.payAmount)
        assertEquals(8000L, receipt.paymentInfo.changeAmount)
    }

    @Test
    fun test9_58mmReceiptFormattingWorks() = runBlocking {
        val prodId = productRepository.insertProductWithCategory(
            name = "Roti Tawar Serbaguna",
            categoryName = "Makanan",
            purchasePrice = 12000,
            sellingPrice = 16000,
            stock = 10.0,
            minimumStock = 2.0,
            unit = "bks"
        )

        val checkoutResult = productRepository.processAtomicCheckout(
            cartItems = mapOf(prodId to 1.0),
            paymentMethod = "CASH"
        )
        val saleId = checkoutResult.getOrThrow()

        val receipt = productRepository.getReceiptData(saleId, UserSettings(), cashGiven = 20000L)!!
        val mockPrinter = MockPrinterConnection()
        val printerService = PrinterService(
            activeConnection = mockPrinter,
            paperWidth = ReceiptPaperWidth.WIDTH_58MM
        )

        val printResult = printerService.printReceipt(receipt, ReceiptPaperWidth.WIDTH_58MM)
        assertTrue(printResult.isSuccess)

        val printedJobs = mockPrinter.receivedByteChunks
        assertEquals(1, printedJobs.size)
        assertTrue("ESC/POS byte payload must not be empty", printedJobs[0].isNotEmpty())

        val preview = printerService.formatPreview(receipt, ReceiptPaperWidth.WIDTH_58MM)
        assertTrue(preview.contains("Roti Tawar Serbaguna"))
        assertTrue(preview.contains("16.000"))
    }

    @Test
    fun test10_80mmReceiptFormattingWorks() = runBlocking {
        val prodId = productRepository.insertProductWithCategory(
            name = "Beras Pandan Wangi 10kg",
            categoryName = "Sembako",
            purchasePrice = 140000,
            sellingPrice = 165000,
            stock = 8.0,
            minimumStock = 2.0,
            unit = "sak"
        )

        val checkoutResult = productRepository.processAtomicCheckout(
            cartItems = mapOf(prodId to 1.0),
            paymentMethod = "CASH"
        )
        val saleId = checkoutResult.getOrThrow()

        val receipt = productRepository.getReceiptData(saleId, UserSettings(), cashGiven = 200000L)!!
        val mockPrinter = MockPrinterConnection()
        val printerService = PrinterService(
            activeConnection = mockPrinter,
            paperWidth = ReceiptPaperWidth.WIDTH_80MM
        )

        val printResult = printerService.printReceipt(receipt, ReceiptPaperWidth.WIDTH_80MM)
        assertTrue(printResult.isSuccess)

        val printedJobs = mockPrinter.receivedByteChunks
        assertEquals(1, printedJobs.size)
        assertTrue(printedJobs[0].isNotEmpty())

        val preview = printerService.formatPreview(receipt, ReceiptPaperWidth.WIDTH_80MM)
        assertTrue(preview.contains("Beras Pandan Wangi 10kg"))
        assertTrue(preview.contains("165.000"))
    }

    @Test
    fun test11_retryPrintUsesPersistedTransaction() = runBlocking {
        val prodId = productRepository.insertProductWithCategory(
            name = "Snack Cokelat Crispy",
            categoryName = "Snack",
            purchasePrice = 2000,
            sellingPrice = 3000,
            stock = 50.0,
            minimumStock = 10.0,
            unit = "pcs"
        )

        val mockPrinter = MockPrinterConnection()
        mockPrinter.simulateErrorOnSend = true
        val printerService = PrinterService(activeConnection = mockPrinter)

        val checkoutResult = productRepository.processAtomicCheckout(
            cartItems = mapOf(prodId to 3.0),
            paymentMethod = "CASH"
        )
        val saleId = checkoutResult.getOrThrow()

        val receiptData = productRepository.getReceiptData(saleId, UserSettings(), cashGiven = 10000L)!!

        // 1. Initial print fails
        val firstPrintResult = printerService.printReceipt(receiptData)
        assertTrue(firstPrintResult.isFailure)
        assertEquals(0, mockPrinter.receivedByteChunks.size)

        // 2. Fix / Reconnect printer
        mockPrinter.simulateErrorOnSend = false

        // 3. Retry printing with same persisted receiptData
        val retryPrintResult = printerService.printReceipt(receiptData)
        assertTrue("Retry print must succeed", retryPrintResult.isSuccess)
        assertEquals(1, mockPrinter.receivedByteChunks.size)
    }

    @Test
    fun test12_syncQueueRecordedIndependentlyOfPrinter() = runBlocking {
        val prodId = productRepository.insertProductWithCategory(
            name = "Biskuit Gandum",
            categoryName = "Makanan",
            purchasePrice = 8000,
            sellingPrice = 11000,
            stock = 25.0,
            minimumStock = 5.0,
            unit = "bks"
        )

        val mockPrinter = MockPrinterConnection()
        mockPrinter.simulateErrorOnConnect = true
        val printerService = PrinterService(activeConnection = mockPrinter)

        val checkoutResult = productRepository.processAtomicCheckout(
            cartItems = mapOf(prodId to 2.0),
            paymentMethod = "CASH"
        )
        val saleId = checkoutResult.getOrThrow()

        val receiptData = productRepository.getReceiptData(saleId, UserSettings())!!
        val printResult = printerService.printReceipt(receiptData)
        assertTrue(printResult.isFailure)

        // Verify SyncQueueEntity is persisted for the sale transaction
        val sale = saleRepository.getTransactionById(saleId)!!
        val syncItems = database.syncQueueDao().getAllItems().first()
        val saleSync = syncItems.find { it.entityUuid == sale.uuid && it.entityType == "SALE" }
        assertNotNull("SyncQueue record must be committed for SALE", saleSync)
        assertEquals("PENDING", saleSync!!.status)
    }
}
