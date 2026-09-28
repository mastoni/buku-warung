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
import id.skmnetwork.bukuwarung.ui.customer.CustomerViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.UUID

/**
 * Step 8 - explicit debt payment targeting.
 *
 * A customer can hold several open debts at once: [id.skmnetwork.bukuwarung.data.repository.SaleRepository]
 * inserts one `debts` row per credit sale, and neither the schema nor the repository collapses
 * them. The pay-debt UI used to settle `openDebts.first()` - the newest open row of a list the
 * DAO returns ordered `created_at DESC` - so with two open debts the merchant paid whichever row
 * happened to sort first while the dialog only showed bare totals and never named the debt.
 *
 * These tests pin the contract that makes the target explicit and safe:
 *  - a payment settles exactly the debt it was given, and never another open debt of the same
 *    customer (the Step 8 critical scenario: Debt #1 = Rp 100.000, Debt #2 = Rp 200.000, pay #2);
 *  - the recorded payment row, the cash income row and the sync event all stay bound to that one
 *    debt and to the active tenant;
 *  - partial and full settlement still work, and the customer's total outstanding is the sum over
 *    its open debts, so it moves only by the amount actually paid on the target debt;
 *  - amounts are still validated against the TARGET debt's remaining balance, not against the
 *    customer's aggregate, so a payment can never silently spill onto a second debt;
 *  - zero, negative, unknown and cross-tenant debts are rejected without writing anything.
 *
 * The in-memory fakes reproduce the `business_id = :businessId` and `status = 'OPEN'` predicates of
 * the real Room queries in [DebtDao] and [CashDao], so tenant isolation and the aggregate total are
 * exercised rather than assumed. This mirrors the harness already used by
 * [CustomerDebtPaymentTenantIntegrityTest].
 */
class CustomerDebtPaymentTargetingTest {

    private companion object {
        const val ACTIVE_BUSINESS = "TEST-BUSINESS-001"
        const val OTHER_BUSINESS = "TEST-BUSINESS-002"
        const val DEBT_1_TOTAL = 100_000L
        const val DEBT_2_TOTAL = 200_000L
    }

    private val customers = mutableMapOf<Long, CustomerEntity>()
    private val debts = mutableMapOf<Long, DebtEntity>()
    private val debtPayments = mutableListOf<DebtPaymentEntity>()
    private val cashTransactions = mutableListOf<CashTransactionEntity>()
    private val syncQueue = mutableListOf<SyncQueueEntity>()
    private val writeLog = mutableListOf<String>()

    private var nextCustomerId = 1L
    private var nextDebtId = 1L
    private var nextDebtPaymentId = 1L
    private var nextCashId = 1L

    private lateinit var database: AppDatabase
    private lateinit var customerRepository: CustomerRepository

    private var customerId: Long = 0
    private var debt1Id: Long = 0
    private var debt2Id: Long = 0

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
                "getDebtsForCustomer" -> flowOf(
                    debts.values
                        .filter { it.businessId == args[1] && it.customerId == args[0] }
                        .sortedByDescending { it.createdAt }
                )
                "getTotalOutstandingForCustomer" -> flowOf(
                    debts.values
                        .filter { it.businessId == args[1] && it.customerId == args[0] && it.status == "OPEN" }
                        .sumOf { it.totalDebt - it.paidAmount }
                )
                "insertDebtPayment" -> {
                    val payment = args[0] as DebtPaymentEntity
                    val assignedId = if (payment.id > 0) payment.id else nextDebtPaymentId++
                    debtPayments += payment.copy(id = assignedId)
                    writeLog += "insertDebtPayment:${payment.debtId}"
                    assignedId
                }
                "updateDebt" -> {
                    val debt = args[0] as DebtEntity
                    debts[debt.id] = debt
                    writeLog += "updateDebt:${debt.id}"
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
                    val tx = args[0] as CashTransactionEntity
                    val assignedId = if (tx.id > 0) tx.id else nextCashId++
                    cashTransactions += tx.copy(id = assignedId)
                    writeLog += "insertCashTransaction"
                    assignedId
                }
                "getAllCashTransactions" -> flowOf(
                    cashTransactions.filter { it.businessId == args[0] }.sortedByDescending { it.createdAt }
                )
                "getTotalCashBalance" -> flowOf(cashBalance(args[0] as String))
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

