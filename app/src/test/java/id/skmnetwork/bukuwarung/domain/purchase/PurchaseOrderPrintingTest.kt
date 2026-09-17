package id.skmnetwork.bukuwarung.domain.purchase

import id.skmnetwork.bukuwarung.data.local.entity.ItemType
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseOrderEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseOrderItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseOrderStatus
import id.skmnetwork.bukuwarung.domain.receipt.ReceiptPaperWidth
import id.skmnetwork.bukuwarung.printer.PrinterService
import id.skmnetwork.bukuwarung.printer.connection.MockPrinterConnection
import id.skmnetwork.bukuwarung.printer.escpos.EscPosCommands
import id.skmnetwork.bukuwarung.purchase.PurchaseOrderPdfBuilder
import id.skmnetwork.bukuwarung.purchase.PurchaseOrderReceiptFormatter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate G13.4 — Purchase Order Printing & PDF Formatter Unit Tests
 * Covers Tests A through Z (Thermal 58mm & 80mm ESC/POS / PlainText)
 * and Tests AA through AJ (PDF Report Document Generation & Safety).
 */
class PurchaseOrderPrintingTest {

    private val fixedTimestamp = 1726617600000L // 18 September 2024 / 2026

    private fun createSampleOrder(
        orderNumber: String = "PO-20260918-001",
        supplierName: String = "PT Sumber Berkah",
        supplierPhone: String? = "081234567890",
        notes: String? = "Tolong kirim sebelum jam 12 siang",
        totalEstimatedAmount: Long = 350000L,
        createdAt: Long = fixedTimestamp
    ): PurchaseOrderEntity {
        return PurchaseOrderEntity(
            id = 1L,
            orderNumber = orderNumber,
            supplierId = 10L,
            supplierNameSnapshot = supplierName,
            supplierPhoneSnapshot = supplierPhone,
            status = PurchaseOrderStatus.DRAFT.name,
            totalEstimatedAmount = totalEstimatedAmount,
            notes = notes,
            createdAt = createdAt,
            updatedAt = createdAt
        )
    }

    private fun createSampleItems(): List<PurchaseOrderItemEntity> {
        return listOf(
            PurchaseOrderItemEntity(
                id = 1L,
                purchaseOrderId = 1L,
                productId = 101L,
                productName = "Beras Rojolele 5kg",
                orderedQuantity = 2.0,
                unit = "sak",
                estimatedPrice = 65000L,
                estimatedSubtotal = 130000L
            ),
            PurchaseOrderItemEntity(
                id = 2L,
                purchaseOrderId = 1L,
                productId = 102L,
                productName = "Minyak Goreng 2L Kemasan Refill",
                orderedQuantity = 4.0,
                unit = "pcs",
                estimatedPrice = 28000L,
                estimatedSubtotal = 112000L
            ),
            PurchaseOrderItemEntity(
                id = 3L,
                purchaseOrderId = 1L,
                productId = 103L,
                productName = "Bensin Pertamax",
                orderedQuantity = 8.64,
                unit = "liter",
                estimatedPrice = 12500L,
                estimatedSubtotal = 108000L
            )
        )
    }

    // =========================================================================
    // THERMAL ESC/POS & PLAIN TEXT TESTS (A to Z)
    // =========================================================================

    /**
     * Test A: 58mm formatter exists and produces valid output with 32 columns.
     */
    @Test
    fun testA_58mmFormatterExists() {
        val order = createSampleOrder()
        val items = createSampleItems()
        val plainText58 = PurchaseOrderReceiptFormatter.formatPlainText(
            order = order,
            items = items,
            shopName = "Warung Berkah",
            paperWidth = ReceiptPaperWidth.WIDTH_58MM
        )
        assertNotNull(plainText58)
        assertTrue(plainText58.contains("--------------------------------")) // 32 chars divider

        val escPos58 = PurchaseOrderReceiptFormatter.formatEscPos(
            order = order,
            items = items,
            shopName = "Warung Berkah",
            paperWidth = ReceiptPaperWidth.WIDTH_58MM
        )
        assertTrue(escPos58.isNotEmpty())
        assertEquals(EscPosCommands.ESC, escPos58[0])
    }

    /**
     * Test B: 80mm formatter exists and produces valid output with 48 columns.
     */
    @Test
    fun testB_80mmFormatterExists() {
        val order = createSampleOrder()
        val items = createSampleItems()
        val plainText80 = PurchaseOrderReceiptFormatter.formatPlainText(
            order = order,
            items = items,
            shopName = "Warung Berkah",
            paperWidth = ReceiptPaperWidth.WIDTH_80MM
        )
        assertNotNull(plainText80)
        assertTrue(plainText80.contains("------------------------------------------------")) // 48 chars divider

        val escPos80 = PurchaseOrderReceiptFormatter.formatEscPos(
            order = order,
            items = items,
            shopName = "Warung Berkah",
            paperWidth = ReceiptPaperWidth.WIDTH_80MM
        )
        assertTrue(escPos80.isNotEmpty())
    }

