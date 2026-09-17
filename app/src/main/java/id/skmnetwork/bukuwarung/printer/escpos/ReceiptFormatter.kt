package id.skmnetwork.bukuwarung.printer.escpos

import id.skmnetwork.bukuwarung.domain.receipt.ReceiptData
import id.skmnetwork.bukuwarung.domain.receipt.ReceiptPaperWidth
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReceiptTextFormatterUtils {

    fun formatRupiah(amount: Long): String {
        val format = NumberFormat.getNumberInstance(Locale("id", "ID"))
        return "Rp " + format.format(amount)
    }

    fun formatDate(timeMillis: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("id", "ID"))
        return sdf.format(Date(timeMillis))
    }

    fun formatQuantity(qty: Double, unit: String): String {
        val qtyStr = if (qty % 1.0 == 0.0) {
            qty.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", qty)
        }
        return if (unit.isNotBlank() && unit != "pcs") "$qtyStr $unit" else qtyStr
    }

    fun dividerLine(char: Char = '-', width: Int): String {
        return char.toString().repeat(width)
    }

    fun twoColumns(left: String, right: String, totalWidth: Int): String {
        val availableSpace = totalWidth - left.length - right.length
        return if (availableSpace >= 0) {
            left + " ".repeat(availableSpace) + right
        } else {
            // Left text is too long; truncate left or let right take priority
            val maxLeft = (totalWidth - right.length - 1).coerceAtLeast(0)
            val truncatedLeft = left.take(maxLeft)
            val padding = (totalWidth - truncatedLeft.length - right.length).coerceAtLeast(0)
            truncatedLeft + " ".repeat(padding) + right
        }
    }

    fun wrapText(text: String, maxWidth: Int): List<String> {
        if (text.length <= maxWidth) return listOf(text)

        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            if (word.length > maxWidth) {
                // If a single word is longer than line width, break it down
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                    currentLine = StringBuilder()
                }
                var remainingWord = word
                while (remainingWord.length > maxWidth) {
                    lines.add(remainingWord.substring(0, maxWidth))
                    remainingWord = remainingWord.substring(maxWidth)
                }
                currentLine.append(remainingWord)
            } else if (currentLine.length + word.length + (if (currentLine.isEmpty()) 0 else 1) <= maxWidth) {
                if (currentLine.isNotEmpty()) {
                    currentLine.append(" ")
                }
                currentLine.append(word)
            } else {
                lines.add(currentLine.toString())
                currentLine = StringBuilder(word)
            }
        }

        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }

        return lines
    }
}

class PlainTextReceiptFormatter {

