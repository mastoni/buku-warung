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
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import id.skmnetwork.bukuwarung.data.repository.ReportRepository
import id.skmnetwork.bukuwarung.data.repository.SaleRepository
import id.skmnetwork.bukuwarung.pdf.PdfReportGenerator
import id.skmnetwork.bukuwarung.pdf.PdfShareManager
import id.skmnetwork.bukuwarung.pdf.reports.ProductReportPdfBuilder
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
 * Gate G.1.4 — Validation Suite for Vertical Slice 3: LAPORAN PRODUK -> PDF.
 * Proves that Product Report PDF derives strictly from persisted sales items and return items,
 * uses historical purchase_price snapshots for COGS, aggregates per product correctly,
 * reconciles 100% with authoritative ReportRepository accounting figures, supports multi-page rendering,
 * handles deleted products, and guarantees zero database mutation.
 *
 * Scenarios:
 * 1. Empty period
 * 2. Single product sale
 * 3. Multiple products
 * 4. Same product sold in multiple transactions
 * 5. Quantity aggregation
 * 6. Revenue aggregation
 * 7. Historical purchase_price HPP
 * 8. Current Product.purchase_price does not override historical snapshot
 * 9. Partial return
 * 10. Full return
 * 11. Multiple returns
 * 12. Returned quantity does not produce negative net quantity
 * 13. Date range filtering
 * 14. Product identity using UUID
 * 15. Deleted/soft-deleted historical product does not crash
 * 16. PDF structure valid
 * 17. Multi-page PDF
 * 18. FileProvider/share integration
 * 19. Read-only Room guarantee
 * 20. Total product report reconciliation with authoritative accounting source
 */
