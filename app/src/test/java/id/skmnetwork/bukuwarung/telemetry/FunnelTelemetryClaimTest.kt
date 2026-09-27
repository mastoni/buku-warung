package id.skmnetwork.bukuwarung.telemetry

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import id.skmnetwork.bukuwarung.preferences.InMemoryPreferencesDataStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * FUNNEL-FIX regression tests.
 *
 * These assert the invariant the app previously violated: a lifecycle event entitlement is
 * claimed exactly once per installation, no matter how many times the relevant composable is
 * entered, recomposed, or recreated, or how many times the process restarts.
 *
 * The audit observed 22 LICENSE_GATE_VIEWED rows and 17 APP_FIRST_OPEN rows from a single
 * device inside 24 minutes. A correct claim guard makes the second burst unrepresentable.
 */
class FunnelTelemetryClaimTest {

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repo: UserPreferencesRepository

    @Before
    fun setup() {
        dataStore = InMemoryPreferencesDataStore()
        repo = UserPreferencesRepository(context = null, dataStore = dataStore)
    }

    // ---------------------------------------------------------------- APP_FIRST_OPEN

    @Test
    fun appFirstOpen_isClaimedOnFirstLaunch() = runBlocking {
        assertTrue("first launch must win the APP_FIRST_OPEN claim", repo.claimAppFirstOpenRecorded())
    }

    @Test
    fun appFirstOpen_recompositionDoesNotProduceAnotherEvent() = runBlocking {
        assertTrue(repo.claimAppFirstOpenRecorded())

        // Recomposition re-enters the same composable many times per second in a real session.
        repeat(50) {
            assertFalse("recomposition must not re-claim APP_FIRST_OPEN", repo.claimAppFirstOpenRecorded())
        }
    }

    @Test
    fun appFirstOpen_activityRecreationDoesNotProduceAnotherEvent() = runBlocking {
        assertTrue(repo.claimAppFirstOpenRecorded())

        // Activity recreation relaunches the process and re-reads state from scratch.
        repeat(20) {
            val recreated = UserPreferencesRepository(context = null, dataStore = dataStore)
            assertFalse("activity recreation must not re-claim APP_FIRST_OPEN", recreated.claimAppFirstOpenRecorded())
        }
    }

    @Test
    fun appFirstOpen_subsequentAppStartsProduceNoFurtherEvents() = runBlocking {
        assertTrue(repo.claimAppFirstOpenRecorded())

        // Each of these is a separate simulated app start against the same persisted store.
        repeat(10) {
            val nextLaunch = UserPreferencesRepository(context = null, dataStore = dataStore)
            assertFalse("later app start must not re-claim APP_FIRST_OPEN", nextLaunch.claimAppFirstOpenRecorded())
        }
    }

    @Test
    fun appFirstOpen_concurrentClaimsElectExactlyOneWinner() = runBlocking {
        // Recomposition and Activity recreation can race inside one process. The mutex must
        // ensure a single winner rather than letting both observe "not yet claimed".
        val results = (1..25).map { async { repo.claimAppFirstOpenRecorded() } }.awaitAll()

        assertEquals("exactly one caller may win the claim", 1, results.count { it })
    }

    // ----------------------------------------------------------- LICENSE_GATE_VIEWED

    @Test
    fun licenseGateView_isClaimedOnFirstMeaningfulExposure() = runBlocking {
        assertTrue("first gate exposure must win the claim", repo.claimLicenseGateViewRecorded())
    }

    @Test
    fun licenseGateView_recompositionDoesNotProduceAnotherEvent() = runBlocking {
        assertTrue(repo.claimLicenseGateViewRecorded())
        repeat(50) {
            assertFalse("recomposition must not re-claim LICENSE_GATE_VIEWED", repo.claimLicenseGateViewRecorded())
        }
    }

    @Test
    fun licenseGateView_returningToGateInSameInstallationDoesNotProduceAnotherEvent() = runBlocking {
        assertTrue(repo.claimLicenseGateViewRecorded())

        // Navigating away and back to the gate is the exact scenario that produced 22 rows.
        repeat(25) {
            assertFalse("re-entering the gate must not re-claim", repo.claimLicenseGateViewRecorded())
        }
    }

