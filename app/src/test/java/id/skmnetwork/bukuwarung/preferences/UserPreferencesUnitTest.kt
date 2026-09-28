package id.skmnetwork.bukuwarung.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The raw keys an older build persisted, spelled out here exactly as it wrote them. Step 12A retired
 * the concept, so the app declares neither key any more; the test re-declares them to reproduce
 * what an install upgraded from that build still has on disk.
 */
private val LEGACY_BUSINESS_TYPE_LOCKED_KEY = booleanPreferencesKey("business_type_locked")
private val LEGACY_IS_SETUP_COMPLETED_KEY = booleanPreferencesKey("is_setup_completed")

class InMemoryPreferencesDataStore(
    initialValue: Preferences = emptyPreferences()
) : DataStore<Preferences> {

    private val state = MutableStateFlow(initialValue)

    override val data = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

/**
 * G2 — Business Profile Model & DataStore Persistence Unit Tests
 */
class UserPreferencesUnitTest {

    private lateinit var testDataStore: DataStore<Preferences>
    private lateinit var prefsRepo: UserPreferencesRepository

    @Before
    fun setup() {
        testDataStore = InMemoryPreferencesDataStore()
        prefsRepo = UserPreferencesRepository(context = null, dataStore = testDataStore)
    }

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

    /**
     * Step 12A - the business type is not locked after onboarding.
     *
     * The PR-11.1 rule that made the type immutable once setup completed is retired: the merchant
     * may change it, the change is acknowledged in the UI, and nothing about the change rewrites
     * existing data. This proves the persistence layer still allows it.
     */
    @Test
    fun testF_BusinessTypeStaysChangeableAfterSetupCompletes() = runBlocking {
        prefsRepo.saveInitialSetupProfile(
            shopName = "Warung Berkah Jaya",
            ownerName = "Pak Berkah",
            phone = "081298765432",
            address = "Jl. Prosperity No. 9",
            primaryBusinessType = "WARUNG_SEMBAKO",
            secondaryActivities = setOf("ACTIVITY_GOODS_SELLING"),
            profileVersion = 1
        )
        val afterSetup = prefsRepo.userSettings.first()
        assertTrue("Setup must be complete for this scenario", afterSetup.isSetupCompleted)
        assertEquals("WARUNG_SEMBAKO", afterSetup.primaryBusinessType)

        // Post-onboarding change must succeed and be readable straight back.
        prefsRepo.updateBusinessProfile(
            primaryType = "KONTER_PULSA_HP",
            secondaryActivities = setOf("ACTIVITY_GOODS_SELLING", "ACTIVITY_SERVICE_LABOR"),
            version = 1
        )

        val afterChange = prefsRepo.userSettings.first()
        assertEquals(
            "Business type must remain changeable after setup completed",
            "KONTER_PULSA_HP",
            afterChange.primaryBusinessType
        )
        assertTrue(
            "Setup completion must not be undone by a type change",
            afterChange.isSetupCompleted
        )
        assertEquals(
            "Existing secondary activities must be carried over, not dropped",
            true,
            afterChange.secondaryActivities.contains("ACTIVITY_GOODS_SELLING")
        )
        assertTrue(
            "Newly seeded activities must be present too",
            afterChange.secondaryActivities.contains("ACTIVITY_SERVICE_LABOR")
        )
        assertEquals("Warung Berkah Jaya", afterChange.shopName)
        assertEquals("Pak Berkah", afterChange.ownerName)
    }

    /**
     * Step 12A - an install upgraded from a build that wrote `business_type_locked = true` must
     * not be blocked by that leftover value.
     *
     * The key is no longer declared, so the entry is simply unread. The merchant's change still
     * lands, which is the invariant that matters for existing installs.
     */
    @Test
    fun testG_LegacyBusinessTypeLockValueCannotBlockAChange() = runBlocking {
        val legacyStore = InMemoryPreferencesDataStore(
            mutablePreferencesOf(
                LEGACY_BUSINESS_TYPE_LOCKED_KEY to true,
                LEGACY_IS_SETUP_COMPLETED_KEY to true
            )
        )
        val legacyRepo = UserPreferencesRepository(context = null, dataStore = legacyStore)

        // The retired flag is not surfaced to the app any more...
        val legacySettings = legacyRepo.userSettings.first()
        assertTrue("The upgraded install is past setup", legacySettings.isSetupCompleted)
        assertEquals("WARUNG_SEMBAKO", legacySettings.primaryBusinessType)

        // ...and it cannot stand in the way of changing the business type.
        legacyRepo.updateBusinessProfile(
            primaryType = "RUMAH_MAKAN",
            secondaryActivities = setOf("ACTIVITY_GOODS_SELLING", "ACTIVITY_DINE_IN"),
            version = 1
        )

        assertEquals(
            "A stale business_type_locked=true must not prevent the change",
            "RUMAH_MAKAN",
            legacyRepo.userSettings.first().primaryBusinessType
        )
        assertEquals(
            "A second change must be possible too; the lock is not a one-time gate",
            "KULINER_CAFE",
            legacyRepo.updateBusinessProfile(
                primaryType = "KULINER_CAFE",
                secondaryActivities = setOf("ACTIVITY_DINE_IN"),
                version = 1
            ).let { legacyRepo.userSettings.first().primaryBusinessType }
        )
    }
}
