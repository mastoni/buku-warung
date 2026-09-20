package id.skmnetwork.bukuwarung

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ProductImageRenderingTest {

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
    fun testProductImageNullAndValidAndEditPreservation() = runBlocking {
        // 1. Insert product with null imageUri
        val prodId1 = productRepository.insertProductWithCategory(
            name = "Indomie Goreng",
            categoryName = "Makanan",
            purchasePrice = 2500,
            sellingPrice = 3500,
            stock = 20.0,
            minimumStock = 2.0,
            unit = "pcs",
            imageUri = null
        )

        val prod1 = productRepository.getProductById(prodId1)
        assertNotNull(prod1)
        assertNull("ImageUri must be null for product created without photo", prod1?.imageUri)

        // 2. Insert product with valid local image path
        val mockImagePath = "/data/user/0/id.skmnetwork.bukuwarung/files/product_images/prod_test.jpg"
        val prodId2 = productRepository.insertProductWithCategory(
            name = "Susu UHT 250ml",
            categoryName = "Minuman",
            purchasePrice = 4000,
            sellingPrice = 5500,
            stock = 15.0,
            minimumStock = 3.0,
            unit = "pcs",
            imageUri = mockImagePath
        )

        val prod2 = productRepository.getProductById(prodId2)
        assertNotNull(prod2)
        assertEquals(mockImagePath, prod2?.imageUri)

        // 3. Edit product fields while preserving imageUri
        productRepository.updateProductWithCategory(
            productId = prodId2,
            name = "Susu UHT 250ml Cokelat",
            categoryName = "Minuman",
            purchasePrice = 4000,
            sellingPrice = 6000,
            stock = 15.0,
            minimumStock = 3.0,
            unit = "pcs",
            imageUri = mockImagePath
        )

        val updatedProd2 = productRepository.getProductById(prodId2)
        assertNotNull(updatedProd2)
        assertEquals("Susu UHT 250ml Cokelat", updatedProd2?.name)
        assertEquals(mockImagePath, updatedProd2?.imageUri) // Image preserved

        // 4. Replace imageUri with new path
        val newImagePath = "/data/user/0/id.skmnetwork.bukuwarung/files/product_images/prod_test_new.jpg"
        productRepository.updateProductWithCategory(
            productId = prodId2,
            name = "Susu UHT 250ml Cokelat",
            categoryName = "Minuman",
            purchasePrice = 4000,
            sellingPrice = 6000,
            stock = 15.0,
            minimumStock = 3.0,
            unit = "pcs",
            imageUri = newImagePath
        )

        val replacedProd2 = productRepository.getProductById(prodId2)
        assertNotNull(replacedProd2)
        assertEquals(newImagePath, replacedProd2?.imageUri) // Image replaced
    }
}


