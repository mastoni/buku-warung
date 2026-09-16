package id.skmnetwork.bukuwarung.notification

import id.skmnetwork.bukuwarung.data.local.dao.DebtWithCustomerItem
import id.skmnetwork.bukuwarung.data.local.dao.PayableWithSupplierItem
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.preferences.UserSettings
import id.skmnetwork.bukuwarung.notification.model.AppNotificationPriority
import id.skmnetwork.bukuwarung.notification.model.AppNotificationType
import id.skmnetwork.bukuwarung.ui.navigation.AppScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRepositoryTest {

    private val defaultSettings = UserSettings(
        businessId = "biz-123",
        lowStockNotificationEnabled = true,
        lowStockAlertEnabled = true
    )

    private fun createProduct(
        id: Long,
        name: String,
        stock: Double,
        minimumStock: Double = 2.0,
        isDeleted: Boolean = false,
        businessId: String = "biz-123",
        updatedAt: Long = 1000L
    ): ProductEntity {
        return ProductEntity(
            id = id,
            categoryId = 1L,
            name = name,
            purchasePrice = 1000L,
            sellingPrice = 2000L,
            stock = stock,
            minimumStock = minimumStock,
            unit = "pcs",
            isDeleted = isDeleted,
            businessId = businessId,
            updatedAt = updatedAt
        )
    }

    private fun createDebt(
        debtId: Long,
        customerName: String,
        totalDebt: Long,
        paidAmount: Long,
        status: String = "OPEN",
        createdAt: Long = 2000L
    ): DebtWithCustomerItem {
        return DebtWithCustomerItem(
            debtId = debtId,
            uuid = "debt-uuid-$debtId",
            customerId = 10L,
            saleTransactionId = 100L,
            customerName = customerName,
            customerPhone = "08123456789",
            customerAddress = "Jl. Merdeka",
            transactionNumber = "TRX-$debtId",
            totalDebt = totalDebt,
            paidAmount = paidAmount,
            status = status,
            createdAt = createdAt,
            updatedAt = createdAt
        )
    }

    private fun createPayable(
        payableId: Long,
        supplierName: String,
        totalDebt: Long,
        paidAmount: Long,
        status: String = "OPEN",
        createdAt: Long = 3000L
    ): PayableWithSupplierItem {
        return PayableWithSupplierItem(
            payableId = payableId,
            uuid = "payable-uuid-$payableId",
            supplierId = 20L,
            purchaseTransactionId = 200L,
            supplierName = supplierName,
            supplierPhone = "08987654321",
            supplierAddress = "Jl. Supplier",
            totalDebt = totalDebt,
            paidAmount = paidAmount,
            status = status,
            createdAt = createdAt,
            updatedAt = createdAt
        )
    }

    @Test
    fun testNoNotificationWhenHealthy() {
        val products = listOf(
            createProduct(id = 1, name = "Kopi ABC", stock = 10.0, minimumStock = 2.0)
        )
        val debts = emptyList<DebtWithCustomerItem>()
        val payables = emptyList<PayableWithSupplierItem>()

        val notifs = NotificationRepository.computeNotifications(
            products = products,
            debts = debts,
            payables = payables,
            userSettings = defaultSettings,
            readIds = emptySet()
        )

        assertTrue(notifs.isEmpty())
    }

    @Test
    fun testLowStockDetected() {
        val products = listOf(
            createProduct(id = 1, name = "Minyak Goreng 1L", stock = 2.0, minimumStock = 5.0)
        )

        val notifs = NotificationRepository.computeNotifications(
            products = products,
            debts = emptyList(),
            payables = emptyList(),
            userSettings = defaultSettings,
            readIds = emptySet()
        )

        assertEquals(1, notifs.size)
        val notif = notifs[0]
        assertEquals("STOCK_LOW_1", notif.id)
        assertEquals(AppNotificationType.STOCK_LOW, notif.type)
        assertEquals("Stok Menipis", notif.title)
        assertTrue(notif.message.contains("Minyak Goreng 1L"))
        assertEquals(AppScreen.PRODUCTS, notif.targetScreen)
        assertEquals(1L, notif.targetId)
        assertFalse(notif.isRead)
    }

    @Test
    fun testZeroStockCriticalPriority() {
        val products = listOf(
            createProduct(id = 2, name = "Beras 5kg", stock = 0.0, minimumStock = 3.0)
        )

        val notifs = NotificationRepository.computeNotifications(
            products = products,
            debts = emptyList(),
            payables = emptyList(),
            userSettings = defaultSettings,
            readIds = emptySet()
        )

        assertEquals(1, notifs.size)
        assertEquals(AppNotificationPriority.CRITICAL, notifs[0].priority)
        assertEquals("Stok Habis", notifs[0].title)
    }

    @Test
    fun testStockAboveMinimumNoNotification() {
        val products = listOf(
            createProduct(id = 1, name = "Gula Pasir", stock = 10.0, minimumStock = 2.0)
        )

        val notifs = NotificationRepository.computeNotifications(
            products = products,
            debts = emptyList(),
            payables = emptyList(),
            userSettings = defaultSettings,
            readIds = emptySet()
        )

        assertTrue(notifs.isEmpty())
    }

    @Test
    fun testDeletedProductNoNotification() {
        val products = listOf(
            createProduct(id = 1, name = "Produk Dihapus", stock = 0.0, minimumStock = 2.0, isDeleted = true)
        )

        val notifs = NotificationRepository.computeNotifications(
            products = products,
            debts = emptyList(),
            payables = emptyList(),
            userSettings = defaultSettings,
            readIds = emptySet()
        )

        assertTrue(notifs.isEmpty())
    }

    @Test
    fun testOpenDebtDetected() {
        val debts = listOf(
            createDebt(debtId = 5, customerName = "Pak Budi", totalDebt = 100000L, paidAmount = 25000L)
        )

        val notifs = NotificationRepository.computeNotifications(
            products = emptyList(),
            debts = debts,
            payables = emptyList(),
            userSettings = defaultSettings,
            readIds = emptySet()
        )

        assertEquals(1, notifs.size)
        val notif = notifs[0]
        assertEquals("DEBT_OPEN_5", notif.id)
        assertEquals(AppNotificationType.DEBT_DUE, notif.type)
        assertEquals("Piutang Belum Lunas", notif.title)
        assertTrue(notif.message.contains("Pak Budi"))
        assertEquals(id.skmnetwork.bukuwarung.util.formatRupiah(75000L), notif.amountFormatted)
        assertEquals(AppScreen.CUSTOMERS, notif.targetScreen)
    }

    @Test
    fun testPaidDebtNoNotification() {
        val debts = listOf(
            createDebt(debtId = 5, customerName = "Pak Budi", totalDebt = 100000L, paidAmount = 100000L, status = "PAID")
        )

        val notifs = NotificationRepository.computeNotifications(
            products = emptyList(),
            debts = debts,
            payables = emptyList(),
            userSettings = defaultSettings,
            readIds = emptySet()
        )

        assertTrue(notifs.isEmpty())
    }

    @Test
    fun testOpenPayableDetected() {
        val payables = listOf(
            createPayable(payableId = 8, supplierName = "PT Sumber Makmur", totalDebt = 500000L, paidAmount = 0L)
        )

        val notifs = NotificationRepository.computeNotifications(
            products = emptyList(),
            debts = emptyList(),
            payables = payables,
            userSettings = defaultSettings,
            readIds = emptySet()
        )

        assertEquals(1, notifs.size)
        val notif = notifs[0]
        assertEquals("PAYABLE_OPEN_8", notif.id)
        assertEquals(AppNotificationType.PAYABLE_DUE, notif.type)
        assertEquals("Hutang Supplier", notif.title)
        assertTrue(notif.message.contains("PT Sumber Makmur"))
        assertEquals(id.skmnetwork.bukuwarung.util.formatRupiah(500000L), notif.amountFormatted)
        assertEquals(AppScreen.SUPPLIERS, notif.targetScreen)
    }

    @Test
    fun testPaidPayableNoNotification() {
        val payables = listOf(
            createPayable(payableId = 8, supplierName = "PT Sumber Makmur", totalDebt = 500000L, paidAmount = 500000L, status = "PAID")
        )

        val notifs = NotificationRepository.computeNotifications(
            products = emptyList(),
            debts = emptyList(),
            payables = payables,
            userSettings = defaultSettings,
            readIds = emptySet()
        )

        assertTrue(notifs.isEmpty())
    }

    @Test
    fun testMultipleNotificationsAndSorting() {
        val products = listOf(
            createProduct(id = 1, name = "Kopi", stock = 1.0, minimumStock = 2.0, updatedAt = 100L),
            createProduct(id = 2, name = "Gula", stock = 0.0, minimumStock = 2.0, updatedAt = 200L) // CRITICAL
        )
        val debts = listOf(
            createDebt(debtId = 10, customerName = "Ahmad", totalDebt = 50000L, paidAmount = 0L, createdAt = 300L) // HIGH
        )
        val payables = listOf(
            createPayable(payableId = 20, supplierName = "Distributor A", totalDebt = 200000L, paidAmount = 0L, createdAt = 400L) // MEDIUM
        )

        val notifs = NotificationRepository.computeNotifications(
            products = products,
            debts = debts,
            payables = payables,
            userSettings = defaultSettings,
            readIds = emptySet()
        )

        assertEquals(4, notifs.size)
        // Order should be: CRITICAL (Gula: stock=0) -> HIGH (Ahmad debt / Kopi low stock) -> MEDIUM (Distributor A)
        assertEquals("STOCK_LOW_2", notifs[0].id)
        assertEquals(AppNotificationPriority.CRITICAL, notifs[0].priority)

        // Verify all 4 types/IDs are present
        val ids = notifs.map { it.id }.toSet()
        assertTrue(ids.contains("STOCK_LOW_1"))
        assertTrue(ids.contains("STOCK_LOW_2"))
        assertTrue(ids.contains("DEBT_OPEN_10"))
        assertTrue(ids.contains("PAYABLE_OPEN_20"))
    }

    @Test
    fun testReadUnreadState() {
        val products = listOf(
            createProduct(id = 1, name = "Kopi", stock = 1.0, minimumStock = 2.0)
        )
        val debts = listOf(
            createDebt(debtId = 10, customerName = "Ahmad", totalDebt = 50000L, paidAmount = 0L)
        )

        val readIds = setOf("STOCK_LOW_1")

        val notifs = NotificationRepository.computeNotifications(
            products = products,
            debts = debts,
            payables = emptyList(),
            userSettings = defaultSettings,
            readIds = readIds
        )

        assertEquals(2, notifs.size)
        val readNotif = notifs.first { it.id == "STOCK_LOW_1" }
        val unreadNotif = notifs.first { it.id == "DEBT_OPEN_10" }

        assertTrue(readNotif.isRead)
        assertFalse(unreadNotif.isRead)

        val unreadCount = notifs.count { !it.isRead }
        assertEquals(1, unreadCount)
    }

    @Test
    fun testBusinessIdIsolation() {
        val products = listOf(
            createProduct(id = 1, name = "Kopi Toko Saya", stock = 1.0, minimumStock = 2.0, businessId = "biz-123"),
            createProduct(id = 2, name = "Kopi Toko Lain", stock = 0.0, minimumStock = 2.0, businessId = "biz-OTHER")
        )

        val notifs = NotificationRepository.computeNotifications(
            products = products,
            debts = emptyList(),
            payables = emptyList(),
            userSettings = defaultSettings, // businessId = "biz-123"
            readIds = emptySet()
        )

        assertEquals(1, notifs.size)
        assertEquals("STOCK_LOW_1", notifs[0].id)
    }

    @Test
    fun testLowStockNotificationDisabledInSettings() {
        val products = listOf(
            createProduct(id = 1, name = "Kopi", stock = 1.0, minimumStock = 2.0)
        )
        val settings = defaultSettings.copy(lowStockNotificationEnabled = false)

        val notifs = NotificationRepository.computeNotifications(
            products = products,
            debts = emptyList(),
            payables = emptyList(),
            userSettings = settings,
            readIds = emptySet()
        )

        assertTrue(notifs.isEmpty())
    }

    @Test
    fun testSelfHealingWhenRestockedOrPaid() {
        // Initial state: 1 low stock product, 1 open debt
        val initialProducts = listOf(createProduct(id = 1, name = "Kopi", stock = 1.0, minimumStock = 2.0))
        val initialDebts = listOf(createDebt(debtId = 10, customerName = "Ahmad", totalDebt = 50000L, paidAmount = 0L))

        val initialNotifs = NotificationRepository.computeNotifications(
            products = initialProducts,
            debts = initialDebts,
            payables = emptyList(),
            userSettings = defaultSettings,
            readIds = emptySet()
        )
        assertEquals(2, initialNotifs.size)

        // After restock & debt payment
        val restockedProducts = listOf(createProduct(id = 1, name = "Kopi", stock = 10.0, minimumStock = 2.0))
        val paidDebts = listOf(createDebt(debtId = 10, customerName = "Ahmad", totalDebt = 50000L, paidAmount = 50000L, status = "PAID"))

        val healedNotifs = NotificationRepository.computeNotifications(
            products = restockedProducts,
            debts = paidDebts,
            payables = emptyList(),
            userSettings = defaultSettings,
            readIds = emptySet()
        )
        assertEquals(0, healedNotifs.size)
    }
}
