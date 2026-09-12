package id.skmnetwork.bukuwarung

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.local.entity.CustomerEntity
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.repository.CustomerRepository
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import kotlinx.coroutines.flow.first
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
class CreditSaleAndDebtTest {

    private lateinit var database: AppDatabase
    private lateinit var productRepository: ProductRepository
    private lateinit var customerRepository: CustomerRepository

    private var customerId: Long = 0
    private var productId: Long = 0

    @Before
    fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        productRepository = ProductRepository(database)
        customerRepository = CustomerRepository(database)

        // Setup base Customer and Product
        val catId = database.categoryDao().insertCategory(CategoryEntity(name = "Umum"))
        productId = database.productDao().insertProduct(
            ProductEntity(
                categoryId = catId,
                name = "Beras",
                purchasePrice = 8000,
                sellingPrice = 10000,
                stock = 20.0
            )
        )
        customerId = customerRepository.saveCustomer("Pak Budi", "08123456789", "Jl. Mawar")
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun test1_creditSaleCreatesDebtNoCashAndDeductsStock() = runBlocking {
        val initialCash = database.cashDao().getTotalCashBalance().first() ?: 0L
        val initialStock = database.productDao().getProductById(productId)!!.stock

        // Perform Credit Sale 1 x Beras = Rp 10.000
        val result = customerRepository.processAtomicCreditCheckout(
            cartItems = mapOf(productId to 1.0),
            customerId = customerId
        )
        assertTrue("Credit sale should succeed", result.isSuccess)

        // Verify Sales Transaction
        val sales = database.saleDao().getAllTransactions().first()
        assertEquals(1, sales.size)
        assertEquals("CREDIT", sales[0].paymentMethod)
        assertEquals(customerId, sales[0].customerId)

        // Verify Stock Deducted (20.0 - 1.0 = 19.0)
        val currentStock = database.productDao().getProductById(productId)!!.stock
        assertEquals(19.0, currentStock, 0.001)

        // Verify NO Cash Income created (Cash Balance unchanged)
        val currentCash = database.cashDao().getTotalCashBalance().first() ?: 0L
        assertEquals(initialCash, currentCash)

        // Verify Debt Record Created (totalDebt = 10000, paidAmount = 0, status = OPEN)
        val debts = database.debtDao().getDebtsForCustomer(customerId).first()
        assertEquals(1, debts.size)
        val debt = debts[0]
        assertEquals(10000L, debt.totalDebt)
        assertEquals(0L, debt.paidAmount)
        assertEquals("OPEN", debt.status)
    }

    @Test
    fun test2_partialDebtPaymentUpdatesPaidAmountOutstandingAndCash() = runBlocking {
        // Step 1: Credit Sale Rp 10.000
        customerRepository.processAtomicCreditCheckout(
            cartItems = mapOf(productId to 1.0),
            customerId = customerId
        )
        val debtId = database.debtDao().getDebtsForCustomer(customerId).first()[0].id

        // Step 2: Pay Rp 4.000
        val payResult = customerRepository.processAtomicDebtPayment(debtId, 4000L, "Cicilan 1")
        assertTrue("Debt payment should succeed", payResult.isSuccess)

        // Verify Debt State
        val debt = database.debtDao().getDebtById(debtId)!!
        assertEquals(10000L, debt.totalDebt)
        assertEquals(4000L, debt.paidAmount)
        assertEquals("OPEN", debt.status)
        assertEquals(6000L, debt.totalDebt - debt.paidAmount)

        // Verify Cash Income Created (+4.000)
        val currentCash = database.cashDao().getTotalCashBalance().first() ?: 0L
        assertEquals(4000L, currentCash)
    }

    @Test
    fun test3_fullDebtPaymentMarksStatusPaidAndUpdatesCash() = runBlocking {
        // Step 1: Credit Sale Rp 10.000
        customerRepository.processAtomicCreditCheckout(
            cartItems = mapOf(productId to 1.0),
            customerId = customerId
        )
        val debtId = database.debtDao().getDebtsForCustomer(customerId).first()[0].id

        // Step 2: Pay Rp 4.000
        customerRepository.processAtomicDebtPayment(debtId, 4000L, "Cicilan 1")

        // Step 3: Pay remaining Rp 6.000
        val payResult2 = customerRepository.processAtomicDebtPayment(debtId, 6000L, "Pelunasan")
        assertTrue("Final debt payment should succeed", payResult2.isSuccess)

        // Verify Debt State (paidAmount = 10000, outstanding = 0, status = PAID)
        val debt = database.debtDao().getDebtById(debtId)!!
        assertEquals(10000L, debt.paidAmount)
        assertEquals(0L, debt.totalDebt - debt.paidAmount)
        assertEquals("PAID", debt.status)

        // Verify Cash Income Total (+10.000)
        val currentCash = database.cashDao().getTotalCashBalance().first() ?: 0L
        assertEquals(10000L, currentCash)
    }

