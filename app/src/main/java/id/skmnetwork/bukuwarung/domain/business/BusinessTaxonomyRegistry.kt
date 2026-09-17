package id.skmnetwork.bukuwarung.domain.business

/**
 * Gate G3 — Business Taxonomy Registry
 * Central, deterministic registry mapping BusinessType presets and resolving
 * composable business profiles with secondary activities.
 */
object BusinessTaxonomyRegistry {

    val DEFAULT_BUSINESS_TYPE = BusinessType.WARUNG_SEMBAKO

    private val PRESETS: Map<BusinessType, BusinessPreset> = mapOf(
        // 1. RETAIL
        BusinessType.WARUNG_SEMBAKO to BusinessPreset(
            businessType = BusinessType.WARUNG_SEMBAKO,
            defaultActivities = setOf(
                BusinessActivity.ACTIVITY_GOODS_SELLING,
                BusinessActivity.ACTIVITY_WHOLESALE_PURCHASE,
                BusinessActivity.ACTIVITY_CREDIT_TABUNGAN
            ),
            defaultCapabilities = setOf(
                BusinessCapability.CAP_INVENTORY_STOCK,
                BusinessCapability.CAP_BARCODE_SCAN,
                BusinessCapability.CAP_CUSTOMER_DEBT,
                BusinessCapability.CAP_SUPPLIER_PAYABLE,
                BusinessCapability.CAP_THERMAL_RECEIPT,
                BusinessCapability.CAP_CASH_EXPENSE,
                BusinessCapability.CAP_UNIT_PRESETS
            ),
            terminology = BusinessTerminology(
                productLabel = "Produk",
                serviceLabel = "Layanan",
                customerLabel = "Pelanggan",
                transactionLabel = "Penjualan"
            ),
            preferredUnits = listOf("pcs", "kg", "bungkus", "karung", "dus", "liter", "renceng", "sachet"),
            defaultCategories = listOf("Sembako", "Minuman", "Makanan Ringan", "Bumbu Dapur", "Kebutuhan Rumah")
        ),

        BusinessType.MINIMARKET_RETAIL to BusinessPreset(
            businessType = BusinessType.MINIMARKET_RETAIL,
            defaultActivities = setOf(
                BusinessActivity.ACTIVITY_GOODS_SELLING,
                BusinessActivity.ACTIVITY_WHOLESALE_PURCHASE,
                BusinessActivity.ACTIVITY_CREDIT_TABUNGAN
            ),
            defaultCapabilities = setOf(
                BusinessCapability.CAP_INVENTORY_STOCK,
                BusinessCapability.CAP_BARCODE_SCAN,
                BusinessCapability.CAP_CUSTOMER_DEBT,
                BusinessCapability.CAP_SUPPLIER_PAYABLE,
                BusinessCapability.CAP_THERMAL_RECEIPT,
                BusinessCapability.CAP_CASH_EXPENSE,
                BusinessCapability.CAP_UNIT_PRESETS
            ),
            terminology = BusinessTerminology(
                productLabel = "Barang",
                serviceLabel = "Layanan",
                customerLabel = "Pelanggan",
                transactionLabel = "Kasir"
            ),
            preferredUnits = listOf("pcs", "pack", "dus", "botol", "kaleng", "box", "kg"),
            defaultCategories = listOf("Makanan & Minuman", "Personal Care", "Kebersihan", "Susu & Olahan", "Rokok")
        ),

        BusinessType.TOKO_PAKAIAN to BusinessPreset(
            businessType = BusinessType.TOKO_PAKAIAN,
            defaultActivities = setOf(
                BusinessActivity.ACTIVITY_GOODS_SELLING,
                BusinessActivity.ACTIVITY_WHOLESALE_PURCHASE
            ),
            defaultCapabilities = setOf(
                BusinessCapability.CAP_INVENTORY_STOCK,
                BusinessCapability.CAP_BARCODE_SCAN,
                BusinessCapability.CAP_CUSTOMER_DEBT,
                BusinessCapability.CAP_SUPPLIER_PAYABLE,
                BusinessCapability.CAP_THERMAL_RECEIPT,
                BusinessCapability.CAP_CASH_EXPENSE,
                BusinessCapability.CAP_UNIT_PRESETS
            ),
            terminology = BusinessTerminology(
                productLabel = "Pakaian",
                serviceLabel = "Layanan",
                customerLabel = "Pelanggan",
                transactionLabel = "Penjualan"
            ),
            preferredUnits = listOf("pcs", "potong", "lusin", "pasang", "set", "kodi"),
            defaultCategories = listOf("Baju Pria", "Baju Wanita", "Pakaian Anak", "Celana", "Jilbab & Aksesoris")
        ),

        BusinessType.TOKO_ELEKTRONIK to BusinessPreset(
            businessType = BusinessType.TOKO_ELEKTRONIK,
            defaultActivities = setOf(
                BusinessActivity.ACTIVITY_GOODS_SELLING,
                BusinessActivity.ACTIVITY_WHOLESALE_PURCHASE,
                BusinessActivity.ACTIVITY_CREDIT_TABUNGAN
            ),
            defaultCapabilities = setOf(
                BusinessCapability.CAP_INVENTORY_STOCK,
                BusinessCapability.CAP_BARCODE_SCAN,
                BusinessCapability.CAP_CUSTOMER_DEBT,
                BusinessCapability.CAP_SUPPLIER_PAYABLE,
                BusinessCapability.CAP_THERMAL_RECEIPT,
                BusinessCapability.CAP_CASH_EXPENSE,
                BusinessCapability.CAP_UNIT_PRESETS
            ),
            terminology = BusinessTerminology(
                productLabel = "Barang Elektronik",
                serviceLabel = "Layanan",
                customerLabel = "Pelanggan",
                transactionLabel = "Nota Penjualan"
            ),
            preferredUnits = listOf("unit", "pcs", "set", "box", "meter", "roll"),
            defaultCategories = listOf("Lampu & Kelistrikan", "Kabel & Steker", "Peralatan Rumah", "Aksesoris", "Komponen")
        ),

        BusinessType.TOKO_BANGUNAN to BusinessPreset(
            businessType = BusinessType.TOKO_BANGUNAN,
            defaultActivities = setOf(
                BusinessActivity.ACTIVITY_GOODS_SELLING,
                BusinessActivity.ACTIVITY_WHOLESALE_PURCHASE,
                BusinessActivity.ACTIVITY_CREDIT_TABUNGAN
            ),
            defaultCapabilities = setOf(
                BusinessCapability.CAP_INVENTORY_STOCK,
                BusinessCapability.CAP_CUSTOMER_DEBT,
                BusinessCapability.CAP_SUPPLIER_PAYABLE,
                BusinessCapability.CAP_THERMAL_RECEIPT,
                BusinessCapability.CAP_CASH_EXPENSE,
                BusinessCapability.CAP_UNIT_PRESETS
            ),
            terminology = BusinessTerminology(
                productLabel = "Material",
                serviceLabel = "Layanan",
                customerLabel = "Pelanggan",
                transactionLabel = "Nota Penjualan"
            ),
            preferredUnits = listOf("sak", "batang", "meter", "lembar", "kg", "kaleng", "pail", "truk", "pcs"),
            defaultCategories = listOf("Semen & Pasir", "Cat & Kuas", "Pipa & Fitting", "Besi & Baja", "Kayu & Triplek", "Alat Pertukangan")
        ),

        BusinessType.APOTEK_OBAT to BusinessPreset(
            businessType = BusinessType.APOTEK_OBAT,
            defaultActivities = setOf(
                BusinessActivity.ACTIVITY_GOODS_SELLING,
                BusinessActivity.ACTIVITY_WHOLESALE_PURCHASE
            ),
            defaultCapabilities = setOf(
                BusinessCapability.CAP_INVENTORY_STOCK,
                BusinessCapability.CAP_BARCODE_SCAN,
                BusinessCapability.CAP_SUPPLIER_PAYABLE,
                BusinessCapability.CAP_THERMAL_RECEIPT,
                BusinessCapability.CAP_CASH_EXPENSE,
                BusinessCapability.CAP_UNIT_PRESETS
            ),
            terminology = BusinessTerminology(
                productLabel = "Obat / Alkes",
                serviceLabel = "Layanan",
                customerLabel = "Pasien",
                transactionLabel = "Struk Apotek"
            ),
            preferredUnits = listOf("strip", "tablet", "botol", "box", "tube", "sachet", "pcs"),
            defaultCategories = listOf("Obat Bebas", "Obat Resep", "Vitamin & Suplemen", "Alat Kesehatan", "Perawatan Luka")
        ),

        BusinessType.KONTER_PULSA_HP to BusinessPreset(
            businessType = BusinessType.KONTER_PULSA_HP,
            defaultActivities = setOf(
                BusinessActivity.ACTIVITY_GOODS_SELLING,
                BusinessActivity.ACTIVITY_DIGITAL_VOUCHER,
                BusinessActivity.ACTIVITY_WHOLESALE_PURCHASE,
                BusinessActivity.ACTIVITY_CREDIT_TABUNGAN
            ),
            defaultCapabilities = setOf(
                BusinessCapability.CAP_INVENTORY_STOCK,
                BusinessCapability.CAP_DIGITAL_ITEMS,
                BusinessCapability.CAP_BARCODE_SCAN,
                BusinessCapability.CAP_CUSTOMER_DEBT,
                BusinessCapability.CAP_SUPPLIER_PAYABLE,
                BusinessCapability.CAP_THERMAL_RECEIPT,
                BusinessCapability.CAP_CASH_EXPENSE,
                BusinessCapability.CAP_UNIT_PRESETS
            ),
            terminology = BusinessTerminology(
                productLabel = "Produk & Pulsa",
                serviceLabel = "Layanan",
                customerLabel = "Pelanggan",
                transactionLabel = "Transaksi"
            ),
            preferredUnits = listOf("pcs", "voucher", "token", "paket", "unit"),
            defaultCategories = listOf("Pulsa & Paket Data", "Voucher Fisik", "Aksesoris HP", "Charger & Kabel", "Perdana")
        ),

        // 2. SERVICES
        BusinessType.BENGKEL_MOTOR_MOBIL to BusinessPreset(
            businessType = BusinessType.BENGKEL_MOTOR_MOBIL,
            defaultActivities = setOf(
                BusinessActivity.ACTIVITY_SERVICE_LABOR,
                BusinessActivity.ACTIVITY_GOODS_SELLING,
                BusinessActivity.ACTIVITY_WHOLESALE_PURCHASE,
                BusinessActivity.ACTIVITY_CREDIT_TABUNGAN
            ),
            defaultCapabilities = setOf(
                BusinessCapability.CAP_INVENTORY_STOCK,
                BusinessCapability.CAP_SERVICE_ITEMS,
                BusinessCapability.CAP_CUSTOMER_DEBT,
                BusinessCapability.CAP_SUPPLIER_PAYABLE,
                BusinessCapability.CAP_THERMAL_RECEIPT,
                BusinessCapability.CAP_CASH_EXPENSE,
                BusinessCapability.CAP_UNIT_PRESETS
            ),
            terminology = BusinessTerminology(
                productLabel = "Sparepart & Oli",
                serviceLabel = "Jasa Servis",
                customerLabel = "Pelanggan",
                transactionLabel = "Nota Servis"
            ),
            preferredUnits = listOf("pcs", "botol", "set", "jasa", "paket", "liter"),
            defaultCategories = listOf("Jasa Servis", "Oli & Pelumas", "Sparepart Mesin", "Ban & Velg", "Kelistrikan")
        ),

        BusinessType.CUCI_KENDARAAN to BusinessPreset(
            businessType = BusinessType.CUCI_KENDARAAN,
            defaultActivities = setOf(
                BusinessActivity.ACTIVITY_SERVICE_LABOR,
                BusinessActivity.ACTIVITY_GOODS_SELLING
            ),
            defaultCapabilities = setOf(
                BusinessCapability.CAP_SERVICE_ITEMS,
                BusinessCapability.CAP_INVENTORY_STOCK,
                BusinessCapability.CAP_THERMAL_RECEIPT,
                BusinessCapability.CAP_CASH_EXPENSE,
                BusinessCapability.CAP_UNIT_PRESETS
            ),
            terminology = BusinessTerminology(
                productLabel = "Produk",
                serviceLabel = "Layanan Cuci",
                customerLabel = "Pelanggan",
                transactionLabel = "Nota Cuci"
            ),
            preferredUnits = listOf("kendaraan", "layanan", "paket", "pcs", "botol"),
            defaultCategories = listOf("Cuci Motor", "Cuci Mobil", "Poles & Detailing", "Aksesoris / Parfum")
        ),

        BusinessType.SERVICE_ELEKTRONIK_HP to BusinessPreset(
            businessType = BusinessType.SERVICE_ELEKTRONIK_HP,
            defaultActivities = setOf(
                BusinessActivity.ACTIVITY_SERVICE_LABOR,
                BusinessActivity.ACTIVITY_GOODS_SELLING,
                BusinessActivity.ACTIVITY_WHOLESALE_PURCHASE,
                BusinessActivity.ACTIVITY_CREDIT_TABUNGAN
            ),
            defaultCapabilities = setOf(
                BusinessCapability.CAP_SERVICE_ITEMS,
                BusinessCapability.CAP_INVENTORY_STOCK,
                BusinessCapability.CAP_CUSTOMER_DEBT,
                BusinessCapability.CAP_SUPPLIER_PAYABLE,
                BusinessCapability.CAP_THERMAL_RECEIPT,
                BusinessCapability.CAP_CASH_EXPENSE,
                BusinessCapability.CAP_UNIT_PRESETS
            ),
            terminology = BusinessTerminology(
                productLabel = "Sparepart",
                serviceLabel = "Jasa Reparasi",
                customerLabel = "Pelanggan",
                transactionLabel = "Nota Servis"
            ),
            preferredUnits = listOf("jasa", "pcs", "unit", "set", "paket"),
            defaultCategories = listOf("Jasa Servis HP", "Jasa Servis Laptop", "Sparepart LCD", "Baterai", "Aksesoris")
        ),

        BusinessType.LAUNDRY to BusinessPreset(
            businessType = BusinessType.LAUNDRY,
            defaultActivities = setOf(
                BusinessActivity.ACTIVITY_SERVICE_LABOR,
                BusinessActivity.ACTIVITY_GOODS_SELLING
            ),
            defaultCapabilities = setOf(
                BusinessCapability.CAP_SERVICE_ITEMS,
                BusinessCapability.CAP_INVENTORY_STOCK,
                BusinessCapability.CAP_CUSTOMER_DEBT,
                BusinessCapability.CAP_THERMAL_RECEIPT,
                BusinessCapability.CAP_CASH_EXPENSE,
                BusinessCapability.CAP_UNIT_PRESETS
            ),
            terminology = BusinessTerminology(
                productLabel = "Produk Laundry",
                serviceLabel = "Layanan Laundry",
                customerLabel = "Pelanggan",
                transactionLabel = "Nota Laundry"
            ),
            preferredUnits = listOf("kg", "potong", "meter", "pasang", "set", "lembar"),
            defaultCategories = listOf("Cuci Kering Setrika", "Cuci Kering", "Setrika Saja", "Bed Cover & Karpet", "Sepatu & Tas")
        ),

        BusinessType.SALON_BARBERSHOP to BusinessPreset(
            businessType = BusinessType.SALON_BARBERSHOP,
            defaultActivities = setOf(
                BusinessActivity.ACTIVITY_SERVICE_LABOR,
                BusinessActivity.ACTIVITY_GOODS_SELLING
            ),
            defaultCapabilities = setOf(
                BusinessCapability.CAP_SERVICE_ITEMS,
                BusinessCapability.CAP_INVENTORY_STOCK,
                BusinessCapability.CAP_THERMAL_RECEIPT,
                BusinessCapability.CAP_CASH_EXPENSE,
                BusinessCapability.CAP_UNIT_PRESETS
            ),
            terminology = BusinessTerminology(
                productLabel = "Produk Perawatan",
                serviceLabel = "Layanan",
                customerLabel = "Pelanggan",
                transactionLabel = "Struk Layanan"
            ),
            preferredUnits = listOf("orang", "layanan", "paket", "pcs", "botol"),
            defaultCategories = listOf("Potong Rambut", "Cuci & Styling", "Pewarnaan", "Perawatan Wajah", "Produk Pomade / Shampo")
        ),

        BusinessType.JAHIT_TAILOR to BusinessPreset(
            businessType = BusinessType.JAHIT_TAILOR,
            defaultActivities = setOf(
                BusinessActivity.ACTIVITY_SERVICE_LABOR,
                BusinessActivity.ACTIVITY_RAW_MATERIALS,
                BusinessActivity.ACTIVITY_CREDIT_TABUNGAN
            ),
            defaultCapabilities = setOf(
                BusinessCapability.CAP_SERVICE_ITEMS,
                BusinessCapability.CAP_INVENTORY_STOCK,
                BusinessCapability.CAP_CUSTOMER_DEBT,
                BusinessCapability.CAP_THERMAL_RECEIPT,
                BusinessCapability.CAP_CASH_EXPENSE,
                BusinessCapability.CAP_UNIT_PRESETS
            ),
            terminology = BusinessTerminology(
                productLabel = "Kain & Aksesoris",
                serviceLabel = "Ongkos Jahit",
                customerLabel = "Pelanggan",
                transactionLabel = "Nota Jahit"
            ),
            preferredUnits = listOf("potong", "stel", "meter", "lusin", "pcs"),
            defaultCategories = listOf("Jahit Baru", "Permak Pakaian", "Kain & Bahan", "Kancing & Resleting")
        ),

        BusinessType.PERCETAKAN_FOTOCOPY to BusinessPreset(
            businessType = BusinessType.PERCETAKAN_FOTOCOPY,
            defaultActivities = setOf(
                BusinessActivity.ACTIVITY_SERVICE_LABOR,
                BusinessActivity.ACTIVITY_GOODS_SELLING,
                BusinessActivity.ACTIVITY_WHOLESALE_PURCHASE
            ),
            defaultCapabilities = setOf(
                BusinessCapability.CAP_SERVICE_ITEMS,
                BusinessCapability.CAP_INVENTORY_STOCK,
                BusinessCapability.CAP_CUSTOMER_DEBT,
                BusinessCapability.CAP_SUPPLIER_PAYABLE,
                BusinessCapability.CAP_THERMAL_RECEIPT,
                BusinessCapability.CAP_CASH_EXPENSE,
                BusinessCapability.CAP_UNIT_PRESETS
            ),
            terminology = BusinessTerminology(
                productLabel = "ATK & Kertas",
                serviceLabel = "Jasa Cetak",
                customerLabel = "Pelanggan",
                transactionLabel = "Nota Cetak"
            ),
            preferredUnits = listOf("lembar", "rim", "buku", "meter", "pcs", "jilid"),
            defaultCategories = listOf("Fotocopy", "Print Dokumen", "Jilid & Laminating", "Cetak Banner & Stiker", "Alat Tulis Kantor")
        ),

        BusinessType.JASA_TEKNISI to BusinessPreset(
            businessType = BusinessType.JASA_TEKNISI,
            defaultActivities = setOf(
                BusinessActivity.ACTIVITY_SERVICE_LABOR,
                BusinessActivity.ACTIVITY_GOODS_SELLING,
                BusinessActivity.ACTIVITY_CREDIT_TABUNGAN
            ),
            defaultCapabilities = setOf(
                BusinessCapability.CAP_SERVICE_ITEMS,
                BusinessCapability.CAP_INVENTORY_STOCK,
                BusinessCapability.CAP_CUSTOMER_DEBT,
                BusinessCapability.CAP_THERMAL_RECEIPT,
                BusinessCapability.CAP_CASH_EXPENSE,
                BusinessCapability.CAP_UNIT_PRESETS
            ),
            terminology = BusinessTerminology(
                productLabel = "Material / Sparepart",
                serviceLabel = "Jasa Teknisi",
                customerLabel = "Pelanggan",
                transactionLabel = "Kwitansi Layanan"
            ),
            preferredUnits = listOf("titik", "unit", "hari", "jasa", "paket", "meter"),
            defaultCategories = listOf("Servis & Cuci AC", "Instalasi Listrik", "Perbaikan Pompa Air", "Tukang Bangunan", "Pipa & Saluran")
        ),

        // 3. FOOD_BEV
        BusinessType.WARUNG_MAKAN to BusinessPreset(
            businessType = BusinessType.WARUNG_MAKAN,
            defaultActivities = setOf(
                BusinessActivity.ACTIVITY_GOODS_SELLING,
                BusinessActivity.ACTIVITY_RAW_MATERIALS,
                BusinessActivity.ACTIVITY_WHOLESALE_PURCHASE
            ),
            defaultCapabilities = setOf(
                BusinessCapability.CAP_INVENTORY_STOCK,
                BusinessCapability.CAP_THERMAL_RECEIPT,
                BusinessCapability.CAP_CASH_EXPENSE,
                BusinessCapability.CAP_UNIT_PRESETS
            ),
            terminology = BusinessTerminology(
                productLabel = "Menu Makanan",
                serviceLabel = "Layanan",
                customerLabel = "Pelanggan",
                transactionLabel = "Pesanan / Kasir"
            ),
            preferredUnits = listOf("porsi", "bungkus", "gelas", "kotak", "paket", "pcs"),
            defaultCategories = listOf("Makanan Utama", "Lauk Pauk", "Sayuran", "Minuman", "Camilan & Kerupuk")
        ),

        BusinessType.KEDAI_KAFE to BusinessPreset(
            businessType = BusinessType.KEDAI_KAFE,
            defaultActivities = setOf(
                BusinessActivity.ACTIVITY_GOODS_SELLING,
                BusinessActivity.ACTIVITY_RAW_MATERIALS,
                BusinessActivity.ACTIVITY_WHOLESALE_PURCHASE
            ),
            defaultCapabilities = setOf(
                BusinessCapability.CAP_INVENTORY_STOCK,
                BusinessCapability.CAP_THERMAL_RECEIPT,
                BusinessCapability.CAP_CASH_EXPENSE,
                BusinessCapability.CAP_UNIT_PRESETS
            ),
            terminology = BusinessTerminology(
                productLabel = "Menu Minuman / Makanan",
                serviceLabel = "Layanan",
                customerLabel = "Pelanggan",
                transactionLabel = "Order / Struk"
            ),
            preferredUnits = listOf("cup", "botol", "porsi", "slice", "gelas", "paket"),
            defaultCategories = listOf("Espresso Based", "Manual Brew", "Non-Coffee", "Snack & Pastry", "Makanan Berat")
        ),

        BusinessType.BAKERY_KUE to BusinessPreset(
            businessType = BusinessType.BAKERY_KUE,
            defaultActivities = setOf(
                BusinessActivity.ACTIVITY_GOODS_SELLING,
                BusinessActivity.ACTIVITY_RAW_MATERIALS,
                BusinessActivity.ACTIVITY_WHOLESALE_PURCHASE
            ),
            defaultCapabilities = setOf(
                BusinessCapability.CAP_INVENTORY_STOCK,
                BusinessCapability.CAP_THERMAL_RECEIPT,
                BusinessCapability.CAP_CASH_EXPENSE,
                BusinessCapability.CAP_UNIT_PRESETS
            ),
            terminology = BusinessTerminology(
                productLabel = "Roti & Kue",
                serviceLabel = "Layanan",
                customerLabel = "Pelanggan",
                transactionLabel = "Struk Pembelian"
            ),
            preferredUnits = listOf("pcs", "box", "loyang", "slice", "kotak", "lusin"),
            defaultCategories = listOf("Roti Manis", "Roti Tawar", "Kue Basah", "Kue Kering", "Tart & Ulang Tahun")
        ),

        // 4. PRODUCTION
        BusinessType.INDUSTRI_RUMAHAN to BusinessPreset(
            businessType = BusinessType.INDUSTRI_RUMAHAN,
            defaultActivities = setOf(
                BusinessActivity.ACTIVITY_GOODS_SELLING,
                BusinessActivity.ACTIVITY_RAW_MATERIALS,
                BusinessActivity.ACTIVITY_WHOLESALE_PURCHASE,
                BusinessActivity.ACTIVITY_CREDIT_TABUNGAN
            ),
            defaultCapabilities = setOf(
                BusinessCapability.CAP_INVENTORY_STOCK,
                BusinessCapability.CAP_CUSTOMER_DEBT,
                BusinessCapability.CAP_SUPPLIER_PAYABLE,
                BusinessCapability.CAP_THERMAL_RECEIPT,
                BusinessCapability.CAP_CASH_EXPENSE,
                BusinessCapability.CAP_UNIT_PRESETS
            ),
            terminology = BusinessTerminology(
                productLabel = "Produk Jadi",
                serviceLabel = "Layanan",
                customerLabel = "Pelanggan",
                transactionLabel = "Nota Penjualan"
            ),
            preferredUnits = listOf("pcs", "dus", "lusin", "kg", "ball", "karung", "pak"),
            defaultCategories = listOf("Produk Jadi", "Bahan Baku Mentah", "Kemasan & Packaging", "Produk Setengah Jadi")
        )
    )

