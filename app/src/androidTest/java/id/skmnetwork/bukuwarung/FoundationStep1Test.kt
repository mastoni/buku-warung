package id.skmnetwork.bukuwarung

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.local.entity.ItemType
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import id.skmnetwork.bukuwarung.data.repository.CustomerRepository
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import id.skmnetwork.bukuwarung.data.repository.SupplierRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FoundationStep1Test {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var productRepository: ProductRepository
    private lateinit var customerRepository: CustomerRepository
    private lateinit var supplierRepository: SupplierRepository
    private lateinit var userPreferencesRepository: UserPreferencesRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        productRepository = ProductRepository(database)
        customerRepository = CustomerRepository(database)
        supplierRepository = SupplierRepository(database)
        userPreferencesRepository = UserPreferencesRepository(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testBusinessIdAndDeviceIdPersistence() = runBlocking {
        val businessId1 = userPreferencesRepository.getOrCreateBusinessId()
        val deviceId1 = userPreferencesRepository.getOrCreateDeviceId()

        assertTrue("businessId must be non-empty", businessId1.isNotEmpty())
        assertTrue("deviceId must be non-empty", deviceId1.isNotEmpty())

        // Validate UUID format (36 chars with dashes)
        assertEquals(36, businessId1.length)
        assertEquals(36, deviceId1.length)
        assertNotNull(UUID.fromString(businessId1))
        assertNotNull(UUID.fromString(deviceId1))

        // Re-read to guarantee persistence
        val businessId2 = userPreferencesRepository.getOrCreateBusinessId()
        val deviceId2 = userPreferencesRepository.getOrCreateDeviceId()

        assertEquals("businessId must be persistent", businessId1, businessId2)
        assertEquals("deviceId must be persistent", deviceId1, deviceId2)
    }

    @Test
    fun testDefaultItemTypeIsPhysical() = runBlocking {
        val prodId = productRepository.insertProductWithCategory(
            name = "Teh Botol",
            categoryName = "Minuman",
            purchasePrice = 3000,
            sellingPrice = 4000,
            stock = 10.0,
            minimumStock = 2.0,
            unit = "botol"
        )

        val product = productRepository.getProductById(prodId)
        assertNotNull(product)
        assertEquals(ItemType.PHYSICAL.name, product?.itemType)
        assertTrue("UUID must be generated", product?.uuid?.isNotEmpty() == true)
        assertEquals(36, product?.uuid?.length)
    }

    @Test
    fun testStockMovementOnSaleAndPurchaseAndInvariant() = runBlocking {
        // 1. Create Product with initial stock 20
        val prodId = productRepository.insertProductWithCategory(
            name = "Indomie Goreng",
            categoryName = "Makanan",
            purchasePrice = 2500,
            sellingPrice = 3500,
            stock = 20.0,
            minimumStock = 5.0,
            unit = "bungkus"
        )
        val product = productRepository.getProductById(prodId)!!

        // Verify Initial StockMovement
        var movements = database.stockMovementDao().getMovementsListForProduct(product.uuid)
        assertEquals(1, movements.size)
        assertEquals("INITIAL", movements[0].movementType)
        assertEquals(20.0, movements[0].deltaQuantity, 0.001)

        // 2. Perform Cash Sale: 5 pcs
        val saleResult = productRepository.processAtomicCheckout(
            cartItems = mapOf(prodId to 5.0),
            paymentMethod = "CASH"
        )
        assertTrue(saleResult.isSuccess)

        // Verify Product Stock & Movement
        val afterSaleProd = productRepository.getProductById(prodId)!!
        assertEquals(15.0, afterSaleProd.stock, 0.001)

        movements = database.stockMovementDao().getMovementsListForProduct(product.uuid)
        assertEquals(2, movements.size)
        assertEquals("SALE", movements[1].movementType)
        assertEquals(-5.0, movements[1].deltaQuantity, 0.001)
        assertEquals(15.0, movements[1].currentStockSnapshot, 0.001)

        // 3. Perform Cash Purchase: 10 pcs
        val purchaseResult = productRepository.processAtomicPurchase(
            purchaseItems = mapOf(prodId to 10.0)
        )
        assertTrue(purchaseResult.isSuccess)

        // Verify Product Stock & Movement
        val afterPurchaseProd = productRepository.getProductById(prodId)!!
        assertEquals(25.0, afterPurchaseProd.stock, 0.001)

        movements = database.stockMovementDao().getMovementsListForProduct(product.uuid)
        assertEquals(3, movements.size)
        assertEquals("PURCHASE", movements[2].movementType)
        assertEquals(10.0, movements[2].deltaQuantity, 0.001)
        assertEquals(25.0, movements[2].currentStockSnapshot, 0.001)

        // 4. CRITICAL INVARIANT VERIFICATION: SUM(deltaQuantity) == Product.stock
        val calculatedStock = database.stockMovementDao().getCalculatedStockForProduct(product.uuid)
        assertEquals(afterPurchaseProd.stock, calculatedStock, 0.001)
        assertEquals(25.0, calculatedStock, 0.001)
    }

    @Test
    fun testCreditSaleStockMovement() = runBlocking {
        val custId = customerRepository.saveCustomer("Pak Joko", "0812345678", "Jl. Solo")
        val prodId = productRepository.insertProductWithCategory(
            name = "Telur 1kg",
            categoryName = "Sembako",
            purchasePrice = 22000,
            sellingPrice = 28000,
            stock = 10.0,
            minimumStock = 2.0,
            unit = "kg"
        )
        val product = productRepository.getProductById(prodId)!!

        val creditSaleResult = customerRepository.processAtomicCreditCheckout(
            cartItems = mapOf(prodId to 3.0),
            customerId = custId
        )
        assertTrue(creditSaleResult.isSuccess)

        val updatedProd = productRepository.getProductById(prodId)!!
        assertEquals(7.0, updatedProd.stock, 0.001)

        val movements = database.stockMovementDao().getMovementsListForProduct(product.uuid)
        assertEquals(2, movements.size)
        assertEquals("SALE", movements[1].movementType)
        assertEquals(-3.0, movements[1].deltaQuantity, 0.001)

        val calculatedStock = database.stockMovementDao().getCalculatedStockForProduct(product.uuid)
        assertEquals(7.0, calculatedStock, 0.001)
    }

    @Test
    fun testCreditPurchaseStockMovement() = runBlocking {
        val suppId = supplierRepository.saveSupplier("Distributor Telur", "0898765432", "Pasar")
        val prodId = productRepository.insertProductWithCategory(
            name = "Gula Pasir 1kg",
            categoryName = "Sembako",
            purchasePrice = 14000,
            sellingPrice = 17000,
            stock = 5.0,
            minimumStock = 1.0,
            unit = "kg"
        )
        val product = productRepository.getProductById(prodId)!!

        val creditPurResult = supplierRepository.processAtomicCreditPurchase(
            purchaseItems = mapOf(prodId to 15.0),
            supplierId = suppId
        )
        assertTrue(creditPurResult.isSuccess)

        val updatedProd = productRepository.getProductById(prodId)!!
        assertEquals(20.0, updatedProd.stock, 0.001)

        val movements = database.stockMovementDao().getMovementsListForProduct(product.uuid)
        assertEquals(2, movements.size)
        assertEquals("PURCHASE", movements[1].movementType)
        assertEquals(15.0, movements[1].deltaQuantity, 0.001)

        val calculatedStock = database.stockMovementDao().getCalculatedStockForProduct(product.uuid)
        assertEquals(20.0, calculatedStock, 0.001)
    }

    @Test
    fun testSoftDeleteHidesFromActiveQueries() = runBlocking {
        val prodId = productRepository.insertProductWithCategory(
            name = "Sabun Mandi",
            categoryName = "Toiletries",
            purchasePrice = 3000,
            sellingPrice = 4500,
            stock = 10.0,
            minimumStock = 2.0,
            unit = "pcs"
        )

        var products = productRepository.allProducts.first()
        assertEquals(1, products.size)

        // Soft Delete
        productRepository.deleteProductById(prodId)

        // Active Flow must be empty
        products = productRepository.allProducts.first()
        assertEquals(0, products.size)

        // Direct lookup by ID via DAO should return null because is_deleted = 1
        val lookedUp = productRepository.getProductById(prodId)
        assertNull(lookedUp)
    }

    @Test
    fun testLegacyBusinessReconciliation() = runBlocking {
        // Insert record with default LEGACY_BUSINESS
        val category = CategoryEntity(
            name = "Kategori Lawas",
            businessId = "LEGACY_BUSINESS"
        )
        database.categoryDao().insertCategory(category)

        val persistentBusinessId = UUID.randomUUID().toString()
        userPreferencesRepository.reconcileLegacyBusinessIdentity(database, persistentBusinessId)

        val updatedCategory = database.categoryDao().getAllCategories().first()[0]
        assertEquals("businessId must be reconciled", persistentBusinessId, updatedCategory.businessId)
    }
}
