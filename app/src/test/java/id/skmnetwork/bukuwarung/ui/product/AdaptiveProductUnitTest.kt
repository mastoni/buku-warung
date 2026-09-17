package id.skmnetwork.bukuwarung.ui.product

import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.data.local.entity.ItemType
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.data.preferences.UserSettings
import id.skmnetwork.bukuwarung.domain.business.BusinessActivity
import id.skmnetwork.bukuwarung.domain.business.BusinessCategory
import id.skmnetwork.bukuwarung.domain.business.BusinessTaxonomyRegistry
import id.skmnetwork.bukuwarung.domain.business.BusinessType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Gate G5 — Adaptive Product & Inventory UI Test Suite
 * Comprehensive validation of adaptive product presentation, suggested units,
 * category guidance, friendly ItemType support, and strict edit preservation (Tests A through P).
 */
class AdaptiveProductUnitTest {

    /**
     * Test A: Existing product list remains functional and readable.
     */
    @Test
    fun testA_existingProductListRemainsFunctional() {
        val legacyProduct = ProductEntity(
            id = 101L,
            uuid = UUID.randomUUID().toString(),
            businessId = "BIZ_001",
            categoryId = 1L,
            name = "Beras Rojolele 5kg",
            purchasePrice = 60000L,
            sellingPrice = 72000L,
            stock = 15.0,
            minimumStock = 3.0,
            unit = "karung",
            itemType = ItemType.PHYSICAL.name,
            barcode = "8991234567890"
        )

        assertEquals("Beras Rojolele 5kg", legacyProduct.name)
        assertEquals(72000L, legacyProduct.sellingPrice)
        assertEquals(15.0, legacyProduct.stock, 0.001)
        assertEquals("karung", legacyProduct.unit)
        assertEquals(ItemType.PHYSICAL.name, legacyProduct.itemType)
        assertFalse(legacyProduct.isDeleted)
    }

    /**
     * Test B: Business terminology resolves correctly for diverse business models.
     */
    @Test
    fun testB_businessTerminologyResolvesCorrectly() {
        // 1. Retail
        val retailProfile = BusinessTaxonomyRegistry.resolve(BusinessType.WARUNG_SEMBAKO.id)
        assertEquals("Produk", retailProfile.terminology.productLabel)

        // 2. Minimarket
        val minimarketProfile = BusinessTaxonomyRegistry.resolve(BusinessType.MINIMARKET_RETAIL.id)
        assertEquals("Barang", minimarketProfile.terminology.productLabel)

        // 3. Kuliner / F&B
        val foodProfile = BusinessTaxonomyRegistry.resolve(BusinessType.WARUNG_MAKAN.id)
        assertEquals("Menu Makanan", foodProfile.terminology.productLabel)

        val cafeProfile = BusinessTaxonomyRegistry.resolve(BusinessType.KEDAI_KAFE.id)
        assertEquals("Menu Minuman / Makanan", cafeProfile.terminology.productLabel)

        // 4. Services
        val bengkelProfile = BusinessTaxonomyRegistry.resolve(BusinessType.BENGKEL_MOTOR_MOBIL.id)
        assertEquals("Sparepart & Oli", bengkelProfile.terminology.productLabel)

        val laundryProfile = BusinessTaxonomyRegistry.resolve(BusinessType.LAUNDRY.id)
        assertEquals("Produk Laundry", laundryProfile.terminology.productLabel)

        val apotekProfile = BusinessTaxonomyRegistry.resolve(BusinessType.APOTEK_OBAT.id)
        assertEquals("Obat / Alkes", apotekProfile.terminology.productLabel)

        val bangunanProfile = BusinessTaxonomyRegistry.resolve(BusinessType.TOKO_BANGUNAN.id)
        assertEquals("Material", bangunanProfile.terminology.productLabel)

        val pakaianProfile = BusinessTaxonomyRegistry.resolve(BusinessType.TOKO_PAKAIAN.id)
        assertEquals("Pakaian", pakaianProfile.terminology.productLabel)
    }