    /**
     * Test C: Contains PO title.
     */
    @Test
    fun testC_containsPoTitle() {
        val order = createSampleOrder()
        val text = PurchaseOrderReceiptFormatter.formatPlainText(
            order = order,
            items = emptyList(),
            shopName = "Toko Saya",
            poTitle = "ORDER SPAREPART"
        )
        assertTrue(text.contains("ORDER SPAREPART"))
    }

    /**
     * Test D: Contains business name and fallback.
     */
    @Test
    fun testD_containsBusinessName() {
        val order = createSampleOrder()
        val text1 = PurchaseOrderReceiptFormatter.formatPlainText(
            order = order,
            items = emptyList(),
            shopName = "Toko Sumber Rejeki"
        )
        assertTrue(text1.contains("Toko Sumber Rejeki"))

        val textFallback = PurchaseOrderReceiptFormatter.formatPlainText(
            order = order,
            items = emptyList(),
            shopName = "   "
        )
        assertTrue(textFallback.contains("Usaha Kami"))
    }

    /**
     * Test E: Contains PO number.
     */
    @Test
    fun testE_containsPoNumber() {
        val order = createSampleOrder(orderNumber = "PO-20260918-999")
        val text = PurchaseOrderReceiptFormatter.formatPlainText(order, emptyList(), "Toko Saya")
        assertTrue(text.contains("No. PO: PO-20260918-999"))
    }

    /**
     * Test F: Contains PO creation date.
     */
    @Test
    fun testF_containsCreationDate() {
        val order = createSampleOrder(createdAt = fixedTimestamp)
        val text = PurchaseOrderReceiptFormatter.formatPlainText(order, emptyList(), "Toko Saya")
        val dateFormatted = PurchaseOrderReceiptFormatter.formatDate(fixedTimestamp)
        assertTrue(text.contains(dateFormatted))
    }

    /**
     * Test G & H: Contains supplier name and phone snapshot.
     */
    @Test
    fun testGH_containsSupplierSnapshot() {
        val orderWithPhone = createSampleOrder(supplierName = "PT Distributor Utama", supplierPhone = "081987654321")
        val textWithPhone = PurchaseOrderReceiptFormatter.formatPlainText(orderWithPhone, emptyList(), "Toko")
        assertTrue(textWithPhone.contains("Supplier: PT Distributor Utama"))
        assertTrue(textWithPhone.contains("Telp: 081987654321"))

        val orderNoPhone = createSampleOrder(supplierName = "PT Distributor Utama", supplierPhone = null)
        val textNoPhone = PurchaseOrderReceiptFormatter.formatPlainText(orderNoPhone, emptyList(), "Toko")
        assertTrue(textNoPhone.contains("Supplier: PT Distributor Utama"))
        assertFalse(textNoPhone.contains("Telp:"))
    }

    /**
     * Test I: Contains product snapshot name.
     */
    @Test
    fun testI_containsProductSnapshotName() {
        val order = createSampleOrder()
        val items = createSampleItems()
        val text = PurchaseOrderReceiptFormatter.formatPlainText(order, items, "Toko")
        assertTrue(text.contains("Beras Rojolele 5kg"))
        assertTrue(text.contains("Minyak Goreng 2L"))
        assertTrue(text.contains("Bensin Pertamax"))
    }

    /**
     * Test J, K, L: Quantity, decimal FUEL preservation, and unit.
     */
    @Test
    fun testJKL_quantityDecimalFuelAndUnit() {
        val order = createSampleOrder()
        val items = createSampleItems()
        val text = PurchaseOrderReceiptFormatter.formatPlainText(order, items, "Toko")

        assertTrue(text.contains("2 sak"))
        assertTrue(text.contains("4 pcs"))
        assertTrue("FUEL decimal preserved", text.contains("8.64 liter"))
    }

    /**
     * Test M & N: Estimated price and estimated subtotal.
     */
    @Test
    fun testMN_estimatedPriceAndSubtotal() {
        val order = createSampleOrder()
        val items = createSampleItems()
        val text = PurchaseOrderReceiptFormatter.formatPlainText(order, items, "Toko")

        assertTrue(text.contains("Rp 65.000"))
        assertTrue(text.contains("Rp 130.000"))
        assertTrue(text.contains("Rp 28.000"))
        assertTrue(text.contains("Rp 112.000"))
        assertTrue(text.contains("Rp 12.500"))
        assertTrue(text.contains("Rp 108.000"))
    }

