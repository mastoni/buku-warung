package id.skmnetwork.bukuwarung

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.backup.BackupRestoreManager
import id.skmnetwork.bukuwarung.backup.CanonicalSerializer
import id.skmnetwork.bukuwarung.backup.MockSheetsTransport
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.ItemType
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import id.skmnetwork.bukuwarung.data.preferences.UserSettings
import id.skmnetwork.bukuwarung.data.repository.CashRepository
import id.skmnetwork.bukuwarung.data.repository.CustomerRepository
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import id.skmnetwork.bukuwarung.data.repository.SaleRepository
import id.skmnetwork.bukuwarung.data.repository.StockRepository
import id.skmnetwork.bukuwarung.data.repository.SupplierRepository
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
class EndToEndReleaseReadinessTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var stockRepository: StockRepository
    private lateinit var saleRepository: SaleRepository
    private lateinit var cashRepository: CashRepository
    private lateinit var customerRepository: CustomerRepository
    private lateinit var supplierRepository: SupplierRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        userPreferencesRepository = UserPreferencesRepository(context)
        productRepository = ProductRepository(database, "LEGACY_BUSINESS")
        stockRepository = StockRepository(database, "LEGACY_BUSINESS")
        saleRepository = SaleRepository(database, "LEGACY_BUSINESS")
        cashRepository = CashRepository(database, "LEGACY_BUSINESS")
        customerRepository = CustomerRepository(database, "LEGACY_BUSINESS")
        supplierRepository = SupplierRepository(database, "LEGACY_BUSINESS")
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testCompleteWarungBusinessLifecycleAndInvariants() = runBlocking {
        // =========================================================================
        // 1. SETUP: Business Profile & User Settings
        // =========================================================================
        val userSettings = UserSettings(
            businessId = "BIZ_WARUNG_BERKAH_01",
            deviceId = "DEV_POS_MAIN_01",
            deviceName = "Kasir Utama",
            shopName = "Warung Berkah Jaya",
            ownerName = "Pak H. Ahmad",
            phone = "081234567890",
            address = "Jl. Raya Pasar No. 123",
            receiptFooterText = "Terima kasih sudah belanja!"
        )

        // =========================================================================
        // 2. MASTER DATA: Categories, Physical Products, Service Products, Customers, Suppliers
        // =========================================================================
        val catSembakoId = database.categoryDao().insertCategory(
            id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity(name = "Sembako", businessId = userSettings.businessId)
        )
        val catJasaId = database.categoryDao().insertCategory(
            id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity(name = "Jasa & Layanan", businessId = userSettings.businessId)
        )

        // Physical Product (Initial Stock = 10.0)
        val prodBerasId = productRepository.insertProductWithCategory(
            name = "Beras Rojolele 5kg",
            categoryName = "Sembako",
            purchasePrice = 60000L,
            sellingPrice = 75000L,
            stock = 10.0,
            minimumStock = 2.0,
            unit = "sak",
            itemType = ItemType.PHYSICAL
        )

        // Physical Product (Initial Stock = 20.0)
        val prodMinyakId = productRepository.insertProductWithCategory(
            name = "Minyak Goreng 1L",
            categoryName = "Sembako",
            purchasePrice = 14000L,
            sellingPrice = 17500L,
            stock = 20.0,
            minimumStock = 5.0,
            unit = "pch",
            itemType = ItemType.PHYSICAL
        )

        // Service Product (Stock = 0.0, Non-tracked)
        val prodJasaAntarId = productRepository.insertProductWithCategory(
            name = "Ongkos Kirim / Jasa Antar",
            categoryName = "Jasa & Layanan",
            purchasePrice = 0L,
            sellingPrice = 10000L,
            stock = 0.0,
            minimumStock = 0.0,
            unit = "trip",
            itemType = ItemType.SERVICE
        )

        // Customer
        val custId = customerRepository.saveCustomer(
            name = "Ibu Siti Nurhaliza",
            phone = "081987654321",
            address = "Komplek Permai Blok B2"
        )

        // Supplier
        val suppId = supplierRepository.saveSupplier(
            name = "Grosir Sembako Makmur",
            phone = "081333444555",
            address = "Pasar Induk Kramat Jati"
        )

        // Verify initial master data
        val beras = database.productDao().getProductById(prodBerasId, "LEGACY_BUSINESS")!!
        val minyak = database.productDao().getProductById(prodMinyakId, "LEGACY_BUSINESS")!!
        val jasa = database.productDao().getProductById(prodJasaAntarId, "LEGACY_BUSINESS")!!

        assertEquals(10.0, beras.stock, 0.001)
        assertEquals(20.0, minyak.stock, 0.001)
        assertEquals(0.0, jasa.stock, 0.001)

        // =========================================================================
        // 3. PURCHASES: Cash Purchase & Credit Purchase
        // =========================================================================
        // A) Cash Purchase: 5 sak Beras @ 60.000 = 300.000 (Increases Beras stock from 10 -> 15)
        val cashPurchaseResult = productRepository.processAtomicPurchase(
            purchaseItems = mapOf(prodBerasId to 5.0)
        )
        assertTrue("Cash purchase must succeed", cashPurchaseResult.isSuccess)

        val berasAfterPurch = database.productDao().getProductById(prodBerasId, "LEGACY_BUSINESS")!!
        assertEquals(15.0, berasAfterPurch.stock, 0.001)

        // B) Credit Purchase: 10 pch Minyak @ 14.000 = 140.000 (Increases Minyak stock from 20 -> 30)
        val creditPurchaseResult = supplierRepository.processAtomicCreditPurchase(
            purchaseItems = mapOf(prodMinyakId to 10.0),
            supplierId = suppId
        )
        assertTrue("Credit purchase must succeed", creditPurchaseResult.isSuccess)

        val minyakAfterPurch = database.productDao().getProductById(prodMinyakId, "LEGACY_BUSINESS")!!
        assertEquals(30.0, minyakAfterPurch.stock, 0.001)

        // Verify Supplier Payable created
        val payables = supplierRepository.getPayablesForSupplier(suppId).first()
        assertEquals(1, payables.size)
        assertEquals(140000L, payables[0].totalDebt)
        assertEquals(0L, payables[0].paidAmount)
        assertEquals("OPEN", payables[0].status)

        // =========================================================================
        // 4. SALES: Cash Sale, QRIS Sale, Credit Sale
        // =========================================================================
        // A) Cash Sale: 2 sak Beras @ 75.000 = 150.000 (Stock 15 -> 13)
        val cashSaleResult = productRepository.processAtomicCheckout(
            cartItems = mapOf(prodBerasId to 2.0),
            paymentMethod = "CASH"
        )
        assertTrue(cashSaleResult.isSuccess)
        val cashSaleId = cashSaleResult.getOrThrow()
        assertEquals(13.0, database.productDao().getProductById(prodBerasId, "LEGACY_BUSINESS")!!.stock, 0.001)

        // B) QRIS Sale: 5 pch Minyak @ 17.500 = 87.500 (Stock 30 -> 25)
        val qrisSaleResult = productRepository.processAtomicCheckout(
            cartItems = mapOf(prodMinyakId to 5.0),
            paymentMethod = "QRIS"
        )
        assertTrue(qrisSaleResult.isSuccess)
        assertEquals(25.0, database.productDao().getProductById(prodMinyakId, "LEGACY_BUSINESS")!!.stock, 0.001)

        // C) Credit Sale: 1 sak Beras (75.000) + 2 pch Minyak (35.000) = 110.000
        val creditSaleResult = customerRepository.processAtomicCreditCheckout(
            cartItems = mapOf(prodBerasId to 1.0, prodMinyakId to 2.0),
            customerId = custId
        )
        assertTrue(creditSaleResult.isSuccess)
        val creditSaleId = creditSaleResult.getOrThrow()

        assertEquals(12.0, database.productDao().getProductById(prodBerasId, "LEGACY_BUSINESS")!!.stock, 0.001)
        assertEquals(23.0, database.productDao().getProductById(prodMinyakId, "LEGACY_BUSINESS")!!.stock, 0.001)

        // Verify Customer Debt created
        val custDebts = customerRepository.getDebtsForCustomer(custId).first()
        assertEquals(1, custDebts.size)
        assertEquals(110000L, custDebts[0].totalDebt)
        assertEquals(0L, custDebts[0].paidAmount)
        assertEquals("OPEN", custDebts[0].status)

        // =========================================================================
        // 5. PAYMENTS: Customer Debt Repayment & Supplier Payable Repayment
        // =========================================================================
        // Customer pays 50.000 partial debt
        val debtPayResult = customerRepository.processAtomicDebtPayment(
            debtId = custDebts[0].id,
            amount = 50000L,
            note = "Cicilan pertama"
        )
        assertTrue(debtPayResult.isSuccess)

        val updatedDebt = database.debtDao().getDebtById(custDebts[0].id, "LEGACY_BUSINESS")!!
        assertEquals(50000L, updatedDebt.paidAmount)
        assertEquals(60000L, updatedDebt.totalDebt - updatedDebt.paidAmount)
        assertEquals("OPEN", updatedDebt.status)

        // Warung pays 100.000 partial supplier payable
        val payablePayResult = supplierRepository.processAtomicSupplierPayment(
            payableId = payables[0].id,
            amount = 100000L,
            note = "Pelunasan sebagian"
        )
        assertTrue(payablePayResult.isSuccess)

        val updatedPayable = database.supplierPayableDao().getSupplierPayableById(payables[0].id, "LEGACY_BUSINESS")!!
        assertEquals(100000L, updatedPayable.paidAmount)
        assertEquals(40000L, updatedPayable.totalDebt - updatedPayable.paidAmount)
        assertEquals("OPEN", updatedPayable.status)

        // =========================================================================
        // 6. STOCK ADJUSTMENT: Manual Stock Opname Adjustment
        // =========================================================================
        // Adjust Minyak from 23.0 -> 24.0 (Found 1 extra pouch during stock opname)
        productRepository.updateProductWithCategory(
            productId = prodMinyakId,
            name = minyak.name,
            categoryName = "Sembako",
            purchasePrice = minyak.purchasePrice,
            sellingPrice = minyak.sellingPrice,
            stock = 24.0,
            minimumStock = minyak.minimumStock,
            unit = minyak.unit
        )
        assertEquals(24.0, database.productDao().getProductById(prodMinyakId, "LEGACY_BUSINESS")!!.stock, 0.001)

        // =========================================================================
        // 7. DATA INVARIANTS VERIFICATION
        // =========================================================================
        // Invariant 1: Stock Invariant: SUM(StockMovement.deltaQuantity) == Product.stock
        val allPhysicalProducts = database.productDao().getAllProducts("LEGACY_BUSINESS").first().filter { it.itemType == ItemType.PHYSICAL.name }
        for (prod in allPhysicalProducts) {
            val movements = stockRepository.getStockMovementList(prod.uuid)
            val calculatedSum = movements.sumOf { it.deltaQuantity }
            assertEquals(
                "Stock invariant violated for ${prod.name}: ledger sum=$calculatedSum vs product stock=${prod.stock}",
                prod.stock,
                calculatedSum,
                0.001
            )
        }

        // Invariant 2: Cash Ledger Consistency
        // Cash Movements:
        // - Cash Purchase Beras: -300.000 (EXPENSE)
        // - Cash Sale Beras: +150.000 (INCOME)
        // - Debt Payment Received: +50.000 (INCOME)
        // - Supplier Payment Made: -100.000 (EXPENSE)
        // Total Expected Cash Delta = -300.000 + 150.000 + 50.000 - 100.000 = -200.000
        val allCashTxs = database.cashDao().getAllCashTransactions("LEGACY_BUSINESS").first()
        val totalIncome = allCashTxs.filter { it.type == "INCOME" }.sumOf { it.amount }
        val totalExpense = allCashTxs.filter { it.type == "EXPENSE" }.sumOf { it.amount }
        assertEquals(200000L, totalIncome) // 150.000 sale + 50.000 debt repayment
        assertEquals(400000L, totalExpense) // 300.000 purchase + 100.000 supplier payment

        // Invariant 3: No duplicate UUIDs in any domain table
        val allUuids = mutableSetOf<String>()
        fun assertUnique(uuid: String, table: String) {
            assertTrue("Duplicate UUID '$uuid' in $table", allUuids.add(uuid))
        }
        database.categoryDao().getAllCategories("LEGACY_BUSINESS").first().forEach { assertUnique(it.uuid, "categories") }
        database.productDao().getAllProducts("LEGACY_BUSINESS").first().forEach { assertUnique(it.uuid, "products") }
        database.customerDao().getAllCustomers("LEGACY_BUSINESS").first().forEach { assertUnique(it.uuid, "customers") }
        database.supplierDao().getAllSuppliers("LEGACY_BUSINESS").first().forEach { assertUnique(it.uuid, "suppliers") }
        database.saleDao().getAllTransactions("LEGACY_BUSINESS").first().forEach { assertUnique(it.uuid, "sales_transactions") }
        database.purchaseDao().getAllPurchaseTransactions("LEGACY_BUSINESS").first().forEach { assertUnique(it.uuid, "purchase_transactions") }
        database.debtDao().getAllOpenDebts("LEGACY_BUSINESS").first().forEach { assertUnique(it.uuid, "debts") }
        database.supplierPayableDao().getPayablesForSupplier(suppId, "LEGACY_BUSINESS").first().forEach { assertUnique(it.uuid, "supplier_payables") }

        // =========================================================================
        // 8. RECEIPT & PRINTER ISOLATION
        // =========================================================================
        val receipt = saleRepository.getReceiptData(cashSaleId, userSettings, cashGiven = 200000L)
        assertNotNull(receipt)
        assertEquals("Warung Berkah Jaya", receipt!!.shopProfile.shopName)
        assertEquals(150000L, receipt.paymentInfo.totalAmount)
        assertEquals(50000L, receipt.paymentInfo.changeAmount)

        val failingPrinter = MockPrinterConnection()
        failingPrinter.simulateErrorOnSend = true
        val printerService = PrinterService(activeConnection = failingPrinter)

        val printResult = printerService.printReceipt(receipt)
        assertTrue("Printer failure is isolated", printResult.isFailure)

        // Verify DB not affected by printer failure
        val postPrintSale = saleRepository.getTransactionById(cashSaleId)
        assertNotNull(postPrintSale)
        assertEquals(12.0, database.productDao().getProductById(prodBerasId, "LEGACY_BUSINESS")!!.stock, 0.001)

        // =========================================================================
        // 9. BACKUP & RESTORE INTEGRITY
        // =========================================================================
        val mockTransport = MockSheetsTransport()
        val backupManager = BackupRestoreManager(
            database = database,
            userPreferencesRepository = userPreferencesRepository,
            transport = mockTransport
        )

        val exportSnapshot = backupManager.exportSnapshot()
        mockTransport.writeBackup("test_e2e_backup", exportSnapshot)

        // Verify 19 tabs & Checksum
        assertEquals(19, exportSnapshot.tabs.size)
        assertTrue(CanonicalSerializer.verifyChecksum(exportSnapshot.metadata.checksum, exportSnapshot.tabs))

        // Create a separate clean database and restore snapshot into it
        val restoredDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val restoreManager = BackupRestoreManager(
            database = restoredDb,
            userPreferencesRepository = userPreferencesRepository,
            transport = mockTransport
        )

        val readSnapshot = mockTransport.readBackup("test_e2e_backup").getOrThrow()
        val restoreResult = restoreManager.restoreSnapshot(readSnapshot)
        assertTrue("Restore into clean database must succeed", restoreResult.isSuccess)

        // Verify restored data in new database
        val restoredProducts = restoredDb.productDao().getAllProducts("LEGACY_BUSINESS").first()
        assertEquals(3, restoredProducts.size)
        val restoredBeras = restoredProducts.find { it.name == "Beras Rojolele 5kg" }!!
        assertEquals(12.0, restoredBeras.stock, 0.001)

        val restoredMinyak = restoredProducts.find { it.name == "Minyak Goreng 1L" }!!
        assertEquals(24.0, restoredMinyak.stock, 0.001)

        // Verify stock invariant in restored database
        val restoredMovements = restoredDb.stockMovementDao().getMovementsListForProduct("LEGACY_BUSINESS", restoredBeras.uuid)
        assertEquals(12.0, restoredMovements.sumOf { it.deltaQuantity }, 0.001)

        restoredDb.close()
    }
}




