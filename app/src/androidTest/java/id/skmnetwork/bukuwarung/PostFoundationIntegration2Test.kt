package id.skmnetwork.bukuwarung

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.backup.BackupMetadata
import id.skmnetwork.bukuwarung.backup.BackupRestoreManager
import id.skmnetwork.bukuwarung.backup.BackupSnapshot
import id.skmnetwork.bukuwarung.backup.BackupValidator
import id.skmnetwork.bukuwarung.backup.CanonicalSerializer
import id.skmnetwork.bukuwarung.backup.ChecksumMismatchException
import id.skmnetwork.bukuwarung.backup.SheetTab
import id.skmnetwork.bukuwarung.backup.SheetsBackupTransport
import id.skmnetwork.bukuwarung.backup.transport.GoogleAuthCredentialProvider
import id.skmnetwork.bukuwarung.backup.transport.GoogleSheetsApiTransport
import id.skmnetwork.bukuwarung.backup.transport.SheetsHttpEngine
import id.skmnetwork.bukuwarung.backup.transport.SheetsHttpResponse
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import id.skmnetwork.bukuwarung.data.repository.CashRepository
import id.skmnetwork.bukuwarung.data.repository.CustomerRepository
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import id.skmnetwork.bukuwarung.data.repository.SaleRepository
import id.skmnetwork.bukuwarung.data.repository.StockRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

@RunWith(AndroidJUnit4::class)
class PostFoundationIntegration2Test {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var stockRepository: StockRepository
    private lateinit var saleRepository: SaleRepository
    private lateinit var cashRepository: CashRepository
    private lateinit var customerRepository: CustomerRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        userPreferencesRepository = UserPreferencesRepository(context)
        productRepository = ProductRepository(database)
        stockRepository = StockRepository(database)
        saleRepository = SaleRepository(database)
        cashRepository = CashRepository(database)
        customerRepository = CustomerRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * In-memory mock HTTP engine simulating Google Sheets v4 REST API
     */
    private class InMemoryGoogleSheetsHttpEngine : SheetsHttpEngine {
        private val sheetsData = ConcurrentHashMap<String, ConcurrentHashMap<String, List<List<String>>>>()

        var forceStatusCode: Int? = null
        var forceNetworkError: Boolean = false
        var lastRequestBody: String? = null

