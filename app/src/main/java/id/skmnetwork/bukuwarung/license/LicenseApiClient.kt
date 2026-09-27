package id.skmnetwork.bukuwarung.license

import id.skmnetwork.bukuwarung.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * HTTP response holder for License API communication.
 */
data class LicenseHttpResponse(
    val statusCode: Int,
    val body: String
)

/**
 * Transport interface for License HTTP calls.
 */
interface LicenseHttpTransport {
    suspend fun post(
        url: String,
        jsonPayload: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): LicenseHttpResponse
}

/**
 * Default production transport using standard HttpURLConnection.
 */
class DefaultLicenseHttpTransport : LicenseHttpTransport {
    override suspend fun post(
        url: String,
        jsonPayload: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): LicenseHttpResponse = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val targetUrl = URL(url)
            connection = (targetUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                doOutput = true
                doInput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
            }

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(jsonPayload)
                writer.flush()
            }

            val responseCode = connection.responseCode
            val responseStream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val responseBody = if (responseStream != null) {
                BufferedReader(InputStreamReader(responseStream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            } else {
                ""
            }

            LicenseHttpResponse(statusCode = responseCode, body = responseBody)
        } finally {
            connection?.disconnect()
        }
    }
}

/**
 * Android License API Client.
 * Connects to C.2 License Server:
 * - POST /v1/license/activate
 * - POST /v1/license/validate
 *
 * Enforces:
 * - No sensitive secrets / API keys in headers.
 * - No raw server error leaks.
 * - Robust timeouts (10s connect / 10s read).
 * - Safe response code mapping.
 * - Strict HTTPS enforcement in release builds.
 */
