package id.skmnetwork.bukuwarung

import android.content.Intent
import id.skmnetwork.bukuwarung.catalog.WhatsAppCatalogFormatter
import id.skmnetwork.bukuwarung.catalog.WhatsAppCatalogShareHelper
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.domain.business.BusinessTaxonomyRegistry
import id.skmnetwork.bukuwarung.domain.business.BusinessType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppCatalogTest {

    private val sampleProducts = listOf(
        ProductEntity(
            id = 1,
            categoryId = 1,
            name = "Beras Rojolele 5kg",
            purchasePrice = 58000,
            sellingPrice = 65000,
            stock = 10.0,
            unit = "sak",
            itemType = "PHYSICAL"
        ),
        ProductEntity(
            id = 2,
            categoryId = 1,
            name = "Minyak Goreng 1L",
            purchasePrice = 13500,
            sellingPrice = 15000,
            stock = 25.5,
            unit = "pouch",
            itemType = "PHYSICAL"
        ),
        ProductEntity(
            id = 3,
            categoryId = 2,
            name = "Kopi Sachet",
            purchasePrice = 1500,
            sellingPrice = 2000,
            stock = 50.0,
            unit = "pcs",
            itemType = "PHYSICAL"
        ),
        ProductEntity(
            id = 4,
            categoryId = 3,
            name = "Jasa Servis Ringan",
            purchasePrice = 0,
            sellingPrice = 35000,
            stock = 1.0,
            unit = "paket",
            itemType = "SERVICE"
        ),
        ProductEntity(
            id = 5,
            categoryId = 4,
            name = "Pulsa 20k",
            purchasePrice = 20000,
            sellingPrice = 22000,
            stock = 1.0,
            unit = "trx",
            itemType = "DIGITAL"
        )
    )

    // ==========================================
    // 1. FORMATTER BASELINE & EDGE CASES
    // ==========================================

    @Test
    fun testEmptyCatalogFormatting() {
        val formatted = WhatsAppCatalogFormatter.formatCatalogText(
            shopName = "Warung Berkah",
            products = emptyList()
        )
        assertTrue(formatted.contains("*KATALOG PRODUK*"))
        assertTrue(formatted.contains("*Warung Berkah*"))
        assertTrue(formatted.contains("Belum ada produk yang dipilih"))
        assertTrue(formatted.contains("_Dibuat dengan Buku Warung_"))
    }

    @Test
    fun testSingleProductCatalog() {
        val singleProduct = listOf(sampleProducts.first())
        val formatted = WhatsAppCatalogFormatter.formatCatalogText(
            shopName = "Toko Jaya",
            products = singleProduct,
            includeStock = true
        )
        assertTrue(formatted.contains("• *Beras Rojolele 5kg* — Rp 65.000 (Stok: 10 sak)"))
        assertFalse(formatted.contains("Minyak Goreng"))
    }

    @Test
    fun testCatalogFormattingWithProductsAndStock() {
        val formatted = WhatsAppCatalogFormatter.formatCatalogText(
            shopName = "Warung Bu Siti",
            ownerName = "Bu Siti",
            phone = "08123456789",
            address = "Pasar Tradisional Kios 12",
            products = sampleProducts.take(3),
            includeStock = true
        )
        assertTrue(formatted.contains("*Warung Bu Siti*"))
        assertTrue(formatted.contains("• *Beras Rojolele 5kg* — Rp 65.000 (Stok: 10 sak)"))
        assertTrue(formatted.contains("• *Minyak Goreng 1L* — Rp 15.000 (Stok: 25.5 pouch)"))
        assertTrue(formatted.contains("• *Kopi Sachet* — Rp 2.000 (Stok: 50 pcs)"))
        assertTrue(formatted.contains("📍 *Alamat:* Pasar Tradisional Kios 12"))
        assertTrue(formatted.contains("📞 *Hubungi / Pesan:* 08123456789 (Bu Siti)"))
    }

    @Test
    fun testCatalogFormattingWithoutStock() {
        val formatted = WhatsAppCatalogFormatter.formatCatalogText(
            shopName = "Toko Sumber Rejeki",
            products = sampleProducts.take(3),
            includeStock = false
        )
        assertTrue(formatted.contains("• *Beras Rojolele 5kg* — Rp 65.000"))
        assertFalse(formatted.contains("(Stok:"))
    }

    @Test
    fun testBlankShopNameFallback() {
        val formatted = WhatsAppCatalogFormatter.formatCatalogText(
            shopName = "   ",
            products = emptyList()
        )
        assertTrue(formatted.contains("*Toko Kami*"))
    }

    @Test
    fun testBlankPhoneAndAddressDoesNotProduceUglyLines() {
        val formatted = WhatsAppCatalogFormatter.formatCatalogText(
            shopName = "Toko Bersih",
            ownerName = "",
            phone = "",
            address = "",
            products = sampleProducts.take(1)
        )
        assertFalse(formatted.contains("📍"))
        assertFalse(formatted.contains("📞"))
        assertFalse(formatted.contains("👤"))
        assertFalse(formatted.contains("null"))
    }

    @Test
    fun testOwnerOnlyWithoutPhone() {
        val formatted = WhatsAppCatalogFormatter.formatCatalogText(
            shopName = "Bengkel Pak Budi",
            ownerName = "Pak Budi",
            phone = "",
            address = "",
            products = sampleProducts.take(1)
        )
        assertTrue(formatted.contains("👤 *Pemilik:* Pak Budi"))
        assertFalse(formatted.contains("📞"))
    }

    @Test
    fun testPhoneWithoutOwner() {
        val formatted = WhatsAppCatalogFormatter.formatCatalogText(
            shopName = "Apotek Sehat",
            ownerName = "",
            phone = "081299998888",
            address = "",
            products = sampleProducts.take(1)
        )
        assertTrue(formatted.contains("📞 *Hubungi / Pesan:* 081299998888"))
        assertFalse(formatted.contains("()"))
    }

    // ==========================================
    // 2. ADAPTIVE TAXONOMY INTEGRATION
    // ==========================================

    @Test
    fun testWarungSembakoTerminology() {
        val profile = BusinessTaxonomyRegistry.resolve(BusinessType.WARUNG_SEMBAKO.name)
        val productLabel = profile.terminology.productLabel
        val formatted = WhatsAppCatalogFormatter.formatCatalogText(
            shopName = "Warung Madura",
            products = sampleProducts.take(2),
            productLabel = productLabel
        )
        assertEquals("Produk", productLabel)
        assertTrue(formatted.contains("*KATALOG PRODUK*"))
        assertTrue(formatted.contains("Berikut daftar produk & harga kami:"))
    }

    @Test
    fun testApotekObatTerminology() {
        val profile = BusinessTaxonomyRegistry.resolve(BusinessType.APOTEK_OBAT.name)
        val productLabel = profile.terminology.productLabel
        val formatted = WhatsAppCatalogFormatter.formatCatalogText(
            shopName = "Apotek Sehat Medika",
            products = listOf(
                ProductEntity(
                    id = 10,
                    categoryId = 1,
                    name = "Paracetamol 500mg",
                    purchasePrice = 5000,
                    sellingPrice = 8000,
                    stock = 50.0,
                    unit = "strip"
                )
            ),
            productLabel = productLabel
        )
        assertEquals("Obat / Alkes", productLabel)
        assertTrue(formatted.contains("*KATALOG OBAT / ALKES*"))
        assertTrue(formatted.contains("Berikut daftar obat / alkes & harga kami:"))
        assertTrue(formatted.contains("• *Paracetamol 500mg* — Rp 8.000"))
    }

    @Test
    fun testTokoBangunanTerminology() {
        val profile = BusinessTaxonomyRegistry.resolve(BusinessType.TOKO_BANGUNAN.name)
        val productLabel = profile.terminology.productLabel
        val formatted = WhatsAppCatalogFormatter.formatCatalogText(
            shopName = "TB Makmur Abadi",
            products = listOf(
                ProductEntity(
                    id = 20,
                    categoryId = 1,
                    name = "Semen Tiga Roda 50kg",
                    purchasePrice = 58000,
                    sellingPrice = 65000,
                    stock = 100.0,
                    unit = "sak"
                )
            ),
            productLabel = productLabel
        )
        assertEquals("Material", productLabel)
        assertTrue(formatted.contains("*KATALOG MATERIAL*"))
        assertTrue(formatted.contains("Berikut daftar material & harga kami:"))
    }

    @Test
    fun testBengkelMotorMobilTerminology() {
        val profile = BusinessTaxonomyRegistry.resolve(BusinessType.BENGKEL_MOTOR_MOBIL.name)
        val productLabel = profile.terminology.productLabel
        val formatted = WhatsAppCatalogFormatter.formatCatalogText(
            shopName = "Bengkel Berkah Speed",
            products = listOf(
                ProductEntity(
                    id = 30,
                    categoryId = 1,
                    name = "Oli Yamalube 0.8L",
                    purchasePrice = 38000,
                    sellingPrice = 48000,
                    stock = 20.0,
                    unit = "botol"
                )
            ),
            productLabel = productLabel
        )
        assertEquals("Sparepart & Oli", productLabel)
        assertTrue(formatted.contains("*KATALOG SPAREPART & OLI*"))
        assertTrue(formatted.contains("Berikut daftar sparepart & oli & harga kami:"))
    }

    @Test
    fun testIndustriRumahanTerminology() {
        val profile = BusinessTaxonomyRegistry.resolve(BusinessType.INDUSTRI_RUMAHAN.name)
        val productLabel = profile.terminology.productLabel
        val formatted = WhatsAppCatalogFormatter.formatCatalogText(
            shopName = "Dapur Kue Bu Endang",
            products = listOf(
                ProductEntity(
                    id = 40,
                    categoryId = 1,
                    name = "Brownies Kukus 20x10",
                    purchasePrice = 25000,
                    sellingPrice = 45000,
                    stock = 5.0,
                    unit = "box"
                )
            ),
            productLabel = productLabel
        )
        assertEquals("Produk Jadi", productLabel)
        assertTrue(formatted.contains("*KATALOG PRODUK JADI*"))
        assertTrue(formatted.contains("Berikut daftar produk jadi & harga kami:"))
    }

    @Test
    fun testUnknownBusinessTypeFallback() {
        val profile = BusinessTaxonomyRegistry.resolve("UNKNOWN_TYPE_XYZ")
        val productLabel = profile.terminology.productLabel
        val formatted = WhatsAppCatalogFormatter.formatCatalogText(
            shopName = "Toko Serba Ada",
            products = emptyList(),
            productLabel = productLabel
        )
        assertEquals("Produk", productLabel)
        assertTrue(formatted.contains("*KATALOG PRODUK*"))
    }

    // ==========================================
    // 3. ITEM TYPE SUPPORT (PHYSICAL, SERVICE, DIGITAL)
    // ==========================================

    @Test
    fun testDiverseItemTypesInCatalog() {
        val formatted = WhatsAppCatalogFormatter.formatCatalogText(
            shopName = "Multi Bisnis",
            products = sampleProducts,
            includeStock = true
        )
        assertTrue(formatted.contains("• *Beras Rojolele 5kg* — Rp 65.000 (Stok: 10 sak)"))
        assertTrue(formatted.contains("• *Jasa Servis Ringan* — Rp 35.000 (Stok: 1 paket)"))
        assertTrue(formatted.contains("• *Pulsa 20k* — Rp 22.000 (Stok: 1 trx)"))
    }

    // ==========================================
    // 4. SHARE HELPER PACKAGE CONSTANTS
    // ==========================================

    @Test
    fun testPackageConstants() {
        assertEquals("com.whatsapp", WhatsAppCatalogShareHelper.PACKAGE_WHATSAPP)
        assertEquals("com.whatsapp.w4b", WhatsAppCatalogShareHelper.PACKAGE_WHATSAPP_BUSINESS)
    }

    // ==========================================
    // 5. PRODUCT SELECTION & FILTER SIMULATION
    // ==========================================

    @Test
    fun testProductSelectionLogic() {
        var selectedProductIds = setOf<Long>()

        // 1. Initial selection is empty
        assertTrue(selectedProductIds.isEmpty())

        // 2. Select individual product
        selectedProductIds = selectedProductIds + 1L
        assertEquals(setOf(1L), selectedProductIds)

        // 3. Select another product
        selectedProductIds = selectedProductIds + 2L
        assertEquals(setOf(1L, 2L), selectedProductIds)

        // 4. Select All from filtered list
        val allIds = sampleProducts.map { it.id }.toSet()
        selectedProductIds = allIds
        assertEquals(5, selectedProductIds.size)

        // 5. Deselect individual product
        selectedProductIds = selectedProductIds - 2L
        assertEquals(4, selectedProductIds.size)
        assertFalse(2L in selectedProductIds)

        // 6. Deselect All
        selectedProductIds = selectedProductIds - allIds
        assertTrue(selectedProductIds.isEmpty())
    }

    @Test
    fun testSearchAndCategoryFiltering() {
        val query = "Minyak"
        val searchFiltered = sampleProducts.filter { it.name.contains(query, ignoreCase = true) }
        assertEquals(1, searchFiltered.size)
        assertEquals("Minyak Goreng 1L", searchFiltered.first().name)

        val categoryFiltered = sampleProducts.filter { it.categoryId == 1L }
        assertEquals(2, categoryFiltered.size)
    }
}
