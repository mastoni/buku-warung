package id.skmnetwork.bukuwarung.tenant

import id.skmnetwork.bukuwarung.data.tenant.TenantResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Step 3A - tenant binding regression suite (JVM).
 *
 * Covers the resolution rule and the CASE 1 / 6 / 7 requirements. The data-visibility cases
 * (CASE 2-5) need a real Room database and are covered by the instrumentation suite
 * `ColdStartTenantVisibilityTest`.
 */
class TenantBindingUnitTest {

    private val realTenant = "a5de61ab-bc79-4736-9473-9359101f1124"

    // ---------------- CASE 1: blank first, real id later ----------------

    @Test
    fun case1_blankBusinessIdResolvesToNullSoNoRepositoryIsBound() {
        // This is the exact first-frame state that caused the P0: userSettings.businessId is "".
        assertNull(TenantResolver.resolve(""))
        assertFalse("A blank id must not be bindable", TenantResolver.isBindable(""))
    }

    @Test
    fun case1_realBusinessIdResolvesToItself() {
        assertEquals(realTenant, TenantResolver.resolve(realTenant))
        assertTrue(TenantResolver.isBindable(realTenant))
    }

    @Test
    fun case1_theTenantChosenAfterTheBlankFrameIsTheRealOne() {
        // Frame 1 -> frame 2, exactly what happens on a cold start.
        val firstFrame = TenantResolver.resolve("")
        val secondFrame = TenantResolver.resolve(realTenant)

        assertNull("No repository may be bound on the first frame", firstFrame)
        assertEquals("The real tenant is used once available", realTenant, secondFrame)
        assertNotEquals(firstFrame, secondFrame)
    }

    // ---------------- CASE 6: the tenant changes ----------------

    @Test
    fun case6_aChangedTenantProducesADifferentBinding() {
        val other = "7f1d0c2a-1111-2222-3333-444455556666"
        assertNotEquals(TenantResolver.resolve(realTenant), TenantResolver.resolve(other))
        assertTrue(TenantResolver.isBindable(other))
    }

    // ---------------- CASE 7: never silently fall back ----------------

    @Test
    fun case7_theMigrationSentinelIsNeverBindable() {
        // LEGACY_BUSINESS survives only as a migration/reconciliation marker.
        assertTrue(TenantResolver.isMigrationOnlySentinel(TenantResolver.MIGRATION_ONLY_SENTINEL))
        assertFalse(
            "LEGACY_BUSINESS must never be a runtime tenant",
            TenantResolver.isBindable(TenantResolver.MIGRATION_ONLY_SENTINEL)
        )
    }

    @Test
    fun case7_blankAndNullAndWhitespaceAreAllUnbindable() {
        listOf(null, "", "   ", "\n\t").forEach { candidate ->
            assertNull("'$candidate' must not resolve to a tenant", TenantResolver.resolve(candidate))
            assertFalse("'$candidate' must not be bindable", TenantResolver.isBindable(candidate))
        }
    }

    @Test
    fun case7_theSentinelComparisonIsExactAfterTrimming() {
        // Surrounding whitespace is removed first, so a padded sentinel is still recognised and still
        // rejected. Case is NOT folded: a business id is a UUID, and guessing that some other string
        // "meant" the sentinel would be exactly the kind of silent rewriting this fix exists to stop.
        assertFalse(TenantResolver.isBindable("  LEGACY_BUSINESS  "))
        assertTrue(TenantResolver.isMigrationOnlySentinel("  LEGACY_BUSINESS  "))

        // A different string is a different tenant, and is not treated as the sentinel.
        assertFalse(TenantResolver.isMigrationOnlySentinel("legacy_business"))
        assertTrue(TenantResolver.isBindable("legacy_business"))
    }

    // ---------------- normalisation ----------------

    @Test
    fun resolve_trimsWhitespaceAroundARealTenant() {
        assertEquals(realTenant, TenantResolver.resolve("  $realTenant \n"))
    }

    @Test
    fun resolve_doesNotRewriteARealTenant() {
        val weird = "Tenant-With_Mixed.Case-42"
        assertEquals(weird, TenantResolver.resolve(weird))
    }
}