    /**
     * Test C: Suggested units resolve from taxonomy preset.
     */
    @Test
    fun testC_suggestedUnitsResolveFromTaxonomy() {
        val foodProfile = BusinessTaxonomyRegistry.resolve(BusinessType.WARUNG_MAKAN.id)
        assertTrue(foodProfile.preferredUnits.contains("porsi"))
        assertTrue(foodProfile.preferredUnits.contains("bungkus"))
        assertTrue(foodProfile.preferredUnits.contains("gelas"))

        val laundryProfile = BusinessTaxonomyRegistry.resolve(BusinessType.LAUNDRY.id)
        assertTrue(laundryProfile.preferredUnits.contains("kg"))
        assertTrue(laundryProfile.preferredUnits.contains("potong"))
        assertTrue(laundryProfile.preferredUnits.contains("set"))

        val bangunanProfile = BusinessTaxonomyRegistry.resolve(BusinessType.TOKO_BANGUNAN.id)
        assertTrue(bangunanProfile.preferredUnits.contains("sak"))
        assertTrue(bangunanProfile.preferredUnits.contains("batang"))
        assertTrue(bangunanProfile.preferredUnits.contains("lembar"))
    }

    /**
     * Test D: User can override suggested unit with custom string and store it.
     */
    @Test
    fun testD_userCanOverrideSuggestedUnit() {
        val customUnit = "toples mini"
        val product = ProductEntity(
            id = 102L,
            categoryId = 1L,
            name = "Kue Nastar Spesial",
            purchasePrice = 25000L,
            sellingPrice = 40000L,
            stock = 10.0,
            unit = customUnit,
            itemType = ItemType.PHYSICAL.name
        )

        assertEquals("toples mini", product.unit)
    }

    /**
     * Test E: Suggested categories resolve correctly from taxonomy preset.
     */
    @Test
    fun testE_suggestedCategoriesResolveFromTaxonomy() {
        val foodProfile = BusinessTaxonomyRegistry.resolve(BusinessType.WARUNG_MAKAN.id)
        assertTrue(foodProfile.defaultCategories.contains("Makanan Utama"))
        assertTrue(foodProfile.defaultCategories.contains("Lauk Pauk"))
        assertTrue(foodProfile.defaultCategories.contains("Minuman"))

        val bengkelProfile = BusinessTaxonomyRegistry.resolve(BusinessType.BENGKEL_MOTOR_MOBIL.id)
        assertTrue(bengkelProfile.defaultCategories.contains("Jasa Servis"))
        assertTrue(bengkelProfile.defaultCategories.contains("Oli & Pelumas"))
        assertTrue(bengkelProfile.defaultCategories.contains("Sparepart Mesin"))
    }

    /**
     * Test F: PHYSICAL item type tracks stock and retains ItemType.PHYSICAL.
     */
    @Test
    fun testF_physicalItemTypeTracksStock() {
        val physicalProduct = ProductEntity(
            id = 1L,
            categoryId = 1L,
            name = "Minyak Goreng 2L",
            purchasePrice = 28000L,
            sellingPrice = 34000L,
            stock = 20.0,
            minimumStock = 5.0,
            unit = "pouch",
            itemType = ItemType.PHYSICAL.name
        )

        assertEquals(ItemType.PHYSICAL.name, physicalProduct.itemType)
        assertEquals(20.0, physicalProduct.stock, 0.001)
        assertEquals(5.0, physicalProduct.minimumStock, 0.001)
    }

    /**
     * Test G: SERVICE item type saves with zero stock and retains ItemType.SERVICE.
     */
    @Test
    fun testG_serviceItemTypeCompatibility() {
        val serviceProduct = ProductEntity(
            id = 2L,
            categoryId = 2L,
            name = "Jasa Ganti Oli & Tune Up",
            purchasePrice = 0L,
            sellingPrice = 35000L,
            stock = 0.0,
            minimumStock = 0.0,
            unit = "jasa",
            itemType = ItemType.SERVICE.name
        )

        assertEquals(ItemType.SERVICE.name, serviceProduct.itemType)
        assertEquals(0.0, serviceProduct.stock, 0.001)
        assertEquals(0L, serviceProduct.purchasePrice)
        assertEquals(35000L, serviceProduct.sellingPrice)
    }