        override suspend fun execute(
            method: String,
            urlString: String,
            headers: Map<String, String>,
            body: String?
        ): Result<SheetsHttpResponse> {
            if (forceNetworkError) {
                return Result.failure(IOException("Simulated network timeout"))
            }

            if (forceStatusCode != null) {
                return Result.success(
                    SheetsHttpResponse(
                        statusCode = forceStatusCode!!,
                        headers = emptyMap(),
                        body = if (forceStatusCode == 404) "{\"error\": \"Spreadsheet not found\"}" else "{\"error\": \"Unauthorized\"}"
                    )
                )
            }

            val spreadsheetId = urlString.substringAfter("/spreadsheets/").substringBefore('/')

            if (method == "POST" && urlString.endsWith("/values:batchUpdate")) {
                lastRequestBody = body
                if (body != null) {
                    val root = JSONObject(body)
                    val data = root.getJSONArray("data")
                    val spreadsheetMap = sheetsData.computeIfAbsent(spreadsheetId) { ConcurrentHashMap() }

                    for (i in 0 until data.length()) {
                        val item = data.getJSONObject(i)
                        val range = item.getString("range")
                        val tabName = range.substringBefore('!')
                        val valuesJson = item.getJSONArray("values")
                        val rows = mutableListOf<List<String>>()

                        for (r in 0 until valuesJson.length()) {
                            val rowJson = valuesJson.getJSONArray(r)
                            val row = mutableListOf<String>()
                            for (c in 0 until rowJson.length()) {
                                row.add(rowJson.optString(c, ""))
                            }
                            rows.add(row)
                        }
                        spreadsheetMap[tabName] = rows
                    }
                }
                return Result.success(
                    SheetsHttpResponse(
                        statusCode = 200,
                        headers = emptyMap(),
                        body = "{\"spreadsheetId\": \"$spreadsheetId\", \"totalUpdatedRows\": 100}"
                    )
                )
            } else if (method == "GET" && urlString.contains("/values:batchGet")) {
                val spreadsheetMap = sheetsData[spreadsheetId]
                    ?: return Result.success(
                        SheetsHttpResponse(
                            statusCode = 404,
                            headers = emptyMap(),
                            body = "{\"error\": \"Spreadsheet '$spreadsheetId' not found\"}"
                        )
                    )

                val responseJson = JSONObject()
                responseJson.put("spreadsheetId", spreadsheetId)
                val valueRangesArray = JSONArray()

                spreadsheetMap.forEach { (tabName, rows) ->
                    val rangeObj = JSONObject()
                    rangeObj.put("range", "$tabName!A1:Z")
                    val valuesArray = JSONArray()
                    rows.forEach { row ->
                        val rowArray = JSONArray()
                        row.forEach { cell -> rowArray.put(cell) }
                        valuesArray.put(rowArray)
                    }
                    rangeObj.put("values", valuesArray)
                    valueRangesArray.put(rangeObj)
                }

                responseJson.put("valueRanges", valueRangesArray)

                return Result.success(
                    SheetsHttpResponse(
                        statusCode = 200,
                        headers = emptyMap(),
                        body = responseJson.toString()
                    )
                )
            }

            return Result.success(SheetsHttpResponse(statusCode = 200, headers = emptyMap(), body = "{}"))
        }

        fun getSpreadsheetTabs(spreadsheetId: String): Map<String, List<List<String>>>? = sheetsData[spreadsheetId]
    }

    private class TestGoogleAuthCredentialProvider(
        var token: String = "test_valid_oauth_token_12345",
        var shouldFail: Boolean = false
    ) : GoogleAuthCredentialProvider {
        override suspend fun authorizeAccount(email: String): id.skmnetwork.bukuwarung.backup.transport.GoogleAuthConnectionState {
            return if (shouldFail) {
                id.skmnetwork.bukuwarung.backup.transport.GoogleAuthConnectionState.Error("Test error")
            } else {
                id.skmnetwork.bukuwarung.backup.transport.GoogleAuthConnectionState.Connected(email, listOf("https://www.googleapis.com/auth/spreadsheets"))
            }
        }

        override suspend fun handleAuthorizationResult(email: String, data: android.content.Intent?): Result<id.skmnetwork.bukuwarung.backup.transport.GoogleAuthConnectionState.Connected> {
            return if (shouldFail) {
                Result.failure(id.skmnetwork.bukuwarung.backup.transport.GoogleConsentDeniedException())
            } else {
                Result.success(id.skmnetwork.bukuwarung.backup.transport.GoogleAuthConnectionState.Connected(email, listOf("https://www.googleapis.com/auth/spreadsheets")))
            }
        }

        override suspend fun getAccessToken(): Result<String> {
            return if (shouldFail) {
                Result.failure(SecurityException("Simulated OAuth token retrieval error"))
            } else {
                Result.success(token)
            }
        }

        override fun clearToken() {
            // No-op for test provider
        }
    }

    @Test
    fun test1_transportInterfaceCompatibility() {
        val authProvider = TestGoogleAuthCredentialProvider()
        val transport: SheetsBackupTransport = GoogleSheetsApiTransport(authProvider = authProvider)
        assertNotNull(transport)
    }

