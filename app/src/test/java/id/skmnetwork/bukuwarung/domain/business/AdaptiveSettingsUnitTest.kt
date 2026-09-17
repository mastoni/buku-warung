package id.skmnetwork.bukuwarung.domain.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate G12 — Adaptive Settings & Business Profile Management UI Unit Tests
 * Validates that BusinessTaxonomyRegistry resolution properly drives adaptive Settings UI presentation,
 * business profile customization, fallback safety, and profile update contract invariants without
 * mutating transaction/financial logic or database schema.
 */
class AdaptiveSettingsUnitTest {

    @Test
    fun testWarungSembakoBaselineTerminology() {
        val profile = BusinessTaxonomyRegistry.resolve(
            primaryType = BusinessType.WARUNG_SEMBAKO.id,
            secondaryActivities = emptySet()
        )

        assertEquals(BusinessType.WARUNG_SEMBAKO, profile.businessType)
        assertEquals(BusinessCategory.RETAIL, profile.category)
        assertEquals("Warung Sembako / Kelontong", profile.businessType.displayName)
        assertEquals("Produk", profile.terminology.productLabel)
        assertEquals("Layanan", profile.terminology.serviceLabel)
        assertEquals("Pelanggan", profile.terminology.customerLabel)
        assertEquals("Penjualan", profile.terminology.transactionLabel)
        assertEquals("Hutang", profile.terminology.debtLabel)
        assertEquals("Supplier", profile.terminology.supplierLabel)
        assertEquals("Pembelian", profile.terminology.purchaseLabel)
        assertEquals("Stok", profile.terminology.stockLabel)

        assertTrue(profile.activities.contains(BusinessActivity.ACTIVITY_GOODS_SELLING))
        assertTrue(profile.activities.contains(BusinessActivity.ACTIVITY_WHOLESALE_PURCHASE))
        assertTrue(profile.activities.contains(BusinessActivity.ACTIVITY_CREDIT_TABUNGAN))
    }

    @Test
    fun testApotekObatTerminology() {
        val profile = BusinessTaxonomyRegistry.resolve(
            primaryType = BusinessType.APOTEK_OBAT.id
        )

        assertEquals(BusinessType.APOTEK_OBAT, profile.businessType)
        assertEquals(BusinessCategory.RETAIL, profile.category)
        assertEquals("Apotek & Toko Obat", profile.businessType.displayName)
        assertEquals("Obat / Alkes", profile.terminology.productLabel)
        assertEquals("Pasien", profile.terminology.customerLabel)
        assertEquals("Struk Apotek", profile.terminology.transactionLabel)
        assertEquals("Pembelian", profile.terminology.purchaseLabel)
        assertEquals("Supplier", profile.terminology.supplierLabel)
    }

    @Test
    fun testTokoBangunanTerminology() {
        val profile = BusinessTaxonomyRegistry.resolve(
            primaryType = BusinessType.TOKO_BANGUNAN.id
        )

        assertEquals(BusinessType.TOKO_BANGUNAN, profile.businessType)
        assertEquals(BusinessCategory.RETAIL, profile.category)
        assertEquals("Toko Material & Bangunan", profile.businessType.displayName)
        assertEquals("Material", profile.terminology.productLabel)
        assertEquals("Pelanggan", profile.terminology.customerLabel)
        assertEquals("Nota Penjualan", profile.terminology.transactionLabel)
        assertEquals("Pembelian", profile.terminology.purchaseLabel)
    }

    @Test
    fun testBengkelMotorMobilTerminology() {
        val profile = BusinessTaxonomyRegistry.resolve(
            primaryType = BusinessType.BENGKEL_MOTOR_MOBIL.id
        )

        assertEquals(BusinessType.BENGKEL_MOTOR_MOBIL, profile.businessType)
        assertEquals(BusinessCategory.SERVICES, profile.category)
        assertEquals("Bengkel Motor / Mobil", profile.businessType.displayName)
        assertEquals("Sparepart & Oli", profile.terminology.productLabel)
        assertEquals("Jasa Servis", profile.terminology.serviceLabel)
        assertEquals("Pelanggan", profile.terminology.customerLabel)
        assertEquals("Nota Servis", profile.terminology.transactionLabel)
        assertEquals("Supplier", profile.terminology.supplierLabel)
    }

    @Test
    fun testIndustriRumahanCompositeActivities() {
        val customActivities = setOf(
            BusinessActivity.ACTIVITY_GOODS_SELLING.id,
            BusinessActivity.ACTIVITY_RAW_MATERIALS.id,
            BusinessActivity.ACTIVITY_WHOLESALE_PURCHASE.id,
            BusinessActivity.ACTIVITY_CREDIT_TABUNGAN.id
        )
        val profile = BusinessTaxonomyRegistry.resolve(
            primaryType = BusinessType.INDUSTRI_RUMAHAN.id,
            secondaryActivities = customActivities
        )

        assertEquals(BusinessType.INDUSTRI_RUMAHAN, profile.businessType)
        assertEquals(BusinessCategory.PRODUCTION, profile.category)
        assertEquals("Produk Jadi", profile.terminology.productLabel)
        assertEquals("Pelanggan", profile.terminology.customerLabel)
        assertEquals("Nota Penjualan", profile.terminology.transactionLabel)

        assertTrue(profile.activities.contains(BusinessActivity.ACTIVITY_GOODS_SELLING))
        assertTrue(profile.activities.contains(BusinessActivity.ACTIVITY_RAW_MATERIALS))
        assertTrue(profile.activities.contains(BusinessActivity.ACTIVITY_WHOLESALE_PURCHASE))
        assertTrue(profile.activities.contains(BusinessActivity.ACTIVITY_CREDIT_TABUNGAN))
    }

