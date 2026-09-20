package id.skmnetwork.bukuwarung

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.repository.CustomerRepository
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import id.skmnetwork.bukuwarung.data.repository.SupplierRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SupplierPayableTest {

    private lateinit var database: AppDatabase
    private lateinit var productRepository: ProductRepository
    private lateinit var customerRepository: CustomerRepository
    private lateinit var supplierRepository: SupplierRepository

    private var productId: Long = 0
    private var customerId: Long = 0
    private var supplierId: Long = 0

    @Before
    fun setup() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        productRepository = ProductRepository(database, "LEGACY_BUSINESS")
        customerRepository = CustomerRepository(database, "LEGACY_BUSINESS")
        supplierRepository = SupplierRepository(database, "LEGACY_BUSINESS")

        val catId = database.categoryDao().insertCategory(CategoryEntity(name = "Umum"))
        productId = database.productDao().insertProduct(
            ProductEntity(
                categoryId = catId,
                name = "Gula Pasir",
                purchasePrice = 10000,
                sellingPrice = 12000,
                stock = 20.0
            )
        )
        customerId = customerRepository.saveCustomer("Pak Budi", "08123456789", "Jl. Mawar")
        supplierId = supplierRepository.saveSupplier("PT Sembako Makmur", "082111222333", "Jl. Industri")
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun test5_cashPurchaseDecreasesCashNoPayable() = runBlocking {
        val initialCash = database.cashDao().getTotalCashBalance("LEGACY_BUSINESS").first() ?: 0L
        val initialStock = database.productDao().getProductById(productId, "LEGACY_BUSINESS")!!.stock

        // CASH purchase: 1 x Gula = Rp 10.000
        val result = productRepository.processAtomicPurchase(mapOf(productId to 1.0))
        assertTrue(result.isSuccess)

        // Verify Cash Expense (-10.000)
        val currentCash = database.cashDao().getTotalCashBalance("LEGACY_BUSINESS").first() ?: 0L
        assertEquals(initialCash - 10000L, currentCash)

        // Verify Stock Increased (+1.0)
        val currentStock = database.productDao().getProductById(productId, "LEGACY_BUSINESS")!!.stock
        assertEquals(initialStock + 1.0, currentStock, 0.001)

        // Verify NO Supplier Payable created
        val payables = database.supplierPayableDao().getPayablesForSupplier(supplierId, "LEGACY_BUSINESS").first()
        assertEquals(0, payables.size)
    }

    @Test
    fun test6_creditPurchaseCreatesPayableNoCashExpense() = runBlocking {
        val initialCash = database.cashDao().getTotalCashBalance("LEGACY_BUSINESS").first() ?: 0L
        val initialStock = database.productDao().getProductById(productId, "LEGACY_BUSINESS")!!.stock

        // CREDIT purchase: 1 x Gula = Rp 10.000
        val result = supplierRepository.processAtomicCreditPurchase(
            purchaseItems = mapOf(productId to 1.0),
            supplierId = supplierId
        )
        assertTrue(result.isSuccess)

        // Verify Stock Increased (+1.0)
        val currentStock = database.productDao().getProductById(productId, "LEGACY_BUSINESS")!!.stock
        assertEquals(initialStock + 1.0, currentStock, 0.001)

        // Verify Cash Balance UNCHANGED
        val currentCash = database.cashDao().getTotalCashBalance("LEGACY_BUSINESS").first() ?: 0L
        assertEquals(initialCash, currentCash)

        // Verify Supplier Payable Created
        val payables = database.supplierPayableDao().getPayablesForSupplier(supplierId, "LEGACY_BUSINESS").first()
        assertEquals(1, payables.size)
        val payable = payables[0]
        assertEquals(10000L, payable.totalDebt)
        assertEquals(0L, payable.paidAmount)
        assertEquals("OPEN", payable.status)
    }

    @Test
    fun test7_supplierPartialPaymentUpdatesPaidAmountOutstandingAndCash() = runBlocking {
        // Step 1: Credit Purchase Rp 10.000
        supplierRepository.processAtomicCreditPurchase(mapOf(productId to 1.0), supplierId)
        val payableId = database.supplierPayableDao().getPayablesForSupplier(supplierId, "LEGACY_BUSINESS").first()[0].id

        // Step 2: Pay Supplier Rp 4.000
        val payResult = supplierRepository.processAtomicSupplierPayment(payableId, 4000L, "Cicilan 1")
        assertTrue(payResult.isSuccess)

        // Verify Payable State
        val payable = database.supplierPayableDao().getSupplierPayableById(payableId, "LEGACY_BUSINESS")!!
        assertEquals(10000L, payable.totalDebt)
        assertEquals(4000L, payable.paidAmount)
        assertEquals(6000L, payable.totalDebt - payable.paidAmount)
        assertEquals("OPEN", payable.status)

        // Verify Cash Expense Created (-4.000)
        val currentCash = database.cashDao().getTotalCashBalance("LEGACY_BUSINESS").first() ?: 0L
        assertEquals(-4000L, currentCash)
    }

    @Test
    fun test8_finalSupplierPaymentMarksStatusPaidAndUpdatesCash() = runBlocking {
        // Step 1: Credit Purchase Rp 10.000
        supplierRepository.processAtomicCreditPurchase(mapOf(productId to 1.0), supplierId)
        val payableId = database.supplierPayableDao().getPayablesForSupplier(supplierId, "LEGACY_BUSINESS").first()[0].id

        // Step 2: Pay Rp 4.000
        supplierRepository.processAtomicSupplierPayment(payableId, 4000L, "Cicilan 1")

        // Step 3: Pay remaining Rp 6.000
        val payResult2 = supplierRepository.processAtomicSupplierPayment(payableId, 6000L, "Pelunasan")
        assertTrue(payResult2.isSuccess)

        // Verify Payable State (paidAmount = 10000, outstanding = 0, status = PAID)
        val payable = database.supplierPayableDao().getSupplierPayableById(payableId, "LEGACY_BUSINESS")!!
        assertEquals(10000L, payable.paidAmount)
        assertEquals(0L, payable.totalDebt - payable.paidAmount)
        assertEquals("PAID", payable.status)

        // Verify Cash Expense Total (-10.000)
        val currentCash = database.cashDao().getTotalCashBalance("LEGACY_BUSINESS").first() ?: 0L
        assertEquals(-10000L, currentCash)
    }

    @Test
    fun test9_supplierOverpaymentIsRejectedWithoutDbWrites() = runBlocking {
        // Step 1: Credit Purchase Rp 10.000
        supplierRepository.processAtomicCreditPurchase(mapOf(productId to 1.0), supplierId)
        val payableId = database.supplierPayableDao().getPayablesForSupplier(supplierId, "LEGACY_BUSINESS").first()[0].id

        // Step 2: Attempt Overpayment Rp 10.001
        val overpayResult = supplierRepository.processAtomicSupplierPayment(payableId, 10001L, "Overpay")
        assertTrue("Overpayment must fail", overpayResult.isFailure)

        // Verify Payable Unchanged
        val payable = database.supplierPayableDao().getSupplierPayableById(payableId, "LEGACY_BUSINESS")!!
        assertEquals(0L, payable.paidAmount)

        // Verify No Payments Recorded
        val payments = database.supplierPayableDao().getPaymentsListForPayable(payableId, "LEGACY_BUSINESS")
        assertEquals(0, payments.size)

        // Verify Cash Unchanged
        val currentCash = database.cashDao().getTotalCashBalance("LEGACY_BUSINESS").first() ?: 0L
        assertEquals(0L, currentCash)
    }

    @Test
    fun test10_atomicRollbackOnCreditPurchaseAndPaymentFailure() = runBlocking {
        val initialStock = database.productDao().getProductById(productId, "LEGACY_BUSINESS")!!.stock

        // Forced Failure in Credit Purchase
        var failedPurchase = false
        try {
            database.withTransaction {
                supplierRepository.processAtomicCreditPurchase(mapOf(productId to 1.0), supplierId)
                throw IllegalStateException("SIMULATED CREDIT PURCHASE FAILURE")
            }
        } catch (e: Exception) {
            failedPurchase = true
        }
        assertTrue(failedPurchase)

        // Verify 0 Purchases, 0 Payables, Stock Unchanged
        val purchases = database.purchaseDao().getAllPurchaseTransactions("LEGACY_BUSINESS").first()
        assertEquals(0, purchases.size)
        val payables = database.supplierPayableDao().getPayablesForSupplier(supplierId, "LEGACY_BUSINESS").first()
        assertEquals(0, payables.size)
        assertEquals(initialStock, database.productDao().getProductById(productId, "LEGACY_BUSINESS")!!.stock, 0.001)
    }

    @Test
    fun test11_reconciliationPaidAmountEqualsSumPaymentsAndOutstanding() = runBlocking {
        // Step 1: Credit Purchase Rp 10.000
        supplierRepository.processAtomicCreditPurchase(mapOf(productId to 1.0), supplierId)
        val payableId = database.supplierPayableDao().getPayablesForSupplier(supplierId, "LEGACY_BUSINESS").first()[0].id

        // Step 2: Multiple Payments (2500 + 2500 = 5000)
        supplierRepository.processAtomicSupplierPayment(payableId, 2500L, "Bayar 1")
        supplierRepository.processAtomicSupplierPayment(payableId, 2500L, "Bayar 2")

        // Reconciliation Assertion
        val payable = database.supplierPayableDao().getSupplierPayableById(payableId, "LEGACY_BUSINESS")!!
        val payments = database.supplierPayableDao().getPaymentsListForPayable(payableId, "LEGACY_BUSINESS")
        val paymentSum = payments.sumOf { it.amount }

        assertEquals(paymentSum, payable.paidAmount) // paidAmount == SUM(payments)
        assertEquals(payable.totalDebt - payable.paidAmount, 5000L) // outstanding == totalDebt - paidAmount
    }

    @Test
    fun test12_appScreenSuppliersAndHome8MenuNavigation() {
        val suppliersScreen = id.skmnetwork.bukuwarung.ui.navigation.AppScreen.SUPPLIERS
        assertEquals("Supplier & Hutang", suppliersScreen.label)

        val customersScreen = id.skmnetwork.bukuwarung.ui.navigation.AppScreen.CUSTOMERS
        assertEquals("Pelanggan & Piutang", customersScreen.label)

        val allScreens = id.skmnetwork.bukuwarung.ui.navigation.AppScreen.values().map { it.name }
        assertTrue(allScreens.contains("HOME"))
        assertTrue(allScreens.contains("POS"))
        assertTrue(allScreens.contains("PRODUCTS"))
        assertTrue(allScreens.contains("PURCHASE"))
        assertTrue(allScreens.contains("CUSTOMERS"))
        assertTrue(allScreens.contains("SUPPLIERS"))
        assertTrue(allScreens.contains("CASH"))
        assertTrue(allScreens.contains("REPORTS"))
        assertTrue(allScreens.contains("SETTINGS"))
    }

    @Test
    fun test13_supplierCRUDLifecycle() = runBlocking {
        // Create
        val newSupplierId = supplierRepository.saveSupplier("Toko Plastik Maju", "081999888777", "Pasar Induk")
        var supplier = supplierRepository.getSupplierById(newSupplierId)
        org.junit.Assert.assertNotNull(supplier)
        assertEquals("Toko Plastik Maju", supplier!!.name)

        // Search
        val searchResults = supplierRepository.searchSuppliers("Plastik").first()
        assertTrue(searchResults.any { it.id == newSupplierId })

        // Update
        supplierRepository.updateSupplier(newSupplierId, "Toko Plastik Makmur Jaya", "081999888999", "Pasar Induk Blok A")
        supplier = supplierRepository.getSupplierById(newSupplierId)
        assertEquals("Toko Plastik Makmur Jaya", supplier!!.name)
        assertEquals("081999888999", supplier.phone)

        // Soft Delete
        supplierRepository.deleteSupplier(newSupplierId)
        val activeSuppliers = supplierRepository.allSuppliers.first()
        org.junit.Assert.assertFalse(activeSuppliers.any { it.id == newSupplierId })
    }

    @Test
    fun test14_cashPurchaseWithAndWithoutSupplier() = runBlocking {
        // Cash purchase WITHOUT supplier
        val res1 = productRepository.processAtomicPurchase(
            purchaseItems = mapOf(productId to 2.0),
            supplierId = null
        )
        assertTrue(res1.isSuccess)

        val purchases = database.purchaseDao().getAllPurchaseTransactions("LEGACY_BUSINESS").first()
        val p1 = purchases.first { it.totalAmount == 20000L }
        org.junit.Assert.assertNull("Cash purchase without supplier must have null supplierId", p1.supplierId)
        assertEquals("CASH", p1.paymentMethod)

        // Verify Cash Expense created
        val cashTx1 = database.cashDao().getAllCashTransactions("LEGACY_BUSINESS").first().find { it.refUuid == p1.uuid }
        org.junit.Assert.assertNotNull("Cash purchase must create CashTransaction EXPENSE", cashTx1)
        assertEquals("EXPENSE", cashTx1!!.type)
        assertEquals(20000L, cashTx1.amount)

        // Verify NO SupplierPayable created
        val payables1 = database.supplierPayableDao().getPayablesForSupplier(supplierId, "LEGACY_BUSINESS").first()
        assertEquals(0, payables1.size)

        // Cash purchase WITH supplier
        val res2 = productRepository.processAtomicPurchase(
            purchaseItems = mapOf(productId to 3.0),
            supplierId = supplierId
        )
        assertTrue(res2.isSuccess)

        val purchases2 = database.purchaseDao().getAllPurchaseTransactions("LEGACY_BUSINESS").first()
        val p2 = purchases2.first { it.totalAmount == 30000L }
        assertEquals("Cash purchase with supplier must store supplierId", supplierId, p2.supplierId)
        assertEquals("CASH", p2.paymentMethod)

        // Verify Cash Expense created
        val cashTx2 = database.cashDao().getAllCashTransactions("LEGACY_BUSINESS").first().find { it.refUuid == p2.uuid }
        org.junit.Assert.assertNotNull("Cash purchase with supplier must create CashTransaction EXPENSE", cashTx2)
        assertEquals("EXPENSE", cashTx2!!.type)
        assertEquals(30000L, cashTx2.amount)

        // Verify still NO SupplierPayable created for CASH purchase
        val payables2 = database.supplierPayableDao().getPayablesForSupplier(supplierId, "LEGACY_BUSINESS").first()
        assertEquals(0, payables2.size)
    }

    @Test
    fun test15_purchaseHistoryAndDetailIntegrity() = runBlocking {
        // Create purchase with 2 items
        val catId = database.categoryDao().insertCategory(CategoryEntity(name = "Bumbu"))
        val prod2Id = database.productDao().insertProduct(
            ProductEntity(
                categoryId = catId,
                name = "Kecap Manis",
                purchasePrice = 7500,
                sellingPrice = 9000,
                stock = 15.0
            )
        )

        val result = productRepository.processAtomicPurchase(
            purchaseItems = mapOf(productId to 4.0, prod2Id to 2.0),
            supplierId = supplierId
        )
        assertTrue(result.isSuccess)

        val purchases = database.purchaseDao().getAllPurchaseTransactions("LEGACY_BUSINESS").first()
        val purchase = purchases.first { it.totalAmount == (4 * 10000L + 2 * 7500L) }
        assertEquals(55000L, purchase.totalAmount)
        assertEquals(supplierId, purchase.supplierId)

        // Retrieve items for purchase detail
        val items = productRepository.getPurchaseItems(purchase.id)
        assertEquals(2, items.size)

        val itemGula = items.find { it.productId == productId }
        org.junit.Assert.assertNotNull(itemGula)
        assertEquals("Gula Pasir", itemGula!!.productName)
        assertEquals(4.0, itemGula.quantity, 0.001)
        assertEquals(10000L, itemGula.purchasePrice)
        assertEquals(40000L, itemGula.subtotal)

        val itemKecap = items.find { it.productId == prod2Id }
        org.junit.Assert.assertNotNull(itemKecap)
        assertEquals("Kecap Manis", itemKecap!!.productName)
        assertEquals(2.0, itemKecap.quantity, 0.001)
        assertEquals(7500L, itemKecap.purchasePrice)
        assertEquals(15000L, itemKecap.subtotal)
    }

    @Test
    fun test16_creditPurchaseRequiresSupplierAndCreatesPayable() = runBlocking {
        // Credit purchase without supplier must fail
        val resFail = database.purchaseDao()
        val purchaseRepo = id.skmnetwork.bukuwarung.data.repository.PurchaseRepository(database, "LEGACY_BUSINESS")
        val failResult = purchaseRepo.completePurchase(
            purchaseItems = mapOf(productId to 1.0),
            paymentMethod = "CREDIT",
            supplierId = null
        )
        assertTrue("Credit purchase without supplier must fail", failResult.isFailure)

        // Credit purchase with valid supplier succeeds
        val successResult = purchaseRepo.completePurchase(
            purchaseItems = mapOf(productId to 2.0),
            paymentMethod = "CREDIT",
            supplierId = supplierId
        )
        assertTrue(successResult.isSuccess)

        // Verify SupplierPayable created
        val payables = database.supplierPayableDao().getPayablesForSupplier(supplierId, "LEGACY_BUSINESS").first()
        assertEquals(1, payables.size)
        assertEquals(20000L, payables[0].totalDebt)
        assertEquals(0L, payables[0].paidAmount)

        // Verify NO CashTransaction for purchase
        val cashTxs = database.cashDao().getAllCashTransactions("LEGACY_BUSINESS").first()
        val purchaseTx = cashTxs.find { it.refUuid == payables[0].purchaseUuid }
        org.junit.Assert.assertNull("Credit purchase must not create CashTransaction", purchaseTx)
    }

    @Test
    fun test17_physicalStockAndSyncQueueInvariant() = runBlocking {
        val initialStock = database.productDao().getProductById(productId, "LEGACY_BUSINESS")!!.stock

        // Perform Cash Purchase
        productRepository.processAtomicPurchase(
            purchaseItems = mapOf(productId to 5.0),
            supplierId = supplierId
        )

        // Verify physical stock increased
        val updatedProduct = database.productDao().getProductById(productId, "LEGACY_BUSINESS")!!
        assertEquals(initialStock + 5.0, updatedProduct.stock, 0.001)

        // Verify StockMovement recorded
        val movements = database.stockMovementDao().getMovementsForProduct("LEGACY_BUSINESS", updatedProduct.uuid).first()
        val purchaseMovement = movements.find { it.movementType == "PURCHASE" }
        org.junit.Assert.assertNotNull("Stock movement for PURCHASE must be recorded", purchaseMovement)
        assertEquals(5.0, purchaseMovement!!.deltaQuantity, 0.001)

        // Verify SyncQueue contains PURCHASE event
        val syncItems = database.syncQueueDao().getAllItems("LEGACY_BUSINESS").first()
        val purchaseSync = syncItems.find { it.entityType == "PURCHASE" }
        org.junit.Assert.assertNotNull("SyncQueue must contain PURCHASE event", purchaseSync)
        assertEquals("INSERT", purchaseSync!!.operation)
    }
}





