package id.skmnetwork.bukuwarung.domain.business

/**
 * Gate G3 — Business Type Model
 * Comprehensive registry of supported Indonesian UMKM profiles.
 */
enum class BusinessType(
    val id: String,
    val displayName: String,
    val category: BusinessCategory,
    val description: String
) {
    // 1. RETAIL
    WARUNG_SEMBAKO(
        id = "WARUNG_SEMBAKO",
        displayName = "Warung Sembako / Kelontong",
        category = BusinessCategory.RETAIL,
        description = "Warung kebutuhan harian, sembako, makanan ringan, dan minuman"
    ),
    MINIMARKET_RETAIL(
        id = "MINIMARKET_RETAIL",
        displayName = "Minimarket / Toko Retail",
        category = BusinessCategory.RETAIL,
        description = "Toko kelontong modern atau swalayan dengan ragam barang ritel"
    ),
    TOKO_PAKAIAN(
        id = "TOKO_PAKAIAN",
        displayName = "Toko Pakaian & Fashion",
        category = BusinessCategory.RETAIL,
        description = "Penjualan baju, celana, jilbab, sepatu, dan aksesoris fashion"
    ),
    TOKO_ELEKTRONIK(
        id = "TOKO_ELEKTRONIK",
        displayName = "Toko Elektronik & Listrik",
        category = BusinessCategory.RETAIL,
        description = "Penjualan peralatan elektronik rumah tangga, lampu, dan alat listrik"
    ),
    TOKO_BANGUNAN(
        id = "TOKO_BANGUNAN",
        displayName = "Toko Material & Bangunan",
        category = BusinessCategory.RETAIL,
        description = "Penjualan semen, cat, paku, pipa, dan perlengkapan bangunan"
    ),
    APOTEK_OBAT(
        id = "APOTEK_OBAT",
        displayName = "Apotek & Toko Obat",
        category = BusinessCategory.RETAIL,
        description = "Penjualan obat-obatan, vitamin, dan alat kesehatan"
    ),
    KONTER_PULSA_HP(
        id = "KONTER_PULSA_HP",
        displayName = "Konter Pulsa & Aksesoris HP",
        category = BusinessCategory.RETAIL,
        description = "Penjualan pulsa, paket data, aksesoris handphone, dan voucher"
    ),

    // 2. SERVICES
    BENGKEL_MOTOR_MOBIL(
        id = "BENGKEL_MOTOR_MOBIL",
        displayName = "Bengkel Motor / Mobil",
        category = BusinessCategory.SERVICES,
        description = "Jasa servis kendaraan bermotor, ganti oli, dan penjualan sparepart"
    ),
    CUCI_KENDARAAN(
        id = "CUCI_KENDARAAN",
        displayName = "Cuci Kendaraan",
        category = BusinessCategory.SERVICES,
        description = "Jasa cuci motor, cuci mobil, dan salon perawatan bodi"
    ),
    SERVICE_ELEKTRONIK_HP(
        id = "SERVICE_ELEKTRONIK_HP",
        displayName = "Service HP & Elektronik",
        category = BusinessCategory.SERVICES,
        description = "Jasa reparasi smartphone, laptop, komputer, dan alat elektronik"
    ),
    LAUNDRY(
        id = "LAUNDRY",
        displayName = "Laundry Kiloan & Satuan",
        category = BusinessCategory.SERVICES,
        description = "Jasa pencucian pakaian kiloan, satuan, bed cover, dan dry cleaning"
    ),
    SALON_BARBERSHOP(
        id = "SALON_BARBERSHOP",
        displayName = "Barbershop & Salon",
        category = BusinessCategory.SERVICES,
        description = "Jasa pangkas rambut, perawatan rambut, dan perawatan kecantikan"
    ),
    JAHIT_TAILOR(
        id = "JAHIT_TAILOR",
        displayName = "Penjahit & Tailor",
        category = BusinessCategory.SERVICES,
        description = "Jasa pembuatan baju, permak pakaian, dan konveksi kecil"
    ),
    PERCETAKAN_FOTOCOPY(
        id = "PERCETAKAN_FOTOCOPY",
        displayName = "Percetakan & Fotocopy",
        category = BusinessCategory.SERVICES,
        description = "Jasa fotocopy, print dokumen, jilid, cetak banner, dan ATK"
    ),
    JASA_TEKNISI(
        id = "JASA_TEKNISI",
        displayName = "Jasa Teknisi & Tukang",
        category = BusinessCategory.SERVICES,
        description = "Jasa servis AC, instalasi listrik, ledeng, dan tukang perbaikan"
    ),

    // 3. FOOD_BEV
    WARUNG_MAKAN(
        id = "WARUNG_MAKAN",
        displayName = "Warung Makan / Resto",
        category = BusinessCategory.FOOD_BEV,
        description = "Warung makan, warteg, depot masakan, dan rumah makan"
    ),
    KEDAI_KAFE(
        id = "KEDAI_KAFE",
        displayName = "Kedai Kopi & Kafe",
        category = BusinessCategory.FOOD_BEV,
        description = "Kedai kopi, kafe minuman kekinian, jus, dan booth minuman"
    ),
    BAKERY_KUE(
        id = "BAKERY_KUE",
        displayName = "Toko Roti & Kue (Bakery)",
        category = BusinessCategory.FOOD_BEV,
        description = "Penjualan roti, kue basah/kering, jajanan pasar, dan pastry"
    ),

    // 4. PRODUCTION
    INDUSTRI_RUMAHAN(
        id = "INDUSTRI_RUMAHAN",
        displayName = "Industri Rumahan & Kerajinan",
        category = BusinessCategory.PRODUCTION,
        description = "Produksi olahan makanan rumahan, kerajinan tangan, dan manufaktur mikro"
    );

    companion object {
        fun fromId(id: String): BusinessType? = entries.find { it.id.equals(id, ignoreCase = true) }
    }
}
