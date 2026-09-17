package id.skmnetwork.bukuwarung.license

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class TestPreferencesDataStore(
    initialValue: Preferences
) : DataStore<Preferences> {

    private val state = MutableStateFlow(initialValue)

    override val data = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

/**
 * Unit tests for LicenseManager.validateOnline() flow.
 *
 * Verifies that the "Periksa / Pulihkan Lisensi" button action (validateOnline)
 * correctly performs online validation against the License Server and that
 * error handling preserves local entitlement on transient failures.
 */
class LicenseValidationUnitTest {

    private val testLicenseCode = "BW-TEST-VALID-1234"
    private val testOwnerEmail = "owner@test.com"
    private val testDeviceId = "device-uuid-test-1234"

    private val httpCallCount = AtomicInteger(0)
    private val httpCallUrls = AtomicReference<List<String>>(emptyList())
    private val httpCallPayloads = AtomicReference<List<String>>(emptyList())
    private var simulateNetworkFailure = false
    private var simulateTimeout = false
    private var simulateServerError = false
    private var validateResponseStatus = "VALID"

    private lateinit var mockTransport: LicenseHttpTransport
    private lateinit var apiClient: LicenseApiClient
    private lateinit var testRepo: UserPreferencesRepository

    @Before
    fun setUp() {
        httpCallCount.set(0)
        httpCallUrls.set(emptyList())
        httpCallPayloads.set(emptyList())
        simulateNetworkFailure = false
        simulateTimeout = false
        simulateServerError = false
        validateResponseStatus = "VALID"

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
                if (simulateTimeout) {
                    throw SocketTimeoutException("Simulated socket timeout")
                }

                if (url.endsWith("/v1/license/validate")) {
                    if (simulateServerError) {
                        return LicenseHttpResponse(500, "{\"error\": \"Internal Server Error\"}")
                    }

                    val resp = JSONObject().apply {
                        put("success", validateResponseStatus == "VALID")
                        put("status", validateResponseStatus)
                    }
                    return LicenseHttpResponse(200, resp.toString())
                }

                return LicenseHttpResponse(404, "{\"error\": \"Not Found\"}")
            }
        }

        apiClient = LicenseApiClient(
            baseUrl = "https://license.skmnetwork.com",
            transport = mockTransport
        )

        val testDataStore = TestPreferencesDataStore(
            emptyPreferences()
        )

        testRepo = UserPreferencesRepository(
            context = null,
            dataStore = testDataStore
        )
    }

    @After
    fun tearDown() {
    }

    private fun createTestManager(
        storedLicenseCode: String = testLicenseCode,
        licenseCodeClearedFlag: AtomicReference<Boolean> = AtomicReference(false)
    ) = object : LicenseManager(
        userPreferencesRepository = testRepo,
        provider = ProductionLicenseProvider(testRepo),
        apiClient = apiClient,
        context = null
    ) {
        override fun getStoredLicenseCode(): String =
            if (licenseCodeClearedFlag.get()) "" else storedLicenseCode

        override fun clearStoredLicenseCode() {
            licenseCodeClearedFlag.set(true)
        }
    }

    // ==========================================
    // Test 1: refreshLicense with valid entitlement returns ACTIVE
    // ==========================================
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun testRefreshLicense_WithValidEntitlement_ReturnsActive() = runBlocking {
        testRepo.saveLicenseEntitlement(
            status = "ACTIVE",
            ownerEmail = testOwnerEmail,
            activatedAt = System.currentTimeMillis(),
            lastValidatedAt = System.currentTimeMillis()
        )

        val testScope = kotlinx.coroutines.test.TestScope(UnconfinedTestDispatcher())
        val manager = LicenseManager(
            userPreferencesRepository = testRepo,
            apiClient = apiClient,
            scope = testScope
        )
        manager.refreshLicense()
        testScope.runCurrent()
        assertEquals(LicenseStatus.ACTIVE, manager.licenseStatus.value)
    }

    // ==========================================
    // Test 2: refreshLicense with unlicensed returns UNLICENSED
    // ==========================================
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun testRefreshLicense_WithUnlicensed_ReturnsUnlicensed() = runBlocking {
        val testScope = kotlinx.coroutines.test.TestScope(kotlinx.coroutines.test.UnconfinedTestDispatcher())
        val manager = LicenseManager(
            userPreferencesRepository = testRepo,
            apiClient = apiClient,
            scope = testScope
        )
        manager.refreshLicense()
        testScope.runCurrent()
        assertEquals(LicenseStatus.UNLICENSED, manager.licenseStatus.value)
    }

    // ==========================================
    // Test 3: validateOnline with no entitlement returns Invalid, NO HTTP
    // ==========================================
    @Test
    fun testValidateOnline_WithNoEntitlement_ReturnsInvalid_NoHTTP() = runBlocking {
        val manager = createTestManager()
        val result = manager.validateOnline()

        assertTrue("Expected Invalid when not entitled, got: $result",
            result is ValidationResult.Invalid)
        assertEquals(0, httpCallCount.get())
    }

    // ==========================================
    // Test 4: validateOnline with valid license returns Valid, calls POST /v1/license/validate
    // ==========================================
    @Test
    fun testValidateOnline_WithValidLicense_ReturnsValid_CallsValidateEndpoint() = runBlocking {
        testRepo.saveLicenseEntitlement(
            status = "ACTIVE",
            ownerEmail = testOwnerEmail,
            activatedAt = System.currentTimeMillis(),
            lastValidatedAt = System.currentTimeMillis()
        )

        val manager = createTestManager(storedLicenseCode = testLicenseCode)
        val result = manager.validateOnline()

        assertTrue("Expected Valid but got: $result", result is ValidationResult.Valid)
        assertEquals(1, httpCallCount.get())

        val callUrl = httpCallUrls.get().first()
        assertTrue("Must call /v1/license/validate endpoint, got: $callUrl",
            callUrl.endsWith("/v1/license/validate"))

        val entitlementAfter = testRepo.getLicenseEntitlement()
        assertTrue("Local entitlement must remain ACTIVE after valid validation",
            entitlementAfter.isEntitled)
    }

    // ==========================================
    // Test 5: validateOnline with no license code returns Invalid, NO HTTP
    // ==========================================
    @Test
    fun testValidateOnline_WithNoLicenseCode_ReturnsInvalid_NoHTTP() = runBlocking {
        testRepo.saveLicenseEntitlement(
            status = "ACTIVE",
            ownerEmail = testOwnerEmail,
            activatedAt = System.currentTimeMillis(),
            lastValidatedAt = System.currentTimeMillis()
        )

        val manager = createTestManager(storedLicenseCode = "")
        val result = manager.validateOnline()

        assertTrue("Expected Invalid when licenseCode blank, got: $result",
            result is ValidationResult.Invalid)
        assertEquals(0, httpCallCount.get())
    }

    // ==========================================
    // Test 6: validateOnline network error preserves local entitlement
    // ==========================================
    @Test
    fun testValidateOnline_NetworkError_PreservesLocalEntitlement() = runBlocking {
        testRepo.saveLicenseEntitlement(
            status = "ACTIVE",
            ownerEmail = testOwnerEmail,
            activatedAt = System.currentTimeMillis(),
            lastValidatedAt = System.currentTimeMillis()
        )

        simulateNetworkFailure = true
        val manager = createTestManager(storedLicenseCode = testLicenseCode)
        val result = manager.validateOnline()

        assertTrue("Expected NetworkError", result is ValidationResult.NetworkError)
        assertEquals(1, httpCallCount.get())

        val entitlementAfter = testRepo.getLicenseEntitlement()
        assertTrue("Local entitlement must remain ACTIVE after network error",
            entitlementAfter.isEntitled)
        assertEquals("owner@test.com", entitlementAfter.ownerEmail)
    }

    // ==========================================
    // Test 7: validateOnline timeout preserves local entitlement
    // ==========================================
    @Test
    fun testValidateOnline_Timeout_PreservesLocalEntitlement() = runBlocking {
        testRepo.saveLicenseEntitlement(
            status = "ACTIVE",
            ownerEmail = testOwnerEmail,
            activatedAt = System.currentTimeMillis(),
            lastValidatedAt = System.currentTimeMillis()
        )

        simulateTimeout = true
        val manager = createTestManager(storedLicenseCode = testLicenseCode)
        val result = manager.validateOnline()

        assertTrue("Expected NetworkError for timeout", result is ValidationResult.NetworkError)
        assertEquals(1, httpCallCount.get())

        val entitlementAfter = testRepo.getLicenseEntitlement()
        assertTrue("Local entitlement must remain ACTIVE after timeout",
            entitlementAfter.isEntitled)
    }

    // ==========================================
    // Test 8: validateOnline server error (500) preserves local entitlement
    // ==========================================
    @Test
    fun testValidateOnline_ServerError_PreservesLocalEntitlement() = runBlocking {
        testRepo.saveLicenseEntitlement(
            status = "ACTIVE",
            ownerEmail = testOwnerEmail,
            activatedAt = System.currentTimeMillis(),
            lastValidatedAt = System.currentTimeMillis()
        )

        simulateServerError = true
        val manager = createTestManager(storedLicenseCode = testLicenseCode)
        val result = manager.validateOnline()

        assertTrue("Expected ServerError", result is ValidationResult.ServerError)
        assertEquals(1, httpCallCount.get())

        val entitlementAfter = testRepo.getLicenseEntitlement()
        assertTrue("Local entitlement must remain ACTIVE after server error",
            entitlementAfter.isEntitled)
    }

    // ==========================================
    // Test 9: validateOnline REVOKED clears local entitlement
    // ==========================================
    @Test
    fun testValidateOnline_Revoked_InvalidatesLocalEntitlement() = runBlocking {
        testRepo.saveLicenseEntitlement(
            status = "ACTIVE",
            ownerEmail = testOwnerEmail,
            activatedAt = System.currentTimeMillis(),
            lastValidatedAt = System.currentTimeMillis()
        )

        validateResponseStatus = "REVOKED"
        val clearedFlag = AtomicReference(false)
        val manager = createTestManager(storedLicenseCode = testLicenseCode, licenseCodeClearedFlag = clearedFlag)
        val result = manager.validateOnline()

        assertTrue("Expected Revoked", result is ValidationResult.Revoked)
        assertEquals(1, httpCallCount.get())

        val entitlementAfter = testRepo.getLicenseEntitlement()
        assertFalse("Local entitlement must be cleared after revocation",
            entitlementAfter.isEntitled)
        assertTrue("License code must be cleared after revocation", clearedFlag.get())
    }

    // ==========================================
    // Test 10: validateOnline DEVICE_MISMATCH clears local entitlement
    // ==========================================
    @Test
    fun testValidateOnline_DeviceMismatch_InvalidatesLocalEntitlement() = runBlocking {
        testRepo.saveLicenseEntitlement(
            status = "ACTIVE",
            ownerEmail = testOwnerEmail,
            activatedAt = System.currentTimeMillis(),
            lastValidatedAt = System.currentTimeMillis()
        )

        validateResponseStatus = "DEVICE_MISMATCH"
        val clearedFlag = AtomicReference(false)
        val manager = createTestManager(storedLicenseCode = testLicenseCode, licenseCodeClearedFlag = clearedFlag)
        val result = manager.validateOnline()

        assertTrue("Expected DeviceMismatch", result is ValidationResult.DeviceMismatch)
        assertEquals(1, httpCallCount.get())

        val entitlementAfter = testRepo.getLicenseEntitlement()
        assertFalse("Local entitlement must be cleared on device mismatch",
            entitlementAfter.isEntitled)
    }

    // ==========================================
    // Test 11: validateOnline EMAIL_MISMATCH clears local entitlement
    // ==========================================
    @Test
    fun testValidateOnline_EmailMismatch_InvalidatesLocalEntitlement() = runBlocking {
        testRepo.saveLicenseEntitlement(
            status = "ACTIVE",
            ownerEmail = testOwnerEmail,
            activatedAt = System.currentTimeMillis(),
            lastValidatedAt = System.currentTimeMillis()
        )

        validateResponseStatus = "EMAIL_MISMATCH"
        val manager = createTestManager(storedLicenseCode = testLicenseCode)
        val result = manager.validateOnline()

        assertTrue("Expected EmailMismatch", result is ValidationResult.EmailMismatch)
        assertEquals(1, httpCallCount.get())

        val entitlementAfter = testRepo.getLicenseEntitlement()
        assertFalse("Local entitlement must be cleared on email mismatch",
            entitlementAfter.isEntitled)
    }

    // ==========================================
    // Test 12: validateOnline INVALID clears local entitlement
    // ==========================================
    @Test
    fun testValidateOnline_Invalid_InvalidatesLocalEntitlement() = runBlocking {
        testRepo.saveLicenseEntitlement(
            status = "ACTIVE",
            ownerEmail = testOwnerEmail,
            activatedAt = System.currentTimeMillis(),
            lastValidatedAt = System.currentTimeMillis()
        )

        validateResponseStatus = "INVALID"
        val manager = createTestManager(storedLicenseCode = testLicenseCode)
        val result = manager.validateOnline()

        assertTrue("Expected Invalid", result is ValidationResult.Invalid)
        assertEquals(1, httpCallCount.get())

        val entitlementAfter = testRepo.getLicenseEntitlement()
        assertFalse("Local entitlement must be cleared on invalid",
            entitlementAfter.isEntitled)
    }

    // ==========================================
    // Test 13: API client uses POST /v1/license/validate with correct HTTPS endpoint
    // ==========================================
    @Test
    fun testLicenseApiClient_ValidateLicense_UsesCorrectEndpoint() = runBlocking {
        apiClient.validateLicense(testLicenseCode, testOwnerEmail, testDeviceId)

        assertEquals(1, httpCallCount.get())
        val url = httpCallUrls.get().first()
        assertTrue("Must call HTTPS endpoint, got: $url",
            url.startsWith("https://license.skmnetwork.com"))
        assertTrue("Must call /v1/license/validate endpoint, got: $url",
            url.endsWith("/v1/license/validate"))
    }

    // ==========================================
    // Test 14: API client sends correct payload (licenseCode, ownerEmail, deviceBinding)
    // ==========================================
    @Test
    fun testLicenseApiClient_ValidateLicense_PayloadContainsRequiredFields() = runBlocking {
        apiClient.validateLicense(testLicenseCode, testOwnerEmail, testDeviceId)

        assertEquals(1, httpCallCount.get())
        val payload = JSONObject(httpCallPayloads.get().first())
        assertEquals(testLicenseCode, payload.getString("licenseCode"))
        assertEquals(testOwnerEmail.lowercase(), payload.getString("ownerEmail"))
        assertEquals(testDeviceId, payload.getString("deviceBinding"))
    }

    // ==========================================
    // Test 15: BuildConfig fields exist and match build type
    // ==========================================
    @Test
    fun testBuildConfig_FieldsMatchBuildType() {
        val buildConfigClass = id.skmnetwork.bukuwarung.BuildConfig::class.java
        val buildType = buildConfigClass.getDeclaredField("BUILD_TYPE")
        buildType.isAccessible = true

        val enableOwnerTestField = buildConfigClass.getDeclaredField("ENABLE_OWNER_TEST")
        enableOwnerTestField.isAccessible = true

        val debugField = buildConfigClass.getDeclaredField("DEBUG")
        debugField.isAccessible = true

        val urlField = buildConfigClass.getDeclaredField("LICENSE_SERVER_URL")
        urlField.isAccessible = true

        val actualBuildType = buildType.get(null) as String
        val enableOwnerTest = enableOwnerTestField.get(null) as Boolean
        val debug = debugField.get(null) as Boolean
        val url = urlField.get(null) as String

        when (actualBuildType) {
            "release" -> {
                assertFalse("ENABLE_OWNER_TEST must be false in release", enableOwnerTest)
                assertFalse("DEBUG must be false in release", debug)
                assertEquals("https://license.skmnetwork.com", url)
            }
            "debug" -> {
                assertTrue("ENABLE_OWNER_TEST is true in debug build", enableOwnerTest)
                assertTrue("DEBUG is true in debug build", debug)
            }
            "ownerTest" -> {
                assertTrue("ENABLE_OWNER_TEST must be true in ownerTest", enableOwnerTest)
                assertFalse("DEBUG must be false in ownerTest", debug)
            }
            else -> throw AssertionError("Unknown build type: $actualBuildType")
        }
    }

    // ==========================================
    // Test 16: No hardware identifiers in device ID generation
    // ==========================================
    @Test
    fun testDeviceId_IsRandomUUID_NotHardwareIdentifier() = runBlocking {
        val deviceId = testRepo.getOrCreateDeviceId()
        assertTrue(
            "Device ID must be a valid UUID, got: $deviceId",
            deviceId.matches(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"))
        )

        val sourceFile = File(
            "src/main/java/id/skmnetwork/bukuwarung/data/preferences/UserPreferencesRepository.kt"
        )
        assertTrue("Source file must exist", sourceFile.exists())

        val source = sourceFile.readText()
        val hardwareIdentifiers = listOf(
            "TelephonyManager", "getImei", "getMeid", "getSerial", "Build.SERIAL",
            "WifiManager", "ANDROID_ID", "BluetoothAdapter", "bluetoothAddress"
        )
        hardwareIdentifiers.forEach { term ->
            assertFalse("Device ID generation must NOT use hardware identifier: $term",
                source.contains(term, ignoreCase = true))
        }
    }

    // ==========================================
    // Test 17: No secrets/API keys in BuildConfig
    // ==========================================
    @Test
    fun testNoSecretsInBuildConfig() {
        val buildConfigFields = id.skmnetwork.bukuwarung.BuildConfig::class.java.declaredFields
            .map { it.name }

        assertFalse("ADMIN_API_KEY must NOT exist in BuildConfig",
            buildConfigFields.contains("ADMIN_API_KEY"))
        assertFalse("SERVER_PEPPER must NOT exist in BuildConfig",
            buildConfigFields.contains("SERVER_PEPPER"))
        assertFalse("API_KEY must NOT exist in BuildConfig",
            buildConfigFields.contains("API_KEY"))
    }

    // ==========================================
    // Test 18: SettingsScreen "Periksa / Pulihkan Lisensi" button calls validateOnline
    // ==========================================
    @Test
    fun testSettingsScreen_ButtonCallsValidateOnline() {
        val sourceFile = File(
            "src/main/java/id/skmnetwork/bukuwarung/ui/settings/SettingsScreen.kt"
        )
        assertTrue("SettingsScreen source file must exist", sourceFile.exists())

        val source = sourceFile.readText()
        val buttonIndex = source.indexOf("Periksa / Pulihkan Lisensi")
        assertTrue("Button label must exist in source", buttonIndex >= 0)

        val buttonArea = source.substring(buttonIndex, minOf(buttonIndex + 500, source.length))
        assertTrue("Button must call validateOnline", buttonArea.contains("validateOnline"))
    }
}