    fun format(receipt: ReceiptData, paperWidth: ReceiptPaperWidth = ReceiptPaperWidth.WIDTH_58MM): String {
        val width = paperWidth.columns
        val sb = StringBuilder()

        // 1. Header
        if (receipt.shopProfile.showShopName && receipt.shopProfile.shopName.isNotBlank()) {
            sb.appendLine(centerText(receipt.shopProfile.shopName, width))
        }
        if (receipt.shopProfile.showAddress && receipt.shopProfile.address.isNotBlank()) {
            sb.appendLine(centerText(receipt.shopProfile.address, width))
        }
        if (receipt.shopProfile.showPhone && receipt.shopProfile.phone.isNotBlank()) {
            sb.appendLine(centerText("Telp: ${receipt.shopProfile.phone}", width))
        }

        sb.appendLine(ReceiptTextFormatterUtils.dividerLine('-', width))

        // 2. Transaction Metadata
        sb.appendLine(ReceiptTextFormatterUtils.twoColumns(
            "${receipt.shopProfile.transactionNumberLabel}: ${receipt.receiptNumber}",
            ReceiptTextFormatterUtils.formatDate(receipt.dateTimeMillis),
            width
        ))
        if (receipt.cashierName.isNotBlank()) {
            sb.appendLine("${receipt.shopProfile.cashierLabel}: ${receipt.cashierName}")
        }
        if (!receipt.paymentInfo.customerName.isNullOrBlank()) {
            sb.appendLine("${receipt.shopProfile.customerLabel}: ${receipt.paymentInfo.customerName}")
        }

        sb.appendLine(ReceiptTextFormatterUtils.dividerLine('-', width))

        // 3. Items
        for (item in receipt.items) {
            val nameLines = ReceiptTextFormatterUtils.wrapText(item.name, width)
            nameLines.forEach { line -> sb.appendLine(line) }

            val qtyPriceStr = "${ReceiptTextFormatterUtils.formatQuantity(item.quantity, item.unit)} x ${ReceiptTextFormatterUtils.formatRupiah(item.price)}"
            val subtotalStr = ReceiptTextFormatterUtils.formatRupiah(item.subtotal)
            sb.appendLine(ReceiptTextFormatterUtils.twoColumns("  $qtyPriceStr", subtotalStr, width))
        }

        sb.appendLine(ReceiptTextFormatterUtils.dividerLine('-', width))

        // 4. Financial Summary
        sb.appendLine(ReceiptTextFormatterUtils.twoColumns("TOTAL", ReceiptTextFormatterUtils.formatRupiah(receipt.paymentInfo.totalAmount), width))

        if (receipt.shopProfile.showPaymentMethod) {
            sb.appendLine(ReceiptTextFormatterUtils.twoColumns("Metode Bayar", receipt.paymentInfo.method, width))
        }

        if (receipt.paymentInfo.method == "CASH") {
            receipt.paymentInfo.payAmount?.let { pay ->
                sb.appendLine(ReceiptTextFormatterUtils.twoColumns("Bayar Tunai", ReceiptTextFormatterUtils.formatRupiah(pay), width))
            }
            if (receipt.shopProfile.showChange) {
                receipt.paymentInfo.changeAmount?.let { change ->
                    sb.appendLine(ReceiptTextFormatterUtils.twoColumns("Kembali", ReceiptTextFormatterUtils.formatRupiah(change), width))
                }
            }
        } else if (receipt.paymentInfo.method == "CREDIT") {
            receipt.paymentInfo.remainingDebt?.let { debt ->
                sb.appendLine(ReceiptTextFormatterUtils.twoColumns("Sisa Hutang", ReceiptTextFormatterUtils.formatRupiah(debt), width))
            }
        }

        sb.appendLine(ReceiptTextFormatterUtils.dividerLine('=', width))

        // 5. Footer
        if (receipt.shopProfile.footerMessage.isNotBlank()) {
            sb.appendLine(centerText(receipt.shopProfile.footerMessage, width))
        }
        // 5. Feed lines
        sb.appendLine()
        sb.appendLine()

        return sb.toString()
    }

