package id.skmnetwork.bukuwarung

import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.ItemType
import id.skmnetwork.bukuwarung.data.preferences.UserSettings
import id.skmnetwork.bukuwarung.data.repository.CustomerRepository
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import id.skmnetwork.bukuwarung.data.repository.ReportRepository
import id.skmnetwork.bukuwarung.data.repository.SaleRepository
import id.skmnetwork.bukuwarung.pdf.PdfReportGenerator
import id.skmnetwork.bukuwarung.pdf.PdfShareManager
import id.skmnetwork.bukuwarung.pdf.reports.SalesReportPdfBuilder
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
import java.util.Calendar

/**
 * Gate G.1.3 — Validation Suite for Vertical Slice 2: LAPORAN PENJUALAN -> PDF.
 * Proves that Sales Report PDF derives strictly from persisted sales/return data,
 * maintains immutability of sales transactions, correctly attributes customers & payment methods,
 * supports multi-page rendering, FileProvider sharing, and guarantees zero database mutation.
 *
 * Scenarios:
 * 1. Empty period
 * 2. Single CASH sale
 * 3. QRIS sale
 * 4. CREDIT sale
 * 5. Sale with customer
 * 6. Sale without customer
 * 7. Multiple sales sorted descending by date
 * 8. Selected date range excludes transactions outside period
 * 9. Partial return does not mutate original sale
 * 10. Full return does not mutate original sale
 * 11. PDF contains expected report structure/data
 * 12. Multi-page sales report
 * 13. FileProvider/share integration
 * 14. Read-only database guarantee
 */