    @Test
    fun licenseGateView_activityRecreationDoesNotProduceAnotherEvent() = runBlocking {
        assertTrue(repo.claimLicenseGateViewRecorded())
        repeat(20) {
            val recreated = UserPreferencesRepository(context = null, dataStore = dataStore)
            assertFalse("activity recreation must not re-claim LICENSE_GATE_VIEWED", recreated.claimLicenseGateViewRecorded())
        }
    }

    @Test
    fun licenseGateView_concurrentClaimsElectExactlyOneWinner() = runBlocking {
        val results = (1..25).map { async { repo.claimLicenseGateViewRecorded() } }.awaitAll()
        assertEquals("exactly one caller may win the claim", 1, results.count { it })
    }

    @Test
    fun licenseGateView_isIndependentOfAppFirstOpen() = runBlocking {
        // The two entitlements are separate: claiming one must not consume the other.
        assertTrue(repo.claimAppFirstOpenRecorded())
        assertTrue("LICENSE_GATE_VIEWED must remain claimable", repo.claimLicenseGateViewRecorded())
    }

    // ------------------------------------------------------------- INSTALLATION_ID

    @Test
    fun installationId_isStableAcrossRepeatedReads() = runBlocking {
        val first = repo.getOrCreateInstallationId()
        val second = repo.getOrCreateInstallationId()
        val third = UserPreferencesRepository(context = null, dataStore = dataStore).getOrCreateInstallationId()

        assertEquals("installation id must be stable within an installation", first, second)
        assertEquals("installation id must survive a new repository instance", first, third)
    }

    @Test
    fun installationId_isANonEmptyUuid() = runBlocking {
        val id = repo.getOrCreateInstallationId()
        assertTrue("installation id must not be blank", id.isNotBlank())
        assertTrue(
            "installation id must be a UUID, got: $id",
            Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$").matches(id)
        )
    }

    @Test
    fun installationId_isDifferentForANewInstallation() = runBlocking {
        val existing = repo.getOrCreateInstallationId()

        // A new installation means cleared app data, which is a fresh DataStore.
        val freshRepo = UserPreferencesRepository(
            context = null,
            dataStore = InMemoryPreferencesDataStore()
        )
        val fresh = freshRepo.getOrCreateInstallationId()

        assertNotEquals("a new installation must get a new identifier", existing, fresh)
    }

    @Test
    fun installationId_concurrentCreationYieldsOneValue() = runBlocking {
        val results = (1..25).map { async { repo.getOrCreateInstallationId() } }.awaitAll()
        assertEquals("concurrent creation must converge on one id", 1, results.distinct().size)
    }

    @Test
    fun installationId_isDistinctFromLicenseDeviceBinding() = runBlocking {
        // Analytics identity must not collide with license device-binding state.
        val installationId = repo.getOrCreateInstallationId()
        val deviceId = repo.getOrCreateDeviceId()
        assertNotEquals("installation id must not reuse the license device id", installationId, deviceId)
    }

    // ------------------------------------------------------------------ PERSISTENCE

    @Test
    fun claims_surviveRepositoryRecreation() = runBlocking {
        assertTrue(repo.claimAppFirstOpenRecorded())
        assertTrue(repo.claimLicenseGateViewRecorded())
        val installationId = repo.getOrCreateInstallationId()

        val reloaded = UserPreferencesRepository(context = null, dataStore = dataStore)

        assertFalse("APP_FIRST_OPEN claim must persist", reloaded.claimAppFirstOpenRecorded())
        assertFalse("LICENSE_GATE_VIEWED claim must persist", reloaded.claimLicenseGateViewRecorded())
        assertEquals("installation id must persist", installationId, reloaded.getOrCreateInstallationId())
    }

    @Test
    fun claims_areReflectedInUserSettingsForFirstLaunchFlag() = runBlocking {
        assertFalse("flag starts unset", repo.userSettings.first().isFirstLaunchRecorded)
        repo.claimAppFirstOpenRecorded()
        assertTrue("flag must be observable as recorded", repo.userSettings.first().isFirstLaunchRecorded)
    }
}
