package id.skmnetwork.bukuwarung.purchase

import id.skmnetwork.bukuwarung.data.local.entity.PurchaseOrderEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseOrderItemEntity
import id.skmnetwork.bukuwarung.domain.receipt.ReceiptPaperWidth
import id.skmnetwork.bukuwarung.printer.escpos.EscPosBuilder
import id.skmnetwork.bukuwarung.printer.escpos.ReceiptTextFormatterUtils
import id.skmnetwork.bukuwarung.util.formatRupiah
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Gate G13.4 — Thermal Receipt Formatter for Purchase Orders (58mm and 80mm).
 * Uses existing ESC/POS infrastructure and text formatting utilities.
 * Pure deterministic formatter with zero database side effects.
 */
object PurchaseOrderReceiptFormatter {

    /**
     * Formats decimal quantities cleanly while preserving fractional precision for FUEL/decimals.
     */
    fun formatQuantity(qty: Double, unit: String = "pcs"): String {
        val qtyStr = if (qty % 1.0 == 0.0) {
            qty.toLong().toString()
        } else {
            val raw = qty.toString()
            if (raw.contains(".")) {
                raw.trimEnd('0').trimEnd('.')
            } else {
                raw
            }
        }
        val unitStr = unit.trim().ifBlank { "pcs" }
        return "$qtyStr $unitStr"
    }

