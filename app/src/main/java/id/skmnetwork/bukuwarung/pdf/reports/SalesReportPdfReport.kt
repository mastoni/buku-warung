package id.skmnetwork.bukuwarung.pdf.reports

import android.graphics.Paint
import id.skmnetwork.bukuwarung.domain.business.BusinessTerminology
import id.skmnetwork.bukuwarung.pdf.KeyValuePair
import id.skmnetwork.bukuwarung.pdf.PdfReportDocument
import id.skmnetwork.bukuwarung.pdf.ReportHeader
import id.skmnetwork.bukuwarung.pdf.ReportSection
import id.skmnetwork.bukuwarung.pdf.TableColumn
import id.skmnetwork.bukuwarung.pdf.TableRow
import id.skmnetwork.bukuwarung.util.formatRupiah

/**
 * Gate G.1.3 — Presentation model for Sales Report rows.
 */
data class SalesReportRow(
    val dateFormatted: String,
    val transactionNumber: String,
    val customerName: String,
    val paymentMethod: String,
    val totalAmount: Long,
    val refundAmount: Long = 0L,
    val status: String
)

/**
 * Gate G.1.3 — Presentation data container for the entire Sales Report.
 */
data class SalesReportData(
    val shopName: String,
    val address: String = "",
    val phone: String = "",
    val periodLabel: String,
    val printedAt: String,
    val items: List<SalesReportRow>,
    val grossSales: Long,
    val totalRefund: Long,
    val netSales: Long,
    val totalTransactions: Int,
    val terminology: BusinessTerminology = BusinessTerminology()
)

/**
 * Gate G.1.3 — Builder to construct structured PdfReportDocument for "LAPORAN PENJUALAN".
 */
object SalesReportPdfBuilder {

    fun build(data: SalesReportData): PdfReportDocument {
        val header = ReportHeader(
            shopName = data.shopName.ifBlank { "Warung Saya" },
            address = data.address,
            phone = data.phone,
            reportTitle = "LAPORAN ${data.terminology.transactionLabel.uppercase()}",
            periodLabel = data.periodLabel,
            printedAt = data.printedAt
        )

        // 1. SUMMARY METRICS SECTION
        val summaryPairs = mutableListOf(
            KeyValuePair(
                label = "Total Transaksi ${data.terminology.transactionLabel}",
                value = "${data.totalTransactions} transaksi"
            ),
            KeyValuePair(
                label = "Total ${data.terminology.transactionLabel} Bruto",
                value = formatRupiah(data.grossSales)
            )
        )
        if (data.totalRefund > 0) {
            summaryPairs.add(
                KeyValuePair(
                    label = "Total Pengembalian / Retur",
                    value = "-${formatRupiah(data.totalRefund)}",
                    isNegative = true
                )
            )
        }
        summaryPairs.add(
            KeyValuePair(
                label = "Total ${data.terminology.transactionLabel} Bersih (Net)",
                value = formatRupiah(data.netSales),
                isBold = true,
                isHighlight = true
            )
        )

        val summarySection = ReportSection(
            title = "RINGKASAN ${data.terminology.transactionLabel.uppercase()}",
            summaryPairs = summaryPairs
        )

        // 2. TRANSACTION DETAILS TABLE SECTION
        val tableSection = if (data.items.isEmpty()) {
            ReportSection(
                title = "DAFTAR TRANSAKSI",
                notes = listOf(
                    "Tidak ada transaksi ${data.terminology.transactionLabel.lowercase()} pada periode ${data.periodLabel}."
                )
            )
        } else {
            val columns = listOf(
                TableColumn(header = "No", weight = 0.8f, align = Paint.Align.CENTER),
                TableColumn(header = "Waktu / No. Trx", weight = 3.4f, align = Paint.Align.LEFT),
                TableColumn(header = data.terminology.customerLabel, weight = 2.4f, align = Paint.Align.LEFT),
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
                            item.customerName,
                            item.paymentMethod,
                            formatRupiah(item.totalAmount),
                            item.status
                        )
                    )
                )
            }

            // Total summary row at bottom
            rows.add(
                TableRow(
                    cells = listOf(
                        "",
                        "TOTAL (${data.totalTransactions} TRX)",
                        "",
                        "",
                        formatRupiah(data.grossSales),
                        if (data.totalRefund > 0) "Net ${formatRupiah(data.netSales)}" else "Selesai"
                    ),
                    isBold = true,
                    isTotal = true
                )
            )

            val notes = mutableListOf(
                "Semua transaksi diurutkan dari yang terbaru.",
                "Transaksi dengan retur tidak mengubah nilai ${data.terminology.transactionLabel.lowercase()} asli dan ditampilkan pada status."
            )

            ReportSection(
                title = "DAFTAR TRANSAKSI (${data.items.size} Transaksi)",
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
