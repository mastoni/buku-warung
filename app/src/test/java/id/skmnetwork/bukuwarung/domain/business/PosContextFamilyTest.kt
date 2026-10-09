package id.skmnetwork.bukuwarung.domain.business

import org.junit.Assert.assertEquals
import org.junit.Test

class PosContextFamilyTest {

    @Test
    fun all19BusinessTypesResolveToCanonicalPosContextFamilies() {
        // 1. RETAIL_CATALOG (7 types)
        assertEquals(PosContextFamily.RETAIL_CATALOG, PosContextFamily.fromBusinessType(BusinessType.WARUNG_SEMBAKO))
        assertEquals(PosContextFamily.RETAIL_CATALOG, PosContextFamily.fromBusinessType(BusinessType.MINIMARKET_RETAIL))
        assertEquals(PosContextFamily.RETAIL_CATALOG, PosContextFamily.fromBusinessType(BusinessType.TOKO_PAKAIAN))
        assertEquals(PosContextFamily.RETAIL_CATALOG, PosContextFamily.fromBusinessType(BusinessType.WARUNG_MAKAN))
        assertEquals(PosContextFamily.RETAIL_CATALOG, PosContextFamily.fromBusinessType(BusinessType.KEDAI_KAFE))
        assertEquals(PosContextFamily.RETAIL_CATALOG, PosContextFamily.fromBusinessType(BusinessType.BAKERY_KUE))
        assertEquals(PosContextFamily.RETAIL_CATALOG, PosContextFamily.fromBusinessType(BusinessType.INDUSTRI_RUMAHAN))

        // 2. COUNTER_HYBRID (4 types)
        assertEquals(PosContextFamily.COUNTER_HYBRID, PosContextFamily.fromBusinessType(BusinessType.TOKO_ELEKTRONIK))
        assertEquals(PosContextFamily.COUNTER_HYBRID, PosContextFamily.fromBusinessType(BusinessType.TOKO_BANGUNAN))
        assertEquals(PosContextFamily.COUNTER_HYBRID, PosContextFamily.fromBusinessType(BusinessType.APOTEK_OBAT))
        assertEquals(PosContextFamily.COUNTER_HYBRID, PosContextFamily.fromBusinessType(BusinessType.PERCETAKAN_FOTOCOPY))

        // 3. SERVICE_QUEUE (7 types)
        assertEquals(PosContextFamily.SERVICE_QUEUE, PosContextFamily.fromBusinessType(BusinessType.BENGKEL_MOTOR_MOBIL))
        assertEquals(PosContextFamily.SERVICE_QUEUE, PosContextFamily.fromBusinessType(BusinessType.CUCI_KENDARAAN))
        assertEquals(PosContextFamily.SERVICE_QUEUE, PosContextFamily.fromBusinessType(BusinessType.SERVICE_ELEKTRONIK_HP))
        assertEquals(PosContextFamily.SERVICE_QUEUE, PosContextFamily.fromBusinessType(BusinessType.LAUNDRY))
        assertEquals(PosContextFamily.SERVICE_QUEUE, PosContextFamily.fromBusinessType(BusinessType.SALON_BARBERSHOP))
        assertEquals(PosContextFamily.SERVICE_QUEUE, PosContextFamily.fromBusinessType(BusinessType.JAHIT_TAILOR))
        assertEquals(PosContextFamily.SERVICE_QUEUE, PosContextFamily.fromBusinessType(BusinessType.JASA_TEKNISI))

        // 4. DIGITAL_OPERATOR (1 type + universal PPOB_ONLY mode)
        assertEquals(PosContextFamily.DIGITAL_OPERATOR, PosContextFamily.fromBusinessType(BusinessType.KONTER_PULSA_HP))
        assertEquals(PosContextFamily.DIGITAL_OPERATOR, PosContextFamily.fromBusinessType(BusinessType.WARUNG_SEMBAKO, OperatingMode.PPOB_ONLY))
        assertEquals(PosContextFamily.DIGITAL_OPERATOR, PosContextFamily.fromBusinessType(BusinessType.BENGKEL_MOTOR_MOBIL, OperatingMode.PPOB_ONLY))
    }
}