class LicenseApiClient(
    private val baseUrl: String = BuildConfig.LICENSE_SERVER_URL,
    private val connectTimeoutMs: Int = 10000,
    private val readTimeoutMs: Int = 10000,
    private val transport: LicenseHttpTransport = DefaultLicenseHttpTransport()
) {
    init {
        // Enforce HTTPS for non-debug/production environments
        if (!BuildConfig.DEBUG && !baseUrl.startsWith("https://")) {
            throw IllegalStateException("Production License Server URL must use secure HTTPS: $baseUrl")
        }
    }

    /**
     * Activates a license on this device.
     */
    suspend fun activateLicense(
        licenseCode: String,
        ownerEmail: String,
        deviceBinding: String
    ): ActivationResult = withContext(Dispatchers.IO) {
        val endpoint = "$baseUrl/v1/license/activate"
        try {
            val payload = JSONObject().apply {
                put("licenseCode", licenseCode.trim())
                put("ownerEmail", ownerEmail.trim().lowercase())
                put("deviceBinding", deviceBinding.trim())
            }

            val response = transport.post(
                url = endpoint,
                jsonPayload = payload.toString(),
                connectTimeoutMs = connectTimeoutMs,
                readTimeoutMs = readTimeoutMs
            )

            val json = try {
                if (response.body.isNotBlank()) JSONObject(response.body) else JSONObject()
            } catch (e: Exception) {
                JSONObject()
            }

            val isSuccess = json.optBoolean("success", false)
            if (response.statusCode == 200 && isSuccess) {
                return@withContext ActivationResult.Active(
                    ownerEmail = ownerEmail.trim().lowercase(),
                    message = "Aktivasi berhasil"
                )
            }

            // Map error codes from C.2 License Server
            val errorCode = json.optJSONObject("error")?.optString("code")
                ?: json.optString("error", "")

            when (errorCode) {
                "EMAIL_MISMATCH" -> ActivationResult.EmailMismatch()
                "DEVICE_MISMATCH" -> ActivationResult.DeviceMismatch()
                "LICENSE_REVOKED" -> ActivationResult.Revoked()
                "LICENSE_NOT_FOUND", "INVALID_REQUEST" -> ActivationResult.Invalid()
                else -> {
                    if (response.statusCode in 500..599) {
                        ActivationResult.ServerError()
                    } else {
                        ActivationResult.Invalid()
                    }
                }
            }
        } catch (e: java.io.IOException) {
            ActivationResult.NetworkError("Tidak dapat terhubung ke server lisensi")
        } catch (e: Exception) {
            ActivationResult.ServerError("Terjadi kesalahan pada sistem")
        }
    }

    /**
     * Validates whether the active license binding is valid on the server.
     */
    suspend fun validateLicense(
        licenseCode: String,
        ownerEmail: String,
        deviceBinding: String
    ): ValidationResult = withContext(Dispatchers.IO) {
        val endpoint = "$baseUrl/v1/license/validate"
        try {
            val payload = JSONObject().apply {
                put("licenseCode", licenseCode.trim())
                put("ownerEmail", ownerEmail.trim().lowercase())
                put("deviceBinding", deviceBinding.trim())
            }

            val response = transport.post(
                url = endpoint,
                jsonPayload = payload.toString(),
                connectTimeoutMs = connectTimeoutMs,
                readTimeoutMs = readTimeoutMs
            )

            val json = try {
                if (response.body.isNotBlank()) JSONObject(response.body) else JSONObject()
            } catch (e: Exception) {
                JSONObject()
            }

            val status = json.optString("status", "")
            val isSuccess = json.optBoolean("success", false)

            if (response.statusCode == 200 && (isSuccess || status == "VALID")) {
                return@withContext ValidationResult.Valid()
            }

            when (status) {
                "DEVICE_MISMATCH" -> ValidationResult.DeviceMismatch()
                "EMAIL_MISMATCH" -> ValidationResult.EmailMismatch()
                "REVOKED" -> ValidationResult.Revoked()
                "INVALID" -> ValidationResult.Invalid()
                else -> {
                    if (response.statusCode in 500..599) {
                        ValidationResult.ServerError()
                    } else {
                        ValidationResult.Invalid()
                    }
                }
            }
        } catch (e: java.io.IOException) {
            ValidationResult.NetworkError("Tidak dapat terhubung ke server")
        } catch (e: Exception) {
            ValidationResult.ServerError("Terjadi kesalahan pada sistem")
        }
    }

    /**
     * Requests a device recovery for an existing licence.
     *
     * This is a customer-initiated, PUBLIC endpoint. It records a PENDING recovery request on
     * the server and changes NOTHING else: it does not revoke the current device, does not
     * activate the new one, and does not grant access. An authorised admin rebind remains a
     * separate, explicit step.
     *
     * Logs nothing. No licence code, owner email, or device binding is written anywhere.
     */
    suspend fun recoverLicense(
        licenseCode: String,
        ownerEmail: String,
        newDeviceBinding: String,
        reason: String? = null
    ): RecoveryResult = withContext(Dispatchers.IO) {
        val endpoint = "$baseUrl/v1/license/recover"
        try {
            val payload = JSONObject().apply {
                put("licenseCode", licenseCode.trim())
                put("ownerEmail", ownerEmail.trim().lowercase())
                put("newDeviceBinding", newDeviceBinding.trim())
                if (!reason.isNullOrBlank()) {
                    put("reason", reason)
                }
            }

            val response = transport.post(
                url = endpoint,
                jsonPayload = payload.toString(),
                connectTimeoutMs = connectTimeoutMs,
                readTimeoutMs = readTimeoutMs
            )

            val json = try {
                if (response.body.isNotBlank()) JSONObject(response.body) else JSONObject()
            } catch (e: Exception) {
                JSONObject()
            }

            val status = json.optString("status", "")
            if (response.statusCode == 200 && status == "RECOVERY_PENDING") {
                return@withContext RecoveryResult.RecoveryPending()
            }

            // Backend contract codes. Raw server messages are deliberately discarded so that no
            // internal detail can reach the customer-facing UI.
            when (json.optJSONObject("error")?.optString("code", "")) {
                "INVALID_REQUEST" -> RecoveryResult.InvalidRequest()
                "LICENSE_NOT_FOUND" -> RecoveryResult.LicenseNotFound()
                "LICENSE_REVOKED" -> RecoveryResult.LicenseRevoked()
                "EMAIL_MISMATCH" -> RecoveryResult.EmailMismatch()
                else -> RecoveryResult.UnexpectedError()
            }
        } catch (e: java.io.IOException) {
            RecoveryResult.NetworkError()
        } catch (e: Exception) {
            RecoveryResult.UnexpectedError()
        }
    }

    /**
     * Reports a non-blocking marketing/conversion event to the server.
     * Respects offline-first constraints and strictly transmits zero business data.
     */
    suspend fun trackFunnelEvent(
        eventType: String,
        utmSource: String? = null,
        utmMedium: String? = null,
        utmCampaign: String? = null,
        utmContent: String? = null,
        leadToken: String? = null,
        installationId: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val endpoint = "$baseUrl/v1/landing/track"
        try {
            val payload = JSONObject().apply {
                put("eventType", eventType)
                if (!utmSource.isNullOrBlank()) put("utm_source", utmSource)
                if (!utmMedium.isNullOrBlank()) put("utm_medium", utmMedium)
                if (!utmCampaign.isNullOrBlank()) put("utm_campaign", utmCampaign)
                if (!utmContent.isNullOrBlank()) put("utm_content", utmContent)
                if (!leadToken.isNullOrBlank()) put("leadToken", leadToken)
                if (!installationId.isNullOrBlank()) put("installationId", installationId)
            }

            val response = transport.post(
                url = endpoint,
                jsonPayload = payload.toString(),
                connectTimeoutMs = connectTimeoutMs,
                readTimeoutMs = readTimeoutMs
            )
            response.statusCode in 200..299
        } catch (_: Exception) {
            false
        }
    }
}
