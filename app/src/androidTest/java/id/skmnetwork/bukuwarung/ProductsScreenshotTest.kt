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
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import id.skmnetwork.bukuwarung.ui.product.ProductViewModel
import id.skmnetwork.bukuwarung.ui.product.ProductsScreen
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
class ProductsScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var productRepository: ProductRepository

    private var catSembakoId: Long = 0
    private var catMinumanId: Long = 0
    private var catMakananId: Long = 0

    @Before
    fun setup() {
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            database = Room.inMemoryDatabaseBuilder(
                context,
                AppDatabase::class.java
            ).allowMainThreadQueries().build()

            productRepository = ProductRepository(database, "LEGACY_BUSINESS")

            catSembakoId = database.categoryDao().insertCategory(CategoryEntity(name = "Sembako"))
            catMinumanId = database.categoryDao().insertCategory(CategoryEntity(name = "Minuman"))
            catMakananId = database.categoryDao().insertCategory(CategoryEntity(name = "Makanan"))

            // Populated Products
            database.productDao().insertProduct(
                ProductEntity(
                    categoryId = catMakananId,
                    name = "Indomie Goreng Original",
                    purchasePrice = 2800,
                    sellingPrice = 3500,
                    stock = 50.0,
                    minimumStock = 10.0,
                    unit = "bks"
                )
            )
            database.productDao().insertProduct(
                ProductEntity(
                    categoryId = catMinumanId,
                    name = "Aqua 600ml",
                    purchasePrice = 3000,
                    sellingPrice = 4000,
                    stock = 24.0,
                    minimumStock = 10.0,
                    unit = "pcs"
                )
            )
            database.productDao().insertProduct(
                ProductEntity(
                    categoryId = catSembakoId,
                    name = "Gula Pasir 1 kg",
                    purchasePrice = 14500,
                    sellingPrice = 17000,
                    stock = 3.0,
                    minimumStock = 5.0, // Low stock -> should show "Sisa 3 kg (Menipis)"
                    unit = "kg"
                )
            )
            database.productDao().insertProduct(
                ProductEntity(
                    categoryId = catMinumanId,
                    name = "Kopi Good Day",
                    purchasePrice = 1800,
                    sellingPrice = 2500,
                    stock = 30.0,
                    minimumStock = 5.0,
                    unit = "pcs"
                )
            )
            database.productDao().insertProduct(
                ProductEntity(
                    categoryId = catSembakoId,
                    name = "Telur Ayam",
                    purchasePrice = 24000,
                    sellingPrice = 28000,
                    stock = 0.0, // Out of stock -> should show "Stok Habis"
                    minimumStock = 5.0,
                    unit = "kg"
                )
            )
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
            android.util.Log.e("ProductsScreenshotTest", "Failed to save screenshot: $filename", e)
        }
    }

    @Test
    fun test1_ProductsMainPopulated() {
        runBlocking {
            productRepository.allProducts.first { it.size >= 5 }
            productRepository.allCategories.first { it.size >= 3 }
        }
        val productVm = ProductViewModel(productRepository)

        composeTestRule.setContent {
            ProductsScreen(
                viewModel = productVm,
                onAddProduct = {},
                onEditProduct = {},
                onNavigateToCatalog = {}
            )
        }
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Gula Pasir 1 kg", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        saveScreenshot("PRODUCTS_MAIN_EVIDENCE.png")
    }

    @Test
    fun test2_ProductsFilterCategory() {
        runBlocking {
            productRepository.allProducts.first { it.size >= 5 }
            productRepository.allCategories.first { it.size >= 3 }
        }
        val productVm = ProductViewModel(productRepository)

        composeTestRule.setContent {
            ProductsScreen(
                viewModel = productVm,
                onAddProduct = {},
                onEditProduct = {},
                onNavigateToCatalog = {}
            )
        }
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Minuman", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()

        // Select "Minuman" category chip
        composeTestRule.onNodeWithText("Minuman").performClick()
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Indomie Goreng Original", substring = true).fetchSemanticsNodes().isEmpty() &&
            composeTestRule.onAllNodesWithText("Aqua 600ml", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        saveScreenshot("PRODUCTS_FILTER_EVIDENCE.png")
    }

    @Test
    fun test3_ProductsEmptyState() {
        val emptyDb = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        val emptyRepo = ProductRepository(emptyDb, "LEGACY_BUSINESS")
        val emptyVm = ProductViewModel(emptyRepo)

        composeTestRule.setContent {
            ProductsScreen(
                viewModel = emptyVm,
                onAddProduct = {},
                onEditProduct = {},
                onNavigateToCatalog = {}
            )
        }
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Belum Ada Produk", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        saveScreenshot("PRODUCTS_EMPTY_EVIDENCE.png")
        emptyDb.close()
    }
}



