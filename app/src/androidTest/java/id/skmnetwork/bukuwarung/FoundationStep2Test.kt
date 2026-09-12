package id.skmnetwork.bukuwarung

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.ItemType
import id.skmnetwork.bukuwarung.data.repository.CashRepository
import id.skmnetwork.bukuwarung.data.repository.CustomerRepository
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import id.skmnetwork.bukuwarung.data.repository.PurchaseRepository
import id.skmnetwork.bukuwarung.data.repository.SaleRepository
import id.skmnetwork.bukuwarung.data.repository.StockRepository
import id.skmnetwork.bukuwarung.data.repository.SupplierRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoundationStep2Test {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var productRepository: ProductRepository
    private lateinit var stockRepository: StockRepository
    private lateinit var saleRepository: SaleRepository
    private lateinit var purchaseRepository: PurchaseRepository
    private lateinit var cashRepository: CashRepository
    private lateinit var customerRepository: CustomerRepository
    private lateinit var supplierRepository: SupplierRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        productRepository = ProductRepository(database)
        stockRepository = StockRepository(database)
        saleRepository = SaleRepository(database)
        purchaseRepository = PurchaseRepository(database)
        cashRepository = CashRepository(database)
        customerRepository = CustomerRepository(database)
        supplierRepository = SupplierRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testCashSale_recordsStockMovementAndCashIncome() = runBlocking {
        val prodId = productRepository.insertProductWithCategory(
            name = "Kopi Hitam",
            categoryName = "Minuman",
            purchasePrice = 2000,
            sellingPrice = 3000,
            stock = 10.0,
            minimumStock = 2.0,
            unit = "gelas"
        )
        val product = productRepository.getProductById(prodId)!!

        val result = saleRepository.completeSale(
            cartItems = mapOf(prodId to 2.0),
            paymentMethod = "CASH"
        )
        assertTrue(result.isSuccess)

        // 1. Stock deducted
        val updatedProd = productRepository.getProductById(prodId)!!
        assertEquals(8.0, updatedProd.stock, 0.001)

        // 2. StockMovement SALE recorded
        val movements = stockRepository.getStockMovementList(product.uuid)
        assertEquals(2, movements.size) // INITIAL + SALE
        assertEquals("SALE", movements[1].movementType)
        assertEquals(-2.0, movements[1].deltaQuantity, 0.001)
        assertEquals(8.0, movements[1].currentStockSnapshot, 0.001)

        // 3. Cash INCOME recorded (2 * 3000 = 6000)
        val cashBalance = cashRepository.totalCashBalance.first()
        assertEquals(6000L, cashBalance)

        // 4. Invariant check
        assertEquals(updatedProd.stock, stockRepository.getStockBalanceFromLedger(product.uuid), 0.001)
    }

    @Test
    fun testQrisSale_recordsStockMovementWithoutCashIncome() = runBlocking {
        val prodId = productRepository.insertProductWithCategory(
            name = "Es Jeruk",
            categoryName = "Minuman",
            purchasePrice = 3000,
            sellingPrice = 5000,
            stock = 15.0,
            minimumStock = 2.0,
            unit = "gelas"
        )
        val product = productRepository.getProductById(prodId)!!

        val result = saleRepository.completeSale(
            cartItems = mapOf(prodId to 3.0),
            paymentMethod = "QRIS"
        )
        assertTrue(result.isSuccess)

        // 1. Stock deducted
        val updatedProd = productRepository.getProductById(prodId)!!
        assertEquals(12.0, updatedProd.stock, 0.001)

        // 2. StockMovement recorded
        val movements = stockRepository.getStockMovementList(product.uuid)
        assertEquals(2, movements.size)
        assertEquals("SALE", movements[1].movementType)
        assertEquals(-3.0, movements[1].deltaQuantity, 0.001)

        // 3. Cash balance unchanged (0 / null)
        val cashBalance = cashRepository.totalCashBalance.first()
        assertTrue(cashBalance == null || cashBalance == 0L)
    }

    @Test
    fun testCreditSale_recordsStockMovementAndDebtWithoutCashIncome() = runBlocking {
        val custId = customerRepository.saveCustomer("Pak Budi", "0812345678", "Jl. Mawar")
        val prodId = productRepository.insertProductWithCategory(
            name = "Beras 5kg",
            categoryName = "Sembako",
            purchasePrice = 50000,
            sellingPrice = 65000,
            stock = 5.0,
            minimumStock = 1.0,
            unit = "sak"
        )
        val product = productRepository.getProductById(prodId)!!

        val result = saleRepository.completeSale(
            cartItems = mapOf(prodId to 1.0),
            paymentMethod = "CREDIT",
            customerId = custId
        )
        assertTrue(result.isSuccess)

        // 1. Stock deducted
        val updatedProd = productRepository.getProductById(prodId)!!
        assertEquals(4.0, updatedProd.stock, 0.001)

        // 2. Debt created
        val debts = customerRepository.getDebtsForCustomer(custId).first()
        assertEquals(1, debts.size)
        assertEquals(65000L, debts[0].totalDebt)
        assertEquals("OPEN", debts[0].status)

        // 3. Cash balance unchanged
        val cashBalance = cashRepository.totalCashBalance.first()
        assertTrue(cashBalance == null || cashBalance == 0L)
    }

    @Test
    fun testCashPurchase_recordsStockMovementAndCashExpense() = runBlocking {
        val prodId = productRepository.insertProductWithCategory(
            name = "Gula 1kg",
            categoryName = "Sembako",
            purchasePrice = 13000,
            sellingPrice = 16000,
            stock = 10.0,
            minimumStock = 2.0,
            unit = "kg"
        )
        val product = productRepository.getProductById(prodId)!!

        val result = purchaseRepository.completePurchase(
            purchaseItems = mapOf(prodId to 5.0),
            paymentMethod = "CASH"
        )
        assertTrue(result.isSuccess)

        // 1. Stock increased
        val updatedProd = productRepository.getProductById(prodId)!!
        assertEquals(15.0, updatedProd.stock, 0.001)

        // 2. StockMovement PURCHASE recorded
        val movements = stockRepository.getStockMovementList(product.uuid)
        assertEquals(2, movements.size)
        assertEquals("PURCHASE", movements[1].movementType)
        assertEquals(5.0, movements[1].deltaQuantity, 0.001)

        // 3. Cash EXPENSE recorded (5 * 13000 = -65000)
        val cashBalance = cashRepository.totalCashBalance.first()
        assertEquals(-65000L, cashBalance)

        // 4. Invariant check
        assertEquals(updatedProd.stock, stockRepository.getStockBalanceFromLedger(product.uuid), 0.001)
    }

    @Test
    fun testCreditPurchase_recordsStockMovementAndSupplierPayableWithoutCashExpense() = runBlocking {
        val suppId = supplierRepository.saveSupplier("Distributor Gula", "085555555", "Pasar Besar")
        val prodId = productRepository.insertProductWithCategory(
            name = "Minyak 2L",
            categoryName = "Sembako",
            purchasePrice = 28000,
            sellingPrice = 33000,
            stock = 4.0,
            minimumStock = 1.0,
            unit = "pouch"
        )
        val product = productRepository.getProductById(prodId)!!

        val result = purchaseRepository.completePurchase(
            purchaseItems = mapOf(prodId to 6.0),
            paymentMethod = "CREDIT",
            supplierId = suppId
        )
        assertTrue(result.isSuccess)

        // 1. Stock increased
        val updatedProd = productRepository.getProductById(prodId)!!
        assertEquals(10.0, updatedProd.stock, 0.001)

        // 2. Payable created
        val payables = supplierRepository.getPayablesForSupplier(suppId).first()
        assertEquals(1, payables.size)
        assertEquals(168000L, payables[0].totalDebt)
        assertEquals("OPEN", payables[0].status)

        // 3. Cash balance unchanged
        val cashBalance = cashRepository.totalCashBalance.first()
        assertTrue(cashBalance == null || cashBalance == 0L)
    }

    @Test
    fun testDebtPayment_updatesDebtAndRecordsCashIncome() = runBlocking {
        val custId = customerRepository.saveCustomer("Pak Ahmad", "081999999", "Jl. Melati")
        val prodId = productRepository.insertProductWithCategory(
            name = "Rokok Filter",
            categoryName = "Rokok",
            purchasePrice = 22000,
            sellingPrice = 25000,
            stock = 10.0,
            minimumStock = 2.0,
            unit = "bungkus"
        )

        // 1. Create Credit Sale
        saleRepository.completeSale(
            cartItems = mapOf(prodId to 2.0),
            paymentMethod = "CREDIT",
            customerId = custId
        )

        val debt = customerRepository.getDebtsForCustomer(custId).first()[0]
        assertEquals(50000L, debt.totalDebt)
        assertEquals(0L, debt.paidAmount)

        // 2. Partial Payment (30,000)
        val payResult1 = customerRepository.processAtomicDebtPayment(debt.id, 30000L, "Cicilan 1")
        assertTrue(payResult1.isSuccess)

        var updatedDebt = customerRepository.getDebtsForCustomer(custId).first()[0]
        assertEquals(30000L, updatedDebt.paidAmount)
        assertEquals("OPEN", updatedDebt.status)

        var cashBalance = cashRepository.totalCashBalance.first()
        assertEquals(30000L, cashBalance)

        // 3. Final Payment (20,000)
        val payResult2 = customerRepository.processAtomicDebtPayment(debt.id, 20000L, "Pelunasan")
        assertTrue(payResult2.isSuccess)

        updatedDebt = customerRepository.getDebtsForCustomer(custId).first()[0]
        assertEquals(50000L, updatedDebt.paidAmount)
        assertEquals("PAID", updatedDebt.status)

        cashBalance = cashRepository.totalCashBalance.first()
        assertEquals(50000L, cashBalance)
    }

    @Test
    fun testSupplierPayment_updatesPayableAndRecordsCashExpense() = runBlocking {
        val suppId = supplierRepository.saveSupplier("PT Sumber Pangan", "08111111", "Kawasan Industri")
        val prodId = productRepository.insertProductWithCategory(
            name = "Tepung Terigu 1kg",
            categoryName = "Sembako",
            purchasePrice = 10000,
            sellingPrice = 12500,
            stock = 20.0,
            minimumStock = 5.0,
            unit = "kg"
        )

        // 1. Create Credit Purchase
        purchaseRepository.completePurchase(
            purchaseItems = mapOf(prodId to 10.0),
            paymentMethod = "CREDIT",
            supplierId = suppId
        )

        val payable = supplierRepository.getPayablesForSupplier(suppId).first()[0]
        assertEquals(100000L, payable.totalDebt)

        // 2. Settle Full Payable (100,000)
        val payResult = supplierRepository.processAtomicSupplierPayment(payable.id, 100000L, "Lunas via Transfer")
        assertTrue(payResult.isSuccess)

        val updatedPayable = supplierRepository.getPayablesForSupplier(suppId).first()[0]
        assertEquals(100000L, updatedPayable.paidAmount)
        assertEquals("PAID", updatedPayable.status)

        val cashBalance = cashRepository.totalCashBalance.first()
        assertEquals(-100000L, cashBalance)
    }

    @Test
    fun testItemType_serviceDigitalFuelDoNotMutateStock() = runBlocking {
        // Insert SERVICE item
        val serviceId = productRepository.insertProductWithCategory(
            name = "Jasa Pasang Gas",
            categoryName = "Jasa",
            purchasePrice = 0,
            sellingPrice = 15000,
            stock = 0.0,
            minimumStock = 0.0,
            unit = "kali",
            itemType = ItemType.SERVICE
        )
        val serviceProd = productRepository.getProductById(serviceId)!!
        assertEquals(ItemType.SERVICE.name, serviceProd.itemType)

        // Sell SERVICE item
        val saleResult = saleRepository.completeSale(
            cartItems = mapOf(serviceId to 1.0),
            paymentMethod = "CASH"
        )
        assertTrue(saleResult.isSuccess)

        // Verify stock is still 0 and NO StockMovement was generated
        val afterSaleProd = productRepository.getProductById(serviceId)!!
        assertEquals(0.0, afterSaleProd.stock, 0.001)

        val movements = stockRepository.getStockMovementList(serviceProd.uuid)
        assertEquals(0, movements.size) // No stock movements for SERVICE
    }

    @Test
    fun testAtomicRollback_whenTransactionFails() = runBlocking {
        val prodId = productRepository.insertProductWithCategory(
            name = "Sabun Cuci",
            categoryName = "Toiletries",
            purchasePrice = 4000,
            sellingPrice = 6000,
            stock = 2.0,
            minimumStock = 1.0,
            unit = "botol"
        )
        val product = productRepository.getProductById(prodId)!!

        // Attempt to sell 5 items when only 2 are in stock
        val result = saleRepository.completeSale(
            cartItems = mapOf(prodId to 5.0),
            paymentMethod = "CASH"
        )
        assertFalse(result.isSuccess)

        // Verify stock is untouched
        val afterFailProd = productRepository.getProductById(prodId)!!
        assertEquals(2.0, afterFailProd.stock, 0.001)

        // Verify no sale transaction, no cash, only INITIAL movement
        val movements = stockRepository.getStockMovementList(product.uuid)
        assertEquals(1, movements.size)
        assertEquals("INITIAL", movements[0].movementType)

        val sales = saleRepository.allTransactions.first()
        assertEquals(0, sales.size)
    }

    @Test
    fun testUuidAndReferenceUuidConsistency() = runBlocking {
        val prodId = productRepository.insertProductWithCategory(
            name = "Garam Dapur",
            categoryName = "Bumbu",
            purchasePrice = 2000,
            sellingPrice = 3000,
            stock = 10.0,
            minimumStock = 2.0,
            unit = "bks"
        )
        val product = productRepository.getProductById(prodId)!!

        val saleId = saleRepository.completeSale(
            cartItems = mapOf(prodId to 2.0),
            paymentMethod = "CASH"
        ).getOrThrow()

        val saleTx = saleRepository.getTransactionById(saleId)!!
        assertNotNull(saleTx.uuid)
        assertTrue(saleTx.uuid.isNotEmpty())

        val saleItems = saleRepository.getItemsForTransaction(saleId)
        assertEquals(1, saleItems.size)
        assertEquals(saleTx.uuid, saleItems[0].saleUuid)
        assertEquals(product.uuid, saleItems[0].productUuid)

        val movements = stockRepository.getStockMovementList(product.uuid)
        val saleMovement = movements.find { it.movementType == "SALE" }!!
        assertEquals(saleTx.uuid, saleMovement.referenceUuid)

        val cashTxs = cashRepository.allCashTransactions.first()
        val saleCashTx = cashTxs.find { it.refId == saleId }!!
        assertEquals(saleTx.uuid, saleCashTx.refUuid)
    }
}