    /**
     * Formats timestamp into standard Indonesian date format.
     */
    fun formatDate(timeMillis: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("id-ID"))
        return sdf.format(Date(timeMillis))
    }

    /**
     * Formats a Purchase Order into plain text for 58mm / 80mm receipt preview.
     */
    fun formatPlainText(
        order: PurchaseOrderEntity,
        items: List<PurchaseOrderItemEntity>,
        shopName: String,
        shopAddress: String = "",
        shopPhone: String = "",
        paperWidth: ReceiptPaperWidth = ReceiptPaperWidth.WIDTH_58MM,
        poTitle: String = "PURCHASE ORDER"
    ): String {
        val width = paperWidth.columns
        val sb = StringBuilder()
        val displayShopName = shopName.trim().ifBlank { "Usaha Kami" }

        // 1. Header
        sb.appendLine(centerText(displayShopName, width))
        if (shopAddress.isNotBlank()) {
            sb.appendLine(centerText(shopAddress.trim(), width))
        }
        if (shopPhone.isNotBlank()) {
            sb.appendLine(centerText("Telp: ${shopPhone.trim()}", width))
        }
        sb.appendLine(ReceiptTextFormatterUtils.dividerLine('-', width))
        sb.appendLine(centerText(poTitle.trim(), width))
        sb.appendLine(ReceiptTextFormatterUtils.dividerLine('-', width))

        // 2. PO Metadata
        val poNumberText = "No. PO: ${order.orderNumber}"
        val dateText = formatDate(order.createdAt)
        if (poNumberText.length + dateText.length + 1 <= width) {
            sb.appendLine(ReceiptTextFormatterUtils.twoColumns(poNumberText, dateText, width))
        } else {
            sb.appendLine(poNumberText)
            sb.appendLine("Tanggal: $dateText")
        }

        sb.appendLine("Supplier: ${order.supplierNameSnapshot.trim()}")
        if (!order.supplierPhoneSnapshot.isNullOrBlank()) {
            sb.appendLine("Telp: ${order.supplierPhoneSnapshot.trim()}")
        }
        sb.appendLine(ReceiptTextFormatterUtils.dividerLine('-', width))

        // 3. Items List
        if (items.isEmpty()) {
            sb.appendLine(centerText("(Tidak ada item pesanan)", width))
        } else {
            items.forEachIndexed { index, item ->
                val num = index + 1
                val nameWithNum = "$num. ${item.productName.trim()}"
                val nameLines = ReceiptTextFormatterUtils.wrapText(nameWithNum, width)
                nameLines.forEach { line -> sb.appendLine(line) }

                val qtyPriceStr = "  ${formatQuantity(item.orderedQuantity, item.unit)} x ${formatRupiah(item.estimatedPrice)}"
                val subtotalStr = formatRupiah(item.estimatedSubtotal)
                if (qtyPriceStr.length + subtotalStr.length + 1 <= width) {
                    sb.appendLine(ReceiptTextFormatterUtils.twoColumns(qtyPriceStr, subtotalStr, width))
                } else {
                    sb.appendLine(qtyPriceStr)
                    sb.appendLine(ReceiptTextFormatterUtils.twoColumns("  Subtotal", subtotalStr, width))
                }
            }
        }
        sb.appendLine(ReceiptTextFormatterUtils.dividerLine('-', width))

        // 4. Financial Summary (Estimated Total)
        sb.appendLine(ReceiptTextFormatterUtils.twoColumns(
            "Estimasi Total",
            formatRupiah(order.totalEstimatedAmount),
            width
        ))
        sb.appendLine(ReceiptTextFormatterUtils.dividerLine('=', width))

        // 5. Notes (if present)
        if (!order.notes.isNullOrBlank()) {
            sb.appendLine("Catatan:")
            val noteLines = ReceiptTextFormatterUtils.wrapText(order.notes.trim(), width)
            noteLines.forEach { line -> sb.appendLine(line) }
            sb.appendLine(ReceiptTextFormatterUtils.dividerLine('-', width))
        }

        // 6. Footer
        sb.appendLine(centerText("Mohon konfirmasi ketersediaan", width))
        sb.appendLine(centerText("dan harga aktual.", width))
        sb.appendLine(centerText("Terima kasih.", width))
        sb.appendLine()
        sb.appendLine(centerText("Buku Warung App", width))
        sb.appendLine()
        sb.appendLine()

        return sb.toString()
    }

    /**
     * Formats a Purchase Order into ESC/POS byte array for 58mm / 80mm thermal printers.
     */
    fun formatEscPos(
        order: PurchaseOrderEntity,
        items: List<PurchaseOrderItemEntity>,
        shopName: String,
        shopAddress: String = "",
        shopPhone: String = "",
        paperWidth: ReceiptPaperWidth = ReceiptPaperWidth.WIDTH_58MM,
        poTitle: String = "PURCHASE ORDER"
    ): ByteArray {
        val width = paperWidth.columns
        val builder = EscPosBuilder()
        val displayShopName = shopName.trim().ifBlank { "Usaha Kami" }

        // 1. Initialize
        builder.initialize()

        // 2. Header
        builder.alignCenter()
        builder.bold(true)
        if (paperWidth == ReceiptPaperWidth.WIDTH_80MM) {
            builder.doubleSize(true)
        } else {
            builder.doubleHeight(true)
        }
        builder.textLine(displayShopName)
        builder.normalSize()
        builder.bold(false)

        if (shopAddress.isNotBlank()) {
            builder.textLine(shopAddress.trim())
        }
        if (shopPhone.isNotBlank()) {
            builder.textLine("Telp: ${shopPhone.trim()}")
        }

        builder.alignLeft()
        builder.textLine(ReceiptTextFormatterUtils.dividerLine('-', width))

        builder.alignCenter()
        builder.bold(true)
        builder.textLine(poTitle.trim())
        builder.bold(false)

        builder.alignLeft()
        builder.textLine(ReceiptTextFormatterUtils.dividerLine('-', width))

        // 3. Metadata
        val poNumberText = "No. PO: ${order.orderNumber}"
        val dateText = formatDate(order.createdAt)
        if (poNumberText.length + dateText.length + 1 <= width) {
            builder.textLine(ReceiptTextFormatterUtils.twoColumns(poNumberText, dateText, width))
        } else {
            builder.textLine(poNumberText)
            builder.textLine("Tanggal: $dateText")
        }

        builder.textLine("Supplier: ${order.supplierNameSnapshot.trim()}")
        if (!order.supplierPhoneSnapshot.isNullOrBlank()) {
            builder.textLine("Telp: ${order.supplierPhoneSnapshot.trim()}")
        }
        builder.textLine(ReceiptTextFormatterUtils.dividerLine('-', width))

        // 4. Items List
        if (items.isEmpty()) {
            builder.alignCenter()
            builder.textLine("(Tidak ada item pesanan)")
            builder.alignLeft()
        } else {
            items.forEachIndexed { index, item ->
                val num = index + 1
                val nameWithNum = "$num. ${item.productName.trim()}"
                val nameLines = ReceiptTextFormatterUtils.wrapText(nameWithNum, width)
                nameLines.forEach { line -> builder.textLine(line) }

                val qtyPriceStr = "  ${formatQuantity(item.orderedQuantity, item.unit)} x ${formatRupiah(item.estimatedPrice)}"
                val subtotalStr = formatRupiah(item.estimatedSubtotal)
                if (qtyPriceStr.length + subtotalStr.length + 1 <= width) {
                    builder.textLine(ReceiptTextFormatterUtils.twoColumns(qtyPriceStr, subtotalStr, width))
                } else {
                    builder.textLine(qtyPriceStr)
                    builder.textLine(ReceiptTextFormatterUtils.twoColumns("  Subtotal", subtotalStr, width))
                }
            }
        }
        builder.textLine(ReceiptTextFormatterUtils.dividerLine('-', width))

        // 5. Financial Summary (Estimated Total)
        builder.bold(true)
        builder.textLine(ReceiptTextFormatterUtils.twoColumns(
            "Estimasi Total",
            formatRupiah(order.totalEstimatedAmount),
            width
        ))
        builder.bold(false)
        builder.textLine(ReceiptTextFormatterUtils.dividerLine('=', width))

        // 6. Notes (if present)
        if (!order.notes.isNullOrBlank()) {
            builder.textLine("Catatan:")
            val noteLines = ReceiptTextFormatterUtils.wrapText(order.notes.trim(), width)
            noteLines.forEach { line -> builder.textLine(line) }
            builder.textLine(ReceiptTextFormatterUtils.dividerLine('-', width))
        }

        // 7. Footer
        builder.alignCenter()
        builder.textLine("Mohon konfirmasi ketersediaan")
        builder.textLine("dan harga aktual.")
        builder.textLine("Terima kasih.")
        builder.textLine()
        builder.textLine("Buku Warung App")

        // 8. Feed & Cut
        builder.lineFeed(3)
        builder.cutPaper(partial = true)

        return builder.build()
    }

    private fun centerText(text: String, width: Int): String {
        if (text.length <= width) {
            val leftPadding = (width - text.length) / 2
            return " ".repeat(leftPadding) + text
        }
        val lines = ReceiptTextFormatterUtils.wrapText(text, width)
        return lines.joinToString("\n") { line ->
            val leftPadding = (width - line.length).coerceAtLeast(0) / 2
            " ".repeat(leftPadding) + line
        }
    }
}
