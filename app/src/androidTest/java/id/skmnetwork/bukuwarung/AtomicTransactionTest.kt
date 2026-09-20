package id.skmnetwork.bukuwarung

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CashTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseTransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AtomicTransactionTest {

    private lateinit var database: AppDatabase

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testAtomicPurchaseRollbackOnFailure() = runBlocking {
        // 1. Setup Initial Data
        val catId = database.categoryDao().insertCategory(CategoryEntity(name = "Makanan"))
        val prodId = database.productDao().insertProduct(
            ProductEntity(
                categoryId = catId,
                name = "Kopi",
                purchasePrice = 2000,
                sellingPrice = 3000,
                stock = 10.0
            )
        )

        // 2. Execute Purchase Transaction that throws Exception halfway
        var exceptionThrown = false
        try {
            database.withTransaction {
                // Step A: Insert Purchase Transaction
                val purId = database.purchaseDao().insertPurchaseTransaction(
                    PurchaseTransactionEntity(transactionNumber = "PUR-TEST", totalAmount = 5000)
                )

                // Step B: Insert Purchase Item
                database.purchaseDao().insertPurchaseItems(
                    listOf(
                        PurchaseItemEntity(
                            transactionId = purId,
                            productId = prodId,
                            productName = "Kopi",
                            quantity = 5.0,
                            purchasePrice = 2000,
                            subtotal = 10000
                        )
                    )
                )

                // Step C: Increase stock
                database.productDao().addProductStock(prodId, 5.0, System.currentTimeMillis(), "LEGACY_BUSINESS")

                // Step D: Insert Cash Expense
                database.cashDao().insertCashTransaction(
                    CashTransactionEntity(type = "EXPENSE", amount = 5000, description = "Test Purchase")
                )

                // Step E: FORCED FAILURE to trigger ROLLBACK
                throw IllegalStateException("SIMULATED ERROR FOR ROLLBACK TEST")
            }
        } catch (e: IllegalStateException) {
            exceptionThrown = true
        }

        // 3. VERIFY ALL OPERATIONS WERE ROLLED BACK ATOMICALLY
        assertTrue("Exception should have been thrown", exceptionThrown)

        // Verify Purchase Transaction was NOT saved
        val purchases = database.purchaseDao().getAllPurchaseTransactions("LEGACY_BUSINESS").first()
        assertEquals(0, purchases.size)

        // Verify Stock was NOT increased (remains 10.0)
        val product = database.productDao().getProductById(prodId, "LEGACY_BUSINESS")
        assertEquals(10.0, product?.stock ?: 0.0, 0.001)

        // Verify Cash Transaction was NOT saved
        val cashTxs = database.cashDao().getAllCashTransactions("LEGACY_BUSINESS").first()
        assertEquals(0, cashTxs.size)
    }
}


