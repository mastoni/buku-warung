package id.skmnetwork.bukuwarung.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Gate G.1.1 — Native Canvas-based PDF Report Generator.
 * Uses Android SDK built-in android.graphics.pdf.PdfDocument.
 * Produces clean, professional A4 portrait documents with pagination, headers, footers, and structured tables.
 */
class PdfReportGenerator(
    private val context: Context
) {

    companion object {
        // Standard A4 dimensions at 72 DPI (Points)
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842

        const val MARGIN_LEFT = 36f
        const val MARGIN_RIGHT = 36f
        const val MARGIN_TOP = 36f
        const val MARGIN_BOTTOM = 40f

        const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT // 523f

        private val COLOR_TEXT_PRIMARY = Color.rgb(33, 33, 33)
        private val COLOR_TEXT_SECONDARY = Color.rgb(117, 117, 117)
        private val COLOR_BRAND_GREEN = Color.rgb(46, 125, 50)
        private val COLOR_RED_EXPENSE = Color.rgb(198, 40, 40)
        private val COLOR_HEADER_BG = Color.rgb(240, 244, 240)
        private val COLOR_ROW_ALT_BG = Color.rgb(250, 250, 250)
        private val COLOR_DIVIDER = Color.rgb(224, 224, 224)
    }

    /**
     * Generates a PDF file from a structured PdfReportDocument.
     * Guaranteed to return a valid File in context.cacheDir/reports.
     */
    suspend fun generatePdf(
        document: PdfReportDocument,
        outputFileName: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        val pdfDoc = PdfDocument()
        var currentY = MARGIN_TOP
        var pageNumber = 1

        val fileName = outputFileName ?: PdfShareManager.generateSafeFileName(
            reportTitle = document.header.reportTitle,
            periodLabel = document.header.periodLabel
        )
        val reportsDir = PdfShareManager.getReportsDirectory(context)
        val outputFile = File(reportsDir, fileName)

        try {
            // Paint configurations
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TEXT_PRIMARY
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 15f
            }

            val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TEXT_SECONDARY
                typeface = Typeface.DEFAULT
                textSize = 9.5f
            }

            val reportTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_BRAND_GREEN
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 13.5f
            }

            val sectionTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TEXT_PRIMARY
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 11f
            }

            val bodyRegularPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TEXT_PRIMARY
                typeface = Typeface.DEFAULT
                textSize = 9f
            }

            val bodyBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TEXT_PRIMARY
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 9f
            }

            val bodySecondaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TEXT_SECONDARY
                typeface = Typeface.DEFAULT
                textSize = 8.5f
            }

            val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_DIVIDER
                strokeWidth = 0.8f
                style = Paint.Style.STROKE
            }

            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }

            // Page management helpers
            var currentPageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            var currentPage = pdfDoc.startPage(currentPageInfo)
            var canvas = currentPage.canvas

            fun drawFooter() {
                val footerY = PAGE_HEIGHT - MARGIN_BOTTOM + 18f
                canvas.drawLine(MARGIN_LEFT, footerY - 10f, PAGE_WIDTH - MARGIN_RIGHT, footerY - 10f, dividerPaint)
                
                bodySecondaryPaint.textAlign = Paint.Align.LEFT
                canvas.drawText(document.footerNote, MARGIN_LEFT, footerY, bodySecondaryPaint)

                bodySecondaryPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText("Halaman $pageNumber", PAGE_WIDTH - MARGIN_RIGHT, footerY, bodySecondaryPaint)
                bodySecondaryPaint.textAlign = Paint.Align.LEFT
            }

            fun createNewPage() {
                drawFooter()
                pdfDoc.finishPage(currentPage)
                pageNumber++
                currentPageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                currentPage = pdfDoc.startPage(currentPageInfo)
                canvas = currentPage.canvas
                currentY = MARGIN_TOP

                // Draw mini running header on subsequent pages
                bodySecondaryPaint.textAlign = Paint.Align.LEFT
                canvas.drawText("${document.header.shopName} — ${document.header.reportTitle} (${document.header.periodLabel})", MARGIN_LEFT, currentY + 8f, bodySecondaryPaint)
                canvas.drawLine(MARGIN_LEFT, currentY + 14f, PAGE_WIDTH - MARGIN_RIGHT, currentY + 14f, dividerPaint)
                currentY += 26f
            }

            fun ensureSpace(neededHeight: Float) {
                if (currentY + neededHeight > PAGE_HEIGHT - MARGIN_BOTTOM - 20f) {
                    createNewPage()
                }
            }

            // ==========================================
            // 1. DRAW MAIN REPORT HEADER (Page 1)
            // ==========================================
            canvas.drawText(document.header.shopName, MARGIN_LEFT, currentY + 14f, titlePaint)
            currentY += 18f

            if (document.header.address.isNotBlank() || document.header.phone.isNotBlank()) {
                val contactText = listOf(document.header.address, document.header.phone).filter { it.isNotBlank() }.joinToString(" • ")
                canvas.drawText(contactText, MARGIN_LEFT, currentY + 9f, subtitlePaint)
                currentY += 14f
            }

            canvas.drawLine(MARGIN_LEFT, currentY + 4f, PAGE_WIDTH - MARGIN_RIGHT, currentY + 4f, dividerPaint)
            currentY += 12f

            canvas.drawText(document.header.reportTitle.uppercase(), MARGIN_LEFT, currentY + 12f, reportTitlePaint)
            currentY += 16f

            val periodAndDate = "Periode: ${document.header.periodLabel}   |   Waktu Cetak: ${document.header.printedAt}"
            canvas.drawText(periodAndDate, MARGIN_LEFT, currentY + 9f, subtitlePaint)
            currentY += 18f

            canvas.drawLine(MARGIN_LEFT, currentY, PAGE_WIDTH - MARGIN_RIGHT, currentY, dividerPaint)
            currentY += 14f

            // ==========================================
            // 2. DRAW SECTIONS
            // ==========================================
            for (section in document.sections) {
                // Section Title
                if (!section.title.isNullOrBlank()) {
                    ensureSpace(28f)
                    canvas.drawText(section.title, MARGIN_LEFT, currentY + 10f, sectionTitlePaint)
                    currentY += 16f
                }

                // Section Description
                if (!section.description.isNullOrBlank()) {
                    ensureSpace(16f)
                    canvas.drawText(section.description, MARGIN_LEFT, currentY + 8f, subtitlePaint)
                    currentY += 12f
                }

                // Summary Key-Value Pairs
                if (section.summaryPairs.isNotEmpty()) {
                    val rowHeight = 16f
                    for (pair in section.summaryPairs) {
                        ensureSpace(rowHeight + 4f)

                        if (pair.isHighlight) {
                            bgPaint.color = COLOR_HEADER_BG
                            canvas.drawRoundRect(
                                RectF(MARGIN_LEFT, currentY, PAGE_WIDTH - MARGIN_RIGHT, currentY + rowHeight),
                                4f, 4f, bgPaint
                            )
                        }

                        val labelPaint = if (pair.isBold) bodyBoldPaint else bodyRegularPaint
                        val valuePaint = Paint(if (pair.isBold) bodyBoldPaint else bodyRegularPaint).apply {
                            if (pair.isNegative) color = COLOR_RED_EXPENSE
                            if (pair.isHighlight) color = COLOR_BRAND_GREEN
                            textAlign = Paint.Align.RIGHT
                        }

                        canvas.drawText(pair.label, MARGIN_LEFT + 6f, currentY + 11f, labelPaint)
                        canvas.drawText(pair.value, PAGE_WIDTH - MARGIN_RIGHT - 6f, currentY + 11f, valuePaint)
                        currentY += rowHeight + 2f
                    }
                    currentY += 4f
                }

                // Structured Table
                if (section.tableColumns.isNotEmpty() && section.tableRows.isNotEmpty()) {
                    val totalWeight = section.tableColumns.sumOf { it.weight.toDouble() }.toFloat()
                    val colWidths = section.tableColumns.map { (it.weight / totalWeight) * CONTENT_WIDTH }

                    fun drawTableHeader() {
                        ensureSpace(20f)
                        bgPaint.color = COLOR_HEADER_BG
                        canvas.drawRect(MARGIN_LEFT, currentY, PAGE_WIDTH - MARGIN_RIGHT, currentY + 18f, bgPaint)

                        var colX = MARGIN_LEFT
                        for (i in section.tableColumns.indices) {
                            val col = section.tableColumns[i]
                            val w = colWidths[i]
                            val paint = Paint(bodyBoldPaint).apply { textAlign = col.align }
                            val textX = when (col.align) {
                                Paint.Align.LEFT -> colX + 4f
                                Paint.Align.CENTER -> colX + (w / 2f)
                                Paint.Align.RIGHT -> colX + w - 4f
                            }
                            canvas.drawText(col.header, textX, currentY + 12f, paint)
                            colX += w
                        }
                        currentY += 18f
                        canvas.drawLine(MARGIN_LEFT, currentY, PAGE_WIDTH - MARGIN_RIGHT, currentY, dividerPaint)
                    }

                    drawTableHeader()

                    // Data Rows
                    for (rowIndex in section.tableRows.indices) {
                        val row = section.tableRows[rowIndex]
                        val rowHeight = 16f
                        
                        if (currentY + rowHeight > PAGE_HEIGHT - MARGIN_BOTTOM - 20f) {
                            createNewPage()
                            drawTableHeader()
                        }

                        if (row.backgroundColor != null) {
                            bgPaint.color = row.backgroundColor
                            canvas.drawRect(MARGIN_LEFT, currentY, PAGE_WIDTH - MARGIN_RIGHT, currentY + rowHeight, bgPaint)
                        } else if (rowIndex % 2 == 1 && !row.isTotal) {
                            bgPaint.color = COLOR_ROW_ALT_BG
                            canvas.drawRect(MARGIN_LEFT, currentY, PAGE_WIDTH - MARGIN_RIGHT, currentY + rowHeight, bgPaint)
                        }

                        if (row.isTotal) {
                            canvas.drawLine(MARGIN_LEFT, currentY, PAGE_WIDTH - MARGIN_RIGHT, currentY, dividerPaint)
                        }

                        var colX = MARGIN_LEFT
                        for (c in row.cells.indices) {
                            if (c >= section.tableColumns.size) break
                            val col = section.tableColumns[c]
                            val w = colWidths[c]
                            val paint = Paint(if (row.isBold || row.isTotal) bodyBoldPaint else bodyRegularPaint).apply {
                                textAlign = col.align
                            }
                            val textX = when (col.align) {
                                Paint.Align.LEFT -> colX + 4f
                                Paint.Align.CENTER -> colX + (w / 2f)
                                Paint.Align.RIGHT -> colX + w - 4f
                            }
                            canvas.drawText(row.cells[c], textX, currentY + 11f, paint)
                            colX += w
                        }
                        currentY += rowHeight
                    }
                    canvas.drawLine(MARGIN_LEFT, currentY, PAGE_WIDTH - MARGIN_RIGHT, currentY, dividerPaint)
                    currentY += 8f
                }

                // Section Notes
                if (section.notes.isNotEmpty()) {
                    for (note in section.notes) {
                        ensureSpace(14f)
                        canvas.drawText("• $note", MARGIN_LEFT, currentY + 9f, subtitlePaint)
                        currentY += 12f
                    }
                    currentY += 4f
                }

                currentY += 6f
            }

            // Finish the final page
            drawFooter()
            pdfDoc.finishPage(currentPage)

            // Write out to disk
            FileOutputStream(outputFile).use { out ->
                pdfDoc.writeTo(out)
                out.flush()
            }

            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(IOException("Gagal menghasilkan file PDF: ${e.localizedMessage ?: e.message}", e))
        } finally {
            pdfDoc.close()
        }
    }
}
