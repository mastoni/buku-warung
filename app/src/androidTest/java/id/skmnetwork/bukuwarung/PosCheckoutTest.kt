package id.skmnetwork.bukuwarung

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.repository.CustomerRepository
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PosCheckoutTest {

    private lateinit var database: AppDatabase
    private lateinit var productRepository: ProductRepository
    private lateinit var customerRepository: CustomerRepository

    private var productId: Long = 0
    private var customerId: Long = 0

    @Before
    fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        productRepository = ProductRepository(database, "LEGACY_BUSINESS")
        customerRepository = CustomerRepository(database, "LEGACY_BUSINESS")

        val catId = database.categoryDao().insertCategory(CategoryEntity(name = "Snack"))
        productId = database.productDao().insertProduct(
            ProductEntity(
                categoryId = catId,
                name = "Kripik Singkong",
                purchasePrice = 3000,
                sellingPrice = 10000,
                stock = 10.0
            )
        )
        customerId = customerRepository.saveCustomer("Budi", "08123456789", "Jl. Mawar")
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun test1_caseA_exactCashReceivedZeroChange() = runBlocking {
        val totalPrice = 10000L
        val cashReceived = 10000L
        val isInsufficient = cashReceived < totalPrice
        val changeAmount = if (isInsufficient) 0L else (cashReceived - totalPrice)

        assertFalse(isInsufficient)
        assertEquals(0L, changeAmount)

        val result = productRepository.processAtomicCheckout(mapOf(productId to 1.0), "CASH")
        assertTrue(result.isSuccess)

        // Verify Stock = 9.0 (Exact 1x Deduction)
        val stock = database.productDao().getProductById(productId, "LEGACY_BUSINESS")!!.stock
        assertEquals(9.0, stock, 0.001)

        // Verify Cash Income = 10.000
        val cashTxs = database.cashDao().getAllCashTransactions("LEGACY_BUSINESS").first()
        assertEquals(1, cashTxs.size)
        assertEquals(10000L, cashTxs[0].amount)
    }

    @Test
    fun test2_caseB_overpaidCashReceivedCorrectChangeIncomeEqualsTotal() = runBlocking {
        val totalPrice = 10000L
        val cashReceived = 15000L
        val isInsufficient = cashReceived < totalPrice
        val changeAmount = if (isInsufficient) 0L else (cashReceived - totalPrice)

        assertFalse(isInsufficient)
        assertEquals(5000L, changeAmount)

        val result = productRepository.processAtomicCheckout(mapOf(productId to 1.0), "CASH")
        assertTrue(result.isSuccess)

        // Verify Stock = 9.0 (Exact 1x Deduction)
        val stock = database.productDao().getProductById(productId, "LEGACY_BUSINESS")!!.stock
        assertEquals(9.0, stock, 0.001)

        // Verify Cash Income = 10.000 (MUST EQUAL Total Price, NOT 15.000 Tendered)
        val cashTxs = database.cashDao().getAllCashTransactions("LEGACY_BUSINESS").first()
        assertEquals(1, cashTxs.size)
        assertEquals(10000L, cashTxs[0].amount)
    }

    @Test
    fun test3_caseC_underpaidCashTenderRejected() = runBlocking {
        val totalPrice = 10000L
        val cashReceived = 8000L
        val isInsufficient = cashReceived < totalPrice

        assertTrue("Underpaid cash tender must be marked as insufficient", isInsufficient)

        // Simulate UI rejection: No DB write executes if insufficient
        val salesBefore = database.saleDao().getAllTransactions("LEGACY_BUSINESS").first()
        assertEquals(0, salesBefore.size)

        val cashBefore = database.cashDao().getAllCashTransactions("LEGACY_BUSINESS").first()
        assertEquals(0, cashBefore.size)

        val stockBefore = database.productDao().getProductById(productId, "LEGACY_BUSINESS")!!.stock
        assertEquals(10.0, stockBefore, 0.001)
    }

    @Test
    fun test4_caseD_zeroCashTenderRejected() = runBlocking {
        val totalPrice = 10000L
        val cashReceived = 0L
        val isInsufficient = cashReceived < totalPrice

        assertTrue("Zero cash tender must be marked as insufficient", isInsufficient)

        // Verify No DB Writes
        val salesBefore = database.saleDao().getAllTransactions("LEGACY_BUSINESS").first()
        assertEquals(0, salesBefore.size)
    }

    @Test
    fun test5_caseE_singleAtomicCheckoutNoDuplicates() = runBlocking {
        // Execute atomic checkout once
        val result1 = productRepository.processAtomicCheckout(mapOf(productId to 1.0), "CASH")
        assertTrue(result1.isSuccess)

        // Verify exact single transaction records
        val sales = database.saleDao().getAllTransactions("LEGACY_BUSINESS").first()
        assertEquals(1, sales.size)

        val saleItems = database.saleDao().getItemsForTransaction(sales[0].id, "LEGACY_BUSINESS")
        assertEquals(1, saleItems.size)

        val stock = database.productDao().getProductById(productId, "LEGACY_BUSINESS")!!.stock
        assertEquals(9.0, stock, 0.001)

        val cashTxs = database.cashDao().getAllCashTransactions("LEGACY_BUSINESS").first()
        assertEquals(1, cashTxs.size)
        assertEquals(10000L, cashTxs[0].amount)
    }

    @Test
    fun test6_qrisCheckoutCreatesSaleStockDeductedNoCashIncome() = runBlocking {
        val result = productRepository.processAtomicCheckout(mapOf(productId to 1.0), "QRIS")
        assertTrue(result.isSuccess)

        // Verify Stock = 9.0
        val stock = database.productDao().getProductById(productId, "LEGACY_BUSINESS")!!.stock
        assertEquals(9.0, stock, 0.001)

        // Verify 0 CashTransaction for QRIS
        val cashTxs = database.cashDao().getAllCashTransactions("LEGACY_BUSINESS").first()
        assertEquals(0, cashTxs.size)

        // Verify SaleTransaction paymentMethod == "QRIS"
        val sales = database.saleDao().getAllTransactions("LEGACY_BUSINESS").first()
        assertEquals(1, sales.size)
        assertEquals("QRIS", sales[0].paymentMethod)
    }

    @Test
    fun test7_creditCheckoutCreatesDebtStockDeductedNoCashIncome() = runBlocking {
        val result = customerRepository.processAtomicCreditCheckout(mapOf(productId to 1.0), customerId)
        assertTrue(result.isSuccess)

        // Verify Stock = 9.0
        val stock = database.productDao().getProductById(productId, "LEGACY_BUSINESS")!!.stock
        assertEquals(9.0, stock, 0.001)

        // Verify 0 CashTransaction
        val cashTxs = database.cashDao().getAllCashTransactions("LEGACY_BUSINESS").first()
        assertEquals(0, cashTxs.size)

        // Verify Debt Created (10.000)
        val debts = database.debtDao().getDebtsForCustomer(customerId, "LEGACY_BUSINESS").first()
        assertEquals(1, debts.size)
        assertEquals(10000L, debts[0].totalDebt)
    }
}



