package id.skmnetwork.bukuwarung

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductCategoryGateATest {

    private lateinit var database: AppDatabase
    private lateinit var repository: ProductRepository

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        repository = ProductRepository(database, "LEGACY_BUSINESS")
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testValidationA_ExistingCategorySelection() = runBlocking {
        // 1. Ensure database has ATK, CCTV, Elektronik
        val atkId = repository.createCategory("ATK").getOrThrow().id
        val cctvId = repository.createCategory("CCTV").getOrThrow().id
        val elektronikId = repository.createCategory("Elektronik").getOrThrow().id

        val allCats = repository.allCategories.first()
        assertEquals(3, allCats.size)
        assertTrue(allCats.any { it.name == "ATK" && it.id == atkId })
        assertTrue(allCats.any { it.name == "CCTV" && it.id == cctvId })
        assertTrue(allCats.any { it.name == "Elektronik" && it.id == elektronikId })

        // 2. Add product choosing CCTV
        val prodId = repository.insertProductWithCategory(
            name = "Kamera IP Outdoor",
            categoryName = "CCTV",
            purchasePrice = 300000,
            sellingPrice = 450000,
            stock = 10.0,
            minimumStock = 2.0,
            unit = "unit",
            categoryId = cctvId
        )

        // 3. Verify product category is linked to CCTV
        val product = repository.getProductById(prodId)
        assertNotNull(product)
        assertEquals(cctvId, product?.categoryId)

        val catName = repository.getCategoryById(product!!.categoryId)?.name
        assertEquals("CCTV", catName)
    }

    @Test
    fun testValidationB_CreateNewCategory() = runBlocking {
        // 1. Create new category "Kabel"
        val createResult = repository.createCategory("Kabel")
        assertTrue(createResult.isSuccess)
        val kabelCat = createResult.getOrThrow()
        assertEquals("Kabel", kabelCat.name)
        assertTrue(kabelCat.id > 0)

        // 2. Save product with "Kabel"
        val prod1Id = repository.insertProductWithCategory(
            name = "Kabel LAN Cat6 50m",
            categoryName = kabelCat.name,
            purchasePrice = 150000,
            sellingPrice = 200000,
            stock = 15.0,
            minimumStock = 3.0,
            unit = "roll",
            categoryId = kabelCat.id
        )

        val savedProd1 = repository.getProductById(prod1Id)
        assertNotNull(savedProd1)
        assertEquals(kabelCat.id, savedProd1?.categoryId)

        // 3. Verify "Kabel" is available in allCategories for second product
        val availableCats = repository.allCategories.first()
        assertTrue(availableCats.any { it.name == "Kabel" && it.id == kabelCat.id })

        val prod2Id = repository.insertProductWithCategory(
            name = "Kabel HDMI 2m",
            categoryName = kabelCat.name,
            purchasePrice = 25000,
            sellingPrice = 40000,
            stock = 20.0,
            minimumStock = 5.0,
            unit = "pcs",
            categoryId = kabelCat.id
        )

        val savedProd2 = repository.getProductById(prod2Id)
        assertNotNull(savedProd2)
        assertEquals(kabelCat.id, savedProd2?.categoryId)
    }

    @Test
    fun testValidationC_EmptyCategoryStateAndCreation() = runBlocking {
        // 1. Verify initially empty
        val initialCats = repository.allCategories.first()
        assertEquals(0, initialCats.size)

        // 2. Create new category when empty
        val result = repository.createCategory("Makanan Ringan")
        assertTrue(result.isSuccess)
        val created = result.getOrThrow()

        // 3. Category immediately usable
        val afterCats = repository.allCategories.first()
        assertEquals(1, afterCats.size)
        assertEquals("Makanan Ringan", afterCats.first().name)
        assertEquals(created.id, afterCats.first().id)
    }

    @Test
    fun testValidationD_DuplicateCategoryHandling() = runBlocking {
        // 1. Create category "ATK"
        val cat1 = repository.createCategory("ATK").getOrThrow()

        // 2. Try creating duplicate category "atk" (lowercase) or "ATK"
        val cat2 = repository.createCategory("atk").getOrThrow()
        val cat3 = repository.createCategory("  ATK  ").getOrThrow()

        // 3. Verify duplicate was prevented and existing category ID is returned
        assertEquals(cat1.id, cat2.id)
        assertEquals(cat1.id, cat3.id)

        val allCats = repository.allCategories.first()
        assertEquals(1, allCats.size)
    }
}


