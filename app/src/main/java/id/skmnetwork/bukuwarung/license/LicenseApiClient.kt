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
}
