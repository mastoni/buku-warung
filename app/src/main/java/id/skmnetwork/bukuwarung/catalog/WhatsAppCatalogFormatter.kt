package id.skmnetwork.bukuwarung.catalog

import id.skmnetwork.bukuwarung.data.local.entity.ProductEntity
import id.skmnetwork.bukuwarung.util.formatRupiah

/**
 * Gate H — Formats store product catalog text cleanly for WhatsApp sharing.
 * Read-only formatter using existing ProductEntity and UserSettings.
 */
object WhatsAppCatalogFormatter {

    /**
     * Formats the selected products and store identity into a clean, readable WhatsApp message.
     */
    fun formatCatalogText(
        shopName: String,
        ownerName: String = "",
        phone: String = "",
        address: String = "",
        products: List<ProductEntity>,
        includeStock: Boolean = false,
        productLabel: String = "Produk",
        catalogTitle: String = "KATALOG ${productLabel.uppercase()}"
    ): String {
        val builder = StringBuilder()

        val displayShopName = shopName.trim().ifBlank { "Toko Kami" }
        builder.append("*$catalogTitle*\n")
        builder.append("*$displayShopName*\n\n")

        if (products.isEmpty()) {
            builder.append("Belum ada ${productLabel.lowercase()} yang dipilih dalam katalog ini.\n")
        } else {
            builder.append("Berikut daftar ${productLabel.lowercase()} & harga kami:\n\n")
            products.forEach { product ->
                builder.append("• *${product.name.trim()}* — ${formatRupiah(product.sellingPrice)}")
                if (includeStock) {
                    val stockLabel = if (product.stock % 1.0 == 0.0) {
                        product.stock.toInt().toString()
                    } else {
                        product.stock.toString()
                    }
                    val unitLabel = product.unit.trim().ifBlank { "pcs" }
                    builder.append(" (Stok: $stockLabel $unitLabel)")
                }
                builder.append("\n")
            }
        }

        val hasContactInfo = phone.isNotBlank() || address.isNotBlank() || ownerName.isNotBlank()
        if (hasContactInfo) {
            builder.append("\n")
            if (address.isNotBlank()) {
                builder.append("📍 *Alamat:* ${address.trim()}\n")
            }
            if (phone.isNotBlank()) {
                val ownerSuffix = if (ownerName.isNotBlank()) " (${ownerName.trim()})" else ""
                builder.append("📞 *Hubungi / Pesan:* ${phone.trim()}$ownerSuffix\n")
            } else if (ownerName.isNotBlank()) {
                builder.append("👤 *Pemilik:* ${ownerName.trim()}\n")
            }
        }

        builder.append("\n_Dibuat dengan Buku Warung_")
        return builder.toString()
    }
}
