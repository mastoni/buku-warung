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
class ProductBarcodeTest {

    private lateinit var database: AppDatabase
    private lateinit var productRepository: ProductRepository

    @Before
    fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        productRepository = ProductRepository(database, "LEGACY_BUSINESS")
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testInsertAndQueryProductByBarcode() = runBlocking {
        val catId = database.categoryDao().insertCategory(CategoryEntity(name = "Minuman"))

        // Insert product with barcode '8991234567890'
        val prodId = productRepository.insertProductWithCategory(
            name = "Teh Botol 350ml",
            categoryName = "Minuman",
            purchasePrice = 3000,
            sellingPrice = 4000,
            stock = 50.0,
            minimumStock = 5.0,
            unit = "pcs",
            barcode = "8991234567890",
            imageUri = "/data/user/0/id.skmnetwork.bukuwarung/files/product_images/teh.jpg"
        )

        // Query by barcode
        val foundProduct = productRepository.getProductByBarcode("8991234567890")
        assertNotNull("Product with barcode 8991234567890 must be found", foundProduct)
        assertEquals(prodId, foundProduct?.id)
        assertEquals("Teh Botol 350ml", foundProduct?.name)
        assertEquals("8991234567890", foundProduct?.barcode)
        assertEquals("/data/user/0/id.skmnetwork.bukuwarung/files/product_images/teh.jpg", foundProduct?.imageUri)

        // Query non-existent barcode
        val missingProduct = productRepository.getProductByBarcode("0000000000000")
        assertNull("Product with non-existent barcode must return null", missingProduct)
    }
}


