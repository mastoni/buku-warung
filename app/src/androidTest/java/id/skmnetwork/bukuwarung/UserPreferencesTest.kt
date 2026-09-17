package id.skmnetwork.bukuwarung

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import id.skmnetwork.bukuwarung.license.DebugLicenseProvider
import id.skmnetwork.bukuwarung.license.LicenseManager
import id.skmnetwork.bukuwarung.license.LicenseStatus
import id.skmnetwork.bukuwarung.license.ProductionLicenseProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class UserPreferencesTest {

    private lateinit var context: Context
    private lateinit var testScope: CoroutineScope
    private lateinit var testFile: File
    private lateinit var testDataStore: DataStore<Preferences>
    private lateinit var prefsRepo: UserPreferencesRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        testScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        testFile = File(context.cacheDir, "test_prefs_${UUID.randomUUID()}.preferences_pb")
        testDataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { testFile }
        )
        prefsRepo = UserPreferencesRepository(context, testDataStore)
    }

    @After
    fun tearDown() {
        testScope.cancel()
        if (testFile.exists()) {
            testFile.delete()
        }
    }

    @Test
    fun testDataStoreDefaultsAndProfilePersistence() = runBlocking {
        // 1. Verify Shop Profile Save
        prefsRepo.saveShopProfile(
            shopName = "Warung Berkah",
            ownerName = "Pak Toni",
            phone = "08123456789",
            address = "Jl. Merdeka No. 10"
        )

        val settings = prefsRepo.userSettings.first()
        assertTrue("Setup must be marked completed after saving profile", settings.isSetupCompleted)
        assertEquals("Warung Berkah", settings.shopName)
        assertEquals("Pak Toni", settings.ownerName)
        assertEquals("08123456789", settings.phone)
        assertEquals("Jl. Merdeka No. 10", settings.address)
    }

    @Test
    fun testPaymentAndStockAndReceiptSettingsPersistence() = runBlocking {
        // 1. Verify Payment Settings
        prefsRepo.updatePaymentSettings(
            cashEnabled = true,
            qrisEnabled = false,
            creditEnabled = true,
            cashReceivedEnabled = true,
            qrisConfirmationRequired = false
        )

        val updatedPaymentSettings = prefsRepo.userSettings.first()
        assertTrue(updatedPaymentSettings.cashEnabled)
        assertFalse(updatedPaymentSettings.qrisEnabled)
        assertTrue(updatedPaymentSettings.creditEnabled)
        assertFalse(updatedPaymentSettings.qrisConfirmationRequired)

        // 2. Verify Stock Settings
        prefsRepo.updateStockSettings(
            lowStockAlertEnabled = true,
            defaultLowStockLimit = 5,
            allowNegativeStock = false
        )

        val updatedStockSettings = prefsRepo.userSettings.first()
        assertTrue(updatedStockSettings.lowStockAlertEnabled)
        assertEquals(5, updatedStockSettings.defaultLowStockLimit)

        // 3. Verify Receipt Settings
        prefsRepo.updateReceiptSettings(
            showShopName = true,
            showAddress = true,
            showPhone = false,
            showPaymentMethod = true,
            showChange = true,
            footerText = "Terima Kasih Telah Berbelanja!"
        )

        val receiptSettings = prefsRepo.userSettings.first()
        assertTrue(receiptSettings.showShopNameOnReceipt)
        assertFalse(receiptSettings.showPhoneOnReceipt)
        assertEquals("Terima Kasih Telah Berbelanja!", receiptSettings.receiptFooterText)
    }

    @Test
    fun testSecureSaltedHashPinStorageAndVerification() = runBlocking {
        // 1. Set PIN "5678"
        prefsRepo.setOwnerPin("5678")

        val settings = prefsRepo.userSettings.first()
        assertTrue("PIN must be marked enabled", settings.pinEnabled)
        assertTrue("hasPinSet must be true", settings.hasPinSet)

        // 2. Verify correct PIN is accepted
        val correctResult = prefsRepo.verifyPin("5678")
        assertTrue("Correct PIN must return true", correctResult)

        // 3. Verify incorrect PIN is rejected
        val wrongResult = prefsRepo.verifyPin("1234")
        assertFalse("Wrong PIN must return false", wrongResult)

        // 4. Clear PIN
        prefsRepo.clearPin()
        val clearedSettings = prefsRepo.userSettings.first()
        assertFalse(clearedSettings.pinEnabled)
        assertFalse(clearedSettings.hasPinSet)
        assertFalse(prefsRepo.verifyPin("5678"))
    }

    @Test
    fun testLegacyPlaintextPinMigration() = runBlocking {
        // 1. Simulate legacy user who had plaintext owner_pin "9876"
        testDataStore.edit { prefs ->
            prefs[UserPreferencesRepository.Keys.LEGACY_OWNER_PIN] = "9876"
        }

        // 2. Verify userSettings recognizes legacy PIN even before migration function runs
        val beforeMigration = prefsRepo.userSettings.first()
        assertTrue("Legacy PIN must be recognized as set", beforeMigration.hasPinSet)
        assertTrue("Legacy PIN must be enabled", beforeMigration.pinEnabled)
        assertTrue("verifyPin must accept legacy PIN before migration", prefsRepo.verifyPin("9876"))

        // 3. Run auto migration
        prefsRepo.autoMigrateExistingUserIfNeeded()

        // 4. Verify migrated state
        val rawPrefs = testDataStore.data.first()
        assertNull("Plaintext owner_pin must be deleted after migration", rawPrefs[UserPreferencesRepository.Keys.LEGACY_OWNER_PIN])
        assertTrue("pin_salt must be generated", !rawPrefs[UserPreferencesRepository.Keys.PIN_SALT].isNullOrEmpty())
        assertTrue("pin_hash must be generated", !rawPrefs[UserPreferencesRepository.Keys.PIN_HASH].isNullOrEmpty())

        val afterMigration = prefsRepo.userSettings.first()
        assertTrue("PIN remains enabled", afterMigration.pinEnabled)
        assertTrue("hasPinSet remains true", afterMigration.hasPinSet)
        assertTrue("verifyPin works with correct PIN", prefsRepo.verifyPin("9876"))
        assertFalse("verifyPin rejects wrong PIN", prefsRepo.verifyPin("0000"))
    }

    @Test
    fun testExistingUserAutoRecognitionWithCustomProfile() = runBlocking {
        // User with existing shop profile but is_setup_completed was not set in older versions
        testDataStore.edit { prefs ->
            prefs[UserPreferencesRepository.Keys.SHOP_NAME] = "Toko Sukses Jaya"
            prefs[UserPreferencesRepository.Keys.OWNER_NAME] = "Pak Budi"
        }

        val settings = prefsRepo.userSettings.first()
        assertTrue("Existing profile must automatically recognize setup as completed", settings.isSetupCompleted)

        // Auto migration should persist is_setup_completed = true
        prefsRepo.autoMigrateExistingUserIfNeeded()
        val rawPrefs = testDataStore.data.first()
        assertEquals(true, rawPrefs[UserPreferencesRepository.Keys.IS_SETUP_COMPLETED])
    }

    @Test
    fun testExistingUserAutoRecognitionWithDatabaseData() = runBlocking {
        val inMemoryDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            // Insert 1 category to simulate existing business data
            inMemoryDb.categoryDao().insertCategory(CategoryEntity(name = "Minuman"))

            // Initially DataStore has default blank settings
            val initialSettings = prefsRepo.userSettings.first()
            assertFalse("Default DataStore without migration is false", initialSettings.isSetupCompleted)

            // Run migration with existing database
            prefsRepo.autoMigrateExistingUserIfNeeded(inMemoryDb)

            val migratedSettings = prefsRepo.userSettings.first()
            assertTrue("Existing database data must mark setup as completed", migratedSettings.isSetupCompleted)
        } finally {
            inMemoryDb.close()
        }
    }

    @Test
    fun testFreshInstallRemainsUncompleted() = runBlocking {
        val emptyInMemoryDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            // Run migration on clean fresh install
            prefsRepo.autoMigrateExistingUserIfNeeded(emptyInMemoryDb)

            val settings = prefsRepo.userSettings.first()
            assertFalse("Fresh install must remain isSetupCompleted = false", settings.isSetupCompleted)
        } finally {
            emptyInMemoryDb.close()
        }
    }

    @Test
    fun testLicenseManagerProductionDefaultAndDebugBypass() = runBlocking {
        // 1. Production Provider MUST NOT be ACTIVE by default
        val prodProvider = ProductionLicenseProvider()
        val prodStatus = prodProvider.checkLicense()
        assertEquals("Production default must be UNLICENSED", LicenseStatus.UNLICENSED, prodStatus)

        // 2. Debug Provider with isDebug = false must be UNLICENSED
        val debugFalseProvider = DebugLicenseProvider(isDebug = false)
        assertEquals("Debug provider with isDebug=false must be UNLICENSED", LicenseStatus.UNLICENSED, debugFalseProvider.checkLicense())

        // 3. Debug Provider with isDebug = true is ACTIVE (for development)
        val debugTrueProvider = DebugLicenseProvider(isDebug = true)
        assertEquals("Debug provider with isDebug=true is ACTIVE", LicenseStatus.ACTIVE, debugTrueProvider.checkLicense())

        // 4. LicenseManager with Production Provider
        val prodManager = LicenseManager(provider = prodProvider)
        prodManager.refreshLicense()
        assertEquals("LicenseManager with ProductionLicenseProvider must be UNLICENSED", LicenseStatus.UNLICENSED, prodManager.licenseStatus.value)
        assertFalse("Pro features must be disabled when UNLICENSED", prodManager.isProFeatureEnabled("MULTI_DEVICE"))
    }

    @Test
    fun testLicenseManagerOwnerTestActivationAndPersistence() = runBlocking {
        // 1. Clean state: Owner test must NOT be activated by default
        assertFalse("Owner test activation must be false by default", prefsRepo.isOwnerTestActivated())

        val manager = LicenseManager(userPreferencesRepository = prefsRepo)
        manager.refreshLicense()
        assertEquals("Default license status must be UNLICENSED", LicenseStatus.UNLICENSED, manager.licenseStatus.value)

        // 2. Perform Owner Test Activation
        val activationResult = manager.activateOwnerTest()
        assertTrue("activateOwnerTest() must return true", activationResult)
        assertTrue("prefsRepo.isOwnerTestActivated() must be true after activation", prefsRepo.isOwnerTestActivated())
        assertEquals("LicenseManager status must transition to ACTIVE", LicenseStatus.ACTIVE, manager.licenseStatus.value)

        // 3. Verify persistence across new LicenseManager instance (simulating app restart)
        val newRestartedManager = LicenseManager(userPreferencesRepository = prefsRepo)
        newRestartedManager.refreshLicense()
        assertEquals("restartedManager licenseStatus flow must be ACTIVE", LicenseStatus.ACTIVE, newRestartedManager.licenseStatus.value)
    }

    @Test
    fun testProductionBuildStrictlyRejectsOwnerTestActivation() = runBlocking {
        // 1. Manually set OWNER_TEST_ACTIVATED to true in DataStore
        prefsRepo.setOwnerTestActivated(true)
        assertTrue(prefsRepo.isOwnerTestActivated())

        // 2. ProductionLicenseProvider with enableOwnerTest = false (simulating Production Release build)
        val prodProvider = ProductionLicenseProvider(
            userPreferencesRepository = prefsRepo,
            enableOwnerTest = false
        )
        val prodStatus = prodProvider.checkLicense()
        assertEquals(
            "Production release build MUST be UNLICENSED even if OWNER_TEST_ACTIVATED is true",
            LicenseStatus.UNLICENSED,
            prodStatus
        )

        // 3. LicenseManager with Production provider (enableOwnerTest = false)
        val prodManager = LicenseManager(
            userPreferencesRepository = prefsRepo,
            provider = prodProvider
        )
        prodManager.refreshLicense()
        assertEquals(
            "Production LicenseManager must remain UNLICENSED",
            LicenseStatus.UNLICENSED,
            prodManager.licenseStatus.value
        )

        // 4. Verify pro features remain disabled on production
        assertFalse(prodManager.isProFeatureEnabled("MULTI_DEVICE"))
    }

    // ==========================================
    // G2 — ADAPTIVE BUSINESS PROFILE TESTS
    // ==========================================

    @Test
    fun testA_DefaultBusinessProfile() = runBlocking {
        val settings = prefsRepo.userSettings.first()
        assertEquals("Default primary business type must be WARUNG_SEMBAKO", "WARUNG_SEMBAKO", settings.primaryBusinessType)
        assertTrue("Default secondary activities must contain ACTIVITY_GOODS_SELLING", settings.secondaryActivities.contains("ACTIVITY_GOODS_SELLING"))
        assertEquals("Default profile version must be 1", 1, settings.profileVersion)
    }

    @Test
    fun testB_SaveCustomBusinessProfile() = runBlocking {
        // Set custom business profile
        prefsRepo.updateBusinessProfile(
            primaryType = "BENGKEL",
            secondaryActivities = setOf("ACTIVITY_SERVICE_LABOR", "ACTIVITY_GOODS_SELLING"),
            version = 1
        )

        val settings = prefsRepo.userSettings.first()
        assertEquals("Primary business type must be BENGKEL", "BENGKEL", settings.primaryBusinessType)
        assertEquals(2, settings.secondaryActivities.size)
        assertTrue(settings.secondaryActivities.contains("ACTIVITY_SERVICE_LABOR"))
        assertTrue(settings.secondaryActivities.contains("ACTIVITY_GOODS_SELLING"))
        assertEquals(1, settings.profileVersion)
    }

    @Test
    fun testC_BusinessProfileMigrationIdempotency() = runBlocking {
        // 1. Set custom business profile
        prefsRepo.updateBusinessProfile(
            primaryType = "KULINER_CAFE",
            secondaryActivities = setOf("ACTIVITY_DINE_IN", "ACTIVITY_TAKEAWAY"),
            version = 1
        )

        // 2. Run auto-migration 1st time
        prefsRepo.autoMigrateExistingUserIfNeeded(database = null)
        val afterFirstMigrate = prefsRepo.userSettings.first()
        assertEquals("Custom profile must NOT be overwritten by migration #1", "KULINER_CAFE", afterFirstMigrate.primaryBusinessType)
        assertEquals(setOf("ACTIVITY_DINE_IN", "ACTIVITY_TAKEAWAY"), afterFirstMigrate.secondaryActivities)

        // 3. Run auto-migration 2nd time
        prefsRepo.autoMigrateExistingUserIfNeeded(database = null)
        val afterSecondMigrate = prefsRepo.userSettings.first()
        assertEquals("Custom profile must NOT be overwritten by migration #2", "KULINER_CAFE", afterSecondMigrate.primaryBusinessType)
        assertEquals(setOf("ACTIVITY_DINE_IN", "ACTIVITY_TAKEAWAY"), afterSecondMigrate.secondaryActivities)
    }

    @Test
    fun testD_ExistingUserPreservationDuringMigration() = runBlocking {
        // 1. Simulate existing v0.1.0 installation (with shop name, custom PIN, license status)
        prefsRepo.saveShopProfile(
            shopName = "Toko Kelontong Berkah",
            ownerName = "Haji Ahmad",
            phone = "081234567890",
            address = "Pasar Tradisional Blok A"
        )
        prefsRepo.setOwnerPin("1234")
        prefsRepo.saveLicenseEntitlement(
            status = "ACTIVE",
            ownerEmail = "ahmad@test.com",
            activatedAt = 1700000000000L,
            lastValidatedAt = 1700000000000L
        )

        val beforeSettings = prefsRepo.userSettings.first()
        assertTrue(beforeSettings.isSetupCompleted)
        val businessIdBefore = beforeSettings.businessId
        val deviceIdBefore = beforeSettings.deviceId

        // 2. Run autoMigrateExistingUserIfNeeded
        prefsRepo.autoMigrateExistingUserIfNeeded(database = null)

        // 3. Verify ALL existing v0.1.0 data are preserved untouched
        val afterSettings = prefsRepo.userSettings.first()
        assertTrue("isSetupCompleted must remain true for existing users", afterSettings.isSetupCompleted)
        assertEquals("Toko Kelontong Berkah", afterSettings.shopName)
        assertEquals("Haji Ahmad", afterSettings.ownerName)
        assertEquals("081234567890", afterSettings.phone)
        assertEquals("Pasar Tradisional Blok A", afterSettings.address)
        assertEquals(businessIdBefore, afterSettings.businessId)
        assertEquals(deviceIdBefore, afterSettings.deviceId)
        assertTrue(afterSettings.pinEnabled)
        assertTrue(prefsRepo.verifyPin("1234"))

        val licenseEntitlement = prefsRepo.getLicenseEntitlement()
        assertEquals("ACTIVE", licenseEntitlement.status)
        assertEquals("ahmad@test.com", licenseEntitlement.ownerEmail)

        // 4. Verify default business profile is populated
        assertEquals("WARUNG_SEMBAKO", afterSettings.primaryBusinessType)
        assertEquals(setOf("ACTIVITY_GOODS_SELLING"), afterSettings.secondaryActivities)
        assertEquals(1, afterSettings.profileVersion)
    }

    @Test
    fun testE_BusinessProfileIsolation() = runBlocking {
        // 1. Save initial settings
        prefsRepo.saveShopProfile(
            shopName = "Warung Bu Siti",
            ownerName = "Siti",
            phone = "08987654321",
            address = "Jl. Melati No. 5"
        )
        prefsRepo.updatePaymentSettings(
            cashEnabled = true,
            qrisEnabled = false,
            creditEnabled = true,
            cashReceivedEnabled = true,
            qrisConfirmationRequired = false
        )
        prefsRepo.updateStockSettings(
            lowStockAlertEnabled = true,
            defaultLowStockLimit = 10,
            allowNegativeStock = false
        )

        // 2. Update ONLY business profile
        prefsRepo.updateBusinessProfile(
            primaryType = "JASA_LAUNDRY",
            secondaryActivities = setOf("ACTIVITY_SERVICE_LABOR"),
            version = 1
        )

        // 3. Verify other settings are completely unaffected
        val settings = prefsRepo.userSettings.first()
        assertEquals("JASA_LAUNDRY", settings.primaryBusinessType)
        assertEquals(setOf("ACTIVITY_SERVICE_LABOR"), settings.secondaryActivities)
        assertEquals("Warung Bu Siti", settings.shopName)
        assertEquals("Siti", settings.ownerName)
        assertEquals("08987654321", settings.phone)
        assertEquals("Jl. Melati No. 5", settings.address)
        assertTrue(settings.cashEnabled)
        assertFalse(settings.qrisEnabled)
        assertEquals(10, settings.defaultLowStockLimit)
    }
}