@RunWith(AndroidJUnit4::class)
class SalesReportPdfTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var productRepository: ProductRepository
    private lateinit var customerRepository: CustomerRepository
    private lateinit var saleRepository: SaleRepository
    private lateinit var reportRepository: ReportRepository
    private lateinit var reportViewModel: ReportViewModel
    private lateinit var pdfGenerator: PdfReportGenerator

    private val sampleSettings = UserSettings(
        shopName = "Kios Kiara Penjualan Test",
        address = "Jl. Sudirman No. 45, Bandung",
        phone = "081298765432"
    )

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        productRepository = ProductRepository(database, "LEGACY_BUSINESS")
        customerRepository = CustomerRepository(database, "LEGACY_BUSINESS")
        saleRepository = SaleRepository(database, "LEGACY_BUSINESS")
        reportRepository = ReportRepository(database, "LEGACY_BUSINESS")
        reportViewModel = ReportViewModel(reportRepository)
        pdfGenerator = PdfReportGenerator(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createProduct(
        name: String = "Kopi Sachet",
        purchasePrice: Long = 3000L,
        sellingPrice: Long = 5000L,
        stock: Double = 500.0
    ): Long = runBlocking {
        productRepository.insertProductWithCategory(
            name = name,
            categoryName = "Minuman",
            purchasePrice = purchasePrice,
            sellingPrice = sellingPrice,
            stock = stock,
            minimumStock = 2.0,
            unit = "pcs",
            itemType = ItemType.PHYSICAL
        )
    }

    // 1. Empty period
    @Test
    fun test1_emptyPeriod_producesZeroRowsAndValidPdf() = runBlocking {
        val salesData = reportViewModel.buildSalesReportData(sampleSettings, ReportPeriod.TODAY)

        assertEquals("Kios Kiara Penjualan Test", salesData.shopName)
        assertEquals(0, salesData.totalTransactions)
        assertEquals(0L, salesData.grossSales)
        assertEquals(0L, salesData.totalRefund)
        assertEquals(0L, salesData.netSales)
        assertTrue(salesData.items.isEmpty())

        // Build doc and generate PDF
        val doc = SalesReportPdfBuilder.build(salesData)
        val result = pdfGenerator.generatePdf(doc)
        assertTrue(result.isSuccess)

        val file = result.getOrThrow()
        assertTrue(file.exists())
        assertTrue(file.length() > 0)

        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        assertEquals(1, renderer.pageCount)
        renderer.close()
        pfd.close()
    }

    // 2. Single CASH sale
    @Test
    fun test2_singleCashSale_formattedCorrectly() = runBlocking {
        val prodId = createProduct("Teh Celup", purchasePrice = 2000L, sellingPrice = 4000L, stock = 10.0)
        saleRepository.completeSale(cartItems = mapOf(prodId to 2.0), paymentMethod = "CASH").getOrThrow()

        val salesData = reportViewModel.buildSalesReportData(sampleSettings, ReportPeriod.TODAY)

        assertEquals(1, salesData.totalTransactions)
        assertEquals(8000L, salesData.grossSales)
        assertEquals(0L, salesData.totalRefund)
        assertEquals(8000L, salesData.netSales)

        val row = salesData.items.first()
        assertEquals("Tunai", row.paymentMethod)
        assertEquals(8000L, row.totalAmount)
        assertEquals(0L, row.refundAmount)
        assertEquals("Lunas", row.status)
        assertEquals("Umum", row.customerName)
    }

    // 3. QRIS sale
    @Test
    fun test3_singleQrisSale_formattedCorrectly() = runBlocking {
        val prodId = createProduct("Snack", purchasePrice = 5000L, sellingPrice = 7500L, stock = 10.0)
        saleRepository.completeSale(cartItems = mapOf(prodId to 2.0), paymentMethod = "QRIS").getOrThrow()

        val salesData = reportViewModel.buildSalesReportData(sampleSettings, ReportPeriod.TODAY)

        assertEquals(1, salesData.totalTransactions)
        val row = salesData.items.first()
        assertEquals("QRIS", row.paymentMethod)
        assertEquals(15000L, row.totalAmount)
        assertEquals("Lunas", row.status)
    }

    // 4. CREDIT sale
    @Test
    fun test4_singleCreditSale_formattedCorrectly() = runBlocking {
        val prodId = createProduct("Sabun Cuci", purchasePrice = 8000L, sellingPrice = 12000L, stock = 10.0)
        val custId = customerRepository.saveCustomer("Pak Joko", "0811223344", "Alamat")

        customerRepository.processAtomicCreditCheckout(mapOf(prodId to 2.0), custId)

        val salesData = reportViewModel.buildSalesReportData(sampleSettings, ReportPeriod.TODAY)

        assertEquals(1, salesData.totalTransactions)
        val row = salesData.items.first()
        assertEquals("Kredit", row.paymentMethod)
        assertEquals(24000L, row.totalAmount)
        assertEquals("Kredit", row.status)
        assertEquals("Pak Joko", row.customerName)
    }

    // 5. Sale with customer
    @Test
    fun test5_saleWithCustomer_displaysCustomerName() = runBlocking {
        val prodId = createProduct("Biskuit", purchasePrice = 4000L, sellingPrice = 6000L, stock = 20.0)
        val custId = customerRepository.saveCustomer("Ibu Siti", "0855667788", "Jl Mawar")

        saleRepository.completeSale(
            cartItems = mapOf(prodId to 3.0),
            paymentMethod = "CASH",
            customerId = custId
        ).getOrThrow()

        val salesData = reportViewModel.buildSalesReportData(sampleSettings, ReportPeriod.TODAY)

        assertEquals(1, salesData.totalTransactions)
        val row = salesData.items.first()
        assertEquals("Ibu Siti", row.customerName)
        assertEquals(18000L, row.totalAmount)
    }

    // 6. Sale without customer
    @Test
    fun test6_saleWithoutCustomer_displaysUmum() = runBlocking {
        val prodId = createProduct("Permen", purchasePrice = 500L, sellingPrice = 1000L, stock = 50.0)

        saleRepository.completeSale(
            cartItems = mapOf(prodId to 5.0),
            paymentMethod = "CASH",
            customerId = null
        ).getOrThrow()

        val salesData = reportViewModel.buildSalesReportData(sampleSettings, ReportPeriod.TODAY)

        assertEquals(1, salesData.totalTransactions)
        val row = salesData.items.first()
        assertEquals("Umum", row.customerName)
        assertEquals(5000L, row.totalAmount)
    }

    // 7. Multiple sales sorted descending by date
    @Test
    fun test7_multipleSales_sortedDescendingByDate() = runBlocking {
        val prodId = createProduct("Air Mineral", purchasePrice = 2000L, sellingPrice = 3000L, stock = 100.0)

        val now = System.currentTimeMillis()
        val t1 = now - 50000 // oldest
        val t2 = now - 30000
        val t3 = now - 10000 // newest

        saleRepository.completeSale(mapOf(prodId to 1.0), "CASH", now = t1).getOrThrow()
        saleRepository.completeSale(mapOf(prodId to 2.0), "CASH", now = t2).getOrThrow()
        saleRepository.completeSale(mapOf(prodId to 3.0), "CASH", now = t3).getOrThrow()

        val salesData = reportViewModel.buildSalesReportData(sampleSettings, ReportPeriod.TODAY)

        assertEquals(3, salesData.totalTransactions)
        assertEquals(3000L * 3, salesData.items[0].totalAmount) // t3 newest = 9,000
        assertEquals(3000L * 2, salesData.items[1].totalAmount) // t2 middle = 6,000
        assertEquals(3000L * 1, salesData.items[2].totalAmount) // t1 oldest = 3,000
    }

    // 8. Selected date range excludes transactions outside period
    @Test
    fun test8_selectedDateRange_excludesTransactionsOutsidePeriod() = runBlocking {
        val prodId = createProduct("Roti Tawar", purchasePrice = 10000L, sellingPrice = 15000L, stock = 20.0)

        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -10) // 10 days ago
        val tenDaysAgo = cal.timeInMillis

        // Old sale
        saleRepository.completeSale(mapOf(prodId to 1.0), "CASH", now = tenDaysAgo).getOrThrow()
        // Today sale
        saleRepository.completeSale(mapOf(prodId to 2.0), "CASH").getOrThrow()

        val todayData = reportViewModel.buildSalesReportData(sampleSettings, ReportPeriod.TODAY)
        assertEquals(1, todayData.totalTransactions)
        assertEquals(30000L, todayData.grossSales)

        val allTimeData = reportViewModel.buildSalesReportData(sampleSettings, ReportPeriod.ALL_TIME)
        assertEquals(2, allTimeData.totalTransactions)
        assertEquals(45000L, allTimeData.grossSales)
    }

    // 9. Partial return does not mutate original sale
    @Test
    fun test9_partialReturn_doesNotMutateOriginalSale() = runBlocking {
        val prodId = createProduct("Susu UHT", purchasePrice = 4000L, sellingPrice = 6000L, stock = 20.0)

        // Sell 4 pcs = 24,000
        val saleId = saleRepository.completeSale(mapOf(prodId to 4.0), "CASH").getOrThrow()
        val saleItems = saleRepository.getItemsForTransaction(saleId)
        val saleItemId = saleItems.first().id

        // Return 1 pcs = 6,000
        val returnResult = saleRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItemId to 1.0),
            reason = "Bocor"
        )
        assertTrue(returnResult.isSuccess)

        // Verify DB original sale transaction total_amount is STILL 24,000
        val persistedSale = database.saleDao().getTransactionById(saleId, "LEGACY_BUSINESS")
        assertNotNull(persistedSale)
        assertEquals(24000L, persistedSale!!.totalAmount)

        // Verify Sales Report presentation data
        val salesData = reportViewModel.buildSalesReportData(sampleSettings, ReportPeriod.TODAY)
        assertEquals(1, salesData.totalTransactions)
        assertEquals(24000L, salesData.grossSales)
        assertEquals(6000L, salesData.totalRefund)
        assertEquals(18000L, salesData.netSales)

        val row = salesData.items.first()
        assertEquals(24000L, row.totalAmount) // Original amount intact
        assertEquals(6000L, row.refundAmount)
        assertEquals("Retur Sbg", row.status)
    }

    // 10. Full return does not mutate original sale
    @Test
    fun test10_fullReturn_doesNotMutateOriginalSale() = runBlocking {
        val prodId = createProduct("Kecap Manis", purchasePrice = 7000L, sellingPrice = 10000L, stock = 20.0)

        // Sell 2 pcs = 20,000
        val saleId = saleRepository.completeSale(mapOf(prodId to 2.0), "CASH").getOrThrow()
        val saleItems = saleRepository.getItemsForTransaction(saleId)
        val saleItemId = saleItems.first().id

        // Return ALL 2 pcs = 20,000
        val returnResult = saleRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItemId to 2.0),
            reason = "Kadaluarsa"
        )
        assertTrue(returnResult.isSuccess)

        // Verify DB original sale transaction total_amount is STILL 20,000
        val persistedSale = database.saleDao().getTransactionById(saleId, "LEGACY_BUSINESS")
        assertNotNull(persistedSale)
        assertEquals(20000L, persistedSale!!.totalAmount)

        val salesData = reportViewModel.buildSalesReportData(sampleSettings, ReportPeriod.TODAY)
        assertEquals(1, salesData.totalTransactions)
        assertEquals(20000L, salesData.grossSales)
        assertEquals(20000L, salesData.totalRefund)
        assertEquals(0L, salesData.netSales)

        val row = salesData.items.first()
        assertEquals(20000L, row.totalAmount) // Original amount intact
        assertEquals(20000L, row.refundAmount)
        assertEquals("Retur Total", row.status)
    }

    // 11. PDF contains expected report structure/data
    @Test
    fun test11_pdfReportDocumentStructure_containsExpectedElements() = runBlocking {
        val prodId = createProduct("Sabun Mandi", purchasePrice = 3000L, sellingPrice = 5000L, stock = 20.0)
        saleRepository.completeSale(mapOf(prodId to 2.0), "CASH").getOrThrow()

        val salesData = reportViewModel.buildSalesReportData(sampleSettings, ReportPeriod.TODAY)
        val doc = SalesReportPdfBuilder.build(salesData)

        // Check header
        assertEquals("Kios Kiara Penjualan Test", doc.header.shopName)
        assertEquals("LAPORAN PENJUALAN", doc.header.reportTitle)
        assertEquals("Jl. Sudirman No. 45, Bandung", doc.header.address)
        assertEquals("081298765432", doc.header.phone)

        // Check sections
        assertEquals(2, doc.sections.size)
        val summarySection = doc.sections[0]
        assertEquals("RINGKASAN PENJUALAN", summarySection.title)
        assertTrue(summarySection.summaryPairs.any { it.label == "Total Transaksi Penjualan" && it.value == "1 transaksi" })
        assertTrue(summarySection.summaryPairs.any { it.label == "Total Penjualan Bruto" && it.value.contains("10.000") })

        val tableSection = doc.sections[1]
        assertTrue(tableSection.title?.contains("DAFTAR TRANSAKSI") == true)
        assertEquals(6, tableSection.tableColumns.size) // No, Waktu / No. Trx, Pelanggan, Metode, Total, Status
        assertEquals(2, tableSection.tableRows.size) // 1 data row + 1 summary row

        // Generate PDF
        val result = pdfGenerator.generatePdf(doc)
        assertTrue(result.isSuccess)
    }

    // 12. Multi-page sales report
    @Test
    fun test12_multiPageSalesReport_paginatesAcrossPages() = runBlocking {
        val prodId = createProduct("Gula Batu", purchasePrice = 2000L, sellingPrice = 3000L, stock = 1000.0)

        // Insert 60 sales to guarantee multi-page rendering
        for (i in 1..60) {
            saleRepository.completeSale(mapOf(prodId to 1.0), "CASH").getOrThrow()
        }

        val salesData = reportViewModel.buildSalesReportData(sampleSettings, ReportPeriod.TODAY)
        assertEquals(60, salesData.totalTransactions)

        val doc = SalesReportPdfBuilder.build(salesData)
        val result = pdfGenerator.generatePdf(doc)
        assertTrue(result.isSuccess)

        val file = result.getOrThrow()
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        assertTrue("Expected >= 2 pages for 60 sales items, got ${renderer.pageCount}", renderer.pageCount >= 2)
        renderer.close()
        pfd.close()
    }

    // 13. FileProvider/share integration
    @Test
    fun test13_fileProviderAndShareIntegration() = runBlocking {
        val prodId = createProduct("Kopi Tubruk", purchasePrice = 1000L, sellingPrice = 2000L, stock = 20.0)
        saleRepository.completeSale(mapOf(prodId to 1.0), "CASH").getOrThrow()

        val salesData = reportViewModel.buildSalesReportData(sampleSettings, ReportPeriod.TODAY)
        val doc = SalesReportPdfBuilder.build(salesData)
        val file = pdfGenerator.generatePdf(doc).getOrThrow()

        val shareIntent = PdfShareManager.createSharePdfIntent(context, file, "Bagikan Laporan Penjualan")
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

    // 14. Read-only database guarantee
    @Test
    fun test14_readOnlyGuarantee_databaseUntouchedDuringPdfGeneration() = runBlocking {
        val prodId = createProduct("Mie Rebus", purchasePrice = 2500L, sellingPrice = 3500L, stock = 50.0)
        saleRepository.completeSale(cartItems = mapOf(prodId to 2.0), paymentMethod = "CASH").getOrThrow()

        val beforeCategories = database.categoryDao().getAllCategories("LEGACY_BUSINESS").first().size
        val beforeProducts = database.productDao().getAllProducts("LEGACY_BUSINESS").first().size
        val beforeSales = database.saleDao().getAllTransactions("LEGACY_BUSINESS").first().size
        val beforeCash = database.cashDao().getAllCashTransactions("LEGACY_BUSINESS").first().size
        val beforeDebts = database.debtDao().getOpenDebtsCount("LEGACY_BUSINESS").first()
        val beforePayables = database.supplierPayableDao().getOpenPayablesCount("LEGACY_BUSINESS").first()

        // Build data & generate PDF
        val salesData = reportViewModel.buildSalesReportData(sampleSettings, ReportPeriod.TODAY)
        val doc = SalesReportPdfBuilder.build(salesData)
        val result = pdfGenerator.generatePdf(doc)
        assertTrue(result.isSuccess)

        // Assert all Room tables remain untouched
        assertEquals(beforeCategories, database.categoryDao().getAllCategories("LEGACY_BUSINESS").first().size)
        assertEquals(beforeProducts, database.productDao().getAllProducts("LEGACY_BUSINESS").first().size)
        assertEquals(beforeSales, database.saleDao().getAllTransactions("LEGACY_BUSINESS").first().size)
        assertEquals(beforeCash, database.cashDao().getAllCashTransactions("LEGACY_BUSINESS").first().size)
        assertEquals(beforeDebts, database.debtDao().getOpenDebtsCount("LEGACY_BUSINESS").first())
        assertEquals(beforePayables, database.supplierPayableDao().getOpenPayablesCount("LEGACY_BUSINESS").first())
    }
}





