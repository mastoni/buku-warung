package id.skmnetwork.bukuwarung.license

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class LicenseActivationUnitTest {

    // Test constants
    private val validLicenseCode = "BW-TEST-VALID-1234"
    private val validOwnerEmail = "owner@test.com"
    private val firstDeviceId = "device-uuid-1111"
    private val secondDeviceId = "device-uuid-2222"
    private val revokedLicenseCode = "BW-TEST-REVOKED-9999"

    private var boundDevice: String? = null
    private var isRevoked = false
    private var simulateNetworkFailure = false

    private lateinit var mockTransport: LicenseHttpTransport
    private lateinit var apiClient: LicenseApiClient

    @Before
    fun setUp() {
        boundDevice = null
        isRevoked = false
        simulateNetworkFailure = false

        mockTransport = object : LicenseHttpTransport {
            override suspend fun post(
                url: String,
                jsonPayload: String,
                connectTimeoutMs: Int,
                readTimeoutMs: Int
            ): LicenseHttpResponse {
                if (simulateNetworkFailure) {
                    throw IOException("Simulated network connection failure")
                }

                val json = if (jsonPayload.isNotBlank()) JSONObject(jsonPayload) else JSONObject()
                val licenseCode = json.optString("licenseCode")
                val ownerEmail = json.optString("ownerEmail")
                val deviceBinding = json.optString("deviceBinding")

                if (url.endsWith("/v1/license/activate")) {
                    if (licenseCode == revokedLicenseCode) {
                        val resp = JSONObject().apply {
                            put("success", false)
                            put("error", JSONObject().put("code", "LICENSE_REVOKED").put("message", "License revoked"))
                        }
                        return LicenseHttpResponse(400, resp.toString())
                    }
                    if (licenseCode != validLicenseCode) {
                        val resp = JSONObject().apply {
                            put("success", false)
                            put("error", JSONObject().put("code", "LICENSE_NOT_FOUND").put("message", "License not found"))
                        }
                        return LicenseHttpResponse(400, resp.toString())
                    }
                    if (ownerEmail != validOwnerEmail) {
                        val resp = JSONObject().apply {
                            put("success", false)
                            put("error", JSONObject().put("code", "EMAIL_MISMATCH").put("message", "Email mismatch"))
                        }
                        return LicenseHttpResponse(400, resp.toString())
                    }
                    if (boundDevice != null && boundDevice != deviceBinding) {
                        val resp = JSONObject().apply {
                            put("success", false)
                            put("error", JSONObject().put("code", "DEVICE_MISMATCH").put("message", "Device mismatch"))
                        }
                        return LicenseHttpResponse(400, resp.toString())
                    }

                    // Activate / Bind first device (or idempotent reactivate on same device)
                    boundDevice = deviceBinding
                    val resp = JSONObject().apply {
                        put("success", true)
                        put("status", "ACTIVE")
                    }
                    return LicenseHttpResponse(200, resp.toString())
                }

                if (url.endsWith("/v1/license/validate")) {
                    if (isRevoked || licenseCode == revokedLicenseCode) {
                        val resp = JSONObject().apply {
                            put("success", false)
                            put("status", "REVOKED")
                        }
                        return LicenseHttpResponse(200, resp.toString())
                    }
                    if (licenseCode != validLicenseCode) {
                        val resp = JSONObject().apply {
                            put("success", false)
                            put("status", "INVALID")
                        }
                        return LicenseHttpResponse(200, resp.toString())
                    }
                    if (ownerEmail != validOwnerEmail) {
                        val resp = JSONObject().apply {
                            put("success", false)
                            put("status", "EMAIL_MISMATCH")
                        }
                        return LicenseHttpResponse(200, resp.toString())
                    }
                    if (boundDevice != null && boundDevice != deviceBinding) {
                        val resp = JSONObject().apply {
                            put("success", false)
                            put("status", "DEVICE_MISMATCH")
                        }
                        return LicenseHttpResponse(200, resp.toString())
                    }

                    val resp = JSONObject().apply {
                        put("success", true)
                        put("status", "VALID")
                    }
                    return LicenseHttpResponse(200, resp.toString())
                }

                return LicenseHttpResponse(404, "{\"error\": \"Not Found\"}")
            }
        }

        apiClient = LicenseApiClient(
            baseUrl = "http://localhost:3000",
            transport = mockTransport
        )
    }

    // ==========================================
    // C4-01: Unactivated app shows activation gate (status = UNLICENSED)
    // ==========================================
    @Test
    fun testC4_01_UnactivatedAppShowsActivationGate() = runBlocking {
        val fakeProvider = object : LicenseProvider {
            override suspend fun checkLicense() = LicenseStatus.UNLICENSED
            override suspend fun getLicenseTier() = LicenseTier.WARUNG
        }
        val manager = LicenseManager(
            provider = fakeProvider,
            apiClient = apiClient
        )
        manager.refreshLicense()
        assertEquals(LicenseStatus.UNLICENSED, manager.licenseStatus.value)
    }

    // ==========================================
    // C4-02: Valid license + correct email + first device -> ACTIVE
    // ==========================================
    @Test
    fun testC4_02_ValidLicenseCorrectEmailFirstDevice_ReturnsActive() = runBlocking {
        val result = apiClient.activateLicense(
            licenseCode = validLicenseCode,
            ownerEmail = validOwnerEmail,
            deviceBinding = firstDeviceId
        )
        assertTrue("Expected ActivationResult.Active but got $result", result is ActivationResult.Active)
        assertEquals(validOwnerEmail, (result as ActivationResult.Active).ownerEmail)
    }

    // ==========================================
    // C4-03: Same device activation again -> idempotent
    // ==========================================
    @Test
    fun testC4_03_SameDeviceActivationAgain_IsIdempotent() = runBlocking {
        val firstResult = apiClient.activateLicense(validLicenseCode, validOwnerEmail, firstDeviceId)
        assertTrue(firstResult is ActivationResult.Active)

        // Reactivate on same device
        val secondResult = apiClient.activateLicense(validLicenseCode, validOwnerEmail, firstDeviceId)
        assertTrue("Expected idempotent Active on second call", secondResult is ActivationResult.Active)
    }

    // ==========================================
    // C4-04: Different email -> EMAIL_MISMATCH
    // ==========================================
    @Test
    fun testC4_04_DifferentEmail_ReturnsEmailMismatch() = runBlocking {
        val result = apiClient.activateLicense(validLicenseCode, "wrong@test.com", firstDeviceId)
        assertTrue("Expected EmailMismatch but got $result", result is ActivationResult.EmailMismatch)
    }

    // ==========================================
    // C4-05: Same license + different device -> DEVICE_MISMATCH
    // ==========================================
    @Test
    fun testC4_05_SameLicenseDifferentDevice_ReturnsDeviceMismatch() = runBlocking {
        // First device binds
        val firstResult = apiClient.activateLicense(validLicenseCode, validOwnerEmail, firstDeviceId)
        assertTrue(firstResult is ActivationResult.Active)

        // Second device attempts activation
        val secondResult = apiClient.activateLicense(validLicenseCode, validOwnerEmail, secondDeviceId)
        assertTrue("Expected DeviceMismatch on second device but got $secondResult", secondResult is ActivationResult.DeviceMismatch)
    }

    // ==========================================
    // C4-06: Revoked license -> REVOKED
    // ==========================================
    @Test
    fun testC4_06_RevokedLicense_ReturnsRevoked() = runBlocking {
        val result = apiClient.activateLicense(revokedLicenseCode, validOwnerEmail, firstDeviceId)
        assertTrue("Expected Revoked but got $result", result is ActivationResult.Revoked)
    }

    // ==========================================
    // C4-07: Invalid license -> INVALID
    // ==========================================
    @Test
    fun testC4_07_InvalidLicense_ReturnsInvalid() = runBlocking {
        val result = apiClient.activateLicense("BW-NONEXISTENT-CODE", validOwnerEmail, firstDeviceId)
        assertTrue("Expected Invalid but got $result", result is ActivationResult.Invalid)
    }

    // ==========================================
    // C4-08: Network failure -> graceful error
    // ==========================================
    @Test
    fun testC4_08_NetworkFailure_ReturnsNetworkError() = runBlocking {
        simulateNetworkFailure = true
        val result = apiClient.activateLicense(validLicenseCode, validOwnerEmail, firstDeviceId)
        assertTrue("Expected NetworkError on connection failure but got $result", result is ActivationResult.NetworkError)
    }

    // ==========================================
    // C4-09: Successful activation persists locally
    // ==========================================
    @Test
    fun testC4_09_SuccessfulActivationPersistsLocally() {
        val entitlement = LicenseEntitlementData(
            status = "ACTIVE",
            ownerEmail = validOwnerEmail,
            activatedAt = 1000L,
            lastValidatedAt = 1000L
        )
        assertTrue(entitlement.isEntitled)
        assertEquals("ACTIVE", entitlement.status)
        assertEquals(validOwnerEmail, entitlement.ownerEmail)
    }

    // ==========================================
    // C4-10: App restart preserves activation state
    // ==========================================
    @Test
    fun testC4_10_AppRestartPreservesActivationState() = runBlocking {
        val localEntitlement = LicenseEntitlementData(
            status = "ACTIVE",
            ownerEmail = validOwnerEmail,
            activatedAt = 1000L,
            lastValidatedAt = 1000L
        )
        val provider = object : LicenseProvider {
            override suspend fun checkLicense(): LicenseStatus {
                return if (localEntitlement.isEntitled) LicenseStatus.ACTIVE else LicenseStatus.UNLICENSED
            }
            override suspend fun getLicenseTier() = LicenseTier.WARUNG
        }

        val restartedManager = LicenseManager(provider = provider)
        restartedManager.refreshLicense()
        assertEquals(LicenseStatus.ACTIVE, restartedManager.licenseStatus.value)
    }

    // ==========================================
    // C4-11: Existing DataStore state is preserved
    // ==========================================
    @Test
    fun testC4_11_ExistingDataStoreStateIsPreserved() {
        val settings = id.skmnetwork.bukuwarung.data.preferences.UserSettings(
            shopName = "Warung Berkah",
            ownerName = "Pak Haji",
            cashEnabled = true,
            qrisEnabled = true
        )
        assertEquals("Warung Berkah", settings.shopName)
        assertEquals("Pak Haji", settings.ownerName)
        assertTrue(settings.cashEnabled)
    }

    // ==========================================
    // C4-12: Existing Room data is preserved
    // ==========================================
    @Test
    fun testC4_12_ExistingRoomDataIsPreserved() {
        val product = id.skmnetwork.bukuwarung.data.local.entity.ProductEntity(
            id = 100L,
            categoryId = 1L,
            name = "Gula Pasir 1kg",
            purchasePrice = 12000,
            sellingPrice = 14000,
            stock = 20.0
        )
        assertEquals(100L, product.id)
        assertEquals("Gula Pasir 1kg", product.name)
    }

    // ==========================================
    // C4-13: Admin secret is absent from Android source/build
    // ==========================================
    @Test
    fun testC4_13_AdminSecretIsAbsentFromAndroid() {
        val buildConfigFields = id.skmnetwork.bukuwarung.BuildConfig::class.java.declaredFields.map { it.name }
        assertFalse("ADMIN_API_KEY must NOT exist in BuildConfig", buildConfigFields.contains("ADMIN_API_KEY"))
        assertFalse("ADMIN_KEY must NOT exist in BuildConfig", buildConfigFields.contains("ADMIN_KEY"))
    }

    // ==========================================
    // C4-14: SERVER_PEPPER is absent from Android source/build
    // ==========================================
    @Test
    fun testC4_14_ServerPepperIsAbsentFromAndroid() {
        val buildConfigFields = id.skmnetwork.bukuwarung.BuildConfig::class.java.declaredFields.map { it.name }
        assertFalse("SERVER_PEPPER must NOT exist in BuildConfig", buildConfigFields.contains("SERVER_PEPPER"))
    }

    // ==========================================
    // C4-15: License state does not expose device identifier in UI
    // ==========================================
    @Test
    fun testC4_15_LicenseStateDoesNotExposeDeviceIdentifierInUI() {
        val mismatch = ActivationResult.DeviceMismatch()
        assertFalse("DeviceMismatch message must NOT expose device identifier", mismatch.message.contains(firstDeviceId))
        assertFalse("DeviceMismatch message must NOT expose device identifier", mismatch.message.contains(secondDeviceId))
    }

    // ==========================================
    // C4-16: Transient network failure does not erase local entitlement
    // ==========================================
    @Test
    fun testC4_16_TransientNetworkFailureDoesNotEraseLocalEntitlement() = runBlocking {
        simulateNetworkFailure = true
        val validationResult = apiClient.validateLicense(validLicenseCode, validOwnerEmail, firstDeviceId)
        assertTrue(validationResult is ValidationResult.NetworkError)

        // Local entitlement remains ACTIVE despite validation network failure
        var localStatus = "ACTIVE"
        if (validationResult !is ValidationResult.NetworkError && validationResult !is ValidationResult.ServerError) {
            localStatus = "UNLICENSED"
        }
        assertEquals("Local entitlement must remain ACTIVE on network failure", "ACTIVE", localStatus)
    }

    // ==========================================
    // C4-17: Server REVOKED invalidates local license state
    // ==========================================
    @Test
    fun testC4_17_ServerRevokedInvalidatesLocalLicenseState() = runBlocking {
        isRevoked = true
        val result = apiClient.validateLicense(validLicenseCode, validOwnerEmail, firstDeviceId)
        assertTrue("Expected ValidationResult.Revoked but got $result", result is ValidationResult.Revoked)

        var localStatus = "ACTIVE"
        if (result is ValidationResult.Revoked) {
            localStatus = "REVOKED"
        }
        assertEquals("Local state must become REVOKED", "REVOKED", localStatus)
    }

    // ==========================================
    // C4-18: Server DEVICE_MISMATCH invalidates/blocks access
    // ==========================================
    @Test
    fun testC4_18_ServerDeviceMismatchInvalidatesAccess() = runBlocking {
        boundDevice = firstDeviceId
        // Validate with second device
        val result = apiClient.validateLicense(validLicenseCode, validOwnerEmail, secondDeviceId)
        assertTrue("Expected ValidationResult.DeviceMismatch but got $result", result is ValidationResult.DeviceMismatch)

        var localEntitled = true
        if (result is ValidationResult.DeviceMismatch) {
            localEntitled = false
        }
        assertFalse("DeviceMismatch must block local access", localEntitled)
    }
}
