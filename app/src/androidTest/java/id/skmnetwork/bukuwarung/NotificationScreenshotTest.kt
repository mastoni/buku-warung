package id.skmnetwork.bukuwarung

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.local.entity.CustomerEntity
import id.skmnetwork.bukuwarung.data.local.entity.DebtEntity
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.local.entity.SupplierEntity
import id.skmnetwork.bukuwarung.data.local.entity.SupplierPayableEntity
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import id.skmnetwork.bukuwarung.data.preferences.UserSettings
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import id.skmnetwork.bukuwarung.notification.NotificationRepository
import id.skmnetwork.bukuwarung.notification.NotificationViewModel
import id.skmnetwork.bukuwarung.ui.home.HomeScreen
import id.skmnetwork.bukuwarung.ui.notification.NotificationCenterScreen
import id.skmnetwork.bukuwarung.ui.product.ProductViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class NotificationScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var prefsRepo: UserPreferencesRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var notificationRepo: NotificationRepository

    private var categoryId: Long = 0
    private var customerId: Long = 0
    private var supplierId: Long = 0

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        prefsRepo = UserPreferencesRepository(context)
        productRepository = ProductRepository(database, "LEGACY_BUSINESS")
        notificationRepo = NotificationRepository(database, prefsRepo, "LEGACY_BUSINESS")

        categoryId = database.categoryDao().insertCategory(CategoryEntity(name = "Sembako"))
        customerId = database.customerDao().insertCustomer(CustomerEntity(name = "Budi Santoso", phone = "08123456789"))
        supplierId = database.supplierDao().insertSupplier(SupplierEntity(name = "PT Sumber Berkah", phone = "08987654321"))
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun saveScreenshot(filename: String) {
        try {
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            val file = File(context.cacheDir, filename)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (_: Exception) {
            // Screenshot capture helper fallback
        }
    }

    @Test
    fun captureA_HomeNoNotification() = runBlocking {
        // Healthy stock
        database.productDao().insertProduct(
            ProductEntity(categoryId = categoryId, name = "Kopi Kapal Api", purchasePrice = 1500, sellingPrice = 2000, stock = 20.0, minimumStock = 5.0)
        )

        val productVm = ProductViewModel(productRepository)

        composeTestRule.setContent {
            HomeScreen(
                viewModel = productVm,
                userSettings = UserSettings(shopName = "Toko Berkah Mandiri"),
                unreadNotificationCount = 0,
                onNavigate = {}
            )
        }
        composeTestRule.waitForIdle()
        saveScreenshot("A_HOME_NO_NOTIFICATION.png")
    }

    @Test
    fun captureB_HomeWithNotificationBadge() = runBlocking {
        // Low stock item triggers unread notification
        database.productDao().insertProduct(
            ProductEntity(categoryId = categoryId, name = "Minyak Goreng 1L", purchasePrice = 14000, sellingPrice = 16000, stock = 1.0, minimumStock = 5.0)
        )

        val productVm = ProductViewModel(productRepository)

        composeTestRule.setContent {
            HomeScreen(
                viewModel = productVm,
                userSettings = UserSettings(shopName = "Toko Berkah Mandiri"),
                unreadNotificationCount = 3,
                onNavigate = {}
            )
        }
        composeTestRule.waitForIdle()
        saveScreenshot("B_HOME_WITH_NOTIFICATION_BADGE.png")
    }

    @Test
    fun captureC_NotificationCenterWithRealBusinessData() = runBlocking {
        // 1. Zero stock product (CRITICAL)
        database.productDao().insertProduct(
            ProductEntity(categoryId = categoryId, name = "Adaptor 12V 1A", purchasePrice = 25000, sellingPrice = 35000, stock = 0.0, minimumStock = 2.0, unit = "pcs")
        )
        // 2. Low stock product (HIGH)
        database.productDao().insertProduct(
            ProductEntity(categoryId = categoryId, name = "Beras Rojolele 5kg", purchasePrice = 60000, sellingPrice = 68000, stock = 2.0, minimumStock = 5.0, unit = "sak")
        )
        // 3. Customer Debt
        database.debtDao().insertDebt(
            DebtEntity(customerId = customerId, totalDebt = 125000L, paidAmount = 50000L, status = "OPEN")
        )
        // 4. Supplier Payable
        database.supplierPayableDao().insertSupplierPayable(
            SupplierPayableEntity(supplierId = supplierId, totalDebt = 500000L, paidAmount = 0L, status = "OPEN")
        )

        val notifVm = NotificationViewModel(notificationRepo)

        composeTestRule.setContent {
            NotificationCenterScreen(
                viewModel = notifVm,
                onBack = {},
                onNavigate = {}
            )
        }
        composeTestRule.waitForIdle()
        saveScreenshot("C_NOTIFICATION_CENTER_REAL_DATA.png")
    }

    @Test
    fun captureE_EmptyNotificationCenter() = runBlocking {
        val notifVm = NotificationViewModel(notificationRepo)

        composeTestRule.setContent {
            NotificationCenterScreen(
                viewModel = notifVm,
                onBack = {},
                onNavigate = {}
            )
        }
        composeTestRule.waitForIdle()
        saveScreenshot("E_NOTIFICATION_CENTER_EMPTY.png")
    }
}