    /**
     * Test O: Contains "Estimasi Total" and never "Total Pembelian" / "Harga Final".
     */
    @Test
    fun testO_estimatedTotalSemantics() {
        val order = createSampleOrder(totalEstimatedAmount = 350000L)
        val items = createSampleItems()
        val text = PurchaseOrderReceiptFormatter.formatPlainText(order, items, "Toko")

        assertTrue(text.contains("Estimasi Total"))
        assertTrue(text.contains("Rp 350.000"))
        assertFalse(text.contains("Total Pembelian"))
        assertFalse(text.contains("Harga Final"))
    }

    /**
     * Test P & Q: Notes included when present, omitted when empty/null.
     */
    @Test
    fun testPQ_notesHandling() {
        val orderWithNotes = createSampleOrder(notes = "Kirim ke pintu barat")
        val textWithNotes = PurchaseOrderReceiptFormatter.formatPlainText(orderWithNotes, emptyList(), "Toko")
        assertTrue(textWithNotes.contains("Catatan:"))
        assertTrue(textWithNotes.contains("Kirim ke pintu barat"))

        val orderNoNotes = createSampleOrder(notes = null)
        val textNoNotes = PurchaseOrderReceiptFormatter.formatPlainText(orderNoNotes, emptyList(), "Toko")
        assertFalse(textNoNotes.contains("Catatan:"))

        val orderBlankNotes = createSampleOrder(notes = "   ")
        val textBlankNotes = PurchaseOrderReceiptFormatter.formatPlainText(orderBlankNotes, emptyList(), "Toko")
        assertFalse(textBlankNotes.contains("Catatan:"))
    }

    /**
     * Test R: Multiple items formatted sequentially.
     */
    @Test
    fun testR_multipleItems() {
        val order = createSampleOrder()
        val items = createSampleItems()
        val text = PurchaseOrderReceiptFormatter.formatPlainText(order, items, "Toko")

        assertTrue(text.contains("1. Beras Rojolele 5kg"))
        assertTrue(text.contains("2. Minyak Goreng 2L"))
        assertTrue(text.contains("3. Bensin Pertamax"))
    }

    /**
     * Test S, T, U, V: Support for PHYSICAL, FUEL, DIGITAL, SERVICE.
     */
    @Test
    fun testSTUV_allItemTypes() {
        val order = createSampleOrder(totalEstimatedAmount = 400000L)
        val items = listOf(
            PurchaseOrderItemEntity(
                id = 1L,
                purchaseOrderId = 1L,
                productId = 1L,
                productName = "Semen Tiga Roda",
                orderedQuantity = 10.0,
                unit = "sak",
                estimatedPrice = 65000L,
                estimatedSubtotal = 650000L
            ),
            PurchaseOrderItemEntity(
                id = 2L,
                purchaseOrderId = 1L,
                productId = 2L,
                productName = "Solar Industri",
                orderedQuantity = 15.5,
                unit = "liter",
                estimatedPrice = 14000L,
                estimatedSubtotal = 217000L
            ),
            PurchaseOrderItemEntity(
                id = 3L,
                purchaseOrderId = 1L,
                productId = 3L,
                productName = "Voucher PLN 50k",
                orderedQuantity = 5.0,
                unit = "voucher",
                estimatedPrice = 50000L,
                estimatedSubtotal = 250000L
            ),
            PurchaseOrderItemEntity(
                id = 4L,
                purchaseOrderId = 1L,
                productId = 4L,
                productName = "Jasa Servis Dinamo",
                orderedQuantity = 2.0,
                unit = "jasa",
                estimatedPrice = 75000L,
                estimatedSubtotal = 150000L
            )
        )
        val text = PurchaseOrderReceiptFormatter.formatPlainText(order, items, "Toko Serba Bisa")

        assertTrue(text.contains("10 sak"))
        assertTrue(text.contains("15.5 liter"))
        assertTrue(text.contains("5 voucher"))
        assertTrue(text.contains("2 jasa"))
    }