    @Test
    fun test4_overpaymentIsRejectedWithoutDbWrites() = runBlocking {
        // Step 1: Credit Sale Rp 10.000
        customerRepository.processAtomicCreditCheckout(
            cartItems = mapOf(productId to 1.0),
            customerId = customerId
        )
        val debtId = database.debtDao().getDebtsForCustomer(customerId).first()[0].id

        // Step 2: Attempt Overpayment Rp 10.001
        val overpayResult = customerRepository.processAtomicDebtPayment(debtId, 10001L, "Overpay")
        assertTrue("Overpayment must fail", overpayResult.isFailure)

        // Verify Debt Unchanged
        val debt = database.debtDao().getDebtById(debtId)!!
        assertEquals(0L, debt.paidAmount)

        // Verify No Debt Payments recorded
        val payments = database.debtDao().getPaymentsListForDebt(debtId)
        assertEquals(0, payments.size)

        // Verify No Cash Income recorded
        val currentCash = database.cashDao().getTotalCashBalance().first() ?: 0L
        assertEquals(0L, currentCash)
    }

    @Test
    fun test5_atomicRollbackOnCreditSaleAndPaymentFailure() = runBlocking {
        val initialStock = database.productDao().getProductById(productId)!!.stock

        // Forced Failure in Credit Sale
        var failedSale = false
        try {
            database.withTransaction {
                // Sale, Items, Stock deduction, Debt
                customerRepository.processAtomicCreditCheckout(mapOf(productId to 1.0), customerId)
                throw IllegalStateException("SIMULATED CREDIT SALE FAILURE")
            }
        } catch (e: Exception) {
            failedSale = true
        }
        assertTrue(failedSale)

        // Verify 0 Sales, 0 Debts, Stock Unchanged
        val sales = database.saleDao().getAllTransactions().first()
        assertEquals(0, sales.size)
        val debts = database.debtDao().getDebtsForCustomer(customerId).first()
        assertEquals(0, debts.size)
        assertEquals(initialStock, database.productDao().getProductById(productId)!!.stock, 0.001)
    }

    @Test
    fun test6_regressionCashSaleCreatesCashNoDebt() = runBlocking {
        val initialCash = database.cashDao().getTotalCashBalance().first() ?: 0L

        // Cash Sale 1 x Beras = Rp 10.000
        val result = productRepository.processAtomicCheckout(mapOf(productId to 1.0))
        assertTrue(result.isSuccess)

        // Verify Cash Transaction Created
        val currentCash = database.cashDao().getTotalCashBalance().first() ?: 0L
        assertEquals(initialCash + 10000L, currentCash)

        // Verify NO Debt Created
        val debts = database.debtDao().getDebtsForCustomer(customerId).first()
        assertEquals(0, debts.size)
    }

    @Test
    fun test7_reconciliationPaidAmountEqualsSumPaymentsAndOutstanding() = runBlocking {
        // Step 1: Credit Sale Rp 10.000
        customerRepository.processAtomicCreditCheckout(mapOf(productId to 1.0), customerId)
        val debtId = database.debtDao().getDebtsForCustomer(customerId).first()[0].id

        // Step 2: Multiple Payments (2500 + 2500 = 5000)
        customerRepository.processAtomicDebtPayment(debtId, 2500L, "Bayar 1")
        customerRepository.processAtomicDebtPayment(debtId, 2500L, "Bayar 2")

        // Reconciliation Assertion
        val debt = database.debtDao().getDebtById(debtId)!!
        val payments = database.debtDao().getPaymentsListForDebt(debtId)
        val paymentSum = payments.sumOf { it.amount }

        assertEquals(paymentSum, debt.paidAmount) // paidAmount == SUM(payments)
        assertEquals(debt.totalDebt - debt.paidAmount, 5000L) // outstanding == totalDebt - paidAmount
    }
}