    /**
     * Retrieves the static preset template for a given BusinessType.
     */
    fun getPreset(businessType: BusinessType): BusinessPreset {
        return PRESETS[businessType] ?: PRESETS.getValue(DEFAULT_BUSINESS_TYPE)
    }

    /**
     * Resolves a complete, deterministic ResolvedBusinessProfile given raw string
     * inputs from DataStore or UI selection.
     *
     * Fallbacks safely to WARUNG_SEMBAKO if primaryType is unknown or blank.
     * Aggregates inherent capabilities from both default and secondary activities.
     */
    fun resolve(
        primaryType: String?,
        secondaryActivities: Set<String>? = emptySet()
    ): ResolvedBusinessProfile {
        val resolvedType = if (!primaryType.isNullOrBlank()) {
            BusinessType.fromId(primaryType) ?: DEFAULT_BUSINESS_TYPE
        } else {
            DEFAULT_BUSINESS_TYPE
        }

        val basePreset = getPreset(resolvedType)

        // Parse valid secondary activities
        val parsedSecondaryActivities = secondaryActivities
            ?.mapNotNull { BusinessActivity.fromId(it) }
            ?.toSet()
            ?: emptySet()

        // Combine default preset activities with secondary activities (union)
        val aggregatedActivities = basePreset.defaultActivities + parsedSecondaryActivities

        // Combine base capabilities with inherent capabilities of all activities
        val activityCapabilities = aggregatedActivities.flatMap { it.inherentCapabilities }.toSet()
        val allCapabilities = basePreset.defaultCapabilities + activityCapabilities + setOf(
            BusinessCapability.CAP_THERMAL_RECEIPT,
            BusinessCapability.CAP_CASH_EXPENSE
        )

        return ResolvedBusinessProfile(
            category = resolvedType.category,
            businessType = resolvedType,
            activities = aggregatedActivities,
            capabilities = allCapabilities,
            terminology = basePreset.terminology,
            preferredUnits = basePreset.preferredUnits.toList(),
            defaultCategories = basePreset.defaultCategories.toList()
        )
    }

    /**
     * Returns all registered BusinessType entries grouped by BusinessCategory.
     */
    fun getBusinessTypesByCategory(category: BusinessCategory): List<BusinessType> {
        return BusinessType.entries.filter { it.category == category }
    }

    /**
     * Returns all registered BusinessType presets.
     */
    fun getAllPresets(): List<BusinessPreset> {
        return BusinessType.entries.map { getPreset(it) }
    }
}
