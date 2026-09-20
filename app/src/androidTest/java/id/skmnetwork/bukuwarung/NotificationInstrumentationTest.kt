package id.skmnetwork.bukuwarung

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.local.entity.CustomerEntity
import id.skmnetwork.bukuwarung.data.local.entity.DebtEntity
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.local.entity.SupplierEntity
import id.skmnetwork.bukuwarung.data.local.entity.SupplierPayableEntity
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import id.skmnetwork.bukuwarung.data.preferences.UserSettings
import id.skmnetwork.bukuwarung.notification.NotificationRepository
import id.skmnetwork.bukuwarung.notification.model.AppNotificationPriority
import id.skmnetwork.bukuwarung.notification.model.AppNotificationType
import id.skmnetwork.bukuwarung.ui.navigation.AppScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class NotificationInstrumentationTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var preferencesRepo: UserPreferencesRepository
    private lateinit var notificationRepo: NotificationRepository

    private var categoryId: Long = 0
    private var customerId: Long = 0
    private var supplierId: Long = 0

    @Before
    fun setup() {
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            database = Room.inMemoryDatabaseBuilder(
                context,
                AppDatabase::class.java
            ).allowMainThreadQueries().build()

            preferencesRepo = UserPreferencesRepository(context)
            notificationRepo = NotificationRepository(database, preferencesRepo, "LEGACY_BUSINESS")

            categoryId = database.categoryDao().insertCategory(CategoryEntity(name = "Sembako"))
            customerId = database.customerDao().insertCustomer(
                CustomerEntity(name = "Budi Santoso", phone = "08123456789")
            )
            supplierId = database.supplierDao().insertSupplier(
                SupplierEntity(name = "PT Supplier Jaya", phone = "08987654321")
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testRealRoomLowStockNotification() = runBlocking {
        // 1. Insert product with stock <= minimumStock
        val product = ProductEntity(
            categoryId = categoryId,
            name = "Minyak Bimoli 2L",
            purchasePrice = 28000,
            sellingPrice = 32000,
            stock = 1.0,
            minimumStock = 3.0,
            unit = "pouch"
        )
        val prodId = database.productDao().insertProduct(product)

        val notifs = notificationRepo.notifications.first()
        val lowStockNotif = notifs.find { it.id == "STOCK_LOW_$prodId" }

        assertTrue("Expected low stock notification for Bimoli", lowStockNotif != null)
        assertEquals(AppNotificationType.STOCK_LOW, lowStockNotif?.type)
        assertEquals("Stok Menipis", lowStockNotif?.title)
        assertEquals(AppScreen.PRODUCTS, lowStockNotif?.targetScreen)

        // 2. Restock product -> self-healing (disappears)
        database.productDao().addProductStock(prodId, 10.0, System.currentTimeMillis(), "LEGACY_BUSINESS")
        val healedNotifs = notificationRepo.notifications.first()
        val afterRestock = healedNotifs.find { it.id == "STOCK_LOW_$prodId" }
        assertTrue("Notification should disappear after restock", afterRestock == null)
    }

    @Test
    fun testRealRoomCustomerDebtNotification() = runBlocking {
        val debt = DebtEntity(
            customerId = customerId,
            totalDebt = 150000L,
            paidAmount = 50000L,
            status = "OPEN"
        )
        val debtId = database.debtDao().insertDebt(debt)

        val notifs = notificationRepo.notifications.first()
        val debtNotif = notifs.find { it.id == "DEBT_OPEN_$debtId" }

        assertTrue("Expected debt notification for open debt", debtNotif != null)
        assertEquals(AppNotificationType.DEBT_DUE, debtNotif?.type)
        assertEquals("Piutang Belum Lunas", debtNotif?.title)
        assertTrue(debtNotif?.message?.contains("Budi Santoso") == true)
        assertEquals(AppScreen.CUSTOMERS, debtNotif?.targetScreen)

        // Mark debt as PAID -> notification disappears
        database.debtDao().updateDebt(debt.copy(id = debtId, paidAmount = 150000L, status = "PAID"))
        val afterPaidNotifs = notificationRepo.notifications.first()
        val afterPaid = afterPaidNotifs.find { it.id == "DEBT_OPEN_$debtId" }
        assertTrue("Debt notification should disappear once paid", afterPaid == null)
    }

    @Test
    fun testRealRoomSupplierPayableNotification() = runBlocking {
        val payable = SupplierPayableEntity(
            supplierId = supplierId,
            totalDebt = 750000L,
            paidAmount = 0L,
            status = "OPEN"
        )
        val payableId = database.supplierPayableDao().insertSupplierPayable(payable)

        val notifs = notificationRepo.notifications.first()
        val payableNotif = notifs.find { it.id == "PAYABLE_OPEN_$payableId" }

        assertTrue("Expected payable notification for open supplier debt", payableNotif != null)
        assertEquals(AppNotificationType.PAYABLE_DUE, payableNotif?.type)
        assertEquals("Hutang Supplier", payableNotif?.title)
        assertTrue(payableNotif?.message?.contains("PT Supplier Jaya") == true)
        assertEquals(AppScreen.SUPPLIERS, payableNotif?.targetScreen)

        // Mark payable as PAID -> notification disappears
        database.supplierPayableDao().updateSupplierPayable(payable.copy(id = payableId, paidAmount = 750000L, status = "PAID"))
        val afterPaidNotifs = notificationRepo.notifications.first()
        val afterPaid = afterPaidNotifs.find { it.id == "PAYABLE_OPEN_$payableId" }
        assertTrue("Payable notification should disappear once paid", afterPaid == null)
    }

    @Test
    fun testNotificationDeduplicationAndPriority() = runBlocking {
        // Insert 1 zero stock product, 1 low stock product, 1 debt, 1 payable
        val pZero = database.productDao().insertProduct(
            ProductEntity(categoryId = categoryId, name = "Kecap Manis", purchasePrice = 5000, sellingPrice = 7000, stock = 0.0, minimumStock = 2.0)
        )
        val pLow = database.productDao().insertProduct(
            ProductEntity(categoryId = categoryId, name = "Susu UHT", purchasePrice = 6000, sellingPrice = 8000, stock = 1.0, minimumStock = 3.0)
        )
        val d1 = database.debtDao().insertDebt(
            DebtEntity(customerId = customerId, totalDebt = 50000L, paidAmount = 0L, status = "OPEN")
        )
        val sp1 = database.supplierPayableDao().insertSupplierPayable(
            SupplierPayableEntity(supplierId = supplierId, totalDebt = 100000L, paidAmount = 0L, status = "OPEN")
        )

        val notifs = notificationRepo.notifications.first()
        assertEquals(4, notifs.size)

        // Check deduplication (IDs must be unique)
        val idSet = notifs.map { it.id }.toSet()
        assertEquals(4, idSet.size)

        // Check top priority is the zero-stock item (CRITICAL)
        assertEquals("STOCK_LOW_$pZero", notifs[0].id)
        assertEquals(AppNotificationPriority.CRITICAL, notifs[0].priority)
    }
}


