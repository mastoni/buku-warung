package id.skmnetwork.bukuwarung

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.preferences.UserSettings
import id.skmnetwork.bukuwarung.data.repository.CustomerRepository
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import id.skmnetwork.bukuwarung.ui.customer.CustomerViewModel
import id.skmnetwork.bukuwarung.ui.pos.PosScreen
import id.skmnetwork.bukuwarung.ui.product.ProductViewModel
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class PosScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var productRepository: ProductRepository
    private lateinit var customerRepository: CustomerRepository

    private var catSembakoId: Long = 0
    private var catMinumanId: Long = 0
    private var catMakananId: Long = 0
    private var p1Id: Long = 0
    private var p2Id: Long = 0
    private var p3Id: Long = 0
    private var p4Id: Long = 0

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

            catSembakoId = database.categoryDao().insertCategory(CategoryEntity(name = "Sembako"))
            catMinumanId = database.categoryDao().insertCategory(CategoryEntity(name = "Minuman"))
            catMakananId = database.categoryDao().insertCategory(CategoryEntity(name = "Makanan"))

            p1Id = database.productDao().insertProduct(
                ProductEntity(categoryId = catSembakoId, name = "Beras Rojolele 5kg", purchasePrice = 60000, sellingPrice = 72000, stock = 15.0, unit = "sak")
            )
            p2Id = database.productDao().insertProduct(
                ProductEntity(categoryId = catSembakoId, name = "Minyak Goreng 2L", purchasePrice = 28000, sellingPrice = 33000, stock = 8.0, unit = "pch")
            )
            p3Id = database.productDao().insertProduct(
                ProductEntity(categoryId = catMinumanId, name = "Kopi Kapal Api Special Mix", purchasePrice = 1500, sellingPrice = 2000, stock = 50.0, unit = "sachet")
            )
            p4Id = database.productDao().insertProduct(
                ProductEntity(categoryId = catMinumanId, name = "Teh Pucuk Harum 350ml", purchasePrice = 3000, sellingPrice = 4000, stock = 24.0, unit = "btl")
            )
            database.productDao().insertProduct(
                ProductEntity(categoryId = catMakananId, name = "Indomie Goreng Original", purchasePrice = 2800, sellingPrice = 3500, stock = 40.0, unit = "bks")
            )
            database.productDao().insertProduct(
                ProductEntity(categoryId = catMakananId, name = "Keripik Singkong Balado", purchasePrice = 5000, sellingPrice = 7500, stock = 0.0, unit = "bks")
            )

            val custId = customerRepository.saveCustomer("Pak Budi", "08123456789", "Jl. Melati No. 5")
            productRepository.processAtomicCheckout(mapOf(p1Id to 1.0, p3Id to 2.0), "CASH")
            productRepository.processAtomicCheckout(mapOf(p4Id to 3.0), "QRIS")
            customerRepository.processAtomicCreditCheckout(mapOf(p2Id to 1.0), custId)
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
            android.util.Log.e("PosScreenshotTest", "Failed to save screenshot: $filename", e)
        }
    }

    @Test
    fun capture1_PosScreenProductsGrid() {
        val productVm = ProductViewModel(productRepository)
        val customerVm = CustomerViewModel(customerRepository)

        composeTestRule.setContent {
            PosScreen(
                viewModel = productVm,
                customerViewModel = customerVm,
                userSettings = UserSettings(showProductImage = true, showStock = true, showBarcode = true)
            )
        }
        composeTestRule.waitUntil(5000) { productVm.products.value.isNotEmpty() }
        composeTestRule.waitForIdle()
        saveScreenshot("KASIR_SCREEN_EVIDENCE.png")
    }

    @Test
    fun capture2_PosScreenWithCart() {
        val productVm = ProductViewModel(productRepository)
        val customerVm = CustomerViewModel(customerRepository)

        composeTestRule.setContent {
            PosScreen(
                viewModel = productVm,
                customerViewModel = customerVm,
                userSettings = UserSettings(showProductImage = true, showStock = true, showBarcode = true)
            )
        }
        composeTestRule.waitForIdle()
        // Click products to add to cart
        composeTestRule.onNodeWithText("Beras Rojolele 5kg", substring = true).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Indomie Goreng Original", substring = true).performClick()
        composeTestRule.waitForIdle()
        saveScreenshot("KASIR_CART_EVIDENCE.png")
    }

    @Test
    fun capture3_PosScreenSalesHistory() {
        val productVm = ProductViewModel(productRepository)
        val customerVm = CustomerViewModel(customerRepository)

        composeTestRule.setContent {
            PosScreen(
                viewModel = productVm,
                customerViewModel = customerVm,
                userSettings = UserSettings(showProductImage = true, showStock = true, showBarcode = true)
            )
        }
        composeTestRule.waitForIdle()
        // Switch to Riwayat Penjualan tab
        composeTestRule.onNodeWithText("Riwayat Penjualan", substring = true).performClick()
        composeTestRule.waitForIdle()
        saveScreenshot("KASIR_HISTORY_EVIDENCE.png")
    }

    @Test
    fun test04_ResponsivePosGrid320dp() {
        val productVm = ProductViewModel(productRepository)
        val customerVm = CustomerViewModel(customerRepository)

        composeTestRule.setContent {
            androidx.compose.foundation.layout.Box(
                modifier = androidx.compose.ui.Modifier.size(width = 320.dp, height = 700.dp)
            ) {
                PosScreen(
                    viewModel = productVm,
                    customerViewModel = customerVm,
                    userSettings = UserSettings(showProductImage = true, showStock = true, showBarcode = true)
                )
            }
        }
        composeTestRule.waitForIdle()
        saveScreenshot("KASIR_RESPONSIVE_320DP.png")
        composeTestRule.onNodeWithText("Beras Rojolele 5kg", substring = true).assertExists()
    }

    @Test
    fun test05_ResponsivePosGrid360dp() {
        val productVm = ProductViewModel(productRepository)
        val customerVm = CustomerViewModel(customerRepository)

        composeTestRule.setContent {
            androidx.compose.foundation.layout.Box(
                modifier = androidx.compose.ui.Modifier.size(width = 360.dp, height = 740.dp)
            ) {
                PosScreen(
                    viewModel = productVm,
                    customerViewModel = customerVm,
                    userSettings = UserSettings(showProductImage = true, showStock = true, showBarcode = true)
                )
            }
        }
        composeTestRule.waitForIdle()
        saveScreenshot("KASIR_RESPONSIVE_360DP.png")
        composeTestRule.onNodeWithText("Beras Rojolele 5kg", substring = true).assertExists()
    }

    @Test
    fun test06_ResponsivePosGrid393dp() {
        val productVm = ProductViewModel(productRepository)
        val customerVm = CustomerViewModel(customerRepository)

        composeTestRule.setContent {
            androidx.compose.foundation.layout.Box(
                modifier = androidx.compose.ui.Modifier.size(width = 393.dp, height = 800.dp)
            ) {
                PosScreen(
                    viewModel = productVm,
                    customerViewModel = customerVm,
                    userSettings = UserSettings(showProductImage = true, showStock = true, showBarcode = true)
                )
            }
        }
        composeTestRule.waitForIdle()
        saveScreenshot("KASIR_RESPONSIVE_393DP.png")
        composeTestRule.onNodeWithText("Beras Rojolele 5kg", substring = true).assertExists()
    }
}


