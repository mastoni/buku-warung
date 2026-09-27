package id.skmnetwork.bukuwarung.domain.money

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import id.skmnetwork.bukuwarung.data.local.dao.CashDao
import id.skmnetwork.bukuwarung.data.local.dao.CategoryDao
import id.skmnetwork.bukuwarung.data.local.dao.CustomerDao
import id.skmnetwork.bukuwarung.data.local.dao.DebtDao
import id.skmnetwork.bukuwarung.data.local.dao.DigitalTransactionDao
import id.skmnetwork.bukuwarung.data.local.dao.ProductDao
import id.skmnetwork.bukuwarung.data.local.dao.PurchaseDao
import id.skmnetwork.bukuwarung.data.local.dao.PurchaseOrderDao
import id.skmnetwork.bukuwarung.data.local.dao.SaleDao
import id.skmnetwork.bukuwarung.data.local.dao.SaleReturnDao
import id.skmnetwork.bukuwarung.data.local.dao.StockMovementDao
import id.skmnetwork.bukuwarung.data.local.dao.SupplierDao
import id.skmnetwork.bukuwarung.data.local.dao.SupplierPayableDao
import id.skmnetwork.bukuwarung.data.local.dao.SyncQueueDao
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CashTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.CustomerEntity
import id.skmnetwork.bukuwarung.data.local.entity.DebtEntity
import id.skmnetwork.bukuwarung.data.local.entity.ItemType
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleReturnItemEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleReturnTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.SaleTransactionEntity
import id.skmnetwork.bukuwarung.data.local.entity.StockMovementEntity
import id.skmnetwork.bukuwarung.data.local.entity.SyncQueueEntity
import id.skmnetwork.bukuwarung.data.repository.SaleRepository
import id.skmnetwork.bukuwarung.domain.checkout.CartLineRequest
import id.skmnetwork.bukuwarung.ui.pos.refundPreviewAmount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.UUID

/**
 * Gate H.2 - fractional quantity correctness across the whole sale and return path.
 *
 * These exercise the real [SaleRepository] so the assertions cover the persisted ledger, not just
 * the calculator: sale subtotals, cash and debt amounts, and fractional stock movements.
 *
 * The refund half of this class is the preview/ledger consistency proof: `refundPreviewAmount`
 * (what the merchant is shown in the POS return dialog) and `processSaleReturn` (what is written
 * to the ledger) are compared for the exact scenario from the gate: 5 kg at Rp 15.000 sold, 1.5 kg
 * returned. Before Gate H.2 the preview said Rp 15.000 and the ledger moved Rp 22.500.
 */
class FractionalQuantitySaleTest {

    private companion object {
        const val BUSINESS = "TEST-BUSINESS-001"
        const val UNIT_PRICE = 15000L
        const val INITIAL_STOCK = 100.0
    }

    private val products = mutableMapOf<Long, ProductEntity>()
    private val customers = mutableMapOf<Long, CustomerEntity>()
    private val debts = mutableMapOf<Long, DebtEntity>()
    private val sales = mutableMapOf<Long, SaleTransactionEntity>()
    private val saleItems = mutableListOf<SaleItemEntity>()
    private val returns = mutableMapOf<Long, SaleReturnTransactionEntity>()
    private val returnItems = mutableListOf<SaleReturnItemEntity>()
    private val stockMovements = mutableListOf<StockMovementEntity>()
    private val cashTransactions = mutableListOf<CashTransactionEntity>()
    private val syncQueue = mutableListOf<SyncQueueEntity>()

    private var nextProductId = 1L
    private var nextCustomerId = 1L
    private var nextSaleId = 1L
    private var nextSaleItemId = 1L
    private var nextReturnId = 1L
    private var nextReturnItemId = 1L
    private var nextMovementId = 1L
    private var nextCashId = 1L

    private lateinit var database: AppDatabase
    private lateinit var saleRepository: SaleRepository