    /**
     * Test W & X: Product name and notes line-wrapping for 58mm (32 cols).
     */
    @Test
    fun testWX_productNameAndNotesWrapping() {
        val longProductName = "Beras Organik Berkualitas Tinggi Super Pandan Wangi 25kg Kemasan Karung Goni"
        val longNotes = "Harap pastikan semua karung dalam keadaan kering dan tidak bocor serta diantar oleh supir berpengalaman."
        val order = createSampleOrder(notes = longNotes)
        val items = listOf(
            PurchaseOrderItemEntity(
                id = 1L,
                purchaseOrderId = 1L,
                productId = 1L,
                productName = longProductName,
                orderedQuantity = 1.0,
                unit = "karung",
                estimatedPrice = 320000L,
                estimatedSubtotal = 320000L
            )
        )

        val text58 = PurchaseOrderReceiptFormatter.formatPlainText(order, items, "Toko Maju", paperWidth = ReceiptPaperWidth.WIDTH_58MM)
        // Verify line widths
        text58.lines().forEach { line ->
            assertTrue("Line '$line' must not exceed 32 characters in 58mm", line.length <= 32)
        }
    }

    /**
     * Test Y: No clipping / overflow for 80mm (48 cols).
     */
    @Test
    fun testY_noClipping80mm() {
        val order = createSampleOrder()
        val items = createSampleItems()
        val text80 = PurchaseOrderReceiptFormatter.formatPlainText(order, items, "Toko Maju", paperWidth = ReceiptPaperWidth.WIDTH_80MM)

        text80.lines().forEach { line ->
            assertTrue("Line '$line' must not exceed 48 characters in 80mm", line.length <= 48)
        }
    }

    /**
     * Test Z: Formatter output is completely deterministic and pure.
     */
    @Test
    fun testZ_deterministicOutput() {
        val order = createSampleOrder()
        val items = createSampleItems()

        val plain1 = PurchaseOrderReceiptFormatter.formatPlainText(order, items, "Toko")
        val plain2 = PurchaseOrderReceiptFormatter.formatPlainText(order, items, "Toko")
        assertEquals(plain1, plain2)

        val esc1 = PurchaseOrderReceiptFormatter.formatEscPos(order, items, "Toko")
        val esc2 = PurchaseOrderReceiptFormatter.formatEscPos(order, items, "Toko")
        assertArrayEquals(esc1, esc2)
    }

    // =========================================================================
    // PDF BUILDER TESTS (AA to AJ)
    // =========================================================================

    /**
     * Test AA: PDF document generated successfully.
     */
    @Test
    fun testAA_pdfGenerated() {
        val order = createSampleOrder()
        val items = createSampleItems()
        val pdfDoc = PurchaseOrderPdfBuilder.build(
            order = order,
            items = items,
            shopName = "Warung Berkah Abadi",
            address = "Jl. Sudirman No. 45",
            phone = "08123456789"
        )
        assertNotNull(pdfDoc)
        assertEquals("PURCHASE ORDER", pdfDoc.header.reportTitle)
    }

    /**
     * Test AB: Valid document sections and structure.
     */
    @Test
    fun testAB_validDocumentStructure() {
        val order = createSampleOrder()
        val items = createSampleItems()
        val pdfDoc = PurchaseOrderPdfBuilder.build(order, items, "Warung Berkah")

        assertEquals(2, pdfDoc.sections.size)
        assertTrue(pdfDoc.sections[0].title?.contains("INFORMASI") == true)
        assertTrue(pdfDoc.sections[1].title?.contains("DAFTAR BARANG") == true)
    }

    /**
     * Test AC: Title present in PDF header.
     */
    @Test
    fun testAC_titlePresentInPdf() {
        val order = createSampleOrder()
        val pdfDoc = PurchaseOrderPdfBuilder.build(order, emptyList(), "Toko", poTitle = "ORDER MATERIAL")
        assertEquals("ORDER MATERIAL", pdfDoc.header.reportTitle)
    }

    /**
     * Test AD: Supplier name and phone present in PDF.
     */
    @Test
    fun testAD_supplierPresentInPdf() {
        val order = createSampleOrder(supplierName = "PT Semen Gresik", supplierPhone = "0812345")
        val pdfDoc = PurchaseOrderPdfBuilder.build(order, emptyList(), "TB Bangunan")
        val infoPairs = pdfDoc.sections[0].summaryPairs

        assertTrue(infoPairs.any { it.label == "Nama Supplier" && it.value == "PT Semen Gresik" })
        assertTrue(infoPairs.any { it.label == "Telp Supplier" && it.value == "0812345" })
    }

    /**
     * Test AE: PO number present in PDF header / metadata.
     */
    @Test
    fun testAE_poNumberPresentInPdf() {
        val order = createSampleOrder(orderNumber = "PO-2026-9999")
        val pdfDoc = PurchaseOrderPdfBuilder.build(order, emptyList(), "Toko")

        assertEquals("No. PO: PO-2026-9999", pdfDoc.header.periodLabel)
        val infoPairs = pdfDoc.sections[0].summaryPairs
        assertTrue(infoPairs.any { it.label == "Nomor Dokumen" && it.value == "PO-2026-9999" })
    }

