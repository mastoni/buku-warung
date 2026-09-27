package id.skmnetwork.bukuwarung.license

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

private class RecoveryTestDataStore(
    initialValue: Preferences
) : DataStore<Preferences> {
    private val state = MutableStateFlow(initialValue)
    override val data = state
    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences
    ): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

/**
 * Unit tests for the customer-initiated device recovery flow.
 *
 * Covers POST /v1/license/recover end to end through the injected LicenseHttpTransport, and
 * asserts the two security-critical properties of LicenseManager.requestDeviceRecovery:
 * it submits the device binding that already exists in DataStore, and it never generates a
 * second identifier for the same installation.
 */
class LicenseRecoveryUnitTest {

    private val testLicenseCode = "BW-TEST-RECOVER-1234"
    private val testOwnerEmail = "owner.recover@test.com"
    private val existingDeviceBinding = "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee"

    private val httpCallCount = AtomicInteger(0)
    private val httpCallUrls = AtomicReference<List<String>>(emptyList())
    private val httpCallPayloads = AtomicReference<List<String>>(emptyList())

    private var simulateNetworkFailure = false
    private var forceStatusCode = 0
    private var forceBody: String? = null

    private lateinit var mockTransport: LicenseHttpTransport
    private lateinit var apiClient: LicenseApiClient
    private lateinit var testRepo: UserPreferencesRepository

    @Before
    fun setUp() {
        httpCallCount.set(0)
        httpCallUrls.set(emptyList())
        httpCallPayloads.set(emptyList())
        simulateNetworkFailure = false
        forceStatusCode = 0
        forceBody = null

        mockTransport = object : LicenseHttpTransport {
            override suspend fun post(
                url: String,
                jsonPayload: String,
                connectTimeoutMs: Int,
                readTimeoutMs: Int
            ): LicenseHttpResponse {
                httpCallCount.incrementAndGet()
                httpCallUrls.set(httpCallUrls.get() + url)
                httpCallPayloads.set(httpCallPayloads.get() + jsonPayload)

                if (simulateNetworkFailure) {
                    throw IOException("Simulated network connection failure")
                }
                if (forceStatusCode != 0) {
                    return LicenseHttpResponse(forceStatusCode, forceBody ?: "")
                }
                if (url.endsWith("/v1/license/recover")) {
                    return LicenseHttpResponse(
                        200,
                        JSONObject().apply {
                            put("success", true)
                            put("status", "RECOVERY_PENDING")
                        }.toString()
                    )
                }
                return LicenseHttpResponse(404, "{\"error\": \"Not Found\"}")
            }
        }

        apiClient = LicenseApiClient(
            baseUrl = "https://license.skmnetwork.com",
            transport = mockTransport
        )

        // Prime the store with a device id so the "no second UUID" guarantee is observable.
        val dataStore = RecoveryTestDataStore(
            emptyPreferences().toMutablePreferences().apply {
                set(stringPreferencesKey("device_id"), existingDeviceBinding)
            }
        )
        testRepo = UserPreferencesRepository(context = null, dataStore = dataStore)
    }

    private fun createTestManager() = object : LicenseManager(
        userPreferencesRepository = testRepo,
        provider = ProductionLicenseProvider(testRepo),
        apiClient = apiClient,
        context = null
    ) {
        override fun getStoredLicenseCode(): String = testLicenseCode
    }

    private fun lastPayload(): JSONObject = JSONObject(httpCallPayloads.get().last())

    private fun respondWithError(code: String) {
        forceStatusCode = 400
        forceBody = JSONObject().apply {
            put("success", false)
            put("error", JSONObject().put("code", code).put("message", "internal detail"))
        }.toString()
    }

    // ------------------------------------------------------------- 1. request payload
    @Test
    fun `request payload carries licenseCode ownerEmail and newDeviceBinding`() = runBlocking {
        val result = createTestManager().requestDeviceRecovery(testLicenseCode, testOwnerEmail)

        assertTrue(result is RecoveryResult.RecoveryPending)
        assertEquals(1, httpCallCount.get())
        assertEquals(
            "https://license.skmnetwork.com/v1/license/recover",
            httpCallUrls.get().last()
        )

        val payload = lastPayload()
        assertEquals(testLicenseCode, payload.getString("licenseCode"))
        assertEquals(testOwnerEmail.lowercase(), payload.getString("ownerEmail"))
        assertEquals(existingDeviceBinding, payload.getString("newDeviceBinding"))
        // reason is intentionally omitted by design
        assertTrue(!payload.has("reason"))
    }

