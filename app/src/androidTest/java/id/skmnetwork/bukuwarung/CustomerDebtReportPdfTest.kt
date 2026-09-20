package id.skmnetwork.bukuwarung

import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.local.entity.ItemType
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.preferences.UserSettings
import id.skmnetwork.bukuwarung.data.repository.CustomerRepository
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import id.skmnetwork.bukuwarung.data.repository.PurchaseRepository
import id.skmnetwork.bukuwarung.data.repository.ReportRepository
import id.skmnetwork.bukuwarung.data.repository.SaleRepository
import id.skmnetwork.bukuwarung.data.repository.SupplierRepository
import id.skmnetwork.bukuwarung.pdf.PdfReportGenerator
import id.skmnetwork.bukuwarung.pdf.PdfShareManager
import id.skmnetwork.bukuwarung.pdf.reports.CustomerDebtReportPdfBuilder
import id.skmnetwork.bukuwarung.pdf.reports.DebtReportMode
import id.skmnetwork.bukuwarung.ui.report.ReportPeriod
import id.skmnetwork.bukuwarung.ui.report.ReportViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CustomerDebtReportPdfTest {

    private lateinit var database: AppDatabase
    private lateinit var customerRepository: CustomerRepository
    private lateinit var saleRepository: SaleRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var purchaseRepository: PurchaseRepository
    private lateinit var supplierRepository: SupplierRepository
    private lateinit var reportRepository: ReportRepository
    private lateinit var reportViewModel: ReportViewModel
    private lateinit var pdfGenerator: PdfReportGenerator
    private lateinit var context: Context

    private var defaultCategoryId: Long = 0L

    private val sampleSettings = UserSettings(
        shopName = "Kios Kiara Piutang Test",
        ownerName = "Kang Toni",
        address = "Losarang, Indramayu",
        phone = "081234567890"
    )

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        customerRepository = CustomerRepository(database, "LEGACY_BUSINESS")
        saleRepository = SaleRepository(database, "LEGACY_BUSINESS")
        productRepository = ProductRepository(database, "LEGACY_BUSINESS")
        purchaseRepository = PurchaseRepository(database, "LEGACY_BUSINESS")
        supplierRepository = SupplierRepository(database, "LEGACY_BUSINESS")
        reportRepository = ReportRepository(database, "LEGACY_BUSINESS")
        reportViewModel = ReportViewModel(reportRepository)
        pdfGenerator = PdfReportGenerator(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createProduct(
        name: String,
        purchasePrice: Long,
        sellingPrice: Long,
        stock: Double
    ): Long = runBlocking {
        productRepository.insertProductWithCategory(
            name = name,
            categoryName = "Kategori Umum",
            purchasePrice = purchasePrice,
            sellingPrice = sellingPrice,
            stock = stock,
            minimumStock = 2.0,
            unit = "pcs",
            itemType = ItemType.PHYSICAL
        )
    }

    // 1. Empty Report (both current outstanding & mutation period)
    @Test
    fun test01_emptyReport_generatesValidPdfWithoutCrashing() = runBlocking {
        val outstandingData = reportViewModel.buildCustomerDebtReportData(
            sampleSettings,
            mode = DebtReportMode.CURRENT_OUTSTANDING
        )
        assertEquals(0, outstandingData.items.size)
        assertEquals(0L, outstandingData.totalOutstanding)
        assertEquals(0L, outstandingData.totalPaid)
        assertEquals(0L, outstandingData.totalDebtCreated)

        val doc1 = CustomerDebtReportPdfBuilder.build(outstandingData)
        val result1 = pdfGenerator.generatePdf(doc1)
        assertTrue(result1.isSuccess)

        val mutationData = reportViewModel.buildCustomerDebtReportData(
            sampleSettings,
            mode = DebtReportMode.MUTATION_PERIOD,
            period = ReportPeriod.TODAY
        )
        val doc2 = CustomerDebtReportPdfBuilder.build(mutationData)
        val result2 = pdfGenerator.generatePdf(doc2)
        assertTrue(result2.isSuccess)
    }

    // 2. Single OPEN credit debt
    @Test
    fun test02_singleOpenDebt_rendersCorrectly() = runBlocking {
        val custId = customerRepository.saveCustomer("Budi Santoso", "081211112222", "Jl. Mawar")
        val prodId = createProduct("Beras 5kg", purchasePrice = 50000L, sellingPrice = 65000L, stock = 10.0)

        customerRepository.processAtomicCreditCheckout(mapOf(prodId to 1.0), custId).getOrThrow()

        val outstandingData = reportViewModel.buildCustomerDebtReportData(
            sampleSettings,
            mode = DebtReportMode.CURRENT_OUTSTANDING
        )

        assertEquals(1, outstandingData.items.size)
        assertEquals(65000L, outstandingData.totalOutstanding)
        assertEquals(0L, outstandingData.totalPaid)
        assertEquals(65000L, outstandingData.totalDebtCreated)
        assertEquals(1, outstandingData.activeDebtorsCount)

        val row = outstandingData.items[0]
        assertEquals("Budi Santoso", row.customerName)
        assertEquals("081211112222", row.customerPhone)
        assertEquals(65000L, row.totalDebt)
        assertEquals(0L, row.paidAmount)
        assertEquals(65000L, row.remainingDebt)
        assertEquals("Belum Lunas", row.status)
    }

    // 3. Single PAID debt (excluded from current outstanding, included in mutation)
    @Test
    fun test03_singlePaidDebt_rendersInMutationAndExcludedFromOutstanding() = runBlocking {
        val custId = customerRepository.saveCustomer("Siti Rahma", "081322223333", "Jl. Melati")
        val prodId = createProduct("Minyak 2L", purchasePrice = 28000L, sellingPrice = 34000L, stock = 10.0)

        customerRepository.processAtomicCreditCheckout(mapOf(prodId to 1.0), custId).getOrThrow()
        val debts = database.debtDao().getOpenDebtsForCustomerList(custId, "LEGACY_BUSINESS")
        val debtId = debts.first().id

        // Pay full amount
        customerRepository.processAtomicDebtPayment(debtId, 34000L, "Pelunasan penuh").getOrThrow()

        // Outstanding report: should be empty (0 open debt)
        val outstandingData = reportViewModel.buildCustomerDebtReportData(
            sampleSettings,
            mode = DebtReportMode.CURRENT_OUTSTANDING
        )
        assertEquals(0, outstandingData.items.size)
        assertEquals(0L, outstandingData.totalOutstanding)

        // Mutation report: should contain the paid transaction
        val mutationData = reportViewModel.buildCustomerDebtReportData(
            sampleSettings,
            mode = DebtReportMode.MUTATION_PERIOD,
            period = ReportPeriod.TODAY
        )
        assertEquals(1, mutationData.items.size)
        val row = mutationData.items[0]
        assertEquals(34000L, row.totalDebt)
        assertEquals(34000L, row.paidAmount)
        assertEquals(0L, row.remainingDebt)
        assertEquals("Lunas", row.status)
    }

    // 4. Partial Payment
    @Test
    fun test04_partialPayment_calculatesRemainingDebtCorrectly() = runBlocking {
        val custId = customerRepository.saveCustomer("Pak Ahmad", "0819999", "Blok C")
        val prodId = createProduct("Gula Pasir 1kg", purchasePrice = 14000L, sellingPrice = 17500L, stock = 10.0)

        // Credit sale: 2 pcs @ 17,500 = 35,000
        customerRepository.processAtomicCreditCheckout(mapOf(prodId to 2.0), custId).getOrThrow()
        val debtId = database.debtDao().getOpenDebtsForCustomerList(custId, "LEGACY_BUSINESS").first().id

        // Partial payment of 20,000 -> remaining 15,000
        customerRepository.processAtomicDebtPayment(debtId, 20000L, "Bayar separuh").getOrThrow()

        val outstandingData = reportViewModel.buildCustomerDebtReportData(
            sampleSettings,
            mode = DebtReportMode.CURRENT_OUTSTANDING
        )

        assertEquals(1, outstandingData.items.size)
        assertEquals(15000L, outstandingData.totalOutstanding)
        assertEquals(20000L, outstandingData.totalPaid)
        assertEquals(35000L, outstandingData.totalDebtCreated)

        val row = outstandingData.items[0]
        assertEquals(35000L, row.totalDebt)
        assertEquals(20000L, row.paidAmount)
        assertEquals(15000L, row.remainingDebt)
        assertEquals("Belum Lunas", row.status)
    }

    // 5. Multiple customers with debts
    @Test
    fun test05_multipleCustomers_rendersAllRowsAndAggregates() = runBlocking {
        val cust1 = customerRepository.saveCustomer("Customer Alpha", "08111", "Alamat 1")
        val cust2 = customerRepository.saveCustomer("Customer Beta", "08222", "Alamat 2")
        val prodId = createProduct("Item Test", purchasePrice = 10000L, sellingPrice = 15000L, stock = 100.0)

        customerRepository.processAtomicCreditCheckout(mapOf(prodId to 2.0), cust1).getOrThrow() // 30,000
        customerRepository.processAtomicCreditCheckout(mapOf(prodId to 3.0), cust2).getOrThrow() // 45,000

        val outstandingData = reportViewModel.buildCustomerDebtReportData(
            sampleSettings,
            mode = DebtReportMode.CURRENT_OUTSTANDING
        )

        assertEquals(2, outstandingData.items.size)
        assertEquals(75000L, outstandingData.totalOutstanding)
        assertEquals(2, outstandingData.activeDebtorsCount)
    }

    // 6. CASH sale excluded
    @Test
    fun test06_cashSale_excludedFromDebtReport() = runBlocking {
        val custId = customerRepository.saveCustomer("Cash Buyer", "08123", "Alamat")
        val prodId = createProduct("Kopi", purchasePrice = 2000L, sellingPrice = 3000L, stock = 10.0)

        // CASH sale with customer attached
        saleRepository.completeSale(cartItems = mapOf(prodId to 2.0), paymentMethod = "CASH", customerId = custId).getOrThrow()

        val outstandingData = reportViewModel.buildCustomerDebtReportData(
            sampleSettings,
            mode = DebtReportMode.CURRENT_OUTSTANDING
        )
        assertEquals(0, outstandingData.items.size)
        assertEquals(0L, outstandingData.totalOutstanding)
    }

    // 7. QRIS sale excluded
    @Test
    fun test07_qrisSale_excludedFromDebtReport() = runBlocking {
        val custId = customerRepository.saveCustomer("QRIS Buyer", "08124", "Alamat")
        val prodId = createProduct("Teh", purchasePrice = 2000L, sellingPrice = 3000L, stock = 10.0)

        saleRepository.completeSale(cartItems = mapOf(prodId to 2.0), paymentMethod = "QRIS", customerId = custId).getOrThrow()

        val outstandingData = reportViewModel.buildCustomerDebtReportData(
            sampleSettings,
            mode = DebtReportMode.CURRENT_OUTSTANDING
        )
        assertEquals(0, outstandingData.items.size)
        assertEquals(0L, outstandingData.totalOutstanding)
    }

    // 8. Supplier Payable excluded
    @Test
    fun test08_supplierPayable_excludedFromCustomerDebtReport() = runBlocking {
        val suppId = supplierRepository.saveSupplier("Distributor Utama", "08555", "Jl Gudang")
        val prodId = createProduct("Mie Instan", purchasePrice = 2500L, sellingPrice = 3500L, stock = 50.0)

        supplierRepository.processAtomicCreditPurchase(mapOf(prodId to 10.0), suppId).getOrThrow() // 25,000 payable

        val outstandingData = reportViewModel.buildCustomerDebtReportData(
            sampleSettings,
            mode = DebtReportMode.CURRENT_OUTSTANDING
        )
        assertEquals(0, outstandingData.items.size)
        assertEquals(0L, outstandingData.totalOutstanding)
    }

    // 9. Supplier Payment excluded
    @Test
    fun test09_supplierPayment_excludedFromCustomerDebtReport() = runBlocking {
        val suppId = supplierRepository.saveSupplier("Agen Telur", "08777", "Jl Telur")
        val prodId = createProduct("Telur 1kg", purchasePrice = 24000L, sellingPrice = 28000L, stock = 50.0)

        supplierRepository.processAtomicCreditPurchase(mapOf(prodId to 5.0), suppId).getOrThrow()
        val payableId = database.supplierPayableDao().getOpenPayablesForSupplierList(suppId, "LEGACY_BUSINESS").first().id
        supplierRepository.processAtomicSupplierPayment(payableId, 50000L, "Bayar hutang supplier").getOrThrow()

        val outstandingData = reportViewModel.buildCustomerDebtReportData(
            sampleSettings,
            mode = DebtReportMode.CURRENT_OUTSTANDING
        )
        assertEquals(0, outstandingData.items.size)
    }

    // 10. Customer details (name and phone) displayed accurately
    @Test
    fun test10_customerDetailsDisplayedCorrectly() = runBlocking {
        val custId = customerRepository.saveCustomer("Haji Dulah", "081298765432", "Losarang")
        val prodId = createProduct("Rokok", purchasePrice = 25000L, sellingPrice = 28000L, stock = 10.0)

        customerRepository.processAtomicCreditCheckout(mapOf(prodId to 1.0), custId).getOrThrow()

        val outstandingData = reportViewModel.buildCustomerDebtReportData(
            sampleSettings,
            mode = DebtReportMode.CURRENT_OUTSTANDING
        )

        val row = outstandingData.items[0]
        assertEquals("Haji Dulah", row.customerName)
        assertEquals("081298765432", row.customerPhone)
    }

    // 11. Soft-deleted customer preserves historical debts in PDF
    @Test
    fun test11_softDeletedCustomer_preservesHistoricalDebtInPdf() = runBlocking {
        val custId = customerRepository.saveCustomer("Pelanggan Lama", "08111222", "Alamat Lama")
        val prodId = createProduct("Sabun", purchasePrice = 3000L, sellingPrice = 4500L, stock = 10.0)

        customerRepository.processAtomicCreditCheckout(mapOf(prodId to 2.0), custId).getOrThrow()

        // Soft delete customer
        customerRepository.deleteCustomer(custId)

        val outstandingData = reportViewModel.buildCustomerDebtReportData(
            sampleSettings,
            mode = DebtReportMode.CURRENT_OUTSTANDING
        )

        // Historical debt must still appear with customer name intact
        assertEquals(1, outstandingData.items.size)
        assertEquals("Pelanggan Lama", outstandingData.items[0].customerName)
        assertEquals(9000L, outstandingData.totalOutstanding)
    }

    // 12. Mutation date filtering by createdAt
    @Test
    fun test12_mutationDateFiltering_includesOnlyDebtsInDateRange() = runBlocking {
        val custId = customerRepository.saveCustomer("Debitur Range", "0819", "Alamat")
        val prodId = createProduct("Air Mineral", purchasePrice = 3000L, sellingPrice = 4000L, stock = 10.0)

        val pastDate = 1700000000000L // Old date
        saleRepository.completeSale(
            cartItems = mapOf(prodId to 1.0),
            paymentMethod = "CREDIT",
            customerId = custId,
            now = pastDate
        ).getOrThrow()

        val todayDate = System.currentTimeMillis()
        saleRepository.completeSale(
            cartItems = mapOf(prodId to 2.0),
            paymentMethod = "CREDIT",
            customerId = custId,
            now = todayDate
        ).getOrThrow()

        val mutationToday = reportViewModel.buildCustomerDebtReportData(
            sampleSettings,
            mode = DebtReportMode.MUTATION_PERIOD,
            period = ReportPeriod.TODAY
        )

        assertEquals(1, mutationToday.items.size)
        assertEquals(8000L, mutationToday.totalDebtCreated)
    }

    // 13. Current outstanding reconciliation with authoritative ReportRepository
    @Test
    fun test13_currentOutstandingReconciliation() = runBlocking {
        val cust1 = customerRepository.saveCustomer("Debitur 1", "081", "A")
        val cust2 = customerRepository.saveCustomer("Debitur 2", "082", "B")
        val prodId = createProduct("Item Recon", purchasePrice = 10000L, sellingPrice = 20000L, stock = 50.0)

        // 1. Cust1 buys 2 pcs = 40,000, pays 15,000 -> remaining 25,000
        customerRepository.processAtomicCreditCheckout(mapOf(prodId to 2.0), cust1).getOrThrow()
        val debt1Id = database.debtDao().getOpenDebtsForCustomerList(cust1, "LEGACY_BUSINESS").first().id
        customerRepository.processAtomicDebtPayment(debt1Id, 15000L, "Bayar 15rb").getOrThrow()

        // 2. Cust2 buys 3 pcs = 60,000, pays 0 -> remaining 60,000
        customerRepository.processAtomicCreditCheckout(mapOf(prodId to 3.0), cust2).getOrThrow()

        val authoritativeOutstanding = reportRepository.totalOutstandingDebt.first() ?: 0L
        val outstandingData = reportViewModel.buildCustomerDebtReportData(
            sampleSettings,
            mode = DebtReportMode.CURRENT_OUTSTANDING
        )

        val sumRemainingRows = outstandingData.items.sumOf { it.remainingDebt }

        assertEquals(85000L, authoritativeOutstanding)
        assertEquals(authoritativeOutstanding, sumRemainingRows)
        assertEquals(authoritativeOutstanding, outstandingData.totalOutstanding)
    }

    // 14. Credit Return reduces debt
    @Test
    fun test14_creditReturn_reducesDebtTotalAccurately() = runBlocking {
        val custId = customerRepository.saveCustomer("Debitur Retur", "0855", "Alamat")
        val prodId = createProduct("Kemeja", purchasePrice = 40000L, sellingPrice = 75000L, stock = 10.0)

        // Credit sale: 2 pcs @ 75,000 = 150,000
        val saleId = customerRepository.processAtomicCreditCheckout(mapOf(prodId to 2.0), custId).getOrThrow()
        val saleItems = saleRepository.getItemsForTransaction(saleId)

        // Return 1 pcs = 75,000 -> debt reduced from 150,000 to 75,000
        saleRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItems.first().id to 1.0),
            reason = "Salah ukuran"
        ).getOrThrow()

        val outstandingData = reportViewModel.buildCustomerDebtReportData(
            sampleSettings,
            mode = DebtReportMode.CURRENT_OUTSTANDING
        )

        assertEquals(1, outstandingData.items.size)
        assertEquals(75000L, outstandingData.totalOutstanding)
        assertEquals(75000L, outstandingData.items[0].totalDebt)
        assertEquals(75000L, outstandingData.items[0].remainingDebt)
    }

    // 15. PDF structure valid
    @Test
    fun test15_pdfReportDocumentStructure_containsExpectedElements() = runBlocking {
        val custId = customerRepository.saveCustomer("Struktur Test", "0899", "Alamat")
        val prodId = createProduct("Produk Test", purchasePrice = 5000L, sellingPrice = 10000L, stock = 10.0)
        customerRepository.processAtomicCreditCheckout(mapOf(prodId to 2.0), custId).getOrThrow()

        val outstandingData = reportViewModel.buildCustomerDebtReportData(
            sampleSettings,
            mode = DebtReportMode.CURRENT_OUTSTANDING
        )
        val doc = CustomerDebtReportPdfBuilder.build(outstandingData)

        assertEquals("Kios Kiara Piutang Test", doc.header.shopName)
        assertEquals("LAPORAN PIUTANG PELANGGAN", doc.header.reportTitle)
        assertEquals(2, doc.sections.size)

        val summarySection = doc.sections[0]
        assertEquals("RINGKASAN PIUTANG AKTIF", summarySection.title)
        assertTrue(summarySection.summaryPairs.any { it.label.contains("Total Pelanggan Berpiutang") && it.value == "1 orang" })
        assertTrue(summarySection.summaryPairs.any { it.label.contains("Sisa Piutang Belum Lunas") && it.value.contains("20.000") })

        val tableSection = doc.sections[1]
        assertTrue(tableSection.title?.contains("DAFTAR PIUTANG AKTIF") == true)
        assertEquals(8, tableSection.tableColumns.size) // No, Tgl/No Trx, Pelanggan, Kontak, Total, Dibayar, Sisa, Status
        assertEquals(2, tableSection.tableRows.size) // 1 data row + 1 total row

        val result = pdfGenerator.generatePdf(doc)
        assertTrue(result.isSuccess)
    }

    // 16. Multi-page pagination
    @Test
    fun test16_multiPageDebtReport_paginatesAcrossPages() = runBlocking {
        val custId = customerRepository.saveCustomer("Banyak Hutang", "0812", "Alamat")
        val prodId = createProduct("Permen Grosir", purchasePrice = 500L, sellingPrice = 1000L, stock = 1000.0)

        // Insert 60 credit sales to guarantee multi-page rendering
        for (i in 1..60) {
            customerRepository.processAtomicCreditCheckout(mapOf(prodId to 1.0), custId).getOrThrow()
        }

        val outstandingData = reportViewModel.buildCustomerDebtReportData(
            sampleSettings,
            mode = DebtReportMode.CURRENT_OUTSTANDING
        )
        assertEquals(60, outstandingData.items.size)

        val doc = CustomerDebtReportPdfBuilder.build(outstandingData)
        val result = pdfGenerator.generatePdf(doc)
        assertTrue(result.isSuccess)

        val file = result.getOrThrow()
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        assertTrue("Expected >= 2 pages for 60 debt rows, got ${renderer.pageCount}", renderer.pageCount >= 2)
        renderer.close()
        pfd.close()
    }

    // 17. FileProvider and Share integration
    @Test
    fun test17_fileProviderAndShareIntegration() = runBlocking {
        val custId = customerRepository.saveCustomer("Share Cust", "0811", "Alamat")
        val prodId = createProduct("Snack", purchasePrice = 1000L, sellingPrice = 2000L, stock = 10.0)
        customerRepository.processAtomicCreditCheckout(mapOf(prodId to 1.0), custId).getOrThrow()

        val outstandingData = reportViewModel.buildCustomerDebtReportData(
            sampleSettings,
            mode = DebtReportMode.CURRENT_OUTSTANDING
        )
        val doc = CustomerDebtReportPdfBuilder.build(outstandingData)
        val file = pdfGenerator.generatePdf(doc).getOrThrow()

        val shareIntent = PdfShareManager.createSharePdfIntent(context, file, "Bagikan Laporan Piutang")
        val viewIntent = PdfShareManager.createViewPdfIntent(context, file)

        assertEquals(Intent.ACTION_CHOOSER, shareIntent.action)
        val targetedIntent = shareIntent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(targetedIntent)
        assertEquals(Intent.ACTION_SEND, targetedIntent!!.action)
        assertEquals("application/pdf", targetedIntent.type)
        assertTrue((targetedIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)

        assertEquals(Intent.ACTION_VIEW, viewIntent.action)
        assertEquals("application/pdf", viewIntent.type)
        assertTrue((viewIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
    }

    // 18. Read-only guarantee
    @Test
    fun test18_readOnlyGuarantee_databaseUntouchedDuringPdfGeneration() = runBlocking {
        val custId = customerRepository.saveCustomer("Read Only Cust", "0815", "Alamat")
        val prodId = createProduct("Produk RO", purchasePrice = 5000L, sellingPrice = 10000L, stock = 20.0)
        customerRepository.processAtomicCreditCheckout(mapOf(prodId to 1.0), custId).getOrThrow()

        val beforeCustomers = database.customerDao().getAllCustomers("LEGACY_BUSINESS").first().size
        val beforeDebts = database.debtDao().getAllOpenDebts("LEGACY_BUSINESS").first().size
        val beforePayments = database.debtDao().getPaymentsForDebt(1L, "LEGACY_BUSINESS").first().size
        val beforeSales = database.saleDao().getAllTransactions("LEGACY_BUSINESS").first().size
        val beforeCash = database.cashDao().getAllCashTransactions("LEGACY_BUSINESS").first().size

        // Build data & generate PDF
        val outstandingData = reportViewModel.buildCustomerDebtReportData(
            sampleSettings,
            mode = DebtReportMode.CURRENT_OUTSTANDING
        )
        val doc = CustomerDebtReportPdfBuilder.build(outstandingData)
        val result = pdfGenerator.generatePdf(doc)
        assertTrue(result.isSuccess)

        // Assert all Room tables remain untouched
        assertEquals(beforeCustomers, database.customerDao().getAllCustomers("LEGACY_BUSINESS").first().size)
        assertEquals(beforeDebts, database.debtDao().getAllOpenDebts("LEGACY_BUSINESS").first().size)
        assertEquals(beforePayments, database.debtDao().getPaymentsForDebt(1L, "LEGACY_BUSINESS").first().size)
        assertEquals(beforeSales, database.saleDao().getAllTransactions("LEGACY_BUSINESS").first().size)
        assertEquals(beforeCash, database.cashDao().getAllCashTransactions("LEGACY_BUSINESS").first().size)
    }

    // 19. Accounting regression test
    @Test
    fun test19_accountingRegression_cashAndCreditAndPaymentAndPayableSeparation() = runBlocking {
        val custId = customerRepository.saveCustomer("Debitur Kompleks", "081234", "Losarang")
        val suppId = supplierRepository.saveSupplier("Supplier Kompleks", "085678", "Indramayu")
        val prodId = createProduct("Beras Pandan", purchasePrice = 60000L, sellingPrice = 75000L, stock = 100.0)

        // 1. CASH sale = 75,000 (1 pcs) -> Sale + Cash INCOME (NO Debt)
        saleRepository.completeSale(mapOf(prodId to 1.0), "CASH", custId).getOrThrow()

        // 2. CREDIT sale = 150,000 (2 pcs) -> Sale + Debt OPEN (150,000) (NO Cash)
        val creditSaleId = customerRepository.processAtomicCreditCheckout(mapOf(prodId to 2.0), custId).getOrThrow()
        val debtId = database.debtDao().getOpenDebtsForCustomerList(custId, "LEGACY_BUSINESS").first().id

        // 3. Debt Payment = 50,000 -> Debt paidAmount=50,000, remaining=100,000, Cash INCOME (NO Sale)
        customerRepository.processAtomicDebtPayment(debtId, 50000L, "Cicilan 1").getOrThrow()

        // 4. Return on Credit Sale = 75,000 (1 pcs) -> reduces Debt totalDebt to 75,000, remaining=25,000
        val saleItems = saleRepository.getItemsForTransaction(creditSaleId)
        saleRepository.processSaleReturn(
            saleId = creditSaleId,
            itemsToReturn = mapOf(saleItems.first().id to 1.0),
            reason = "Retur 1 karung"
        ).getOrThrow()

        // 5. Supplier Credit Purchase = 120,000 (2 pcs @ 60,000) -> Purchase + SupplierPayable (NO Customer Debt)
        supplierRepository.processAtomicCreditPurchase(mapOf(prodId to 2.0), suppId).getOrThrow()

        // 6. Supplier Payment = 40,000 -> SupplierPayment + Cash EXPENSE (NO Customer Debt)
        val payableId = database.supplierPayableDao().getOpenPayablesForSupplierList(suppId, "LEGACY_BUSINESS").first().id
        supplierRepository.processAtomicSupplierPayment(payableId, 40000L, "Bayar ke supplier").getOrThrow()

        // VERIFY CUSTOMER DEBT REPORT
        val outstandingData = reportViewModel.buildCustomerDebtReportData(
            sampleSettings,
            mode = DebtReportMode.CURRENT_OUTSTANDING
        )

        assertEquals(1, outstandingData.items.size)
        val row = outstandingData.items[0]
        assertEquals(75000L, row.totalDebt) // 150,000 - 75,000 retur
        assertEquals(50000L, row.paidAmount) // 50,000 payment
        assertEquals(25000L, row.remainingDebt) // 75,000 - 50,000
        assertEquals("Belum Lunas", row.status)
        assertEquals(25000L, outstandingData.totalOutstanding)

        val authoritativeOutstanding = reportRepository.totalOutstandingDebt.first() ?: 0L
        assertEquals(25000L, authoritativeOutstanding)
        assertEquals(authoritativeOutstanding, outstandingData.totalOutstanding)
    }
}







