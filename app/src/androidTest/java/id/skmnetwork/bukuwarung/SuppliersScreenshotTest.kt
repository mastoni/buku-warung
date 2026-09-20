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
import id.skmnetwork.bukuwarung.data.repository.PurchaseRepository
import id.skmnetwork.bukuwarung.data.repository.SupplierRepository
import id.skmnetwork.bukuwarung.ui.supplier.SupplierViewModel
import id.skmnetwork.bukuwarung.ui.supplier.SuppliersScreen
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
class SuppliersScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var supplierRepository: SupplierRepository
    private lateinit var purchaseRepository: PurchaseRepository
    private lateinit var productRepository: ProductRepository

    private var supplierIndoId: Long = 0
    private var supplierWingsId: Long = 0
    private var supplierMayoraId: Long = 0
    private var productId: Long = 0

    @Before
    fun setup() {
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            database = Room.inMemoryDatabaseBuilder(
                context,
                AppDatabase::class.java
            ).allowMainThreadQueries().build()

            supplierRepository = SupplierRepository(database, "LEGACY_BUSINESS")
            purchaseRepository = PurchaseRepository(database, "LEGACY_BUSINESS")
            productRepository = ProductRepository(database, "LEGACY_BUSINESS")

            // Setup Category & Product
            val catId = database.categoryDao().insertCategory(CategoryEntity(name = "Sembako"))
            productId = database.productDao().insertProduct(
                ProductEntity(
                    categoryId = catId,
                    name = "Minyak Goreng 2L",
                    purchasePrice = 28000,
                    sellingPrice = 34000,
                    stock = 100.0
                )
            )

            // Setup Suppliers
            supplierIndoId = supplierRepository.saveSupplier("PT Indofood Sukses", "081122334455", "Kawasan Industri MM2100")
            supplierWingsId = supplierRepository.saveSupplier("CV Wings Surya", "081233445566", "Jl. Rungkut Industri No. 10")
            supplierMayoraId = supplierRepository.saveSupplier("Distributor Mayora", "081344556677", "Jl. Daan Mogot KM 18")

            // PT Indofood has credit purchase (outstanding payable debt)
            supplierRepository.processAtomicCreditPurchase(
                purchaseItems = mapOf(productId to 10.0),
                supplierId = supplierIndoId
            )

            // CV Wings had credit purchase but paid in full
            supplierRepository.processAtomicCreditPurchase(
                purchaseItems = mapOf(productId to 5.0),
                supplierId = supplierWingsId
            )
            val wingsPayables = database.supplierPayableDao().getPayablesForSupplier(supplierWingsId, "LEGACY_BUSINESS").first()
            if (wingsPayables.isNotEmpty()) {
                supplierRepository.processAtomicSupplierPayment(
                    payableId = wingsPayables.first().id,
                    amount = 140000L,
                    note = "Pelunasan transfer BCA"
                )
            }
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun saveScreenshot(fileName: String) {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir = File("/sdcard/Download")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    @Test
    fun test1_SuppliersMainScreen() {
        val viewModel = SupplierViewModel(supplierRepository)

        composeTestRule.setContent {
            SuppliersScreen(supplierViewModel = viewModel)
        }
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("PT Indofood Sukses", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        saveScreenshot("SUPPLIERS_MAIN_EVIDENCE.png")
    }

    @Test
    fun test2_SuppliersDebtEvidence() {
        val viewModel = SupplierViewModel(supplierRepository)

        composeTestRule.setContent {
            SuppliersScreen(supplierViewModel = viewModel)
        }
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Belum Lunas", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        saveScreenshot("SUPPLIERS_DEBT_EVIDENCE.png")
    }

    @Test
    fun test3_SuppliersPaidEvidence() {
        val viewModel = SupplierViewModel(supplierRepository)

        composeTestRule.setContent {
            SuppliersScreen(supplierViewModel = viewModel)
        }
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("CV Wings Surya", substring = true).fetchSemanticsNodes().isNotEmpty() &&
            composeTestRule.onAllNodesWithText("Lunas", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        saveScreenshot("SUPPLIERS_PAID_EVIDENCE.png")
    }

    @Test
    fun test4_SuppliersEmptyState() {
        val emptyDb = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        val emptyRepo = SupplierRepository(emptyDb, "LEGACY_BUSINESS")
        val emptyViewModel = SupplierViewModel(emptyRepo)

        composeTestRule.setContent {
            SuppliersScreen(supplierViewModel = emptyViewModel)
        }
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Belum Ada Supplier", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        saveScreenshot("SUPPLIERS_EMPTY_EVIDENCE.png")
        emptyDb.close()
    }

    @Test
    fun test5_SupplierDetailEvidence() {
        val viewModel = SupplierViewModel(supplierRepository)

        composeTestRule.setContent {
            SuppliersScreen(supplierViewModel = viewModel)
        }
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("PT Indofood Sukses", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()

        // Click supplier to open detail dialog
        composeTestRule.onNodeWithText("PT Indofood Sukses").performClick()
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Sisa Hutang", substring = true).fetchSemanticsNodes().isNotEmpty() &&
            composeTestRule.onAllNodesWithText("Bayar Hutang Supplier", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        saveScreenshot("SUPPLIER_DETAIL_EVIDENCE.png")
    }
}



