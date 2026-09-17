package id.skmnetwork.bukuwarung.printer

import id.skmnetwork.bukuwarung.data.local.entity.CustomerEntity
import id.skmnetwork.bukuwarung.data.local.entity.DebtEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleTransactionEntity
import id.skmnetwork.bukuwarung.data.preferences.UserSettings
import id.skmnetwork.bukuwarung.domain.receipt.ReceiptMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReceiptMapperUnitTest {

    @Test
    fun testMapFromSaleCashWithChange() {
        val sale = SaleTransactionEntity(
            id = 1L,
            uuid = "sale-uuid-001",
            transactionNumber = "TRX-001",
            transactionDate = 1773738000000L,
            totalAmount = 50000L,
            paymentMethod = "CASH"
        )
        val items = listOf(
            SaleItemEntity(
                id = 10L,
                transactionId = 1L,
                productId = 100L,
                productName = "Beras 5kg",
                quantity = 1.0,
                price = 50000L,
                subtotal = 50000L
            )
        )
        val userSettings = UserSettings(
            shopName = "Warung Ibu Siti",
            ownerName = "Siti",
            phone = "0811111111",
            address = "Pasar Tradisional No 5",
            deviceName = "HP Kasir"
        )

        val receipt = ReceiptMapper.mapFromSale(
            sale = sale,
            items = items,
            userSettings = userSettings,
            cashGiven = 70000L
        )

        assertEquals("TRX-001", receipt.receiptNumber)
        assertEquals("sale-uuid-001", receipt.transactionUuid)
        assertEquals(1773738000000L, receipt.dateTimeMillis)
        assertEquals("HP Kasir", receipt.cashierName)
        assertEquals("Warung Ibu Siti", receipt.shopProfile.shopName)
        assertEquals("Pasar Tradisional No 5", receipt.shopProfile.address)
        assertEquals(1, receipt.items.size)
        assertEquals("Beras 5kg", receipt.items[0].name)
        assertEquals(1.0, receipt.items[0].quantity, 0.001)

        assertEquals("CASH", receipt.paymentInfo.method)
        assertEquals(50000L, receipt.paymentInfo.totalAmount)
        assertEquals(70000L, receipt.paymentInfo.payAmount)
        assertEquals(20000L, receipt.paymentInfo.changeAmount)
    }

    @Test
    fun testMapFromSaleCreditWithCustomerAndDebt() {
        val sale = SaleTransactionEntity(
            id = 2L,
            uuid = "sale-uuid-002",
            transactionNumber = "TRX-002",
            transactionDate = 1773738000000L,
            totalAmount = 120000L,
            paymentMethod = "CREDIT",
            customerId = 55L
        )
        val items = listOf(
            SaleItemEntity(
                id = 20L,
                transactionId = 2L,
                productId = 200L,
                productName = "Gula Pasir",
                quantity = 2.0,
                price = 15000L,
                subtotal = 30000L
            ),
            SaleItemEntity(
                id = 21L,
                transactionId = 2L,
                productId = 201L,
                productName = "Minyak Goreng",
                quantity = 3.0,
                price = 30000L,
                subtotal = 90000L
            )
        )
        val customer = CustomerEntity(
            id = 55L,
            name = "Pak Rahmat",
            phone = "0822222222"
        )
        val debt = DebtEntity(
            id = 99L,
            customerId = 55L,
            saleTransactionId = 2L,
            totalDebt = 120000L,
            paidAmount = 20000L,
            status = "OPEN"
        )

        val receipt = ReceiptMapper.mapFromSale(
            sale = sale,
            items = items,
            customer = customer,
            debt = debt
        )

        assertEquals("CREDIT", receipt.paymentInfo.method)
        assertEquals("Pak Rahmat", receipt.paymentInfo.customerName)
        assertEquals(120000L, receipt.paymentInfo.totalAmount)
        assertNull(receipt.paymentInfo.payAmount)
        assertNull(receipt.paymentInfo.changeAmount)
        assertEquals(100000L, receipt.paymentInfo.remainingDebt)
    }

    @Test
    fun testMapFromSaleAdaptiveProfileTerminology() {
        val sale = SaleTransactionEntity(
            id = 3L,
            uuid = "sale-uuid-003",
            transactionNumber = "TRX-003",
            transactionDate = 1773738000000L,
            totalAmount = 25000L,
            paymentMethod = "QRIS"
        )
        val items = listOf(
            SaleItemEntity(
                id = 30L,
                transactionId = 3L,
                productId = 300L,
                productName = "Jasa Servis Sepeda",
                quantity = 1.0,
                price = 25000L,
                subtotal = 25000L
            )
        )
        val userSettings = UserSettings(
            shopName = "Bengkel Sepeda Maju",
            primaryBusinessType = "BENGKEL_REPARASI",
            secondaryActivities = setOf("ACTIVITY_SERVICES")
        )

        val receipt = ReceiptMapper.mapFromSale(
            sale = sale,
            items = items,
            userSettings = userSettings
        )

        assertEquals("Pelanggan", receipt.shopProfile.customerLabel)
        assertEquals("QRIS", receipt.paymentInfo.method)
        assertEquals(25000L, receipt.paymentInfo.totalAmount)
    }

    @Test
    fun testMapFromSaleNullSettingsDefaults() {
        val sale = SaleTransactionEntity(
            id = 4L,
            uuid = "sale-uuid-004",
            transactionNumber = "TRX-004",
            transactionDate = 1773738000000L,
            totalAmount = 10000L,
            paymentMethod = "CASH"
        )
        val items = emptyList<SaleItemEntity>()

        val receipt = ReceiptMapper.mapFromSale(
            sale = sale,
            items = items,
            userSettings = null
        )

        assertNotNull(receipt)
        assertEquals("Warung Saya", receipt.shopProfile.shopName)
        assertEquals("Kasir", receipt.cashierName)
    }
}
