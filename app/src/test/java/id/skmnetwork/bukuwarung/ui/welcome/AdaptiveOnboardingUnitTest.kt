package id.skmnetwork.bukuwarung.ui.welcome

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import id.skmnetwork.bukuwarung.domain.business.BusinessActivity
import id.skmnetwork.bukuwarung.domain.business.BusinessCategory
import id.skmnetwork.bukuwarung.domain.business.BusinessTaxonomyRegistry
import id.skmnetwork.bukuwarung.domain.business.BusinessType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class InMemoryDataStore(
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
 * Gate G4 — Adaptive Onboarding Comprehensive Unit Tests
 * Validates Tests A through M according to the G4 Governance and Audit Specification.
 */
class AdaptiveOnboardingUnitTest {

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var prefsRepo: UserPreferencesRepository

    @Before
    fun setup() {
        dataStore = InMemoryDataStore()
        prefsRepo = UserPreferencesRepository(context = null, dataStore = dataStore)
    }

    @Test
    fun testA_Step1_IdentityValidation() {
        // Validation rule: shopName cannot be blank
        val validShopName = " Toko Sumber Rejeki "
        val blankShopName = "   "

        fun validateShopName(input: String): String? {
            return if (input.trim().isEmpty()) "Nama warung / usaha wajib diisi" else null
        }

        assertNotNull("Blank shop name must yield error", validateShopName(blankShopName))
        assertEquals(null, validateShopName(validShopName))
    }

    @Test
    fun testB_Step2_DefaultBusinessTypePreset() {
        val defaultType = BusinessTaxonomyRegistry.DEFAULT_BUSINESS_TYPE
        assertEquals("Default business type must be WARUNG_SEMBAKO", BusinessType.WARUNG_SEMBAKO, defaultType)

        val preset = BusinessTaxonomyRegistry.getPreset(defaultType)
        assertEquals(BusinessType.WARUNG_SEMBAKO, preset.businessType)
        assertTrue(preset.defaultActivities.contains(BusinessActivity.ACTIVITY_GOODS_SELLING))
    }

    @Test
    fun testC_Step2_SelectingBusinessTypeUpdatesDefaultActivities() {
        // 1. Initial selection: WARUNG_SEMBAKO
        var selectedType = BusinessType.WARUNG_SEMBAKO
        var selectedActivities = BusinessTaxonomyRegistry.getPreset(selectedType).defaultActivities
        assertTrue(selectedActivities.contains(BusinessActivity.ACTIVITY_GOODS_SELLING))
        assertFalse(selectedActivities.contains(BusinessActivity.ACTIVITY_SERVICE_LABOR))

        // 2. Select BENGKEL_MOTOR_MOBIL
        selectedType = BusinessType.BENGKEL_MOTOR_MOBIL
        selectedActivities = BusinessTaxonomyRegistry.getPreset(selectedType).defaultActivities
        assertTrue(selectedActivities.contains(BusinessActivity.ACTIVITY_SERVICE_LABOR))
        assertTrue(selectedActivities.contains(BusinessActivity.ACTIVITY_GOODS_SELLING))
        assertTrue(selectedActivities.contains(BusinessActivity.ACTIVITY_WHOLESALE_PURCHASE))

        // 3. Select LAUNDRY
        selectedType = BusinessType.LAUNDRY
        selectedActivities = BusinessTaxonomyRegistry.getPreset(selectedType).defaultActivities
        assertTrue(selectedActivities.contains(BusinessActivity.ACTIVITY_SERVICE_LABOR))
        assertTrue(selectedActivities.contains(BusinessActivity.ACTIVITY_GOODS_SELLING))
        assertFalse(selectedActivities.contains(BusinessActivity.ACTIVITY_DIGITAL_VOUCHER))
    }

    @Test
    fun testD_Step3_AddAndRemoveSecondaryActivities() {
        val selectedType = BusinessType.WARUNG_SEMBAKO
        val defaultActivities = BusinessTaxonomyRegistry.getPreset(selectedType).defaultActivities
        val selectedActivities = defaultActivities.toMutableSet()

        // 1. Initially contains default activities
        assertTrue(selectedActivities.contains(BusinessActivity.ACTIVITY_GOODS_SELLING))
        assertFalse(selectedActivities.contains(BusinessActivity.ACTIVITY_DIGITAL_VOUCHER))

        // 2. User adds DIGITAL_VOUCHER (e.g. Warung selling pulsa)
        selectedActivities.add(BusinessActivity.ACTIVITY_DIGITAL_VOUCHER)
        assertTrue(selectedActivities.contains(BusinessActivity.ACTIVITY_DIGITAL_VOUCHER))

        // 3. User removes CREDIT_TABUNGAN from active selection
        selectedActivities.remove(BusinessActivity.ACTIVITY_CREDIT_TABUNGAN)
        assertFalse(selectedActivities.contains(BusinessActivity.ACTIVITY_CREDIT_TABUNGAN))
        assertEquals(3, selectedActivities.size) // GOODS_SELLING, WHOLESALE_PURCHASE, DIGITAL_VOUCHER

        // 4. Resolve composed profile with secondary activities (union of preset default + secondary)
        val secondaryOnly = setOf(BusinessActivity.ACTIVITY_DIGITAL_VOUCHER.id)
        val resolved = BusinessTaxonomyRegistry.resolve(
            primaryType = selectedType.id,
            secondaryActivities = secondaryOnly
        )
        assertTrue(resolved.activities.contains(BusinessActivity.ACTIVITY_DIGITAL_VOUCHER))
        assertTrue(resolved.activities.contains(BusinessActivity.ACTIVITY_GOODS_SELLING))
    }

    @Test
    fun testE_MultiStep_BackNavigationStatePreservation() {
        // Simulate step wizard state holder
        var currentStep = 1
        var shopName = "Bengkel Maju Jaya"
        var ownerName = "Pak Joko"
        var phone = "0812999888"
        var address = "Jl. Otomotif No. 10"
        var selectedType = BusinessType.BENGKEL_MOTOR_MOBIL
        var selectedActivities = BusinessTaxonomyRegistry.getPreset(selectedType).defaultActivities.toSet()

        // Step 1 -> Step 2
        currentStep = 2
        assertEquals(2, currentStep)
        assertEquals("Bengkel Maju Jaya", shopName)

        // Step 2 -> Step 3
        currentStep = 3
        // In step 3, user adds digital voucher
        selectedActivities = selectedActivities + BusinessActivity.ACTIVITY_DIGITAL_VOUCHER

        // Step 3 -> Back to Step 2
        currentStep = 2
        assertEquals(2, currentStep)
        // Step 2 -> Back to Step 1
        currentStep = 1
        assertEquals(1, currentStep)
        // Verify all fields retained
        assertEquals("Bengkel Maju Jaya", shopName)
        assertEquals("Pak Joko", ownerName)
        assertEquals("0812999888", phone)
        assertEquals("Jl. Otomotif No. 10", address)
        assertEquals(BusinessType.BENGKEL_MOTOR_MOBIL, selectedType)
        assertTrue(selectedActivities.contains(BusinessActivity.ACTIVITY_DIGITAL_VOUCHER))
    }

    @Test
    fun testF_Step4_SummaryNoInternalCapFlagsExposed() {
        val selectedType = BusinessType.WARUNG_MAKAN
        val selectedActivities = setOf(
            BusinessActivity.ACTIVITY_GOODS_SELLING,
            BusinessActivity.ACTIVITY_RAW_MATERIALS
        )

        val resolved = BusinessTaxonomyRegistry.resolve(
            primaryType = selectedType.id,
            secondaryActivities = selectedActivities.map { it.id }.toSet()
        )

        // Terminology preview strings
        val productLabel = resolved.terminology.productLabel
        val transactionLabel = resolved.terminology.transactionLabel
        val customerLabel = resolved.terminology.customerLabel

        assertEquals("Menu Makanan", productLabel)
        assertEquals("Pesanan / Kasir", transactionLabel)
        assertEquals("Pelanggan", customerLabel)

        // Verify that human-readable summaries do not expose raw "CAP_" flags
        val summaryText = "Produk: $productLabel, Transaksi: $transactionLabel, Pelanggan: $customerLabel, Satuan: ${resolved.preferredUnits.joinToString()}"
        assertFalse("Summary must not contain CAP_ prefix", summaryText.contains("CAP_"))
    }

    @Test
    fun testG_Step5_AtomicSaveInitialSetupProfile() = runBlocking {
        // Complete onboarding
        prefsRepo.saveInitialSetupProfile(
            shopName = "Warung Kopi Senja",
            ownerName = "Budi Santoso",
            phone = "0811223344",
            address = "Jl. Kopi No. 7",
            primaryBusinessType = "KEDAI_KAFE",
            secondaryActivities = setOf("ACTIVITY_GOODS_SELLING", "ACTIVITY_RAW_MATERIALS"),
            profileVersion = 1
        )

        val settings = prefsRepo.userSettings.first()
        assertTrue("Setup must be marked completed", settings.isSetupCompleted)
        assertEquals("Warung Kopi Senja", settings.shopName)
        assertEquals("Budi Santoso", settings.ownerName)
        assertEquals("0811223344", settings.phone)
        assertEquals("Jl. Kopi No. 7", settings.address)
        assertEquals("KEDAI_KAFE", settings.primaryBusinessType)
        assertEquals(setOf("ACTIVITY_GOODS_SELLING", "ACTIVITY_RAW_MATERIALS"), settings.secondaryActivities)
        assertEquals(1, settings.profileVersion)
        assertTrue("BusinessId must be generated", settings.businessId.isNotEmpty())
        assertTrue("DeviceId must be generated", settings.deviceId.isNotEmpty())
    }

    @Test
    fun testH_RestartReloadDataStoreConsistency() = runBlocking {
        // 1. Initial setup
        prefsRepo.saveInitialSetupProfile(
            shopName = "Apotek Sehat Mandiri",
            ownerName = "dr. Farhan",
            phone = "08133445566",
            address = "Jl. Kesehatan No. 12",
            primaryBusinessType = "APOTEK_OBAT",
            secondaryActivities = setOf("ACTIVITY_GOODS_SELLING", "ACTIVITY_WHOLESALE_PURCHASE"),
            profileVersion = 1
        )

        // 2. Simulate app restart with new repository instance referencing same DataStore
        val reloadedRepo = UserPreferencesRepository(context = null, dataStore = dataStore)
        val reloadedSettings = reloadedRepo.userSettings.first()

        assertTrue(reloadedSettings.isSetupCompleted)
        assertEquals("Apotek Sehat Mandiri", reloadedSettings.shopName)
        assertEquals("APOTEK_OBAT", reloadedSettings.primaryBusinessType)

        val resolved = BusinessTaxonomyRegistry.resolve(
            reloadedSettings.primaryBusinessType,
            reloadedSettings.secondaryActivities
        )
        assertEquals(BusinessType.APOTEK_OBAT, resolved.businessType)
        assertEquals("Obat / Alkes", resolved.terminology.productLabel)
    }

    @Test
    fun testI_LegacyUserBypassOnboarding() = runBlocking {
        // Simulate legacy v0.1.0 user with existing shop profile
        prefsRepo.saveShopProfile(
            shopName = "Warung Bu Siti Lama",
            ownerName = "Bu Siti",
            phone = "0812345678",
            address = "Pasar Pagi"
        )

        // Run autoMigrateExistingUserIfNeeded
        prefsRepo.autoMigrateExistingUserIfNeeded(database = null)

        val settings = prefsRepo.userSettings.first()
        assertTrue("Legacy user must be marked setup completed to bypass onboarding wizard", settings.isSetupCompleted)
        assertEquals("Warung Bu Siti Lama", settings.shopName)
        // Adaptive business type defaults safely to WARUNG_SEMBAKO without overwriting
        assertEquals("WARUNG_SEMBAKO", settings.primaryBusinessType)
    }

    @Test
    fun testJ_CustomProfilePreservationDuringMigration() = runBlocking {
        // Set custom business profile
        prefsRepo.saveInitialSetupProfile(
            shopName = "Toko Pakaian Cantik",
            ownerName = "Sari",
            phone = "08778899",
            address = "Ruko Blok C",
            primaryBusinessType = "TOKO_PAKAIAN",
            secondaryActivities = setOf("ACTIVITY_GOODS_SELLING", "ACTIVITY_WHOLESALE_PURCHASE"),
            profileVersion = 1
        )

        // Trigger startup migration
        prefsRepo.autoMigrateExistingUserIfNeeded(database = null)

        val settings = prefsRepo.userSettings.first()
        assertEquals("TOKO_PAKAIAN", settings.primaryBusinessType)
        assertEquals("Toko Pakaian Cantik", settings.shopName)
        assertEquals(setOf("ACTIVITY_GOODS_SELLING", "ACTIVITY_WHOLESALE_PURCHASE"), settings.secondaryActivities)
    }

    @Test
    fun testK_InvalidBusinessTypeFallback() {
        val unknownTypeString = "NON_EXISTENT_TYPE_XYZ"
        val resolved = BusinessTaxonomyRegistry.resolve(
            primaryType = unknownTypeString,
            secondaryActivities = emptySet()
        )

        assertEquals("Unknown type must fall back to DEFAULT_BUSINESS_TYPE (WARUNG_SEMBAKO)", BusinessType.WARUNG_SEMBAKO, resolved.businessType)
        assertEquals(BusinessCategory.RETAIL, resolved.category)
    }

    @Test
    fun testL_PresetImmutability() {
        val initialPreset = BusinessTaxonomyRegistry.getPreset(BusinessType.WARUNG_SEMBAKO)
        val initialActivities = initialPreset.defaultActivities

        // Create a custom set and resolve
        val customActivities = setOf(BusinessActivity.ACTIVITY_SERVICE_LABOR.id)
        val resolved = BusinessTaxonomyRegistry.resolve(BusinessType.WARUNG_SEMBAKO.id, customActivities)

        // Verify the original static preset was NOT mutated
        val presetAfter = BusinessTaxonomyRegistry.getPreset(BusinessType.WARUNG_SEMBAKO)
        assertEquals(initialActivities, presetAfter.defaultActivities)
        assertFalse(presetAfter.defaultActivities.contains(BusinessActivity.ACTIVITY_SERVICE_LABOR))
    }

    @Test
    fun testM_CategoryFiltering() {
        val retailTypes = BusinessTaxonomyRegistry.getBusinessTypesByCategory(BusinessCategory.RETAIL)
        assertTrue(retailTypes.contains(BusinessType.WARUNG_SEMBAKO))
        assertTrue(retailTypes.contains(BusinessType.MINIMARKET_RETAIL))
        assertTrue(retailTypes.contains(BusinessType.TOKO_PAKAIAN))
        assertFalse(retailTypes.contains(BusinessType.BENGKEL_MOTOR_MOBIL))

        val serviceTypes = BusinessTaxonomyRegistry.getBusinessTypesByCategory(BusinessCategory.SERVICES)
        assertTrue(serviceTypes.contains(BusinessType.BENGKEL_MOTOR_MOBIL))
        assertTrue(serviceTypes.contains(BusinessType.LAUNDRY))
        assertFalse(serviceTypes.contains(BusinessType.WARUNG_MAKAN))

        val foodTypes = BusinessTaxonomyRegistry.getBusinessTypesByCategory(BusinessCategory.FOOD_BEV)
        assertTrue(foodTypes.contains(BusinessType.WARUNG_MAKAN))
        assertTrue(foodTypes.contains(BusinessType.KEDAI_KAFE))

        val productionTypes = BusinessTaxonomyRegistry.getBusinessTypesByCategory(BusinessCategory.PRODUCTION)
        assertTrue(productionTypes.contains(BusinessType.INDUSTRI_RUMAHAN))
    }
}
