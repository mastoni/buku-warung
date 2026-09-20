package id.skmnetwork.bukuwarung

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.local.entity.CustomerEntity
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.repository.CustomerRepository
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import id.skmnetwork.bukuwarung.data.repository.ReportRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReportAndAggregationTest {

    private lateinit var database: AppDatabase
    private lateinit var productRepository: ProductRepository
    private lateinit var customerRepository: CustomerRepository
    private lateinit var reportRepository: ReportRepository

    private var customerId: Long = 0
    private var productId: Long = 0

    @Before
    fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        productRepository = ProductRepository(database, "LEGACY_BUSINESS")
        customerRepository = CustomerRepository(database, "LEGACY_BUSINESS")
        reportRepository = ReportRepository(database, "LEGACY_BUSINESS")

        val catId = database.categoryDao().insertCategory(CategoryEntity(name = "Sembako"))
        productId = database.productDao().insertProduct(
            ProductEntity(
                categoryId = catId,
                name = "Minyak Goreng",
                purchasePrice = 12000,
                sellingPrice = 15000,
                stock = 50.0
            )
        )
        customerId = customerRepository.saveCustomer("Ibu Siti", "08198765432", "Jl. Melati No. 2")
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun test1_salesAggregation() = runBlocking {
        // 1. Cash Sale: 2 x Minyak = Rp 30.000
        productRepository.processAtomicCheckout(mapOf(productId to 2.0))

        // 2. Credit Sale: 1 x Minyak = Rp 15.000
        customerRepository.processAtomicCreditCheckout(mapOf(productId to 1.0), customerId)

        val start = 0L
        val end = Long.MAX_VALUE

        val totalSales = reportRepository.getSalesTotal(start, end).first() ?: 0L
        val salesCount = reportRepository.getSalesCount(start, end).first()
        val itemsSold = reportRepository.getItemsSoldTotal(start, end).first() ?: 0.0
        val cashSales = reportRepository.getCashSalesTotal(start, end).first() ?: 0L
        val creditSales = reportRepository.getCreditSalesTotal(start, end).first() ?: 0L

        assertEquals(45000L, totalSales)
        assertEquals(2, salesCount)
        assertEquals(3.0, itemsSold, 0.001)
        assertEquals(30000L, cashSales)
        assertEquals(15000L, creditSales)
    }

    @Test
    fun test2_purchaseAggregation() = runBlocking {
        // Restock Purchase: 10 x Minyak @ Rp 12.000 = Rp 120.000
        productRepository.processAtomicPurchase(mapOf(productId to 10.0))

        val start = 0L
        val end = Long.MAX_VALUE

        val purchaseTotal = reportRepository.getPurchaseTotal(start, end).first() ?: 0L
        val purchaseCount = reportRepository.getPurchaseCount(start, end).first()
        val itemsPurchased = reportRepository.getItemsPurchasedTotal(start, end).first() ?: 0.0

        assertEquals(120000L, purchaseTotal)
        assertEquals(1, purchaseCount)
        assertEquals(10.0, itemsPurchased, 0.001)
    }

    @Test
    fun test3_cashAggregationAndNetMovement() = runBlocking {
        // Cash Sale (+30.000)
        productRepository.processAtomicCheckout(mapOf(productId to 2.0))

        // Manual Expense (-10.000)
        productRepository.addManualCashTransaction("EXPENSE", 10000L, "Bayar Listrik")

        val start = 0L
        val end = Long.MAX_VALUE

        val income = reportRepository.getCashIncomeTotal(start, end).first() ?: 0L
        val expense = reportRepository.getCashExpenseTotal(start, end).first() ?: 0L
        val netCash = income - expense

        assertEquals(30000L, income)
        assertEquals(10000L, expense)
        assertEquals(20000L, netCash)
    }

    @Test
    fun test4_creditSaleDoesNotIncreaseCashAndDebtPaymentDoes() = runBlocking {
        val start = 0L
        val end = Long.MAX_VALUE

        // Credit Sale Rp 15.000
        customerRepository.processAtomicCreditCheckout(mapOf(productId to 1.0), customerId)

        val cashIncomeAfterCredit = reportRepository.getCashIncomeTotal(start, end).first() ?: 0L
        assertEquals(0L, cashIncomeAfterCredit) // Credit sale does NOT increase cash

        // Debt Payment Rp 5.000
        val debtId = database.debtDao().getDebtsForCustomer(customerId, "LEGACY_BUSINESS").first()[0].id
        customerRepository.processAtomicDebtPayment(debtId, 5000L, "Cicilan")

        val cashIncomeAfterPayment = reportRepository.getCashIncomeTotal(start, end).first() ?: 0L
        assertEquals(5000L, cashIncomeAfterPayment) // Debt payment DOES increase cash
    }

    @Test
    fun test5_financialReconciliationBetweenTransactionsAndReports() = runBlocking {
        // 1. Cash Sale: Rp 30.000
        productRepository.processAtomicCheckout(mapOf(productId to 2.0))

        // 2. Credit Sale: Rp 15.000
        customerRepository.processAtomicCreditCheckout(mapOf(productId to 1.0), customerId)

        // 3. Purchase Restock: Rp 24.000
        productRepository.processAtomicPurchase(mapOf(productId to 2.0))

        // 4. Manual Income: Rp 50.000
        productRepository.addManualCashTransaction("INCOME", 50000L, "Modal Awal")

        // 5. Debt Payment: Rp 10.000
        val debtId = database.debtDao().getDebtsForCustomer(customerId, "LEGACY_BUSINESS").first()[0].id
        customerRepository.processAtomicDebtPayment(debtId, 10000L, "Pelunasan Sebagian")

        val start = 0L
        val end = Long.MAX_VALUE

        // Reconcile Cash Income = Cash Sales (30k) + Debt Payments (10k) + Manual Income (50k) = 90k
        val cashIncome = reportRepository.getCashIncomeTotal(start, end).first() ?: 0L
        assertEquals(90000L, cashIncome)

        // Reconcile Cash Expense = Purchases (24k) + Manual Expenses (0) = 24k
        val cashExpense = reportRepository.getCashExpenseTotal(start, end).first() ?: 0L
        assertEquals(24000L, cashExpense)

        // Net Cash = 90k - 24k = 66k
        val netCash = cashIncome - cashExpense
        assertEquals(66000L, netCash)

        // Debt Outstanding = Total Debt (15k) - Paid (10k) = 5k
        val outstanding = reportRepository.totalOutstandingDebt.first() ?: 0L
        assertEquals(5000L, outstanding)
    }
}



