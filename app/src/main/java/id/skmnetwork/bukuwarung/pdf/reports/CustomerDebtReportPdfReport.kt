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
 * Gate G.1.6 — Mode of Customer Debt Report.
 */
enum class DebtReportMode {
    CURRENT_OUTSTANDING,
    MUTATION_PERIOD
}

/**
 * Gate G.1.6 — Presentation model for individual Debt Report rows.
 */
data class DebtReportRow(
    val dateFormatted: String,
    val transactionNumber: String,
    val customerName: String,
    val customerPhone: String,
    val totalDebt: Long,
    val paidAmount: Long,
    val remainingDebt: Long,
    val status: String
)

/**
 * Gate G.1.6 — Presentation data container for the entire Customer Debt Report.
 */
data class DebtReportData(
    val mode: DebtReportMode,
    val shopName: String,
    val address: String = "",
    val phone: String = "",
    val periodLabel: String,
    val printedAt: String,
    val items: List<DebtReportRow>,
    val totalOutstanding: Long,
    val totalPaid: Long,
    val totalDebtCreated: Long,
    val activeDebtorsCount: Int,
    val totalTransactions: Int
)

/**
 * Gate G.1.6 — Builder to construct structured PdfReportDocument for "LAPORAN PIUTANG PELANGGAN".
 */
object CustomerDebtReportPdfBuilder {

    fun build(data: DebtReportData): PdfReportDocument {
        val title = if (data.mode == DebtReportMode.CURRENT_OUTSTANDING) {
            "LAPORAN PIUTANG PELANGGAN"
        } else {
            "LAPORAN MUTASI PIUTANG PELANGGAN"
        }

        val header = ReportHeader(
            shopName = data.shopName.ifBlank { "Warung Saya" },
            address = data.address,
            phone = data.phone,
            reportTitle = title,
            periodLabel = data.periodLabel,
            printedAt = data.printedAt
        )

        // 1. SUMMARY METRICS SECTION
        val summaryPairs = mutableListOf<KeyValuePair>()
        if (data.mode == DebtReportMode.CURRENT_OUTSTANDING) {
            summaryPairs.add(
                KeyValuePair(
                    label = "Total Pelanggan Berpiutang",
                    value = "${data.activeDebtorsCount} orang"
                )
            )
            summaryPairs.add(
                KeyValuePair(
                    label = "Total Tagihan Piutang Dibuat",
                    value = formatRupiah(data.totalDebtCreated)
                )
            )
            summaryPairs.add(
                KeyValuePair(
                    label = "Total Sudah Dibayar",
                    value = formatRupiah(data.totalPaid)
                )
            )
            summaryPairs.add(
                KeyValuePair(
                    label = "Sisa Piutang Belum Lunas (Aktif)",
                    value = formatRupiah(data.totalOutstanding),
                    isBold = true,
                    isHighlight = true
                )
            )
        } else {
            summaryPairs.add(
                KeyValuePair(
                    label = "Total Transaksi Piutang Periode",
                    value = "${data.totalTransactions} transaksi"
                )
            )
            summaryPairs.add(
                KeyValuePair(
                    label = "Total Piutang Timbul",
                    value = formatRupiah(data.totalDebtCreated),
                    isBold = true
                )
            )
            summaryPairs.add(
                KeyValuePair(
                    label = "Total Sudah Dibayar Saat Ini",
                    value = formatRupiah(data.totalPaid)
                )
            )
            summaryPairs.add(
                KeyValuePair(
                    label = "Sisa Piutang Belum Lunas Saat Ini",
                    value = formatRupiah(data.totalOutstanding),
                    isBold = true,
                    isHighlight = true
                )
            )
        }

        val summarySection = ReportSection(
            title = if (data.mode == DebtReportMode.CURRENT_OUTSTANDING) "RINGKASAN PIUTANG AKTIF" else "RINGKASAN MUTASI PIUTANG",
            summaryPairs = summaryPairs
        )

        // 2. DEBT DETAILS TABLE SECTION
        val tableSection = if (data.items.isEmpty()) {
            ReportSection(
                title = if (data.mode == DebtReportMode.CURRENT_OUTSTANDING) "DAFTAR PIUTANG AKTIF" else "DAFTAR TRANSAKSI PIUTANG",
                notes = listOf(
                    if (data.mode == DebtReportMode.CURRENT_OUTSTANDING) {
                        "Tidak ada catatan piutang aktif saat ini. Semua piutang telah lunas."
                    } else {
                        "Tidak ada transaksi piutang pada periode ${data.periodLabel}."
                    }
                )
            )
        } else {
            val columns = listOf(
                TableColumn(header = "No", weight = 0.7f, align = Paint.Align.CENTER),
                TableColumn(header = "Tgl / No. Trx", weight = 2.8f, align = Paint.Align.LEFT),
                TableColumn(header = "Pelanggan", weight = 2.5f, align = Paint.Align.LEFT),
                TableColumn(header = "Kontak", weight = 2.0f, align = Paint.Align.LEFT),
                TableColumn(header = "Total Piutang", weight = 2.2f, align = Paint.Align.RIGHT),
                TableColumn(header = "Dibayar", weight = 2.0f, align = Paint.Align.RIGHT),
                TableColumn(header = "Sisa", weight = 2.0f, align = Paint.Align.RIGHT),
                TableColumn(header = "Status", weight = 1.8f, align = Paint.Align.RIGHT)
            )

            val rows = mutableListOf<TableRow>()
            data.items.forEachIndexed { index, item ->
                rows.add(
                    TableRow(
                        cells = listOf(
                            "${index + 1}",
                            "${item.dateFormatted}\n${item.transactionNumber}",
                            item.customerName,
                            item.customerPhone,
                            formatRupiah(item.totalDebt),
                            formatRupiah(item.paidAmount),
                            formatRupiah(item.remainingDebt),
                            item.status
                        )
                    )
                )
            }

            // TOTAL ROW
            rows.add(
                TableRow(
                    cells = listOf(
                        "",
                        "TOTAL PIUTANG",
                        "",
                        "",
                        formatRupiah(data.totalDebtCreated),
                        formatRupiah(data.totalPaid),
                        formatRupiah(data.totalOutstanding),
                        ""
                    ),
                    isTotal = true,
                    isBold = true
                )
            )

            ReportSection(
                title = if (data.mode == DebtReportMode.CURRENT_OUTSTANDING) {
                    "DAFTAR PIUTANG AKTIF (${data.items.size} transaksi)"
                } else {
                    "DAFTAR TRANSAKSI PIUTANG (${data.items.size} transaksi)"
                },
                tableColumns = columns,
                tableRows = rows
            )
        }

        return PdfReportDocument(
            header = header,
            sections = listOf(summarySection, tableSection),
            footerNote = "Buku Warung — Sistem Catatan Keuangan Warung & UMKM"
        )
    }
}
