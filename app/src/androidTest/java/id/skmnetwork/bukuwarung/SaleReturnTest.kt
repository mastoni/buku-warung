package id.skmnetwork.bukuwarung

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CustomerEntity
import id.skmnetwork.bukuwarung.data.local.entity.DebtPaymentEntity
import id.skmnetwork.bukuwarung.data.local.entity.ItemType
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import id.skmnetwork.bukuwarung.data.repository.SaleRepository
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
class SaleReturnTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var saleRepository: SaleRepository
    private lateinit var productRepository: ProductRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        saleRepository = SaleRepository(database)
        productRepository = ProductRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createProduct(
        name: String = "Kopi Sachet",
        sellingPrice: Long = 5000L,
        purchasePrice: Long = 3000L,
        stock: Double = 20.0,
        itemType: ItemType = ItemType.PHYSICAL
    ): Long = runBlocking {
        productRepository.insertProductWithCategory(
            name = name,
            categoryName = "Minuman",
            purchasePrice = purchasePrice,
            sellingPrice = sellingPrice,
            stock = stock,
            minimumStock = 2.0,
            unit = "pcs",
            itemType = itemType
        )
    }

    private fun createCustomer(name: String = "Pak Budi"): Long = runBlocking {
        database.customerDao().insertCustomer(
            CustomerEntity(
                name = name,
                phone = "08123456789",
                address = "Jl. Mawar No. 1"
            )
        )
    }

    // 1. Partial CASH Return
    @Test
    fun testPartialCashReturn() = runBlocking {
        val prodId = createProduct(stock = 10.0, sellingPrice = 10000L)
        val saleId = saleRepository.completeSale(
            cartItems = mapOf(prodId to 5.0),
            paymentMethod = "CASH"
        ).getOrThrow()

        val saleItems = saleRepository.getItemsForTransaction(saleId)
        val saleItemId = saleItems.first().id

        // Return 2 items
        val returnResult = saleRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItemId to 2.0),
            reason = "Barang rusak"
        )
        assertTrue(returnResult.isSuccess)
        val returnId = returnResult.getOrThrow()

        // Assert Stock increased to 10 - 5 + 2 = 7
        val product = productRepository.getProductById(prodId)!!
        assertEquals(7.0, product.stock, 0.001)

        // Assert Cash balance reduced by 20.000 (Income was 50k, Expense 20k -> Net 30k)
        val cashBalance = database.cashDao().getTotalCashBalance().first()
        assertEquals(30000L, cashBalance)

        // Assert Return row
        val returnTrx = saleRepository.getReturnById(returnId)
        assertNotNull(returnTrx)
        assertEquals(20000L, returnTrx!!.totalRefundAmount)
        assertEquals("CASH", returnTrx.refundMethod)
        assertEquals("Barang rusak", returnTrx.reason)

        // Assert Remaining returnable is 3.0
        val remaining = saleRepository.getReturnableQuantitiesForSale(saleId)
        assertEquals(3.0, remaining[saleItemId] ?: 0.0, 0.001)
    }

    // 2. Full CASH Return
    @Test
    fun testFullCashReturn() = runBlocking {
        val prodId = createProduct(stock = 10.0, sellingPrice = 10000L)
        val saleId = saleRepository.completeSale(
            cartItems = mapOf(prodId to 5.0),
            paymentMethod = "CASH"
        ).getOrThrow()

        val saleItemId = saleRepository.getItemsForTransaction(saleId).first().id

        val returnResult = saleRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItemId to 5.0)
        )
        assertTrue(returnResult.isSuccess)

        // Stock fully restored to 10.0
        val product = productRepository.getProductById(prodId)!!
        assertEquals(10.0, product.stock, 0.001)

        // Net cash balance is 0 (50k in - 50k out)
        val cashBalance = database.cashDao().getTotalCashBalance().first()
        assertEquals(0L, cashBalance)

        // Remaining returnable is 0.0
        val remaining = saleRepository.getReturnableQuantitiesForSale(saleId)
        assertEquals(0.0, remaining[saleItemId] ?: 0.0, 0.001)
    }

    // 3 & 30 & 31. Partial QRIS Return ALWAYS creates CASH EXPENSE
    @Test
    fun testPartialQrisReturnCreatesCashExpense() = runBlocking {
        val prodId = createProduct(stock = 10.0, sellingPrice = 10000L)
        val saleId = saleRepository.completeSale(
            cartItems = mapOf(prodId to 5.0),
            paymentMethod = "QRIS"
        ).getOrThrow()

        // QRIS sale creates NO physical cash income
        val initialCash = database.cashDao().getTotalCashBalance().first() ?: 0L
        assertEquals(0L, initialCash)

        val saleItemId = saleRepository.getItemsForTransaction(saleId).first().id

        val returnResult = saleRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItemId to 2.0)
        )
        assertTrue(returnResult.isSuccess)

        // QRIS return creates CASH EXPENSE refund of 20.000
        val cashBalance = database.cashDao().getTotalCashBalance().first()
        assertEquals(-20000L, cashBalance)

        val cashTx = database.cashDao().getAllCashTransactions().first()
        assertEquals(1, cashTx.size)
        assertEquals("EXPENSE", cashTx.first().type)
        assertEquals(20000L, cashTx.first().amount)

        // Stock restored +2
        val product = productRepository.getProductById(prodId)!!
        assertEquals(7.0, product.stock, 0.001)
    }

    // 4. Full QRIS Return
    @Test
    fun testFullQrisReturn() = runBlocking {
        val prodId = createProduct(stock = 10.0, sellingPrice = 10000L)
        val saleId = saleRepository.completeSale(
            cartItems = mapOf(prodId to 5.0),
            paymentMethod = "QRIS"
        ).getOrThrow()

        val saleItemId = saleRepository.getItemsForTransaction(saleId).first().id

        val returnResult = saleRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItemId to 5.0)
        )
        assertTrue(returnResult.isSuccess)

        // Full 50.000 cash expense refund
        val cashBalance = database.cashDao().getTotalCashBalance().first()
        assertEquals(-50000L, cashBalance)

        val product = productRepository.getProductById(prodId)!!
        assertEquals(10.0, product.stock, 0.001)
    }

    // 5. Partial CREDIT Return
    @Test
    fun testPartialCreditReturn() = runBlocking {
        val custId = createCustomer("Pak Budi")
        val prodId = createProduct(stock = 10.0, sellingPrice = 10000L)

        val saleId = saleRepository.completeSale(
            cartItems = mapOf(prodId to 5.0),
            paymentMethod = "CREDIT",
            customerId = custId
        ).getOrThrow()

        // Debt was created for 50.000 (OPEN)
        val initialDebt = database.debtDao().getOpenDebtsForCustomerList(custId).first()
        assertEquals(50000L, initialDebt.totalDebt)
        assertEquals(0L, initialDebt.paidAmount)
        assertEquals("OPEN", initialDebt.status)

        val saleItemId = saleRepository.getItemsForTransaction(saleId).first().id

        // Return 2 items (20.000 refund value)
        val returnResult = saleRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItemId to 2.0)
        )
        assertTrue(returnResult.isSuccess)

        // Debt reduced to 30.000 (still OPEN)
        val updatedDebt = database.debtDao().getDebtById(initialDebt.id)!!
        assertEquals(30000L, updatedDebt.totalDebt)
        assertEquals(0L, updatedDebt.paidAmount)
        assertEquals("OPEN", updatedDebt.status)

        // No cash expense created since entire amount was absorbed by debt
        val cashTxs = database.cashDao().getAllCashTransactions().first()
        assertTrue(cashTxs.isEmpty())
    }

    // 6. Full CREDIT Return
    @Test
    fun testFullCreditReturn() = runBlocking {
        val custId = createCustomer("Pak Budi")
        val prodId = createProduct(stock = 10.0, sellingPrice = 10000L)

        val saleId = saleRepository.completeSale(
            cartItems = mapOf(prodId to 5.0),
            paymentMethod = "CREDIT",
            customerId = custId
        ).getOrThrow()

        val initialDebt = database.debtDao().getOpenDebtsForCustomerList(custId).first()
        val saleItemId = saleRepository.getItemsForTransaction(saleId).first().id

        val returnResult = saleRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItemId to 5.0)
        )
        assertTrue(returnResult.isSuccess)

        // Debt reduced to 0L and status becomes PAID
        val updatedDebt = database.debtDao().getDebtById(initialDebt.id)!!
        assertEquals(0L, updatedDebt.totalDebt)
        assertEquals("PAID", updatedDebt.status)
    }

    // 7. CREDIT Return after partial payment
    @Test
    fun testCreditReturnAfterPartialPayment() = runBlocking {
        val custId = createCustomer("Pak Budi")
        val prodId = createProduct(stock = 10.0, sellingPrice = 10000L)

        val saleId = saleRepository.completeSale(
            cartItems = mapOf(prodId to 10.0), // 100k
            paymentMethod = "CREDIT",
            customerId = custId
        ).getOrThrow()

        val debt = database.debtDao().getOpenDebtsForCustomerList(custId).first()

        // Customer pays 40k towards 100k debt -> remaining unpaid debt = 60k
        database.debtDao().insertDebtPayment(
            DebtPaymentEntity(
                debtId = debt.id,
                debtUuid = debt.uuid,
                amount = 40000L
            )
        )
        database.debtDao().updateDebt(debt.copy(paidAmount = 40000L))

        val saleItemId = saleRepository.getItemsForTransaction(saleId).first().id

        // Return 3 items (30k refund value) -> <= remaining 60k
        val returnResult = saleRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItemId to 3.0)
        )
        assertTrue(returnResult.isSuccess)

        val updatedDebt = database.debtDao().getDebtById(debt.id)!!
        // totalDebt becomes 100k - 30k = 70k. paidAmount remains 40k. Unpaid balance = 30k.
        assertEquals(70000L, updatedDebt.totalDebt)
        assertEquals(40000L, updatedDebt.paidAmount)
        assertEquals("OPEN", updatedDebt.status)

        // No cash refund needed
        val cashTxs = database.cashDao().getAllCashTransactions().first()
        assertTrue(cashTxs.isEmpty())
    }

    // 8. CREDIT Return after full payment / overpayment
    @Test
    fun testCreditReturnAfterFullPaymentOverpayment() = runBlocking {
        val custId = createCustomer("Pak Budi")
        val prodId = createProduct(stock = 10.0, sellingPrice = 10000L)

        val saleId = saleRepository.completeSale(
            cartItems = mapOf(prodId to 10.0), // 100k
            paymentMethod = "CREDIT",
            customerId = custId
        ).getOrThrow()

        val debt = database.debtDao().getOpenDebtsForCustomerList(custId).first()

        // Customer pays 80k towards 100k debt -> remaining unpaid = 20k
        database.debtDao().insertDebtPayment(
            DebtPaymentEntity(
                debtId = debt.id,
                debtUuid = debt.uuid,
                amount = 80000L
            )
        )
        database.debtDao().updateDebt(debt.copy(paidAmount = 80000L))

        val saleItemId = saleRepository.getItemsForTransaction(saleId).first().id

        // Return 5 items (50k refund value). Unpaid debt was only 20k.
        // So 20k wipes out remaining debt (totalDebt becomes 80k = paidAmount -> PAID),
        // and remaining 30k is refunded in CASH to customer!
        val returnResult = saleRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItemId to 5.0)
        )
        assertTrue(returnResult.isSuccess)

        val updatedDebt = database.debtDao().getDebtById(debt.id)!!
        assertEquals(80000L, updatedDebt.totalDebt)
        assertEquals(80000L, updatedDebt.paidAmount)
        assertEquals("PAID", updatedDebt.status)

        // Cash expense created for the 30k overpayment
        val cashTxs = database.cashDao().getAllCashTransactions().first()
        assertEquals(1, cashTxs.size)
        assertEquals(30000L, cashTxs.first().amount)
        assertEquals("EXPENSE", cashTxs.first().type)
    }

    // 9. Return qty > sold qty rejected
    @Test
    fun testReturnQtyGreaterThanSoldQtyRejected() = runBlocking {
        val prodId = createProduct(stock = 10.0, sellingPrice = 10000L)
        val saleId = saleRepository.completeSale(
            cartItems = mapOf(prodId to 3.0),
            paymentMethod = "CASH"
        ).getOrThrow()

        val saleItemId = saleRepository.getItemsForTransaction(saleId).first().id

        val returnResult = saleRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItemId to 5.0)
        )
        assertTrue(returnResult.isFailure)
    }

    // 10. Multiple returns cannot exceed original qty
    @Test
    fun testMultipleReturnsCannotExceedOriginalQty() = runBlocking {
        val prodId = createProduct(stock = 10.0, sellingPrice = 10000L)
        val saleId = saleRepository.completeSale(
            cartItems = mapOf(prodId to 5.0),
            paymentMethod = "CASH"
        ).getOrThrow()

        val saleItemId = saleRepository.getItemsForTransaction(saleId).first().id

        // 1st return 3.0 -> OK
        val res1 = saleRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItemId to 3.0)
        )
        assertTrue(res1.isSuccess)

        // 2nd return 3.0 -> Should FAIL (3 + 3 = 6 > 5)
        val res2 = saleRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItemId to 3.0)
        )
        assertTrue(res2.isFailure)

        // 2nd return 2.0 -> OK (3 + 2 = 5)
        val res3 = saleRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItemId to 2.0)
        )
        assertTrue(res3.isSuccess)

        // 3rd return 0.5 -> Should FAIL (already 5/5 returned)
        val res4 = saleRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItemId to 0.5)
        )
        assertTrue(res4.isFailure)
    }

    // 11. Return qty <= 0 rejected
    @Test
    fun testReturnQtyZeroOrNegativeRejected() = runBlocking {
        val prodId = createProduct(stock = 10.0)
        val saleId = saleRepository.completeSale(
            cartItems = mapOf(prodId to 2.0),
            paymentMethod = "CASH"
        ).getOrThrow()

        val saleItemId = saleRepository.getItemsForTransaction(saleId).first().id

        val resZero = saleRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItemId to 0.0)
        )
        assertTrue(resZero.isFailure)

        val resNeg = saleRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItemId to -1.0)
        )
        assertTrue(resNeg.isFailure)
    }

    // 12. Unknown sale rejected
    @Test
    fun testUnknownSaleRejected() = runBlocking {
        val res = saleRepository.processSaleReturn(
            saleId = 999999L,
            itemsToReturn = mapOf(1L to 1.0)
        )
        assertTrue(res.isFailure)
    }

    // 13. Sale item from different sale rejected
    @Test
    fun testSaleItemFromDifferentSaleRejected() = runBlocking {
        val prodId = createProduct(stock = 10.0)
        val saleId1 = saleRepository.completeSale(
            cartItems = mapOf(prodId to 2.0),
            paymentMethod = "CASH"
        ).getOrThrow()

        val saleId2 = saleRepository.completeSale(
            cartItems = mapOf(prodId to 2.0),
            paymentMethod = "CASH"
        ).getOrThrow()

        val saleItem1Id = saleRepository.getItemsForTransaction(saleId1).first().id

        // Attempting to return item from sale 1 against sale 2
        val res = saleRepository.processSaleReturn(
            saleId = saleId2,
            itemsToReturn = mapOf(saleItem1Id to 1.0)
        )
        assertTrue(res.isFailure)
    }

    // 14 & 16. Physical stock increases & Stock Invariant
    @Test
    fun testPhysicalStockIncreasesAndStockInvariant() = runBlocking {
        val prodId = createProduct(stock = 50.0, sellingPrice = 5000L)
        val productBefore = productRepository.getProductById(prodId)!!

        val saleId = saleRepository.completeSale(
            cartItems = mapOf(prodId to 20.0),
            paymentMethod = "CASH"
        ).getOrThrow()

        val saleItemId = saleRepository.getItemsForTransaction(saleId).first().id

        saleRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItemId to 8.0)
        ).getOrThrow()

        val productAfter = productRepository.getProductById(prodId)!!
        // 50 - 20 + 8 = 38
        assertEquals(38.0, productAfter.stock, 0.001)

        // Invariant: SUM(StockMovement.deltaQuantity) == Product.stock
        val ledgerStock = database.stockMovementDao().getCalculatedStockForProduct(productBefore.uuid)
        assertEquals(productAfter.stock, ledgerStock, 0.001)
    }

    // 15. Non-physical product does not mutate stock
    @Test
    fun testNonPhysicalProductDoesNotMutateStock() = runBlocking {
        val prodId = createProduct(
            name = "Pulsa 10k",
            sellingPrice = 12000L,
            stock = 0.0,
            itemType = ItemType.SERVICE
        )

        val saleId = saleRepository.completeSale(
            cartItems = mapOf(prodId to 2.0),
            paymentMethod = "CASH"
        ).getOrThrow()

        val saleItemId = saleRepository.getItemsForTransaction(saleId).first().id

        saleRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItemId to 1.0)
        ).getOrThrow()

        val product = productRepository.getProductById(prodId)!!
        assertEquals(0.0, product.stock, 0.001)

        // No movements inserted
        val movements = database.stockMovementDao().getMovementsListForProduct(product.uuid)
        assertTrue(movements.isEmpty())
    }

    // 17. Cash Invariant
    @Test
    fun testCashInvariant() = runBlocking {
        val prodId = createProduct(sellingPrice = 15000L, stock = 10.0)
        val saleId = saleRepository.completeSale(
            cartItems = mapOf(prodId to 4.0), // 60k
            paymentMethod = "CASH"
        ).getOrThrow()

        val saleItemId = saleRepository.getItemsForTransaction(saleId).first().id

        saleRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItemId to 1.0) // 15k
        ).getOrThrow()

        val cashIncome = database.cashDao().getCashIncomeTotal(0, System.currentTimeMillis() + 10000).first() ?: 0L
        val cashExpense = database.cashDao().getCashExpenseTotal(0, System.currentTimeMillis() + 10000).first() ?: 0L
        val netBalance = database.cashDao().getTotalCashBalance().first() ?: 0L

        assertEquals(60000L, cashIncome)
        assertEquals(15000L, cashExpense)
        assertEquals(45000L, netBalance)
    }

    // 18. Debt Invariant
    @Test
    fun testDebtInvariant() = runBlocking {
        val custId = createCustomer("Bu Siti")
        val prodId = createProduct(sellingPrice = 25000L, stock = 10.0)

        val saleId = saleRepository.completeSale(
            cartItems = mapOf(prodId to 4.0), // 100k
            paymentMethod = "CREDIT",
            customerId = custId
        ).getOrThrow()

        val saleItemId = saleRepository.getItemsForTransaction(saleId).first().id

        saleRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItemId to 2.0) // 50k
        ).getOrThrow()

        val outstanding = database.debtDao().getTotalOutstandingDebt().first() ?: 0L
        assertEquals(50000L, outstanding)
    }

    // 19. Return UUID uniqueness
    @Test
    fun testReturnUuidUniqueness() = runBlocking {
        val prodId = createProduct(stock = 10.0, sellingPrice = 10000L)
        val saleId = saleRepository.completeSale(
            cartItems = mapOf(prodId to 5.0),
            paymentMethod = "CASH"
        ).getOrThrow()

        val saleItemId = saleRepository.getItemsForTransaction(saleId).first().id

        val ret1 = saleRepository.processSaleReturn(saleId, mapOf(saleItemId to 1.0)).getOrThrow()
        val ret2 = saleRepository.processSaleReturn(saleId, mapOf(saleItemId to 1.0)).getOrThrow()

        val trx1 = saleRepository.getReturnById(ret1)!!
        val trx2 = saleRepository.getReturnById(ret2)!!

        assertTrue(trx1.uuid.isNotBlank())
        assertTrue(trx2.uuid.isNotBlank())
        assertFalse(trx1.uuid == trx2.uuid)
    }

    // 20. SyncQueue inserted atomically
    @Test
    fun testSyncQueueInsertedAtomically() = runBlocking {
        val prodId = createProduct(stock = 10.0, sellingPrice = 10000L)
        val saleId = saleRepository.completeSale(
            cartItems = mapOf(prodId to 2.0),
            paymentMethod = "CASH"
        ).getOrThrow()

        val saleItemId = saleRepository.getItemsForTransaction(saleId).first().id

        val returnId = saleRepository.processSaleReturn(saleId, mapOf(saleItemId to 1.0)).getOrThrow()
        val returnTrx = saleRepository.getReturnById(returnId)!!

        val syncQueues = database.syncQueueDao().getAllItems().first().filter { it.entityType == "RETURN" }
        assertEquals(1, syncQueues.size)
        assertEquals(returnTrx.uuid, syncQueues.first().entityUuid)
        assertEquals("INSERT", syncQueues.first().operation)
    }

    // 21. Transaction rollback on failure
    @Test
    fun testTransactionRollbackOnFailure() = runBlocking {
        val prodId = createProduct(stock = 10.0, sellingPrice = 10000L)
        val saleId = saleRepository.completeSale(
            cartItems = mapOf(prodId to 2.0),
            paymentMethod = "CASH"
        ).getOrThrow()

        val initialStock = productRepository.getProductById(prodId)!!.stock
        val initialMovementsCount = database.stockMovementDao().getAllMovements().first().size
        val initialCashCount = database.cashDao().getAllCashTransactions().first().size

        // Attempting invalid return (qty > sold)
        val saleItemId = saleRepository.getItemsForTransaction(saleId).first().id
        val res = saleRepository.processSaleReturn(saleId, mapOf(saleItemId to 10.0))
        assertTrue(res.isFailure)

        // Assert no mutations took place
        assertEquals(initialStock, productRepository.getProductById(prodId)!!.stock, 0.001)
        assertEquals(initialMovementsCount, database.stockMovementDao().getAllMovements().first().size)
        assertEquals(initialCashCount, database.cashDao().getAllCashTransactions().first().size)
        assertEquals(0, database.saleReturnDao().getAllReturns().first().size)
    }

    // 22. Optional reason empty (null in DB, no placeholder)
    @Test
    fun testOptionalReasonEmpty() = runBlocking {
        val prodId = createProduct(stock = 5.0)
        val saleId = saleRepository.completeSale(mapOf(prodId to 1.0)).getOrThrow()
        val saleItemId = saleRepository.getItemsForTransaction(saleId).first().id

        val returnId = saleRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItemId to 1.0),
            reason = null,
            notes = null
        ).getOrThrow()

        val returnTrx = saleRepository.getReturnById(returnId)!!
        assertNull(returnTrx.reason)
        assertNull(returnTrx.notes)
    }

    // 23. Reason selected
    @Test
    fun testReasonSelected() = runBlocking {
        val prodId = createProduct(stock = 5.0)
        val saleId = saleRepository.completeSale(mapOf(prodId to 1.0)).getOrThrow()
        val saleItemId = saleRepository.getItemsForTransaction(saleId).first().id

        val returnId = saleRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItemId to 1.0),
            reason = "Salah barang",
            notes = ""
        ).getOrThrow()

        val returnTrx = saleRepository.getReturnById(returnId)!!
        assertEquals("Salah barang", returnTrx.reason)
        assertNull(returnTrx.notes)
    }

    // 24 & 25. Optional notes empty vs filled
    @Test
    fun testOptionalNotesEmptyAndFilled() = runBlocking {
        val prodId = createProduct(stock = 5.0)
        val saleId = saleRepository.completeSale(mapOf(prodId to 2.0)).getOrThrow()
        val saleItemId = saleRepository.getItemsForTransaction(saleId).first().id

        val returnId = saleRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItemId to 1.0),
            notes = "Kemasan sobek sedikit"
        ).getOrThrow()

        val returnTrx = saleRepository.getReturnById(returnId)!!
        assertNull(returnTrx.reason)
        assertEquals("Kemasan sobek sedikit", returnTrx.notes)
    }

    // 26. Reason + notes together
    @Test
    fun testReasonAndNotesTogether() = runBlocking {
        val prodId = createProduct(stock = 5.0)
        val saleId = saleRepository.completeSale(mapOf(prodId to 1.0)).getOrThrow()
        val saleItemId = saleRepository.getItemsForTransaction(saleId).first().id

        val returnId = saleRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItemId to 1.0),
            reason = "Lainnya",
            notes = "Pembeli membawa pulang ukuran salah"
        ).getOrThrow()

        val returnTrx = saleRepository.getReturnById(returnId)!!
        assertEquals("Lainnya", returnTrx.reason)
        assertEquals("Pembeli membawa pulang ukuran salah", returnTrx.notes)
    }

    // 27. Soft-deleted product can still be returned
    @Test
    fun testSoftDeletedProductCanStillBeReturned() = runBlocking {
        val prodId = createProduct(name = "Kue Tradisional", stock = 10.0, sellingPrice = 8000L)
        val saleId = saleRepository.completeSale(mapOf(prodId to 3.0)).getOrThrow()

        // Soft delete the product
        productRepository.deleteProductById(prodId)
        assertNull(productRepository.getProductById(prodId))

        val saleItemId = saleRepository.getItemsForTransaction(saleId).first().id

        // Execute return
        val returnResult = saleRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItemId to 1.0)
        )
        assertTrue(returnResult.isSuccess)

        val returnItems = saleRepository.getItemsForReturn(returnResult.getOrThrow())
        assertEquals(1, returnItems.size)
        assertEquals("Kue Tradisional", returnItems.first().productName)
        assertEquals(8000L, returnItems.first().price)

        // Physical product stock in DB was updated from 7 to 8 even though soft deleted
        val rawProduct = database.productDao().getProductByIdRaw(prodId)!!
        assertEquals(8.0, rawProduct.stock, 0.001)
    }

    // 28. Persistence after query reload
    @Test
    fun testPersistenceAfterReload() = runBlocking {
        val prodId = createProduct(stock = 10.0, sellingPrice = 5000L)
        val saleId = saleRepository.completeSale(mapOf(prodId to 2.0)).getOrThrow()
        val saleItemId = saleRepository.getItemsForTransaction(saleId).first().id

        val returnId = saleRepository.processSaleReturn(
            saleId = saleId,
            itemsToReturn = mapOf(saleItemId to 1.0),
            reason = "Barang tidak sesuai"
        ).getOrThrow()

        val returnsForSale = saleRepository.getReturnsForSale(saleId).first()
        assertEquals(1, returnsForSale.size)
        assertEquals(returnId, returnsForSale.first().id)
        assertEquals(5000L, returnsForSale.first().totalRefundAmount)
    }

    // 29. Offline return
    @Test
    fun testOfflineReturn() = runBlocking {
        // Runs completely without network
        val prodId = createProduct(stock = 10.0, sellingPrice = 5000L)
        val saleId = saleRepository.completeSale(mapOf(prodId to 1.0)).getOrThrow()
        val saleItemId = saleRepository.getItemsForTransaction(saleId).first().id

        val res = saleRepository.processSaleReturn(saleId, mapOf(saleItemId to 1.0))
        assertTrue(res.isSuccess)
    }
}
