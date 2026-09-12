package id.skmnetwork.bukuwarung.backup.transport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

data class SheetsHttpResponse(
    val statusCode: Int,
    val headers: Map<String, List<String>>,
    val body: String
) {
    val isSuccessful: Boolean get() = statusCode in 200..299
}

interface SheetsHttpEngine {
    suspend fun execute(
        method: String,
        urlString: String,
        headers: Map<String, String>,
        body: String? = null
    ): Result<SheetsHttpResponse>
}

class DefaultSheetsHttpEngine(
    private val connectTimeoutMs: Int = 15000,
    private val readTimeoutMs: Int = 30000
) : SheetsHttpEngine {

    override suspend fun execute(
        method: String,
        urlString: String,
        headers: Map<String, String>,
        body: String?
    ): Result<SheetsHttpResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                doInput = true

                if (this is HttpsURLConnection) {
                    // Standard Android TLS configuration
                }

                headers.forEach { (key, value) ->
                    setRequestProperty(key, value)
                }

                if (!body.isNullOrEmpty() && (method == "POST" || method == "PUT" || method == "PATCH")) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                        writer.write(body)
                        writer.flush()
                    }
                }
            }

            val statusCode = connection.responseCode
            val inputStream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }

            val responseBody = inputStream?.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            } ?: ""

            val responseHeaders = connection.headerFields ?: emptyMap()
            connection.disconnect()

            SheetsHttpResponse(
                statusCode = statusCode,
                headers = responseHeaders,
                body = responseBody
            )
        }
    }
}