    // -------------------------------------------------------------- 2. success mapping
    @Test
    fun `HTTP 200 with RECOVERY_PENDING maps to RecoveryPending`() = runBlocking {
        val result = createTestManager().requestDeviceRecovery(testLicenseCode, testOwnerEmail)
        assertEquals(RecoveryResult.RecoveryPending::class, result::class)
    }

    // ----------------------------------------------------------- 3-6. error code mapping
    @Test
    fun `INVALID_REQUEST maps to InvalidRequest`() = runBlocking {
        respondWithError("INVALID_REQUEST")
        val result = createTestManager().requestDeviceRecovery(testLicenseCode, testOwnerEmail)
        assertEquals(RecoveryResult.InvalidRequest::class, result::class)
    }

    @Test
    fun `LICENSE_NOT_FOUND maps to LicenseNotFound`() = runBlocking {
        respondWithError("LICENSE_NOT_FOUND")
        val result = createTestManager().requestDeviceRecovery(testLicenseCode, testOwnerEmail)
        assertEquals(RecoveryResult.LicenseNotFound::class, result::class)
    }

    @Test
    fun `LICENSE_REVOKED maps to LicenseRevoked`() = runBlocking {
        respondWithError("LICENSE_REVOKED")
        val result = createTestManager().requestDeviceRecovery(testLicenseCode, testOwnerEmail)
        assertEquals(RecoveryResult.LicenseRevoked::class, result::class)
    }

    @Test
    fun `EMAIL_MISMATCH maps to EmailMismatch`() = runBlocking {
        respondWithError("EMAIL_MISMATCH")
        val result = createTestManager().requestDeviceRecovery(testLicenseCode, testOwnerEmail)
        assertEquals(RecoveryResult.EmailMismatch::class, result::class)
    }

    // ------------------------------------------------------------ 7. transport failure
    @Test
    fun `IOException maps to NetworkError`() = runBlocking {
        simulateNetworkFailure = true
        val result = createTestManager().requestDeviceRecovery(testLicenseCode, testOwnerEmail)
        assertEquals(RecoveryResult.NetworkError::class, result::class)
    }

    // ---------------------------------------------------------- 8. unexpected response
    @Test
    fun `unrecognised body maps to UnexpectedError`() = runBlocking {
        forceStatusCode = 200
        forceBody = "{ this is not json"
        val result = createTestManager().requestDeviceRecovery(testLicenseCode, testOwnerEmail)
        assertEquals(RecoveryResult.UnexpectedError::class, result::class)
    }

    @Test
    fun `server 5xx with unknown code maps to UnexpectedError`() = runBlocking {
        forceStatusCode = 500
        forceBody = "{\"error\": \"boom\"}"
        val result = createTestManager().requestDeviceRecovery(testLicenseCode, testOwnerEmail)
        assertEquals(RecoveryResult.UnexpectedError::class, result::class)
    }

    @Test
    fun `unmapped backend code maps to UnexpectedError`() = runBlocking {
        respondWithError("SOMETHING_ELSE")
        val result = createTestManager().requestDeviceRecovery(testLicenseCode, testOwnerEmail)
        assertEquals(RecoveryResult.UnexpectedError::class, result::class)
    }

    // ------------------------------------------------------ 9. existing binding reused
    @Test
    fun `manager submits the binding already stored in DataStore`() = runBlocking {
        createTestManager().requestDeviceRecovery(testLicenseCode, testOwnerEmail)
        val fromRepo = testRepo.getOrCreateDeviceId()
        assertEquals(fromRepo, lastPayload().getString("newDeviceBinding"))
        assertEquals(existingDeviceBinding, fromRepo)
    }

    // -------------------------------------------------------- 10. no second UUID ever
    @Test
    fun `repeated recovery never generates a second identifier`() = runBlocking {
        val manager = createTestManager()

        manager.requestDeviceRecovery(testLicenseCode, testOwnerEmail)
        val first = lastPayload().getString("newDeviceBinding")
        manager.requestDeviceRecovery(testLicenseCode, testOwnerEmail)
        val second = lastPayload().getString("newDeviceBinding")

        assertEquals(existingDeviceBinding, first)
        assertEquals(first, second)
        assertNotEquals("", second)
        // The persisted value is untouched by recovery.
        assertEquals(existingDeviceBinding, testRepo.getOrCreateDeviceId())
    }

