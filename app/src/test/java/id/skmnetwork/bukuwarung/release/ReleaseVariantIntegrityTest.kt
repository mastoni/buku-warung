package id.skmnetwork.bukuwarung.release

import id.skmnetwork.bukuwarung.BuildConfig
import id.skmnetwork.bukuwarung.license.LicenseApiClient
import id.skmnetwork.bukuwarung.license.LicenseStatus
import id.skmnetwork.bukuwarung.license.ProductionLicenseProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate H.3 - release integrity / owner-test boundary.
 *
 * `android.testBuildType = "release"` means this suite is compiled into the RELEASE variant, so
 * every assertion below reads the *shipped* BuildConfig rather than a debug stand-in. That is the
 * whole point of the gate: before H.3 the unit tests only ever saw `ENABLE_OWNER_TEST = true`.
 *
 * The multi-variant half of the boundary (ownerTest must not use the production applicationId or
 * the production signing key) cannot be observed from inside a single compiled variant, so it is
 * asserted by the `verifyVariantSecurity` Gradle task, which reads the resolved variant
 * configuration. This class covers what is observable in-process, and the bypass behaviour.
 */
class ReleaseVariantIntegrityTest {

    // -----------------------------------------------------------------------
    // 1. Release BuildConfig must not carry the owner-test licence bypass.
    // -----------------------------------------------------------------------

    @Test
    fun releaseBuildConfigDoesNotEnableOwnerTestBypass() {
        assertFalse(
            "ENABLE_OWNER_TEST must be false in the release variant",
            BuildConfig.ENABLE_OWNER_TEST
        )
    }

    @Test
    fun releaseBuildConfigIsTheReleaseVariant() {
        assertEquals("release", BuildConfig.BUILD_TYPE)
        assertFalse("Release must not be debuggable", BuildConfig.DEBUG)
    }

    // -----------------------------------------------------------------------
    // 3. Production identity is unchanged.
    // -----------------------------------------------------------------------

    @Test
    fun releaseApplicationIdAndVersionAreUnchanged() {
        assertEquals("id.skmnetwork.bukuwarung", BuildConfig.APPLICATION_ID)
        assertEquals("0.2.1", BuildConfig.VERSION_NAME)
        assertEquals(5, BuildConfig.VERSION_CODE)
    }

    @Test
    fun releasePointsAtTheProductionHttpsLicenseServer() {
        assertTrue(
            "Production licence server must be HTTPS, was ${BuildConfig.LICENSE_SERVER_URL}",
            BuildConfig.LICENSE_SERVER_URL.startsWith("https://")
        )
    }

    /**
     * The release-only HTTPS guard. Proves the release BuildConfig cannot be pointed at a
     * plain-HTTP licence server, which is the property that used to be the only thing standing
     * between a misconfigured owner-test build and a working local licence server.
     */
    @Test
    fun releaseRefusesPlainHttpLicenseServerUrl() {
        val thrown = runCatching {
            LicenseApiClient(baseUrl = "http://localhost:3000")
        }.exceptionOrNull()

        assertTrue(
            "LicenseApiClient must reject a plain-HTTP URL in the release variant",
            thrown is IllegalStateException
        )
    }

    // -----------------------------------------------------------------------
    // 4. The owner-test bypass is inert in the release build, behaviourally.
    // -----------------------------------------------------------------------

    @Test
    fun productionLicenseProviderIgnoresOwnerTestEntitlement() = runBlocking {
        // enableOwnerTest = false models the release build: even the owner-test branch being
        // reachable in the code is irrelevant, because the flag the branch is gated on is off.
        val provider = ProductionLicenseProvider(
            userPreferencesRepository = null,
            enableOwnerTest = false
        )
        assertEquals(LicenseStatus.UNLICENSED, provider.checkLicense())
    }

    @Test
    fun ownerTestBranchIsUnreachableWithoutAFlaggedProvider() = runBlocking {
        // Even with the bypass flag forced on, a provider with no persisted owner-test activation
        // must not report an active licence. Nothing is hardcoded to ACTIVE.
        val provider = ProductionLicenseProvider(
            userPreferencesRepository = null,
            enableOwnerTest = true
        )
        assertEquals(LicenseStatus.UNLICENSED, provider.checkLicense())
    }

    @Test
    fun releaseNeverHardcodesAnActiveLicence() = runBlocking {
        val defaultProvider = ProductionLicenseProvider()
        assertEquals(
            "A freshly constructed provider must never report ACTIVE",
            LicenseStatus.UNLICENSED,
            defaultProvider.checkLicense()
        )
        assertNotEquals(LicenseStatus.ACTIVE, defaultProvider.checkLicense())
    }
}
