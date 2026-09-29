package id.skmnetwork.bukuwarung.supplier

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
 * Step 21 - the supplier list loading contract.
 *
 * `SupplierViewModel.suppliers` is a `stateIn(..., initialValue = emptyList())`, and
 * `SuppliersScreen` used to render `SuppliersEmptyState` whenever that list was empty. On a real
 * 411x891 device with 4001 suppliers the screen therefore told the merchant "Belum Ada Supplier"
 * for roughly 270 ms - measurably, on every first entry - before the query returned.
 *
 * The fix follows the convention the project already set in `ProductViewModel`: a `loadingFlag`
 * attached to the RAW flow, before `stateIn`, so the initial value can never be read as a real
 * answer. These tests pin that behaviour, the three-way render decision, and the fact that search
 * and query semantics are untouched.
 *
 * The harness reproduces the production wiring - flag on the raw flow, `stateIn` on the same flow,
 * a collector attached the way a screen attaches one - and runs it in `backgroundScope` so nothing
 * is reported as a leaked coroutine.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SupplierLoadingStateTest {

    /**
     * Reproduces the production wiring - loading flag on the raw flow, `stateIn` on the same flow -
     * over an explicit scope the test owns, so the sequence is deterministic and nothing is left
     * running.
     *
     * [db] stands in for the repository flow. It is modelled as a pair of feeds because the two
     * consumers each collect independently: a Room query emits the same result to every collector,
     * whereas a single `Channel` would hand each element to only one of them.
     */
    private class Db {
        val forFlag = Channel<List<String>>(Channel.UNLIMITED)
        val forList = Channel<List<String>>(Channel.UNLIMITED)
        suspend fun emit(rows: List<String>) {
            forFlag.send(rows)
            forList.send(rows)
        }
    }

    private class Harness(
        val scope: CoroutineScope,
        db: Db
    ) {
        val isLoading = db.forFlag.receiveAsFlow().loadingFlag(scope)
        val suppliers = db.forList.receiveAsFlow().stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )
    }

    // ---- 1. loading is distinct from a genuine empty result -----------------

    @Test
    fun `loading is true until the first emission and false after, even when it is empty`() = runTest {
        val db = Db()
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        try {
            val vm = Harness(scope, db)

            // Nothing has been sent yet: the list is unresolved. This is exactly the window in
            // which the old code rendered the misleading "Belum Ada Supplier" state.
            runCurrent()
            assertTrue("Before the first emission the list is unresolved", vm.isLoading.value)
            assertEquals(
                "stateIn still reports its initial value while loading",
                emptyList<String>(),
                vm.suppliers.value
            )

            // An EMPTY first emission is a real result and must end loading, never spin forever.
            db.emit(emptyList())
            runCurrent()
            runCurrent()
            assertFalse(
                "The first emission ends loading even when it is empty",
                vm.isLoading.value
            )
            assertEquals(
                "An empty result after loading is a genuine empty result",
                emptyList<String>(),
                vm.suppliers.value
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `a failed read also ends loading so no state can spin forever`() = runTest {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        try {
            // Only the flag subscribes: loadingFlag catches the failure itself, which is the
            // behaviour under test. A real repository flow is queried independently by each
            // consumer, so isolating the collector here models that faithfully.
            val failing = flow<List<String>> { throw IllegalStateException("read failed") }
            val isLoading = failing.loadingFlag(scope)
            runCurrent()
            assertFalse(
                "A failed read is a finished attempt, matching the LoadingState contract",
                isLoading.value
            )
        } finally {
            scope.cancel()
        }
    }

    // ---- 2/3. non-empty result renders the list path -----------------------

    @Test
    fun `a non-empty result arrives after loading and drives the list branch`() = runTest {
        val rows = listOf("PT Sumber", "PT Makmur")
        val db = Db()
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        try {
            val vm = Harness(scope, db)
            runCurrent()

            assertTrue("The list is unresolved before the first emission", vm.isLoading.value)
            assertTrue(
                "The empty branch must not be chosen while the list is unresolved",
                vm.suppliers.value.isEmpty()
            )

            db.emit(rows)
            runCurrent()
            runCurrent()

            assertFalse(vm.isLoading.value)
            assertEquals("The list branch is chosen once data exists", rows, vm.suppliers.value)
        } finally {
            scope.cancel()
        }
    }

    // ---- 4. the flag is attached before stateIn ----------------------------

    @Test
    fun `the flag is attached to the raw flow before stateIn`() {
        val source = readSource("ui/supplier/SupplierViewModel.kt")
        val flagLine = source.indexOf("suppliersFlow.loadingFlag(viewModelScope)")
        val stateInLine = source.indexOf(".stateIn(", source.indexOf("val suppliers:"))

        assertTrue("The loading flag must be attached to the raw flow", flagLine > 0)
        assertTrue(
            "The flag must be declared before stateIn so the initial value is never read as an answer",
            flagLine in 1 until stateInLine
        )
        assertTrue(
            "suppliers must still be built from the same raw flow",
            source.substringAfter("val suppliers:").contains("suppliersFlow")
        )
        assertTrue(
            "Search semantics must be untouched: the same flatMapLatest over searchSuppliers",
            source.contains("flatMapLatest { query ->") && source.contains("repository.searchSuppliers(query)")
        )
    }

    @Test
    fun `the list upstream starts eagerly because the view model is created at the app root`() {
        // This is the property that actually removes the flash. The ViewModel is built by
        // AppNavigation long before the merchant opens Suppliers, so a WhileSubscribed upstream
        // would still be holding `initialValue` when the screen first composed - the flag would be
        // long resolved while the list was unresolved, and the empty state would be shown for the
        // whole query. Starting eagerly resolves the list before anyone can observe it pending.
        val source = readSource("ui/supplier/SupplierViewModel.kt")
        val suppliersBlock = source.substringAfter("val suppliers:").substringBefore("fun search(")
        assertTrue(
            "suppliers must start its upstream eagerly",
            suppliersBlock.contains("SharingStarted.Eagerly")
        )
        assertTrue(
            "Eagerly must be scoped to suppliers, not to every list in the app",
            !source.substringBefore("val suppliers:").contains("SharingStarted.Eagerly")
        )
        assertTrue(
            "The initial value must remain an empty list",
            suppliersBlock.contains("initialValue = emptyList()")
        )
    }

    // ---- 5. search and query semantics unchanged ----------------------------

    @Test
    fun `successive query results are surfaced, and a no-match search is a genuine empty result`() =
        runTest {
            val db = Db()
            val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
            try {
                val vm = Harness(scope, db)
                runCurrent()
                assertTrue("The list starts unresolved", vm.isLoading.value)

                db.emit(listOf("PT Sumber", "PT Makmur"))
                runCurrent(); runCurrent()
                assertEquals(listOf("PT Sumber", "PT Makmur"), vm.suppliers.value)
                assertFalse(vm.isLoading.value)

                db.emit(listOf("PT Sumber"))
                runCurrent(); runCurrent()
                assertEquals("A narrowed query still surfaces its rows", listOf("PT Sumber"), vm.suppliers.value)

                db.emit(emptyList())
                runCurrent(); runCurrent()
                assertEquals(
                    "A query with no match is a genuine empty result",
                    emptyList<String>(),
                    vm.suppliers.value
                )
                assertFalse(
                    "Searching must not re-enter loading - the flag resolves once",
                    vm.isLoading.value
                )
            } finally {
                scope.cancel()
            }
        }

    // ---- screen contract ---------------------------------------------------

    @Test
    fun `the screen renders loading before choosing between empty and list`() {
        val screen = readSource("ui/supplier/SuppliersScreen.kt")
        assertTrue(
            "The screen must collect the loading flag",
            screen.contains("supplierViewModel.isLoading.collectAsStateWithLifecycle()")
        )
        val loadingBranch = screen.indexOf("if (isLoading) {")
        val emptyBranch = screen.indexOf("else if (suppliers.isEmpty()) {")
        val emptyState = screen.indexOf("SuppliersEmptyState(")
        assertTrue("A loading branch must exist", loadingBranch > 0)
        assertTrue(
            "Loading must be decided before the empty state",
            loadingBranch in 1 until emptyBranch
        )
        assertTrue(
            "The existing empty state must still be used, unmodified",
            emptyBranch > 0 && emptyState > emptyBranch
        )
        assertTrue(
            "Loading must reuse the existing shared component, not a new one",
            screen.substring(loadingBranch, emptyBranch).contains("AppLoadingState()")
        )
    }

    @Test
    fun `the shared loading components and util are untouched by this change`() {
        val appComponents = readSource("ui/components/AppComponents.kt")
        val loadingState = readSource("util/LoadingState.kt")
        assertTrue("AppLoadingState must still exist", appComponents.contains("fun AppLoadingState("))
        assertTrue("loadingFlag must still exist", loadingState.contains("fun <T> Flow<T>.loadingFlag("))
        assertTrue(
            "loadingFlag must keep its contract of ending on the first emission",
            loadingState.contains("flag.value = false")
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
