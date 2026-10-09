package id.skmnetwork.bukuwarung.domain.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Release 3 — Gate 1B: Operating Mode Resolver & Global PPOB Test Suite
 * Validates deterministic resolution of OperatingMode, opt-in PPOB behavior,
 * multi-activity hybridization, fallback handling, and immutable BusinessType identity.
 */
class OperatingModeResolverTest {

    @Test
    fun test01_freshInstallAndLegacyFallbacks() {
        // Null inputs fallback safely to DEFAULT_BUSINESS_TYPE (WARUNG_SEMBAKO) -> RETAIL_PRIMARY
        val modeNull = OperatingModeResolver.resolve(null, null as Set<BusinessActivity>?)
        assertEquals(OperatingMode.RETAIL_PRIMARY, modeNull)

        val modeStringNull = OperatingModeResolver.resolve(null, null as Set<String>?)
        assertEquals(OperatingMode.RETAIL_PRIMARY, modeStringNull)

        // Legacy record with standard retail activity
        val modeLegacy = OperatingModeResolver.resolve(
            primaryType = "WARUNG_SEMBAKO",
            secondaryActivities = setOf("ACTIVITY_GOODS_SELLING")
        )
        assertEquals(OperatingMode.RETAIL_PRIMARY, modeLegacy)
    }

    @Test
    fun test02_warungWithoutPpobIsRetailPrimary() {
        val profile = BusinessTaxonomyRegistry.resolve(
            primaryType = "WARUNG_SEMBAKO",
            secondaryActivities = emptySet()
        )
        assertEquals(BusinessType.WARUNG_SEMBAKO, profile.businessType)
        assertEquals(OperatingMode.RETAIL_PRIMARY, profile.operatingMode)
        assertFalse("PPOB must not be active by default for standard warung", profile.hasActivity(BusinessActivity.ACTIVITY_DIGITAL_VOUCHER))
        assertFalse("CAP_DIGITAL_ITEMS must not be active by default for standard warung", profile.hasCapability(BusinessCapability.CAP_DIGITAL_ITEMS))
        assertTrue(profile.hasCapability(BusinessCapability.CAP_INVENTORY_STOCK))
    }

    @Test
    fun test03_warungWithPpobBecomesHybridMixed() {
        val profile = BusinessTaxonomyRegistry.resolve(
            primaryType = "WARUNG_SEMBAKO",
            secondaryActivities = setOf(BusinessActivity.ACTIVITY_DIGITAL_VOUCHER.id)
        )
        // BusinessType remains WARUNG_SEMBAKO
        assertEquals(BusinessType.WARUNG_SEMBAKO, profile.businessType)
        assertEquals(OperatingMode.HYBRID_MIXED, profile.operatingMode)
        assertTrue(profile.hasActivity(BusinessActivity.ACTIVITY_GOODS_SELLING))
        assertTrue(profile.hasActivity(BusinessActivity.ACTIVITY_DIGITAL_VOUCHER))
        assertTrue(profile.hasCapability(BusinessCapability.CAP_INVENTORY_STOCK))
        assertTrue(profile.hasCapability(BusinessCapability.CAP_DIGITAL_ITEMS))
    }

    @Test
    fun test04_apotekWithPpobBecomesHybridMixed() {
        val profile = BusinessTaxonomyRegistry.resolve(
            primaryType = "APOTEK_OBAT",
            secondaryActivities = setOf(BusinessActivity.ACTIVITY_DIGITAL_VOUCHER.id)
        )
        assertEquals(BusinessType.APOTEK_OBAT, profile.businessType)
        assertEquals(OperatingMode.HYBRID_MIXED, profile.operatingMode)
        assertTrue(profile.hasActivity(BusinessActivity.ACTIVITY_GOODS_SELLING))
        assertTrue(profile.hasActivity(BusinessActivity.ACTIVITY_DIGITAL_VOUCHER))
        assertTrue(profile.hasCapability(BusinessCapability.CAP_DIGITAL_ITEMS))
    }

    @Test
    fun test05_serviceWithPpobBecomesHybridMixed() {
        // Laundry + PPOB
        val laundryProfile = BusinessTaxonomyRegistry.resolve(
            primaryType = "LAUNDRY",
            secondaryActivities = setOf(BusinessActivity.ACTIVITY_DIGITAL_VOUCHER.id)
        )
        assertEquals(BusinessType.LAUNDRY, laundryProfile.businessType)
        assertEquals(OperatingMode.HYBRID_MIXED, laundryProfile.operatingMode)
        assertTrue(laundryProfile.hasActivity(BusinessActivity.ACTIVITY_SERVICE_LABOR))
        assertTrue(laundryProfile.hasActivity(BusinessActivity.ACTIVITY_DIGITAL_VOUCHER))

        // Bengkel (Service + Goods) + PPOB
        val bengkelProfile = BusinessTaxonomyRegistry.resolve(
            primaryType = "BENGKEL_MOTOR_MOBIL",
            secondaryActivities = setOf(BusinessActivity.ACTIVITY_DIGITAL_VOUCHER.id)
        )
        assertEquals(BusinessType.BENGKEL_MOTOR_MOBIL, bengkelProfile.businessType)
        assertEquals(OperatingMode.HYBRID_MIXED, bengkelProfile.operatingMode)
        assertTrue(bengkelProfile.hasCapability(BusinessCapability.CAP_DIGITAL_ITEMS))
    }

