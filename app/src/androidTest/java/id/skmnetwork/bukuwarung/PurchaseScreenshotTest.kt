package id.skmnetwork.bukuwarung

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.local.entity.SupplierEntity
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import id.skmnetwork.bukuwarung.data.repository.SupplierRepository
import id.skmnetwork.bukuwarung.ui.product.ProductViewModel
import id.skmnetwork.bukuwarung.ui.purchase.PurchaseScreen
import id.skmnetwork.bukuwarung.ui.supplier.SupplierViewModel
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
class PurchaseScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var productRepository: ProductRepository
    private lateinit var supplierRepository: SupplierRepository

    private var supIndoGrosirId: Long = 0
    private var supSumberPanganId: Long = 0
    private var p1Id: Long = 0
    private var p2Id: Long = 0

    @Before
    fun setup() {
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            database = Room.inMemoryDatabaseBuilder(
                context,
                AppDatabase::class.java
            ).allowMainThreadQueries().build()

            productRepository = ProductRepository(database, "LEGACY_BUSINESS")
            supplierRepository = SupplierRepository(database, "LEGACY_BUSINESS")

            val catId = database.categoryDao().insertCategory(CategoryEntity(name = "Sembako"))
            val catMinumanId = database.categoryDao().insertCategory(CategoryEntity(name = "Minuman"))

            supIndoGrosirId = database.supplierDao().insertSupplier(
                SupplierEntity(name = "PT Indogrosir Jaya", phone = "081122334455", address = "Kawasan Industri")
            )
            supSumberPanganId = database.supplierDao().insertSupplier(
                SupplierEntity(name = "UD Sumber Pangan", phone = "085566778899", address = "Pasar Induk")
            )

            p1Id = database.productDao().insertProduct(
                ProductEntity(
                    categoryId = catId,
                    name = "Beras Rojolele 5kg",
                    purchasePrice = 60000,
                    sellingPrice = 72000,
                    stock = 10.0,
                    minimumStock = 5.0,
                    unit = "sak"
                )
            )
            p2Id = database.productDao().insertProduct(
                ProductEntity(
                    categoryId = catId,
                    name = "Minyak Goreng 2L",
                    purchasePrice = 28000,
                    sellingPrice = 33000,
                    stock = 15.0,
                    minimumStock = 5.0,
                    unit = "pch"
                )
            )
            database.productDao().insertProduct(
                ProductEntity(
                    categoryId = catMinumanId,
                    name = "Kopi Kapal Api Special Mix",
                    purchasePrice = 1500,
                    sellingPrice = 2000,
                    stock = 50.0,
                    minimumStock = 10.0,
                    unit = "sachet"
                )
            )

            // Seed sample purchases for history
            productRepository.processAtomicPurchase(mapOf(p1Id to 2.0, p2Id to 1.0), supIndoGrosirId)
            supplierRepository.processAtomicCreditPurchase(mapOf(p2Id to 3.0), supSumberPanganId)
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
            android.util.Log.e("PurchaseScreenshotTest", "Failed to save screenshot: $filename", e)
        }
    }

    @Test
    fun test1_PurchaseMainScreen() {
        runBlocking {
            productRepository.allProducts.first { it.isNotEmpty() }
        }
        val productVm = ProductViewModel(productRepository)
        val supplierVm = SupplierViewModel(supplierRepository)

        composeTestRule.setContent {
            PurchaseScreen(
                viewModel = productVm,
                supplierViewModel = supplierVm,
                onNavigateToAddProduct = {}
            )
        }
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Beras Rojolele 5kg", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        saveScreenshot("PURCHASE_MAIN_EVIDENCE.png")
    }

    @Test
    fun test2_PurchaseCashCart() {
        runBlocking {
            productRepository.allProducts.first { it.isNotEmpty() }
        }
        val productVm = ProductViewModel(productRepository)
        val supplierVm = SupplierViewModel(supplierRepository)

        composeTestRule.setContent {
            PurchaseScreen(
                viewModel = productVm,
                supplierViewModel = supplierVm,
                onNavigateToAddProduct = {}
            )
        }
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Beras Rojolele 5kg", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()

        // Increase quantity of Beras Rojolele 5kg
        composeTestRule.onAllNodesWithContentDescription("Tambah")[0].performClick()
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Total Belanja", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        saveScreenshot("PURCHASE_CASH_EVIDENCE.png")
    }

    @Test
    fun test3_PurchaseCreditCart() {
        runBlocking {
            productRepository.allProducts.first { it.isNotEmpty() }
            supplierRepository.allSuppliers.first { it.isNotEmpty() }
        }
        val productVm = ProductViewModel(productRepository)
        val supplierVm = SupplierViewModel(supplierRepository)

        composeTestRule.setContent {
            PurchaseScreen(
                viewModel = productVm,
                supplierViewModel = supplierVm,
                onNavigateToAddProduct = {}
            )
        }
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Beras Rojolele 5kg", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()

        // Increase quantity
        composeTestRule.onAllNodesWithContentDescription("Tambah")[0].performClick()
        composeTestRule.waitForIdle()
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Hutang Supplier", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Switch to Hutang Supplier
        composeTestRule.onNodeWithText("Hutang Supplier").performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        saveScreenshot("PURCHASE_CREDIT_EVIDENCE.png")
    }

    @Test
    fun test4_PurchaseHistoryScreen() {
        runBlocking {
            productRepository.allPurchases.first { it.isNotEmpty() }
        }
        val productVm = ProductViewModel(productRepository)
        val supplierVm = SupplierViewModel(supplierRepository)

        composeTestRule.setContent {
            PurchaseScreen(
                viewModel = productVm,
                supplierViewModel = supplierVm,
                onNavigateToAddProduct = {}
            )
        }
        composeTestRule.waitForIdle()

        // Switch to Riwayat Belanja tab
        composeTestRule.onNodeWithText("Riwayat Belanja").performClick()
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("HUTANG", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        saveScreenshot("PURCHASE_HISTORY_EVIDENCE.png")
    }

    @Test
    fun test5_PurchaseEmptyState() {
        val emptyDb = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        val emptyRepo = ProductRepository(emptyDb, "LEGACY_BUSINESS")
        val emptySupRepo = SupplierRepository(emptyDb, "LEGACY_BUSINESS")
        val emptyVm = ProductViewModel(emptyRepo)
        val emptySupVm = SupplierViewModel(emptySupRepo)

        composeTestRule.setContent {
            PurchaseScreen(
                viewModel = emptyVm,
                supplierViewModel = emptySupVm,
                onNavigateToAddProduct = {}
            )
        }
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Belum Ada Produk Untuk Dibeli", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        saveScreenshot("PURCHASE_EMPTY_EVIDENCE.png")
        emptyDb.close()
    }
}