    @Test
    fun test2_canonical19TabNamesPreserved() = runBlocking {
        val httpEngine = InMemoryGoogleSheetsHttpEngine()
        val authProvider = TestGoogleAuthCredentialProvider()
        val transport = GoogleSheetsApiTransport(authProvider = authProvider, httpEngine = httpEngine)

        val manager = BackupRestoreManager(
            database = database,
            userPreferencesRepository = userPreferencesRepository,
            transport = transport
        )

        val snapshot = manager.exportSnapshot()
        val writeResult = transport.writeBackup("test_spreadsheet_1", snapshot)
        assertTrue("Write backup must succeed", writeResult.isSuccess)

        val savedTabs = httpEngine.getSpreadsheetTabs("test_spreadsheet_1")
        assertNotNull(savedTabs)
        assertEquals(19, savedTabs!!.size)

        val expectedTabs = listOf(
            "00_Metadata", "01_Business", "02_Device", "03_Categories",
            "04_Products", "05_Customers", "06_Sales", "07_SaleItems",
            "08_Purchases", "09_PurchaseItems", "10_Debts", "11_DebtPayments",
            "12_Suppliers", "13_SupplierPayables", "14_SupplierPayments",
            "15_CashTransactions", "16_StockMovements", "17_SaleReturns", "18_SaleReturnItems"
        )
        expectedTabs.forEach { tabName ->
            assertTrue("Tab $tabName must be present", savedTabs.containsKey(tabName))
        }
    }

    @Test
    fun test3_writeReadMatrixPreservation() = runBlocking {
        val httpEngine = InMemoryGoogleSheetsHttpEngine()
        val authProvider = TestGoogleAuthCredentialProvider()
        val transport = GoogleSheetsApiTransport(authProvider = authProvider, httpEngine = httpEngine)

        // Populate products & categories
        val p1 = productRepository.insertProductWithCategory(
            name = "Kecap Manis Cap Bango",
            categoryName = "Sembako",
            purchasePrice = 18000,
            sellingPrice = 22000,
            stock = 15.0,
            minimumStock = 3.0,
            unit = "btl",
            barcode = "899999912345"
        )

        val manager = BackupRestoreManager(
            database = database,
            userPreferencesRepository = userPreferencesRepository,
            transport = transport
        )

        val exportedSnapshot = manager.exportSnapshot()
        val writeResult = transport.writeBackup("test_spreadsheet_preserve", exportedSnapshot)
        assertTrue(writeResult.isSuccess)

        val readResult = transport.readBackup("test_spreadsheet_preserve")
        assertTrue(readResult.isSuccess)
        val readSnapshot = readResult.getOrThrow()

        assertEquals(exportedSnapshot.metadata.checksum, readSnapshot.metadata.checksum)
        assertEquals(exportedSnapshot.metadata.totalRecords, readSnapshot.metadata.totalRecords)

        val productsTab = readSnapshot.getTab("04_Products")
        assertNotNull(productsTab)
        assertEquals(1, productsTab!!.rows.size)
        assertTrue(productsTab.rows[0].contains("Kecap Manis Cap Bango"))
        assertTrue(productsTab.rows[0].contains("899999912345"))
    }

    @Test
    fun test4_metadataPreservation() = runBlocking {
        val httpEngine = InMemoryGoogleSheetsHttpEngine()
        val authProvider = TestGoogleAuthCredentialProvider()
        val transport = GoogleSheetsApiTransport(authProvider = authProvider, httpEngine = httpEngine)

        val manager = BackupRestoreManager(
            database = database,
            userPreferencesRepository = userPreferencesRepository,
            transport = transport
        )

        val exportedSnapshot = manager.exportSnapshot()
        val original = exportedSnapshot.metadata
        transport.writeBackup("test_meta_spreadsheet", exportedSnapshot)

        val readSnapshot = transport.readBackup("test_meta_spreadsheet").getOrThrow()
        val restoredMeta = readSnapshot.metadata

        assertEquals(original.backupFormatVersion, restoredMeta.backupFormatVersion)
        assertEquals(original.roomSchemaVersion, restoredMeta.roomSchemaVersion)
        assertEquals(original.businessId, restoredMeta.businessId)
        assertEquals(original.deviceId, restoredMeta.deviceId)
        assertEquals(original.appVersion, restoredMeta.appVersion)
        assertEquals(original.checksum, restoredMeta.checksum)
    }

