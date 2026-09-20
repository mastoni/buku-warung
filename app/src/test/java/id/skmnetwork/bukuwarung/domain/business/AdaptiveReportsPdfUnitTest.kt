package id.skmnetwork.bukuwarung.domain.business

import id.skmnetwork.bukuwarung.pdf.reports.BusinessSummaryPdfBuilder
import id.skmnetwork.bukuwarung.pdf.reports.BusinessSummaryReportData
import id.skmnetwork.bukuwarung.pdf.reports.CustomerDebtReportPdfBuilder
import id.skmnetwork.bukuwarung.pdf.reports.DebtReportData
import id.skmnetwork.bukuwarung.pdf.reports.DebtReportMode
import id.skmnetwork.bukuwarung.pdf.reports.ProductReportData
import id.skmnetwork.bukuwarung.pdf.reports.ProductReportPdfBuilder
import id.skmnetwork.bukuwarung.pdf.reports.PurchaseReportData
import id.skmnetwork.bukuwarung.pdf.reports.PurchaseReportPdfBuilder
import id.skmnetwork.bukuwarung.pdf.reports.PurchaseReportRow
import id.skmnetwork.bukuwarung.pdf.reports.SalesReportData
import id.skmnetwork.bukuwarung.pdf.reports.SalesReportPdfBuilder
import id.skmnetwork.bukuwarung.pdf.reports.SalesReportRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate G11 — Adaptive Reports & PDF Export UI Unit Tests
 * Validates that BusinessTaxonomyRegistry resolution properly drives adaptive reports presentation
 * and native PDF report builder terminology across all business presets, while preserving
 * financial numeric values and fallback safety.
 */
class AdaptiveReportsPdfUnitTest {

    @Test
    fun testWarungSembakoBaselineReportTerminology() {
        val profile = BusinessTaxonomyRegistry.resolve(
            primaryType = BusinessType.WARUNG_SEMBAKO.name
        )

        assertEquals("Penjualan", profile.terminology.transactionLabel)
        assertEquals("Produk", profile.terminology.productLabel)
        assertEquals("Pembelian", profile.terminology.purchaseLabel)
        assertEquals("Pelanggan", profile.terminology.customerLabel)
        assertEquals("Supplier", profile.terminology.supplierLabel)
        assertEquals("Hutang", profile.terminology.debtLabel)
        assertEquals("Stok", profile.terminology.stockLabel)

        // PDF Builder Verification
        val salesData = SalesReportData(
            shopName = "Toko Berkah",
            periodLabel = "Hari Ini",
            printedAt = "17 Sep 2026",
            items = emptyList(),
            grossSales = 100000L,
            totalRefund = 0L,
            netSales = 100000L,
            totalTransactions = 1,
            terminology = profile.terminology
        )
        val salesDoc = SalesReportPdfBuilder.build(salesData)
        assertEquals("LAPORAN PENJUALAN", salesDoc.header.reportTitle)
        assertEquals("RINGKASAN PENJUALAN", salesDoc.sections[0].title)
    }

    @Test
    fun testApotekObatReportTerminology() {
        val profile = BusinessTaxonomyRegistry.resolve(
            primaryType = BusinessType.APOTEK_OBAT.id
        )

        assertEquals("Struk Apotek", profile.terminology.transactionLabel)
        assertEquals("Obat / Alkes", profile.terminology.productLabel)
        assertEquals("Pasien", profile.terminology.customerLabel)
        assertEquals("Pembelian", profile.terminology.purchaseLabel)
        assertEquals("Supplier", profile.terminology.supplierLabel)

        // PDF Document Title Verification
        val productData = ProductReportData(
            shopName = "Apotek Sehat",
            periodLabel = "Bulan Ini",
            printedAt = "17 Sep 2026",
            items = emptyList(),
            totalProductsCount = 5,
            totalNetQuantity = 20.0,
            totalNetRevenue = 500000L,
            totalNetCogs = 350000L,
            totalGrossProfit = 150000L,
            terminology = profile.terminology
        )
        val productDoc = ProductReportPdfBuilder.build(productData)
        assertEquals("LAPORAN STRUK APOTEK OBAT / ALKES", productDoc.header.reportTitle)
        assertEquals("RINGKASAN STRUK APOTEK OBAT / ALKES", productDoc.sections[0].title)
    }

    @Test
    fun testTokoBangunanReportTerminology() {
        val profile = BusinessTaxonomyRegistry.resolve(
            primaryType = BusinessType.TOKO_BANGUNAN.id
        )

        assertEquals("Nota Penjualan", profile.terminology.transactionLabel)
        assertEquals("Material", profile.terminology.productLabel)
        assertEquals("Pelanggan", profile.terminology.customerLabel)
        assertEquals("Pembelian", profile.terminology.purchaseLabel)

        val purchaseData = PurchaseReportData(
            shopName = "TB Jaya Abadi",
            periodLabel = "7 Hari",
            printedAt = "17 Sep 2026",
            items = emptyList(),
            totalPurchases = 2500000L,
            cashPurchasesTotal = 1500000L,
            creditPurchasesTotal = 1000000L,
            totalTransactions = 3,
            terminology = profile.terminology
        )
        val purchaseDoc = PurchaseReportPdfBuilder.build(purchaseData)
        assertEquals("LAPORAN PEMBELIAN", purchaseDoc.header.reportTitle)
        assertEquals("RINGKASAN PEMBELIAN", purchaseDoc.sections[0].title)
    }

