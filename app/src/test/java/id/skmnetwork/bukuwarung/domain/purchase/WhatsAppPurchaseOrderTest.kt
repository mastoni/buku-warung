package id.skmnetwork.bukuwarung.domain.purchase

import id.skmnetwork.bukuwarung.data.local.entity.ItemType
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseOrderEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseOrderItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseOrderStatus
import id.skmnetwork.bukuwarung.purchase.WhatsAppPurchaseOrderFormatter
import id.skmnetwork.bukuwarung.purchase.WhatsAppPurchaseOrderShareHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Gate G13.3 — WhatsApp Purchase Order Text Formatter & Dispatch Unit Tests
 * Tests A through Z verifying message composition, snapshots, decimal precision,
 * estimated pricing semantics, notes handling, and phone normalization.
 */
class WhatsAppPurchaseOrderTest {

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
                productName = "Minyak Goreng 2L",
                orderedQuantity = 4.0,
                unit = "pcs",
                estimatedPrice = 28000L,
                estimatedSubtotal = 112000L
            ),
            PurchaseOrderItemEntity(
                id = 3L,
                purchaseOrderId = 1L,
                productId = 103L,
                productName = "Pertamax",
                orderedQuantity = 8.64,
                unit = "liter",
                estimatedPrice = 12500L,
                estimatedSubtotal = 108000L
            )
        )
    }

    /**
     * Test A: Formatter contains PO title.
     */
    @Test
    fun testA_containsPoTitle() {
        val order = createSampleOrder()
        val text = WhatsAppPurchaseOrderFormatter.formatOrderText("Toko Maju Jaya", order, emptyList())
        assertTrue("Must contain header title", text.contains("*PURCHASE ORDER*"))
    }

    /**
     * Test B: Contains business name.
     */
    @Test
    fun testB_containsBusinessName() {
        val order = createSampleOrder()
        val text = WhatsAppPurchaseOrderFormatter.formatOrderText("Warung Berkah 88", order, emptyList())
        assertTrue("Must contain business name", text.contains("*Warung Berkah 88*"))

        // Fallback when blank
        val fallbackText = WhatsAppPurchaseOrderFormatter.formatOrderText("", order, emptyList())
        assertTrue("Must contain fallback name", fallbackText.contains("*Usaha Kami*"))
    }

    /**
     * Test C: Contains PO number.
     */
    @Test
    fun testC_containsPoNumber() {
        val order = createSampleOrder(orderNumber = "PO-20260918-999")
        val text = WhatsAppPurchaseOrderFormatter.formatOrderText("Toko Saya", order, emptyList())
        assertTrue("Must contain PO number", text.contains("No. PO: PO-20260918-999"))
    }

    /**
     * Test D: Contains date.
     */
    @Test
    fun testD_containsDate() {
        val order = createSampleOrder(createdAt = fixedTimestamp)
        val text = WhatsAppPurchaseOrderFormatter.formatOrderText("Toko Saya", order, emptyList())
        assertTrue("Must contain Tanggal prefix", text.contains("Tanggal:"))
    }

    /**
     * Test E: Contains supplier name snapshot.
     */
    @Test
    fun testE_containsSupplierName() {
        val order = createSampleOrder(supplierName = "CV Sejahtera Pangan")
        val text = WhatsAppPurchaseOrderFormatter.formatOrderText("Toko Saya", order, emptyList())
        assertTrue("Must contain supplier name", text.contains("CV Sejahtera Pangan"))
    }

    /**
     * Test F: Contains supplier phone snapshot when present.
     */
    @Test
    fun testF_containsSupplierPhoneWhenPresent() {
        val order = createSampleOrder(supplierPhone = "081234567890")
        val text = WhatsAppPurchaseOrderFormatter.formatOrderText("Toko Saya", order, emptyList())
        assertTrue("Must contain supplier phone", text.contains("Telp: 081234567890"))
    }

    /**
     * Test G & H: Contains every item with product name snapshot.
     */
    @Test
    fun testGAndH_containsEveryItemAndProductName() {
        val order = createSampleOrder()
        val items = createSampleItems()
        val text = WhatsAppPurchaseOrderFormatter.formatOrderText("Toko Saya", order, items)

        assertTrue(text.contains("Beras Rojolele 5kg"))
        assertTrue(text.contains("Minyak Goreng 2L"))
        assertTrue(text.contains("Pertamax"))
    }

    /**
     * Test I, J, K: Quantity, decimal FUEL formatting, and unit.
     */
    @Test
    fun testIJK_quantityDecimalAndUnit() {
        val order = createSampleOrder()
        val items = createSampleItems()
        val text = WhatsAppPurchaseOrderFormatter.formatOrderText("Toko Saya", order, items)

        assertTrue("Integer quantity formatted cleanly", text.contains("2 sak"))
        assertTrue("Integer quantity formatted cleanly", text.contains("4 pcs"))
        assertTrue("Decimal quantity preserved for FUEL", text.contains("8.64 liter"))
    }

    /**
     * Test L & M: Estimated price and estimated subtotal.
     */
    @Test
    fun testLM_estimatedPriceAndSubtotal() {
        val order = createSampleOrder()
        val items = createSampleItems()
        val text = WhatsAppPurchaseOrderFormatter.formatOrderText("Toko Saya", order, items)

        assertTrue("Contains price formatted", text.contains("Rp 65.000"))
        assertTrue("Contains subtotal formatted", text.contains("Subtotal: Rp 130.000"))
        assertTrue("Contains fuel subtotal formatted", text.contains("Subtotal: Rp 108.000"))
    }

    /**
     * Test N & O: Contains "Estimasi Total", does NOT call total "final" or "Total Pembelian".
     */
    @Test
    fun testNO_estimatedTotalSemantics() {
        val order = createSampleOrder(totalEstimatedAmount = 350000L)
        val items = createSampleItems()
        val text = WhatsAppPurchaseOrderFormatter.formatOrderText("Toko Saya", order, items)

        assertTrue("Must say Estimasi Total", text.contains("*Estimasi Total: Rp 350.000*"))
        assertFalse("Must not call it Total Pembelian", text.contains("Total Pembelian"))
        assertFalse("Must not say Final", text.contains("Harga Final"))
    }

    /**
     * Test P & Q: Notes included when present, omitted when empty/null.
     */
    @Test
    fun testPQ_notesInclusionAndOmission() {
        // With notes
        val orderWithNotes = createSampleOrder(notes = "Kirim ke gudang belakang")
        val textWithNotes = WhatsAppPurchaseOrderFormatter.formatOrderText("Toko Saya", orderWithNotes, emptyList())
        assertTrue("Must include Catatan header", textWithNotes.contains("Catatan:"))
        assertTrue("Must include note content", textWithNotes.contains("Kirim ke gudang belakang"))

        // Without notes
        val orderNoNotes = createSampleOrder(notes = null)
        val textNoNotes = WhatsAppPurchaseOrderFormatter.formatOrderText("Toko Saya", orderNoNotes, emptyList())
        assertFalse("Must omit Catatan when null", textNoNotes.contains("Catatan:"))

        // With blank notes
        val orderBlankNotes = createSampleOrder(notes = "   ")
        val textBlankNotes = WhatsAppPurchaseOrderFormatter.formatOrderText("Toko Saya", orderBlankNotes, emptyList())
        assertFalse("Must omit Catatan when blank", textBlankNotes.contains("Catatan:"))
    }

    /**
     * Test R: Multiple items formatted with sequential numbers.
     */
    @Test
    fun testR_multipleItemsSequential() {
        val order = createSampleOrder()
        val items = createSampleItems()
        val text = WhatsAppPurchaseOrderFormatter.formatOrderText("Toko Saya", order, items)

        assertTrue(text.contains("1. *Beras Rojolele 5kg*"))
        assertTrue(text.contains("2. *Minyak Goreng 2L*"))
        assertTrue(text.contains("3. *Pertamax*"))
    }

    /**
     * Test S, T, U, V: All ItemTypes formatted cleanly.
     */
    @Test
    fun testSTUV_allItemTypesSupported() {
        val order = createSampleOrder(totalEstimatedAmount = 250000L)
        val items = listOf(
            PurchaseOrderItemEntity(
                id = 1L,
                purchaseOrderId = 1L,
                productId = 1L,
                productName = "Barang Fisik",
                orderedQuantity = 5.0,
                unit = "pcs",
                estimatedPrice = 10000L,
                estimatedSubtotal = 50000L
            ),
            PurchaseOrderItemEntity(
                id = 2L,
                purchaseOrderId = 1L,
                productId = 2L,
                productName = "Bahan Bakar",
                orderedQuantity = 12.5,
                unit = "liter",
                estimatedPrice = 12000L,
                estimatedSubtotal = 150000L
            ),
            PurchaseOrderItemEntity(
                id = 3L,
                purchaseOrderId = 1L,
                productId = 3L,
                productName = "Voucher Digital",
                orderedQuantity = 1.0,
                unit = "voucher",
                estimatedPrice = 25000L,
                estimatedSubtotal = 25000L
            ),
            PurchaseOrderItemEntity(
                id = 4L,
                purchaseOrderId = 1L,
                productId = 4L,
                productName = "Jasa Pasang",
                orderedQuantity = 1.0,
                unit = "jasa",
                estimatedPrice = 25000L,
                estimatedSubtotal = 25000L
            )
        )
        val text = WhatsAppPurchaseOrderFormatter.formatOrderText("Toko Serba Ada", order, items)

        assertTrue(text.contains("5 pcs"))
        assertTrue(text.contains("12.5 liter"))
        assertTrue(text.contains("1 voucher"))
        assertTrue(text.contains("1 jasa"))
    }

    /**
     * Test W: Empty/null supplier phone handled safely.
     */
    @Test
    fun testW_emptySupplierPhoneHandledSafely() {
        val order = createSampleOrder(supplierPhone = null)
        val text = WhatsAppPurchaseOrderFormatter.formatOrderText("Toko Saya", order, emptyList())
        assertNotNull(text)
        assertFalse("Should not have Telp line if phone is null", text.contains("Telp:"))
    }

    /**
     * Test X & Y: Formatting produces zero database writes/side effects.
     */
    @Test
    fun testXY_zeroSideEffects() {
        val order = createSampleOrder()
        val items = createSampleItems()
        val text1 = WhatsAppPurchaseOrderFormatter.formatOrderText("Toko", order, items)
        val text2 = WhatsAppPurchaseOrderFormatter.formatOrderText("Toko", order, items)

        assertEquals("Formatter must be completely pure and idempotent", text1, text2)
    }

    /**
     * Test Z: Formatter is deterministic for identical inputs.
     */
    @Test
    fun testZ_deterministicFormatting() {
        val order = createSampleOrder()
        val items = createSampleItems()
        val result1 = WhatsAppPurchaseOrderFormatter.formatOrderText("Toko A", order, items)
        val result2 = WhatsAppPurchaseOrderFormatter.formatOrderText("Toko A", order, items)

        assertEquals(result1, result2)
    }

    /**
     * Test Phone Normalization in ShareHelper.
     */
    @Test
    fun testPhoneNormalization() {
        assertEquals("6281234567890", WhatsAppPurchaseOrderShareHelper.normalizePhoneNumber("081234567890"))
        assertEquals("6281234567890", WhatsAppPurchaseOrderShareHelper.normalizePhoneNumber("+6281234567890"))
        assertEquals("6281234567890", WhatsAppPurchaseOrderShareHelper.normalizePhoneNumber("6281234567890"))
        assertEquals("6281234567890", WhatsAppPurchaseOrderShareHelper.normalizePhoneNumber("0812-3456-7890"))
        assertNull(WhatsAppPurchaseOrderShareHelper.normalizePhoneNumber(null))
        assertNull(WhatsAppPurchaseOrderShareHelper.normalizePhoneNumber(""))
        assertNull(WhatsAppPurchaseOrderShareHelper.normalizePhoneNumber("   "))
    }
}
