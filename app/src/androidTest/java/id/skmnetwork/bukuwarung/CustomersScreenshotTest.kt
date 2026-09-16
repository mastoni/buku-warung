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
import id.skmnetwork.bukuwarung.data.repository.CustomerRepository
import id.skmnetwork.bukuwarung.ui.customer.CustomerViewModel
import id.skmnetwork.bukuwarung.ui.customer.CustomersScreen
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
class CustomersScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var customerRepository: CustomerRepository

    private var customerBudiId: Long = 0
    private var customerSitiId: Long = 0
    private var customerToniId: Long = 0
    private var productId: Long = 0

    @Before
    fun setup() {
        runBlocking {
            context = ApplicationProvider.getApplicationContext()
            database = Room.inMemoryDatabaseBuilder(
                context,
                AppDatabase::class.java
            ).allowMainThreadQueries().build()

            customerRepository = CustomerRepository(database)

            // Setup categories & product
            val catId = database.categoryDao().insertCategory(CategoryEntity(name = "Sembako"))
            productId = database.productDao().insertProduct(
                ProductEntity(
                    categoryId = catId,
                    name = "Beras Rojolele 5kg",
                    purchasePrice = 60000,
                    sellingPrice = 75000,
                    stock = 50.0
                )
            )

            // Setup Customers
            customerBudiId = customerRepository.saveCustomer("Pak Budi Santoso", "081234567890", "Jl. Melati No. 12")
            customerSitiId = customerRepository.saveCustomer("Ibu Siti Aminah", "085678901234", "Jl. Mawar No. 4")
            customerToniId = customerRepository.saveCustomer("Mas Toni Sukses", "087890123456", "Jl. Kenanga No. 8")

            // Pak Budi has credit sale (outstanding debt)
            customerRepository.processAtomicCreditCheckout(
                cartItems = mapOf(productId to 2.0),
                customerId = customerBudiId
            )

            // Ibu Siti had credit sale but paid in full
            customerRepository.processAtomicCreditCheckout(
                cartItems = mapOf(productId to 1.0),
                customerId = customerSitiId
            )
            val sitiDebts = database.debtDao().getDebtsForCustomer(customerSitiId).first()
            if (sitiDebts.isNotEmpty()) {
                customerRepository.processAtomicDebtPayment(
                    debtId = sitiDebts.first().id,
                    amount = 75000L,
                    note = "Pelunasan tunai"
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
    fun test1_CustomersMainScreen() {
        val viewModel = CustomerViewModel(customerRepository)

        composeTestRule.setContent {
            CustomersScreen(customerViewModel = viewModel)
        }
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Pak Budi Santoso", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        saveScreenshot("CUSTOMERS_MAIN_EVIDENCE.png")
    }

    @Test
    fun test2_CustomersDebtEvidence() {
        val viewModel = CustomerViewModel(customerRepository)

        composeTestRule.setContent {
            CustomersScreen(customerViewModel = viewModel)
        }
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Belum Lunas", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        saveScreenshot("CUSTOMERS_DEBT_EVIDENCE.png")
    }

    @Test
    fun test3_CustomersPaidEvidence() {
        val viewModel = CustomerViewModel(customerRepository)

        composeTestRule.setContent {
            CustomersScreen(customerViewModel = viewModel)
        }
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Ibu Siti Aminah", substring = true).fetchSemanticsNodes().isNotEmpty() &&
            composeTestRule.onAllNodesWithText("Lunas", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        saveScreenshot("CUSTOMERS_PAID_EVIDENCE.png")
    }

    @Test
    fun test4_CustomersEmptyState() {
        val emptyDb = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        val emptyRepo = CustomerRepository(emptyDb)
        val emptyViewModel = CustomerViewModel(emptyRepo)

        composeTestRule.setContent {
            CustomersScreen(customerViewModel = emptyViewModel)
        }
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Belum Ada Pelanggan", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        saveScreenshot("CUSTOMERS_EMPTY_EVIDENCE.png")
        emptyDb.close()
    }

    @Test
    fun test5_CustomerDetailEvidence() {
        val viewModel = CustomerViewModel(customerRepository)

        composeTestRule.setContent {
            CustomersScreen(customerViewModel = viewModel)
        }
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Pak Budi Santoso", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()

        // Click customer to open detail dialog
        composeTestRule.onNodeWithText("Pak Budi Santoso").performClick()
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Sisa Piutang", substring = true).fetchSemanticsNodes().isNotEmpty() &&
            composeTestRule.onAllNodesWithText("Bayar Hutang", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        saveScreenshot("CUSTOMER_DETAIL_EVIDENCE.png")
    }
}
