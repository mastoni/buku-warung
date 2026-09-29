package id.skmnetwork.bukuwarung.customer

import id.skmnetwork.bukuwarung.util.loadingFlag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
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
 * Step 23 - the customer list loading contract.
 *
 * `CustomerViewModel.customers` was `stateIn(WhileSubscribed(5000), initialValue = emptyList())`
 * while the ViewModel is created at the app root and nothing on Home subscribes to it. The loading
 * flag, although correctly attached to the raw flow before `stateIn`, therefore resolved long
 * before the screen subscribed: `CustomersScreen` saw `isLoading == false` and an empty list, and
 * rendered "Belum Ada Pelanggan" for ~200-230 ms on every first entry - reproduced on a real
 * 411x891 device three times out of three with 4000 customers already stored.
 *
 * `SharingStarted.Eagerly` is the whole fix, matching STEP 21 for suppliers. These tests pin it
 * and the behaviour around it: the flag placement, the genuine-empty outcome after the first
 * emission, delivery of a non-empty result, and that search results still arrive with a no-match
 * result remaining distinct from a genuinely empty book.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CustomerLoadingStateTest {

    /**
     * Stands in for the repository flow. It is a pair of feeds because the two consumers collect
     * independently: a Room query emits the same result to every collector, whereas a single
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
        val customers = db.forList.receiveAsFlow().stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )
    }

    // ---- 1. the list is started eagerly -------------------------------------

    @Test
    fun `the customer list is started eagerly`() {
        val source = readSource("ui/customer/CustomerViewModel.kt")
        val customersBlock = source.substringAfter("val customers:").substringBefore("fun search(")
        assertTrue(
            "customers must start its upstream eagerly, or the list is still pending when the screen opens",
            customersBlock.contains("SharingStarted.Eagerly")
        )
        assertTrue(
            "The initial value must remain an empty list",
            customersBlock.contains("initialValue = emptyList()")
        )
        assertTrue(
            "The change must be scoped to the customer list, not to every flow in the model",
            !source.substringBefore("val customers:").contains("SharingStarted.Eagerly")
        )
    }

    @Test
    fun `the per-customer detail flows are left alone`() {
        val source = readSource("ui/customer/CustomerViewModel.kt")
        for (flow in listOf("getDebtsForCustomer", "getPaymentsForDebt", "getTotalOutstandingForCustomer")) {
            val start = source.indexOf("fun $flow(")
            assertTrue("Expected to find $flow", start > 0)
            val end = source.indexOf("\n    fun ", start + 1).let { if (it < 0) source.length else it }
            val block = source.substring(start, end)
            assertTrue(
                "$flow is created on demand and must keep its existing WhileSubscribed policy",
                block.contains("SharingStarted.WhileSubscribed(5000)")
            )
        }
    }

    // ---- 2. the flag is still attached before stateIn -----------------------

    @Test
    fun `the loading flag remains attached to the raw flow before stateIn`() {
        val source = readSource("ui/customer/CustomerViewModel.kt")
        val flagLine = source.indexOf("customerSource.loadingFlag(viewModelScope)")
        val stateInLine = source.indexOf(".stateIn(", source.indexOf("val customers:"))
        assertTrue("The flag must still be attached to the raw flow", flagLine > 0)
        assertTrue(
            "The flag must remain declared before stateIn",
            flagLine in 1 until stateInLine
        )
        assertTrue(
            "The flag must come from customerSource, not from a second raw flow",
            source.contains("private val customerSource = searchQuery")
        )
    }

    // ---- 3. an empty result still becomes a genuine empty state -------------

    @Test
    fun `an empty result ends loading and is a genuine empty list`() = runTest {
        val db = Db()
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        try {
            val vm = Harness(scope, db)
            runCurrent()
            assertTrue("Before the first emission the list is unresolved", vm.isLoading.value)
            assertEquals(emptyList<String>(), vm.customers.value)

            db.emit(emptyList())
            runCurrent(); runCurrent()

            assertFalse("An empty first emission must end loading, never spin", vm.isLoading.value)
            assertEquals(
                "An empty result after loading is a genuine empty result",
                emptyList<String>(),
                vm.customers.value
            )
        } finally {
            scope.cancel()
        }
    }

    // ---- 4. a non-empty result is delivered normally ------------------------

    @Test
    fun `a non-empty result is delivered without an intervening empty state`() = runTest {
        val rows = listOf("Pak Budi", "Bu Sari")
        val db = Db()
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        try {
            val vm = Harness(scope, db)
            runCurrent()
            assertTrue("The list is unresolved until the first emission", vm.isLoading.value)

            db.emit(rows)
            runCurrent(); runCurrent()

            assertFalse(vm.isLoading.value)
            assertEquals(rows, vm.customers.value)
        } finally {
            scope.cancel()
        }
    }

    // ---- 5/6. search still updates, and a no-match stays distinct ----------

    @Test
    fun `search results update and a no-match result is delivered as an empty list`() = runTest {
        val db = Db()
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        try {
            val vm = Harness(scope, db)
            runCurrent()
            db.emit(listOf("Pak Budi", "Bu Sari"))
            runCurrent(); runCurrent()
            assertEquals(2, vm.customers.value.size)

            db.emit(listOf("Pak Budi"))
            runCurrent(); runCurrent()
            assertEquals("A narrowed query is delivered", listOf("Pak Budi"), vm.customers.value)

            db.emit(emptyList())
            runCurrent(); runCurrent()
            assertEquals(
                "A query with no match is a genuine empty result for that query",
                emptyList<String>(),
                vm.customers.value
            )
            assertFalse(
                "Searching must not re-enter loading - the flag resolves once",
                vm.isLoading.value
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `search wiring and the screen branches are unchanged`() {
        val vm = readSource("ui/customer/CustomerViewModel.kt")
        val screen = readSource("ui/customer/CustomersScreen.kt")

        assertTrue(
            "Search semantics must be untouched",
            vm.contains("flatMapLatest { query ->") && vm.contains("repository.searchCustomers(query)")
        )
        assertTrue("The screen must still collect the loading flag", screen.contains("customerViewModel.isLoading.collectAsStateWithLifecycle()"))
        val loading = screen.indexOf("if (isLoading) {")
        val empty = screen.indexOf("if (customers.isEmpty()) {")
        assertTrue("A loading branch must exist", loading > 0)
        assertTrue("Loading must still be decided before the empty branch", loading < empty)
        assertTrue(
            "Loading must still reuse the shared component",
            screen.substring(loading, empty).contains("AppLoadingState()")
        )
        assertTrue(
            "The empty state must still distinguish a search with no match",
            screen.contains("CustomersEmptyState(")
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
