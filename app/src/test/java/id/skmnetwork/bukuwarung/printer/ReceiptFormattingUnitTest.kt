package id.skmnetwork.bukuwarung.printer

import id.skmnetwork.bukuwarung.domain.receipt.ReceiptData
import id.skmnetwork.bukuwarung.domain.receipt.ReceiptItem
import id.skmnetwork.bukuwarung.domain.receipt.ReceiptPaperWidth
import id.skmnetwork.bukuwarung.domain.receipt.ReceiptPaymentInfo
import id.skmnetwork.bukuwarung.domain.receipt.ShopProfile
import id.skmnetwork.bukuwarung.printer.escpos.EscPosBuilder
import id.skmnetwork.bukuwarung.printer.escpos.EscPosCommands
import id.skmnetwork.bukuwarung.printer.escpos.EscPosReceiptFormatter
import id.skmnetwork.bukuwarung.printer.escpos.PlainTextReceiptFormatter
import id.skmnetwork.bukuwarung.printer.escpos.ReceiptTextFormatterUtils
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptFormattingUnitTest {

    @Test
    fun testReceiptTextFormatterUtilsRupiah() {
        assertEquals("Rp 0", ReceiptTextFormatterUtils.formatRupiah(0))
        assertEquals("Rp 10.000", ReceiptTextFormatterUtils.formatRupiah(10000))
        assertEquals("Rp 1.250.000", ReceiptTextFormatterUtils.formatRupiah(1250000))
    }

    @Test
    fun testReceiptTextFormatterUtilsQuantity() {
        assertEquals("1", ReceiptTextFormatterUtils.formatQuantity(1.0, "pcs"))
        assertEquals("2.50 kg", ReceiptTextFormatterUtils.formatQuantity(2.5, "kg"))
        assertEquals("5 liter", ReceiptTextFormatterUtils.formatQuantity(5.0, "liter"))
    }

    @Test
    fun testReceiptTextFormatterUtilsDivider() {
        assertEquals("--------------------------------", ReceiptTextFormatterUtils.dividerLine('-', 32))
        assertEquals("================================================", ReceiptTextFormatterUtils.dividerLine('=', 48))
    }

    @Test
    fun testReceiptTextFormatterUtilsTwoColumns() {
        val result = ReceiptTextFormatterUtils.twoColumns("TOTAL", "Rp 50.000", 32)
        assertEquals(32, result.length)
        assertTrue(result.startsWith("TOTAL"))
        assertTrue(result.endsWith("Rp 50.000"))

        // Very long left text should be truncated safely without exceeding totalWidth
        val longResult = ReceiptTextFormatterUtils.twoColumns("This is an extremely long item name that exceeds width", "Rp 100.000", 32)
        assertEquals(32, longResult.length)
        assertTrue(longResult.endsWith("Rp 100.000"))
    }

    @Test
    fun testReceiptTextFormatterUtilsWrapText() {
        val shortText = "Kopi Susu"
        val wrappedShort = ReceiptTextFormatterUtils.wrapText(shortText, 32)
        assertEquals(1, wrappedShort.size)
        assertEquals("Kopi Susu", wrappedShort[0])

        val longText = "Beras Rojolele Super Premium Pulen Wangi 5kg Karung Kemasan Baru"
        val wrappedLong = ReceiptTextFormatterUtils.wrapText(longText, 20)
        assertTrue(wrappedLong.size > 1)
        wrappedLong.forEach { line ->
            assertTrue("Line '$line' must not exceed 20 characters", line.length <= 20)
        }

        val unbreakableWord = "SUPEREXTRALONGCATEGORYNAMEWITHOUTANYSPACES"
        val wrappedUnbreakable = ReceiptTextFormatterUtils.wrapText(unbreakableWord, 10)
        assertTrue(wrappedUnbreakable.size >= 4)
        wrappedUnbreakable.forEach { line ->
            assertTrue(line.length <= 10)
        }
    }

    @Test
    fun testPlainTextReceiptFormatter58mmCash() {
        val receipt = ReceiptData(
            receiptNumber = "TRX-2026-001",
            transactionUuid = "uuid-1234",
            dateTimeMillis = 1773738000000L,
            shopProfile = ShopProfile(
                shopName = "Toko Kelontong Berkah",
                ownerName = "Pak Budi",
                phone = "081234567890",
                address = "Jl. Merdeka No. 10",
                footerMessage = "Terima kasih atas kunjungan Anda!",
                customerLabel = "Pelanggan",
                cashierLabel = "Kasir",
                transactionNumberLabel = "No"
            ),
            cashierName = "Budi",
            items = listOf(
                ReceiptItem(name = "Minyak Goreng 2L", quantity = 2.0, unit = "pch", price = 35000L, subtotal = 70000L),
                ReceiptItem(name = "Gula Pasir 1kg", quantity = 1.0, unit = "kg", price = 17500L, subtotal = 17500L)
            ),
            paymentInfo = ReceiptPaymentInfo(
                method = "CASH",
                totalAmount = 87500L,
                payAmount = 100000L,
                changeAmount = 12500L,
                customerName = "Ibu Siti"
            )
        )

        val formatter = PlainTextReceiptFormatter()
        val text = formatter.format(receipt, ReceiptPaperWidth.WIDTH_58MM)

        assertTrue(text.contains("Toko Kelontong Berkah"))
        assertTrue(text.contains("Jl. Merdeka No. 10"))
        assertTrue(text.contains("Telp: 081234567890"))
        assertTrue(text.contains("No: TRX-2026-001"))
        assertTrue(text.contains("Kasir: Budi"))
        assertTrue(text.contains("Pelanggan: Ibu Siti"))
        assertTrue(text.contains("Minyak Goreng 2L"))
        assertTrue(text.contains("Gula Pasir 1kg"))
        assertTrue(text.contains("TOTAL"))
        assertTrue(text.contains("Bayar Tunai"))
        assertTrue(text.contains("Terima kasih"))
        assertTrue(text.contains("kunjungan"))
    }

    @Test
    fun testPlainTextReceiptFormatter80mmCreditDebt() {
        val receipt = ReceiptData(
            receiptNumber = "TRX-2026-002",
            transactionUuid = "uuid-5678",
            dateTimeMillis = 1773738000000L,
            shopProfile = ShopProfile(
                shopName = "Bengkel Motor Sejahtera",
                customerLabel = "Klien",
                cashierLabel = "Mekanik"
            ),
            cashierName = "Agus",
            items = listOf(
                ReceiptItem(name = "Oli Mesin Matic 0.8L", quantity = 1.0, unit = "btl", price = 55000L, subtotal = 55000L),
                ReceiptItem(name = "Jasa Servis Ringan", quantity = 1.0, unit = "jasa", price = 30000L, subtotal = 30000L)
            ),
            paymentInfo = ReceiptPaymentInfo(
                method = "CREDIT",
                totalAmount = 85000L,
                customerName = "Pak Joko",
                remainingDebt = 85000L
            )
        )

        val formatter = PlainTextReceiptFormatter()
        val text = formatter.format(receipt, ReceiptPaperWidth.WIDTH_80MM)

        assertTrue(text.contains("Bengkel Motor Sejahtera"))
        assertTrue(text.contains("Mekanik: Agus"))
        assertTrue(text.contains("Klien: Pak Joko"))
        assertTrue(text.contains("Metode Bayar"))
        assertTrue(text.contains("CREDIT"))
        assertTrue(text.contains("Sisa Hutang"))
        assertTrue(text.contains("Rp 85.000"))
    }

    @Test
    fun testEscPosCommandsAndBuilderByteIntegrity() {
        val builder = EscPosBuilder()
        val bytes = builder
            .initialize()
            .alignCenter()
            .bold(true)
            .textLine("WARUNG TEST")
            .bold(false)
            .alignLeft()
            .textLine("Item: Kopi")
            .lineFeed(2)
            .cutPaper(partial = true)
            .build()

        assertTrue("Byte array must not be empty", bytes.isNotEmpty())
        // Starts with ESC @ (INIT)
        assertEquals(EscPosCommands.ESC, bytes[0])
        assertEquals(0x40.toByte(), bytes[1])

        // Verify custom drawer kick command
        val drawerKick = EscPosCommands.DRAWER_KICK
        assertEquals(5, drawerKick.size)
        assertEquals(EscPosCommands.ESC, drawerKick[0])
        assertEquals('p'.code.toByte(), drawerKick[1])

        // Verify feedLines
        val feed3 = EscPosCommands.feedLines(3)
        assertEquals(3, feed3.size)
        assertEquals(EscPosCommands.ESC, feed3[0])
        assertEquals('d'.code.toByte(), feed3[1])
        assertEquals(3.toByte(), feed3[2])
    }

    @Test
    fun testEscPosReceiptFormatterOutput() {
        val receipt = ReceiptData(
            receiptNumber = "TRX-2026-003",
            transactionUuid = "uuid-9999",
            dateTimeMillis = 1773738000000L,
            shopProfile = ShopProfile(shopName = "Warung Kopi Mantap"),
            cashierName = "Kasir 1",
            items = listOf(
                ReceiptItem(name = "Kopi Hitam", quantity = 2.0, unit = "cangkir", price = 4000L, subtotal = 8000L)
            ),
            paymentInfo = ReceiptPaymentInfo(
                method = "QRIS",
                totalAmount = 8000L
            )
        )

        val escPosFormatter = EscPosReceiptFormatter()
        val bytes58 = escPosFormatter.format(receipt, ReceiptPaperWidth.WIDTH_58MM)
        val text58 = String(bytes58, Charsets.ISO_8859_1)

        assertTrue(text58.contains("Warung Kopi Mantap"))
        assertTrue(text58.contains("TRX-2026-003"))
        assertTrue(text58.contains("Kopi Hitam"))
        assertTrue(text58.contains("QRIS"))
        assertTrue(text58.contains("Buku Warung App"))

        val bytes80 = escPosFormatter.format(receipt, ReceiptPaperWidth.WIDTH_80MM)
        assertTrue(bytes80.isNotEmpty())

        val testReceiptBytes = escPosFormatter.formatTestReceipt("Toko Uji", ReceiptPaperWidth.WIDTH_58MM)
        val testText = String(testReceiptBytes, Charsets.ISO_8859_1)
        assertTrue(testText.contains("Toko Uji"))
        assertTrue(testText.contains("TEST PRINT"))
        assertTrue(testText.contains("TEST PRINT BERHASIL"))
    }
}
