package id.skmnetwork.bukuwarung

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.repository.CashRepository
import id.skmnetwork.bukuwarung.data.repository.CustomerRepository
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import id.skmnetwork.bukuwarung.data.repository.PurchaseRepository
import id.skmnetwork.bukuwarung.data.repository.ReportRepository
import id.skmnetwork.bukuwarung.data.repository.SaleRepository
import id.skmnetwork.bukuwarung.data.repository.SupplierRepository
import id.skmnetwork.bukuwarung.ui.report.ReportPeriod
import id.skmnetwork.bukuwarung.ui.report.ReportViewModel
import id.skmnetwork.bukuwarung.ui.report.ReportsScreen
import id.skmnetwork.bukuwarung.ui.theme.BukuWarungTheme
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
class ReportsScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var productRepository: ProductRepository
    private lateinit var saleRepository: SaleRepository
    private lateinit var customerRepository: CustomerRepository
    private lateinit var supplierRepository: SupplierRepository
    private lateinit var purchaseRepository: PurchaseRepository
    private lateinit var cashRepository: CashRepository
    private lateinit var reportRepository: ReportRepository

    private var catId: Long = 0
    private var prodKopiId: Long = 0
    private var prodGulaId: Long = 0
    private var prodBerasId: Long = 0
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

            productRepository = ProductRepository(database)
            saleRepository = SaleRepository(database)
            customerRepository = CustomerRepository(database)
            supplierRepository = SupplierRepository(database)
            purchaseRepository = PurchaseRepository(database)
            cashRepository = CashRepository(database)
            reportRepository = ReportRepository(database)

            catId = database.categoryDao().insertCategory(CategoryEntity(name = "Sembako"))
            prodKopiId = database.productDao().insertProduct(
                ProductEntity(
                    categoryId = catId,
                    name = "Kopi Kapal Api Special",
                    purchasePrice = 6500,
                    sellingPrice = 9000,
                    stock = 50.0
                )
            )
            prodGulaId = database.productDao().insertProduct(
                ProductEntity(
                    categoryId = catId,
                    name = "Gula Pasir Gulaku 1kg",
                    purchasePrice = 14500,
                    sellingPrice = 18000,
                    stock = 30.0
                )
            )
            prodBerasId = database.productDao().insertProduct(
                ProductEntity(
                    categoryId = catId,
                    name = "Beras Ramos Setra 5kg",
                    purchasePrice = 65000,
                    sellingPrice = 75000,
                    stock = 20.0
                )
            )

            customerId = customerRepository.saveCustomer("Pak Haji Mahmud", "081234567890", "Jl. Warung No. 1")
            supplierId = supplierRepository.saveSupplier("Distributor Sembako Utama", "081987654321", "Kawasan Pergudangan")

            // Seed initial cash
            cashRepository.recordManualIncome(1500000L, "Modal Kas Awal")

            // Seed sales
            saleRepository.completeSale(
                cartItems = mapOf(prodKopiId to 10.0, prodGulaId to 5.0),
                paymentMethod = "CASH"
            )

            // Seed credit sale
            customerRepository.processAtomicCreditCheckout(
                cartItems = mapOf(prodBerasId to 2.0),
                customerId = customerId
            )

            // Seed supplier credit purchase
            supplierRepository.processAtomicCreditPurchase(
                purchaseItems = mapOf(prodGulaId to 10.0),
                supplierId = supplierId
            )

            // Seed operational expense
            cashRepository.recordManualExpense(25000L, "Listrik & Air Toko")
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
            android.util.Log.e("ReportsScreenshotTest", "Failed to save screenshot: $filename", e)
        }
    }

    @Test
    fun test1_ReportsMainScreen() {
        val viewModel = ReportViewModel(reportRepository)
        viewModel.selectPeriod(ReportPeriod.ALL_TIME)

        composeTestRule.setContent {
            BukuWarungTheme {
                ReportsScreen(reportViewModel = viewModel)
            }
        }
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Laba Rugi Sederhana", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        saveScreenshot("REPORTS_MAIN_EVIDENCE.png")
    }

    @Test
    fun test2_ReportsPeriodFilter() {
        val viewModel = ReportViewModel(reportRepository)
        viewModel.selectPeriod(ReportPeriod.THIS_MONTH)

        composeTestRule.setContent {
            BukuWarungTheme {
                ReportsScreen(reportViewModel = viewModel)
            }
        }
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Bulan Ini", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        saveScreenshot("REPORTS_PERIOD_EVIDENCE.png")
    }

    @Test
    fun test3_ReportsCardsAndPosition() {
        val viewModel = ReportViewModel(reportRepository)
        viewModel.selectPeriod(ReportPeriod.ALL_TIME)

        composeTestRule.setContent {
            BukuWarungTheme {
                ReportsScreen(reportViewModel = viewModel)
            }
        }
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Laba Rugi Sederhana", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()

        // Swipe up to reveal Posisi Keuangan and Aktivitas Transaksi cards
        composeTestRule.onNodeWithText("Laba Rugi Sederhana").performTouchInput {
            swipeUp(durationMillis = 500)
        }
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        saveScreenshot("REPORTS_CARDS_EVIDENCE.png")
    }

    @Test
    fun test4_ReportsEmptyState() {
        val emptyDb = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        val emptyRepo = ReportRepository(emptyDb)
        val emptyViewModel = ReportViewModel(emptyRepo)
        emptyViewModel.selectPeriod(ReportPeriod.TODAY)

        composeTestRule.setContent {
            BukuWarungTheme {
                ReportsScreen(reportViewModel = emptyViewModel)
            }
        }
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Laba Rugi Sederhana", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        saveScreenshot("REPORTS_EMPTY_EVIDENCE.png")
        emptyDb.close()
    }

    @Test
    fun test5_ReportsDetailExpanded() {
        val viewModel = ReportViewModel(reportRepository)
        viewModel.selectPeriod(ReportPeriod.ALL_TIME)

        composeTestRule.setContent {
            BukuWarungTheme {
                ReportsScreen(reportViewModel = viewModel)
            }
        }
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Lihat Rincian Laba", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        // Expand rincian laba
        composeTestRule.onNodeWithText("Lihat Rincian Laba").performClick()
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Penjualan Bruto", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        saveScreenshot("REPORTS_DETAIL_EVIDENCE.png")
    }
}
