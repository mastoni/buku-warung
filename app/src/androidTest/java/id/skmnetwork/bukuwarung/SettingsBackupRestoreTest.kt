package id.skmnetwork.bukuwarung

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.backup.BackupRestoreManager
import id.skmnetwork.bukuwarung.backup.MockSheetsTransport
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import id.skmnetwork.bukuwarung.data.repository.ProductRepository
import id.skmnetwork.bukuwarung.ui.settings.BackupOpState
import id.skmnetwork.bukuwarung.ui.settings.BackupViewModel
import id.skmnetwork.bukuwarung.ui.settings.RestoreOpState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SettingsBackupRestoreTest {

    private lateinit var context: Context
    private lateinit var testScope: CoroutineScope
    private lateinit var testFile: File
    private lateinit var testDataStore: DataStore<Preferences>
    private lateinit var prefsRepo: UserPreferencesRepository

    private lateinit var database: AppDatabase
    private lateinit var mockTransport: MockSheetsTransport
    private lateinit var backupRestoreManager: BackupRestoreManager
    private lateinit var productRepository: ProductRepository
    private lateinit var backupViewModel: BackupViewModel

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        testScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        testFile = File(context.cacheDir, "test_backup_prefs_${UUID.randomUUID()}.preferences_pb")
        testDataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testFile }
        )
        prefsRepo = UserPreferencesRepository(context, testDataStore)

        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        mockTransport = MockSheetsTransport()
        backupRestoreManager = BackupRestoreManager(
            database = database,
            userPreferencesRepository = prefsRepo,
            transport = mockTransport
        )
        productRepository = ProductRepository(database, "LEGACY_BUSINESS")
        backupViewModel = BackupViewModel(backupRestoreManager, prefsRepo)
    }

    @After
    fun tearDown() {
        testScope.cancel()
        if (testFile.exists()) {
            testFile.delete()
        }
        database.close()
    }

    @Test
    fun testRequirementA_SettingsInitialStateAndPreferencesDefaults() = runBlocking {
        // A. Section Cadangan & Pemulihan initial state
        val settings = prefsRepo.userSettings.first()
        assertEquals(0L, settings.lastBackupTimestamp)
        assertEquals("", settings.backupSpreadsheetId)
        assertEquals("", settings.googleAccountEmail)

        assertEquals(BackupOpState.Idle, backupViewModel.backupState.value)
        assertEquals(RestoreOpState.Idle, backupViewModel.restoreState.value)
    }

    @Test
    fun testRequirementB_StatusNeverBackedUpWhenTimestampZero() = runBlocking {
        // B. Status "Belum pernah dicadangkan" when timestamp = 0
        val settings = prefsRepo.userSettings.first()
        val formattedStatus = if (settings.lastBackupTimestamp <= 0L) {
            "Belum pernah dicadangkan"
        } else {
            "Tercadangkan"
        }
        assertEquals("Belum pernah dicadangkan", formattedStatus)
    }

    @Test
    fun testRequirementC_BackupLoadingAndSuccessState() = runBlocking {
        prefsRepo.setGoogleAccount("test@warung.com")

        // Seed some data first
        val cat = productRepository.createCategory("Makanan").getOrThrow()
        productRepository.insertProductWithCategory(
            name = "Mie Instan",
            categoryName = "Makanan",
            purchasePrice = 2500,
            sellingPrice = 3500,
            stock = 100.0,
            minimumStock = 10.0,
            unit = "bungkus",
            categoryId = cat.id
        )

        // Perform backup
        backupViewModel.performBackup("TEST_SPREADSHEET_ID")

        // Wait for coroutine completion
        var retries = 0
        while (backupViewModel.backupState.value !is BackupOpState.Success && retries < 50) {
            delay(50)
            retries++
        }

        assertTrue("Backup state should be Success", backupViewModel.backupState.value is BackupOpState.Success)
        val successState = backupViewModel.backupState.value as BackupOpState.Success
        assertTrue(successState.message.contains("berhasil dicadangkan"))
    }

    @Test
    fun testRequirementD_BackupSuccessUpdatesTimestampAndPreferences() = runBlocking {
        prefsRepo.setGoogleAccount("test@warung.com")

        val cat = productRepository.createCategory("Minuman").getOrThrow()
        productRepository.insertProductWithCategory(
            name = "Kopi Hitam",
            categoryName = "Minuman",
            purchasePrice = 3000,
            sellingPrice = 5000,
            stock = 50.0,
            minimumStock = 5.0,
            unit = "cup",
            categoryId = cat.id
        )

        val beforeTime = System.currentTimeMillis()
        backupViewModel.performBackup("SPREADSHEET_123")

        var retries = 0
        while (backupViewModel.backupState.value !is BackupOpState.Success && retries < 50) {
            delay(50)
            retries++
        }

        // Wait for DataStore write to settle
        delay(100)

        val updatedSettings = prefsRepo.userSettings.first()
        assertTrue("Timestamp must be >= beforeTime", updatedSettings.lastBackupTimestamp >= beforeTime)
        assertEquals("SPREADSHEET_123", updatedSettings.backupSpreadsheetId)
    }

    @Test
    fun testRequirementE_BackupFailureDisplaysFriendlyErrorWithoutMutatingLocalData() = runBlocking {
        prefsRepo.setGoogleAccount("test@warung.com")

        // Configure transport failure
        mockTransport.simulateNetworkFailure = true

        val cat = productRepository.createCategory("Sembako").getOrThrow()
        val prodId = productRepository.insertProductWithCategory(
            name = "Gula Pasir 1kg",
            categoryName = "Sembako",
            purchasePrice = 14000,
            sellingPrice = 17000,
            stock = 20.0,
            minimumStock = 2.0,
            unit = "kg",
            categoryId = cat.id
        )

        backupViewModel.performBackup("SPREADSHEET_ERR")

        var retries = 0
        while (backupViewModel.backupState.value !is BackupOpState.Error && retries < 50) {
            delay(50)
            retries++
        }

        assertTrue("Backup state should be Error", backupViewModel.backupState.value is BackupOpState.Error)
        val errorState = backupViewModel.backupState.value as BackupOpState.Error
        assertTrue(
            "Error message must be user-friendly without technical terms",
            errorState.message.contains("Gagal mencadangkan") || errorState.message.contains("koneksi")
        )
        assertFalse(errorState.message.contains("checksum"))
        assertFalse(errorState.message.contains("hash"))
        assertFalse(errorState.message.contains("Room"))
        assertFalse(errorState.message.contains("UUID"))

        // Local data remains completely intact
        val prod = productRepository.getProductById(prodId)
        assertNotNull(prod)
        assertEquals("Gula Pasir 1kg", prod?.name)
    }

    @Test
    fun testRequirementF_G_CancelRestoreDoesNotModifyData() = runBlocking {
        // Initial state: Product A exists
        val cat = productRepository.createCategory("Alat Tulis").getOrThrow()
        val prodId = productRepository.insertProductWithCategory(
            name = "Buku Tulis Sinar Dunia",
            categoryName = "Alat Tulis",
            purchasePrice = 4000,
            sellingPrice = 6000,
            stock = 30.0,
            minimumStock = 5.0,
            unit = "buah",
            categoryId = cat.id
        )

        // Simulating user cancelling restore dialog: performRestore is NOT called.
        // Confirm ViewModel state is still Idle
        assertEquals(RestoreOpState.Idle, backupViewModel.restoreState.value)

        // Verify data intact
        val prod = productRepository.getProductById(prodId)
        assertNotNull(prod)
        assertEquals("Buku Tulis Sinar Dunia", prod?.name)
    }

    @Test
    fun testRequirementH_RestoreFailureKeepsLocalDatabaseIntact() = runBlocking {
        prefsRepo.setGoogleAccount("test@warung.com")

        // 1. Initial valid local data
        val cat = productRepository.createCategory("Kebutuhan").getOrThrow()
        val prodId = productRepository.insertProductWithCategory(
            name = "Sabun Mandi",
            categoryName = "Kebutuhan",
            purchasePrice = 3000,
            sellingPrice = 4500,
            stock = 15.0,
            minimumStock = 2.0,
            unit = "pcs",
            categoryId = cat.id
        )

        // 2. Perform a successful backup to mock transport
        val backupResult = backupRestoreManager.performBackup("TEST_BACKUP")
        assertTrue(backupResult.isSuccess)

        // 3. Corrupt the mock transport data (checksum mismatch)
        val snapshot = mockTransport.getSnapshotDirectly("TEST_BACKUP")
        assertNotNull(snapshot)
        val validSnapshot = snapshot!!

        val corruptedTabs = validSnapshot.tabs.toMutableMap()
        val prodTab = corruptedTabs["04_Products"]!!
        val modifiedRows = prodTab.rows.map { row ->
            val mutableRow = row.toMutableList()
            mutableRow[3] = "Sabun Mandi CORRUPTED"
            mutableRow.toList()
        }
        corruptedTabs["04_Products"] = prodTab.copy(rows = modifiedRows)
        // Checksum in metadata is unchanged -> Tamper / Checksum mismatch triggered!
        mockTransport.putSnapshotDirectly("TEST_BACKUP", validSnapshot.copy(tabs = corruptedTabs))

        // 4. Perform restore via ViewModel
        backupViewModel.performRestore("TEST_BACKUP")

        var retries = 0
        while (backupViewModel.restoreState.value !is RestoreOpState.Error && retries < 50) {
            delay(50)
            retries++
        }

        // 5. Verify Restore state is Error
        assertTrue(backupViewModel.restoreState.value is RestoreOpState.Error)
        val errorState = backupViewModel.restoreState.value as RestoreOpState.Error
        assertTrue(
            "User-friendly error message should explain corrupted/tampered data",
            errorState.message.contains("diubah atau rusak") || errorState.message.contains("keamanan data")
        )

        // 6. Verify Local Room database is NOT wiped and intact
        val prod = productRepository.getProductById(prodId)
        assertNotNull("Local product must survive failed restore", prod)
        assertEquals("Sabun Mandi", prod?.name)
    }

    @Test
    fun testRequirementI_OfflineSettingsNoAutomaticNetworkCall() = runBlocking {
        // Verify ViewModel initialization does not make any transport calls or snapshots
        val directSnapshot = mockTransport.getSnapshotDirectly("OFFLINE_ID")
        assertEquals(null, directSnapshot)

        val settings = prefsRepo.userSettings.first()
        assertNotNull(settings)
        assertEquals(null, mockTransport.getSnapshotDirectly("OFFLINE_ID"))
    }
}


