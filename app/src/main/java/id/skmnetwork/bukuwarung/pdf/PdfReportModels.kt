package id.skmnetwork.bukuwarung.pdf

import android.graphics.Paint

/**
 * Gate G.1.1 — PDF Report Domain & Layout Models.
 * Decoupled from business logic, structured for native canvas-based PDF generation.
 */

data class ReportHeader(
    val shopName: String,
    val address: String = "",
    val phone: String = "",
    val reportTitle: String,
    val periodLabel: String,
    val printedAt: String
)

data class TableColumn(
    val header: String,
    val weight: Float = 1f,
    val align: Paint.Align = Paint.Align.LEFT
)

data class TableRow(
    val cells: List<String>,
    val isBold: Boolean = false,
    val isHeader: Boolean = false,
    val isTotal: Boolean = false,
    val backgroundColor: Int? = null
)

data class KeyValuePair(
    val label: String,
    val value: String,
    val isBold: Boolean = false,
    val isHighlight: Boolean = false,
    val isNegative: Boolean = false
)

data class ReportSection(
    val title: String? = null,
    val description: String? = null,
    val summaryPairs: List<KeyValuePair> = emptyList(),
    val tableColumns: List<TableColumn> = emptyList(),
    val tableRows: List<TableRow> = emptyList(),
    val notes: List<String> = emptyList()
)

data class PdfReportDocument(
    val header: ReportHeader,
    val sections: List<ReportSection>,
    val footerNote: String = "Buku Warung — Catatan Keuangan & Kasir UMKM"
)