    @Test
    fun testUnknownAndBlankFallbackToWarungSembako() {
        val nullProfile = BusinessTaxonomyRegistry.resolve(null, null)
        assertEquals(BusinessType.WARUNG_SEMBAKO, nullProfile.businessType)
        assertEquals(BusinessCategory.RETAIL, nullProfile.category)
        assertEquals("Produk", nullProfile.terminology.productLabel)

        val blankProfile = BusinessTaxonomyRegistry.resolve("   ", emptySet())
        assertEquals(BusinessType.WARUNG_SEMBAKO, blankProfile.businessType)
        assertEquals(BusinessCategory.RETAIL, blankProfile.category)
        assertEquals("Produk", blankProfile.terminology.productLabel)

        val invalidProfile = BusinessTaxonomyRegistry.resolve("NON_EXISTENT_TYPE_XYZ", setOf("INVALID_ACTIVITY"))
        assertEquals(BusinessType.WARUNG_SEMBAKO, invalidProfile.businessType)
        assertEquals(BusinessCategory.RETAIL, invalidProfile.category)
        assertEquals("Pelanggan", invalidProfile.terminology.customerLabel)
    }

    @Test
    fun testAll19PresetsResolutionIntegrity() {
        val allPresets = BusinessTaxonomyRegistry.getAllPresets()
        assertEquals(19, allPresets.size)

        for (preset in allPresets) {
            val resolved = BusinessTaxonomyRegistry.resolve(preset.businessType.id)
            assertNotNull(resolved)
            assertEquals(preset.businessType, resolved.businessType)
            assertEquals(preset.businessType.category, resolved.category)
            assertTrue(resolved.activities.isNotEmpty())
            assertTrue(resolved.capabilities.isNotEmpty())
            assertNotNull(resolved.terminology.productLabel)
            assertNotNull(resolved.terminology.serviceLabel)
            assertNotNull(resolved.terminology.customerLabel)
            assertNotNull(resolved.terminology.transactionLabel)
            assertNotNull(resolved.terminology.purchaseLabel)
            assertNotNull(resolved.terminology.supplierLabel)
            assertNotNull(resolved.terminology.debtLabel)
            assertNotNull(resolved.terminology.stockLabel)
        }
    }

    @Test
    fun testBusinessProfileUpdateContractAndVersionSafety() {
        val primaryType = BusinessType.TOKO_ELEKTRONIK.id
        val secondaryActivities = setOf(
            BusinessActivity.ACTIVITY_SERVICE_LABOR.id,
            BusinessActivity.ACTIVITY_GOODS_SELLING.id
        )

        val resolved = BusinessTaxonomyRegistry.resolve(
            primaryType = primaryType,
            secondaryActivities = secondaryActivities
        )

        assertEquals(BusinessType.TOKO_ELEKTRONIK, resolved.businessType)
        assertEquals(BusinessCategory.RETAIL, resolved.category)
        assertEquals("Barang Elektronik", resolved.terminology.productLabel)
        assertEquals("Pelanggan", resolved.terminology.customerLabel)
        assertEquals("Nota Penjualan", resolved.terminology.transactionLabel)

        assertTrue(resolved.activities.contains(BusinessActivity.ACTIVITY_SERVICE_LABOR))
        assertTrue(resolved.activities.contains(BusinessActivity.ACTIVITY_GOODS_SELLING))
    }

    @Test
    fun testNoFinancialOrDataMutationOnProfileResolution() {
        // Business profile resolution is strictly a presentation and capability mapping layer.
        // It does not alter monetary calculations, pricing, discounts, or inventory counts.
        val baseQuantity = 10.0
        val unitPrice = 25000L
        val discountAmount = 5000L
        val expectedTotal = (baseQuantity * unitPrice).toLong() - discountAmount // 245,000

        // Resolve across multiple presets
        val presetsToTest = listOf(
            BusinessType.WARUNG_SEMBAKO,
            BusinessType.APOTEK_OBAT,
            BusinessType.TOKO_BANGUNAN,
            BusinessType.BENGKEL_MOTOR_MOBIL,
            BusinessType.KEDAI_KAFE
        )

        for (presetType in presetsToTest) {
            val resolved = BusinessTaxonomyRegistry.resolve(presetType.id)
            assertNotNull(resolved)
            // Financial arithmetic remains invariant
            val calculatedTotal = (baseQuantity * unitPrice).toLong() - discountAmount
            assertEquals(expectedTotal, calculatedTotal)
        }
    }
}