    private class TestAppDatabase(
        private val saleDao: SaleDao,
        private val productDao: ProductDao,
        private val customerDao: CustomerDao,
        private val debtDao: DebtDao,
        private val cashDao: CashDao,
        private val movementDao: StockMovementDao,
        private val returnDao: SaleReturnDao,
        private val syncDao: SyncQueueDao,
        private val helper: SupportSQLiteOpenHelper
    ) : AppDatabase() {
        override fun saleDao(): SaleDao = saleDao
        override fun productDao(): ProductDao = productDao
        override fun customerDao(): CustomerDao = customerDao
        override fun debtDao(): DebtDao = debtDao
        override fun cashDao(): CashDao = cashDao
        override fun stockMovementDao(): StockMovementDao = movementDao
        override fun saleReturnDao(): SaleReturnDao = returnDao
        override fun syncQueueDao(): SyncQueueDao = syncDao

        override fun categoryDao(): CategoryDao = quietDao(CategoryDao::class.java)
        override fun purchaseDao(): PurchaseDao = quietDao(PurchaseDao::class.java)
        override fun supplierDao(): SupplierDao = quietDao(SupplierDao::class.java)
        override fun supplierPayableDao(): SupplierPayableDao = quietDao(SupplierPayableDao::class.java)
        override fun purchaseOrderDao(): PurchaseOrderDao = quietDao(PurchaseOrderDao::class.java)
        override fun digitalTransactionDao(): DigitalTransactionDao = quietDao(DigitalTransactionDao::class.java)

        override val openHelper: SupportSQLiteOpenHelper = helper

        override fun createOpenHelper(config: androidx.room.DatabaseConfiguration): SupportSQLiteOpenHelper = helper
        override fun createInvalidationTracker(): androidx.room.InvalidationTracker =
            androidx.room.InvalidationTracker(this, emptyMap(), emptyMap(), "sale_items", "cash_transactions")

        override fun clearAllTables() {}

        companion object {
            @Suppress("UNCHECKED_CAST")
            fun <T> quietDao(type: Class<T>): T = Proxy.newProxyInstance(
                type.classLoader,
                arrayOf(type)
            ) { _, method, _ ->
                when {
                    Flow::class.java.isAssignableFrom(method.returnType) -> flowOf(emptyList<Any>())
                    method.returnType == java.lang.Long.TYPE -> 0L
                    method.returnType == java.lang.Integer.TYPE -> 0
                    method.returnType == java.lang.Boolean.TYPE -> false
                    else -> null
                }
            } as T
        }
    }

