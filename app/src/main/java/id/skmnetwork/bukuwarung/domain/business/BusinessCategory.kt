package id.skmnetwork.bukuwarung.domain.business

/**
 * Gate G3 — Business Category Model
 * Top-level domain classification for Indonesian UMKM profiles.
 */
enum class BusinessCategory(
    val id: String,
    val displayName: String,
    val description: String
) {
    RETAIL(
        id = "RETAIL",
        displayName = "Toko & Ritel",
        description = "Penjualan barang dagangan langsung kepada konsumen akhir"
    ),
    SERVICES(
        id = "SERVICES",
        displayName = "Jasa & Layanan",
        description = "Layanan keahlian, perbaikan, perawatan, dan pengerjaan"
    ),
    FOOD_BEV(
        id = "FOOD_BEV",
        displayName = "Kuliner (F&B)",
        description = "Penyediaan makanan, minuman, kafe, dan olahan kuliner"
    ),
    PRODUCTION(
        id = "PRODUCTION",
        displayName = "Produksi & Kerajinan",
        description = "Pengolahan bahan baku menjadi barang jadi skala UMKM"
    );

    companion object {
        fun fromId(id: String): BusinessCategory? = entries.find { it.id.equals(id, ignoreCase = true) }
    }
}