    @Test
    fun testBengkelMotorMobilReportTerminology() {
        val profile = BusinessTaxonomyRegistry.resolve(
            primaryType = BusinessType.BENGKEL_MOTOR_MOBIL.id
        )

        assertEquals("Nota Servis", profile.terminology.transactionLabel)
        assertEquals("Sparepart & Oli", profile.terminology.productLabel)
        assertEquals("Jasa Servis", profile.terminology.serviceLabel)
        assertEquals("Pelanggan", profile.terminology.customerLabel)
        assertEquals("Supplier", profile.terminology.supplierLabel)

        val debtData = DebtReportData(
            mode = DebtReportMode.CURRENT_OUTSTANDING,
            shopName = "Bengkel Motor Maju",
            periodLabel = "Saldo Piutang Aktif Saat Ini",
            printedAt = "17 Sep 2026",
            items = emptyList(),
            totalOutstanding = 200000L,
            totalPaid = 100000L,
            totalDebtCreated = 300000L,
            activeDebtorsCount = 2,
            totalTransactions = 2,
            terminology = profile.terminology
        )
        val debtDoc = CustomerDebtReportPdfBuilder.build(debtData)
        assertEquals("LAPORAN PIUTANG PELANGGAN", debtDoc.header.reportTitle)
    }

    @Test
    fun testIndustriRumahanCompositeReportTerminology() {
        val profile = BusinessTaxonomyRegistry.resolve(
            primaryType = BusinessType.INDUSTRI_RUMAHAN.id,
            secondaryActivities = setOf(
                BusinessActivity.ACTIVITY_RAW_MATERIALS.name,
                BusinessActivity.ACTIVITY_WHOLESALE_PURCHASE.name
            )
        )

        assertNotNull(profile)
        assertEquals("Nota Penjualan", profile.terminology.transactionLabel)
        assertEquals("Produk Jadi", profile.terminology.productLabel)
        assertEquals("Pelanggan", profile.terminology.customerLabel)

        val summaryData = BusinessSummaryReportData(
            shopName = "Keripik Mantap",
            periodLabel = "Bulan Ini",
            printedAt = "17 Sep 2026",
            grossSales = 10000000L,
            salesReturn = 0L,
            netSales = 10000000L,
            salesCount = 50,
            salesReturnCount = 0,
            netCogs = 6000000L,
            grossProfit = 4000000L,
            operatingExpense = 1500000L,
            netProfit = 2500000L,
            cashBalance = 5000000L,
            stockValue = 3000000L,
            outstandingDebt = 500000L,
            outstandingPayable = 1000000L,
            terminology = profile.terminology
        )
        val summaryDoc = BusinessSummaryPdfBuilder.build(summaryData)
        assertEquals("RINGKASAN USAHA", summaryDoc.header.reportTitle)
        assertEquals("A. NOTA PENJUALAN & PENDAPATAN", summaryDoc.sections[0].title)
        assertEquals("B. LABA RUGI USAHA", summaryDoc.sections[1].title)
        assertEquals("C. POSISI KEUANGAN SAAT INI", summaryDoc.sections[2].title)
    }

    @Test
    fun testPdfPresentationDataPreservesNumericValues() {
        val profile = BusinessTaxonomyRegistry.resolve(BusinessType.APOTEK_OBAT.id)

        val grossSales = 1250000L
        val totalRefund = 50000L
        val netSales = 1200000L

        val salesData = SalesReportData(
            shopName = "Apotek Farma",
            periodLabel = "Hari Ini",
            printedAt = "17 Sep 2026",
            items = emptyList(),
            grossSales = grossSales,
            totalRefund = totalRefund,
            netSales = netSales,
            totalTransactions = 10,
            terminology = profile.terminology
        )

        // Numbers in data container remain identical
        assertEquals(grossSales, salesData.grossSales)
        assertEquals(totalRefund, salesData.totalRefund)
        assertEquals(netSales, salesData.netSales)
        assertEquals(10, salesData.totalTransactions)
    }