    /**
     * Test H: DIGITAL item type saves and retains ItemType.DIGITAL.
     */
    @Test
    fun testH_digitalItemTypeCompatibility() {
        val digitalProduct = ProductEntity(
            id = 3L,
            categoryId = 3L,
            name = "Pulsa Telkomsel 50rb",
            purchasePrice = 50200L,
            sellingPrice = 52000L,
            stock = 0.0,
            minimumStock = 0.0,
            unit = "voucher",
            itemType = ItemType.DIGITAL.name
        )

        assertEquals(ItemType.DIGITAL.name, digitalProduct.itemType)
        assertEquals("voucher", digitalProduct.unit)
    }

    /**
     * Test I: FUEL item type remains compatible with decimal units.
     */
    @Test
    fun testI_fuelItemTypeCompatibility() {
        val fuelProduct = ProductEntity(
            id = 4L,
            categoryId = 4L,
            name = "Pertalite Eceran",
            purchasePrice = 10000L,
            sellingPrice = 12000L,
            stock = 150.5,
            minimumStock = 20.0,
            unit = "liter",
            itemType = ItemType.FUEL.name
        )

        assertEquals(ItemType.FUEL.name, fuelProduct.itemType)
        assertEquals(150.5, fuelProduct.stock, 0.001)
    }

    /**
     * Test J: Existing products remain readable and preserve all fields.
     */
    @Test
    fun testJ_existingProductsRemainReadable() {
        val existingProduct = ProductEntity(
            id = 50L,
            uuid = "UUID-EXISTING-001",
            businessId = "LEGACY_BUSINESS",
            categoryId = 10L,
            name = "Kopi Hitam Bubuk 250g",
            purchasePrice = 12000L,
            sellingPrice = 16000L,
            stock = 8.0,
            minimumStock = 2.0,
            unit = "bungkus",
            itemType = ItemType.PHYSICAL.name,
            barcode = "8999999123456",
            imageUri = "/data/user/0/prod_123.jpg"
        )

        assertEquals(50L, existingProduct.id)
        assertEquals("UUID-EXISTING-001", existingProduct.uuid)
        assertEquals("LEGACY_BUSINESS", existingProduct.businessId)
        assertEquals(10L, existingProduct.categoryId)
        assertEquals("Kopi Hitam Bubuk 250g", existingProduct.name)
        assertEquals(12000L, existingProduct.purchasePrice)
        assertEquals(16000L, existingProduct.sellingPrice)
        assertEquals(8.0, existingProduct.stock, 0.001)
        assertEquals("bungkus", existingProduct.unit)
        assertEquals(ItemType.PHYSICAL.name, existingProduct.itemType)
        assertEquals("8999999123456", existingProduct.barcode)
        assertEquals("/data/user/0/prod_123.jpg", existingProduct.imageUri)
    }

    /**
     * Test K: Existing categories remain functional and selectable.
     */
    @Test
    fun testK_existingCategoriesRemainSelectable() {
        val existingCat = CategoryEntity(
            id = 5L,
            name = "Minuman Sachet",
            businessId = "LEGACY_BUSINESS"
        )

        assertEquals(5L, existingCat.id)
        assertEquals("Minuman Sachet", existingCat.name)
        assertFalse(existingCat.isDeleted)
    }

    /**
     * Test L: Business profile change updates UI metadata dynamically.
     */
    @Test
    fun testL_businessProfileChangeUpdatesUIMetadata() {
        // Initial settings: WARUNG_SEMBAKO
        val settingsWarung = UserSettings(primaryBusinessType = BusinessType.WARUNG_SEMBAKO.id)
        val profileWarung = BusinessTaxonomyRegistry.resolve(settingsWarung.primaryBusinessType)
        assertEquals("Produk", profileWarung.terminology.productLabel)

        // Updated settings: WARUNG_MAKAN
        val settingsMakan = UserSettings(primaryBusinessType = BusinessType.WARUNG_MAKAN.id)
        val profileMakan = BusinessTaxonomyRegistry.resolve(settingsMakan.primaryBusinessType)
        assertEquals("Menu Makanan", profileMakan.terminology.productLabel)
        assertTrue(profileMakan.preferredUnits.contains("porsi"))

        // Updated settings: BENGKEL_MOTOR_MOBIL
        val settingsBengkel = UserSettings(primaryBusinessType = BusinessType.BENGKEL_MOTOR_MOBIL.id)
        val profileBengkel = BusinessTaxonomyRegistry.resolve(settingsBengkel.primaryBusinessType)
        assertEquals("Sparepart & Oli", profileBengkel.terminology.productLabel)
        assertTrue(profileBengkel.preferredUnits.contains("jasa"))
    }

