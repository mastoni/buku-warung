package id.skmnetwork.bukuwarung.domain.business

/**
 * Gate G3 — Business Activity Model
 * Composable operational activities for UMKM businesses.
 */
enum class BusinessActivity(
    val id: String,
    val displayName: String,
    val description: String,
    val inherentCapabilities: Set<BusinessCapability>
) {
    ACTIVITY_GOODS_SELLING(
        id = "ACTIVITY_GOODS_SELLING",
        displayName = "Jual Barang Dagangan",
        description = "Menjual produk fisik jadi langsung ke pelanggan",
        inherentCapabilities = setOf(
            BusinessCapability.CAP_INVENTORY_STOCK,
            BusinessCapability.CAP_BARCODE_SCAN,
            BusinessCapability.CAP_UNIT_PRESETS
        )
    ),
    ACTIVITY_SERVICE_LABOR(
        id = "ACTIVITY_SERVICE_LABOR",
        displayName = "Jasa & Pengerjaan",
        description = "Melayani jasa perbaikan, perawatan, atau layanan tenaga kerja",
        inherentCapabilities = setOf(
            BusinessCapability.CAP_SERVICE_ITEMS,
            BusinessCapability.CAP_UNIT_PRESETS
        )
    ),
    ACTIVITY_DIGITAL_VOUCHER(
        id = "ACTIVITY_DIGITAL_VOUCHER",
        displayName = "Produk Digital & Pulsa",
        description = "Menjual pulsa, paket data, token PLN, atau voucher digital",
        inherentCapabilities = setOf(
            BusinessCapability.CAP_DIGITAL_ITEMS
        )
    ),
    ACTIVITY_RAW_MATERIALS(
        id = "ACTIVITY_RAW_MATERIALS",
        displayName = "Bahan Baku & Bahan Mentah",
        description = "Mengelola stok bahan mentah untuk proses olahan atau produksi",
        inherentCapabilities = setOf(
            BusinessCapability.CAP_INVENTORY_STOCK,
            BusinessCapability.CAP_UNIT_PRESETS
        )
    ),
    ACTIVITY_WHOLESALE_PURCHASE(
        id = "ACTIVITY_WHOLESALE_PURCHASE",
        displayName = "Kulakan & Pembelian Supplier",
        description = "Membeli barang dagangan atau bahan baku secara grosir / tempo dari supplier",
        inherentCapabilities = setOf(
            BusinessCapability.CAP_SUPPLIER_PAYABLE
        )
    ),
    ACTIVITY_CREDIT_TABUNGAN(
        id = "ACTIVITY_CREDIT_TABUNGAN",
        displayName = "Pencatatan Hutang / Kasbon",
        description = "Mencatat transaksi tempo, kasbon pelanggan, atau piutang",
        inherentCapabilities = setOf(
            BusinessCapability.CAP_CUSTOMER_DEBT
        )
    );

    companion object {
        fun fromId(id: String): BusinessActivity? = entries.find { it.id.equals(id, ignoreCase = true) }
    }
}