    /**
     * Test AF: All items present in PDF table rows.
     */
    @Test
    fun testAF_allItemsPresentInPdf() {
        val order = createSampleOrder()
        val items = createSampleItems()
        val pdfDoc = PurchaseOrderPdfBuilder.build(order, items, "Toko")
        val tableRows = pdfDoc.sections[1].tableRows

        // 3 items + 1 summary total row = 4 rows
        assertEquals(4, tableRows.size)
        assertTrue(tableRows[0].cells.contains("Beras Rojolele 5kg"))
        assertTrue(tableRows[1].cells.contains("Minyak Goreng 2L Kemasan Refill"))
        assertTrue(tableRows[2].cells.contains("Bensin Pertamax"))
    }

    /**
     * Test AG: Estimated total present in summary pairs and table total row.
     */
    @Test
    fun testAG_estimatedTotalPresentInPdf() {
        val order = createSampleOrder(totalEstimatedAmount = 350000L)
        val items = createSampleItems()
        val pdfDoc = PurchaseOrderPdfBuilder.build(order, items, "Toko")

        val summaryPairs = pdfDoc.sections[0].summaryPairs
        assertTrue(summaryPairs.any { it.label == "Estimasi Total Pesanan" && it.value == "Rp 350.000" })

        val totalRow = pdfDoc.sections[1].tableRows.last()
        assertTrue(totalRow.isTotal)
        assertTrue(totalRow.cells.contains("ESTIMASI TOTAL"))
        assertTrue(totalRow.cells.contains("Rp 350.000"))
    }

    /**
     * Test AH: Notes present in PDF notes section.
     */
    @Test
    fun testAH_notesPresentInPdf() {
        val order = createSampleOrder(notes = "Tolong faktur disertakan fisik")
        val pdfDoc = PurchaseOrderPdfBuilder.build(order, emptyList(), "Toko")
        val notes = pdfDoc.sections[1].notes

        assertTrue(notes.any { it.contains("Tolong faktur disertakan fisik") })
    }

    /**
     * Test AI: No final-purchase wording in PDF.
     */
    @Test
    fun testAI_noFinalPurchaseWordingInPdf() {
        val order = createSampleOrder()
        val items = createSampleItems()
        val pdfDoc = PurchaseOrderPdfBuilder.build(order, items, "Toko")

        val docString = pdfDoc.toString()
        assertFalse("Must not call it Total Pembelian", docString.contains("Total Pembelian"))
        assertFalse("Must not say Harga Final", docString.contains("Harga Final"))
    }

    /**
     * Test AJ: Pure idempotency and zero side effects.
     */
    @Test
    fun testAJ_zeroSideEffectsAndIdempotency() {
        val order = createSampleOrder()
        val items = createSampleItems()

        val doc1 = PurchaseOrderPdfBuilder.build(order, items, "Toko", printedAt = "18 Sep 2026, 05:00")
        val doc2 = PurchaseOrderPdfBuilder.build(order, items, "Toko", printedAt = "18 Sep 2026, 05:00")

        assertEquals(doc1, doc2)
    }

    // =========================================================================
    // PRINTER SERVICE INTEGRATION TEST
    // =========================================================================

    /**
     * Test PrinterService.printPurchaseOrder with MockPrinterConnection.
     */
    @Test
    fun testPrinterServicePrintPurchaseOrder() = runBlocking {
        val mock = MockPrinterConnection()
        val printerService = PrinterService(activeConnection = mock)
        val order = createSampleOrder()
        val items = createSampleItems()

        val result = printerService.printPurchaseOrder(
            order = order,
            items = items,
            shopName = "Warung Mock Test",
            width = ReceiptPaperWidth.WIDTH_58MM,
            autoConnect = true
        )

        assertTrue("Printing PO via PrinterService must succeed", result.isSuccess)
        assertTrue("Mock connection must receive byte chunks", mock.receivedByteChunks.isNotEmpty())
        assertTrue("Mock connection must receive shop name", mock.fullPrintedText.contains("Warung Mock Test"))
        assertTrue("Mock connection must receive PO number", mock.fullPrintedText.contains("PO-20260918-001"))
        assertTrue("Mock connection must receive item name", mock.fullPrintedText.contains("Beras Rojolele 5kg"))
        assertTrue("Mock connection must receive Estimasi Total", mock.fullPrintedText.contains("Estimasi Total"))
    }
}
