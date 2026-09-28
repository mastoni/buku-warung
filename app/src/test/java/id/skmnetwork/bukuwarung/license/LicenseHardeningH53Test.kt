package id.skmnetwork.bukuwarung.license

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException

/**
 * Gate H.5.3 - lifecycle hardening regression suite.
 *
 * Covers exactly three findings from the H.5.2 audit:
 *  - H5.2-P1-1  local licence storage failure escapes and can crash startup / wedge Settings
 *  - H5.2-P2-2  activation bypasses the validation sequence ordering
 *  - H5.2-P2-3  ProcessLifecycleOwner acquisition is unguarded
 *
 * All JVM unit tests using an injected [LicenseHttpTransport] and a fault-injecting in-memory
 * DataStore. No real device, no instrumentation, no real server, no real AndroidKeyStore.
 */
class LicenseHardeningH53Test {

    private val licenseCode = "BW-H53-TEST-0001"
    private val ownerEmail = "owner@h53.test"

    private var now: Long = 1_700_000_000_000L
    private val policy = LicensePolicy(freshTtlMillis = 1_000L, graceWindowMillis = 5_000L)

    private lateinit var transport: ScriptedTransport
    private lateinit var apiClient: LicenseApiClient
    private lateinit var store: FaultyPreferencesDataStore
    private lateinit var repo: UserPreferencesRepository

    // ------------------------------------------------------------------ fixtures

    /**
     * In-memory DataStore that can fail reads and writes with an arbitrary Throwable, so the
     * guarded paths in LicenseManager can be exercised without an Android runtime.
     */
    private class FaultyPreferencesDataStore : DataStore<Preferences> {
        @Volatile var readFailure: Throwable? = null
        @Volatile var writeFailure: Throwable? = null
        private val state = MutableStateFlow(emptyPreferences())

