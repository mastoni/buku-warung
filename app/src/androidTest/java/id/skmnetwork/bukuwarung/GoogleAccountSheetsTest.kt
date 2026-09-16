package id.skmnetwork.bukuwarung

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.backup.BackupRestoreManager
import id.skmnetwork.bukuwarung.backup.MockSheetsTransport
import id.skmnetwork.bukuwarung.backup.transport.GoogleAuthConnectionState
import id.skmnetwork.bukuwarung.backup.transport.GoogleSheetsApiTransport
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import id.skmnetwork.bukuwarung.data.preferences.dataStore
import id.skmnetwork.bukuwarung.ui.settings.BackupOpState
import id.skmnetwork.bukuwarung.ui.settings.BackupViewModel
import id.skmnetwork.bukuwarung.ui.settings.RestoreOpState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Gate F.2 — Modern Google Account & Authorization Test Suite.
 * Covers all 12 critical authorization, resolution, token safety, error mapping, and offline-first boundaries.
 */
@RunWith(AndroidJUnit4::class)
class GoogleAccountSheetsTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var mockTransport: MockSheetsTransport
    private lateinit var mockAuthProvider: MockGoogleAuthCredentialProvider
    private lateinit var backupRestoreManager: BackupRestoreManager
    private lateinit var backupViewModel: BackupViewModel

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        userPreferencesRepository = UserPreferencesRepository(context)
        context.dataStore.edit { it.clear() }

        mockTransport = MockSheetsTransport()
        mockAuthProvider = MockGoogleAuthCredentialProvider()

        val mockIntent = Intent(context, MainActivity::class.java)
        mockAuthProvider.mockPendingIntent = PendingIntent.getActivity(
            context, 0, mockIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        backupRestoreManager = BackupRestoreManager(
            database = database,
            userPreferencesRepository = userPreferencesRepository,
            transport = mockTransport
        )

        withContext(Dispatchers.Main) {
            backupViewModel = BackupViewModel(
                backupRestoreManager = backupRestoreManager,
                userPreferencesRepository = userPreferencesRepository,
                authProvider = mockAuthProvider
            )
        }
    }

    @After
    fun tearDown() = runBlocking {
        context.dataStore.edit { it.clear() }
        database.close()
    }

    @Test
    fun test1_disconnectedState_initialIsDisconnected() = runBlocking {
        val settings = userPreferencesRepository.userSettings.first()
        assertTrue("Initial email must be empty", settings.googleAccountEmail.isBlank())
        assertTrue("Initial spreadsheet ID must be empty", settings.backupSpreadsheetId.isBlank())
        assertEquals(GoogleAuthConnectionState.Disconnected, backupViewModel.googleAuthState.value)
    }

    @Test
    fun test2_accountSelectionDoesNotMeanAuthorized_untilAuthSucceeds() = runBlocking {
        // Configure auth provider to require resolution (consent)
        mockAuthProvider.simulateResolutionRequired = true

        withContext(Dispatchers.Main) {
            backupViewModel.initiateAccountAuthorization("owner@gmail.com")
        }
        delay(100)

        // Email in DataStore must NOT be set yet
        var attempts = 0
        while (backupViewModel.googleAuthState.value !is GoogleAuthConnectionState.AuthorizationRequired && attempts < 100) {
            delay(50)
            attempts++
        }
        val settings = userPreferencesRepository.userSettings.first()
        assertTrue("Email must not be saved when resolution is pending", settings.googleAccountEmail.isBlank())
        assertTrue(backupViewModel.googleAuthState.value is GoogleAuthConnectionState.AuthorizationRequired)
    }

    @Test
    fun test3_authorizationSuccess_setsConnectedAndPersistsEmail() = runBlocking {
        mockAuthProvider.simulateResolutionRequired = false

        withContext(Dispatchers.Main) {
            backupViewModel.initiateAccountAuthorization("warung.barokah@gmail.com")
        }
        var attempts = 0
        while ((backupViewModel.googleAuthState.value !is GoogleAuthConnectionState.Connected ||
                userPreferencesRepository.userSettings.first().googleAccountEmail.isBlank()) && attempts < 100) {
            delay(50)
            attempts++
        }

        val settings = userPreferencesRepository.userSettings.first()
        assertEquals("warung.barokah@gmail.com", settings.googleAccountEmail)
        val state = backupViewModel.googleAuthState.value
        assertTrue("State must be Connected", state is GoogleAuthConnectionState.Connected)
        assertEquals("warung.barokah@gmail.com", (state as GoogleAuthConnectionState.Connected).email)
    }

    @Test
    fun test4_authorizationCancellation_doesNotSetConnected() = runBlocking {
        mockAuthProvider.simulateConsentDenied = true

        withContext(Dispatchers.Main) {
            backupViewModel.initiateAccountAuthorization("cancelled@gmail.com")
        }
        var attempts = 0
        while (backupViewModel.googleAuthState.value !is GoogleAuthConnectionState.Error && attempts < 100) {
            delay(50)
            attempts++
        }

        val settings = userPreferencesRepository.userSettings.first()
        assertTrue("Cancelled auth must not persist email in DataStore", settings.googleAccountEmail.isBlank())
        assertTrue("State must be Error", backupViewModel.googleAuthState.value is GoogleAuthConnectionState.Error)
        val errorState = backupViewModel.googleAuthState.value as GoogleAuthConnectionState.Error
        assertTrue(errorState.message.contains("Izin Google belum diberikan"))
    }

    @Test
    fun test5_requiredScopesVerified() = runBlocking {
        withContext(Dispatchers.Main) {
            backupViewModel.initiateAccountAuthorization("scopes@gmail.com")
        }
        var attempts = 0
        while (backupViewModel.googleAuthState.value !is GoogleAuthConnectionState.Connected && attempts < 100) {
            delay(50)
            attempts++
        }

        val state = backupViewModel.googleAuthState.value as GoogleAuthConnectionState.Connected
        assertTrue(state.grantedScopes.contains("https://www.googleapis.com/auth/spreadsheets"))
        assertTrue(state.grantedScopes.contains("https://www.googleapis.com/auth/drive.file"))
    }

    @Test
    fun test6_tokenNotPersistedInDataStore() = runBlocking {
        withContext(Dispatchers.Main) {
            backupViewModel.initiateAccountAuthorization("security@gmail.com")
        }
        var attempts = 0
        while (userPreferencesRepository.userSettings.first().googleAccountEmail.isBlank() && attempts < 100) {
            delay(50)
            attempts++
        }

        val settings = userPreferencesRepository.userSettings.first()
        assertEquals("security@gmail.com", settings.googleAccountEmail)

        // Verify DataStore keys do not have any token
        val allPrefs = context.dataStore.data.first().asMap()
        for ((key, value) in allPrefs) {
            assertFalse("Token must not be stored in key ${key.name}", key.name.contains("token", ignoreCase = true))
            assertFalse("Token must not be stored in value", value.toString().contains("MOCK_ACCESS_TOKEN"))
        }
    }

    @Test
    fun test7_backupTimestampOnlyAfterSuccessfulBackup() = runBlocking {
        val testEmail = "lestari@gmail.com"
        userPreferencesRepository.setGoogleAccount(testEmail)
        assertEquals(0L, userPreferencesRepository.userSettings.first().lastBackupTimestamp)

        // Fail backup first
        mockTransport.simulateNetworkFailure = true
        withContext(Dispatchers.Main) {
            backupViewModel.performBackup("TEST_FAIL_SHEET")
        }
        var attempts = 0
        while (backupViewModel.backupState.value !is BackupOpState.Error && attempts < 40) {
            delay(50)
            attempts++
        }
        assertEquals(0L, userPreferencesRepository.userSettings.first().lastBackupTimestamp)

        // Succeed backup
        mockTransport.simulateNetworkFailure = false
        withContext(Dispatchers.Main) {
            backupViewModel.performBackup("TEST_PASS_SHEET")
        }
        attempts = 0
        while (backupViewModel.backupState.value !is BackupOpState.Success && attempts < 50) {
            delay(50)
            attempts++
        }
        attempts = 0
        while (userPreferencesRepository.userSettings.first().lastBackupTimestamp == 0L && attempts < 40) {
            delay(50)
            attempts++
        }
        val settings = userPreferencesRepository.userSettings.first()
        assertTrue("Timestamp must be updated after successful backup", settings.lastBackupTimestamp > 0L)
    }

    @Test
    fun test8_error401Handling_mappedToFriendlyReauthorizationMessage() = runBlocking {
        userPreferencesRepository.setGoogleAccount("auth401@gmail.com")
        mockAuthProvider.simulate401Error = true

        val sheetsTransport = GoogleSheetsApiTransport(mockAuthProvider)
        val testMgr = BackupRestoreManager(database, userPreferencesRepository, sheetsTransport)
        val vm = withContext(Dispatchers.Main) {
            BackupViewModel(testMgr, userPreferencesRepository, mockAuthProvider)
        }

        withContext(Dispatchers.Main) {
            vm.performBackup("SHEET_401")
        }
        var attempts = 0
        while (vm.backupState.value !is BackupOpState.Error && attempts < 20) {
            delay(100)
            attempts++
        }
        val error = vm.backupState.value as BackupOpState.Error
        assertTrue("Error 401 must prompt to re-authorize: ${error.message}", error.message.contains("diperbarui") || error.message.contains("Hubungkan kembali"))
    }

    @Test
    fun test9_error403Handling_mappedToFriendlyAccessDeniedMessage() = runBlocking {
        userPreferencesRepository.setGoogleAccount("auth403@gmail.com")
        mockAuthProvider.simulate403Error = true

        val sheetsTransport = GoogleSheetsApiTransport(mockAuthProvider)
        val testMgr = BackupRestoreManager(database, userPreferencesRepository, sheetsTransport)
        val vm = withContext(Dispatchers.Main) {
            BackupViewModel(testMgr, userPreferencesRepository, mockAuthProvider)
        }

        withContext(Dispatchers.Main) {
            vm.performBackup("SHEET_403")
        }
        var attempts = 0
        while (vm.backupState.value !is BackupOpState.Error && attempts < 20) {
            delay(100)
            attempts++
        }
        val error = vm.backupState.value as BackupOpState.Error
        assertTrue("Error 403 must indicate access denied: ${error.message}", error.message.contains("ditolak") || error.message.contains("izin"))
    }

    @Test
    fun test10_error404Handling_mappedToFriendlySpreadsheetNotFoundMessage() = runBlocking {
        userPreferencesRepository.setGoogleAccount("auth404@gmail.com")

        // Use mock engine returning 404
        val sheetsTransport = GoogleSheetsApiTransport(
            authProvider = mockAuthProvider,
            httpEngine = object : id.skmnetwork.bukuwarung.backup.transport.SheetsHttpEngine {
                override suspend fun execute(
                    method: String,
                    urlString: String,
                    headers: Map<String, String>,
                    body: String?
                ): Result<id.skmnetwork.bukuwarung.backup.transport.SheetsHttpResponse> {
                    return Result.success(
                        id.skmnetwork.bukuwarung.backup.transport.SheetsHttpResponse(
                            statusCode = 404,
                            headers = emptyMap(),
                            body = "{\"error\":{\"message\":\"Spreadsheet not found\"}}"
                        )
                    )
                }
            }
        )
        val testMgr = BackupRestoreManager(database, userPreferencesRepository, sheetsTransport)
        val vm = withContext(Dispatchers.Main) {
            BackupViewModel(testMgr, userPreferencesRepository, mockAuthProvider)
        }

        withContext(Dispatchers.Main) {
            vm.performBackup("NONEXISTENT_SHEET")
        }
        var attempts = 0
        while (vm.backupState.value !is BackupOpState.Error && attempts < 20) {
            delay(100)
            attempts++
        }
        val error = vm.backupState.value as BackupOpState.Error
        assertTrue("Error 404 must indicate spreadsheet not found: ${error.message}", error.message.contains("tidak ditemukan"))
    }

    @Test
    fun test11_errorOfflineHandling_mappedToFriendlyNetworkMessage() = runBlocking {
        userPreferencesRepository.setGoogleAccount("offline@gmail.com")
        mockAuthProvider.simulateNetworkError = true

        val sheetsTransport = GoogleSheetsApiTransport(mockAuthProvider)
        val testMgr = BackupRestoreManager(database, userPreferencesRepository, sheetsTransport)
        val vm = withContext(Dispatchers.Main) {
            BackupViewModel(testMgr, userPreferencesRepository, mockAuthProvider)
        }

        withContext(Dispatchers.Main) {
            vm.performBackup("SHEET_OFFLINE")
        }
        var attempts = 0
        while (vm.backupState.value !is BackupOpState.Error && attempts < 20) {
            delay(100)
            attempts++
        }
        val error = vm.backupState.value as BackupOpState.Error
        assertTrue("Offline error must mention connection: ${error.message}", error.message.contains("koneksi") || error.message.contains("internet"))
    }

    @Test
    fun test12_spreadsheetMetadataPersistenceAndOpenUrl() = runBlocking {
        val testEmail = "metadata@gmail.com"
        val testSpreadsheetId = "1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms"
        val testSpreadsheetName = "Buku Warung - Warung Barokah"
        val timestamp = System.currentTimeMillis()

        userPreferencesRepository.updateBackupInfo(
            lastBackupTimestamp = timestamp,
            backupSpreadsheetId = testSpreadsheetId,
            backupSpreadsheetName = testSpreadsheetName,
            googleAccountEmail = testEmail
        )

        val settings = userPreferencesRepository.userSettings.first()
        assertEquals(testEmail, settings.googleAccountEmail)
        assertEquals(testSpreadsheetId, settings.backupSpreadsheetId)
        assertEquals(testSpreadsheetName, settings.backupSpreadsheetName)
        assertEquals(timestamp, settings.lastBackupTimestamp)

        val url = backupViewModel.getSpreadsheetWebUrl(testSpreadsheetId)
        assertEquals("https://docs.google.com/spreadsheets/d/1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms", url)
    }

    @Test
    fun test13_accountDisconnect_clearsMetadataWithoutTouchingRoomDatabase() = runBlocking {
        val category = CategoryEntity(businessId = "TEST_BIZ", name = "Sembako")
        val categoryId = database.categoryDao().insertCategory(category)

        val product = ProductEntity(
            businessId = "TEST_BIZ",
            categoryId = categoryId,
            name = "Beras Rojolele 5kg",
            purchasePrice = 60000L,
            sellingPrice = 70000L,
            stock = 10.0,
            minimumStock = 2.0,
            unit = "PCS",
            itemType = "BARANG"
        )
        val insertedId = database.productDao().insertProduct(product)
        assertTrue(insertedId > 0)

        userPreferencesRepository.setGoogleAccount("test@gmail.com")
        userPreferencesRepository.updateBackupInfo(
            lastBackupTimestamp = System.currentTimeMillis(),
            backupSpreadsheetId = "SHEET_123",
            backupSpreadsheetName = "Buku Warung - Barokah",
            googleAccountEmail = "test@gmail.com"
        )

        withContext(Dispatchers.Main) {
            backupViewModel.disconnectGoogleAccount()
        }
        delay(100)

        val settings = userPreferencesRepository.userSettings.first()
        assertTrue("Email must be cleared", settings.googleAccountEmail.isBlank())
        assertTrue("Spreadsheet ID must be cleared", settings.backupSpreadsheetId.isBlank())
        assertTrue("Spreadsheet Name must be cleared", settings.backupSpreadsheetName.isBlank())
        assertEquals(GoogleAuthConnectionState.Disconnected, backupViewModel.googleAuthState.value)

        // Verify Room Database is completely untouched
        val retrievedProduct = database.productDao().getProductByIdRaw(insertedId)
        assertNotNull("Product in Room must remain intact after account disconnect", retrievedProduct)
        assertEquals("Beras Rojolele 5kg", retrievedProduct!!.name)
    }

    @Test
    fun test14_offlinePos_functionsCompletelyWithoutGoogleAccount() = runBlocking {
        userPreferencesRepository.clearGoogleAccount()
        val settings = userPreferencesRepository.userSettings.first()
        assertTrue(settings.googleAccountEmail.isBlank())

        val category = CategoryEntity(businessId = "OFFLINE_BIZ", name = "Minyak")
        val categoryId = database.categoryDao().insertCategory(category)

        val product = ProductEntity(
            businessId = "OFFLINE_BIZ",
            categoryId = categoryId,
            name = "Minyak Goreng 2L",
            purchasePrice = 28000L,
            sellingPrice = 32000L,
            stock = 20.0,
            minimumStock = 5.0,
            unit = "BTL",
            itemType = "BARANG"
        )
        val prodId = database.productDao().insertProduct(product)
        val saved = database.productDao().getProductByIdRaw(prodId)

        assertNotNull(saved)
        assertEquals(20.0, saved!!.stock, 0.001)
        assertEquals(32000L, saved.sellingPrice)
    }

    // ==========================================
    // GATE F.2-R3: SAFE 404 AUTO-HEALING TESTS
    // ==========================================

    private class AutoHealingMockHttpEngine : id.skmnetwork.bukuwarung.backup.transport.SheetsHttpEngine {
        var createCount = 0
        var writeCount = 0
        var createdSpreadsheetId = "NEW_CREATED_SPREADSHEET_123"
        var createFailsWith: Int? = null
        var createFailsWithNetwork: Boolean = false
        var writeFailsForIdMap = mutableMapOf<String, Int>()
        var writeFailsWithNetwork = false

        override suspend fun execute(
            method: String,
            urlString: String,
            headers: Map<String, String>,
            body: String?
        ): Result<id.skmnetwork.bukuwarung.backup.transport.SheetsHttpResponse> {
            if (method == "POST" && !urlString.contains("/values:")) {
                createCount++
                if (createFailsWithNetwork) {
                    return Result.failure(java.io.IOException("Create network failed"))
                }
                if (createFailsWith != null) {
                    return Result.success(
                        id.skmnetwork.bukuwarung.backup.transport.SheetsHttpResponse(
                            statusCode = createFailsWith!!,
                            headers = emptyMap(),
                            body = "{\"error\": \"Create failed\"}"
                        )
                    )
                }
                return Result.success(
                    id.skmnetwork.bukuwarung.backup.transport.SheetsHttpResponse(
                        statusCode = 200,
                        headers = emptyMap(),
                        body = "{\"spreadsheetId\": \"$createdSpreadsheetId\"}"
                    )
                )
            }

            if (method == "POST" && urlString.contains("/values:batchUpdate")) {
                writeCount++
                if (writeFailsWithNetwork) {
                    return Result.failure(java.io.IOException("Write network failed"))
                }
                val idInUrl = urlString.substringAfter("/spreadsheets/").substringBefore('/')
                val failCode = writeFailsForIdMap[idInUrl]
                if (failCode != null) {
                    return Result.success(
                        id.skmnetwork.bukuwarung.backup.transport.SheetsHttpResponse(
                            statusCode = failCode,
                            headers = emptyMap(),
                            body = "{\"error\": \"Status $failCode for $idInUrl\"}"
                        )
                    )
                }
                return Result.success(
                    id.skmnetwork.bukuwarung.backup.transport.SheetsHttpResponse(
                        statusCode = 200,
                        headers = emptyMap(),
                        body = "{\"spreadsheetId\": \"$idInUrl\", \"totalUpdatedRows\": 10}"
                    )
                )
            }

            return Result.success(
                id.skmnetwork.bukuwarung.backup.transport.SheetsHttpResponse(
                    statusCode = 200,
                    headers = emptyMap(),
                    body = "{}"
                )
            )
        }
    }

    @Test
    fun test15_validExistingSpreadsheet_writeSuccess_noCreate() = runBlocking {
        val httpEngine = AutoHealingMockHttpEngine()
        val transport = GoogleSheetsApiTransport(mockAuthProvider, httpEngine)
        val manager = BackupRestoreManager(database, userPreferencesRepository, transport)
        val vm = withContext(Dispatchers.Main) {
            BackupViewModel(manager, userPreferencesRepository, mockAuthProvider)
        }

        userPreferencesRepository.setGoogleAccount("test@gmail.com")
        userPreferencesRepository.updateBackupInfo(
            lastBackupTimestamp = 0L,
            backupSpreadsheetId = "VALID_SHEET_999",
            backupSpreadsheetName = "Buku Warung - Test",
            googleAccountEmail = "test@gmail.com"
        )

        withContext(Dispatchers.Main) {
            vm.performBackup()
        }
        var attempts = 0
        while (vm.backupState.value !is BackupOpState.Success && attempts < 20) {
            delay(100)
            attempts++
        }

        assertEquals(0, httpEngine.createCount)
        assertEquals(1, httpEngine.writeCount)
        assertTrue(vm.backupState.value is BackupOpState.Success)

        val settings = userPreferencesRepository.userSettings.first()
        assertEquals("VALID_SHEET_999", settings.backupSpreadsheetId)
        assertTrue(settings.lastBackupTimestamp > 0L)
    }

    @Test
    fun test16_staleSpreadsheetId_writeReturns404_autoHealsAndCreatesNewSpreadsheet() = runBlocking {
        val httpEngine = AutoHealingMockHttpEngine()
        httpEngine.writeFailsForIdMap["STALE_OLD_ID"] = 404
        val transport = GoogleSheetsApiTransport(mockAuthProvider, httpEngine)
        val manager = BackupRestoreManager(database, userPreferencesRepository, transport)
        val vm = withContext(Dispatchers.Main) {
            BackupViewModel(manager, userPreferencesRepository, mockAuthProvider)
        }

        userPreferencesRepository.setGoogleAccount("test@gmail.com")
        userPreferencesRepository.updateBackupInfo(
            lastBackupTimestamp = 0L,
            backupSpreadsheetId = "STALE_OLD_ID",
            backupSpreadsheetName = "Buku Warung - Test",
            googleAccountEmail = "test@gmail.com"
        )

        withContext(Dispatchers.Main) {
            vm.performBackup()
        }
        var attempts = 0
        while (vm.backupState.value !is BackupOpState.Success && attempts < 20) {
            delay(100)
            attempts++
        }

        assertEquals(1, httpEngine.createCount)
        assertEquals(2, httpEngine.writeCount) // 1 for STALE_OLD_ID (failed with 404) + 1 for NEW_CREATED_SPREADSHEET_123 (success)
        assertTrue(vm.backupState.value is BackupOpState.Success)

        val settings = userPreferencesRepository.userSettings.first()
        assertEquals("NEW_CREATED_SPREADSHEET_123", settings.backupSpreadsheetId)
        assertTrue(settings.lastBackupTimestamp > 0L)
    }

    @Test
    fun test17_staleSpreadsheetId_createFails_preservesOldIdAndDoesNotUpdateTimestamp() = runBlocking {
        val httpEngine = AutoHealingMockHttpEngine()
        httpEngine.writeFailsForIdMap["STALE_OLD_ID"] = 404
        httpEngine.createFailsWith = 500
        val transport = GoogleSheetsApiTransport(mockAuthProvider, httpEngine)
        val manager = BackupRestoreManager(database, userPreferencesRepository, transport)
        val vm = withContext(Dispatchers.Main) {
            BackupViewModel(manager, userPreferencesRepository, mockAuthProvider)
        }

        userPreferencesRepository.setGoogleAccount("test@gmail.com")
        userPreferencesRepository.updateBackupInfo(
            lastBackupTimestamp = 0L,
            backupSpreadsheetId = "STALE_OLD_ID",
            backupSpreadsheetName = "Buku Warung - Test",
            googleAccountEmail = "test@gmail.com"
        )

        withContext(Dispatchers.Main) {
            vm.performBackup()
        }
        var attempts = 0
        while (vm.backupState.value !is BackupOpState.Error && attempts < 20) {
            delay(100)
            attempts++
        }

        assertTrue(vm.backupState.value is BackupOpState.Error)
        val settings = userPreferencesRepository.userSettings.first()
        assertEquals("STALE_OLD_ID", settings.backupSpreadsheetId)
        assertEquals(0L, settings.lastBackupTimestamp)
    }

    @Test
    fun test18_staleSpreadsheetId_retryBackupFails_doesNotUpdateTimestamp() = runBlocking {
        val httpEngine = AutoHealingMockHttpEngine()
        httpEngine.writeFailsForIdMap["STALE_OLD_ID"] = 404
        httpEngine.writeFailsForIdMap["NEW_CREATED_SPREADSHEET_123"] = 500
        val transport = GoogleSheetsApiTransport(mockAuthProvider, httpEngine)
        val manager = BackupRestoreManager(database, userPreferencesRepository, transport)
        val vm = withContext(Dispatchers.Main) {
            BackupViewModel(manager, userPreferencesRepository, mockAuthProvider)
        }

        userPreferencesRepository.setGoogleAccount("test@gmail.com")
        userPreferencesRepository.updateBackupInfo(
            lastBackupTimestamp = 0L,
            backupSpreadsheetId = "STALE_OLD_ID",
            backupSpreadsheetName = "Buku Warung - Test",
            googleAccountEmail = "test@gmail.com"
        )

        withContext(Dispatchers.Main) {
            vm.performBackup()
        }
        var attempts = 0
        while (vm.backupState.value !is BackupOpState.Error && attempts < 20) {
            delay(100)
            attempts++
        }

        assertTrue(vm.backupState.value is BackupOpState.Error)
        val settings = userPreferencesRepository.userSettings.first()
        assertEquals(0L, settings.lastBackupTimestamp)
    }

    @Test
    fun test19_http401_doesNotCreateSpreadsheet() = runBlocking {
        val httpEngine = AutoHealingMockHttpEngine()
        httpEngine.writeFailsForIdMap["SHEET_401"] = 401
        val transport = GoogleSheetsApiTransport(mockAuthProvider, httpEngine)
        val manager = BackupRestoreManager(database, userPreferencesRepository, transport)
        val vm = withContext(Dispatchers.Main) {
            BackupViewModel(manager, userPreferencesRepository, mockAuthProvider)
        }

        userPreferencesRepository.setGoogleAccount("test@gmail.com")
        userPreferencesRepository.updateBackupInfo(
            lastBackupTimestamp = 0L,
            backupSpreadsheetId = "SHEET_401",
            backupSpreadsheetName = "Buku Warung - Test",
            googleAccountEmail = "test@gmail.com"
        )

        withContext(Dispatchers.Main) {
            vm.performBackup()
        }
        var attempts = 0
        while (vm.backupState.value !is BackupOpState.Error && attempts < 20) {
            delay(100)
            attempts++
        }

        assertEquals(0, httpEngine.createCount)
        assertTrue(vm.backupState.value is BackupOpState.Error)
        val settings = userPreferencesRepository.userSettings.first()
        assertEquals(0L, settings.lastBackupTimestamp)
    }

    @Test
    fun test20_http403_doesNotCreateSpreadsheet() = runBlocking {
        val httpEngine = AutoHealingMockHttpEngine()
        httpEngine.writeFailsForIdMap["SHEET_403"] = 403
        val transport = GoogleSheetsApiTransport(mockAuthProvider, httpEngine)
        val manager = BackupRestoreManager(database, userPreferencesRepository, transport)
        val vm = withContext(Dispatchers.Main) {
            BackupViewModel(manager, userPreferencesRepository, mockAuthProvider)
        }

        userPreferencesRepository.setGoogleAccount("test@gmail.com")
        userPreferencesRepository.updateBackupInfo(
            lastBackupTimestamp = 0L,
            backupSpreadsheetId = "SHEET_403",
            backupSpreadsheetName = "Buku Warung - Test",
            googleAccountEmail = "test@gmail.com"
        )

        withContext(Dispatchers.Main) {
            vm.performBackup()
        }
        var attempts = 0
        while (vm.backupState.value !is BackupOpState.Error && attempts < 20) {
            delay(100)
            attempts++
        }

        assertEquals(0, httpEngine.createCount)
        assertTrue(vm.backupState.value is BackupOpState.Error)
        val settings = userPreferencesRepository.userSettings.first()
        assertEquals(0L, settings.lastBackupTimestamp)
    }

    @Test
    fun test21_networkOffline_doesNotCreateSpreadsheet() = runBlocking {
        val httpEngine = AutoHealingMockHttpEngine()
        httpEngine.writeFailsWithNetwork = true
        val transport = GoogleSheetsApiTransport(mockAuthProvider, httpEngine)
        val manager = BackupRestoreManager(database, userPreferencesRepository, transport)
        val vm = withContext(Dispatchers.Main) {
            BackupViewModel(manager, userPreferencesRepository, mockAuthProvider)
        }

        userPreferencesRepository.setGoogleAccount("test@gmail.com")
        userPreferencesRepository.updateBackupInfo(
            lastBackupTimestamp = 0L,
            backupSpreadsheetId = "SHEET_NET",
            backupSpreadsheetName = "Buku Warung - Test",
            googleAccountEmail = "test@gmail.com"
        )

        withContext(Dispatchers.Main) {
            vm.performBackup()
        }
        var attempts = 0
        while (vm.backupState.value !is BackupOpState.Error && attempts < 20) {
            delay(100)
            attempts++
        }

        assertEquals(0, httpEngine.createCount)
        assertTrue(vm.backupState.value is BackupOpState.Error)
        val settings = userPreferencesRepository.userSettings.first()
        assertEquals(0L, settings.lastBackupTimestamp)
    }

    @Test
    fun test22_http500_doesNotCreateSpreadsheet() = runBlocking {
        val httpEngine = AutoHealingMockHttpEngine()
        httpEngine.writeFailsForIdMap["SHEET_500"] = 500
        val transport = GoogleSheetsApiTransport(mockAuthProvider, httpEngine)
        val manager = BackupRestoreManager(database, userPreferencesRepository, transport)
        val vm = withContext(Dispatchers.Main) {
            BackupViewModel(manager, userPreferencesRepository, mockAuthProvider)
        }

        userPreferencesRepository.setGoogleAccount("test@gmail.com")
        userPreferencesRepository.updateBackupInfo(
            lastBackupTimestamp = 0L,
            backupSpreadsheetId = "SHEET_500",
            backupSpreadsheetName = "Buku Warung - Test",
            googleAccountEmail = "test@gmail.com"
        )

        withContext(Dispatchers.Main) {
            vm.performBackup()
        }
        var attempts = 0
        while (vm.backupState.value !is BackupOpState.Error && attempts < 20) {
            delay(100)
            attempts++
        }

        assertEquals(0, httpEngine.createCount)
        assertTrue(vm.backupState.value is BackupOpState.Error)
        val settings = userPreferencesRepository.userSettings.first()
        assertEquals(0L, settings.lastBackupTimestamp)
    }

    @Test
    fun test23_validSpreadsheet_exactlyOneBackupWrite_noUnnecessaryCreate() = runBlocking {
        val httpEngine = AutoHealingMockHttpEngine()
        val transport = GoogleSheetsApiTransport(mockAuthProvider, httpEngine)
        val manager = BackupRestoreManager(database, userPreferencesRepository, transport)
        val vm = withContext(Dispatchers.Main) {
            BackupViewModel(manager, userPreferencesRepository, mockAuthProvider)
        }

        userPreferencesRepository.setGoogleAccount("test@gmail.com")
        userPreferencesRepository.updateBackupInfo(
            lastBackupTimestamp = 0L,
            backupSpreadsheetId = "SINGLE_WRITE_SHEET",
            backupSpreadsheetName = "Buku Warung - Test",
            googleAccountEmail = "test@gmail.com"
        )

        withContext(Dispatchers.Main) {
            vm.performBackup()
        }
        var attempts = 0
        while (vm.backupState.value !is BackupOpState.Success && attempts < 20) {
            delay(100)
            attempts++
        }

        assertEquals(0, httpEngine.createCount)
        assertEquals(1, httpEngine.writeCount)
        assertTrue(vm.backupState.value is BackupOpState.Success)
    }
}
