package id.skmnetwork.bukuwarung

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.skmnetwork.bukuwarung.backup.BackupRestoreManager
import id.skmnetwork.bukuwarung.backup.MockSheetsTransport
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.local.entity.CustomerEntity
import id.skmnetwork.bukuwarung.data.local.entity.DebtEntity
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.local.entity.SupplierEntity
import id.skmnetwork.bukuwarung.data.local.entity.SupplierPayableEntity
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import id.skmnetwork.bukuwarung.data.preferences.UserSettings
import id.skmnetwork.bukuwarung.data.repository.CustomerRepository
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import id.skmnetwork.bukuwarung.data.repository.ReportRepository
import id.skmnetwork.bukuwarung.data.repository.SupplierRepository
import id.skmnetwork.bukuwarung.license.LicenseManager
import id.skmnetwork.bukuwarung.notification.NotificationRepository
import id.skmnetwork.bukuwarung.notification.NotificationViewModel
import id.skmnetwork.bukuwarung.printer.PrinterService
import id.skmnetwork.bukuwarung.ui.cash.CashScreen
import id.skmnetwork.bukuwarung.ui.customer.CustomerViewModel
import id.skmnetwork.bukuwarung.ui.customer.CustomersScreen
import id.skmnetwork.bukuwarung.ui.home.HomeScreen
import id.skmnetwork.bukuwarung.ui.notification.NotificationCenterScreen
import id.skmnetwork.bukuwarung.ui.pos.PosScreen
import id.skmnetwork.bukuwarung.ui.product.ProductViewModel
import id.skmnetwork.bukuwarung.ui.product.ProductsScreen
import id.skmnetwork.bukuwarung.ui.purchase.PurchaseScreen
import id.skmnetwork.bukuwarung.ui.report.ReportPeriod
import id.skmnetwork.bukuwarung.ui.report.ReportViewModel
import id.skmnetwork.bukuwarung.ui.report.ReportsScreen
import id.skmnetwork.bukuwarung.ui.settings.BackupViewModel
import id.skmnetwork.bukuwarung.ui.settings.SettingsScreen
import id.skmnetwork.bukuwarung.ui.supplier.SupplierViewModel
import id.skmnetwork.bukuwarung.ui.supplier.SuppliersScreen
import id.skmnetwork.bukuwarung.ui.theme.BukuWarungTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class CrossScreenScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var prefsRepo: UserPreferencesRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var customerRepository: CustomerRepository
    private lateinit var supplierRepository: SupplierRepository
    private lateinit var reportRepository: ReportRepository
    private lateinit var notificationRepository: NotificationRepository

    private lateinit var productViewModel: ProductViewModel
    private lateinit var customerViewModel: CustomerViewModel
    private lateinit var supplierViewModel: SupplierViewModel
    private lateinit var reportViewModel: ReportViewModel
    private lateinit var notificationViewModel: NotificationViewModel
    private lateinit var backupViewModel: BackupViewModel

    private lateinit var licenseManager: LicenseManager
    private lateinit var printerService: PrinterService
    private var cachedUserSettings: UserSettings = UserSettings()

    @Before
    fun setup() {
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            database = Room.inMemoryDatabaseBuilder(
                context,
                AppDatabase::class.java
            ).allowMainThreadQueries().build()

            prefsRepo = UserPreferencesRepository(context)
            prefsRepo.saveShopProfile(
                shopName = "Warung Berkah Bu Siti",
                ownerName = "Bu Siti Rohmah",
                phone = "081234567890",
                address = "Jl. Raya Pasar Minggu No. 12"
            )
            prefsRepo.updateStockSettings(
                lowStockAlertEnabled = true,
                defaultLowStockLimit = 5,
                allowNegativeStock = false
            )
            prefsRepo.updateNotificationSettings(
                lowStockNotificationEnabled = true,
                debtReminderEnabled = true
            )

            productRepository = ProductRepository(database, "LEGACY_BUSINESS")
            customerRepository = CustomerRepository(database, "LEGACY_BUSINESS")
            supplierRepository = SupplierRepository(database, "LEGACY_BUSINESS")
            reportRepository = ReportRepository(database, "LEGACY_BUSINESS")
            notificationRepository = NotificationRepository(database, prefsRepo, "LEGACY_BUSINESS")

            // Seed Categories & Products
            val catSembako = database.categoryDao().insertCategory(CategoryEntity(name = "Sembako"))
            val catMinuman = database.categoryDao().insertCategory(CategoryEntity(name = "Minuman"))

            val supId = database.supplierDao().insertSupplier(
                SupplierEntity(name = "PT Indogrosir Jaya", phone = "081122334455", address = "Kawasan Industri No. 5")
            )
            database.supplierPayableDao().insertSupplierPayable(
                SupplierPayableEntity(
                    supplierId = supId,
                    totalDebt = 350000,
                    paidAmount = 200000,
                    createdAt = System.currentTimeMillis() - 86400000L
                )
            )

            val custId = database.customerDao().insertCustomer(
                CustomerEntity(name = "Pak Haji Rahmat", phone = "081298765432", address = "Komplek Taman Blok A")
            )
            database.debtDao().insertDebt(
                DebtEntity(
                    customerId = custId,
                    totalDebt = 85000,
                    paidAmount = 30000,
                    createdAt = System.currentTimeMillis() - 43200000L
                )
            )

            val p1 = database.productDao().insertProduct(
                ProductEntity(
                    categoryId = catSembako,
                    name = "Beras Rojolele 5kg",
                    purchasePrice = 60000,
                    sellingPrice = 72000,
                    stock = 15.0,
                    minimumStock = 5.0,
                    unit = "sak"
                )
            )

            val p2 = database.productDao().insertProduct(
                ProductEntity(
                    categoryId = catMinuman,
                    name = "Kopi Kapal Api Special",
                    purchasePrice = 6500,
                    sellingPrice = 9000,
                    stock = 3.0,
                    minimumStock = 5.0,
                    unit = "pcs"
                )
            )

            val p3 = database.productDao().insertProduct(
                ProductEntity(
                    categoryId = catSembako,
                    name = "Gula Pasir Gulaku 1kg",
                    purchasePrice = 14500,
                    sellingPrice = 18000,
                    stock = 25.0,
                    minimumStock = 5.0,
                    unit = "kg"
                )
            )

            // Capital deposit and sales
            productRepository.addManualCashTransaction("INCOME", 1500000, "Modal Awal Kas")
            productRepository.processAtomicCheckout(mapOf(p1 to 1.0, p2 to 2.0), "CASH")
            productRepository.processAtomicPurchase(mapOf(p3 to 10.0), supId)

            productViewModel = ProductViewModel(productRepository)
            customerViewModel = CustomerViewModel(customerRepository)
            supplierViewModel = SupplierViewModel(supplierRepository)
            reportViewModel = ReportViewModel(reportRepository)
            reportViewModel.selectPeriod(ReportPeriod.THIS_MONTH)
            notificationViewModel = NotificationViewModel(notificationRepository)

            licenseManager = LicenseManager(prefsRepo)
            printerService = PrinterService()

            val mgr = BackupRestoreManager(
                database = database,
                userPreferencesRepository = prefsRepo,
                transport = MockSheetsTransport()
            )
            backupViewModel = BackupViewModel(mgr, prefsRepo)

            cachedUserSettings = prefsRepo.userSettings.first()
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun saveScreenshot(filename: String) {
        try {
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            val tempFile = File(context.cacheDir, filename)
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                "cp ${tempFile.absolutePath} /sdcard/Download/$filename"
            ).close()
        } catch (e: Exception) {
            android.util.Log.e("CrossScreenScreenshotTest", "Failed to save screenshot: $filename", e)
        }
    }

    @Test
    fun test01_CaptureHome() {
        composeTestRule.setContent {
            BukuWarungTheme {
                HomeScreen(
                    viewModel = productViewModel,
                    userSettings = cachedUserSettings,
                    unreadNotificationCount = 2,
                    onNavigate = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        Thread.sleep(800)
        saveScreenshot("I12_HOME_EVIDENCE.png")
    }

    @Test
    fun test02_CaptureKasir() {
        composeTestRule.setContent {
            BukuWarungTheme {
                PosScreen(
                    viewModel = productViewModel,
                    customerViewModel = customerViewModel,
                    userSettings = cachedUserSettings,
                    printerService = printerService,
                    onNavigateToAddProduct = {},
                    onNavigateToSettings = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        Thread.sleep(800)
        saveScreenshot("I12_KASIR_EVIDENCE.png")
    }

    @Test
    fun test03_CaptureProducts() {
        composeTestRule.setContent {
            BukuWarungTheme {
                ProductsScreen(
                    viewModel = productViewModel,
                    onAddProduct = {},
                    onEditProduct = {},
                    onNavigateToCatalog = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        Thread.sleep(800)
        saveScreenshot("I12_PRODUCTS_EVIDENCE.png")
    }

    @Test
    fun test04_CapturePurchase() {
        composeTestRule.setContent {
            BukuWarungTheme {
                PurchaseScreen(
                    viewModel = productViewModel,
                    supplierViewModel = supplierViewModel,
                    onNavigateToAddProduct = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        Thread.sleep(800)
        saveScreenshot("I12_PURCHASE_EVIDENCE.png")
    }

    @Test
    fun test05_CaptureCash() {
        composeTestRule.setContent {
            BukuWarungTheme {
                CashScreen(viewModel = productViewModel)
            }
        }
        composeTestRule.waitForIdle()
        Thread.sleep(800)
        saveScreenshot("I12_CASH_EVIDENCE.png")
    }

    @Test
    fun test06_CaptureCustomers() {
        composeTestRule.setContent {
            BukuWarungTheme {
                CustomersScreen(customerViewModel = customerViewModel)
            }
        }
        composeTestRule.waitForIdle()
        Thread.sleep(800)
        saveScreenshot("I12_CUSTOMERS_EVIDENCE.png")
    }

    @Test
    fun test07_CaptureSuppliers() {
        composeTestRule.setContent {
            BukuWarungTheme {
                SuppliersScreen(supplierViewModel = supplierViewModel)
            }
        }
        composeTestRule.waitForIdle()
        Thread.sleep(800)
        saveScreenshot("I12_SUPPLIERS_EVIDENCE.png")
    }

    @Test
    fun test08_CaptureReports() {
        composeTestRule.setContent {
            BukuWarungTheme {
                ReportsScreen(
                    reportViewModel = reportViewModel,
                    userPreferencesRepository = prefsRepo
                )
            }
        }
        composeTestRule.waitForIdle()
        Thread.sleep(800)
        saveScreenshot("I12_REPORTS_EVIDENCE.png")
    }

    @Test
    fun test09_CaptureSettings() {
        composeTestRule.setContent {
            BukuWarungTheme {
                SettingsScreen(
                    userPreferencesRepository = prefsRepo,
                    licenseManager = licenseManager,
                    printerService = printerService,
                    backupViewModel = backupViewModel
                )
            }
        }
        composeTestRule.waitForIdle()
        Thread.sleep(800)
        saveScreenshot("I12_SETTINGS_EVIDENCE.png")
    }

    @Test
    fun test10_CaptureNotification() {
        composeTestRule.setContent {
            BukuWarungTheme {
                NotificationCenterScreen(
                    viewModel = notificationViewModel,
                    onBack = {},
                    onNavigate = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        Thread.sleep(800)
        saveScreenshot("I12_NOTIFICATION_EVIDENCE.png")
    }
}


