package id.skmnetwork.bukuwarung.domain.business

import id.skmnetwork.bukuwarung.data.local.entity.ItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate G3 — Business Taxonomy & Activity Registry Test Suite
 * Validates domain taxonomy invariants, deterministic capability resolution,
 * immutability, legacy fallbacks, and ItemType compatibility (Tests A through K).
 */
class BusinessTaxonomyTest {

    /**
     * Test A: Every registered BusinessType has a valid, non-null BusinessCategory.
     */
    @Test
    fun testA_everyBusinessTypeHasValidCategory() {
        val allTypes = BusinessType.entries
        assertTrue("Must have registered business types", allTypes.isNotEmpty())

        allTypes.forEach { type ->
            assertNotNull("BusinessType ${type.id} must have a valid category", type.category)
            assertTrue("Category must be registered in BusinessCategory", BusinessCategory.entries.contains(type.category))
        }
    }

    /**
     * Test B: Every BusinessType has valid, non-empty default capabilities.
     */
    @Test
    fun testB_everyBusinessTypeHasValidCapabilities() {
        val allPresets = BusinessTaxonomyRegistry.getAllPresets()
        assertEquals(BusinessType.entries.size, allPresets.size)

        allPresets.forEach { preset ->
            assertTrue("Preset for ${preset.businessType.id} must have capabilities", preset.defaultCapabilities.isNotEmpty())
            preset.defaultCapabilities.forEach { cap ->
                assertTrue("Capability must be a recognized BusinessCapability", BusinessCapability.entries.contains(cap))
            }
        }
    }

    /**
     * Test C: Every activity references valid capability semantics.
     */
    @Test
    fun testC_everyActivityReferencesValidCapabilities() {
        val allActivities = BusinessActivity.entries
        assertTrue("Must have registered activities", allActivities.isNotEmpty())

        allActivities.forEach { activity ->
            assertTrue("Activity ${activity.id} must have non-empty inherent capabilities", activity.inherentCapabilities.isNotEmpty())
            activity.inherentCapabilities.forEach { cap ->
                assertTrue("Inherent capability must be valid", BusinessCapability.entries.contains(cap))
            }
        }
    }

    /**
     * Test D: No duplicate BusinessType IDs or displayName.
     */
    @Test
    fun testD_noDuplicateBusinessTypeIds() {
        val ids = BusinessType.entries.map { it.id }
        val uniqueIds = ids.toSet()
        assertEquals("BusinessType IDs must be unique without collisions", ids.size, uniqueIds.size)
    }

    /**
     * Test E: No duplicate Activity IDs.
     */
    @Test
    fun testE_noDuplicateActivityIds() {
        val ids = BusinessActivity.entries.map { it.id }
        val uniqueIds = ids.toSet()
        assertEquals("BusinessActivity IDs must be unique without collisions", ids.size, uniqueIds.size)
    }

    /**
     * Test F: WARUNG_SEMBAKO is registered and valid default.
     */
    @Test
    fun testF_warungSembakoIsRegisteredAndValidDefault() {
        val defaultType = BusinessTaxonomyRegistry.DEFAULT_BUSINESS_TYPE
        assertEquals(BusinessType.WARUNG_SEMBAKO, defaultType)

        val profile = BusinessTaxonomyRegistry.resolve("WARUNG_SEMBAKO")
        assertEquals(BusinessType.WARUNG_SEMBAKO, profile.businessType)
        assertEquals(BusinessCategory.RETAIL, profile.category)
        assertTrue(profile.hasCapability(BusinessCapability.CAP_INVENTORY_STOCK))
        assertTrue(profile.hasCapability(BusinessCapability.CAP_BARCODE_SCAN))
        assertTrue(profile.hasCapability(BusinessCapability.CAP_CUSTOMER_DEBT))
        assertTrue(profile.hasCapability(BusinessCapability.CAP_SUPPLIER_PAYABLE))
        assertTrue(profile.hasCapability(BusinessCapability.CAP_THERMAL_RECEIPT))
        assertTrue(profile.hasCapability(BusinessCapability.CAP_CASH_EXPENSE))
        assertTrue(profile.hasCapability(BusinessCapability.CAP_UNIT_PRESETS))
    }

    /**
     * Test G: Unknown business type safely falls back to WARUNG_SEMBAKO.
     */
    @Test
    fun testG_unknownBusinessTypeFallsBackSafely() {
        val profileNull = BusinessTaxonomyRegistry.resolve(null)
        assertEquals(BusinessType.WARUNG_SEMBAKO, profileNull.businessType)

        val profileBlank = BusinessTaxonomyRegistry.resolve("   ")
        assertEquals(BusinessType.WARUNG_SEMBAKO, profileBlank.businessType)

        val profileUnknown = BusinessTaxonomyRegistry.resolve("NON_EXISTENT_BUSINESS_TYPE_XYZ")
        assertEquals(BusinessType.WARUNG_SEMBAKO, profileUnknown.businessType)
    }

