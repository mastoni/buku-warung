package id.skmnetwork.bukuwarung

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CashTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.local.entity.SupplierEntity
import id.skmnetwork.bukuwarung.data.repository.CustomerRepository
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import id.skmnetwork.bukuwarung.data.repository.SupplierRepository
import id.skmnetwork.bukuwarung.ui.cash.CashScreen
import id.skmnetwork.bukuwarung.ui.product.ProductViewModel
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
class CashScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var productRepository: ProductRepository
    private lateinit var customerRepository: CustomerRepository
    private lateinit var supplierRepository: SupplierRepository

    @Before
    fun setup() {
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            database = Room.inMemoryDatabaseBuilder(
                context,
                AppDatabase::class.java
            ).allowMainThreadQueries().build()

            productRepository = ProductRepository(database, "LEGACY_BUSINESS")
            customerRepository = CustomerRepository(database, "LEGACY_BUSINESS")
            supplierRepository = SupplierRepository(database, "LEGACY_BUSINESS")

            val catId = database.categoryDao().insertCategory(CategoryEntity(name = "Sembako"))
            val supId = database.supplierDao().insertSupplier(
                SupplierEntity(name = "PT Indogrosir Jaya", phone = "081122334455", address = "Kawasan Industri")
            )

            val p1Id = database.productDao().insertProduct(
                ProductEntity(
                    categoryId = catId,
                    name = "Beras Rojolele 5kg",
                    purchasePrice = 60000,
                    sellingPrice = 72000,
                    stock = 20.0,
                    unit = "sak"
                )
            )

            // 1. Initial manual capital deposit (Income)
            productRepository.addManualCashTransaction("INCOME", 2000000, "Isi Saldo Awal Modal Usaha")

            // 2. Automatic Cash Sale (Income)
            productRepository.processAtomicCheckout(mapOf(p1Id to 2.0), "CASH")

            // 3. Automatic Cash Purchase (Expense)
            productRepository.processAtomicPurchase(mapOf(p1Id to 1.0), supId)

            // 4. Manual electricity expense (Expense)
            productRepository.addManualCashTransaction("EXPENSE", 125000, "Bayar Listrik & Token PLN")
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
            android.util.Log.e("CashScreenshotTest", "Failed to save screenshot: $filename", e)
        }
    }

    @Test
    fun test1_CashMainScreen() {
        runBlocking {
            productRepository.allCashTransactions.first { it.isNotEmpty() }
        }
        val productVm = ProductViewModel(productRepository)

        composeTestRule.setContent {
            CashScreen(viewModel = productVm)
        }
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Saldo Kas", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        saveScreenshot("CASH_MAIN_EVIDENCE.png")
    }

    @Test
    fun test2_CashIncomeFilter() {
        runBlocking {
            productRepository.allCashTransactions.first { it.isNotEmpty() }
        }
        val productVm = ProductViewModel(productRepository)

        composeTestRule.setContent {
            CashScreen(viewModel = productVm)
        }
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Pemasukan", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()

        // Select "Pemasukan" chip
        composeTestRule.onNodeWithText("Pemasukan").performClick()
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Isi Saldo Awal Modal Usaha", substring = true).fetchSemanticsNodes().isNotEmpty() &&
            composeTestRule.onAllNodesWithText("Bayar Listrik", substring = true).fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        saveScreenshot("CASH_INCOME_EVIDENCE.png")
    }

    @Test
    fun test3_CashExpenseFilter() {
        runBlocking {
            productRepository.allCashTransactions.first { it.isNotEmpty() }
        }
        val productVm = ProductViewModel(productRepository)

        composeTestRule.setContent {
            CashScreen(viewModel = productVm)
        }
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Pengeluaran", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()

        // Select "Pengeluaran" chip
        composeTestRule.onNodeWithText("Pengeluaran").performClick()
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Bayar Listrik", substring = true).fetchSemanticsNodes().isNotEmpty() &&
            composeTestRule.onAllNodesWithText("Isi Saldo Awal", substring = true).fetchSemanticsNodes().isEmpty()
        }
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        saveScreenshot("CASH_EXPENSE_EVIDENCE.png")
    }

    @Test
    fun test4_CashFilterGeneral() {
        runBlocking {
            productRepository.allCashTransactions.first { it.isNotEmpty() }
        }
        val productVm = ProductViewModel(productRepository)

        composeTestRule.setContent {
            CashScreen(viewModel = productVm)
        }
        composeTestRule.waitForIdle()
        saveScreenshot("CASH_FILTER_EVIDENCE.png")
    }

    @Test
    fun test5_CashEmptyState() {
        val emptyDb = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        val emptyRepo = ProductRepository(emptyDb, "LEGACY_BUSINESS")
        val emptyVm = ProductViewModel(emptyRepo)

        composeTestRule.setContent {
            CashScreen(viewModel = emptyVm)
        }
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Belum Ada Transaksi Kas", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        saveScreenshot("CASH_EMPTY_EVIDENCE.png")
        emptyDb.close()
    }
}



