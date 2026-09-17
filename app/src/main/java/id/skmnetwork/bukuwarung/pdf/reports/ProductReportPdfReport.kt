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
 * Gate G.1.4 — Presentation model for Product Report rows.
 */
data class ProductReportRow(
    val productUuid: String,
    val productName: String,
    val quantitySold: Double,
    val revenue: Long,
    val cogs: Long,
    val grossProfit: Long
)

/**
 * Gate G.1.4 — Presentation data container for the entire Product Report.
 */
data class ProductReportData(
    val shopName: String,
    val address: String = "",
    val phone: String = "",
    val periodLabel: String,
    val printedAt: String,
    val items: List<ProductReportRow>,
    val totalProductsCount: Int,
    val totalNetQuantity: Double,
    val totalNetRevenue: Long,
    val totalNetCogs: Long,
    val totalGrossProfit: Long,
    val terminology: BusinessTerminology = BusinessTerminology()
)

/**
 * Gate G.1.4 — Builder to construct structured PdfReportDocument for "LAPORAN PENJUALAN PRODUK".
 */
object ProductReportPdfBuilder {

    fun build(data: ProductReportData): PdfReportDocument {
        val header = ReportHeader(
            shopName = data.shopName.ifBlank { "Warung Saya" },
            address = data.address,
            phone = data.phone,
            reportTitle = "LAPORAN ${data.terminology.transactionLabel.uppercase()} ${data.terminology.productLabel.uppercase()}",
            periodLabel = data.periodLabel,
            printedAt = data.printedAt
        )

        val totalQtyText = if (data.totalNetQuantity % 1.0 == 0.0) {
            "${data.totalNetQuantity.toInt()} pcs"
        } else {
            "${data.totalNetQuantity} pcs"
        }

        // 1. SUMMARY METRICS SECTION
        val summaryPairs = mutableListOf(
            KeyValuePair(
                label = "Total Jenis ${data.terminology.productLabel}",
                value = "${data.totalProductsCount} ${data.terminology.productLabel.lowercase()}"
            ),
            KeyValuePair(
                label = "Total Jumlah Terjual",
                value = totalQtyText
            ),
            KeyValuePair(
                label = "Total Omzet Bersih",
                value = formatRupiah(data.totalNetRevenue)
            ),
            KeyValuePair(
                label = "Total HPP / Modal ${data.terminology.productLabel}",
                value = formatRupiah(data.totalNetCogs)
            ),
            KeyValuePair(
                label = "Total Laba Kotor ${data.terminology.productLabel}",
                value = formatRupiah(data.totalGrossProfit),
                isBold = true,
                isHighlight = true
            )
        )

        val summarySection = ReportSection(
            title = "RINGKASAN ${data.terminology.transactionLabel.uppercase()} ${data.terminology.productLabel.uppercase()}",
            summaryPairs = summaryPairs
        )

        // 2. PRODUCT DETAILS TABLE SECTION
        val tableSection = if (data.items.isEmpty()) {
            ReportSection(
                title = "DAFTAR ${data.terminology.productLabel.uppercase()}",
                notes = listOf(
                    "Tidak ada ${data.terminology.transactionLabel.lowercase()} ${data.terminology.productLabel.lowercase()} pada periode ${data.periodLabel}."
                )
            )
        } else {
            val columns = listOf(
                TableColumn(header = "No", weight = 0.8f, align = Paint.Align.CENTER),
                TableColumn(header = "Nama ${data.terminology.productLabel}", weight = 3.4f, align = Paint.Align.LEFT),
                TableColumn(header = "Qty", weight = 1.6f, align = Paint.Align.RIGHT),
                TableColumn(header = "Omzet", weight = 2.1f, align = Paint.Align.RIGHT),
                TableColumn(header = "HPP", weight = 2.0f, align = Paint.Align.RIGHT),
                TableColumn(header = "Laba Kotor", weight = 2.1f, align = Paint.Align.RIGHT)
            )

            val rows = mutableListOf<TableRow>()
            data.items.forEachIndexed { index, item ->
                val qtyText = if (item.quantitySold % 1.0 == 0.0) {
                    "${item.quantitySold.toInt()}"
                } else {
                    "${item.quantitySold}"
                }
                rows.add(
                    TableRow(
                        cells = listOf(
                            (index + 1).toString(),
                            item.productName,
                            qtyText,
                            formatRupiah(item.revenue),
                            formatRupiah(item.cogs),
                            formatRupiah(item.grossProfit)
                        )
                    )
                )
            }

            // Summary Total Row
            rows.add(
                TableRow(
                    cells = listOf(
                        "",
                        "TOTAL (${data.totalProductsCount} ${data.terminology.productLabel.uppercase()})",
                        if (data.totalNetQuantity % 1.0 == 0.0) "${data.totalNetQuantity.toInt()}" else "${data.totalNetQuantity}",
                        formatRupiah(data.totalNetRevenue),
                        formatRupiah(data.totalNetCogs),
                        formatRupiah(data.totalGrossProfit)
                    ),
                    isBold = true,
                    isTotal = true
                )
            )

            val notes = listOf(
                "Semua nilai omzet, HPP, dan laba kotor dihitung berdasarkan ${data.terminology.transactionLabel.lowercase()} bersih (dikurangi retur).",
                "HPP menggunakan historical purchase price snapshot saat ${data.terminology.productLabel.lowercase()} terjual."
            )

            ReportSection(
                title = "DAFTAR ${data.terminology.productLabel.uppercase()} TERJUAL (${data.items.size} ${data.terminology.productLabel})",
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
