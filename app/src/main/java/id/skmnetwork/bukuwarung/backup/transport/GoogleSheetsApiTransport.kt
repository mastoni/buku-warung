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
    val authProvider: GoogleAuthCredentialProvider,
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

        // Tab 00_README
        val readmeTab = snapshot.getTab(CanonicalSerializer.README_TAB_NAME)
        if (readmeTab != null) {
            val readmeRange = JSONObject().apply {
                put("range", "${CanonicalSerializer.README_TAB_NAME}!A1")
                val rowsArray = JSONArray().apply {
                    // Header row
                    put(JSONArray().apply {
                        readmeTab.headers.forEach { put(it) }
                    })
                    // Data rows
                    readmeTab.rows.forEach { row ->
                        put(JSONArray().apply {
                            row.forEach { put(it) }
                        })
                    }
                }
                put("values", rowsArray)
            }
            valueRangesJson.put(readmeRange)
        }

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

        // Data Tabs 01_Business to 18_SaleReturnItems
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
            in 200..299 -> {
                // Apply visual formatting & protection metadata (non-fatal)
                applyFormattingAndProtection(spreadsheetId, authHeaders)
                Result.success(Unit)
            }
            401, 403 -> Result.failure(SecurityException("Google API autentikasi ditolak (HTTP ${response.statusCode})"))
            404 -> Result.failure(id.skmnetwork.bukuwarung.backup.SpreadsheetNotFoundException(spreadsheetId))
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
            return@withContext Result.failure(id.skmnetwork.bukuwarung.backup.SpreadsheetNotFoundException(spreadsheetId))
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
                        checksum = metadataMap["checksum"] ?: "",
                        capabilities = metadataMap["capabilities"]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
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

    /**
     * Creates a new Google Spreadsheet with all 19 canonical tabs on the owner's Google Drive.
     */
    suspend fun createSpreadsheet(title: String): Result<String> = withContext(Dispatchers.IO) {
        val tokenResult = authProvider.getAccessToken()
        if (tokenResult.isFailure) {
            return@withContext Result.failure(
                tokenResult.exceptionOrNull() ?: SecurityException("Gagal mendapatkan token autentikasi Google")
            )
        }
        val accessToken = tokenResult.getOrThrow()
        val authHeaders = mapOf(
            "Authorization" to "Bearer $accessToken",
            "Content-Type" to "application/json",
            "Accept" to "application/json"
        )

        val sheetsArray = JSONArray()
        CanonicalSerializer.ALL_TAB_NAMES.forEach { tabName ->
            sheetsArray.put(JSONObject().apply {
                put("properties", JSONObject().apply {
                    put("title", tabName)
                })
            })
        }

        val requestBody = JSONObject().apply {
            put("properties", JSONObject().apply {
                put("title", title)
            })
            put("sheets", sheetsArray)
        }.toString()

        val responseResult = httpEngine.execute("POST", baseUrl, authHeaders, requestBody)
        if (responseResult.isFailure) {
            return@withContext Result.failure(
                responseResult.exceptionOrNull() ?: IOException("Gagal membuat spreadsheet baru di Google Drive")
            )
        }

        val response = responseResult.getOrThrow()
        if (!response.isSuccessful) {
            return@withContext Result.failure(
                IOException("Gagal membuat spreadsheet (HTTP ${response.statusCode}): ${response.body}")
            )
        }

        try {
            val rootJson = JSONObject(response.body)
            val spreadsheetId = rootJson.getString("spreadsheetId")
            Result.success(spreadsheetId)
        } catch (e: Exception) {
            Result.failure(IOException("Gagal membaca ID spreadsheet baru: ${e.message}", e))
        }
    }

    /**
     * Builds Google Sheets v4 formatting requests:
     * - Freezes row 1 on all tabs
     * - Dark header background styling with bold white text and centered alignment
     * - Auto-resizes columns to fit content cleanly
     */
    fun buildFormattingRequests(sheetIdMap: Map<String, Int>): JSONArray {
        val requests = JSONArray()
        sheetIdMap.forEach { (tabName, sheetId) ->
            // 1. Freeze Row 1
            requests.put(JSONObject().apply {
                put("updateSheetProperties", JSONObject().apply {
                    put("properties", JSONObject().apply {
                        put("sheetId", sheetId)
                        put("gridProperties", JSONObject().apply {
                            put("frozenRowCount", 1)
                        })
                    })
                    put("fields", "gridProperties.frozenRowCount")
                })
            })

            // 2. Header styling (Row 0)
            val isReadme = tabName == CanonicalSerializer.README_TAB_NAME
            val headerColor = if (isReadme) {
                // Dark Slate for README overview
                JSONObject().apply { put("red", 0.09); put("green", 0.13); put("blue", 0.24) }
            } else {
                // Elegant Emerald / Dark Teal for Domain Data Tabs
                JSONObject().apply { put("red", 0.06); put("green", 0.40); put("blue", 0.38) }
            }

            requests.put(JSONObject().apply {
                put("repeatCell", JSONObject().apply {
                    put("range", JSONObject().apply {
                        put("sheetId", sheetId)
                        put("startRowIndex", 0)
                        put("endRowIndex", 1)
                    })
                    put("cell", JSONObject().apply {
                        put("userEnteredFormat", JSONObject().apply {
                            put("backgroundColor", headerColor)
                            put("textFormat", JSONObject().apply {
                                put("bold", true)
                                put("foregroundColor", JSONObject().apply {
                                    put("red", 1.0)
                                    put("green", 1.0)
                                    put("blue", 1.0)
                                })
                                put("fontSize", 10)
                            })
                            put("horizontalAlignment", "CENTER")
                        })
                    })
                    put("fields", "userEnteredFormat(backgroundColor,textFormat,horizontalAlignment)")
                })
            })

            // 3. Auto resize columns
            requests.put(JSONObject().apply {
                put("autoResizeDimensions", JSONObject().apply {
                    put("dimensions", JSONObject().apply {
                        put("sheetId", sheetId)
                        put("dimension", "COLUMNS")
                        put("startIndex", 0)
                        put("endIndex", 20)
                    })
                })
            })
        }
        return requests
    }

    /**
     * Builds Google Sheets v4 protection requests:
     * - Adds ProtectedRange on all data tabs (01_Business .. 18_SaleReturnItems) and 00_Metadata
     * - Sets warningOnly = false to prevent accidental/manual modification via Google Sheets UI
     */
    fun buildProtectionRequests(sheetIdMap: Map<String, Int>): JSONArray {
        val requests = JSONArray()
        val protectedTabs = listOf(CanonicalSerializer.METADATA_TAB_NAME) + CanonicalSerializer.DATA_TAB_NAMES
        protectedTabs.forEach { tabName ->
            val sheetId = sheetIdMap[tabName]
            if (sheetId != null) {
                requests.put(JSONObject().apply {
                    put("addProtectedRange", JSONObject().apply {
                        put("protectedRange", JSONObject().apply {
                            put("range", JSONObject().apply {
                                put("sheetId", sheetId)
                            })
                            put("description", "Tab data Buku Warung dilindungi secara otomatis")
                            put("warningOnly", false)
                        })
                    })
                })
            }
        }
        return requests
    }

    /**
     * Queries sheet IDs and executes batch formatting & protection updates.
     * Guaranteed non-fatal so data backup persistence is never blocked.
     */
    suspend fun applyFormattingAndProtection(
        spreadsheetId: String,
        authHeaders: Map<String, String>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val getUrl = "$baseUrl/$spreadsheetId?fields=sheets.properties(sheetId,title)"
            val metaResponse = httpEngine.execute("GET", getUrl, authHeaders, null)
            if (metaResponse.isFailure || !metaResponse.getOrThrow().isSuccessful) {
                return@withContext Result.success(Unit)
            }

            val metaJson = JSONObject(metaResponse.getOrThrow().body)
            val sheetsArray = metaJson.optJSONArray("sheets") ?: return@withContext Result.success(Unit)
            val sheetIdMap = mutableMapOf<String, Int>()
            for (i in 0 until sheetsArray.length()) {
                val prop = sheetsArray.getJSONObject(i).getJSONObject("properties")
                val title = prop.getString("title")
                val id = prop.getInt("sheetId")
                sheetIdMap[title] = id
            }

            val formattingRequests = buildFormattingRequests(sheetIdMap)
            val protectionRequests = buildProtectionRequests(sheetIdMap)

            val combinedRequests = JSONArray()
            for (i in 0 until formattingRequests.length()) {
                combinedRequests.put(formattingRequests.getJSONObject(i))
            }
            for (i in 0 until protectionRequests.length()) {
                combinedRequests.put(protectionRequests.getJSONObject(i))
            }

            if (combinedRequests.length() == 0) return@withContext Result.success(Unit)

            val batchUpdateUrl = "$baseUrl/$spreadsheetId:batchUpdate"
            val requestBody = JSONObject().apply {
                put("requests", combinedRequests)
            }.toString()

            httpEngine.execute("POST", batchUpdateUrl, authHeaders, requestBody)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.success(Unit)
        }
    }
}