    @Test
    fun `recovery on a fresh install without a stored id still reuses one stable value`() = runBlocking {
        // No device_id primed: the repository mints one, and it must then be stable.
        val freshRepo = UserPreferencesRepository(
            context = null,
            dataStore = RecoveryTestDataStore(emptyPreferences())
        )
        val manager = object : LicenseManager(
            userPreferencesRepository = freshRepo,
            provider = ProductionLicenseProvider(freshRepo),
            apiClient = apiClient,
            context = null
        ) {}

        manager.requestDeviceRecovery(testLicenseCode, testOwnerEmail)
        val first = lastPayload().getString("newDeviceBinding")
        manager.requestDeviceRecovery(testLicenseCode, testOwnerEmail)
        val second = lastPayload().getString("newDeviceBinding")

        assertTrue(first.isNotBlank())
        assertEquals(first, second)
        assertEquals(freshRepo.getOrCreateDeviceId(), first)
    }

    // ------------------------------------------------- 11-12. client-side pre-validation
    @Test
    fun `blank license code returns InvalidRequest without calling the network`() = runBlocking {
        val result = createTestManager().requestDeviceRecovery("   ", testOwnerEmail)
        assertEquals(RecoveryResult.InvalidRequest::class, result::class)
        assertEquals(0, httpCallCount.get())
    }

    @Test
    fun `blank owner email returns InvalidRequest without calling the network`() = runBlocking {
        val result = createTestManager().requestDeviceRecovery(testLicenseCode, "  ")
        assertEquals(RecoveryResult.InvalidRequest::class, result::class)
        assertEquals(0, httpCallCount.get())
    }

    // ------------------------------------------------------ 13. no admin rebind involved
    @Test
    fun `recovery never targets an admin endpoint`() = runBlocking {
        createTestManager().requestDeviceRecovery(testLicenseCode, testOwnerEmail)
        for (url in httpCallUrls.get()) {
            assertTrue(
                "unexpected endpoint contacted: $url",
                url.endsWith("/v1/license/recover")
            )
            assertTrue(!url.contains("/v1/admin/"))
        }
    }

    @Test
    fun `recovery does not alter local licence entitlement`() = runBlocking {
        val before = testRepo.getLicenseEntitlement()
        createTestManager().requestDeviceRecovery(testLicenseCode, testOwnerEmail)
        val after = testRepo.getLicenseEntitlement()
        assertEquals(before, after)
    }

    // ------------------------------------------------------ source-level structure guards
    @Test
    fun `gate screen exposes recovery action and pending copy without any admin endpoint`() {
        val src = java.io.File(
            "src/main/java/id/skmnetwork/bukuwarung/ui/license/LicenseGateScreen.kt"
        ).takeIf { it.exists() }?.readText() ?: java.io.File(
            "app/src/main/java/id/skmnetwork/bukuwarung/ui/license/LicenseGateScreen.kt"
        ).readText()

        assertTrue("recovery action label missing", src.contains("Pulihkan Perangkat"))
        assertTrue(
            "pending copy must not claim the licence is restored",
            src.contains("Silakan tunggu persetujuan dukungan")
        )
        assertTrue(
            "recovery must be user-initiated only",
            src.contains("requestDeviceRecovery")
        )
        assertTrue("admin rebind must never be referenced", !src.contains("/v1/admin/"))
        assertTrue(
            "duplicate taps must be prevented",
            src.contains("recoveryInFlight")
        )
    }

    @Test
    fun `api client recovery never references an admin endpoint or logs credentials`() {
        val src = java.io.File(
            "src/main/java/id/skmnetwork/bukuwarung/license/LicenseApiClient.kt"
        ).takeIf { it.exists() }?.readText() ?: java.io.File(
            "app/src/main/java/id/skmnetwork/bukuwarung/license/LicenseApiClient.kt"
        ).readText()

        val recoverBlock = src.substringAfter("suspend fun recoverLicense(")
            .substringBefore("suspend fun trackFunnelEvent(")

        assertTrue(recoverBlock.contains("/v1/license/recover"))
        assertTrue("admin endpoint referenced in client", !recoverBlock.contains("/v1/admin/"))
        assertTrue("credentials must not be logged", !recoverBlock.contains("Log."))
    }
}
