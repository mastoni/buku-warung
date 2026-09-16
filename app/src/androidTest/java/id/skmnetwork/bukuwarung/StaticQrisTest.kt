package id.skmnetwork.bukuwarung

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import id.skmnetwork.bukuwarung.data.repository.SaleRepository
import id.skmnetwork.bukuwarung.ui.pos.QrisPaymentDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class StaticQrisTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var testScope: CoroutineScope
    private lateinit var testPrefsFile: File
    private lateinit var testDataStore: DataStore<Preferences>
    private lateinit var prefsRepo: UserPreferencesRepository
    private lateinit var database: AppDatabase
    private lateinit var productRepository: ProductRepository
    private lateinit var saleRepository: SaleRepository

    private var testProductId: Long = 0L

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        testScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        testPrefsFile = File(context.cacheDir, "test_qris_prefs_${UUID.randomUUID()}.preferences_pb")
        testDataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testPrefsFile }
        )
        prefsRepo = UserPreferencesRepository(context, testDataStore)

        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        productRepository = ProductRepository(database)
        saleRepository = SaleRepository(database)

        val catId = database.categoryDao().insertCategory(CategoryEntity(name = "Minuman"))
        testProductId = database.productDao().insertProduct(
            ProductEntity(
                categoryId = catId,
                name = "Kopi Susu",
                purchasePrice = 5000L,
                sellingPrice = 12000L,
                stock = 20.0
            )
        )
    }

    @After
    fun tearDown() {
        testScope.cancel()
        if (testPrefsFile.exists()) {
            testPrefsFile.delete()
        }
        database.close()
        // Cleanup qris directory
        val qrisDir = File(context.filesDir, "qris")
        if (qrisDir.exists()) {
            qrisDir.listFiles()?.forEach { it.delete() }
        }
    }

    private fun createSampleQrisBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.BLACK
            isAntiAlias = true
        }
        canvas.drawColor(Color.WHITE)
        canvas.drawRect(20f, 20f, 80f, 80f, paint)
        canvas.drawRect(120f, 20f, 180f, 80f, paint)
        canvas.drawRect(20f, 120f, 80f, 180f, paint)
        canvas.drawRect(100f, 100f, 140f, 140f, paint)
        return bitmap
    }

    @Test
    fun test1_noQrisConfigured_defaultPathEmpty() = runBlocking {
        val settings = prefsRepo.userSettings.first()
        assertEquals("", settings.qrisImagePath)
        assertEquals("", prefsRepo.getQrisImagePath())
    }

    @Test
    fun test2_saveQrisImageBitmap_persistsPathInDatastoreAndDecodesSuccessfully() = runBlocking {
        val sampleBitmap = createSampleQrisBitmap()
        val result = prefsRepo.saveQrisImageBitmap(sampleBitmap)

        assertTrue(result.isSuccess)
        val savedPath = result.getOrNull()
        assertNotNull(savedPath)
        assertTrue(File(savedPath!!).exists())

        // Verify DataStore
        val settings = prefsRepo.userSettings.first()
        assertEquals(savedPath, settings.qrisImagePath)

        // Verify Bitmap decodable
        val decoded = BitmapFactory.decodeFile(savedPath)
        assertNotNull(decoded)
        assertEquals(200, decoded.width)
        assertEquals(200, decoded.height)
    }

    @Test
    fun test3_imagePersistence_persistsAcrossRepositoryInstances() = runBlocking {
        val sampleBitmap = createSampleQrisBitmap()
        val result = prefsRepo.saveQrisImageBitmap(sampleBitmap)
        assertTrue(result.isSuccess)
        val savedPath = result.getOrNull()!!

        // Create new repository instance pointing to the same DataStore
        val secondRepo = UserPreferencesRepository(context, testDataStore)
        val secondSettings = secondRepo.userSettings.first()
        assertEquals(savedPath, secondSettings.qrisImagePath)
        assertTrue(File(secondSettings.qrisImagePath).exists())
    }

    @Test
    fun test4_replaceQris_cleansUpOldFilesNoOrphans() = runBlocking {
        val bitmap1 = createSampleQrisBitmap()
        val result1 = prefsRepo.saveQrisImageBitmap(bitmap1)
        assertTrue(result1.isSuccess)
        val oldPath = result1.getOrNull()!!
        assertTrue(File(oldPath).exists())

        // Save a second different image
        val bitmap2 = Bitmap.createBitmap(150, 150, Bitmap.Config.ARGB_8888)
        val result2 = prefsRepo.saveQrisImageBitmap(bitmap2)
        assertTrue(result2.isSuccess)
        val newPath = result2.getOrNull()!!

        // Verify DataStore points to newPath
        val settings = prefsRepo.userSettings.first()
        assertEquals(newPath, settings.qrisImagePath)

        // Verify old file was deleted (no orphans in qris directory)
        val qrisDir = File(context.filesDir, "qris")
        val files = qrisDir.listFiles() ?: emptyArray()
        assertEquals(1, files.size)
        assertEquals(newPath, files[0].absolutePath)
    }

    @Test
    fun test5_deleteQris_deletesFileAndClearsDataStore() = runBlocking {
        val sampleBitmap = createSampleQrisBitmap()
        val result = prefsRepo.saveQrisImageBitmap(sampleBitmap)
        assertTrue(result.isSuccess)
        val savedPath = result.getOrNull()!!
        assertTrue(File(savedPath).exists())

        // Delete QRIS
        val deleted = prefsRepo.deleteQrisImage()
        assertTrue(deleted)

        // Verify DataStore is empty
        val settings = prefsRepo.userSettings.first()
        assertEquals("", settings.qrisImagePath)

        // Verify file deleted from storage
        assertFalse(File(savedPath).exists())
    }

    @Test
    fun test6_qrisCheckout_stockDeductedPaymentMethodQrisCashUnaffected() = runBlocking {
        // Initial state
        val stockBefore = database.productDao().getProductById(testProductId)!!.stock
        assertEquals(20.0, stockBefore, 0.001)

        // Execute QRIS checkout
        val result = productRepository.processAtomicCheckout(mapOf(testProductId to 2.0), "QRIS")
        assertTrue(result.isSuccess)

        // Verify Stock = 18.0
        val stockAfter = database.productDao().getProductById(testProductId)!!.stock
        assertEquals(18.0, stockAfter, 0.001)

        // Verify Payment Method == "QRIS"
        val sales = database.saleDao().getAllTransactions().first()
        assertEquals(1, sales.size)
        assertEquals("QRIS", sales[0].paymentMethod)
        assertEquals(24000L, sales[0].totalAmount)

        // Verify Cash Transaction Balance is NOT increased (0 cash transactions)
        val cashTxs = database.cashDao().getAllCashTransactions().first()
        assertEquals(0, cashTxs.size)
    }

    @Test
    fun test7_returnQrisTransaction_refundsViaCashPhysicalBalance() = runBlocking {
        // 1. Initial cash balance = 50.000
        database.cashDao().insertCashTransaction(
            id.skmnetwork.bukuwarung.data.local.entity.CashTransactionEntity(
                type = "INCOME",
                amount = 50000L,
                description = "Kas Awal Modal"
            )
        )

        // 2. Perform QRIS Sale (2x Kopi Susu = 24.000)
        val checkoutResult = productRepository.processAtomicCheckout(mapOf(testProductId to 2.0), "QRIS")
        assertTrue(checkoutResult.isSuccess)

        val sale = database.saleDao().getAllTransactions().first()[0]
        val saleItems = database.saleDao().getItemsForTransaction(sale.id)

        // 3. Process Full Return for QRIS sale
        val returnResult = saleRepository.processSaleReturn(
            saleId = sale.id,
            itemsToReturn = mapOf(saleItems[0].id to 2.0),
            reason = "Pelanggan batal minum"
        )
        assertTrue(returnResult.isSuccess)

        // Verify Stock Restored to 20.0
        val stockRestored = database.productDao().getProductById(testProductId)!!.stock
        assertEquals(20.0, stockRestored, 0.001)

        // Verify Refund is recorded as CASH EXPENSE (24.000)
        val cashTxs = database.cashDao().getAllCashTransactions().first()
        val expenseTx = cashTxs.find { it.type == "EXPENSE" }
        assertNotNull("Return must record cash EXPENSE refund", expenseTx)
        assertEquals(24000L, expenseTx!!.amount)

        // Net cash balance should be 50.000 - 24.000 = 26.000
        val netBalance = database.cashDao().getTotalCashBalance().first()
        assertEquals(26000L, netBalance)
    }

    @Test
    fun test8_ui_qrisDialog_rendersUnconfiguredStateWhenEmpty() {
        var navigatedToSettings = false

        composeTestRule.setContent {
            QrisPaymentDialog(
                totalPrice = 50000L,
                isCheckingOut = false,
                qrisImagePath = null,
                onNavigateToSettings = { navigatedToSettings = true },
                onDismiss = {},
                onConfirmQrisPayment = {}
            )
        }

        // Must display Unconfigured title and instruction
        composeTestRule.onNodeWithText("QRIS Warung Belum Diatur").assertIsDisplayed()
        composeTestRule.onNodeWithText("Atur QRIS warung Anda di Pengaturan agar pelanggan dapat memindai QRIS dari aplikasi.").assertIsDisplayed()

        // Click "Atur QRIS"
        composeTestRule.onNodeWithText("Atur QRIS").performClick()
        assertTrue(navigatedToSettings)
    }

    @Test
    fun test9_ui_qrisDialog_rendersConfiguredImageWhenPathValid() {
        val sampleBitmap = createSampleQrisBitmap()
        val qrisDir = File(context.filesDir, "qris").apply { if (!exists()) mkdirs() }
        val testFile = File(qrisDir, "test_dialog_qris.jpg")
        FileOutputStream(testFile).use { out ->
            sampleBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }

        var confirmed = false

        composeTestRule.setContent {
            QrisPaymentDialog(
                totalPrice = 35000L,
                isCheckingOut = false,
                qrisImagePath = testFile.absolutePath,
                onNavigateToSettings = {},
                onDismiss = {},
                onConfirmQrisPayment = { confirmed = true }
            )
        }

        // Must display QRIS checkout title, total price, and instruction
        composeTestRule.onNodeWithText("PEMBAYARAN QRIS").assertIsDisplayed()
        composeTestRule.onNodeWithText("Total Belanja").assertIsDisplayed()
        composeTestRule.onNodeWithText("Rp 35.000").assertIsDisplayed()
        composeTestRule.onNodeWithText("Silakan minta pelanggan memindai QRIS warung Anda dan pastikan pembayaran telah masuk.").assertIsDisplayed()

        // Click "SUDAH DIBAYAR"
        composeTestRule.onNodeWithText("SUDAH DIBAYAR").performClick()
        assertTrue(confirmed)
    }
}
