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
    val totalTransactions: Int,
    val terminology: BusinessTerminology = BusinessTerminology()
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
            reportTitle = "LAPORAN ${data.terminology.purchaseLabel.uppercase()}",
            periodLabel = data.periodLabel,
            printedAt = data.printedAt
        )

        // 1. SUMMARY METRICS SECTION
        val summaryPairs = mutableListOf(
            KeyValuePair(
                label = "Total Transaksi ${data.terminology.purchaseLabel}",
                value = "${data.totalTransactions} transaksi"
            ),
            KeyValuePair(
                label = "Total ${data.terminology.purchaseLabel} Tunai",
                value = formatRupiah(data.cashPurchasesTotal)
            ),
            KeyValuePair(
                label = "Total ${data.terminology.purchaseLabel} Kredit (${data.terminology.debtLabel})",
                value = formatRupiah(data.creditPurchasesTotal)
            ),
            KeyValuePair(
                label = "Total ${data.terminology.purchaseLabel}",
                value = formatRupiah(data.totalPurchases),
                isBold = true,
                isHighlight = true
            )
        )

        val summarySection = ReportSection(
            title = "RINGKASAN ${data.terminology.purchaseLabel.uppercase()}",
            summaryPairs = summaryPairs
        )

        // 2. PURCHASE DETAILS TABLE SECTION
        val tableSection = if (data.items.isEmpty()) {
            ReportSection(
                title = "DAFTAR ${data.terminology.purchaseLabel.uppercase()}",
                notes = listOf(
                    "Tidak ada transaksi ${data.terminology.purchaseLabel.lowercase()} pada periode ${data.periodLabel}."
                )
            )
        } else {
            val columns = listOf(
                TableColumn(header = "No", weight = 0.8f, align = Paint.Align.CENTER),
                TableColumn(header = "Waktu / No. Trx", weight = 3.4f, align = Paint.Align.LEFT),
                TableColumn(header = data.terminology.supplierLabel, weight = 2.4f, align = Paint.Align.LEFT),
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
                "Semua transaksi ${data.terminology.purchaseLabel.lowercase()} diurutkan dari yang terbaru.",
                "${data.terminology.purchaseLabel} kredit (${data.terminology.debtLabel.lowercase()} ${data.terminology.supplierLabel.lowercase()}) tercatat secara terpisah dari arus kas keluar."
            )

            ReportSection(
                title = "DAFTAR ${data.terminology.purchaseLabel.uppercase()} (${data.items.size} Transaksi)",
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