    @Test
    fun testFallbackForBlankOrUnknownBusinessType() {
        val blankProfile = BusinessTaxonomyRegistry.resolve("")
        assertEquals(BusinessType.WARUNG_SEMBAKO, blankProfile.businessType)
        assertEquals("Penjualan", blankProfile.terminology.transactionLabel)
        assertEquals("Produk", blankProfile.terminology.productLabel)
        assertEquals("Pelanggan", blankProfile.terminology.customerLabel)
        assertEquals("Supplier", blankProfile.terminology.supplierLabel)
        assertEquals("Hutang", blankProfile.terminology.debtLabel)
        assertEquals("Stok", blankProfile.terminology.stockLabel)

        val unknownProfile = BusinessTaxonomyRegistry.resolve("UNKNOWN_BIZ_XYZ")
        assertEquals(BusinessType.WARUNG_SEMBAKO, unknownProfile.businessType)
        assertEquals("Penjualan", unknownProfile.terminology.transactionLabel)
        assertEquals("Produk", unknownProfile.terminology.productLabel)
    }

    @Test
    fun testSalesReportWithTaxSnapshot() {
        val profile = BusinessTaxonomyRegistry.resolve(BusinessType.WARUNG_SEMBAKO.name)

        val rows = listOf(
            SalesReportRow(
                dateFormatted = "20/09/26 10:00",
                transactionNumber = "TRX-001",
                customerName = "Pelanggan A",
                paymentMethod = "Tunai",
                totalAmount = 110000L,
                refundAmount = 0L,
                status = "Lunas",
                taxableBase = 100000L,
                taxAmount = 10000L
            )
        )

        val salesData = SalesReportData(
            shopName = "Toko PPN",
            periodLabel = "Hari Ini",
            printedAt = "20 Sep 2026",
            items = rows,
            grossSales = 110000L,
            totalRefund = 0L,
            netSales = 110000L,
            totalTransactions = 1,
            totalTaxableBase = 100000L,
            totalTaxAmount = 10000L,
            terminology = profile.terminology
        )

        val doc = SalesReportPdfBuilder.build(salesData)
        val summarySection = doc.sections[0]
        assertTrue(summarySection.summaryPairs.any { it.label.contains("Dasar Pengenaan") })
        assertTrue(summarySection.summaryPairs.any { it.label.contains("PPN") })

        val tableSection = doc.sections[1]
        assertTrue(tableSection.tableColumns.any { it.header == "DPP" })
        assertTrue(tableSection.tableColumns.any { it.header == "PPN" })
        assertTrue(tableSection.tableRows.size >= 2)
    }

    @Test
    fun testSalesReportWithoutTaxShowsDash() {
        val profile = BusinessTaxonomyRegistry.resolve(BusinessType.WARUNG_SEMBAKO.name)

        val rows = listOf(
            SalesReportRow(
                dateFormatted = "20/09/26 10:00",
                transactionNumber = "TRX-002",
                customerName = "Pelanggan B",
                paymentMethod = "QRIS",
                totalAmount = 50000L,
                refundAmount = 0L,
                status = "Lunas",
                taxableBase = 0L,
                taxAmount = 0L
            )
        )

        val salesData = SalesReportData(
            shopName = "Toko Tanpa PPN",
            periodLabel = "Hari Ini",
            printedAt = "20 Sep 2026",
            items = rows,
            grossSales = 50000L,
            totalRefund = 0L,
            netSales = 50000L,
            totalTransactions = 1,
            totalTaxableBase = 0L,
            totalTaxAmount = 0L,
            terminology = profile.terminology
        )

        val doc = SalesReportPdfBuilder.build(salesData)
        val summarySection = doc.sections[0]
        assertFalse(summarySection.summaryPairs.any { it.label.contains("Dasar Pengenaan") })
        assertFalse(summarySection.summaryPairs.any { it.label.contains("PPN") })
    }

    @Test
    fun testPurchaseReportWithTaxSnapshot() {
        val profile = BusinessTaxonomyRegistry.resolve(BusinessType.WARUNG_SEMBAKO.name)

        val rows = listOf(
            PurchaseReportRow(
                dateFormatted = "20/09/26 08:00",
                transactionNumber = "BL-001",
                supplierName = "Supplier A",
                paymentMethod = "Tunai",
                totalAmount = 110000L,
                status = "Lunas",
                taxableBase = 100000L,
                taxAmount = 10000L
            )
        )

        val purchaseData = PurchaseReportData(
            shopName = "Toko PPN",
            periodLabel = "Hari Ini",
            printedAt = "20 Sep 2026",
            items = rows,
            totalPurchases = 110000L,
            purchasesTaxableBaseTotal = 100000L,
            purchasesTaxAmountTotal = 10000L,
            cashPurchasesTotal = 110000L,
            creditPurchasesTotal = 0L,
            totalTransactions = 1,
            terminology = profile.terminology
        )

        val doc = PurchaseReportPdfBuilder.build(purchaseData)
        val summarySection = doc.sections[0]
        assertTrue(summarySection.summaryPairs.any { it.label.contains("Dasar Pengenaan") })
        assertTrue(summarySection.summaryPairs.any { it.label.contains("PPN") })

        val tableSection = doc.sections[1]
        assertTrue(tableSection.tableColumns.any { it.header == "DPP" })
        assertTrue(tableSection.tableColumns.any { it.header == "PPN" })
    }
}