    private fun createFakeProductDao(): ProductDao =
        Proxy.newProxyInstance(
            ProductDao::class.java.classLoader,
            arrayOf(ProductDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getProductById" -> {
                    val id = args[0] as Long
                    val business = args[1] as String
                    products[id]?.takeIf { it.businessId == business && !it.isDeleted }
                }
                "getProductByIdRaw" -> products[args[0] as Long]
                "deductProductStock" -> {
                    val id = args[0] as Long
                    val qty = args[1] as Double
                    val at = args[2] as Long
                    val business = args[3] as String
                    val p = products[id]
                    if (p != null && p.businessId == business) {
                        products[id] = p.copy(stock = p.stock - qty, updatedAt = at)
                    }
                    null
                }
                "addProductStock" -> {
                    val id = args[0] as Long
                    val qty = args[1] as Double
                    val at = args[2] as Long
                    val business = args[3] as String
                    val p = products[id]
                    if (p != null && p.businessId == business) {
                        products[id] = p.copy(stock = p.stock + qty, updatedAt = at)
                    }
                    null
                }
                else -> null
            }
        } as ProductDao

    private fun createFakeSaleDao(): SaleDao =
        Proxy.newProxyInstance(
            SaleDao::class.java.classLoader,
            arrayOf(SaleDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "insertTransaction" -> {
                    val t = args[0] as SaleTransactionEntity
                    val id = nextSaleId++
                    sales[id] = t.copy(id = id)
                    id
                }
                "insertSaleItems" -> {
                    @Suppress("UNCHECKED_CAST")
                    val items = args[0] as List<SaleItemEntity>
                    items.map { item ->
                        val id = nextSaleItemId++
                        saleItems += item.copy(id = id)
                        id
                    }
                }
                "getTransactionById" -> {
                    val id = args[0] as Long
                    sales[id]?.takeIf { it.businessId == args[1] }
                }
                "getItemsForTransaction" -> {
                    val id = args[0] as Long
                    val business = args[1] as String
                    saleItems.filter { it.transactionId == id && it.businessId == business }
                }
                "getAllTransactions" -> flowOf(sales.values.filter { it.businessId == args[0] })
                else -> null
            }
        } as SaleDao

    private fun createFakeCustomerDao(): CustomerDao =
        Proxy.newProxyInstance(
            CustomerDao::class.java.classLoader,
            arrayOf(CustomerDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getCustomerById" -> {
                    val id = args[0] as Long
                    val business = args[1] as String
                    customers[id]?.takeIf { it.businessId == business && !it.isDeleted }
                }
                "getAllCustomers" -> flowOf(customers.values.filter { it.businessId == args[0] }.toList())
                else -> null
            }
        } as CustomerDao

    private fun createFakeDebtDao(): DebtDao =
        Proxy.newProxyInstance(
            DebtDao::class.java.classLoader,
            arrayOf(DebtDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "insertDebt" -> {
                    val d = args[0] as DebtEntity
                    val id = if (d.id > 0) d.id else nextCustomerId + 900L
                    debts[d.saleTransactionId ?: 0L] = d.copy(id = id)
                    id
                }
                "getDebtBySaleId" -> {
                    val saleId = args[0] as Long
                    debts[saleId]?.takeIf { it.businessId == args[1] }
                }
                "updateDebt" -> {
                    val d = args[0] as DebtEntity
                    debts[d.saleTransactionId ?: 0L] = d
                    null
                }
                else -> null
            }
        } as DebtDao

    private fun createFakeCashDao(): CashDao =
        Proxy.newProxyInstance(
            CashDao::class.java.classLoader,
            arrayOf(CashDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "insertCashTransaction" -> {
                    val tx = args[0] as CashTransactionEntity
                    val id = if (tx.id > 0) tx.id else nextCashId++
                    cashTransactions += tx.copy(id = id)
                    id
                }
                else -> null
            }
        } as CashDao

    private fun createFakeMovementDao(): StockMovementDao =
        Proxy.newProxyInstance(
            StockMovementDao::class.java.classLoader,
            arrayOf(StockMovementDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "insertMovement" -> {
                    val m = args[0] as StockMovementEntity
                    stockMovements += m
                    nextMovementId++
                }
                else -> null
            }
        } as StockMovementDao

    private fun createFakeReturnDao(): SaleReturnDao =
        Proxy.newProxyInstance(
            SaleReturnDao::class.java.classLoader,
            arrayOf(SaleReturnDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "insertReturnTransaction" -> {
                    val t = args[0] as SaleReturnTransactionEntity
                    val id = nextReturnId++
                    returns[id] = t.copy(id = id)
                    id
                }
                "insertReturnItems" -> {
                    @Suppress("UNCHECKED_CAST")
                    val items = args[0] as List<SaleReturnItemEntity>
                    items.forEach { returnItems += it.copy(id = nextReturnItemId++) }
                    null
                }
                "getReturnsListForSale" -> returns.values.filter { it.saleTransactionId == args[1] }.toList()
                "getItemsForReturn" -> returnItems.filter { it.returnTransactionId == args[1] }
                "getReturnItemsForSale" -> {
                    val saleId = args[0] as Long
                    returnItems.filter { it.saleItemId in saleItems.filter { s -> s.transactionId == saleId }.map { s -> s.id } }
                }
                "getReturnedQuantityForSaleItem" -> {
                    val saleItemId = args[0] as Long
                    returnItems.filter { it.saleItemId == saleItemId }.sumOf { it.quantity }
                }
                "getTotalReturnedForSale" -> {
                    val saleId = args[1] as Long
                    val ids = saleItems.filter { it.transactionId == saleId }.map { it.id }.toSet()
                    returnItems.filter { it.saleItemId in ids }.sumOf { it.subtotal }
                }
                "getAllReturns" -> flowOf(returns.values.filter { it.businessId == args[0] })
                else -> null
            }
        } as SaleReturnDao

    private fun createFakeSyncQueueDao(): SyncQueueDao =
        Proxy.newProxyInstance(
            SyncQueueDao::class.java.classLoader,
            arrayOf(SyncQueueDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "insert" -> { syncQueue += args[0] as SyncQueueEntity; 1L }
                else -> null
            }
        } as SyncQueueDao

    @Before
    fun setUp() {
        val sqlite = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, _ ->
            when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Long.TYPE -> 0L
                java.lang.Integer.TYPE -> 0
                else -> null
            }
        } as SupportSQLiteDatabase

        val helper = Proxy.newProxyInstance(
            SupportSQLiteOpenHelper::class.java.classLoader,
            arrayOf(SupportSQLiteOpenHelper::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getWritableDatabase" -> sqlite
                "getReadableDatabase" -> sqlite
                "getDatabaseName" -> ":memory:"
                else -> null
            }
        } as SupportSQLiteOpenHelper

        database = TestAppDatabase(
            saleDao = createFakeSaleDao(),
            productDao = createFakeProductDao(),
            customerDao = createFakeCustomerDao(),
            debtDao = createFakeDebtDao(),
            cashDao = createFakeCashDao(),
            movementDao = createFakeMovementDao(),
            returnDao = createFakeReturnDao(),
            syncDao = createFakeSyncQueueDao(),
            helper = helper
        )

        saleRepository = SaleRepository(
            appDatabase = database,
            businessId = BUSINESS,
            transactionRunner = { it() }
        )
    }

    private fun seedFuelProduct(name: String = "Beras", price: Long = UNIT_PRICE): Long {
        val id = nextProductId++
        products[id] = ProductEntity(
            id = id,
            uuid = UUID.randomUUID().toString(),
            businessId = BUSINESS,
            categoryId = 1L,
            name = name,
            purchasePrice = price,
            sellingPrice = price,
            stock = INITIAL_STOCK,
            minimumStock = 1.0,
            itemType = ItemType.PHYSICAL.name
        )
        return id
    }

    private fun seedCustomer(): Long {
        val id = nextCustomerId++
        customers[id] = CustomerEntity(
            id = id,
            uuid = UUID.randomUUID().toString(),
            businessId = BUSINESS,
            name = "Pak Budi"
        )
        return id
    }

    private fun cart(productId: Long, quantity: Double) =
        listOf(CartLineRequest(productId = productId, quantity = quantity))

    // -----------------------------------------------------------------------
    // 1, 2, 3 - sale subtotals for the contract quantities.
    // -----------------------------------------------------------------------

    @Test
    fun saleOfTwoAndAHalfKgBills37500() = runBlocking {
        val productId = seedFuelProduct()
        val result = saleRepository.completeSale(cart(productId, 2.5), "CASH")
        assertTrue("Sale must succeed: ${result.exceptionOrNull()}", result.isSuccess)

        val sale = sales.getValue(result.getOrThrow())
        assertEquals("2.5 kg at 15.000 must bill 37.500", 37500L, sale.totalAmount)
        assertEquals(37500L, sale.subtotalAmount)
        assertEquals(37500L, MoneyCalculator.lineSubtotal(UNIT_PRICE, 2.5))
    }

    @Test
    fun saleOfHalfKgBills7500AndIsNotRejected() = runBlocking {
        // Pre-fix this produced a 0 subtotal and the `grossSubtotal > 0` guard rejected the sale.
        val productId = seedFuelProduct()
        val result = saleRepository.completeSale(cart(productId, 0.5), "CASH")
        assertTrue("A 0.5 kg sale must be accepted, not rejected: ${result.exceptionOrNull()}", result.isSuccess)

        assertEquals("0.5 kg at 15.000 must bill 7.500", 7500L, sales.getValue(result.getOrThrow()).totalAmount)
    }

    @Test
    fun saleOfOneAndAQuarterKgBills18750() = runBlocking {
        val productId = seedFuelProduct()
        val result = saleRepository.completeSale(cart(productId, 1.25), "CASH")
        assertTrue(result.isSuccess)
        assertEquals(18750L, sales.getValue(result.getOrThrow()).totalAmount)
    }

    @Test
    fun saleOfATenthKgBills1500() = runBlocking {
        val productId = seedFuelProduct()
        val result = saleRepository.completeSale(cart(productId, 0.1), "CASH")
        assertTrue(result.isSuccess)
        assertEquals(1500L, sales.getValue(result.getOrThrow()).totalAmount)
    }

    // -----------------------------------------------------------------------
    // 8 - cash and debt totals use the same calculated money amount.
    // -----------------------------------------------------------------------

    @Test
    fun cashLedgerReceivesTheFullFractionalAmount() = runBlocking {
        val productId = seedFuelProduct()
        val result = saleRepository.completeSale(cart(productId, 2.5), "CASH")
        assertTrue(result.isSuccess)

        val income = cashTransactions.single { it.type == "INCOME" }
        assertEquals("Cash INCOME must match the sale total", 37500L, income.amount)
        assertEquals(BUSINESS, income.businessId)
        assertEquals(37500L, cashTransactions.filter { it.type == "INCOME" }.sumOf { it.amount })
    }

    @Test
    fun creditDebtReceivesTheFullFractionalAmount() = runBlocking {
        val productId = seedFuelProduct()
        val customerId = seedCustomer()
        val result = saleRepository.completeSale(cart(productId, 2.5), "CREDIT", customerId)
        assertTrue("Credit sale must succeed: ${result.exceptionOrNull()}", result.isSuccess)

        val debt = debts.values.single()
        assertEquals("Debt must match the fractional sale total", 37500L, debt.totalDebt)
        assertEquals(0L, debt.paidAmount)
        assertEquals("OPEN", debt.status)
        assertEquals(BUSINESS, debt.businessId)
    }

    @Test
    fun saleItemSubtotalAndPersistedQuantityStayFractional() = runBlocking {
        val productId = seedFuelProduct()
        val result = saleRepository.completeSale(cart(productId, 2.5), "CASH")
        assertTrue(result.isSuccess)

        val item = saleItems.single()
        assertEquals("Persisted quantity must stay 2.5", 2.5, item.quantity, 0.0001)
        assertEquals(37500L, item.subtotal)
    }

    // -----------------------------------------------------------------------
    // 7 - fractional stock movement.
    // -----------------------------------------------------------------------

    @Test
    fun stockMovementRemainsFractionalAndConsistent() = runBlocking {
        val productId = seedFuelProduct()
        val result = saleRepository.completeSale(cart(productId, 2.5), "CASH")
        assertTrue(result.isSuccess)

        val movement = stockMovements.single()
        assertEquals("Stock must move by the exact fractional quantity", -2.5, movement.deltaQuantity, 0.0001)
        assertEquals(INITIAL_STOCK - 2.5, movement.currentStockSnapshot, 0.0001)
        assertEquals(INITIAL_STOCK - 2.5, products.getValue(productId).stock, 0.0001)
    }

    @Test
    fun multipleFractionalSalesAccumulateExactly() = runBlocking {
        val productId = seedFuelProduct()
        saleRepository.completeSale(cart(productId, 0.1), "CASH")
        saleRepository.completeSale(cart(productId, 0.2), "CASH")
        saleRepository.completeSale(cart(productId, 2.5), "CASH")

        // 0.1 + 0.2 == 0.30000000000000004 in binary floating point; stock must not inherit that.
        assertEquals(INITIAL_STOCK - 2.8, products.getValue(productId).stock, 0.0000001)
        // 1500 + 3000 + 37500
        assertEquals(42000L, cashTransactions.filter { it.type == "INCOME" }.sumOf { it.amount })
    }

    // -----------------------------------------------------------------------
    // 4 - refund preview == refund ledger, for the exact gate scenario.
    // -----------------------------------------------------------------------

    @Test
    fun refundPreviewMatchesLedgerForOneAndAHalfKgOfFiveKg() = runBlocking {
        val productId = seedFuelProduct()
        val saleId = saleRepository.completeSale(cart(productId, 5.0), "CASH").getOrThrow()
        val saleItem = saleItems.single()

        // Given: 5 kg at 15.000 = Rp 75.000 sold; 1.5 kg returned.
        assertEquals(75000L, saleItem.subtotal)

        // The figure the merchant is shown in the POS return dialog.
        val preview = refundPreviewAmount(saleItem, 1.5)
        // The old preview formula, for the record: 15.000 * 1.5.toLong() == 15.000.
        val oldPreview = saleItem.price * 1.5.toLong()
        assertEquals(15000L, oldPreview)
        assertEquals("Preview must be the correct 22.500", 22500L, preview)

        val result = saleRepository.processSaleReturn(saleId, mapOf(saleItem.id to 1.5), reason = "Barang rusak")
        assertTrue("Return must succeed: ${result.exceptionOrNull()}", result.isSuccess)

        val ledgerRefund = returns.values.single().totalRefundAmount
        val ledgerLine = returnItems.single().subtotal
        assertEquals("Ledger must record 22.500", 22500L, ledgerRefund)
        assertEquals(22500L, ledgerLine)

        assertEquals(
            "Preview and ledger must never disagree",
            preview,
            ledgerRefund
        )
    }

    @Test
    fun refundRestoresFractionalStockAndMovesTheSameMoney() = runBlocking {
        val productId = seedFuelProduct()
        val saleId = saleRepository.completeSale(cart(productId, 5.0), "CASH").getOrThrow()
        val saleItem = saleItems.single()

        saleRepository.processSaleReturn(saleId, mapOf(saleItem.id to 1.5), reason = "Barang rusak")

        // Stock comes back fractionally.
        assertEquals(INITIAL_STOCK - 3.5, products.getValue(productId).stock, 0.0001)
        val returnMovement = stockMovements.first { it.movementType == "RETURN" }
        assertEquals(1.5, returnMovement.deltaQuantity, 0.0001)

        // Cash: one INCOME of 75.000, one EXPENSE of 22.500.
        assertEquals(75000L, cashTransactions.single { it.type == "INCOME" }.amount)
        assertEquals(22500L, cashTransactions.single { it.type == "EXPENSE" }.amount)
        assertEquals(52500L, cashTransactions.filter { it.type == "INCOME" }.sumOf { it.amount } -
                cashTransactions.filter { it.type == "EXPENSE" }.sumOf { it.amount })
    }

    @Test
    fun fullRefundReturnsTheWholeFractionalLine() = runBlocking {
        val productId = seedFuelProduct()
        val saleId = saleRepository.completeSale(cart(productId, 2.5), "CASH").getOrThrow()
        val saleItem = saleItems.single()

        saleRepository.processSaleReturn(saleId, mapOf(saleItem.id to 2.5), reason = "Barang rusak")

        assertEquals(37500L, returns.values.single().totalRefundAmount)
        assertEquals(refundPreviewAmount(saleItem, 2.5), returns.values.single().totalRefundAmount)
        assertEquals(INITIAL_STOCK, products.getValue(productId).stock, 0.0001)
    }
}
