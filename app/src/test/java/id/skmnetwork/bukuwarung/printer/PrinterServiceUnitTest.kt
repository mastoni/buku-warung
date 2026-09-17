package id.skmnetwork.bukuwarung.printer

import id.skmnetwork.bukuwarung.domain.receipt.ReceiptData
import id.skmnetwork.bukuwarung.domain.receipt.ReceiptItem
import id.skmnetwork.bukuwarung.domain.receipt.ReceiptPaperWidth
import id.skmnetwork.bukuwarung.domain.receipt.ReceiptPaymentInfo
import id.skmnetwork.bukuwarung.domain.receipt.ShopProfile
import id.skmnetwork.bukuwarung.printer.connection.MockPrinterConnection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrinterServiceUnitTest {

    private fun createSampleReceipt(): ReceiptData {
        return ReceiptData(
            receiptNumber = "TRX-TEST-001",
            transactionUuid = "uuid-test",
            dateTimeMillis = 1773738000000L,
            shopProfile = ShopProfile(shopName = "Warung Unit Test"),
            cashierName = "Tester",
            items = listOf(
                ReceiptItem(name = "Teh Manis", quantity = 1.0, unit = "gelas", price = 3000L, subtotal = 3000L)
            ),
            paymentInfo = ReceiptPaymentInfo(method = "CASH", totalAmount = 3000L, payAmount = 5000L, changeAmount = 2000L)
        )
    }

    @Test
    fun testPrinterServiceLifecycle() = runBlocking {
        val printerService = PrinterService()
        assertNull(printerService.getConnection())
        assertFalse(printerService.isConnected)

        val mock = MockPrinterConnection(mockAddress = "AA:BB:CC:DD:EE:FF")
        printerService.setConnection(mock)
        assertEquals(mock, printerService.getConnection())
        assertFalse(printerService.isConnected)

        val connectResult = printerService.connect()
        assertTrue(connectResult.isSuccess)
        assertTrue(printerService.isConnected)

        printerService.disconnect()
        assertFalse(printerService.isConnected)
    }

    @Test
    fun testPrintReceiptSuccess() = runBlocking {
        val mock = MockPrinterConnection()
        val printerService = PrinterService(activeConnection = mock)
        val receipt = createSampleReceipt()

        val result = printerService.printReceipt(receipt, autoConnect = true)
        assertTrue("Print receipt should succeed", result.isSuccess)
        assertTrue("Mock connection must receive byte chunks", mock.receivedByteChunks.isNotEmpty())
        assertTrue("Sent text must contain shop name", mock.fullPrintedText.contains("Warung Unit Test"))
    }

    @Test
    fun testPrintTestReceiptSuccess() = runBlocking {
        val mock = MockPrinterConnection()
        val printerService = PrinterService(activeConnection = mock)

        val result = printerService.printTestReceipt("Toko Sukses", ReceiptPaperWidth.WIDTH_58MM)
        assertTrue(result.isSuccess)
        assertTrue(mock.fullPrintedText.contains("Toko Sukses"))
        assertTrue(mock.fullPrintedText.contains("TEST PRINT"))
    }

    @Test
    fun testPrintingFailureWhenNoConnectionConfigured() = runBlocking {
        val printerService = PrinterService()
        val receipt = createSampleReceipt()

        val result = printerService.printReceipt(receipt)
        assertTrue(result.isFailure)
        assertEquals("Printer belum dikonfigurasi", result.exceptionOrNull()?.message)
    }

    @Test
    fun testPrintingFailureWhenConnectionFails() = runBlocking {
        val mock = MockPrinterConnection()
        mock.simulateErrorOnConnect = true
        val printerService = PrinterService(activeConnection = mock)
        val receipt = createSampleReceipt()

        val result = printerService.printReceipt(receipt, autoConnect = true)
        assertTrue(result.isFailure)
        assertFalse(printerService.isConnected)
    }

    @Test
    fun testPrintingFailureWhenTransmissionFails() = runBlocking {
        val mock = MockPrinterConnection()
        mock.simulateErrorOnSend = true
        val printerService = PrinterService(activeConnection = mock)
        val receipt = createSampleReceipt()

        val result = printerService.printReceipt(receipt, autoConnect = true)
        assertTrue(result.isFailure)
    }

    @Test
    fun testPaperWidthConfigurationAndPreview() {
        val printerService = PrinterService()
        assertEquals(ReceiptPaperWidth.WIDTH_58MM, printerService.getPaperWidth())

        printerService.setPaperWidth(ReceiptPaperWidth.WIDTH_80MM)
        assertEquals(ReceiptPaperWidth.WIDTH_80MM, printerService.getPaperWidth())

        val receipt = createSampleReceipt()
        val preview58 = printerService.formatPreview(receipt, ReceiptPaperWidth.WIDTH_58MM)
        assertTrue(preview58.contains("Warung Unit Test"))
        assertTrue(preview58.contains("Teh Manis"))

        val preview80 = printerService.formatPreview(receipt, ReceiptPaperWidth.WIDTH_80MM)
        assertTrue(preview80.contains("Warung Unit Test"))
    }

    @Test
    fun testIsMatchingConnection() {
        val mock = MockPrinterConnection(mockAddress = "11:22:33:44:55:66")
        val printerService = PrinterService(activeConnection = mock)

        assertTrue(printerService.isMatchingConnection("MOCK", "11:22:33:44:55:66"))
        assertFalse(printerService.isMatchingConnection("BLUETOOTH", "11:22:33:44:55:66"))
        assertFalse(printerService.isMatchingConnection("MOCK", "99:99:99:99:99:99"))
    }

    @Test
    fun testClearConnection() = runBlocking {
        val mock = MockPrinterConnection()
        val printerService = PrinterService(activeConnection = mock)
        mock.connect()
        assertTrue(printerService.isConnected)

        printerService.clearConnection()
        assertNull(printerService.getConnection())
        assertFalse(printerService.isConnected)
    }
}
