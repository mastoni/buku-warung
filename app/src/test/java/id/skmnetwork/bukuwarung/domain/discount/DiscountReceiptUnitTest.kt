package id.skmnetwork.bukuwarung.domain.discount

import id.skmnetwork.bukuwarung.data.local.entity.SaleItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleTransactionEntity
import id.skmnetwork.bukuwarung.data.preferences.UserSettings
import id.skmnetwork.bukuwarung.domain.receipt.ReceiptItem
import id.skmnetwork.bukuwarung.domain.receipt.ReceiptMapper
import id.skmnetwork.bukuwarung.domain.receipt.ReceiptPaperWidth
import id.skmnetwork.bukuwarung.domain.receipt.ReceiptPaymentInfo
import id.skmnetwork.bukuwarung.domain.receipt.ShopProfile
import id.skmnetwork.bukuwarung.printer.escpos.EscPosReceiptFormatter
import id.skmnetwork.bukuwarung.printer.escpos.PlainTextReceiptFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscountReceiptUnitTest {

    /**
     * Test 1: ReceiptMapper maps discount and subtotal correctly when discount > 0.
     */
    @Test
    fun testReceiptMapperWithDiscount() {
        val sale = SaleTransactionEntity(
            id = 101L,
            transactionNumber = "TRX-101",
            transactionDate = 1700000000000L,
            totalAmount = 45000L,
            discountAmount = 5000L,
            paymentMethod = "CASH"
        )
        val items = listOf(
            SaleItemEntity(
                id = 1L,
                transactionId = 101L,
                productId = 1L,
                productName = "Kopi Susu",
                quantity = 2.0,
                price = 25000L,
                purchasePrice = 15000L,
                subtotal = 50000L
            )
        )

        val receiptData = ReceiptMapper.mapFromSale(
            sale = sale,
            items = items,
            cashGiven = 50000L
        )

        assertEquals(45000L, receiptData.paymentInfo.totalAmount)
        assertEquals(50000L, receiptData.paymentInfo.subtotalAmount)
        assertEquals(5000L, receiptData.paymentInfo.discountAmount)
        assertEquals(5000L, receiptData.paymentInfo.changeAmount)
    }

    /**
     * Test 2: ReceiptMapper leaves subtotal and discount null when discount is 0.
     */
    @Test
    fun testReceiptMapperWithoutDiscount() {
        val sale = SaleTransactionEntity(
            id = 102L,
            transactionNumber = "TRX-102",
            transactionDate = 1700000000000L,
            totalAmount = 50000L,
            discountAmount = 0L,
            paymentMethod = "CASH"
        )
        val items = listOf(
            SaleItemEntity(
                id = 2L,
                transactionId = 102L,
                productId = 1L,
                productName = "Kopi Susu",
                quantity = 2.0,
                price = 25000L,
                purchasePrice = 15000L,
                subtotal = 50000L
            )
        )

        val receiptData = ReceiptMapper.mapFromSale(sale = sale, items = items)

        assertEquals(50000L, receiptData.paymentInfo.totalAmount)
        assertEquals(null, receiptData.paymentInfo.subtotalAmount)
        assertEquals(null, receiptData.paymentInfo.discountAmount)
    }

    /**
     * Test 3: PlainTextReceiptFormatter displays Subtotal and Diskon lines when discount > 0.
     */
    @Test
    fun testPlainTextReceiptFormatterWithDiscount() {
        val sale = SaleTransactionEntity(
            id = 103L,
            transactionNumber = "TRX-103",
            transactionDate = 1700000000000L,
            totalAmount = 90000L,
            discountAmount = 10000L,
            paymentMethod = "CASH"
        )
        val items = listOf(
            SaleItemEntity(
                id = 3L,
                transactionId = 103L,
                productId = 2L,
                productName = "Beras 5kg",
                quantity = 1.0,
                price = 100000L,
                purchasePrice = 90000L,
                subtotal = 100000L
            )
        )

        val receiptData = ReceiptMapper.mapFromSale(sale = sale, items = items, cashGiven = 100000L)
        val plainText58 = PlainTextReceiptFormatter().format(receiptData, ReceiptPaperWidth.WIDTH_58MM)
        val plainText80 = PlainTextReceiptFormatter().format(receiptData, ReceiptPaperWidth.WIDTH_80MM)

        assertTrue("58mm should contain Subtotal", plainText58.contains("Subtotal"))
        assertTrue("58mm should contain Diskon", plainText58.contains("Diskon"))
        assertTrue("58mm should contain TOTAL", plainText58.contains("TOTAL"))
        assertTrue("58mm should contain Rp 90.000", plainText58.contains("90.000"))

        assertTrue("80mm should contain Subtotal", plainText80.contains("Subtotal"))
        assertTrue("80mm should contain Diskon", plainText80.contains("Diskon"))
        assertTrue("80mm should contain TOTAL", plainText80.contains("TOTAL"))
    }

    /**
     * Test 4: PlainTextReceiptFormatter does NOT display Subtotal or Diskon lines when discount is 0.
     */
    @Test
    fun testPlainTextReceiptFormatterZeroDiscount() {
        val sale = SaleTransactionEntity(
            id = 104L,
            transactionNumber = "TRX-104",
            transactionDate = 1700000000000L,
            totalAmount = 100000L,
            discountAmount = 0L,
            paymentMethod = "CASH"
        )
        val items = listOf(
            SaleItemEntity(
                id = 4L,
                transactionId = 104L,
                productId = 2L,
                productName = "Beras 5kg",
                quantity = 1.0,
                price = 100000L,
                purchasePrice = 90000L,
                subtotal = 100000L
            )
        )

        val receiptData = ReceiptMapper.mapFromSale(sale = sale, items = items)
        val plainText = PlainTextReceiptFormatter().format(receiptData, ReceiptPaperWidth.WIDTH_58MM)

        assertFalse("Zero discount should not have Subtotal line", plainText.contains("Subtotal"))
        assertFalse("Zero discount should not have Diskon line", plainText.contains("Diskon"))
        assertTrue("Should have TOTAL line", plainText.contains("TOTAL"))
    }

    /**
     * Test 5: EscPosReceiptFormatter encodes discount lines cleanly.
     */
    @Test
    fun testEscPosReceiptFormatterWithDiscount() {
        val sale = SaleTransactionEntity(
            id = 105L,
            transactionNumber = "TRX-105",
            transactionDate = 1700000000000L,
            totalAmount = 85000L,
            discountAmount = 15000L,
            paymentMethod = "CASH"
        )
        val items = listOf(
            SaleItemEntity(
                id = 5L,
                transactionId = 105L,
                productId = 3L,
                productName = "Minyak Goreng 2L",
                quantity = 2.0,
                price = 50000L,
                purchasePrice = 45000L,
                subtotal = 100000L
            )
        )

        val receiptData = ReceiptMapper.mapFromSale(sale = sale, items = items)
        val escpos58Bytes = EscPosReceiptFormatter().format(receiptData, ReceiptPaperWidth.WIDTH_58MM)
        val escpos80Bytes = EscPosReceiptFormatter().format(receiptData, ReceiptPaperWidth.WIDTH_80MM)

        assertTrue(escpos58Bytes.isNotEmpty())
        assertTrue(escpos80Bytes.isNotEmpty())

        val text58 = String(escpos58Bytes, Charsets.ISO_8859_1)
        assertTrue(text58.contains("Subtotal"))
        assertTrue(text58.contains("Diskon"))
        assertTrue(text58.contains("TOTAL"))
    }
}
