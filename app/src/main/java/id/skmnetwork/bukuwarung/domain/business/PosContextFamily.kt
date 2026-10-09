package id.skmnetwork.bukuwarung.domain.business

/**
 * Release 3 — POS Context Family
 * Defines the operational workspace interaction pattern for the POS screen.
 *
 * Mapped deterministically from BusinessType and OperatingMode.
 */
enum class PosContextFamily(
    val id: String,
    val displayName: String,
    val description: String
) {
    RETAIL_CATALOG(
        id = "RETAIL_CATALOG",
        displayName = "Katalog Retail Visual",
        description = "Grid visual produk cepat, filter kategori, tap tambah langsung, uang pas"
    ),
    COUNTER_HYBRID(
        id = "COUNTER_HYBRID",
        displayName = "Kasir Meja & Counter Hybrid",
        description = "Pencarian cepat SKU/tabel, info stok & spesifikasi, kuantitas presisi"
    ),
    SERVICE_QUEUE(
        id = "SERVICE_QUEUE",
        displayName = "Antrean Jasa & Servis",
        description = "Form penerimaan pengerjaan, identitas pelanggan, uang muka, tanda terima servis"
    ),
    DIGITAL_OPERATOR(
        id = "DIGITAL_OPERATOR",
        displayName = "Operator Pulsa & PPOB",
        description = "Keypad nomor tujuan, auto-detect provider, grid denom pulsa & token"
    );

    companion object {
        /**
         * Resolves the primary POS Context Family from a BusinessType and optional OperatingMode.
         */
        fun fromBusinessType(
            businessType: BusinessType,
            operatingMode: OperatingMode? = null
        ): PosContextFamily {
            if (operatingMode == OperatingMode.PPOB_ONLY) {
                return DIGITAL_OPERATOR
            }

            return when (businessType) {
                BusinessType.WARUNG_SEMBAKO,
                BusinessType.MINIMARKET_RETAIL,
                BusinessType.TOKO_PAKAIAN,
                BusinessType.WARUNG_MAKAN,
                BusinessType.KEDAI_KAFE,
                BusinessType.BAKERY_KUE,
                BusinessType.INDUSTRI_RUMAHAN -> RETAIL_CATALOG

                BusinessType.TOKO_ELEKTRONIK,
                BusinessType.TOKO_BANGUNAN,
                BusinessType.APOTEK_OBAT,
                BusinessType.PERCETAKAN_FOTOCOPY -> COUNTER_HYBRID

                BusinessType.BENGKEL_MOTOR_MOBIL,
                BusinessType.CUCI_KENDARAAN,
                BusinessType.SERVICE_ELEKTRONIK_HP,
                BusinessType.LAUNDRY,
                BusinessType.SALON_BARBERSHOP,
                BusinessType.JAHIT_TAILOR,
                BusinessType.JASA_TEKNISI -> SERVICE_QUEUE

                BusinessType.KONTER_PULSA_HP -> {
                    if (operatingMode == OperatingMode.RETAIL_PRIMARY) {
                        RETAIL_CATALOG
                    } else {
                        DIGITAL_OPERATOR
                    }
                }
            }
        }
    }
}
