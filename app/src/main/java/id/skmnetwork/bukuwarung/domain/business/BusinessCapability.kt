package id.skmnetwork.bukuwarung.domain.business

/**
 * Gate G3 — Business Capability Model
 * Granular functional capabilities used by adaptive domain and UI layers.
 */
enum class BusinessCapability(
    val id: String,
    val displayName: String,
    val description: String
) {
    CAP_INVENTORY_STOCK(
        id = "CAP_INVENTORY_STOCK",
        displayName = "Manajemen Stok Barang",
        description = "Pelacakan stok fisik, stok minimum, dan peringatan stok habis"
    ),
    CAP_SERVICE_ITEMS(
        id = "CAP_SERVICE_ITEMS",
        displayName = "Item Jasa & Layanan",
        description = "Pencatatan item layanan/jasa tanpa batasan stok fisik"
    ),
    CAP_DIGITAL_ITEMS(
        id = "CAP_DIGITAL_ITEMS",
        displayName = "Produk Digital & Pulsa",
        description = "Pencatatan transaksi voucher, pulsa, token, dan PPOB"
    ),
    CAP_BARCODE_SCAN(
        id = "CAP_BARCODE_SCAN",
        displayName = "Pemindaian Barcode",
        description = "Dukungan scan barcode produk menggunakan kamera atau scanner"
    ),
    CAP_CUSTOMER_DEBT(
        id = "CAP_CUSTOMER_DEBT",
        displayName = "Catatan Hutang Pelanggan",
        description = "Pencatatan kasbon, batas hutang, dan cicilan pelanggan"
    ),
    CAP_SUPPLIER_PAYABLE(
        id = "CAP_SUPPLIER_PAYABLE",
        displayName = "Catatan Hutang Supplier",
        description = "Pencatatan pembelian tempo dan pembayaran ke supplier"
    ),
    CAP_THERMAL_RECEIPT(
        id = "CAP_THERMAL_RECEIPT",
        displayName = "Cetak Struk Thermal",
        description = "Pencetakan nota atau struk transaksi via printer thermal Bluetooth/USB"
    ),
    CAP_CASH_EXPENSE(
        id = "CAP_CASH_EXPENSE",
        displayName = "Catatan Pengeluaran Kas",
        description = "Pencatatan arus kas keluar untuk biaya operasional warung"
    ),
    CAP_UNIT_PRESETS(
        id = "CAP_UNIT_PRESETS",
        displayName = "Preset Satuan Ukuran",
        description = "Daftar rekomendasi satuan unit yang sesuai dengan jenis usaha"
    );

    companion object {
        fun fromId(id: String): BusinessCapability? = entries.find { it.id.equals(id, ignoreCase = true) }
    }
}