        override val data: Flow<Preferences>
            get() {
                val failure = readFailure
                return if (failure != null) flow { throw failure } else state
            }

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences
        ): Preferences {
            writeFailure?.let { throw it }
            val next = transform(state.value)
            state.value = next
            return next
        }
    }

    private class ScriptedTransport : LicenseHttpTransport {
        val calls = AtomicInteger(0)
        val validateCalls = AtomicInteger(0)
        val activateCalls = AtomicInteger(0)

        @Volatile var validateStatus: Int = 200
        @Volatile var validateBody: String = """{"success":true,"status":"VALID"}"""
        @Volatile var activateStatus: Int = 200
        @Volatile var activateBody: String = """{"success":true,"status":"ACTIVE"}"""
        @Volatile var validateGate: CompletableDeferred<Unit>? = null
        @Volatile var activateGate: CompletableDeferred<Unit>? = null

        override suspend fun post(
            url: String,
            jsonPayload: String,
            connectTimeoutMs: Int,
            readTimeoutMs: Int
        ): LicenseHttpResponse {
            calls.incrementAndGet()
            when {
                url.contains("/validate") -> {
                    validateCalls.incrementAndGet()
                    validateGate?.await()
                    return LicenseHttpResponse(validateStatus, validateBody)
                }
                url.contains("/activate") -> {
                    activateCalls.incrementAndGet()
                    activateGate?.await()
                    return LicenseHttpResponse(activateStatus, activateBody)
                }
            }
            return LicenseHttpResponse(200, "{}")
        }
    }

    private inner class TestManager(
        private val storedCode: String = licenseCode,
        val cleared: AtomicReference<Boolean> = AtomicReference(false)
    ) : LicenseManager(
        userPreferencesRepository = repo,
        apiClient = apiClient,
        context = null,
        scope = CoroutineScope(Dispatchers.IO),
        policy = policy,
        clock = { now },
        minValidationIntervalMillis = 0L
    ) {
        override fun getStoredLicenseCode(): String = storedCode
        override fun clearStoredLicenseCode() {
            cleared.set(true)
        }
    }

    private fun body(status: String) =
        """{"success":${status == "VALID"},"status":"$status"}"""

    private suspend fun seedActive(lastValidatedAt: Long = now) {
        repo.saveLicenseEntitlement(
            status = "ACTIVE",
            ownerEmail = ownerEmail,
            activatedAt = now,
            lastValidatedAt = lastValidatedAt
        )
    }

    @Before
    fun setUp() {
        transport = ScriptedTransport()
        apiClient = LicenseApiClient(baseUrl = "https://license.skmnetwork.com", transport = transport)
        store = FaultyPreferencesDataStore()
        repo = UserPreferencesRepository(context = null, dataStore = store)
    }

    // =================================================================
    // H5.2-P1-1 - local storage failure must never crash or bypass
    // =================================================================

    @Test
    fun h53_01_startupWithUnreadableLocalStoreDoesNotCrashAndDoesNotGrantAccess() = runBlocking {
        store.readFailure = IOException("corrupted preferences")
        val manager = TestManager()

        // The init launch runs on a SupervisorJob scope with no CoroutineExceptionHandler. If the
        // failure escaped, the process would be terminated instead of publishing a state.
        delay(300)
        assertEquals(LicensePhase.TRANSIENT_ERROR, manager.licenseState.value.phase)
        assertEquals(
            TransientReason.LOCAL_STORAGE_UNAVAILABLE,
            manager.licenseState.value.transientReason
        )
        assertFalse(
            "A local read failure must NEVER grant access",
            manager.licenseState.value.grantsAccess
        )

        // And the explicit call site must return normally rather than throwing.
        manager.refreshLicense()
        assertFalse(manager.licenseState.value.grantsAccess)
        assertEquals(LicensePhase.TRANSIENT_ERROR, manager.licenseState.value.phase)
    }

    @Test
    fun h53_02_refreshRecoversOnceLocalStorageIsReadableAgain() = runBlocking {
        val manager = TestManager()

        store.readFailure = IOException("corrupted preferences")
        manager.refreshLicense()
        assertFalse(manager.licenseState.value.grantsAccess)

        // Storage comes back, and the merchant must be able to use the app again.
        store.readFailure = null
        seedActive(lastValidatedAt = now)
        manager.refreshLicense()

        assertEquals(LicensePhase.FRESH_ACTIVE, manager.licenseState.value.phase)
        assertTrue(manager.licenseState.value.grantsAccess)
    }

    @Test
    fun h53_03_validationWithUnreadableLocalStoreIsTransientAndIssuesNoRequest() = runBlocking {
        seedActive(lastValidatedAt = 0L)
        val manager = TestManager()

        store.readFailure = IOException("corrupted preferences")
        val result = manager.validateOnline()

        assertTrue("Expected a transient outcome, got $result", result is ValidationResult.Transient)
        assertEquals(
            TransientReason.LOCAL_STORAGE_UNAVAILABLE,
            (result as ValidationResult.Transient).reason
        )
        assertEquals("No HTTP request may be issued without readable local state", 0, transport.calls.get())
        assertFalse("A local read failure must not delete the credential", manager.cleared.get())
        assertFalse(manager.licenseState.value.grantsAccess)
    }

    @Test
    fun h53_04_validationStillWorksAfterLocalStorageRecovers() = runBlocking {
        seedActive(lastValidatedAt = 0L)
        val manager = TestManager()

        store.readFailure = IOException("corrupted preferences")
        manager.validateOnline()

        store.readFailure = null
        val result = manager.validateOnline()

        assertTrue("The lifecycle coroutine must stay usable, got $result", result is ValidationResult.Valid)
        assertEquals(1, transport.calls.get())
        assertTrue(repo.getLicenseEntitlement().isEntitled)
        assertEquals(LicensePhase.FRESH_ACTIVE, manager.licenseState.value.phase)
    }

    @Test
    fun h53_05_coldStartWithUnreadableLocalStoreDoesNotThrowAndDoesNotValidate() = runBlocking {
        store.readFailure = IOException("corrupted preferences")
        val manager = TestManager()

        val result = manager.triggerColdStartValidation()

        assertNull("Cold start must not attempt a validation it cannot prepare", result)
        assertEquals(0, transport.calls.get())
        assertFalse(manager.licenseState.value.grantsAccess)
    }

    @Test
    fun h53_06_foregroundWithUnreadableLocalStoreDoesNotThrowAndDoesNotValidate() = runBlocking {
        store.readFailure = IOException("corrupted preferences")
        val manager = TestManager()

        val result = manager.triggerForegroundValidation()

        assertNull(result)
        assertEquals(0, transport.calls.get())
        assertFalse(manager.licenseState.value.grantsAccess)
    }

    @Test
    fun h53_07_activationDoesNotThrowWhenLocalStorageIsUnreadable() = runBlocking {
        val manager = TestManager()
        store.readFailure = IOException("corrupted preferences")

        val result = manager.activateLicense(licenseCode, ownerEmail)

        assertTrue("Expected a transient outcome, got $result", result is ActivationResult.Transient)
        assertEquals(
            TransientReason.LOCAL_STORAGE_UNAVAILABLE,
            (result as ActivationResult.Transient).reason
        )
        assertFalse(manager.licenseState.value.grantsAccess)
    }

    @Test
    fun h53_08_cancellationExceptionFromLocalStorageStillPropagates() = runBlocking {
        val manager = TestManager()
        store.readFailure = CancellationException("scope cancelled while reading")

        var caught: Throwable? = null
        try {
            manager.refreshLicense()
        } catch (e: Throwable) {
            caught = e
        }

        assertTrue(
            "CancellationException must never be converted into a stored failure, got $caught",
            caught is CancellationException
        )
        assertTrue("The calling coroutine must stay active", coroutineContext.isActive)
    }

    @Test
    fun h53_09_cancellationExceptionFromTransportStillPropagates() = runBlocking {
        seedActive(lastValidatedAt = now)
        val manager = TestManager()
        val gate = CompletableDeferred<Unit>()
        transport.validateGate = gate

        val waiting = async(Dispatchers.Default) { manager.validateOnline() }
        withTimeout(10_000) { while (transport.validateCalls.get() == 0) delay(5) }
        waiting.cancel()
        gate.complete(Unit)

        var observed: Throwable? = null
        try {
            waiting.await()
        } catch (e: Throwable) {
            observed = e
        }
        assertTrue(
            "A cancelled caller must see CancellationException, got $observed",
            observed is CancellationException
        )
    }

    @Test
    fun h53_10_aFailedLocalWriteDoesNotAdvanceTheOrderingWatermark() = runBlocking {
        seedActive(lastValidatedAt = now)
        val manager = TestManager()
        val stale = manager.nextValidationSequence()
        val newer = stale + 1

        // The newer attempt cannot be persisted, so it must not be able to suppress the older one.
        store.writeFailure = IOException("disk full")
        assertTrue(manager.commitValidationOutcome(newer, ValidationResult.Valid()))
        store.writeFailure = null

        assertTrue(manager.commitValidationOutcome(stale, ValidationResult.Valid()))
        assertTrue(repo.getLicenseEntitlement().isEntitled)
        assertEquals(LicensePhase.FRESH_ACTIVE, manager.licenseState.value.phase)
    }

    @Test
    fun h53_11_entitlementInfoNeverThrows() = runBlocking {
        val manager = TestManager()
        store.readFailure = IOException("corrupted preferences")

        val info = manager.getEntitlementInfo()

        assertNotNull(info)
        assertFalse(info.isEntitled)
    }

    @Test
    fun h53_12_settingsValidationAlwaysClearsItsLoadingFlag() {
        // A Composable is not unit-testable here, so the contract is asserted at the source level:
        // the flag must be released in a finally, and cancellation must be rethrown.
        val source = File("src/main/java/id/skmnetwork/bukuwarung/ui/settings/SettingsScreen.kt").readText()
        val anchor = source.indexOf("isCheckingLicense = true")
        assertTrue("The validation click handler must set its loading flag", anchor > 0)
        val block = source.substring(anchor, (anchor + 3000).coerceAtMost(source.length))

        assertTrue("The validation click handler must have a finally", block.contains("finally {"))
        assertTrue(
            "The loading flag must be released in the finally",
            block.substringAfter("finally {").contains("isCheckingLicense = false")
        )
        assertTrue(
            "CancellationException must be rethrown, not swallowed",
            block.contains("catch (e: CancellationException)")
        )
    }

    // =================================================================
    // H5.2-P2-2 - activation participates in the ordering invariant
    // =================================================================

    @Test
    fun h53_20_activationIsNotOverwrittenByAnOlderDeviceMismatchResponse() = runBlocking {
        seedActive(lastValidatedAt = 0L)
        val manager = TestManager()
        transport.validateBody = body("DEVICE_MISMATCH")

        val gate = CompletableDeferred<Unit>()
        transport.validateGate = gate

        val pending = async(Dispatchers.Default) { manager.validateOnline() }
        withTimeout(10_000) { while (transport.validateCalls.get() == 0) delay(5) }

        val activation = manager.activateLicense(licenseCode, ownerEmail)
        assertTrue("Activation must complete without waiting for the in-flight validation",
            activation is ActivationResult.Active)

        gate.complete(Unit)
        assertTrue(pending.await() is ValidationResult.DeviceMismatch)

        assertTrue(
            "An older definitive response must not undo the activation",
            repo.getLicenseEntitlement().isEntitled
        )
        assertFalse(manager.cleared.get())
        // The activation is the newest fact: access is granted, but never as FRESH, because
        // activation is not a validation.
        assertEquals(LicensePhase.STALE_ACTIVE, manager.licenseState.value.phase)
        assertTrue(manager.licenseState.value.grantsAccess)
    }

    @Test
    fun h53_21_activationIsNotOverwrittenByAnOlderRevokedResponse() = runBlocking {
        seedActive(lastValidatedAt = 0L)
        val manager = TestManager()
        transport.validateBody = body("REVOKED")

        val gate = CompletableDeferred<Unit>()
        transport.validateGate = gate

        val pending = async(Dispatchers.Default) { manager.validateOnline() }
        withTimeout(10_000) { while (transport.validateCalls.get() == 0) delay(5) }

        assertTrue(manager.activateLicense(licenseCode, ownerEmail) is ActivationResult.Active)

        gate.complete(Unit)
        assertTrue(pending.await() is ValidationResult.Revoked)

        assertTrue(
            "An older definitive response must not revoke a freshly activated licence",
            repo.getLicenseEntitlement().isEntitled
        )
        assertFalse(manager.cleared.get())
    }

    @Test
    fun h53_22_activationIsNotOverwrittenByAnOlderValidResponse() = runBlocking {
        seedActive(lastValidatedAt = 0L)
        val manager = TestManager()

        val gate = CompletableDeferred<Unit>()
        transport.validateGate = gate

        val pending = async(Dispatchers.Default) { manager.validateOnline() }
        withTimeout(10_000) { while (transport.validateCalls.get() == 0) delay(5) }

        assertTrue(manager.activateLicense(licenseCode, ownerEmail) is ActivationResult.Active)

        gate.complete(Unit)
        assertTrue(pending.await() is ValidationResult.Valid)

        // The older VALID must not have been applied on top of the activation, which records
        // lastValidatedAt = 0 by contract.
        assertEquals(0L, repo.getLicenseEntitlement().lastValidatedAt)
    }

    @Test
    fun h53_23_aNewerDefinitiveValidationStillOverridesTheActivation() = runBlocking {
        // The merchant is at the gate with a lapsed-but-still-stored entitlement, which is exactly
        // the state in which a re-activation and a validation can be in flight together.
        seedActive(lastValidatedAt = 0L)
        val manager = TestManager()
        transport.validateBody = body("DEVICE_MISMATCH")

        val activateGate = CompletableDeferred<Unit>()
        transport.activateGate = activateGate

        val pendingActivation = async { manager.activateLicense(licenseCode, ownerEmail) }
        withTimeout(10_000) { while (transport.activateCalls.get() == 0) delay(5) }

        // A validation issued AFTER the activation is the newer verdict and must win.
        assertTrue(manager.validateOnline() is ValidationResult.DeviceMismatch)

        activateGate.complete(Unit)
        val activation = pendingActivation.await()

        assertTrue("A superseded activation must be reported, not silently applied", activation is ActivationResult.Transient)
        assertEquals(
            TransientReason.ACTIVATION_SUPERSEDED,
            (activation as ActivationResult.Transient).reason
        )
        assertEquals(LicensePhase.BLOCKED, manager.licenseState.value.phase)
        assertEquals(LicenseBlockReason.DEVICE_MISMATCH, manager.licenseState.value.blockReason)
        assertFalse(repo.getLicenseEntitlement().isEntitled)
    }

    @Test
    fun h53_24_aTransientValidationDoesNotSuppressALegitimateActivation() = runBlocking {
        seedActive(lastValidatedAt = 0L)
        val manager = TestManager()
        transport.validateBody = "<html>captive portal</html>"

        val activateGate = CompletableDeferred<Unit>()
        transport.activateGate = activateGate

        val pendingActivation = async { manager.activateLicense(licenseCode, ownerEmail) }
        withTimeout(10_000) { while (transport.activateCalls.get() == 0) delay(5) }

        // A transient verdict writes nothing, so it must not advance the ordering watermark.
        val validation = manager.validateOnline()
        assertTrue(validation is ValidationResult.Transient)

        activateGate.complete(Unit)
        val activation = pendingActivation.await()

        assertTrue(
            "A transient attempt must not discard a real activation, got $activation",
            activation is ActivationResult.Active
        )
        assertTrue(repo.getLicenseEntitlement().isEntitled)
    }

    @Test
    fun h53_25_activationAndValidationDoNotDeadlockAndIssueOneRequestEach() = runBlocking {
        seedActive(lastValidatedAt = 0L)
        val manager = TestManager()

        val gate = CompletableDeferred<Unit>()
        transport.validateGate = gate

        val pending = async(Dispatchers.Default) { manager.validateOnline() }
        withTimeout(10_000) { while (transport.validateCalls.get() == 0) delay(5) }

        val activation = withTimeout(10_000) { manager.activateLicense(licenseCode, ownerEmail) }
        assertTrue(activation is ActivationResult.Active)

        gate.complete(Unit)
        assertNotNull(pending.await())

        assertEquals("Exactly one validate request", 1, transport.validateCalls.get())
        assertEquals("Exactly one activate request", 1, transport.activateCalls.get())
    }

    // =================================================================
    // H5.2-P2-3 - defensive ProcessLifecycleOwner acquisition
    // =================================================================

    @Test
    fun h53_30_processLifecycleObserverAcquisitionIsGuarded() {
        // ProcessLifecycleOwner.get() needs a real Android runtime, so the contract is asserted at
        // the source level: the acquisition is guarded, the cold start stays outside that block,
        // the dependency is retained, and the global AndroidX Startup configuration is untouched.
        val source = File("src/main/java/id/skmnetwork/bukuwarung/ui/navigation/AppNavigation.kt").readText()

        val coldStart = source.indexOf("triggerColdStartValidation()")
        val observerBlock = source.indexOf("DisposableEffect(licenseManager)")
        assertTrue("Cold-start validation must be wired", coldStart > 0)
        assertTrue("The observer must be wired", observerBlock > 0)
        assertTrue(
            "Cold-start validation must remain independent of the lifecycle observer",
            coldStart < observerBlock
        )

        val block = source.substring(observerBlock, (observerBlock + 1600).coerceAtMost(source.length))
        assertTrue("The acquisition must be guarded", block.contains("try {"))
        assertTrue(
            "A failure to acquire the process lifecycle must be swallowed",
            block.contains("catch (_: Exception)")
        )
        assertTrue("Cleanup must still remove the observer", block.contains("removeObserver(observerRef)"))
        assertTrue("Cleanup must be guarded too", block.indexOf("try {", block.indexOf("onDispose")) > 0)

        val gradle = File("build.gradle.kts").readText()
        assertTrue(
            "The lifecycle-process dependency must be retained",
            gradle.contains("androidx.lifecycle:lifecycle-process")
        )

        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertFalse(
            "The global AndroidX Startup configuration must not be modified",
            manifest.contains("androidx.startup") || manifest.contains("InitializationProvider")
        )
    }
}
