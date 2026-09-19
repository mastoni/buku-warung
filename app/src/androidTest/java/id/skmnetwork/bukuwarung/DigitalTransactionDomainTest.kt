package id.skmnetwork.bukuwarung

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.local.entity.DigitalTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.DigitalTransactionStatus
import id.skmnetwork.bukuwarung.data.local.entity.FulfillmentMode
import id.skmnetwork.bukuwarung.data.local.entity.ItemType
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleTransactionEntity
import id.skmnetwork.bukuwarung.data.repository.DigitalTransactionRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DigitalTransactionDomainTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: DigitalTransactionRepository

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).allowMainThreadQueries().build()

        repository = DigitalTransactionRepository(db.digitalTransactionDao())
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testValidStateTransitions() = runBlocking {
        // Setup initial data
        val categoryId = db.categoryDao().insertCategory(CategoryEntity(name = "Cat"))
        val product = ProductEntity(
            categoryId = categoryId,
            name = "Pulsa 25K",
            purchasePrice = 24000,
            sellingPrice = 26000,
            stock = 0.0,
            itemType = ItemType.DIGITAL.name,
            fulfillmentMode = FulfillmentMode.PROVIDER.name,
            digitalProviderId = "DIGIFLAZZ",
            digitalProductCode = "TSEL25"
        )
        val productId = db.productDao().insertProduct(product)
        
        val sale = SaleTransactionEntity(transactionNumber = "INV-001", totalAmount = 26000)
        val saleId = db.saleDao().insertTransaction(sale)
        
        val saleItem = SaleItemEntity(
            transactionId = saleId,
            productId = productId,
            productName = product.name,
            quantity = 1.0,
            price = product.sellingPrice,
            purchasePrice = product.purchasePrice,
            subtotal = product.sellingPrice
        )
        val saleItemId = db.saleDao().insertSaleItems(listOf(saleItem)).single()


        // 1. Create DRAFT
        val dtId = repository.createDraft(
            saleItemId = saleItemId,
            providerId = product.digitalProviderId!!,
            providerProductCode = product.digitalProductCode!!,
            destinationNumber = "08123456789",
            sellingPrice = product.sellingPrice,
            actualPurchasePrice = product.purchasePrice
        )

        var dt = db.digitalTransactionDao().getById(dtId)
        assertEquals(DigitalTransactionStatus.DRAFT.name, dt?.status)

        // 2. DRAFT -> PENDING
        repository.transitionState(dtId, DigitalTransactionStatus.PENDING)
        dt = db.digitalTransactionDao().getById(dtId)
        assertEquals(DigitalTransactionStatus.PENDING.name, dt?.status)

        // 3. PENDING -> UNKNOWN
        repository.transitionState(dtId, DigitalTransactionStatus.UNKNOWN)
        dt = db.digitalTransactionDao().getById(dtId)
        assertEquals(DigitalTransactionStatus.UNKNOWN.name, dt?.status)

        // 4. UNKNOWN -> SUCCESS
        repository.transitionState(dtId, DigitalTransactionStatus.SUCCESS, snToken = "SN123456")
        dt = db.digitalTransactionDao().getById(dtId)
        assertEquals(DigitalTransactionStatus.SUCCESS.name, dt?.status)
        assertEquals("SN123456", dt?.snToken)
    }

    @Test
    fun testInvalidStateTransitions() = runBlocking {
        // Setup initial data
        val categoryId = db.categoryDao().insertCategory(CategoryEntity(name = "Cat"))
        val product = ProductEntity(
            categoryId = categoryId,
            name = "Pulsa 25K",
            purchasePrice = 24000,
            sellingPrice = 26000,
            stock = 0.0
        )
        val productId = db.productDao().insertProduct(product)
        
        val sale = SaleTransactionEntity(transactionNumber = "INV-002", totalAmount = 26000)
        val saleId = db.saleDao().insertTransaction(sale)
        
        val saleItem = SaleItemEntity(
            transactionId = saleId,
            productId = productId,
            productName = product.name,
            quantity = 1.0,
            price = product.sellingPrice,
            subtotal = product.sellingPrice
        )
        val saleItemId = db.saleDao().insertSaleItems(listOf(saleItem)).single()

        val dtId = repository.createDraft(
            saleItemId = saleItemId,
            providerId = "DIGIFLAZZ",
            providerProductCode = "TSEL25",
            destinationNumber = "08123456789",
            sellingPrice = 26000,
            actualPurchasePrice = 24000
        )

        // Transition to SUCCESS (Invalid from DRAFT)
        var exceptionThrown = false
        try {
            repository.transitionState(dtId, DigitalTransactionStatus.SUCCESS)
        } catch (e: IllegalStateException) {
            exceptionThrown = true
        }
        assertTrue("Expected IllegalStateException", exceptionThrown)

        // Valid to PENDING
        repository.transitionState(dtId, DigitalTransactionStatus.PENDING)

        // Valid to SUCCESS
        repository.transitionState(dtId, DigitalTransactionStatus.SUCCESS)

        // Invalid: SUCCESS -> FAILED
        exceptionThrown = false
        try {
            repository.transitionState(dtId, DigitalTransactionStatus.FAILED)
        } catch (e: IllegalStateException) {
            exceptionThrown = true
        }
        assertTrue("Expected IllegalStateException from SUCCESS to FAILED", exceptionThrown)
    }

    @Test
    fun testSnapshotIntegrity() = runBlocking {
        val categoryId = db.categoryDao().insertCategory(CategoryEntity(name = "Cat"))
        val product = ProductEntity(
            categoryId = categoryId,
            name = "Pulsa 25K",
            purchasePrice = 24000,
            sellingPrice = 26000,
            stock = 0.0
        )
        val productId = db.productDao().insertProduct(product)
        
        val sale = SaleTransactionEntity(transactionNumber = "INV-003", totalAmount = 26000)
        val saleId = db.saleDao().insertTransaction(sale)
        
        val saleItem = SaleItemEntity(
            transactionId = saleId,
            productId = productId,
            productName = product.name,
            quantity = 1.0,
            price = product.sellingPrice,
            subtotal = product.sellingPrice
        )
        val saleItemId = db.saleDao().insertSaleItems(listOf(saleItem)).single()

        val initialPurchasePrice = 24000L
        val dtId = repository.createDraft(
            saleItemId = saleItemId,
            providerId = "DIGIFLAZZ",
            providerProductCode = "TSEL25",
            destinationNumber = "08123456789",
            sellingPrice = 26000,
            actualPurchasePrice = initialPurchasePrice
        )

        // Mutate Product
        db.productDao().updateProduct(product.copy(id = productId, purchasePrice = 26500L))

        // Assert DigitalTransaction actualPurchasePrice remains 24000
        val dt = db.digitalTransactionDao().getById(dtId)
        assertEquals(initialPurchasePrice, dt?.actualPurchasePrice)
    }

    @Test
    fun testCascadeDelete() = runBlocking {
        val categoryId = db.categoryDao().insertCategory(CategoryEntity(name = "Cat"))
        val product = ProductEntity(categoryId = categoryId, name = "Pulsa 25K", purchasePrice = 24000, sellingPrice = 26000, stock = 0.0)
        val productId = db.productDao().insertProduct(product)
        
        val sale = SaleTransactionEntity(transactionNumber = "INV-004", totalAmount = 26000)
        val saleId = db.saleDao().insertTransaction(sale)
        
        val saleItem = SaleItemEntity(transactionId = saleId, productId = productId, productName = product.name, quantity = 1.0, price = product.sellingPrice, subtotal = product.sellingPrice)
        val saleItemId = db.saleDao().insertSaleItems(listOf(saleItem)).single()

        val dtId = repository.createDraft(
            saleItemId = saleItemId,
            providerId = "DIGIFLAZZ",
            providerProductCode = "TSEL25",
            destinationNumber = "08123456789",
            sellingPrice = 26000,
            actualPurchasePrice = 24000
        )

        assertNotNull(db.digitalTransactionDao().getById(dtId))

        // Delete Sale Transaction -> Cascades to SaleItem -> Cascades to DigitalTransaction
        db.query("PRAGMA foreign_keys = ON", null)
        db.compileStatement("DELETE FROM sales_transactions WHERE id = $saleId").execute()

        assertNull("Digital transaction should be cascade deleted", db.digitalTransactionDao().getById(dtId))
    }
}
