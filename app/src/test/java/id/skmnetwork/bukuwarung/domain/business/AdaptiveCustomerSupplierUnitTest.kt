package id.skmnetwork.bukuwarung.domain.business

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Gate G9 — Adaptive Customer, Debt & Supplier UI Unit Tests
 * Validates that BusinessTaxonomyRegistry resolution properly drives adaptive customer,
 * debt/receivable, and supplier terminology across different business presets.
 */
class AdaptiveCustomerSupplierUnitTest {

    @Test
    fun testDefaultWarungSembakoCustomerSupplierTerminology() {
        val profile = BusinessTaxonomyRegistry.resolve(
            primaryType = BusinessType.WARUNG_SEMBAKO.name
        )

        assertEquals("Pelanggan", profile.terminology.customerLabel)
        assertEquals("Supplier", profile.terminology.supplierLabel)
        assertEquals("Hutang", profile.terminology.debtLabel)
    }

    @Test
    fun testKlinikKesehatanCustomerTerminology() {
        val profile = BusinessTaxonomyRegistry.resolve(
            primaryType = BusinessType.APOTEK_OBAT.id,
            secondaryActivities = emptySet()
        )

        assertEquals("Pasien", profile.terminology.customerLabel)
        assertEquals("Supplier", profile.terminology.supplierLabel)
        assertEquals("Hutang", profile.terminology.debtLabel)
    }

    @Test
    fun testKedaiKafeAndBengkelCustomerSupplierTerminology() {
        val kafeProfile = BusinessTaxonomyRegistry.resolve(
            primaryType = BusinessType.KEDAI_KAFE.id,
            secondaryActivities = emptySet()
        )
        assertEquals("Pelanggan", kafeProfile.terminology.customerLabel)
        assertEquals("Supplier", kafeProfile.terminology.supplierLabel)

        val bengkelProfile = BusinessTaxonomyRegistry.resolve(
            primaryType = BusinessType.BENGKEL_MOTOR_MOBIL.id,
            secondaryActivities = emptySet()
        )
        assertEquals("Pelanggan", bengkelProfile.terminology.customerLabel)
        assertEquals("Supplier", bengkelProfile.terminology.supplierLabel)
    }

    @Test
    fun testFallbackForBlankOrInvalidBusinessType() {
        val blankProfile = BusinessTaxonomyRegistry.resolve(
            primaryType = "",
            secondaryActivities = emptySet()
        )
        assertEquals(BusinessType.WARUNG_SEMBAKO, blankProfile.businessType)
        assertEquals("Pelanggan", blankProfile.terminology.customerLabel)
        assertEquals("Supplier", blankProfile.terminology.supplierLabel)
        assertEquals("Hutang", blankProfile.terminology.debtLabel)

        val invalidProfile = BusinessTaxonomyRegistry.resolve(
            primaryType = "NON_EXISTENT_TYPE_XYZ",
            secondaryActivities = emptySet()
        )
        assertEquals(BusinessType.WARUNG_SEMBAKO, invalidProfile.businessType)
        assertEquals("Pelanggan", invalidProfile.terminology.customerLabel)
        assertEquals("Supplier", invalidProfile.terminology.supplierLabel)
        assertEquals("Hutang", invalidProfile.terminology.debtLabel)
    }

    @Test
    fun testSecondaryActivitiesPreserveTerminologyIntegrity() {
        val mixedProfile = BusinessTaxonomyRegistry.resolve(
            primaryType = BusinessType.APOTEK_OBAT.id,
            secondaryActivities = setOf(
                BusinessActivity.ACTIVITY_SERVICE_LABOR.name,
                BusinessActivity.ACTIVITY_CREDIT_TABUNGAN.name
            )
        )

        assertNotNull(mixedProfile)
        assertEquals(BusinessType.APOTEK_OBAT, mixedProfile.businessType)
        assertEquals("Pasien", mixedProfile.terminology.customerLabel)
        assertEquals("Supplier", mixedProfile.terminology.supplierLabel)
        assertEquals("Hutang", mixedProfile.terminology.debtLabel)
    }
}