@RunWith(AndroidJUnit4::class)
class ProductReportPdfTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var productRepository: ProductRepository
    private lateinit var saleRepository: SaleRepository
    private lateinit var reportRepository: ReportRepository
    private lateinit var reportViewModel: ReportViewModel
    private lateinit var pdfGenerator: PdfReportGenerator

    private val sampleSettings = UserSettings(
        shopName = "Kios Kiara Produk Test",
        address = "Jl. Merdeka No. 88, Bandung",
        phone = "081233445566"
    )

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        productRepository = ProductRepository(database, "LEGACY_BUSINESS")
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
        val productData = reportViewModel.buildProductReportData(sampleSettings, ReportPeriod.TODAY)

        assertEquals("Kios Kiara Produk Test", productData.shopName)
        assertEquals(0, productData.totalProductsCount)
        assertEquals(0.0, productData.totalNetQuantity, 0.001)
        assertEquals(0L, productData.totalNetRevenue)
        assertEquals(0L, productData.totalNetCogs)
        assertEquals(0L, productData.totalGrossProfit)
        assertTrue(productData.items.isEmpty())

        // Generate PDF
        val doc = ProductReportPdfBuilder.build(productData)
        val result = pdfGenerator.generatePdf(doc)
        assertTrue(result.isSuccess)

        val file = result.getOrThrow()
        assertTrue(file.exists())
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        assertEquals(1, renderer.pageCount)
        renderer.close()
        pfd.close()
    }

    // 2. Single product sale
    @Test
    fun test2_singleProductSale_correctMetrics() = runBlocking {
        val prodId = createProduct("Gula Pasir 1kg", purchasePrice = 12000L, sellingPrice = 15000L, stock = 50.0)
        saleRepository.completeSale(mapOf(prodId to 3.0), "CASH").getOrThrow()

        val productData = reportViewModel.buildProductReportData(sampleSettings, ReportPeriod.TODAY)

        assertEquals(1, productData.totalProductsCount)
        assertEquals(3.0, productData.totalNetQuantity, 0.001)
        assertEquals(45000L, productData.totalNetRevenue)
        assertEquals(36000L, productData.totalNetCogs)
        assertEquals(9000L, productData.totalGrossProfit)

        val row = productData.items.first()
        assertEquals("Gula Pasir 1kg", row.productName)
        assertEquals(3.0, row.quantitySold, 0.001)
        assertEquals(45000L, row.revenue)
        assertEquals(36000L, row.cogs)
        assertEquals(9000L, row.grossProfit)
    }

    // 3. Multiple products
    @Test
    fun test3_multipleProducts_aggregatedCorrectly() = runBlocking {
        val prodA = createProduct("Kopi ABC", purchasePrice = 1000L, sellingPrice = 1500L, stock = 100.0)
        val prodB = createProduct("Teh Celup", purchasePrice = 2000L, sellingPrice = 3000L, stock = 100.0)

        saleRepository.completeSale(
            mapOf(prodA to 4.0, prodB to 2.0),
            "CASH"
        ).getOrThrow()

        val productData = reportViewModel.buildProductReportData(sampleSettings, ReportPeriod.TODAY)

        assertEquals(2, productData.totalProductsCount)
        assertEquals(6.0, productData.totalNetQuantity, 0.001)
        // Kopi ABC: 4 * 1500 = 6000, Teh Celup: 2 * 3000 = 6000 -> Total = 12000
        assertEquals(12000L, productData.totalNetRevenue)
        // COGS: 4 * 1000 + 2 * 2000 = 8000
        assertEquals(8000L, productData.totalNetCogs)
        assertEquals(4000L, productData.totalGrossProfit)
    }

    // 4. Same product sold in multiple transactions
    @Test
    fun test4_sameProductSoldInMultipleTransactions_aggregatedIntoSingleRow() = runBlocking {
        val prodId = createProduct("Air Mineral 600ml", purchasePrice = 2000L, sellingPrice = 3000L, stock = 100.0)

        saleRepository.completeSale(mapOf(prodId to 2.0), "CASH").getOrThrow() // 6000
        saleRepository.completeSale(mapOf(prodId to 3.0), "CASH").getOrThrow() // 9000
        saleRepository.completeSale(mapOf(prodId to 5.0), "CASH").getOrThrow() // 15000

        val productData = reportViewModel.buildProductReportData(sampleSettings, ReportPeriod.TODAY)

        assertEquals(1, productData.totalProductsCount)
        val row = productData.items.first()
        assertEquals("Air Mineral 600ml", row.productName)
        assertEquals(10.0, row.quantitySold, 0.001)
        assertEquals(30000L, row.revenue)
        assertEquals(20000L, row.cogs)
        assertEquals(10000L, row.grossProfit)
    }

    // 5. Quantity aggregation
    @Test
    fun test5_quantityAggregation_sumsProperly() = runBlocking {
        val p1 = createProduct("P1", purchasePrice = 100L, sellingPrice = 200L, stock = 100.0)
        val p2 = createProduct("P2", purchasePrice = 200L, sellingPrice = 400L, stock = 100.0)

        saleRepository.completeSale(mapOf(p1 to 12.0), "CASH").getOrThrow()
        saleRepository.completeSale(mapOf(p2 to 8.5), "CASH").getOrThrow()

        val productData = reportViewModel.buildProductReportData(sampleSettings, ReportPeriod.TODAY)

        assertEquals(20.5, productData.totalNetQuantity, 0.001)
    }

    // 6. Revenue aggregation
    @Test
    fun test6_revenueAggregation_sumsProperly() = runBlocking {
        val p1 = createProduct("Snack A", purchasePrice = 1000L, sellingPrice = 2000L, stock = 100.0)
        val p2 = createProduct("Snack B", purchasePrice = 3000L, sellingPrice = 5000L, stock = 100.0)

        saleRepository.completeSale(mapOf(p1 to 10.0), "CASH").getOrThrow() // 20000
        saleRepository.completeSale(mapOf(p2 to 4.0), "CASH").getOrThrow()  // 20000

        val productData = reportViewModel.buildProductReportData(sampleSettings, ReportPeriod.TODAY)

        assertEquals(40000L, productData.totalNetRevenue)
    }

    // 7. Historical purchase_price HPP
    @Test
    fun test7_historicalPurchasePriceHpp_usesSaleSnapshot() = runBlocking {
        val prodId = createProduct("Minyak Goreng", purchasePrice = 14000L, sellingPrice = 17000L, stock = 50.0)

        // Sold 2 pcs with snapshot purchase_price = 14000
        saleRepository.completeSale(mapOf(prodId to 2.0), "CASH").getOrThrow()

        val productData = reportViewModel.buildProductReportData(sampleSettings, ReportPeriod.TODAY)
        val row = productData.items.first()

        assertEquals(28000L, row.cogs) // 2 * 14000
        assertEquals(6000L, row.grossProfit) // 34000 - 28000
    }

    // 8. Current Product.purchase_price does not override historical snapshot
    @Test
    fun test8_currentProductPriceChange_doesNotAlterHistoricalSaleCogs() = runBlocking {
        val prodId = createProduct("Sabun Batang", purchasePrice = 2000L, sellingPrice = 3000L, stock = 50.0)

        // Sale 1: purchase_price snapshot = 2000
        saleRepository.completeSale(mapOf(prodId to 5.0), "CASH").getOrThrow() // revenue 15000, cogs 10000

        // Now supplier raises price -> update product in DB to purchasePrice = 2800
        productRepository.updateProductWithCategory(
            productId = prodId,
            name = "Sabun Batang",
            categoryName = "Minuman",
            purchasePrice = 2800L,
            sellingPrice = 3500L,
            stock = 45.0,
            minimumStock = 2.0,
            unit = "pcs"
        )

        val productData = reportViewModel.buildProductReportData(sampleSettings, ReportPeriod.TODAY)
        val row = productData.items.first()

        // Historical COGS must remain 10000 (5 * 2000), NOT 14000 (5 * 2800)
        assertEquals(10000L, row.cogs)
        assertEquals(5000L, row.grossProfit)
    }

    // 9. Partial return
    @Test
    fun test9_partialReturn_deductsFromNetMetrics() = runBlocking {
        val prodId = createProduct("Susu Kotak", purchasePrice = 4000L, sellingPrice = 6000L, stock = 50.0)

        // Sell 5 pcs = 30,000 (COGS 20,000)
        val saleId = saleRepository.completeSale(mapOf(prodId to 5.0), "CASH").getOrThrow()
        val saleItems = saleRepository.getItemsForTransaction(saleId)

        // Return 2 pcs = 12,000 (COGS 8,000)
        saleRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItems.first().id to 2.0),
            reason = "Kemasan Rusak"
        ).getOrThrow()

        val productData = reportViewModel.buildProductReportData(sampleSettings, ReportPeriod.TODAY)
        val row = productData.items.first()

        assertEquals(3.0, row.quantitySold, 0.001) // 5 - 2
        assertEquals(18000L, row.revenue) // 30000 - 12000
        assertEquals(12000L, row.cogs) // 20000 - 8000
        assertEquals(6000L, row.grossProfit) // 18000 - 12000
    }

    // 10. Full return
    @Test
    fun test10_fullReturn_resultsInZeroNetMetrics() = runBlocking {
        val prodId = createProduct("Biskuit Roma", purchasePrice = 5000L, sellingPrice = 8000L, stock = 50.0)

        // Sell 3 pcs = 24,000
        val saleId = saleRepository.completeSale(mapOf(prodId to 3.0), "CASH").getOrThrow()
        val saleItems = saleRepository.getItemsForTransaction(saleId)

        // Return ALL 3 pcs = 24,000
        saleRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItems.first().id to 3.0),
            reason = "Batal"
        ).getOrThrow()

        val productData = reportViewModel.buildProductReportData(sampleSettings, ReportPeriod.TODAY)
        val row = productData.items.first()

        assertEquals(0.0, row.quantitySold, 0.001)
        assertEquals(0L, row.revenue)
        assertEquals(0L, row.cogs)
        assertEquals(0L, row.grossProfit)
    }

    // 11. Multiple returns
    @Test
    fun test11_multipleReturns_accumulateCorrectly() = runBlocking {
        val prodId = createProduct("Mie Sedaap", purchasePrice = 2500L, sellingPrice = 3500L, stock = 50.0)

        // Sell 10 pcs = 35,000
        val saleId = saleRepository.completeSale(mapOf(prodId to 10.0), "CASH").getOrThrow()
        val saleItemId = saleRepository.getItemsForTransaction(saleId).first().id

        // Return 1: 2 pcs
        saleRepository.processSaleReturn(saleId, mapOf(saleItemId to 2.0), "Rusak").getOrThrow()
        // Return 2: 3 pcs
        saleRepository.processSaleReturn(saleId, mapOf(saleItemId to 3.0), "Salah Varian").getOrThrow()

        val productData = reportViewModel.buildProductReportData(sampleSettings, ReportPeriod.TODAY)
        val row = productData.items.first()

        assertEquals(5.0, row.quantitySold, 0.001) // 10 - 5
        assertEquals(17500L, row.revenue) // 35000 - 17500
        assertEquals(12500L, row.cogs) // 25000 - 12500
        assertEquals(5000L, row.grossProfit)
    }

    // 12. Returned quantity does not produce negative net quantity
    @Test
    fun test12_returnedQuantity_neverProducesNegative() = runBlocking {
        val prodId = createProduct("Kecap", purchasePrice = 6000L, sellingPrice = 9000L, stock = 50.0)

        val saleId = saleRepository.completeSale(mapOf(prodId to 2.0), "CASH").getOrThrow()
        val saleItemId = saleRepository.getItemsForTransaction(saleId).first().id

        saleRepository.processSaleReturn(saleId, mapOf(saleItemId to 2.0), "Retur").getOrThrow()

        val productData = reportViewModel.buildProductReportData(sampleSettings, ReportPeriod.TODAY)
        val row = productData.items.first()

        assertTrue(row.quantitySold >= 0.0)
    }

    // 13. Date range filtering
    @Test
    fun test13_dateRangeFiltering_excludesOutsideTransactions() = runBlocking {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -10) // 10 days ago
        val tenDaysAgo = cal.timeInMillis

        val prodA = createProduct("Produk Lama", purchasePrice = 1000L, sellingPrice = 2000L, stock = 50.0)
        val prodB = createProduct("Produk Hari Ini", purchasePrice = 2000L, sellingPrice = 4000L, stock = 50.0)

        // Sale 10 days ago
        saleRepository.completeSale(mapOf(prodA to 5.0), "CASH", now = tenDaysAgo).getOrThrow()
        // Sale today
        saleRepository.completeSale(mapOf(prodB to 3.0), "CASH").getOrThrow()

        val todayData = reportViewModel.buildProductReportData(sampleSettings, ReportPeriod.TODAY)
        assertEquals(1, todayData.totalProductsCount)
        assertEquals("Produk Hari Ini", todayData.items.first().productName)

        val allTimeData = reportViewModel.buildProductReportData(sampleSettings, ReportPeriod.ALL_TIME)
        assertEquals(2, allTimeData.totalProductsCount)
    }

    // 14. Product identity using UUID
    @Test
    fun test14_productIdentityUsingUuid() = runBlocking {
        val prodId = createProduct("Teh Botol Kotak", purchasePrice = 3000L, sellingPrice = 4500L, stock = 50.0)
        val prodEntity = database.productDao().getProductById(prodId, "LEGACY_BUSINESS")!!

        saleRepository.completeSale(mapOf(prodId to 2.0), "CASH").getOrThrow()

        val productData = reportViewModel.buildProductReportData(sampleSettings, ReportPeriod.TODAY)
        val row = productData.items.first()

        assertEquals(prodEntity.uuid, row.productUuid)
    }

    // 15. Deleted/soft-deleted historical product does not crash
    @Test
    fun test15_deletedProduct_displaysSnapshotNameAndDoesNotCrash() = runBlocking {
        val prodId = createProduct("Produk Dihapus", purchasePrice = 5000L, sellingPrice = 8000L, stock = 50.0)
        saleRepository.completeSale(mapOf(prodId to 2.0), "CASH").getOrThrow()

        // Soft delete the product in DB
        database.productDao().softDeleteProduct(prodId, System.currentTimeMillis(), "LEGACY_BUSINESS")

        val productData = reportViewModel.buildProductReportData(sampleSettings, ReportPeriod.TODAY)

        assertEquals(1, productData.totalProductsCount)
        val row = productData.items.first()
        assertEquals("Produk Dihapus", row.productName)
        assertEquals(16000L, row.revenue)
        assertEquals(10000L, row.cogs)
    }

    // 16. PDF structure valid
    @Test
    fun test16_pdfReportDocumentStructure_containsExpectedElements() = runBlocking {
        val prodId = createProduct("Kopi Kapal Api", purchasePrice = 1200L, sellingPrice = 2000L, stock = 50.0)
        saleRepository.completeSale(mapOf(prodId to 5.0), "CASH").getOrThrow()

        val productData = reportViewModel.buildProductReportData(sampleSettings, ReportPeriod.TODAY)
        val doc = ProductReportPdfBuilder.build(productData)

        assertEquals("Kios Kiara Produk Test", doc.header.shopName)
        assertEquals("LAPORAN PENJUALAN PRODUK", doc.header.reportTitle)
        assertEquals(2, doc.sections.size)

        val summarySection = doc.sections[0]
        assertEquals("RINGKASAN PENJUALAN PRODUK", summarySection.title)
        assertTrue(summarySection.summaryPairs.any { it.label == "Total Jenis Produk" && it.value == "1 produk" })
        assertTrue(summarySection.summaryPairs.any { it.label == "Total Omzet Bersih" && it.value.contains("10.000") })

        val tableSection = doc.sections[1]
        assertTrue(tableSection.title?.contains("DAFTAR PRODUK TERJUAL") == true)
        assertEquals(6, tableSection.tableColumns.size) // No, Nama Produk, Qty, Omzet, HPP, Laba Kotor
        assertEquals(2, tableSection.tableRows.size) // 1 product row + 1 total row

        val result = pdfGenerator.generatePdf(doc)
        assertTrue(result.isSuccess)
    }

    // 17. Multi-page PDF
    @Test
    fun test17_multiPageProductReport_paginatesAcrossPages() = runBlocking {
        // Create 60 distinct products and sell 1 of each
        val cart = mutableMapOf<Long, Double>()
        for (i in 1..60) {
            val id = createProduct("Produk Variasi $i", purchasePrice = 1000L, sellingPrice = 2000L, stock = 10.0)
            cart[id] = 1.0
        }
        saleRepository.completeSale(cart, "CASH").getOrThrow()

        val productData = reportViewModel.buildProductReportData(sampleSettings, ReportPeriod.TODAY)
        assertEquals(60, productData.totalProductsCount)

        val doc = ProductReportPdfBuilder.build(productData)
        val result = pdfGenerator.generatePdf(doc)
        assertTrue(result.isSuccess)

        val file = result.getOrThrow()
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        assertTrue("Expected >= 2 pages for 60 product rows, got ${renderer.pageCount}", renderer.pageCount >= 2)
        renderer.close()
        pfd.close()
    }

    // 18. FileProvider/share integration
    @Test
    fun test18_fileProviderAndShareIntegration() = runBlocking {
        val prodId = createProduct("Teh Kotak", purchasePrice = 3000L, sellingPrice = 4000L, stock = 10.0)
        saleRepository.completeSale(mapOf(prodId to 1.0), "CASH").getOrThrow()

        val productData = reportViewModel.buildProductReportData(sampleSettings, ReportPeriod.TODAY)
        val doc = ProductReportPdfBuilder.build(productData)
        val file = pdfGenerator.generatePdf(doc).getOrThrow()

        val shareIntent = PdfShareManager.createSharePdfIntent(context, file, "Bagikan Laporan Produk")
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

    // 19. Read-only Room guarantee
    @Test
    fun test19_readOnlyGuarantee_databaseUntouchedDuringPdfGeneration() = runBlocking {
        val prodId = createProduct("Permen Karet", purchasePrice = 500L, sellingPrice = 1000L, stock = 50.0)
        saleRepository.completeSale(cartItems = mapOf(prodId to 2.0), paymentMethod = "CASH").getOrThrow()

        val beforeCategories = database.categoryDao().getAllCategories("LEGACY_BUSINESS").first().size
        val beforeProducts = database.productDao().getAllProducts("LEGACY_BUSINESS").first().size
        val beforeSales = database.saleDao().getAllTransactions("LEGACY_BUSINESS").first().size
        val beforeCash = database.cashDao().getAllCashTransactions("LEGACY_BUSINESS").first().size
        val beforeDebts = database.debtDao().getOpenDebtsCount("LEGACY_BUSINESS").first()
        val beforePayables = database.supplierPayableDao().getOpenPayablesCount("LEGACY_BUSINESS").first()

        // Build data & generate PDF
        val productData = reportViewModel.buildProductReportData(sampleSettings, ReportPeriod.TODAY)
        val doc = ProductReportPdfBuilder.build(productData)
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

    // 20. Total product report reconciliation with authoritative accounting source
    @Test
    fun test20_reconciliationWithAuthoritativeAccountingSource() = runBlocking {
        val p1 = createProduct("Susu Bayi", purchasePrice = 80000L, sellingPrice = 95000L, stock = 20.0)
        val p2 = createProduct("Biskuit Bayi", purchasePrice = 10000L, sellingPrice = 15000L, stock = 30.0)
        val p3 = createProduct("Popok Bayi", purchasePrice = 45000L, sellingPrice = 55000L, stock = 40.0)

        // Sale 1: p1 (2 pcs), p2 (4 pcs)
        val sale1 = saleRepository.completeSale(mapOf(p1 to 2.0, p2 to 4.0), "CASH").getOrThrow()
        // Sale 2: p2 (2 pcs), p3 (3 pcs)
        val sale2 = saleRepository.completeSale(mapOf(p2 to 2.0, p3 to 3.0), "CASH").getOrThrow()

        // Return: 1 pc of p2 from sale1
        val sale1Items = saleRepository.getItemsForTransaction(sale1)
        val p2Item = sale1Items.first { it.productId == p2 }
        saleRepository.processSaleReturn(sale1, mapOf(p2Item.id to 1.0), "Kelebihan Beli").getOrThrow()

        val range = ReportViewModel.calculateDateRange(ReportPeriod.TODAY)

        // Authoritative accounting figures from ReportRepository
        val authoritativeNetSales = reportRepository.getNetSalesTotal(range.startDate, range.endDate).first()
        val authoritativeNetCogs = reportRepository.getNetCogsTotal(range.startDate, range.endDate).first()
        val authoritativeGrossProfit = reportRepository.getGrossProfitTotal(range.startDate, range.endDate).first()

        // Product Report data
        val productData = reportViewModel.buildProductReportData(sampleSettings, ReportPeriod.TODAY)

        // SUM of all rows
        val sumNetRevenue = productData.items.sumOf { it.revenue }
        val sumNetCogs = productData.items.sumOf { it.cogs }
        val sumGrossProfit = productData.items.sumOf { it.grossProfit }

        // RECONCILIATION ASSERTIONS: Must be 100% IDENTICAL
        assertEquals("Product Report Net Revenue must match ReportRepository.getNetSalesTotal", authoritativeNetSales, sumNetRevenue)
        assertEquals("Product Report Net Revenue must match container totalNetRevenue", productData.totalNetRevenue, sumNetRevenue)

        assertEquals("Product Report Net COGS must match ReportRepository.getNetCogsTotal", authoritativeNetCogs, sumNetCogs)
        assertEquals("Product Report Net COGS must match container totalNetCogs", productData.totalNetCogs, sumNetCogs)

        assertEquals("Product Report Gross Profit must match ReportRepository.getGrossProfitTotal", authoritativeGrossProfit, sumGrossProfit)
        assertEquals("Product Report Gross Profit must match container totalGrossProfit", productData.totalGrossProfit, sumGrossProfit)
    }
}




