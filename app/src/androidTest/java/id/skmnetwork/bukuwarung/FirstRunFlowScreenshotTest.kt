package id.skmnetwork.bukuwarung

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
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
import id.skmnetwork.bukuwarung.ui.theme.AppColors
import id.skmnetwork.bukuwarung.ui.theme.BukuWarungTheme
import id.skmnetwork.bukuwarung.ui.welcome.FirstSetupScreen
import id.skmnetwork.bukuwarung.ui.welcome.WelcomeScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FirstRunFlowScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun captureScreenshot(name: String) {
        try {
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            if (bitmap != null) {
                val file = File(context.cacheDir, "$name.png")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            }
        } catch (_: Exception) {
            // Helper fallback
        }
    }

    @Test
    fun test01_SplashIndicatorRender() {
        composeTestRule.setContent {
            BukuWarungTheme {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AppColors.GreenPrimary)
                }
            }
        }
        composeTestRule.waitForIdle()
        captureScreenshot("gate_j2_01_splash_loading")
    }

    @Test
    fun test02_WelcomeScreenRenderAndAction() {
        var startSetupClicked = false
        composeTestRule.setContent {
            BukuWarungTheme {
                WelcomeScreen(
                    onStartSetup = { startSetupClicked = true }
                )
            }
        }
        composeTestRule.waitForIdle()
        captureScreenshot("gate_j2_02_welcome_screen")

        composeTestRule.onNodeWithText("Buku Warung").assertIsDisplayed()
        composeTestRule.onNodeWithText("Catat Jualan").assertIsDisplayed()
        composeTestRule.onNodeWithText("Mulai Sekarang").assertIsDisplayed()

        composeTestRule.onNodeWithText("Mulai Sekarang").performClick()
        assertTrue("onStartSetup must be triggered", startSetupClicked)
    }

    @Test
    fun test03_FirstSetupScreenRenderAndValidation() {
        var submittedShopName = ""
        var submittedOwnerName = ""
        var submittedPhone = ""
        var submittedAddress = ""

        composeTestRule.setContent {
            BukuWarungTheme {
                FirstSetupScreen(
                    onCompleteSetup = { shop, owner, phone, address ->
                        submittedShopName = shop
                        submittedOwnerName = owner
                        submittedPhone = phone
                        submittedAddress = address
                    }
                )
            }
        }
        composeTestRule.waitForIdle()
        captureScreenshot("gate_j2_03_first_setup_empty")

        composeTestRule.onNodeWithText("Selamat Datang 👋").assertIsDisplayed()
        composeTestRule.onNodeWithText("Nama Warung *").assertIsDisplayed()

        // Fill form fields
        composeTestRule.onNodeWithText("Nama Warung *").performTextInput("Warung Berkah Baru")
        composeTestRule.onNodeWithText("Nama Pemilik (opsional)").performTextInput("Budi Santoso")
        composeTestRule.onNodeWithText("Nomor WhatsApp (opsional)").performTextInput("08123456789")
        composeTestRule.onNodeWithText("Alamat Warung (opsional)").performTextInput("Jl. Melati No. 12")

        composeTestRule.waitForIdle()
        captureScreenshot("gate_j2_04_first_setup_filled")

        composeTestRule.onNodeWithText("Simpan Profil & Mulai").performClick()

        assertEquals("Warung Berkah Baru", submittedShopName)
        assertEquals("Budi Santoso", submittedOwnerName)
        assertEquals("08123456789", submittedPhone)
        assertEquals("Jl. Melati No. 12", submittedAddress)
    }

    @Test
    fun test04_ProductsFabCompactRender() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val catId = database.categoryDao().insertCategory(CategoryEntity(name = "Sembako"))
        database.productDao().insertProduct(
            ProductEntity(
                categoryId = catId,
                name = "Beras Ramos 5kg",
                purchasePrice = 65000,
                sellingPrice = 72000,
                stock = 10.0,
                minimumStock = 2.0,
                unit = "karung"
            )
        )
        val productRepo = ProductRepository(database)
        val viewModel = ProductViewModel(productRepo)

        composeTestRule.setContent {
            BukuWarungTheme {
                ProductsScreen(
                    viewModel = viewModel,
                    onAddProduct = {},
                    onEditProduct = {},
                    onNavigateToCatalog = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        captureScreenshot("gate_j2_05_products_compact_fab")

        composeTestRule.onNodeWithText("Tambah Produk").assertIsDisplayed()
        composeTestRule.onNodeWithText("Beras Ramos 5kg").assertIsDisplayed()

        database.close()
    }

    @Test
    fun test05_ResponsiveWelcome320dp() {
        composeTestRule.setContent {
            BukuWarungTheme {
                Box(modifier = Modifier.size(width = 320.dp, height = 700.dp)) {
                    WelcomeScreen(onStartSetup = {})
                }
            }
        }
        composeTestRule.waitForIdle()
        captureScreenshot("gate_j2_06_welcome_responsive_320dp")
        composeTestRule.onNodeWithText("Mulai Sekarang").assertIsDisplayed()
    }

    @Test
    fun test06_ResponsiveWelcome360dp() {
        composeTestRule.setContent {
            BukuWarungTheme {
                Box(modifier = Modifier.size(width = 360.dp, height = 740.dp)) {
                    WelcomeScreen(onStartSetup = {})
                }
            }
        }
        composeTestRule.waitForIdle()
        captureScreenshot("gate_j2_07_welcome_responsive_360dp")
        composeTestRule.onNodeWithText("Mulai Sekarang").assertIsDisplayed()
    }

    @Test
    fun test07_ResponsiveWelcome393dp() {
        composeTestRule.setContent {
            BukuWarungTheme {
                Box(modifier = Modifier.size(width = 393.dp, height = 800.dp)) {
                    WelcomeScreen(onStartSetup = {})
                }
            }
        }
        composeTestRule.waitForIdle()
        captureScreenshot("gate_j2_08_welcome_responsive_393dp")
        composeTestRule.onNodeWithText("Mulai Sekarang").assertIsDisplayed()
    }
}