    fun formatTestReceipt(
        shopName: String = "BUKU WARUNG",
        paperWidth: ReceiptPaperWidth = ReceiptPaperWidth.WIDTH_58MM
    ): String {
        val width = paperWidth.columns
        val sb = StringBuilder()
        sb.appendLine(centerText(if (shopName.isNotBlank()) shopName else "BUKU WARUNG", width))
        sb.appendLine(ReceiptTextFormatterUtils.dividerLine('-', width))
        sb.appendLine(centerText("TEST PRINT", width))
        sb.appendLine(centerText("--- TEST PRINT BERHASIL ---", width))
        sb.appendLine(centerText(ReceiptTextFormatterUtils.formatDate(System.currentTimeMillis()), width))
        sb.appendLine(ReceiptTextFormatterUtils.dividerLine('-', width))
        return sb.toString()
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

class EscPosReceiptFormatter {

    fun format(receipt: ReceiptData, paperWidth: ReceiptPaperWidth = ReceiptPaperWidth.WIDTH_58MM): ByteArray {
        val width = paperWidth.columns
        val builder = EscPosBuilder()

        // 1. Init
        builder.initialize()

        // 2. Header
        builder.alignCenter()
        if (receipt.shopProfile.showShopName && receipt.shopProfile.shopName.isNotBlank()) {
            builder.bold(true)
            if (paperWidth == ReceiptPaperWidth.WIDTH_80MM) {
                builder.doubleSize(true)
            } else {
                builder.doubleHeight(true)
            }
            builder.textLine(receipt.shopProfile.shopName)
            builder.normalSize()
            builder.bold(false)
        }

        if (receipt.shopProfile.showAddress && receipt.shopProfile.address.isNotBlank()) {
            builder.textLine(receipt.shopProfile.address)
        }
        if (receipt.shopProfile.showPhone && receipt.shopProfile.phone.isNotBlank()) {
            builder.textLine("Telp: ${receipt.shopProfile.phone}")
        }

        builder.alignLeft()
        builder.textLine(ReceiptTextFormatterUtils.dividerLine('-', width))

        // 3. Metadata
        builder.textLine(ReceiptTextFormatterUtils.twoColumns(
            "${receipt.shopProfile.transactionNumberLabel}: ${receipt.receiptNumber}",
            ReceiptTextFormatterUtils.formatDate(receipt.dateTimeMillis),
            width
        ))
        if (receipt.cashierName.isNotBlank()) {
            builder.textLine("${receipt.shopProfile.cashierLabel}: ${receipt.cashierName}")
        }
        if (!receipt.paymentInfo.customerName.isNullOrBlank()) {
            builder.textLine("${receipt.shopProfile.customerLabel}: ${receipt.paymentInfo.customerName}")
        }

        builder.textLine(ReceiptTextFormatterUtils.dividerLine('-', width))

        // 4. Items
        for (item in receipt.items) {
            val nameLines = ReceiptTextFormatterUtils.wrapText(item.name, width)
            nameLines.forEach { line -> builder.textLine(line) }

            val qtyPriceStr = "${ReceiptTextFormatterUtils.formatQuantity(item.quantity, item.unit)} x ${ReceiptTextFormatterUtils.formatRupiah(item.price)}"
            val subtotalStr = ReceiptTextFormatterUtils.formatRupiah(item.subtotal)
            builder.textLine(ReceiptTextFormatterUtils.twoColumns("  $qtyPriceStr", subtotalStr, width))
        }

        builder.textLine(ReceiptTextFormatterUtils.dividerLine('-', width))

        // 5. Financial Summary
        builder.bold(true)
        builder.textLine(ReceiptTextFormatterUtils.twoColumns("TOTAL", ReceiptTextFormatterUtils.formatRupiah(receipt.paymentInfo.totalAmount), width))
        builder.bold(false)

        if (receipt.shopProfile.showPaymentMethod) {
            builder.textLine(ReceiptTextFormatterUtils.twoColumns("Metode Bayar", receipt.paymentInfo.method, width))
        }

        if (receipt.paymentInfo.method == "CASH") {
            receipt.paymentInfo.payAmount?.let { pay ->
                builder.textLine(ReceiptTextFormatterUtils.twoColumns("Bayar Tunai", ReceiptTextFormatterUtils.formatRupiah(pay), width))
            }
            if (receipt.shopProfile.showChange) {
                receipt.paymentInfo.changeAmount?.let { change ->
                    builder.textLine(ReceiptTextFormatterUtils.twoColumns("Kembali", ReceiptTextFormatterUtils.formatRupiah(change), width))
                }
            }
        } else if (receipt.paymentInfo.method == "CREDIT") {
            receipt.paymentInfo.remainingDebt?.let { debt ->
                builder.textLine(ReceiptTextFormatterUtils.twoColumns("Sisa Hutang", ReceiptTextFormatterUtils.formatRupiah(debt), width))
            }
        }

        builder.textLine(ReceiptTextFormatterUtils.dividerLine('=', width))

        // 6. Footer
        builder.alignCenter()
        if (receipt.shopProfile.footerMessage.isNotBlank()) {
            builder.textLine(receipt.shopProfile.footerMessage)
        }
        builder.textLine("Buku Warung App")

        // 7. Feed & Cut
        builder.lineFeed(3)
        builder.cutPaper(partial = true)

        return builder.build()
    }

    fun formatTestReceipt(
        shopName: String = "BUKU WARUNG",
        paperWidth: ReceiptPaperWidth = ReceiptPaperWidth.WIDTH_58MM
    ): ByteArray {
        val width = paperWidth.columns
        val builder = EscPosBuilder()
        builder.initialize()
        builder.alignCenter()
        builder.bold(true)
        builder.textLine(if (shopName.isNotBlank()) shopName else "BUKU WARUNG")
        builder.bold(false)
        builder.textLine(ReceiptTextFormatterUtils.dividerLine('-', width))
        builder.bold(true)
        builder.textLine("TEST PRINT")
        builder.textLine("--- TEST PRINT BERHASIL ---")
        builder.bold(false)
        builder.textLine(ReceiptTextFormatterUtils.formatDate(System.currentTimeMillis()))
        builder.textLine(ReceiptTextFormatterUtils.dividerLine('-', width))
        builder.lineFeed(3)
        builder.cutPaper(partial = true)
        return builder.build()
    }
}
