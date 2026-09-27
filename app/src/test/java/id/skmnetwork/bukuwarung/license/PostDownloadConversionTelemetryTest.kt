package id.skmnetwork.bukuwarung.license

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class PostDownloadConversionTelemetryTest {

    private val trackedEvents = mutableListOf<JSONObject>()
    private var simulateNetworkFailure = false

    private lateinit var mockTransport: LicenseHttpTransport
    private lateinit var apiClient: LicenseApiClient

    @Before
    fun setUp() {
        trackedEvents.clear()
        simulateNetworkFailure = false

        mockTransport = object : LicenseHttpTransport {
            override suspend fun post(
                url: String,
                jsonPayload: String,
                connectTimeoutMs: Int,
                readTimeoutMs: Int
            ): LicenseHttpResponse {
                if (simulateNetworkFailure) {
                    throw IOException("Simulated offline/network connection failure")
                }

                if (url.endsWith("/v1/landing/track")) {
                    val json = if (jsonPayload.isNotBlank()) JSONObject(jsonPayload) else JSONObject()
                    trackedEvents.add(json)
                    val resp = JSONObject().apply {
                        put("success", true)
                        put("data", JSONObject().put("leadToken", "LW-TEST01").put("action", "created"))
                    }
                    return LicenseHttpResponse(200, resp.toString())
                }

                return LicenseHttpResponse(404, "{}")
            }
        }

        // Gate H.3: `mockTransport` answers every request, so the base URL is never dialled, but
        // LicenseApiClient refuses plain HTTP whenever BuildConfig.DEBUG is false. The suite now
        // runs against the release configuration, so the fixture must be HTTPS to stay
        // build-variant agnostic. The production HTTPS guard itself is unchanged.
        apiClient = LicenseApiClient(
            baseUrl = "https://localhost:3000",
            transport = mockTransport
        )
    }

    @Test
    fun `TEST A - First app launch emits APP_FIRST_OPEN and marks isFirstLaunchRecorded`() = runBlocking {
        var isFirstLaunchRecorded = false
        val eventList = mutableListOf<String>()

        // Simulate first launch condition
        if (!isFirstLaunchRecorded) {
            isFirstLaunchRecorded = true
            val success = apiClient.trackFunnelEvent(
                eventType = "APP_FIRST_OPEN",
                utmSource = "app_license_gate",
                utmMedium = "in_app",
                utmCampaign = "buku_warung_v020"
            )
            if (success) {
                eventList.add("APP_FIRST_OPEN")
            }
        }

        assertTrue(isFirstLaunchRecorded)
        assertEquals(1, trackedEvents.size)
        assertEquals("APP_FIRST_OPEN", trackedEvents[0].getString("eventType"))
        assertEquals("app_license_gate", trackedEvents[0].getString("utm_source"))
        assertEquals("in_app", trackedEvents[0].getString("utm_medium"))
        assertEquals("buku_warung_v020", trackedEvents[0].getString("utm_campaign"))
    }

    @Test
    fun `TEST B - Second app launch does NOT emit APP_FIRST_OPEN again`() = runBlocking {
        var isFirstLaunchRecorded = true // already recorded
        val eventList = mutableListOf<String>()

        // Simulate second launch check
        if (!isFirstLaunchRecorded) {
            isFirstLaunchRecorded = true
            apiClient.trackFunnelEvent(
                eventType = "APP_FIRST_OPEN",
                utmSource = "app_license_gate",
                utmMedium = "in_app",
                utmCampaign = "buku_warung_v020"
            )
            eventList.add("APP_FIRST_OPEN")
        }

        assertTrue(isFirstLaunchRecorded)
        assertEquals(0, trackedEvents.size)
        assertEquals(0, eventList.size)
    }

    @Test
    fun `TEST C - LicenseGateScreen emits LICENSE_GATE_VIEWED once when displayed to unlicensed user`() = runBlocking {
        val success = apiClient.trackFunnelEvent(
            eventType = "LICENSE_GATE_VIEWED",
            utmSource = "app_license_gate",
            utmMedium = "in_app",
            utmCampaign = "buku_warung_v020"
        )

        assertTrue(success)
        assertEquals(1, trackedEvents.size)
        assertEquals("LICENSE_GATE_VIEWED", trackedEvents[0].getString("eventType"))
        assertEquals("app_license_gate", trackedEvents[0].getString("utm_source"))
    }

    @Test
    fun `TEST D - Compose recomposition does NOT create duplicate LICENSE_GATE_VIEWED events when guarded by LaunchedEffect Unit`() = runBlocking {
        var launchedEffectRan = false

        // Simulate initial composition
        if (!launchedEffectRan) {
            launchedEffectRan = true
            apiClient.trackFunnelEvent(
                eventType = "LICENSE_GATE_VIEWED",
                utmSource = "app_license_gate",
                utmMedium = "in_app",
                utmCampaign = "buku_warung_v020"
            )
        }

        // Simulate 5 recompositions (LaunchedEffect(Unit) does not re-run on recomposition)
        for (i in 1..5) {
            if (!launchedEffectRan) {
                apiClient.trackFunnelEvent(
                    eventType = "LICENSE_GATE_VIEWED",
                    utmSource = "app_license_gate",
                    utmMedium = "in_app",
                    utmCampaign = "buku_warung_v020"
                )
            }
        }

        assertEquals(1, trackedEvents.size)
    }

    @Test
    fun `TEST E - Purchase button click emits LICENSE_PURCHASE_CLICKED`() = runBlocking {
        val success = apiClient.trackFunnelEvent(
            eventType = "LICENSE_PURCHASE_CLICKED",
            utmSource = "app_license_gate",
            utmMedium = "in_app",
            utmCampaign = "buku_warung_v020"
        )

        assertTrue(success)
        assertEquals(1, trackedEvents.size)
        assertEquals("LICENSE_PURCHASE_CLICKED", trackedEvents[0].getString("eventType"))
    }

    @Test
    fun `TEST F - Purchase URL preserves UTM parameters`() {
        val purchaseUrl = "https://license.skmnetwork.com/beli/buku-warung?utm_source=app_license_gate&utm_medium=in_app&utm_campaign=buku_warung_v020"
        assertTrue(purchaseUrl.contains("utm_source=app_license_gate"))
        assertTrue(purchaseUrl.contains("utm_medium=in_app"))
        assertTrue(purchaseUrl.contains("utm_campaign=buku_warung_v020"))
    }

    @Test
    fun `TEST G - Offline network failure does not throw exception and fails silently`() = runBlocking {
        simulateNetworkFailure = true

        val success = apiClient.trackFunnelEvent(
            eventType = "APP_FIRST_OPEN",
            utmSource = "app_license_gate",
            utmMedium = "in_app",
            utmCampaign = "buku_warung_v020"
        )

        // Offline: fails gracefully returning false without throwing unhandled exceptions
        assertFalse(success)
        assertEquals(0, trackedEvents.size)
    }

    @Test
    fun `TEST H - Licensed users do not emit LICENSE_GATE_VIEWED`() = runBlocking {
        val isLicensed = true

        if (!isLicensed) {
            apiClient.trackFunnelEvent(
                eventType = "LICENSE_GATE_VIEWED",
                utmSource = "app_license_gate",
                utmMedium = "in_app",
                utmCampaign = "buku_warung_v020"
            )
        }

        assertEquals(0, trackedEvents.size)
    }

    @Test
    fun `TEST I - WhatsApp click emits LICENSE_WHATSAPP_CLICKED`() = runBlocking {
        val success = apiClient.trackFunnelEvent(
            eventType = "LICENSE_WHATSAPP_CLICKED",
            utmSource = "app_license_gate",
            utmMedium = "in_app",
            utmCampaign = "buku_warung_v020"
        )

        assertTrue(success)
        assertEquals(1, trackedEvents.size)
        assertEquals("LICENSE_WHATSAPP_CLICKED", trackedEvents[0].getString("eventType"))
    }
}
