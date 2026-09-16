package id.skmnetwork.bukuwarung.pdf.reports

import android.graphics.Paint
import id.skmnetwork.bukuwarung.pdf.KeyValuePair
import id.skmnetwork.bukuwarung.pdf.PdfReportDocument
import id.skmnetwork.bukuwarung.pdf.ReportHeader
import id.skmnetwork.bukuwarung.pdf.ReportSection
import id.skmnetwork.bukuwarung.pdf.TableColumn
import id.skmnetwork.bukuwarung.pdf.TableRow
import id.skmnetwork.bukuwarung.util.formatRupiah

/**
 * Gate G.1.5 — Presentation model for Purchase Report rows.
 */
data class PurchaseReportRow(
    val dateFormatted: String,
    val transactionNumber: String,
    val supplierName: String,
    val paymentMethod: String,
    val totalAmount: Long,
    val status: String
)

/**
 * Gate G.1.5 — Presentation data container for the entire Purchase Report.
 */
data class PurchaseReportData(
    val shopName: String,
    val address: String = "",
    val phone: String = "",
    val periodLabel: String,
    val printedAt: String,
    val items: List<PurchaseReportRow>,
    val totalPurchases: Long,
    val cashPurchasesTotal: Long,
    val creditPurchasesTotal: Long,
    val totalTransactions: Int
)

/**
 * Gate G.1.5 — Builder to construct structured PdfReportDocument for "LAPORAN PEMBELIAN".
 */
object PurchaseReportPdfBuilder {

    fun build(data: PurchaseReportData): PdfReportDocument {
        val header = ReportHeader(
            shopName = data.shopName.ifBlank { "Warung Saya" },
            address = data.address,
            phone = data.phone,
            reportTitle = "LAPORAN PEMBELIAN",
            periodLabel = data.periodLabel,
            printedAt = data.printedAt
        )

        // 1. SUMMARY METRICS SECTION
        val summaryPairs = mutableListOf(
            KeyValuePair(
                label = "Total Transaksi Pembelian",
                value = "${data.totalTransactions} transaksi"
            ),
            KeyValuePair(
                label = "Total Pembelian Tunai",
                value = formatRupiah(data.cashPurchasesTotal)
            ),
            KeyValuePair(
                label = "Total Pembelian Kredit (Hutang)",
                value = formatRupiah(data.creditPurchasesTotal)
            ),
            KeyValuePair(
                label = "Total Pembelian (Kulakan)",
                value = formatRupiah(data.totalPurchases),
                isBold = true,
                isHighlight = true
            )
        )

        val summarySection = ReportSection(
            title = "RINGKASAN PEMBELIAN",
            summaryPairs = summaryPairs
        )

        // 2. PURCHASE DETAILS TABLE SECTION
        val tableSection = if (data.items.isEmpty()) {
            ReportSection(
                title = "DAFTAR PEMBELIAN",
                notes = listOf(
                    "Tidak ada transaksi pembelian pada periode ${data.periodLabel}."
                )
            )
        } else {
            val columns = listOf(
                TableColumn(header = "No", weight = 0.8f, align = Paint.Align.CENTER),
                TableColumn(header = "Waktu / No. Trx", weight = 3.4f, align = Paint.Align.LEFT),
                TableColumn(header = "Supplier", weight = 2.4f, align = Paint.Align.LEFT),
                TableColumn(header = "Metode", weight = 1.6f, align = Paint.Align.CENTER),
                TableColumn(header = "Total", weight = 2.2f, align = Paint.Align.RIGHT),
                TableColumn(header = "Status", weight = 2.0f, align = Paint.Align.RIGHT)
            )

            val rows = mutableListOf<TableRow>()
            data.items.forEachIndexed { index, item ->
                rows.add(
                    TableRow(
                        cells = listOf(
                            (index + 1).toString(),
                            "${item.dateFormatted} • ${item.transactionNumber}",
                            item.supplierName,
                            item.paymentMethod,
                            formatRupiah(item.totalAmount),
                            item.status
                        )
                    )
                )
            }

            // Summary Total Row
            rows.add(
                TableRow(
                    cells = listOf(
                        "",
                        "TOTAL (${data.totalTransactions} TRX)",
                        "",
                        "",
                        formatRupiah(data.totalPurchases),
                        "Selesai"
                    ),
                    isBold = true,
                    isTotal = true
                )
            )

            val notes = listOf(
                "Semua transaksi pembelian diurutkan dari yang terbaru.",
                "Pembelian kredit (hutang supplier) tercatat secara terpisah dari arus kas keluar."
            )

            ReportSection(
                title = "DAFTAR PEMBELIAN (${data.items.size} Transaksi)",
                tableColumns = columns,
                tableRows = rows,
                notes = notes
            )
        }

        return PdfReportDocument(
            header = header,
            sections = listOf(summarySection, tableSection),
            footerNote = "Buku Warung — Catatan Keuangan & Kasir UMKM"
        )
    }
}
