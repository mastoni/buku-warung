package id.skmnetwork.bukuwarung.purchase

import android.graphics.Paint
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseOrderEntity
import id.skmnetwork.bukuwarung.data.local.entity.PurchaseOrderItemEntity
import id.skmnetwork.bukuwarung.pdf.KeyValuePair
import id.skmnetwork.bukuwarung.pdf.PdfReportDocument
import id.skmnetwork.bukuwarung.pdf.ReportHeader
import id.skmnetwork.bukuwarung.pdf.ReportSection
import id.skmnetwork.bukuwarung.pdf.TableColumn
import id.skmnetwork.bukuwarung.pdf.TableRow
import id.skmnetwork.bukuwarung.util.formatRupiah
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Gate G13.4 — Purchase Order PDF Document Builder.
 * Reuses existing PdfReportDocument / PdfReportGenerator architecture.
 * Pure deterministic document structure builder with zero database side effects.
 */
object PurchaseOrderPdfBuilder {

    fun build(
        order: PurchaseOrderEntity,
        items: List<PurchaseOrderItemEntity>,
        shopName: String,
        address: String = "",
        phone: String = "",
        poTitle: String = "PURCHASE ORDER",
        printedAt: String = ""
    ): PdfReportDocument {
        val dateFormat = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale.forLanguageTag("id-ID"))
        val orderCreatedFormatted = dateFormat.format(Date(order.createdAt))
        val currentPrintTime = if (printedAt.isNotBlank()) printedAt else dateFormat.format(Date())

        val header = ReportHeader(
            shopName = shopName.trim().ifBlank { "Usaha Kami" },
            address = address.trim(),
            phone = phone.trim(),
            reportTitle = poTitle.trim().uppercase(),
            periodLabel = "No. PO: ${order.orderNumber}",
            printedAt = currentPrintTime
        )

        // 1. PO & Supplier Info Section
        val summaryPairs = mutableListOf(
            KeyValuePair(label = "Nomor Dokumen", value = order.orderNumber, isBold = true),
            KeyValuePair(label = "Tanggal Dokumen", value = orderCreatedFormatted),
            KeyValuePair(label = "Status Dokumen", value = order.status),
            KeyValuePair(label = "Nama Supplier", value = order.supplierNameSnapshot.trim(), isBold = true)
        )

        if (!order.supplierPhoneSnapshot.isNullOrBlank()) {
            summaryPairs.add(KeyValuePair(label = "Telp Supplier", value = order.supplierPhoneSnapshot.trim()))
        }

        summaryPairs.add(
            KeyValuePair(
                label = "Estimasi Total Pesanan",
                value = formatRupiah(order.totalEstimatedAmount),
                isBold = true,
                isHighlight = true
            )
        )

        val infoSection = ReportSection(
            title = "INFORMASI ${poTitle.trim().uppercase()}",
            summaryPairs = summaryPairs
        )

        // 2. Items Table Section
        val tableColumns = listOf(
            TableColumn(header = "No", weight = 0.8f, align = Paint.Align.CENTER),
            TableColumn(header = "Nama Produk / Barang", weight = 4.2f, align = Paint.Align.LEFT),
            TableColumn(header = "Jumlah", weight = 1.8f, align = Paint.Align.CENTER),
            TableColumn(header = "Estimasi Harga", weight = 2.4f, align = Paint.Align.RIGHT),
            TableColumn(header = "Estimasi Subtotal", weight = 2.6f, align = Paint.Align.RIGHT)
        )

        val tableRows = mutableListOf<TableRow>()
        items.forEachIndexed { index, item ->
            val qtyStr = PurchaseOrderReceiptFormatter.formatQuantity(item.orderedQuantity, item.unit)
            tableRows.add(
                TableRow(
                    cells = listOf(
                        (index + 1).toString(),
                        item.productName.trim(),
                        qtyStr,
                        formatRupiah(item.estimatedPrice),
                        formatRupiah(item.estimatedSubtotal)
                    )
                )
            )
        }

        // Summary Total Row
        tableRows.add(
            TableRow(
                cells = listOf(
                    "",
                    "ESTIMASI TOTAL",
                    "",
                    "",
                    formatRupiah(order.totalEstimatedAmount)
                ),
                isBold = true,
                isTotal = true
            )
        )

        val notesList = mutableListOf<String>()
        if (!order.notes.isNullOrBlank()) {
            notesList.add("Catatan: ${order.notes.trim()}")
        }
        notesList.add("Dokumen ini adalah Purchase Order (Order Pembelian) dan bukan bukti pembayaran / transaksi final.")
        notesList.add("Mohon supplier melakukan konfirmasi ketersediaan barang dan harga aktual sebelum pengiriman.")

        val itemsSection = ReportSection(
            title = "DAFTAR BARANG PESANAN (${items.size} Item)",
            tableColumns = tableColumns,
            tableRows = tableRows,
            notes = notesList
        )

        return PdfReportDocument(
            header = header,
            sections = listOf(infoSection, itemsSection),
            footerNote = "Buku Warung — Dokumen Purchase Order"
        )
    }
}