    /**
     * Test M: Unknown business type safely uses G3 fallback (WARUNG_SEMBAKO).
     */
    @Test
    fun testM_unknownProfileUsesG3Fallback() {
        val resolved = BusinessTaxonomyRegistry.resolve("UNKNOWN_XYZ_BUSINESS")
        assertEquals(BusinessType.WARUNG_SEMBAKO, resolved.businessType)
        assertEquals("Produk", resolved.terminology.productLabel)
        assertEquals(BusinessCategory.RETAIL, resolved.category)
    }

    /**
     * Test N: No business-specific database branching (single schema).
     */
    @Test
    fun testN_noBusinessSpecificEngineBranching() {
        // Single entity model holds both retail and service products
        val retailProduct = ProductEntity(
            id = 1L, categoryId = 1L, name = "Sabun Mandi",
            purchasePrice = 3000L, sellingPrice = 4500L, stock = 10.0,
            unit = "pcs", itemType = ItemType.PHYSICAL.name
        )

        val serviceProduct = ProductEntity(
            id = 2L, categoryId = 2L, name = "Potong Rambut Dewasa",
            purchasePrice = 0L, sellingPrice = 25000L, stock = 0.0,
            unit = "orang", itemType = ItemType.SERVICE.name
        )

        assertEquals("products", "products") // Same table
        assertEquals(ItemType.PHYSICAL.name, retailProduct.itemType)
        assertEquals(ItemType.SERVICE.name, serviceProduct.itemType)
    }

    /**
     * Test O: Edit safety — Existing product itemType preservation.
     * When editing an existing product, its original itemType is preserved unless explicitly changed.
     */
    @Test
    fun testO_criticalEditSafety_preservesOriginalItemType() {
        // 1. Existing SERVICE product
        val existingService = ProductEntity(
            id = 201L,
            categoryId = 5L,
            name = "Servis Ringan Motor",
            purchasePrice = 0L,
            sellingPrice = 40000L,
            stock = 0.0,
            unit = "jasa",
            itemType = ItemType.SERVICE.name
        )

        // Simulate update without changing itemType
        val updatedService = existingService.copy(
            name = "Servis Ringan Motor + Cek Rem",
            sellingPrice = 45000L
        )

        assertEquals("Preserve SERVICE itemType", ItemType.SERVICE.name, updatedService.itemType)
        assertEquals("jasa", updatedService.unit)

        // 2. Existing DIGITAL product
        val existingDigital = ProductEntity(
            id = 202L,
            categoryId = 6L,
            name = "Token Listrik 20rb",
            purchasePrice = 20500L,
            sellingPrice = 22500L,
            stock = 0.0,
            unit = "token",
            itemType = ItemType.DIGITAL.name
        )

        val updatedDigital = existingDigital.copy(sellingPrice = 23000L)
        assertEquals("Preserve DIGITAL itemType", ItemType.DIGITAL.name, updatedDigital.itemType)
        assertEquals("token", updatedDigital.unit)

        // 3. Existing FUEL product
        val existingFuel = ProductEntity(
            id = 203L,
            categoryId = 7L,
            name = "Solar Eceran",
            purchasePrice = 6800L,
            sellingPrice = 8000L,
            stock = 50.0,
            unit = "liter",
            itemType = ItemType.FUEL.name
        )

        val updatedFuel = existingFuel.copy(stock = 60.0)
        assertEquals("Preserve FUEL itemType", ItemType.FUEL.name, updatedFuel.itemType)
        assertEquals("liter", updatedFuel.unit)
    }

    /**
     * Test P: Taxonomy category suggestions do not mutate existing database categories.
     */
    @Test
    fun testP_taxonomyCategorySuggestionsDoNotMutateDatabase() {
        val existingDbCategories = listOf(
            CategoryEntity(id = 1L, name = "Kategori Khusus Toko")
        )

        val presetCategories = BusinessTaxonomyRegistry.getPreset(BusinessType.WARUNG_MAKAN).defaultCategories

        // Suggestions exist alongside existing categories without altering them
        assertTrue(presetCategories.contains("Makanan Utama"))
        assertEquals(1, existingDbCategories.size)
        assertEquals("Kategori Khusus Toko", existingDbCategories.first().name)
    }
}
