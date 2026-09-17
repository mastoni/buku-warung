package id.skmnetwork.bukuwarung.purchase

import id.skmnetwork.bukuwarung.data.local.entity.PurchaseOrderEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseOrderItemEntity
import id.skmnetwork.bukuwarung.util.formatRupiah
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Gate G13.3 — Formats Purchase Order text cleanly for WhatsApp sharing with suppliers.
 * Pure deterministic formatter with zero database side effects.
 */
object WhatsAppPurchaseOrderFormatter {

    /**
     * Formats a PurchaseOrder and its items into a clean, professional WhatsApp message.
     */
    fun formatOrderText(
        shopName: String,
        order: PurchaseOrderEntity,
        items: List<PurchaseOrderItemEntity>,
        poTitle: String = "PURCHASE ORDER"
    ): String {
        val builder = StringBuilder()
        val displayShopName = shopName.trim().ifBlank { "Usaha Kami" }
        val dateFormat = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.forLanguageTag("id-ID"))
        val formattedDate = dateFormat.format(Date(order.createdAt))

        builder.append("*$poTitle*\n")
        builder.append("*$displayShopName*\n\n")

        builder.append("No. PO: ${order.orderNumber}\n")
        builder.append("Tanggal: $formattedDate\n\n")

        builder.append("Supplier:\n")
        builder.append("${order.supplierNameSnapshot.trim()}\n")
        if (!order.supplierPhoneSnapshot.isNullOrBlank()) {
            builder.append("Telp: ${order.supplierPhoneSnapshot.trim()}\n")
        }
        builder.append("\n")

        builder.append("Daftar pesanan:\n\n")

        if (items.isEmpty()) {
            builder.append("(Tidak ada item pesanan)\n\n")
        } else {
            items.forEachIndexed { index, item ->
                val num = index + 1
                val qtyStr = formatQuantity(item.orderedQuantity)
                val unitStr = item.unit.trim().ifBlank { "pcs" }
                val priceStr = formatRupiah(item.estimatedPrice)
                val subtotalStr = formatRupiah(item.estimatedSubtotal)

                builder.append("$num. *${item.productName.trim()}*\n")
                builder.append("   $qtyStr $unitStr × $priceStr\n")
                builder.append("   Subtotal: $subtotalStr\n\n")
            }
        }

        builder.append("--------------------------------\n")
        builder.append("*Estimasi Total: ${formatRupiah(order.totalEstimatedAmount)}*\n")
        builder.append("--------------------------------\n")

        if (!order.notes.isNullOrBlank()) {
            builder.append("\nCatatan:\n")
            builder.append("${order.notes.trim()}\n")
        }

        builder.append("\nMohon konfirmasi ketersediaan dan harga aktual.\n\n")
        builder.append("Terima kasih.\n\n")
        builder.append("_Dibuat dengan Buku Warung_")

        return builder.toString()
    }

    /**
     * Formats decimal quantities cleanly (e.g. 2.0 -> "2", 12.5 -> "12.5").
     */
    fun formatQuantity(quantity: Double): String {
        return if (quantity % 1.0 == 0.0) {
            quantity.toInt().toString()
        } else {
            quantity.toString()
        }
    }
}
