package id.skmnetwork.bukuwarung.domain.business

/**
 * Release 3 — Operating Mode
 * Defines the high-level shell and operational interaction mode of Buku Warung.
 */
enum class OperatingMode(
    val id: String,
    val displayName: String,
    val description: String
) {
    RETAIL_PRIMARY(
        id = "RETAIL_PRIMARY",
        displayName = "Retail & Barang Fisik",
        description = "Fokus pada penjualan barang fisik dengan katalog dan manajemen stok"
    ),
    SERVICE_PRIMARY(
        id = "SERVICE_PRIMARY",
        displayName = "Jasa & Layanan",
        description = "Fokus pada layanan/jasa dan antrean pengerjaan tanpa stok fisik"
    ),
    HYBRID_MIXED(
        id = "HYBRID_MIXED",
        displayName = "Usaha Campuran / Hybrid",
        description = "Kombinasi multi-aktivitas (Retail + PPOB, Jasa + Retail, Bengkel + Sparepart)"
    ),
    PPOB_ONLY(
        id = "PPOB_ONLY",
        displayName = "PPOB & Pulsa Saja",
        description = "Khusus transaksi produk digital/PPOB tanpa katalog barang fisik dan tanpa stok"
    );

    companion object {
        fun fromId(id: String?): OperatingMode? {
            if (id.isNullOrBlank()) return null
            return entries.find { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) }
        }
    }
}