        // Customer A with two open debts created by two separate credit sales:
        //   Debt #1 = Rp 100.000, Debt #2 = Rp 200.000
        customerId = nextCustomerId++
        customers[customerId] = CustomerEntity(
            id = customerId,
            uuid = UUID.randomUUID().toString(),
            businessId = ACTIVE_BUSINESS,
            name = "Pak Budi",
            phone = "08123456789",
            address = "Jl. Mawar"
        )
        val customerUuid = customers.getValue(customerId).uuid
        val now = System.currentTimeMillis()

        debt1Id = nextDebtId++
        debts[debt1Id] = DebtEntity(
            id = debt1Id,
            uuid = UUID.randomUUID().toString(),
            businessId = ACTIVE_BUSINESS,
            customerId = customerId,
            customerUuid = customerUuid,
            totalDebt = DEBT_1_TOTAL,
            paidAmount = 0L,
            status = "OPEN",
            createdAt = now - 10_000L
        )
        debt2Id = nextDebtId++
        debts[debt2Id] = DebtEntity(
            id = debt2Id,
            uuid = UUID.randomUUID().toString(),
            businessId = ACTIVE_BUSINESS,
            customerId = customerId,
            customerUuid = customerUuid,
            totalDebt = DEBT_2_TOTAL,
            paidAmount = 0L,
            status = "OPEN",
            createdAt = now
        )
        writeLog.clear()
    }

    // -----------------------------------------------------------------------
    // The Step 8 critical scenario: paying Debt #2 must not touch Debt #1.
    // -----------------------------------------------------------------------
    @Test
    fun testPaymentTargetsTheSelectedDebtAndLeavesTheOtherUntouched() = runBlocking {
        assertEquals("The fixture must hold two open debts", 2, openDebts().size)

        val result = customerRepository.processAtomicDebtPayment(debt2Id, 50_000L, "Bayarhutang #2")
        assertTrue("Payment aimed at Debt #2 must succeed", result.isSuccess)

        // Debt #2 is reduced by exactly what was paid and stays open.
        val debt2 = debts.getValue(debt2Id)
        assertEquals(50_000L, debt2.paidAmount)
        assertEquals(150_000L, debt2.totalDebt - debt2.paidAmount)
        assertEquals("OPEN", debt2.status)

        // Debt #1 is untouched - not a rupiah of it moved.
        val debt1 = debts.getValue(debt1Id)
        assertEquals("Debt #1 must remain Rp 100.000", DEBT_1_TOTAL, debt1.totalDebt)
        assertEquals("Debt #1 must not be paid at all", 0L, debt1.paidAmount)
        assertEquals("OPEN", debt1.status)
        assertTrue("Debt #1 must have no payment rows", paymentsForDebt(debt1Id, ACTIVE_BUSINESS).isEmpty())

        // The payment record references Debt #2 specifically.
        val payments = paymentsForDebt(debt2Id, ACTIVE_BUSINESS)
        assertEquals(1, payments.size)
        assertEquals(debt2Id, payments.first().debtId)
        assertEquals(debts.getValue(debt2Id).uuid, payments.first().debtUuid)
        assertEquals(50_000L, payments.first().amount)
        assertEquals(ACTIVE_BUSINESS, payments.first().businessId)

        // The debt history of the target debt shows the payment; the sibling's does not.
        assertEquals(1, customerRepository.getPaymentsForDebt(debt2Id).first().size)
        assertTrue(customerRepository.getPaymentsForDebt(debt1Id).first().isEmpty())

        // Customer total outstanding drops by the paid amount only.
        assertEquals(250_000L, totalOutstanding())

        // Cash and sync stay bound to this one payment, under the active tenant.
        val cash = cashRows(ACTIVE_BUSINESS)
        assertEquals(1, cash.size)
        assertEquals("INCOME", cash.first().type)
        assertEquals(50_000L, cash.first().amount)
        assertEquals(payments.first().uuid, cash.first().refUuid)
        assertEquals(50_000L, cashBalance(ACTIVE_BUSINESS))
        assertEquals(0L, cashBalance(OTHER_BUSINESS))

        val events = syncQueue.filter { it.entityType == "DEBT_PAYMENT" }
        assertEquals(1, events.size)
        assertEquals(payments.first().uuid, events.first().entityUuid)
        assertEquals(ACTIVE_BUSINESS, events.first().businessId)
    }

    // -----------------------------------------------------------------------
    // Full settlement of the target closes only that debt.
    // -----------------------------------------------------------------------
    @Test
    fun testFullPaymentClosesTheTargetDebtOnly() = runBlocking {
        val result = customerRepository.processAtomicDebtPayment(debt1Id, DEBT_1_TOTAL, "Lunas")
        assertTrue(result.isSuccess)

        val debt1 = debts.getValue(debt1Id)
        assertEquals(DEBT_1_TOTAL, debt1.paidAmount)
        assertEquals("PAID", debt1.status)

        val debt2 = debts.getValue(debt2Id)
        assertEquals(0L, debt2.paidAmount)
        assertEquals("OPEN", debt2.status)

        assertEquals(DEBT_2_TOTAL, totalOutstanding())
    }

    // -----------------------------------------------------------------------
    // Partial payment stays supported: the target debt remains open and the
    // remaining amount is still payable afterwards.
    // -----------------------------------------------------------------------
    @Test
    fun testPartialPaymentsAccumulateOnTheTargetDebt() = runBlocking {
        assertTrue(customerRepository.processAtomicDebtPayment(debt2Id, 100_000L, "Cicilan 1").isSuccess)
        assertTrue(customerRepository.processAtomicDebtPayment(debt2Id, 60_000L, "Cicilan 2").isSuccess)

        val debt2 = debts.getValue(debt2Id)
        assertEquals(160_000L, debt2.paidAmount)
        assertEquals("OPEN", debt2.status)
        assertEquals(40_000L, debt2.totalDebt - debt2.paidAmount)

        // paidAmount reconciles with the ledger of the target debt.
        val payments = paymentsForDebt(debt2Id, ACTIVE_BUSINESS)
        assertEquals(2, payments.size)
        assertEquals(160_000L, payments.sumOf { it.amount })
        assertEquals(160_000L, cashBalance(ACTIVE_BUSINESS))

        // The final instalment closes exactly the target debt.
        assertTrue(customerRepository.processAtomicDebtPayment(debt2Id, 40_000L, "Lunas").isSuccess)
        assertEquals("PAID", debts.getValue(debt2Id).status)
        assertEquals(DEBT_1_TOTAL, totalOutstanding())
        assertEquals(0L, debts.getValue(debt1Id).paidAmount)
    }

    // -----------------------------------------------------------------------
    // The limit is the TARGET debt's remaining balance, not the customer's
    // aggregate. Otherwise an over-large entry would spill onto a second debt.
    // -----------------------------------------------------------------------
    @Test
    fun testAmountAboveTargetRemainingIsRejectedEvenWhenCustomerTotalIsLarger() = runBlocking {
        // The customer owes Rp 300.000 in total, but only Rp 200.000 on Debt #2.
        val overpayment = customerRepository.processAtomicDebtPayment(debt2Id, 250_000L, "Kelebihan")
        assertTrue("An amount above the target debt's remaining must be rejected", overpayment.isFailure)

        assertTrue("A rejected payment must issue no DAO write at all", writeLog.isEmpty())
        assertTrue(debtPayments.isEmpty())
        assertTrue(cashTransactions.isEmpty())
        assertTrue(syncQueue.isEmpty())
        assertEquals(0L, debts.getValue(debt2Id).paidAmount)
        assertEquals(0L, debts.getValue(debt1Id).paidAmount)
        assertEquals(300_000L, totalOutstanding())

        // Exactly the remaining amount is accepted.
        assertTrue(customerRepository.processAtomicDebtPayment(debt2Id, DEBT_2_TOTAL, "Lunas").isSuccess)
        assertEquals("PAID", debts.getValue(debt2Id).status)
    }

    // -----------------------------------------------------------------------
    // Zero, negative, unknown debt and cross-tenant debt are all rejected
    // before any write happens.
    // -----------------------------------------------------------------------
    @Test
    fun testInvalidPaymentsAreRejectedWithoutAnyWrite() = runBlocking {
        assertTrue(customerRepository.processAtomicDebtPayment(debt2Id, 0L, "Nol").isFailure)
        assertTrue(customerRepository.processAtomicDebtPayment(debt2Id, -5_000L, "Negatif").isFailure)
        assertTrue(customerRepository.processAtomicDebtPayment(9_999L, 1_000L, "Tidak ada").isFailure)
        assertTrue("A rejected payment must issue no DAO write at all", writeLog.isEmpty())
        assertTrue(debtPayments.isEmpty())
        assertTrue(cashTransactions.isEmpty())
        assertEquals(300_000L, totalOutstanding())
    }

    // -----------------------------------------------------------------------
    // A debt that is already settled cannot be paid again, and the rejection
    // does not touch the customer's other open debt.
    // -----------------------------------------------------------------------
    @Test
    fun testSettledDebtRejectsFurtherPayment() = runBlocking {
        assertTrue(customerRepository.processAtomicDebtPayment(debt1Id, DEBT_1_TOTAL, "Lunas").isSuccess)
        writeLog.clear()

        val second = customerRepository.processAtomicDebtPayment(debt1Id, 1_000L, "Lagi")
        assertTrue("An already settled debt must reject a further payment", second.isFailure)
        assertTrue("The rejected payment must not write anything", writeLog.isEmpty())
        assertEquals(DEBT_1_TOTAL, debts.getValue(debt1Id).paidAmount)
        assertEquals(DEBT_2_TOTAL, totalOutstanding())
    }

    // -----------------------------------------------------------------------
    // Tenant binding: another business' debt id is not reachable even though
    // the id exists in the same table.
    // -----------------------------------------------------------------------
    @Test
    fun testPaymentCannotReachADebtOfAnotherTenant() = runBlocking {
        val foreignCustomerId = nextCustomerId++
        customers[foreignCustomerId] = CustomerEntity(
            id = foreignCustomerId,
            uuid = UUID.randomUUID().toString(),
            businessId = OTHER_BUSINESS,
            name = "Pak Asing"
        )
        val foreignDebtId = nextDebtId++
        debts[foreignDebtId] = DebtEntity(
            id = foreignDebtId,
            uuid = UUID.randomUUID().toString(),
            businessId = OTHER_BUSINESS,
            customerId = foreignCustomerId,
            customerUuid = customers.getValue(foreignCustomerId).uuid,
            totalDebt = 75_000L,
            paidAmount = 0L,
            status = "OPEN"
        )
        writeLog.clear()

        val result = customerRepository.processAtomicDebtPayment(foreignDebtId, 10_000L, "Cross tenant")
        assertTrue("A foreign tenant's debt must not be payable from this tenant", result.isFailure)
        assertTrue("The rejected payment must not write anything", writeLog.isEmpty())
        assertEquals(0L, debts.getValue(foreignDebtId).paidAmount)
        assertEquals(0L, cashBalance(ACTIVE_BUSINESS))
        assertTrue(syncQueue.isEmpty())
    }

    // -----------------------------------------------------------------------
    // One payment settles one debt: paying several debts needs several calls
    // and each produces exactly one payment row, cash row and sync event.
    // -----------------------------------------------------------------------
    @Test
    fun testOnePaymentNeverSettlesMoreThanOneDebt() = runBlocking {
        assertTrue(customerRepository.processAtomicDebtPayment(debt1Id, DEBT_1_TOTAL, "Lunas #1").isSuccess)
        assertTrue(customerRepository.processAtomicDebtPayment(debt2Id, DEBT_2_TOTAL, "Lunas #2").isSuccess)

        assertEquals("Each payment writes exactly one payment row", 2, debtPayments.size)
        assertEquals(setOf(debt1Id, debt2Id), debtPayments.map { it.debtId }.toSet())
        assertEquals(2, cashTransactions.size)
        assertEquals(2, syncQueue.count { it.entityType == "DEBT_PAYMENT" })
        assertEquals(300_000L, cashBalance(ACTIVE_BUSINESS))
        assertEquals("PAID", debts.getValue(debt1Id).status)
        assertEquals("PAID", debts.getValue(debt2Id).status)
        assertEquals(0L, totalOutstanding())
    }

    // -----------------------------------------------------------------------
    // Scenario A: a customer with a single open debt pays part of it.
    // -----------------------------------------------------------------------
    @Test
    fun testSingleOpenDebtIsPaidPartially() = runBlocking {
        // Close the sibling so this customer is left with Debt #1 = Rp 100.000 only.
        assertTrue(
            customerRepository.processAtomicDebtPayment(debt2Id, DEBT_2_TOTAL, "Lunas dulu").isSuccess
        )
        assertEquals(1, openDebts().size)

        val result = customerRepository.processAtomicDebtPayment(debt1Id, 25_000L, "Cicilan")
        assertTrue("Paying the only open debt must succeed", result.isSuccess)

        val debt1 = debts.getValue(debt1Id)
        assertEquals(DEBT_1_TOTAL, debt1.totalDebt)
        assertEquals(25_000L, debt1.paidAmount)
        assertEquals("Sisa hutang harus Rp 75.000", 75_000L, debt1.totalDebt - debt1.paidAmount)
        assertEquals("OPEN", debt1.status)
        assertEquals("Hanya Debt #1 yang tersisa", 75_000L, totalOutstanding())
        // Cash holds the earlier Rp 200.000 settlement plus this Rp 25.000 instalment.
        assertEquals(DEBT_2_TOTAL + 25_000L, cashBalance(ACTIVE_BUSINESS))
        assertTrue(
            "Only the selected debt may have a payment row",
            paymentsForDebt(debt1Id, ACTIVE_BUSINESS).all { it.amount == 25_000L }
        )
    }

    // -----------------------------------------------------------------------
    // Scenario F: the debt id the UI hands to the payment action is the id the
    // repository settles, all the way through the ViewModel the dialog uses.
    // -----------------------------------------------------------------------
    @Test
    fun testViewModelPaysExactlyTheSelectedDebt() = runTest {
        // CustomerViewModel touches viewModelScope in its constructor, so the Main dispatcher has
        // to be installed before the ViewModel is built.
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = CustomerViewModel(customerRepository)
            val success = CompletableDeferred<Unit>()
            val failure = CompletableDeferred<String>()

            // The dialog passes the selected debt's id, not a list position.
            viewModel.payDebt(
                debtId = debt2Id,
                amountStr = "50000",
                note = "Dari dialog",
                onSuccess = { success.complete(Unit) },
                onError = { failure.complete(it) }
            )
            advanceUntilIdle()

            // Checked rather than awaited: a failing payment completes the error deferred, so
            // awaiting the success path would deadlock the test dispatcher.
            if (failure.isCompleted) {
                fail("The payment must succeed, but failed with: ${failure.await()}")
            }
            assertTrue("The payment must report success", success.isCompleted)

            val debt2 = debts.getValue(debt2Id)
            assertEquals(50_000L, debt2.paidAmount)
            assertEquals(0L, debts.getValue(debt1Id).paidAmount)
            assertEquals(
                "Exactly one payment row, and it must reference the selected debt",
                listOf(debt2Id),
                debtPayments.map { it.debtId }
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    // -----------------------------------------------------------------------
    // Scenario F, negative case: the ViewModel still rejects a non-positive amount
    // before any debt is touched.
    // -----------------------------------------------------------------------
    @Test
    fun testViewModelRejectsNonPositiveAmountForTheSelectedDebt() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = CustomerViewModel(customerRepository)
            for (amount in listOf("0", "-1000", "", "abc")) {
                val failure = CompletableDeferred<String>()
                viewModel.payDebt(
                    debtId = debt2Id,
                    amountStr = amount,
                    note = null,
                    onSuccess = { failure.complete("") },
                    onError = { failure.complete(it) }
                )
                advanceUntilIdle()
                assertTrue("'$amount' must be rejected", failure.isCompleted)
                assertTrue(
                    "'$amount' must be rejected with a non-empty message",
                    failure.await().isNotEmpty()
                )
            }
            assertTrue("No debt may be touched by a rejected amount", writeLog.isEmpty())
            assertEquals(0L, debts.getValue(debt1Id).paidAmount)
            assertEquals(0L, debts.getValue(debt2Id).paidAmount)
        } finally {
            Dispatchers.resetMain()
        }
    }

    // ---- helpers mirroring the real Room queries ---------------------------

    private fun openDebts(): List<DebtEntity> =
        debts.values.filter { it.businessId == ACTIVE_BUSINESS && it.customerId == customerId && it.status == "OPEN" }

    private suspend fun totalOutstanding(): Long =
        customerRepository.getTotalOutstandingForCustomer(customerId).first() ?: 0L

    private fun paymentsForDebt(debtIdArg: Long, business: String): List<DebtPaymentEntity> =
        debtPayments.filter { it.debtId == debtIdArg && it.businessId == business }

    private fun cashRows(business: String): List<CashTransactionEntity> =
        cashTransactions.filter { it.businessId == business }

    private fun cashBalance(business: String): Long =
        cashRows(business).sumOf { if (it.type == "INCOME") it.amount else -it.amount }
}
