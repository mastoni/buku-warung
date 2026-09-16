package id.skmnetwork.bukuwarung.pdf.reports

import id.skmnetwork.bukuwarung.pdf.KeyValuePair
import id.skmnetwork.bukuwarung.pdf.PdfReportDocument
import id.skmnetwork.bukuwarung.pdf.ReportHeader
import id.skmnetwork.bukuwarung.pdf.ReportSection
import id.skmnetwork.bukuwarung.util.formatRupiah

/**
 * Gate G.1.2 — Business Summary Report Presentation Data.
 * Decoupled presentation model strictly populated from ReportRepository source of truth.
 */
data class BusinessSummaryReportData(
    val shopName: String,
    val address: String = "",
    val phone: String = "",
    val periodLabel: String,
    val printedAt: String,

    // A. Penjualan
    val grossSales: Long,
    val salesReturn: Long,
    val netSales: Long,
    val salesCount: Int,
    val salesReturnCount: Int,

    // B. Laba Rugi
    val netCogs: Long,
    val grossProfit: Long,
    val operatingExpense: Long,
    val netProfit: Long,

    // C. Posisi Keuangan Riil
    val cashBalance: Long,
    val stockValue: Long,
    val outstandingDebt: Long,
    val outstandingPayable: Long
)

/**
 * Gate G.1.2 — Builder to construct structured PdfReportDocument for "RINGKASAN USAHA".
 */
object BusinessSummaryPdfBuilder {

    fun build(data: BusinessSummaryReportData): PdfReportDocument {
        val header = ReportHeader(
            shopName = data.shopName.ifBlank { "Warung Saya" },
            address = data.address,
            phone = data.phone,
            reportTitle = "RINGKASAN USAHA",
            periodLabel = data.periodLabel,
            printedAt = data.printedAt
        )

        // 1. SECTION A: PENJUALAN
        val salesPairs = mutableListOf(
            KeyValuePair(
                label = "Penjualan Kotor (Bruto)",
                value = formatRupiah(data.grossSales)
            ),
            KeyValuePair(
                label = "Retur Penjualan",
                value = if (data.salesReturn > 0) "-${formatRupiah(data.salesReturn)}" else formatRupiah(0L),
                isNegative = data.salesReturn > 0
            ),
            KeyValuePair(
                label = "Penjualan Bersih",
                value = formatRupiah(data.netSales),
                isBold = true
            ),
            KeyValuePair(
                label = "Jumlah Transaksi Penjualan",
                value = "${data.salesCount} transaksi"
            )
        )
        if (data.salesReturnCount > 0) {
            salesPairs.add(
                KeyValuePair(
                    label = "Jumlah Transaksi Retur",
                    value = "${data.salesReturnCount} transaksi"
                )
            )
        }

        val salesSection = ReportSection(
            title = "A. PENJUALAN & PENDAPATAN",
            summaryPairs = salesPairs
        )

        // 2. SECTION B: LABA RUGI
        val profitPairs = listOf(
            KeyValuePair(
                label = "HPP / Modal Barang Terjual",
                value = if (data.netCogs > 0) "-${formatRupiah(data.netCogs)}" else formatRupiah(0L)
            ),
            KeyValuePair(
                label = "Laba Kotor (Gross Profit)",
                value = formatRupiah(data.grossProfit),
                isBold = true,
                isNegative = data.grossProfit < 0
            ),
            KeyValuePair(
                label = "Beban Operasional (Listrik, Sewa, Gaji, dll)",
                value = if (data.operatingExpense > 0) "-${formatRupiah(data.operatingExpense)}" else formatRupiah(0L),
                isNegative = data.operatingExpense > 0
            ),
            KeyValuePair(
                label = "Laba Bersih (Net Profit)",
                value = formatRupiah(data.netProfit),
                isBold = true,
                isHighlight = true,
                isNegative = data.netProfit < 0
            )
        )

        val profitSection = ReportSection(
            title = "B. LABA RUGI USAHA",
            summaryPairs = profitPairs
        )

        // 3. SECTION C: POSISI KEUANGAN SAAT INI
        val positionPairs = listOf(
            KeyValuePair(
                label = "Saldo Kas Tersedia",
                value = formatRupiah(data.cashBalance),
                isBold = true
            ),
            KeyValuePair(
                label = "Nilai Modal Stok Barang",
                value = formatRupiah(data.stockValue)
            ),
            KeyValuePair(
                label = "Total Piutang Pelanggan (Belum Lunas)",
                value = formatRupiah(data.outstandingDebt),
                isNegative = data.outstandingDebt > 0
            ),
            KeyValuePair(
                label = "Total Hutang ke Supplier (Belum Lunas)",
                value = formatRupiah(data.outstandingPayable),
                isNegative = data.outstandingPayable > 0
            )
        )

        val positionSection = ReportSection(
            title = "C. POSISI KEUANGAN SAAT INI",
            description = "Posisi saldo kas, modal stok, serta kewajiban riil toko saat ini.",
            summaryPairs = positionPairs,
            notes = listOf(
                "Nilai HPP dihitung berdasarkan snapshot harga modal riil saat transaksi penjualan terjadi.",
                "Saldo kas dan stok barang merepresentasikan posisi saat ini dan terpisah dari laba periode berjalan.",
                "Laporan ini dibuat otomatis dari aplikasi Buku Warung."
            )
        )

        return PdfReportDocument(
            header = header,
            sections = listOf(salesSection, profitSection, positionSection),
            footerNote = "Buku Warung — Catatan Keuangan & Kasir UMKM"
        )
    }
}