    @Test
    fun test5_checksumPreservation() = runBlocking {
        val httpEngine = InMemoryGoogleSheetsHttpEngine()
        val authProvider = TestGoogleAuthCredentialProvider()
        val transport = GoogleSheetsApiTransport(authProvider = authProvider, httpEngine = httpEngine)

        val manager = BackupRestoreManager(
            database = database,
            userPreferencesRepository = userPreferencesRepository,
            transport = transport
        )

        val snapshot = manager.exportSnapshot()
        transport.writeBackup("test_checksum_spreadsheet", snapshot)
        val readSnapshot = transport.readBackup("test_checksum_spreadsheet").getOrThrow()

        // Validate via BackupValidator
        try {
            BackupValidator.validate(readSnapshot)
        } catch (e: Exception) {
            fail("Checksum validation must pass: ${e.message}")
        }
    }

    @Test
    fun test6_malformedRemoteDataRejection() = runBlocking {
        val httpEngine = InMemoryGoogleSheetsHttpEngine()
        val authProvider = TestGoogleAuthCredentialProvider()
        val transport = GoogleSheetsApiTransport(authProvider = authProvider, httpEngine = httpEngine)

        val emptySpreadsheetId = "corrupted_sheet_no_meta"
        val readResult = transport.readBackup(emptySpreadsheetId)
        assertTrue("Read from non-existent spreadsheet must fail", readResult.isFailure)
    }

    @Test
    fun test7_networkFailureIsolation() = runBlocking {
        val httpEngine = InMemoryGoogleSheetsHttpEngine()
        httpEngine.forceNetworkError = true
        val authProvider = TestGoogleAuthCredentialProvider()
        val transport = GoogleSheetsApiTransport(authProvider = authProvider, httpEngine = httpEngine)

        val manager = BackupRestoreManager(
            database = database,
            userPreferencesRepository = userPreferencesRepository,
            transport = transport
        )

        val snapshot = manager.exportSnapshot()
        val writeResult = transport.writeBackup("test_network_err", snapshot)
        assertTrue(writeResult.isFailure)
        assertTrue(writeResult.exceptionOrNull() is IOException)

        // Room DB must remain unaffected
        val categories = database.categoryDao().getAllCategories().first()
        assertNotNull(categories)
    }

    @Test
    fun test8_authenticationFailureHandling() = runBlocking {
        val httpEngine = InMemoryGoogleSheetsHttpEngine()
        val authProvider = TestGoogleAuthCredentialProvider(shouldFail = true)
        val transport = GoogleSheetsApiTransport(authProvider = authProvider, httpEngine = httpEngine)

        val manager = BackupRestoreManager(
            database = database,
            userPreferencesRepository = userPreferencesRepository,
            transport = transport
        )

        val snapshot = manager.exportSnapshot()
        val writeResult = transport.writeBackup("test_auth_fail", snapshot)
        assertTrue(writeResult.isFailure)
        assertTrue(writeResult.exceptionOrNull() is SecurityException)
    }

    @Test
    fun test9_spreadsheetNotFoundHandling() = runBlocking {
        val httpEngine = InMemoryGoogleSheetsHttpEngine()
        httpEngine.forceStatusCode = 404
        val authProvider = TestGoogleAuthCredentialProvider()
        val transport = GoogleSheetsApiTransport(authProvider = authProvider, httpEngine = httpEngine)

        val readResult = transport.readBackup("missing_sheet_404")
        assertTrue(readResult.isFailure)
        assertTrue(readResult.exceptionOrNull() is IOException)
    }

