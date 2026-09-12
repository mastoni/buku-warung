package id.skmnetwork.bukuwarung.backup.transport

import id.skmnetwork.bukuwarung.backup.BackupMetadata
import id.skmnetwork.bukuwarung.backup.BackupSnapshot
import id.skmnetwork.bukuwarung.backup.CanonicalSerializer
import id.skmnetwork.bukuwarung.backup.SheetTab
import id.skmnetwork.bukuwarung.backup.SheetsBackupTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/**
 * Production implementation of SheetsBackupTransport conforming to Step 5 SPI.
 * Communicates with Google Sheets v4 REST API using OAuth Bearer authorization.
 */
class GoogleSheetsApiTransport(
    private val authProvider: GoogleAuthCredentialProvider,
    private val httpEngine: SheetsHttpEngine = DefaultSheetsHttpEngine(),
    private val baseUrl: String = "https://sheets.googleapis.com/v4/spreadsheets"
) : SheetsBackupTransport {

    companion object {
        const val METADATA_TAB_NAME = "00_Metadata"
    }

    override suspend fun writeBackup(spreadsheetId: String, snapshot: BackupSnapshot): Result<Unit> = withContext(Dispatchers.IO) {
        // 1. Authorize
        val tokenResult = authProvider.getAccessToken()
        if (tokenResult.isFailure) {
            return@withContext Result.failure(
                tokenResult.exceptionOrNull() ?: SecurityException("Gagal mendapatkan token autentikasi Google")
            )
        }
        val accessToken = tokenResult.getOrThrow()
        if (accessToken.isBlank()) {
            return@withContext Result.failure(SecurityException("Token autentikasi Google kosong"))
        }

        val authHeaders = mapOf(
            "Authorization" to "Bearer $accessToken",
            "Accept" to "application/json"
        )

        // 2. Formulate Batch Data Payload
        val valueRangesJson = JSONArray()

        // Tab 00_Metadata
        val metadataRange = JSONObject().apply {
            put("range", "$METADATA_TAB_NAME!A1")
            val rowsArray = JSONArray().apply {
                // Header
                put(JSONArray().apply {
                    put("key")
                    put("value")
                })
                // Key-value rows
                put(JSONArray().apply { put("backup_format_version"); put(snapshot.metadata.backupFormatVersion) })
                put(JSONArray().apply { put("room_schema_version"); put(snapshot.metadata.roomSchemaVersion.toString()) })
                put(JSONArray().apply { put("exported_at"); put(snapshot.metadata.exportedAt.toString()) })
                put(JSONArray().apply { put("business_id"); put(snapshot.metadata.businessId) })
                put(JSONArray().apply { put("device_id"); put(snapshot.metadata.deviceId) })
                put(JSONArray().apply { put("app_version"); put(snapshot.metadata.appVersion) })
                put(JSONArray().apply { put("total_records"); put(snapshot.metadata.totalRecords.toString()) })
                put(JSONArray().apply { put("checksum"); put(snapshot.metadata.checksum) })
            }
            put("values", rowsArray)
        }
        valueRangesJson.put(metadataRange)

        // Data Tabs 01_Business to 16_StockMovements
        CanonicalSerializer.DATA_TAB_NAMES.forEach { tabName ->
            val tab = snapshot.getTab(tabName)
            if (tab != null) {
                val tabRange = JSONObject().apply {
                    put("range", "$tabName!A1")
                    val rowsArray = JSONArray().apply {
                        // Header row
                        put(JSONArray().apply {
                            tab.headers.forEach { put(it) }
                        })
                        // Data rows
                        tab.rows.forEach { row ->
                            put(JSONArray().apply {
                                row.forEach { put(it) }
                            })
                        }
                    }
                    put("values", rowsArray)
                }
                valueRangesJson.put(tabRange)
            }
        }

        val requestBody = JSONObject().apply {
            put("valueInputOption", "RAW")
            put("data", valueRangesJson)
        }.toString()

        val updateUrl = "$baseUrl/$spreadsheetId/values:batchUpdate"
        val responseResult = httpEngine.execute("POST", updateUrl, authHeaders, requestBody)

        if (responseResult.isFailure) {
            return@withContext Result.failure(
                responseResult.exceptionOrNull() ?: IOException("Gagal mengirim request ke Google Sheets API")
            )
        }

        val response = responseResult.getOrThrow()
        when (response.statusCode) {
            in 200..299 -> Result.success(Unit)
            401, 403 -> Result.failure(SecurityException("Google API autentikasi ditolak (HTTP ${response.statusCode})"))
            404 -> Result.failure(IOException("Spreadsheet '$spreadsheetId' tidak ditemukan di Google Drive (HTTP 404)"))
            else -> Result.failure(IOException("Google Sheets API error HTTP ${response.statusCode}: ${response.body}"))
        }
    }

    override suspend fun readBackup(spreadsheetId: String): Result<BackupSnapshot> = withContext(Dispatchers.IO) {
        // 1. Authorize
        val tokenResult = authProvider.getAccessToken()
        if (tokenResult.isFailure) {
            return@withContext Result.failure(
                tokenResult.exceptionOrNull() ?: SecurityException("Gagal mendapatkan token autentikasi Google")
            )
        }
        val accessToken = tokenResult.getOrThrow()
        if (accessToken.isBlank()) {
            return@withContext Result.failure(SecurityException("Token autentikasi Google kosong"))
        }

        val authHeaders = mapOf(
            "Authorization" to "Bearer $accessToken",
            "Accept" to "application/json"
        )

        // 2. Build Query Ranges for All 17 Tabs
        val rangeParams = CanonicalSerializer.ALL_TAB_NAMES.joinToString("&") { tabName ->
            "ranges=" + URLEncoder.encode("$tabName!A1:Z", "UTF-8")
        }
        val getUrl = "$baseUrl/$spreadsheetId/values:batchGet?$rangeParams&valueRenderOption=UNFORMATTED_VALUE&dateTimeRenderOption=FORMATTED_STRING"

        val responseResult = httpEngine.execute("GET", getUrl, authHeaders, null)
        if (responseResult.isFailure) {
            return@withContext Result.failure(
                responseResult.exceptionOrNull() ?: IOException("Gagal membaca spreadsheet dari Google Sheets API")
            )
        }

        val response = responseResult.getOrThrow()
        if (response.statusCode == 401 || response.statusCode == 403) {
            return@withContext Result.failure(SecurityException("Google API autentikasi ditolak (HTTP ${response.statusCode})"))
        }
        if (response.statusCode == 404) {
            return@withContext Result.failure(IOException("Spreadsheet '$spreadsheetId' tidak ditemukan (HTTP 404)"))
        }
        if (!response.isSuccessful) {
            return@withContext Result.failure(IOException("Google Sheets API error HTTP ${response.statusCode}: ${response.body}"))
        }

        try {
            val rootJson = JSONObject(response.body)
            val valueRanges = rootJson.optJSONArray("valueRanges")
                ?: return@withContext Result.failure(IOException("Respons Google Sheets tidak memiliki data valueRanges"))

            var metadata: BackupMetadata? = null
            val tabsMap = mutableMapOf<String, SheetTab>()

            for (i in 0 until valueRanges.length()) {
                val rangeObj = valueRanges.getJSONObject(i)
                val fullRange = rangeObj.optString("range", "")
                val tabName = fullRange.substringBefore('!').trim('\'')
                val valuesArray = rangeObj.optJSONArray("values")

                if (tabName == METADATA_TAB_NAME) {
                    val metadataMap = mutableMapOf<String, String>()
                    if (valuesArray != null) {
                        for (r in 1 until valuesArray.length()) {
                            val rowArray = valuesArray.optJSONArray(r)
                            if (rowArray != null && rowArray.length() >= 2) {
                                val key = rowArray.optString(0, "")
                                val value = rowArray.optString(1, "")
                                if (key.isNotEmpty()) {
                                    metadataMap[key] = value
                                }
                            }
                        }
                    }
                    metadata = BackupMetadata(
                        backupFormatVersion = metadataMap["backup_format_version"] ?: "1.0",
                        roomSchemaVersion = metadataMap["room_schema_version"]?.toIntOrNull() ?: 9,
                        exportedAt = metadataMap["exported_at"]?.toLongOrNull() ?: 0L,
                        businessId = metadataMap["business_id"] ?: "",
                        deviceId = metadataMap["device_id"] ?: "",
                        appVersion = metadataMap["app_version"] ?: "1.0.0",
                        totalRecords = metadataMap["total_records"]?.toIntOrNull() ?: 0,
                        checksum = metadataMap["checksum"] ?: ""
                    )
                } else if (CanonicalSerializer.DATA_TAB_NAMES.contains(tabName)) {
                    val headers = mutableListOf<String>()
                    val dataRows = mutableListOf<List<String>>()

                    if (valuesArray != null && valuesArray.length() > 0) {
                        // Header row
                        val headerRow = valuesArray.getJSONArray(0)
                        for (c in 0 until headerRow.length()) {
                            headers.add(headerRow.optString(c, ""))
                        }
                        // Data rows
                        for (r in 1 until valuesArray.length()) {
                            val rowArray = valuesArray.optJSONArray(r)
                            if (rowArray != null) {
                                val rowValues = mutableListOf<String>()
                                for (c in 0 until headers.size) {
                                    rowValues.add(rowArray.optString(c, ""))
                                }
                                dataRows.add(rowValues)
                            }
                        }
                    }

                    tabsMap[tabName] = SheetTab(
                        name = tabName,
                        headers = headers,
                        rows = dataRows
                    )
                }
            }

            if (metadata == null) {
                return@withContext Result.failure(IOException("Metadata tab '$METADATA_TAB_NAME' tidak ditemukan di spreadsheet"))
            }

            Result.success(BackupSnapshot(metadata = metadata, tabs = tabsMap))
        } catch (e: Exception) {
            Result.failure(IOException("Gagal mem-parsing payload Google Sheets: ${e.message}", e))
        }
    }
}
