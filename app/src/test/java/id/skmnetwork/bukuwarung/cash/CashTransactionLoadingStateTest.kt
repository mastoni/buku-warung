package id.skmnetwork.bukuwarung.cash

import id.skmnetwork.bukuwarung.util.loadingFlag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Step 25 - the cash transaction list loading contract.
 *
 * `ProductViewModel.cashTransactions` was `stateIn(WhileSubscribed(5000), emptyList())`. That ViewModel
 * is created at the app root and CashScreen is its only collector, so the query had not run when the
 * ledger opened and the screen rendered "Belum Ada Transaksi Kas" - measured at ~200 ms on device
 * across two runs, with the same frame's header already showing "Saldo Kas Rp 34.000.000" derived
 * from the 500 transactions the list was claiming did not exist.
 *
 * `SharingStarted.Eagerly` is the whole fix, the same change STEP 21 made for suppliers and STEP 23
 * for customers. These tests pin it, its scoping, and the behaviour around it: a genuine empty
 * ledger must still be distinguishable, a populated ledger must arrive, the balance contract is
 * untouched, and no other flow in the model moved.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CashTransactionLoadingStateTest {

    /**
     * Stands in for the repository cash-transaction flow. A pair of feeds, because the consumers
     * collect independently: a Room query emits the same result to every collector, whereas one
     * `Channel` would hand each element to only one of them.
     */
    private class Db {
        val forFlag = Channel<List<String>>(Channel.UNLIMITED)
        val forList = Channel<List<String>>(Channel.UNLIMITED)
        suspend fun emit(rows: List<String>) {
            forFlag.send(rows)
            forList.send(rows)
        }
    }

    private class Harness(val scope: CoroutineScope, db: Db) {
        val isLoading = db.forFlag.receiveAsFlow().loadingFlag(scope)
        val transactions = db.forList.receiveAsFlow().stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )
    }

    // ---- 1 & 2. the intended start policy, scoped to this one flow ----------

    @Test
    fun `cash transactions start eagerly`() {
        val source = readSource("ui/product/ProductViewModel.kt")
        val block = source
            .substringAfter("val cashTransactions:")
            .substringBefore("val purchases:")
        assertTrue(
            "cashTransactions must start its upstream eagerly, or the ledger shows as empty while loading",
            block.contains("SharingStarted.Eagerly")
        )
        assertTrue(
            "The initial value must remain an empty list",
            block.contains("initialValue = emptyList()")
        )
    }

    @Test
    fun `no other ProductViewModel flow was changed`() {
        val source = readSource("ui/product/ProductViewModel.kt")
        // Each flow's pre-existing policy, unchanged by this gate.
        val expected = mapOf(
            "products" to "Lazily",
            "categories" to "Lazily",
            "purchases" to "WhileSubscribed",
            "sales" to "WhileSubscribed",
            "returns" to "WhileSubscribed"
        )
        for ((flow, policy) in expected) {
            val start = source.indexOf("val $flow:")
            assertTrue("Expected to find $flow", start > 0)
            val end = source.indexOf("\n    val ", start + 1).let { if (it < 0) source.length else it }
            val block = source.substring(start, end)
            assertTrue(
                "$flow is out of scope and must keep its SharingStarted.$policy policy",
                block.contains("SharingStarted.$policy")
            )
            assertTrue(
                "$flow must not have been switched to Eagerly",
                !block.contains("SharingStarted.Eagerly")
            )
        }
    }

    @Test
    fun `exactly one flow in the model is eager`() {
        val source = readSource("ui/product/ProductViewModel.kt")
        assertEquals(
            "The change must be scoped to cashTransactions alone",
            1,
            Regex("started = SharingStarted\\.Eagerly").findAll(source).count()
        )
    }

    // ---- 3. a genuine empty ledger is still distinguishable ----------------

    @Test
    fun `an empty ledger ends loading and becomes a genuine empty result`() = runTest {
        val db = Db()
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        try {
            val vm = Harness(scope, db)
            runCurrent()
            assertTrue("Before the first emission the ledger is unresolved", vm.isLoading.value)
            assertEquals(emptyList<String>(), vm.transactions.value)

            db.emit(emptyList())
            runCurrent(); runCurrent()

            assertFalse(
                "An empty first emission must end loading, never spin",
                vm.isLoading.value
            )
            assertEquals(
                "An empty result after loading is a genuine empty ledger",
                emptyList<String>(),
                vm.transactions.value
            )
        } finally {
            scope.cancel()
        }
    }

    // ---- 4. a populated ledger arrives normally ----------------------------

    @Test
    fun `a populated ledger is delivered without an intervening empty state`() = runTest {
        val rows = listOf("Pemasukan Rp 25.000", "Pengeluaran Rp 5.000")
        val db = Db()
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        try {
            val vm = Harness(scope, db)
            runCurrent()
            assertTrue("The ledger is unresolved until the first emission", vm.isLoading.value)

            db.emit(rows)
            runCurrent(); runCurrent()

            assertFalse(vm.isLoading.value)
            assertEquals(rows, vm.transactions.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `successive emissions are surfaced, as they are when income and expense are added`() = runTest {
        val db = Db()
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        try {
            val vm = Harness(scope, db)
            runCurrent()
            db.emit(listOf("Pemasukan Rp 25.000"))
            runCurrent(); runCurrent()
            assertEquals(1, vm.transactions.value.size)

            db.emit(listOf("Pemasukan Rp 25.000", "Pengeluaran Rp 5.000"))
            runCurrent(); runCurrent()
            assertEquals("A later write is pushed into the open list", 2, vm.transactions.value.size)
            assertFalse("Writing must not re-enter loading", vm.isLoading.value)
        } finally {
            scope.cancel()
        }
    }

    // ---- 5 & 6. balance, screen gate and write path untouched ---------------

    @Test
    fun `the cash screen gate and the balance calculation are untouched`() {
        val screen = readSource("ui/cash/CashScreen.kt")
        val repository = readSource("data/repository/CashRepository.kt")

        assertTrue(
            "The screen must still gate on the existing viewModel.isLoading flag - this gate does not repurpose it",
            screen.contains("viewModel.isLoading.collectAsStateWithLifecycle()")
        )
        val loading = screen.indexOf("if (isLoading) {")
        assertTrue("The loading branch must still exist", loading > 0)
        assertTrue(
            "Loading must still be decided before the cash list is rendered",
            loading < screen.indexOf("if (filteredTransactions.isEmpty()) {")
        )
        assertTrue(
            "The genuine empty state must still be the one the screen already used",
            screen.contains("Belum Ada Transaksi Kas")
        )
        assertTrue(
            "The balance must still come from the repository, not from the list",
            screen.contains("CashHeader(")
        )
        assertTrue(
            "The repository's balance flow must be untouched",
            repository.contains("totalCashBalance")
        )
    }

    private fun readSource(relative: String): String {
        val candidates = listOf(
            File("src/main/java/id/skmnetwork/bukuwarung/$relative"),
            File("app/src/main/java/id/skmnetwork/bukuwarung/$relative")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Source not found; looked at ${candidates.joinToString { it.absolutePath }}")
        return file.readText()
    }
}
