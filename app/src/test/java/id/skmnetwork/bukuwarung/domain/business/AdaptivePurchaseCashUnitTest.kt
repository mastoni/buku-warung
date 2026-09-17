package id.skmnetwork.bukuwarung.domain.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate G10 — Adaptive Purchase & Cash/Expense UI Unit Tests
 * Validates that BusinessTaxonomyRegistry resolution properly drives adaptive purchase,
 * product, supplier, debt, and cash transaction terminology across different business presets.
 */
class AdaptivePurchaseCashUnitTest {

    @Test
    fun testDefaultWarungSembakoPurchaseCashTerminology() {
        val profile = BusinessTaxonomyRegistry.resolve(
            primaryType = BusinessType.WARUNG_SEMBAKO.name
        )

        assertEquals("Pembelian", profile.terminology.purchaseLabel)
        assertEquals("Produk", profile.terminology.productLabel)
        assertEquals("Supplier", profile.terminology.supplierLabel)
        assertEquals("Hutang", profile.terminology.debtLabel)
        assertEquals("Stok", profile.terminology.stockLabel)
    }

    @Test
    fun testApotekObatPurchaseTerminology() {
        val profile = BusinessTaxonomyRegistry.resolve(
            primaryType = BusinessType.APOTEK_OBAT.id,
            secondaryActivities = emptySet()
        )

        assertEquals("Obat / Alkes", profile.terminology.productLabel)
        assertEquals("Pembelian", profile.terminology.purchaseLabel)
        assertEquals("Supplier", profile.terminology.supplierLabel)
        assertEquals("Hutang", profile.terminology.debtLabel)
    }

    @Test
    fun testTokoBangunanAndBengkelPurchaseTerminology() {
        val bangunanProfile = BusinessTaxonomyRegistry.resolve(
            primaryType = BusinessType.TOKO_BANGUNAN.id,
            secondaryActivities = emptySet()
        )
        assertEquals("Material", bangunanProfile.terminology.productLabel)
        assertEquals("Pembelian", bangunanProfile.terminology.purchaseLabel)
        assertEquals("Supplier", bangunanProfile.terminology.supplierLabel)

        val bengkelProfile = BusinessTaxonomyRegistry.resolve(
            primaryType = BusinessType.BENGKEL_MOTOR_MOBIL.id,
            secondaryActivities = emptySet()
        )
        assertEquals("Sparepart & Oli", bengkelProfile.terminology.productLabel)
        assertEquals("Jasa Servis", bengkelProfile.terminology.serviceLabel)
        assertEquals("Pembelian", bengkelProfile.terminology.purchaseLabel)
        assertEquals("Supplier", bengkelProfile.terminology.supplierLabel)
    }

    @Test
    fun testFallbackForBlankOrInvalidBusinessType() {
        val blankProfile = BusinessTaxonomyRegistry.resolve(
            primaryType = "",
            secondaryActivities = emptySet()
        )
        assertEquals(BusinessType.WARUNG_SEMBAKO, blankProfile.businessType)
        assertEquals("Pembelian", blankProfile.terminology.purchaseLabel)
        assertEquals("Produk", blankProfile.terminology.productLabel)
        assertEquals("Supplier", blankProfile.terminology.supplierLabel)
        assertEquals("Hutang", blankProfile.terminology.debtLabel)

        val invalidProfile = BusinessTaxonomyRegistry.resolve(
            primaryType = "NON_EXISTENT_PURCHASE_TYPE",
            secondaryActivities = emptySet()
        )
        assertEquals(BusinessType.WARUNG_SEMBAKO, invalidProfile.businessType)
        assertEquals("Pembelian", invalidProfile.terminology.purchaseLabel)
        assertEquals("Produk", invalidProfile.terminology.productLabel)
        assertEquals("Supplier", invalidProfile.terminology.supplierLabel)
        assertEquals("Hutang", invalidProfile.terminology.debtLabel)
    }

    @Test
    fun testSecondaryActivitiesPreservePurchaseCapabilities() {
        val compositeProfile = BusinessTaxonomyRegistry.resolve(
            primaryType = BusinessType.INDUSTRI_RUMAHAN.id,
            secondaryActivities = setOf(
                BusinessActivity.ACTIVITY_RAW_MATERIALS.name,
                BusinessActivity.ACTIVITY_WHOLESALE_PURCHASE.name
            )
        )

        assertNotNull(compositeProfile)
        assertEquals(BusinessType.INDUSTRI_RUMAHAN, compositeProfile.businessType)
        assertEquals("Produk Jadi", compositeProfile.terminology.productLabel)
        assertEquals("Pembelian", compositeProfile.terminology.purchaseLabel)
        assertEquals("Supplier", compositeProfile.terminology.supplierLabel)
        assertTrue(compositeProfile.capabilities.contains(BusinessCapability.CAP_SUPPLIER_PAYABLE))
        assertTrue(compositeProfile.capabilities.contains(BusinessCapability.CAP_INVENTORY_STOCK))
    }
}
