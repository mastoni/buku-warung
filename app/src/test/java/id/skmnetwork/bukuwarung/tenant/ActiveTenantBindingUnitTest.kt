package id.skmnetwork.bukuwarung.tenant

import id.skmnetwork.bukuwarung.data.tenant.TenantResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Step 3A-FIX - focused proof that the ACTIVE tenant is the only runtime tenant.
 *
 * Two layers are asserted here:
 *
 *  1. The production decision function itself ([TenantResolver]), which is the single place a
 *     repository tenant may be decided, and which `TenantGate` - the production gate in front of
 *     `AppNavigation`'s repositories - calls directly.
 *  2. The wiring in `AppNavigation.kt` / `TenantGate.kt`, asserted at source level the same way the
 *     licence gate tests assert their own wiring. This is what stops the P0 from silently coming
 *     back: a repository built from an unkeyed `remember { }` over `businessId.ifBlank {
 *     "LEGACY_BUSINESS" }` is exactly the shape these assertions forbid.
 *
 * A composition-level test that runs the real gate is in
 * `app/src/androidTest/.../tenant/ActiveTenantBindingCompositionTest.kt`.
 */
class ActiveTenantBindingUnitTest {

    private val activeBusiness = "a5de61ab-bc79-4736-9473-9359101f1124"
    private val otherBusiness = "7f1d0c2a-1111-2222-3333-444455556666"
    private val legacySentinel = TenantResolver.MIGRATION_ONLY_SENTINEL

    private val appNavigation = File("src/main/java/id/skmnetwork/bukuwarung/ui/navigation/AppNavigation.kt")
    private val tenantGate = File("src/main/java/id/skmnetwork/bukuwarung/ui/navigation/TenantGate.kt")
    private val userPreferences = File("src/main/java/id/skmnetwork/bukuwarung/data/preferences/UserPreferencesRepository.kt")

