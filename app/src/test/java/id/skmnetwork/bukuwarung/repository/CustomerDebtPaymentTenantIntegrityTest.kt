package id.skmnetwork.bukuwarung.repository

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
import id.skmnetwork.bukuwarung.data.local.entity.DebtPaymentEntity
import id.skmnetwork.bukuwarung.data.local.entity.SyncQueueEntity
import id.skmnetwork.bukuwarung.data.repository.CustomerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.UUID

/**
 * Gate H.1 - Customer Debt Payment Tenant / Business ID Integrity.
 *
 * Regression coverage for the financial-integrity defect in
 * [CustomerRepository.processAtomicDebtPayment], which built
 * [DebtPaymentEntity] and [CashTransactionEntity] without passing the active
 * businessId, so both silently fell back to the entity default
 * "LEGACY_BUSINESS". In production the active tenant is a real business UUID,
 * so the payment row, the cash income row and the payment history all became
 * invisible to the active business: the debt was shown as paid while the cash
 * ledger never received the money.
 *
 * The pre-existing coverage (CreditSaleAndDebtTest) hardcodes
 * "LEGACY_BUSINESS" as the repository tenant, so the entity default
 * coincidentally equalled the active tenant and the defect was invisible.
 * Every test in this class uses a NON-LEGACY business id, which makes any
 * fallback to "LEGACY_BUSINESS" observable.
 *
 * The in-memory fakes below reproduce the `business_id = :businessId`
 * predicate of the real Room queries in [DebtDao] and [CashDao], so tenant
 * isolation and the cross-tenant invisibility assertions are exercised rather
 * than assumed.
 */
class CustomerDebtPaymentTenantIntegrityTest {

    private companion object {
        const val ACTIVE_BUSINESS = "TEST-BUSINESS-001"
        const val LEGACY_BUSINESS = "LEGACY_BUSINESS"
        const val DEBT_TOTAL = 10000L
    }

    private val customers = mutableMapOf<Long, CustomerEntity>()
    private val debts = mutableMapOf<Long, DebtEntity>()
    private val debtPayments = mutableListOf<DebtPaymentEntity>()
    private val cashTransactions = mutableListOf<CashTransactionEntity>()
    private val syncQueue = mutableListOf<SyncQueueEntity>()

    /** Every write issued through a DAO, so atomicity can be asserted. */
    private val writeLog = mutableListOf<String>()

    /** When set, [CashDao.insertCashTransaction] fails to simulate a mid-transaction fault. */
    private var failCashInsert = false

    private var nextCustomerId = 1L
    private var nextDebtId = 1L
    private var nextDebtPaymentId = 1L
    private var nextCashId = 1L

    private lateinit var database: AppDatabase
    private lateinit var customerRepository: CustomerRepository

