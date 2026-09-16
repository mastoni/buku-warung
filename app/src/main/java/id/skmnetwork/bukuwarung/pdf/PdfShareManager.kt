package id.skmnetwork.bukuwarung.pdf

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Gate G.1.1 — Reusable FileProvider & Share / Open Manager for PDF Reports.
 */
object PdfShareManager {

    const val MIME_TYPE_PDF = "application/pdf"
    private const val REPORTS_FOLDER = "reports"

    /**
     * Obtains the dedicated reports directory inside app cache storage.
     * Guaranteed to exist and be accessible via FileProvider.
     */
    fun getReportsDirectory(context: Context): File {
        val dir = File(context.cacheDir, REPORTS_FOLDER)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Generates a safe, clean, alphanumeric filename without illegal OS characters.
     */
    fun generateSafeFileName(
        reportTitle: String,
        periodLabel: String,
        timestamp: Long = System.currentTimeMillis()
    ): String {
        val safeTitle = reportTitle.trim()
            .replace(Regex("[^a-zA-Z0-9\\-_]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .ifBlank { "Laporan" }

        val safePeriod = periodLabel.trim()
            .replace(Regex("[^a-zA-Z0-9\\-_]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .ifBlank { "Periode" }

        val dateSuffix = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(timestamp))
        return "${safeTitle}_${safePeriod}_${dateSuffix}.pdf"
    }

    /**
     * Resolves a content:// URI for the generated PDF file using AndroidX FileProvider.
     */
    fun getUriForReportFile(context: Context, file: File): Uri {
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }

    /**
     * Constructs an Intent to view the PDF using an installed PDF reader.
     */
    fun createViewPdfIntent(context: Context, file: File): Intent {
        val uri = getUriForReportFile(context, file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, MIME_TYPE_PDF)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Constructs an Intent to share the PDF via Android Share Sheet (WhatsApp, Email, Drive, etc.).
     */
    fun createSharePdfIntent(
        context: Context,
        file: File,
        chooserTitle: String = "Bagikan Laporan PDF"
    ): Intent {
        val uri = getUriForReportFile(context, file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = MIME_TYPE_PDF
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(shareIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