    @Test
    fun test10_partialEmptySheetHandling() = runBlocking {
        val httpEngine = InMemoryGoogleSheetsHttpEngine()
        val authProvider = TestGoogleAuthCredentialProvider()
        val transport = GoogleSheetsApiTransport(authProvider = authProvider, httpEngine = httpEngine)

        // Export empty database
        val manager = BackupRestoreManager(
            database = database,
            userPreferencesRepository = userPreferencesRepository,
            transport = transport
        )

        val snapshot = manager.exportSnapshot()
        val writeResult = transport.writeBackup("test_empty_db", snapshot)
        assertTrue(writeResult.isSuccess)

        val readSnapshot = transport.readBackup("test_empty_db").getOrThrow()
        // DataStore business profile and device profile generate 1 row each (total 2 records)
        assertEquals(2, readSnapshot.metadata.totalRecords)

        val salesTab = readSnapshot.getTab("06_Sales")
        assertNotNull(salesTab)
        assertEquals(0, salesTab!!.rows.size)
    }

    @Test
    fun test11_backupDoesNotMutateRoom() = runBlocking {
        val p1 = productRepository.insertProductWithCategory(
            name = "Gula Pasir 1kg",
            categoryName = "Sembako",
            purchasePrice = 12000,
            sellingPrice = 15000,
            stock = 20.0,
            minimumStock = 5.0,
            unit = "kg"
        )

        val productsBefore = database.productDao().getAllProducts().first()
        val countBefore = productsBefore.size

        val httpEngine = InMemoryGoogleSheetsHttpEngine()
        val authProvider = TestGoogleAuthCredentialProvider()
        val transport = GoogleSheetsApiTransport(authProvider = authProvider, httpEngine = httpEngine)

        val manager = BackupRestoreManager(
            database = database,
            userPreferencesRepository = userPreferencesRepository,
            transport = transport
        )

        val snapshot = manager.exportSnapshot()
        val writeResult = transport.writeBackup("test_no_mutation", snapshot)
        assertTrue(writeResult.isSuccess)

        val productsAfter = database.productDao().getAllProducts().first()
        assertEquals(countBefore, productsAfter.size)
        assertEquals(20.0, productsAfter[0].stock, 0.001)
    }

    @Test
    fun test12_restoreValidationFailureDoesNotMutateRoom() = runBlocking {
        val p1 = productRepository.insertProductWithCategory(
            name = "Beras Premium 5kg",
            categoryName = "Sembako",
            purchasePrice = 65000,
            sellingPrice = 75000,
            stock = 10.0,
            minimumStock = 2.0,
            unit = "sak"
        )

        val httpEngine = InMemoryGoogleSheetsHttpEngine()
        val authProvider = TestGoogleAuthCredentialProvider()
        val transport = GoogleSheetsApiTransport(authProvider = authProvider, httpEngine = httpEngine)

        val manager = BackupRestoreManager(
            database = database,
            userPreferencesRepository = userPreferencesRepository,
            transport = transport
        )
        val snapshot = manager.exportSnapshot()
        transport.writeBackup("test_corrupt_restore", snapshot)

        // Corrupt checksum in remote storage
        val remoteTabs = httpEngine.getSpreadsheetTabs("test_corrupt_restore") as ConcurrentHashMap<String, List<List<String>>>
        val metaRows = remoteTabs["00_Metadata"]!!.map { row ->
            if (row.getOrNull(0) == "checksum") listOf("checksum", "TAMPERED_CHECKSUM_HASH") else row
        }
        remoteTabs["00_Metadata"] = metaRows

        // Attempt restore
        val readSnapshot = transport.readBackup("test_corrupt_restore").getOrThrow()
        val restoreResult = manager.restoreSnapshot(readSnapshot)
        assertTrue("Corrupted backup must fail validation before Room restore", restoreResult.isFailure)
        assertTrue(restoreResult.exceptionOrNull() is ChecksumMismatchException)

        // Verify Room was NOT cleared or mutated
        val products = database.productDao().getAllProducts().first()
        assertEquals(1, products.size)
        assertEquals("Beras Premium 5kg", products[0].name)
    }
}