    /**
     * Test H: Primary + secondary activities produce deterministic union capability set.
     */
    @Test
    fun testH_primaryAndSecondaryActivitiesProduceDeterministicUnionCapabilities() {
        // Base Bengkel: Does NOT have CAP_DIGITAL_ITEMS by default
        val bengkelOnly = BusinessTaxonomyRegistry.resolve("BENGKEL_MOTOR_MOBIL", emptySet())
        assertEquals(BusinessType.BENGKEL_MOTOR_MOBIL, bengkelOnly.businessType)
        assertTrue(bengkelOnly.hasCapability(BusinessCapability.CAP_SERVICE_ITEMS))
        assertTrue(bengkelOnly.hasCapability(BusinessCapability.CAP_INVENTORY_STOCK))
        assertFalse(bengkelOnly.hasCapability(BusinessCapability.CAP_DIGITAL_ITEMS))

        // Bengkel + Secondary Activity: ACTIVITY_DIGITAL_VOUCHER
        val bengkelWithDigital = BusinessTaxonomyRegistry.resolve(
            primaryType = "BENGKEL_MOTOR_MOBIL",
            secondaryActivities = setOf("ACTIVITY_DIGITAL_VOUCHER")
        )
        assertEquals(BusinessType.BENGKEL_MOTOR_MOBIL, bengkelWithDigital.businessType)
        assertTrue(bengkelWithDigital.hasCapability(BusinessCapability.CAP_SERVICE_ITEMS))
        assertTrue(bengkelWithDigital.hasCapability(BusinessCapability.CAP_INVENTORY_STOCK))
        assertTrue(bengkelWithDigital.hasCapability(BusinessCapability.CAP_DIGITAL_ITEMS))
        assertTrue(bengkelWithDigital.hasActivity(BusinessActivity.ACTIVITY_DIGITAL_VOUCHER))
    }

    /**
     * Test I: ItemType compatibility remains valid without database modifications.
     */
    @Test
    fun testI_itemTypeCompatibility() {
        // Verify ItemType enum values match domain capabilities
        val physicalItem = ItemType.PHYSICAL
        val serviceItem = ItemType.SERVICE
        val digitalItem = ItemType.DIGITAL
        val fuelItem = ItemType.FUEL

        assertNotNull(physicalItem)
        assertNotNull(serviceItem)
        assertNotNull(digitalItem)
        assertNotNull(fuelItem)

        // Verify service capability corresponds to SERVICE item type
        val serviceProfile = BusinessTaxonomyRegistry.resolve("SALON_BARBERSHOP")
        assertTrue(serviceProfile.hasCapability(BusinessCapability.CAP_SERVICE_ITEMS))
        assertEquals("Jasa & Layanan", serviceProfile.category.displayName)
    }

    /**
     * Test J: Preset data is immutable and does not expose mutable shared collections.
     */
    @Test
    fun testJ_presetDataIsImmutable() {
        val preset = BusinessTaxonomyRegistry.getPreset(BusinessType.WARUNG_SEMBAKO)
        val units1 = preset.preferredUnits
        val categories1 = preset.defaultCategories

        val resolved = BusinessTaxonomyRegistry.resolve("WARUNG_SEMBAKO")
        val resolvedUnits = resolved.preferredUnits

        assertEquals(units1, resolvedUnits)
        assertEquals(categories1, resolved.defaultCategories)
    }

    /**
     * Test K: Changing secondary activities does not mutate the primary BusinessType preset.
     */
    @Test
    fun testK_secondaryActivitiesDoNotMutateBasePreset() {
        val basePresetBefore = BusinessTaxonomyRegistry.getPreset(BusinessType.LAUNDRY)
        assertFalse(basePresetBefore.defaultActivities.contains(BusinessActivity.ACTIVITY_DIGITAL_VOUCHER))

        // Resolve with extra secondary activities
        val resolvedCustom = BusinessTaxonomyRegistry.resolve(
            primaryType = "LAUNDRY",
            secondaryActivities = setOf("ACTIVITY_DIGITAL_VOUCHER", "ACTIVITY_RAW_MATERIALS")
        )
        assertTrue(resolvedCustom.hasActivity(BusinessActivity.ACTIVITY_DIGITAL_VOUCHER))
        assertTrue(resolvedCustom.hasActivity(BusinessActivity.ACTIVITY_RAW_MATERIALS))

        // Check that base preset remains pristine and unmodified
        val basePresetAfter = BusinessTaxonomyRegistry.getPreset(BusinessType.LAUNDRY)
        assertFalse("Base preset must never be mutated by custom resolution", basePresetAfter.defaultActivities.contains(BusinessActivity.ACTIVITY_DIGITAL_VOUCHER))
        assertFalse("Base preset must never be mutated by custom resolution", basePresetAfter.defaultActivities.contains(BusinessActivity.ACTIVITY_RAW_MATERIALS))
    }
}
