package id.skmnetwork.bukuwarung

import id.skmnetwork.bukuwarung.catalog.WhatsAppCatalogFormatter
import id.skmnetwork.bukuwarung.catalog.WhatsAppCatalogShareHelper
import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            unit = "sak"
        ),
        ProductEntity(
            id = 2,
            categoryId = 1,
            name = "Minyak Goreng 1L",
            purchasePrice = 13500,
            sellingPrice = 15000,
            stock = 25.0,
            unit = "pouch"
        ),
        ProductEntity(
            id = 3,
            categoryId = 2,
            name = "Kopi Sachet",
            purchasePrice = 1500,
            sellingPrice = 2000,
            stock = 50.0,
            unit = "pcs"
        )
    )

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
    fun testCatalogFormattingWithProductsAndStock() {
        val formatted = WhatsAppCatalogFormatter.formatCatalogText(
            shopName = "Warung Bu Siti",
            ownerName = "Bu Siti",
            phone = "08123456789",
            address = "Pasar Tradisional Kios 12",
            products = sampleProducts,
            includeStock = true
        )
        assertTrue(formatted.contains("*Warung Bu Siti*"))
        assertTrue(formatted.contains("• *Beras Rojolele 5kg* — Rp 65.000 (Stok: 10 sak)"))
        assertTrue(formatted.contains("• *Minyak Goreng 1L* — Rp 15.000 (Stok: 25 pouch)"))
        assertTrue(formatted.contains("• *Kopi Sachet* — Rp 2.000 (Stok: 50 pcs)"))
        assertTrue(formatted.contains("📍 *Alamat:* Pasar Tradisional Kios 12"))
        assertTrue(formatted.contains("📞 *Hubungi / Pesan:* 08123456789 (Bu Siti)"))
    }

    @Test
    fun testCatalogFormattingWithoutStock() {
        val formatted = WhatsAppCatalogFormatter.formatCatalogText(
            shopName = "Toko Sumber Rejeki",
            products = sampleProducts,
            includeStock = false
        )
        assertTrue(formatted.contains("• *Beras Rojolele 5kg* — Rp 65.000"))
        assertFalse(formatted.contains("(Stok:"))
    }
}