    private var customerId: Long = 0
    private var debtId: Long = 0

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    private class TestAppDatabase(
        private val customerDao: CustomerDao,
        private val debtDao: DebtDao,
        private val cashDao: CashDao,
        private val syncDao: SyncQueueDao,
        private val helper: SupportSQLiteOpenHelper
    ) : AppDatabase() {
        override fun customerDao(): CustomerDao = customerDao
        override fun debtDao(): DebtDao = debtDao
        override fun cashDao(): CashDao = cashDao
        override fun syncQueueDao(): SyncQueueDao = syncDao

        override fun categoryDao(): CategoryDao = quietDao(CategoryDao::class.java)
        override fun productDao(): ProductDao = quietDao(ProductDao::class.java)
        override fun saleDao(): SaleDao = quietDao(SaleDao::class.java)
        override fun purchaseDao(): PurchaseDao = quietDao(PurchaseDao::class.java)
        override fun supplierDao(): SupplierDao = quietDao(SupplierDao::class.java)
        override fun supplierPayableDao(): SupplierPayableDao = quietDao(SupplierPayableDao::class.java)
        override fun stockMovementDao(): StockMovementDao = quietDao(StockMovementDao::class.java)
        override fun saleReturnDao(): SaleReturnDao = quietDao(SaleReturnDao::class.java)
        override fun purchaseOrderDao(): PurchaseOrderDao = quietDao(PurchaseOrderDao::class.java)
        override fun digitalTransactionDao(): DigitalTransactionDao = quietDao(DigitalTransactionDao::class.java)

        override val openHelper: SupportSQLiteOpenHelper = helper

        override fun createOpenHelper(config: androidx.room.DatabaseConfiguration): SupportSQLiteOpenHelper = helper
        override fun createInvalidationTracker(): androidx.room.InvalidationTracker {
            return androidx.room.InvalidationTracker(
                this, emptyMap(), emptyMap(), "debt_payments", "cash_transactions"
            )
        }

        override fun clearAllTables() {}

        companion object {
            /**
             * Stand-in for DAOs this test never writes through. The repository
             * constructor only reads a Flow from them, so an empty Flow is enough.
             */
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

    private fun createFakeCustomerDao(): CustomerDao =
        Proxy.newProxyInstance(
            CustomerDao::class.java.classLoader,
            arrayOf(CustomerDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getAllCustomers" -> flowOf(
                    customers.values.filter { it.businessId == args[0] && !it.isDeleted }.toList()
                )
                "getCustomerById" -> {
                    val id = args[0] as Long
                    val business = args[1] as String
                    customers[id]?.takeIf { it.businessId == business && !it.isDeleted }
                }
                "insertCustomer" -> {
                    val customer = args[0] as CustomerEntity
                    val assignedId = if (customer.id > 0) customer.id else nextCustomerId++
                    customers[assignedId] = customer.copy(id = assignedId)
                    writeLog += "insertCustomer"
                    assignedId
                }
                else -> null
            }
        } as CustomerDao

    private fun createFakeDebtDao(): DebtDao =
        Proxy.newProxyInstance(
            DebtDao::class.java.classLoader,
            arrayOf(DebtDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getDebtById" -> {
                    val id = args[0] as Long
                    val business = args[1] as String
                    debts[id]?.takeIf { it.businessId == business }
                }
                "insertDebtPayment" -> {
                    val payment = args[0] as DebtPaymentEntity
                    val assignedId = if (payment.id > 0) payment.id else nextDebtPaymentId++
                    debtPayments += payment.copy(id = assignedId)
                    writeLog += "insertDebtPayment"
                    assignedId
                }
                "updateDebt" -> {
                    val debt = args[0] as DebtEntity
                    debts[debt.id] = debt
                    writeLog += "updateDebt"
                    null
                }
                "getPaymentsForDebt" -> flowOf(paymentsForDebt(args[0] as Long, args[1] as String))
                "getPaymentsListForDebt" -> paymentsForDebt(args[0] as Long, args[1] as String)
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
                    writeLog += "insertCashTransaction"
                    if (failCashInsert) throw IllegalStateException("SIMULATED CASH LEDGER FAULT")
                    val tx = args[0] as CashTransactionEntity
                    val assignedId = if (tx.id > 0) tx.id else nextCashId++
                    cashTransactions += tx.copy(id = assignedId)
                    assignedId
                }
                "getAllCashTransactions" -> flowOf(
                    cashTransactions.filter { it.businessId == args[0] }.sortedByDescending { it.createdAt }
                )
                "getTotalCashBalance" -> flowOf(cashBalance(args[0] as String))
                "getCashIncomeTotal" -> {
                    val business = args[0] as String
                    val start = args[1] as Long
                    val end = args[2] as Long
                    flowOf(
                        cashTransactions
                            .filter { it.businessId == business && it.type == "INCOME" && it.createdAt in start..end }
                            .sumOf { it.amount }
                    )
                }
                else -> null
            }
        } as CashDao

    private fun createFakeSyncQueueDao(): SyncQueueDao =
        Proxy.newProxyInstance(
            SyncQueueDao::class.java.classLoader,
            arrayOf(SyncQueueDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "insert" -> {
                    syncQueue += args[0] as SyncQueueEntity
                    writeLog += "insertSyncQueue"
                    1L
                }
                "getAllItems" -> flowOf(syncQueue.filter { it.businessId == args[0] })
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
            customerDao = createFakeCustomerDao(),
            debtDao = createFakeDebtDao(),
            cashDao = createFakeCashDao(),
            syncDao = createFakeSyncQueueDao(),
            helper = helper
        )

        val directExecutor = java.util.concurrent.Executor { it.run() }
        for (field in androidx.room.RoomDatabase::class.java.declaredFields) {
            field.isAccessible = true
            if (field.type == java.util.concurrent.Executor::class.java) {
                field.set(database, directExecutor)
            }
        }

        // Room's withTransaction needs a real SQLite + Looper, which the JVM test
        // host does not provide. CustomerRepository therefore exposes the same
        // optional transaction seam PurchaseRepository already uses. Here the seam
        // implements genuine snapshot / rollback so atomicity is really exercised
        // rather than assumed.
        customerRepository = CustomerRepository(
            appDatabase = database,
            businessId = ACTIVE_BUSINESS,
            transactionRunner = { block ->
                val debtsSnapshot = debts.toMap()
                val paymentsSnapshot = debtPayments.toList()
                val cashSnapshot = cashTransactions.toList()
                val syncSnapshot = syncQueue.toList()
                try {
                    block()
                } catch (t: Throwable) {
                    debts.clear(); debts.putAll(debtsSnapshot)
                    debtPayments.clear(); debtPayments.addAll(paymentsSnapshot)
                    cashTransactions.clear(); cashTransactions.addAll(cashSnapshot)
                    syncQueue.clear(); syncQueue.addAll(syncSnapshot)
                    throw t
                }
            }
        )

        // Given: a customer and a debt that BOTH belong to the ACTIVE business.
        customerId = nextCustomerId++
        customers[customerId] = CustomerEntity(
            id = customerId,
            uuid = UUID.randomUUID().toString(),
            businessId = ACTIVE_BUSINESS,
            name = "Pak Budi",
            phone = "08123456789",
            address = "Jl. Mawar"
        )
        debts[nextDebtId] = DebtEntity(
            id = nextDebtId,
            uuid = UUID.randomUUID().toString(),
            businessId = ACTIVE_BUSINESS,
            customerId = customerId,
            customerUuid = customers.getValue(customerId).uuid,
            totalDebt = DEBT_TOTAL,
            paidAmount = 0L,
            status = "OPEN"
        )
        debtId = nextDebtId
        writeLog.clear()
    }

    // -----------------------------------------------------------------------
    // A - DebtPaymentEntity.businessId, E - payment history under the active
    //     tenant, G - LEGACY_BUSINESS never sees the payment, H - one row only.
    // -----------------------------------------------------------------------
    @Test
    fun testA_debtPaymentIsPersistedUnderActiveBusinessId() = runBlocking {
        val result = customerRepository.processAtomicDebtPayment(debtId, 4000L, "Cicilan 1")
        assertTrue("Debt payment must succeed", result.isSuccess)

        // A. The persisted payment row carries the active business id.
        assertEquals("Exactly one payment row must be written", 1, debtPayments.size)
        val payment = debtPayments.first()
        assertEquals(
            "DebtPaymentEntity.businessId must be the active business id, not LEGACY_BUSINESS",
            ACTIVE_BUSINESS,
            payment.businessId
        )
        assertEquals(4000L, payment.amount)
        assertEquals(debtId, payment.debtId)

        // E. Payment history read through the repository with the active tenant.
        val activeHistory = customerRepository.getPaymentsForDebt(debtId).first()
        assertEquals(1, activeHistory.size)
        assertEquals(ACTIVE_BUSINESS, activeHistory.first().businessId)

        // G. The same read under LEGACY_BUSINESS must not return the payment.
        assertTrue(
            "LEGACY_BUSINESS must not receive the new debt payment",
            paymentsForDebt(debtId, LEGACY_BUSINESS).isEmpty()
        )

        // H. No duplicate payment row was created.
        assertEquals(1, debtPayments.size)

        // The queued DEBT_PAYMENT sync event targets the same tenant, so
        // cross-device replication cannot strand the payment either.
        val events = syncQueue.filter { it.entityType == "DEBT_PAYMENT" }
        assertEquals(1, events.size)
        assertEquals(ACTIVE_BUSINESS, events.first().businessId)
    }

    // -----------------------------------------------------------------------
    // B - CashTransactionEntity.businessId, D - cash balance includes the
    //     payment, F - cash row visible to active tenant, invisible to legacy.
    // -----------------------------------------------------------------------
    @Test
    fun testB_cashIncomeIsPersistedUnderActiveBusinessId() = runBlocking {
        val result = customerRepository.processAtomicDebtPayment(debtId, 4000L, "Cicilan 1")
        assertTrue("Debt payment must succeed", result.isSuccess)

        // B. The persisted cash row carries the active business id.
        assertEquals("Exactly one cash row must be written", 1, cashTransactions.size)
        val cash = cashTransactions.first()
        assertEquals(
            "CashTransactionEntity.businessId must be the active business id, not LEGACY_BUSINESS",
            ACTIVE_BUSINESS,
            cash.businessId
        )
        assertEquals("INCOME", cash.type)
        assertEquals(4000L, cash.amount)

        // F. Cash read with the active tenant returns the payment, and the row
        //    is linked to the debt payment it settles.
        val activeRows = cashRows(ACTIVE_BUSINESS)
        assertEquals(1, activeRows.size)
        assertEquals(debtPayments.first().uuid, activeRows.first().refUuid)

        // G. Cash read under LEGACY_BUSINESS must not return the payment.
        assertTrue(
            "LEGACY_BUSINESS must not receive the new cash income",
            cashRows(LEGACY_BUSINESS).isEmpty()
        )

        // D. Cash balance for the active business includes the received money.
        assertEquals(4000L, cashBalance(ACTIVE_BUSINESS))
        assertEquals(0L, cashBalance(LEGACY_BUSINESS))
    }

    // -----------------------------------------------------------------------
    // C - debt.paidAmount stays correct and stays reconciled with the ledger.
    // -----------------------------------------------------------------------
    @Test
    fun testC_debtPaidAmountStaysReconciledUnderActiveBusinessId() = runBlocking {
        customerRepository.processAtomicDebtPayment(debtId, 2500L, "Bayar 1")
        customerRepository.processAtomicDebtPayment(debtId, 2500L, "Bayar 2")

        val debt = debts.getValue(debtId)
        assertEquals(ACTIVE_BUSINESS, debt.businessId)

        // C. paidAmount advanced by exactly the two recorded payments.
        assertEquals(5000L, debt.paidAmount)
        assertEquals("OPEN", debt.status)
        assertEquals(5000L, debt.totalDebt - debt.paidAmount)

        // paidAmount == SUM(payments visible to the active tenant)
        val activePayments = paymentsForDebt(debtId, ACTIVE_BUSINESS)
        assertEquals(2, activePayments.size)
        assertEquals(activePayments.sumOf { it.amount }, debt.paidAmount)

        // Cash ledger agrees with the debt ledger.
        assertEquals(5000L, cashBalance(ACTIVE_BUSINESS))
    }

    // -----------------------------------------------------------------------
    // I - Atomicity, across all three failure/ordering modes:
    //    1. a rejected payment issues no write at all;
    //    2. a fault raised mid-transaction, after the payment row was already
    //       written, is reported as a failure AND the partial writes are
    //       discarded by the transaction;
    //    3. an accepted payment applies exactly one write per effect, all
    //       under the active tenant.
    // -----------------------------------------------------------------------
    @Test
    fun testI_atomicityAcrossRejectionFaultAndSuccess() = runBlocking {
        // 1. Validation precedes the first write, so a rejected payment touches
        //    neither the payment table, the debt, the cash ledger nor the queue.
        val overpayment = customerRepository.processAtomicDebtPayment(debtId, DEBT_TOTAL + 1L, "Overpay")
        assertTrue("Overpayment must be rejected", overpayment.isFailure)
        assertTrue("A rejected payment must issue no DAO write at all", writeLog.isEmpty())
        assertTrue(debtPayments.isEmpty())
        assertTrue(cashTransactions.isEmpty())
        assertTrue(syncQueue.isEmpty())
        assertEquals(0L, debts.getValue(debtId).paidAmount)

        // 2. A fault after the payment row was written must not be reported as
        //    success, and must not leave a half-applied financial state.
        failCashInsert = true
        val faulted = customerRepository.processAtomicDebtPayment(debtId, 1000L, "Cicilan 1")
        assertTrue("A mid-transaction fault must surface as a failed Result", faulted.isFailure)
        assertTrue("The payment row must be rolled back", debtPayments.isEmpty())
        assertTrue("The debt update must be rolled back", debts.getValue(debtId).paidAmount == 0L)
        assertTrue("The cash ledger must be untouched", cashTransactions.isEmpty())
        assertTrue("The sync queue must be untouched", syncQueue.isEmpty())

        // 3. The accepted payment is applied once, completely, active tenant only.
        failCashInsert = false
        writeLog.clear()
        val valid = customerRepository.processAtomicDebtPayment(debtId, 1000L, "Cicilan 1")
        assertTrue(valid.isSuccess)

        assertEquals(
            "One payment must produce exactly one payment row, one debt update, one cash row and one sync event",
            listOf("insertDebtPayment", "updateDebt", "insertCashTransaction", "insertSyncQueue"),
            writeLog
        )
        assertEquals(1, debtPayments.size)
        assertEquals(1, cashTransactions.size)
        assertEquals(ACTIVE_BUSINESS, debtPayments.first().businessId)
        assertEquals(ACTIVE_BUSINESS, cashTransactions.first().businessId)
        assertEquals(1000L, debts.getValue(debtId).paidAmount)
        assertEquals(1000L, cashBalance(ACTIVE_BUSINESS))
        assertEquals(0L, cashBalance(LEGACY_BUSINESS))
    }

    // ---- helpers mirroring the real Room queries ---------------------------

    private fun paymentsForDebt(debtIdArg: Long, business: String): List<DebtPaymentEntity> =
        debtPayments.filter { it.debtId == debtIdArg && it.businessId == business }

    private fun cashRows(business: String): List<CashTransactionEntity> =
        cashTransactions.filter { it.businessId == business }

    private fun cashBalance(business: String): Long =
        cashRows(business).sumOf { if (it.type == "INCOME") it.amount else -it.amount }
}