    @Test
    fun test06_konterPulsaHpModes() {
        // Konter with PPOB-Only (explicit exact activities with only digital)
        val ppobOnlyMode = OperatingModeResolver.resolveFromExactActivities(
            businessType = BusinessType.KONTER_PULSA_HP,
            exactActivities = setOf(BusinessActivity.ACTIVITY_DIGITAL_VOUCHER)
        )
        assertEquals(OperatingMode.PPOB_ONLY, ppobOnlyMode)

        // Konter with both Digital and Physical accessories (Hybrid)
        val hybridMode = OperatingModeResolver.resolveFromExactActivities(
            businessType = BusinessType.KONTER_PULSA_HP,
            exactActivities = setOf(
                BusinessActivity.ACTIVITY_DIGITAL_VOUCHER,
                BusinessActivity.ACTIVITY_GOODS_SELLING
            )
        )
        assertEquals(OperatingMode.HYBRID_MIXED, hybridMode)
    }

    @Test
    fun test07_serviceOnlyMode() {
        val pureServiceMode = OperatingModeResolver.resolveFromExactActivities(
            businessType = BusinessType.LAUNDRY,
            exactActivities = setOf(BusinessActivity.ACTIVITY_SERVICE_LABOR)
        )
        assertEquals(OperatingMode.SERVICE_PRIMARY, pureServiceMode)
    }

    @Test
    fun test08_goodsAndServiceCombinationWithoutDigitalIsHybrid() {
        val hybridNoDigital = OperatingModeResolver.resolveFromExactActivities(
            businessType = BusinessType.BENGKEL_MOTOR_MOBIL,
            exactActivities = setOf(
                BusinessActivity.ACTIVITY_SERVICE_LABOR,
                BusinessActivity.ACTIVITY_GOODS_SELLING
            )
        )
        assertEquals(OperatingMode.HYBRID_MIXED, hybridNoDigital)
    }

    @Test
    fun test09_invalidAndUnknownInputsHandling() {
        // Blank or unknown business type strings
        val mode1 = OperatingModeResolver.resolve(
            primaryType = "RANDOM_UNKNOWN_TYPE_XYZ",
            secondaryActivities = emptySet()
        )
        assertEquals(OperatingMode.RETAIL_PRIMARY, mode1)

        // Invalid activity strings are filtered out
        val mode2 = OperatingModeResolver.resolve(
            primaryType = "WARUNG_SEMBAKO",
            secondaryActivities = setOf("INVALID_ACTIVITY_1", "CORRUPT_STRING")
        )
        assertEquals(OperatingMode.RETAIL_PRIMARY, mode2)
    }

    @Test
    fun test10_activityChangesPreserveBusinessTypeIdentity() {
        // Step 1: Start as Warung Kelontong (Retail Primary)
        val initialProfile = BusinessTaxonomyRegistry.resolve("WARUNG_SEMBAKO", emptySet())
        assertEquals(BusinessType.WARUNG_SEMBAKO, initialProfile.businessType)
        assertEquals(OperatingMode.RETAIL_PRIMARY, initialProfile.operatingMode)

        // Step 2: Add PPOB activity
        val hybridProfile = BusinessTaxonomyRegistry.resolve(
            "WARUNG_SEMBAKO",
            setOf(BusinessActivity.ACTIVITY_DIGITAL_VOUCHER.id)
        )
        assertEquals(BusinessType.WARUNG_SEMBAKO, hybridProfile.businessType)
        assertEquals(OperatingMode.HYBRID_MIXED, hybridProfile.operatingMode)

        // Step 3: Remove PPOB activity
        val revertedProfile = BusinessTaxonomyRegistry.resolve("WARUNG_SEMBAKO", emptySet())
        assertEquals(BusinessType.WARUNG_SEMBAKO, revertedProfile.businessType)
        assertEquals(OperatingMode.RETAIL_PRIMARY, revertedProfile.operatingMode)
    }

    @Test
    fun test11_operatingModeEnumProperties() {
        val allModes = OperatingMode.entries
        assertEquals(4, allModes.size)

        allModes.forEach { mode ->
            assertNotNull(mode.id)
            assertNotNull(mode.displayName)
            assertNotNull(mode.description)
            assertEquals(mode, OperatingMode.fromId(mode.id))
            assertEquals(mode, OperatingMode.fromId(mode.name))
        }

        assertEquals(null, OperatingMode.fromId(null))
        assertEquals(null, OperatingMode.fromId(""))
        assertEquals(null, OperatingMode.fromId("NON_EXISTENT"))
    }
}