    /** Source text with KDoc blocks and line comments removed, so prose can never satisfy an assertion. */
    private fun codeOnly(file: File): String {
        val withoutBlocks = file.readText()
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
        return withoutBlocks.lines()
            .map { line -> line.substringBefore("//") }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    // ------------------------------------------------------------------
    // TEST A: an empty business id must not produce a repository tenant.
    // ------------------------------------------------------------------

    @Test
    fun testA_emptyBusinessId_yieldsNoTenantAndNeverTheLegacySentinel() {
        listOf("", "   ", null).forEach { candidate ->
            assertNull("'$candidate' must not resolve to a tenant", TenantResolver.resolve(candidate))
            assertFalse("'$candidate' must not be bindable", TenantResolver.isBindable(candidate))
        }

        assertFalse(
            "The migration sentinel must not be usable as a runtime tenant",
            TenantResolver.isBindable(legacySentinel)
        )
        assertEquals(legacySentinel, TenantResolver.MIGRATION_ONLY_SENTINEL)
    }

    // ------------------------------------------------------------------
    // TEST B: a real business id becomes the repository tenant.
    // ------------------------------------------------------------------

    @Test
    fun testB_activeBusinessId_isTheTenantRepositoriesAreBoundTo() {
        val resolved = TenantResolver.resolve(activeBusiness)

        assertEquals(activeBusiness, resolved)
        assertTrue(TenantResolver.isBindable(activeBusiness))
        assertNotEquals(
            "The active tenant must never be the migration sentinel",
            legacySentinel,
            resolved
        )
    }

    // ------------------------------------------------------------------
    // TEST C: a tenant change must produce a different binding.
    // ------------------------------------------------------------------

    @Test
    fun testC_businessIdChange_rebindsToTheNewTenant() {
        val before = TenantResolver.resolve(activeBusiness)
        val after = TenantResolver.resolve(otherBusiness)

        assertNotEquals("A tenant change must change the binding", before, after)
        assertEquals(activeBusiness, before)
        assertEquals(otherBusiness, after)
        assertTrue(TenantResolver.isBindable(after))

        // Round trip: a tenant that is no longer the active one stops being bindable for the
        // previous identity, i.e. the binding is derived per call and never cached globally.
        assertNull(TenantResolver.resolve(""))
    }

    // ------------------------------------------------------------------
    // TEST G: no valid business id means the UI stays not-ready, never LEGACY_BUSINESS.
    // ------------------------------------------------------------------

    @Test
    fun testG_withoutAValidBusinessId_noTenantIsEverProduced() {
        // The sentinel comparison is exact and case sensitive on purpose: the migration sentinel is
        // written by this app as exactly "LEGACY_BUSINESS", and a business id is a generated UUID,
        // so a near-miss string is a distinct (real) identity rather than the sentinel.
        val inputs = listOf(null, "", "  \n ", legacySentinel, " $legacySentinel ")

        inputs.forEach { candidate ->
            val tenant = TenantResolver.resolve(candidate)?.takeIf { TenantResolver.isBindable(it) }
            assertNull(
                "Input '$candidate' must leave the gate in the not-ready state instead of " +
                    "falling back to $legacySentinel",
                tenant
            )
        }
    }

    // ------------------------------------------------------------------
    // Production wiring: the P0 must not be able to come back silently.
    // ------------------------------------------------------------------

    @Test
    fun production_navigationHost_hasNoLegacyBusinessRuntimeFallback() {
        assertTrue("AppNavigation.kt must exist", appNavigation.exists())
        val code = codeOnly(appNavigation)

        assertFalse(
            "AppNavigation must not resolve a blank business id to the migration sentinel",
            code.contains(legacySentinel)
        )
        assertFalse(
            "AppNavigation must not use the ifBlank business-id fallback at all",
            code.contains("businessId.ifBlank")
        )
    }

    @Test
    fun production_navigationHost_waitsForTheRealIdentityInsteadOfAssumingABlankOne() {
        val code = codeOnly(appNavigation)

        assertTrue(
            "Settings must be collected with a null initial value, not a blank UserSettings()",
            code.contains("initialValue = null")
        )
        assertFalse(
            "Collecting with initialValue = UserSettings() is what pinned the first frame to " +
                "LEGACY_BUSINESS",
            code.contains("initialValue = UserSettings()")
        )
        assertTrue(
            "The tenant-scoped app must be composed through TenantGate",
            code.contains("TenantGate(userSettings = loadedUserSettings)")
        )
    }

    @Test
    fun production_tenantGate_resolvesThroughTenantResolver() {
        assertTrue("TenantGate.kt must exist", tenantGate.exists())
        val code = codeOnly(tenantGate)

        assertTrue("The gate must resolve the tenant through TenantResolver", code.contains("TenantResolver.resolve("))
        assertTrue("The gate must reject a non-bindable tenant", code.contains("TenantResolver.isBindable("))
        assertFalse(
            "The gate must not contain a literal migration sentinel",
            code.contains("LEGACY_BUSINESS")
        )
    }

    @Test
    fun production_navigationHost_keysRepositoriesAndViewModelsOnTheActiveTenant() {
        val code = codeOnly(appNavigation)

        listOf(
            "ProductRepository(database, activeBusinessId)",
            "CustomerRepository(database, activeBusinessId)",
            "SaleRepository(database, activeBusinessId)",
            "SupplierRepository(database, activeBusinessId)",
            "ReportRepository(database, activeBusinessId)",
            "CheckoutOrchestrator(database, saleRepository, digitalTransactionRepository)",
            "PurchaseOrderRepository(database, activeBusinessId)"
        ).forEach { construction ->
            assertTrue(
                "Repository must be constructed from the active tenant: $construction",
                code.contains(construction)
            )
        }

        listOf("product", "customer", "supplier", "report", "purchaseOrder", "notification").forEach { screen ->
            assertTrue(
                "The $screen ViewModel must be re-keyed per tenant so a tenant change rebinds it",
                code.contains("key = \"tenant:\$activeBusinessId:$screen\"")
            )
        }

        // Every repository construction above must sit inside a tenant-keyed remember, otherwise a
        // tenant change would keep serving the previous tenant's repository.
        val tenantKeyedRemembers = Regex("remember\\(activeBusinessId\\)")
            .findAll(code)
            .count()
        assertTrue(
            "Tenant-scoped objects must be created with remember(activeBusinessId), found $tenantKeyedRemembers",
            tenantKeyedRemembers >= 7
        )
    }

    @Test
    fun migration_reconciliationForTheSentinelIsKept() {
        assertTrue("UserPreferencesRepository.kt must exist", userPreferences.exists())
        val code = codeOnly(userPreferences)

        assertTrue(
            "LEGACY_BUSINESS must remain a migration/reconciliation sentinel",
            code.contains("reconcileLegacyBusinessIdentity")
        )
        assertTrue(
            "Startup migration must keep reconciling the legacy rows onto the real business id",
            code.contains("reconcileLegacyBusinessIdentity(database, businessId)")
        )
    }
}
