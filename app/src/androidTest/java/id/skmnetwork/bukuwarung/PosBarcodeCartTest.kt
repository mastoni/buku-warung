package id.skmnetwork.bukuwarung

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PosBarcodeCartTest {

    private lateinit var database: AppDatabase
    private lateinit var productRepository: ProductRepository

    private var normalProdId: Long = 0
    private var zeroStockProdId: Long = 0
    private var limitedStockProdId: Long = 0
    private var deletedProdId: Long = 0

    @Before
    fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        productRepository = ProductRepository(database)

        val catId = database.categoryDao().insertCategory(CategoryEntity(name = "Minuman"))

        // Product 1: Normal with barcode (Stock = 10)
        normalProdId = database.productDao().insertProduct(
            ProductEntity(
                categoryId = catId,
                name = "Teh Botol 350ml",
                purchasePrice = 3000,
                sellingPrice = 4500,
                stock = 10.0,
                barcode = "8991234567890"
            )
        )

        // Product 2: Zero stock with barcode (Stock = 0)
        zeroStockProdId = database.productDao().insertProduct(
            ProductEntity(
                categoryId = catId,
                name = "Kopi Susu Kaleng",
                purchasePrice = 5000,
                sellingPrice = 7000,
                stock = 0.0,
                barcode = "8997778889990"
            )
        )

        // Product 3: Limited stock with barcode (Stock = 2)
        limitedStockProdId = database.productDao().insertProduct(
            ProductEntity(
                categoryId = catId,
                name = "Air Mineral 600ml",
                purchasePrice = 2000,
                sellingPrice = 3000,
                stock = 2.0,
                barcode = "8993334445550"
            )
        )

        // Product 4: Deleted product with barcode (is_deleted = 1)
        deletedProdId = database.productDao().insertProduct(
            ProductEntity(
                categoryId = catId,
                name = "Produk Lama Nonaktif",
                purchasePrice = 1000,
                sellingPrice = 2000,
                stock = 5.0,
                barcode = "8999999999999",
                isDeleted = true
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    // Helper simulating POS scan-to-cart logic
    private suspend fun simulateScanToCart(
        scannedBarcode: String,
        cart: MutableMap<Long, Double>
    ): String {
        val cleanBarcode = scannedBarcode.trim()
        if (cleanBarcode.isEmpty()) return "IGNORED_EMPTY"

        val matchedProduct = productRepository.getProductByBarcode(cleanBarcode)
        return if (matchedProduct != null) {
            if (matchedProduct.stock <= 0.0) {
                "OUT_OF_STOCK"
            } else {
                val currentQty = cart[matchedProduct.id] ?: 0.0
                if (currentQty + 1.0 <= matchedProduct.stock) {
                    cart[matchedProduct.id] = currentQty + 1.0
                    "ADDED"
                } else {
                    "STOCK_LIMIT_EXCEEDED"
                }
            }
        } else {
            "NOT_FOUND"
        }
    }

    @Test
    fun testPriorityA_barcodeFound_addsToCart() = runBlocking {
        val cart = mutableMapOf<Long, Double>()

        val result = simulateScanToCart("8991234567890", cart)
        assertEquals("ADDED", result)
        assertEquals(1.0, cart[normalProdId] ?: 0.0, 0.0)
        assertEquals(1, cart.size)
    }

    @Test
    fun testPriorityB_sameBarcodeScannedAgain_incrementsQty() = runBlocking {
        val cart = mutableMapOf<Long, Double>()

        // 1st scan
        val result1 = simulateScanToCart("8991234567890", cart)
        assertEquals("ADDED", result1)
        assertEquals(1.0, cart[normalProdId] ?: 0.0, 0.0)

        // 2nd scan
        val result2 = simulateScanToCart("8991234567890", cart)
        assertEquals("ADDED", result2)
        assertEquals(2.0, cart[normalProdId] ?: 0.0, 0.0)
        assertEquals(1, cart.size)
    }

    @Test
    fun testPriorityC_barcodeNotFound_cartUnchanged() = runBlocking {
        val cart = mutableMapOf<Long, Double>()
        cart[normalProdId] = 1.0

        // Unregistered barcode
        val resultNotFound = simulateScanToCart("0000000000000", cart)
        assertEquals("NOT_FOUND", resultNotFound)
        assertEquals(1.0, cart[normalProdId] ?: 0.0, 0.0)
        assertEquals(1, cart.size)

        // Empty barcode
        val resultEmpty = simulateScanToCart("   ", cart)
        assertEquals("IGNORED_EMPTY", resultEmpty)
        assertEquals(1.0, cart[normalProdId] ?: 0.0, 0.0)
        assertEquals(1, cart.size)
    }

    @Test
    fun testPriorityD_stockZero_cartUnchanged() = runBlocking {
        val cart = mutableMapOf<Long, Double>()

        val result = simulateScanToCart("8997778889990", cart)
        assertEquals("OUT_OF_STOCK", result)
        assertEquals(0, cart.size)
    }

    @Test
    fun testPriorityE_insufficientStock_cartDoesNotExceedStock() = runBlocking {
        val cart = mutableMapOf<Long, Double>()

        // 1st scan (stock = 2, qty -> 1)
        val result1 = simulateScanToCart("8993334445550", cart)
        assertEquals("ADDED", result1)
        assertEquals(1.0, cart[limitedStockProdId] ?: 0.0, 0.0)

        // 2nd scan (stock = 2, qty -> 2)
        val result2 = simulateScanToCart("8993334445550", cart)
        assertEquals("ADDED", result2)
        assertEquals(2.0, cart[limitedStockProdId] ?: 0.0, 0.0)

        // 3rd scan (stock = 2, qty cannot exceed 2)
        val result3 = simulateScanToCart("8993334445550", cart)
        assertEquals("STOCK_LIMIT_EXCEEDED", result3)
        assertEquals(2.0, cart[limitedStockProdId] ?: 0.0, 0.0)
    }

    @Test
    fun testDeletedProductWithBarcode_cannotBeAddedToCart() = runBlocking {
        val cart = mutableMapOf<Long, Double>()

        val result = simulateScanToCart("8999999999999", cart)
        assertEquals("NOT_FOUND", result)
        assertEquals(0, cart.size)
    }
}
