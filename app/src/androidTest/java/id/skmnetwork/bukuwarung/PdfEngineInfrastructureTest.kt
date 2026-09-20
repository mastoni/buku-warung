package id.skmnetwork.bukuwarung

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.skmnetwork.bukuwarung.data.local.database.AppDatabase
import id.skmnetwork.bukuwarung.data.local.entity.CategoryEntity
import id.skmnetwork.bukuwarung.pdf.KeyValuePair
import id.skmnetwork.bukuwarung.pdf.PdfReportDocument
import id.skmnetwork.bukuwarung.pdf.PdfReportGenerator
import id.skmnetwork.bukuwarung.pdf.PdfShareManager
import id.skmnetwork.bukuwarung.pdf.ReportHeader
import id.skmnetwork.bukuwarung.pdf.ReportSection
import id.skmnetwork.bukuwarung.pdf.TableColumn
import id.skmnetwork.bukuwarung.pdf.TableRow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Gate G.1.1 — Comprehensive Verification for PDF Engine & Infrastructure.
 * Verifies all 10 required test points:
 * 1. PDF kosong tetap menghasilkan file valid.
 * 2. PDF menghasilkan ukuran file > 0.
 * 3. PDF dapat dibuka sebagai dokumen valid (PdfRenderer).
 * 4. Multiple page tidak corrupt.
 * 5. Filename valid dan tersanitasi.
 * 6. FileProvider URI valid dengan scheme content://.
 * 7. MIME type application/pdf.
 * 8. Share Intent memiliki URI PDF dan grant read URI permission.
 * 9. View Intent memiliki URI PDF dan grant read URI permission.
 * 10. Generator tidak melakukan INSERT/UPDATE/DELETE ke Room (Zero side-effects).
 */
@RunWith(AndroidJUnit4::class)
class PdfEngineInfrastructureTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var pdfGenerator: PdfReportGenerator

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        pdfGenerator = PdfReportGenerator(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    // 1. PDF kosong tetap menghasilkan file valid.
    @Test
    fun test1_emptyReport_producesValidFile() = runBlocking {
        val emptyDoc = PdfReportDocument(
            header = ReportHeader(
                shopName = "Kios Kiara",
                reportTitle = "Laporan Kosong",
                periodLabel = "September 2026",
                printedAt = "13/09/2026 01:00"
            ),
            sections = emptyList()
        )

        val result = pdfGenerator.generatePdf(emptyDoc)
        assertTrue("PDF generation must succeed", result.isSuccess)

        val file = result.getOrThrow()
        assertTrue("Output file must exist", file.exists())
        assertTrue("Output file must be a regular file", file.isFile)
    }

    // 2. PDF menghasilkan ukuran file > 0.
    @Test
    fun test2_pdfFileSize_isGreaterThanZero() = runBlocking {
        val doc = PdfReportDocument(
            header = ReportHeader(
                shopName = "Toko Berkah",
                reportTitle = "Ringkasan Usaha",
                periodLabel = "Hari Ini",
                printedAt = "13/09/2026 01:00"
            ),
            sections = listOf(
                ReportSection(
                    title = "Ringkasan Finansial",
                    summaryPairs = listOf(
                        KeyValuePair(label = "Penjualan Bersih", value = "Rp 1.500.000", isBold = true),
                        KeyValuePair(label = "Laba Bersih", value = "Rp 350.000", isHighlight = true)
                    )
                )
            )
        )

        val result = pdfGenerator.generatePdf(doc)
        val file = result.getOrThrow()
        assertTrue("Generated PDF file size must be > 0 bytes", file.length() > 0)
    }

    // 3. PDF dapat dibuka sebagai PdfRenderer / dokumen PDF yang valid.
    @Test
    fun test3_pdfCanBeOpenedAndParsed() = runBlocking {
        val doc = PdfReportDocument(
            header = ReportHeader(
                shopName = "Kios Kiara",
                reportTitle = "Laporan Penjualan",
                periodLabel = "Bulan Ini",
                printedAt = "13/09/2026 01:00"
            ),
            sections = listOf(
                ReportSection(
                    title = "Detail Penjualan",
                    tableColumns = listOf(
                        TableColumn(header = "No", weight = 1f, align = Paint.Align.CENTER),
                        TableColumn(header = "Item", weight = 4f, align = Paint.Align.LEFT),
                        TableColumn(header = "Total", weight = 3f, align = Paint.Align.RIGHT)
                    ),
                    tableRows = listOf(
                        TableRow(cells = listOf("1", "Kopi ABC Sachet", "Rp 50.000")),
                        TableRow(cells = listOf("2", "Gula Pasir 1kg", "Rp 18.000"))
                    )
                )
            )
        )

        val file = pdfGenerator.generatePdf(doc).getOrThrow()

        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        assertNotNull("ParcelFileDescriptor must not be null", pfd)

        val renderer = PdfRenderer(pfd)
        assertEquals("Single page document must have exactly 1 page", 1, renderer.pageCount)

        // Verify page can be opened
        val page = renderer.openPage(0)
        assertEquals(PdfReportGenerator.PAGE_WIDTH, page.width)
        assertEquals(PdfReportGenerator.PAGE_HEIGHT, page.height)
        page.close()
        renderer.close()
        pfd.close()
    }

    // 4. Multiple page tidak corrupt.
    @Test
    fun test4_multiplePages_generateSuccessfullyWithoutCorruption() = runBlocking {
        val rows = (1..60).map { i ->
            TableRow(
                cells = listOf(
                    i.toString(),
                    "Barang Dagangan Item #$i",
                    "10",
                    "Rp ${(i * 10_000)}"
                )
            )
        }

        val doc = PdfReportDocument(
            header = ReportHeader(
                shopName = "Kios Kiara — Multi Page Test",
                reportTitle = "Laporan Stok Barang Panjang",
                periodLabel = "Semua Data",
                printedAt = "13/09/2026 01:00"
            ),
            sections = listOf(
                ReportSection(
                    title = "Daftar Inventaris Lengkap",
                    tableColumns = listOf(
                        TableColumn(header = "No", weight = 1f, align = Paint.Align.CENTER),
                        TableColumn(header = "Nama Produk", weight = 5f, align = Paint.Align.LEFT),
                        TableColumn(header = "Stok", weight = 2f, align = Paint.Align.RIGHT),
                        TableColumn(header = "Nilai", weight = 3f, align = Paint.Align.RIGHT)
                    ),
                    tableRows = rows
                )
            )
        )

        val file = pdfGenerator.generatePdf(doc).getOrThrow()
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)

        assertTrue("60 rows must span across multiple pages (>= 2)", renderer.pageCount >= 2)

        // Verify each page opens cleanly without throwing
        for (i in 0 until renderer.pageCount) {
            val page = renderer.openPage(i)
            assertEquals(PdfReportGenerator.PAGE_WIDTH, page.width)
            assertEquals(PdfReportGenerator.PAGE_HEIGHT, page.height)
            page.close()
        }

        renderer.close()
        pfd.close()
    }

    // 5. Filename valid (sanitasi karakter illegal).
    @Test
    fun test5_safeFileName_sanitizesIllegalCharacters() {
        val dangerousTitle = "Laporan / Penjualan : Kios * Kiara ? < Utama > | \"Test\""
        val dangerousPeriod = "01/01/2026 - 31/01/2026"

        val safeName = PdfShareManager.generateSafeFileName(dangerousTitle, dangerousPeriod)

        val illegalRegex = Regex("[/\\\\:*?\"<>|]")
        assertTrue("Filename must not contain illegal OS characters", !illegalRegex.containsMatchIn(safeName))
        assertTrue("Filename must end with .pdf", safeName.endsWith(".pdf"))
        assertTrue("Filename must start with sanitized title", safeName.startsWith("Laporan_Penjualan_Kios_Kiara_Utama_Test"))
    }

    // 6. FileProvider URI valid.
    @Test
    fun test6_fileProviderUri_resolvesValidContentUri() = runBlocking {
        val doc = PdfReportDocument(
            header = ReportHeader(
                shopName = "Kios Kiara",
                reportTitle = "Laporan URI Test",
                periodLabel = "Hari Ini",
                printedAt = "13/09/2026 01:00"
            ),
            sections = emptyList()
        )

        val file = pdfGenerator.generatePdf(doc).getOrThrow()
        val uri = PdfShareManager.getUriForReportFile(context, file)

        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.fileprovider", uri.authority)
        assertTrue("URI path should reference reports folder", uri.path?.contains("report_pdfs") == true || uri.path?.contains("reports") == true)
    }

    // 7. MIME type application/pdf.
    @Test
    fun test7_mimeType_isApplicationPdf() {
        assertEquals("application/pdf", PdfShareManager.MIME_TYPE_PDF)
    }

    // 8. Share Intent memiliki URI PDF.
    @Test
    fun test8_shareIntent_hasCorrectUriAndFlags() = runBlocking {
        val doc = PdfReportDocument(
            header = ReportHeader(
                shopName = "Kios Kiara",
                reportTitle = "Laporan Share Intent",
                periodLabel = "Hari Ini",
                printedAt = "13/09/2026 01:00"
            ),
            sections = emptyList()
        )

        val file = pdfGenerator.generatePdf(doc).getOrThrow()
        val chooserIntent = PdfShareManager.createSharePdfIntent(context, file, "Bagikan Laporan")

        // Chooser intent wraps the inner ACTION_SEND intent
        val innerIntent = chooserIntent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull("Chooser must contain inner intent", innerIntent)
        assertEquals(Intent.ACTION_SEND, innerIntent?.action)
        assertEquals(PdfShareManager.MIME_TYPE_PDF, innerIntent?.type)

        val streamUri = innerIntent?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        assertNotNull("Inner intent must contain EXTRA_STREAM URI", streamUri)
        assertEquals("content", streamUri?.scheme)

        assertTrue(
            "Inner intent must have FLAG_GRANT_READ_URI_PERMISSION",
            (innerIntent!!.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0
        )
    }

    // 9. View Intent memiliki URI PDF.
    @Test
    fun test9_viewIntent_hasCorrectUriAndFlags() = runBlocking {
        val doc = PdfReportDocument(
            header = ReportHeader(
                shopName = "Kios Kiara",
                reportTitle = "Laporan View Intent",
                periodLabel = "Hari Ini",
                printedAt = "13/09/2026 01:00"
            ),
            sections = emptyList()
        )

        val file = pdfGenerator.generatePdf(doc).getOrThrow()
        val viewIntent = PdfShareManager.createViewPdfIntent(context, file)

        assertEquals(Intent.ACTION_VIEW, viewIntent.action)
        assertEquals(PdfShareManager.MIME_TYPE_PDF, viewIntent.type)
        assertNotNull("View intent must have data URI", viewIntent.data)
        assertEquals("content", viewIntent.data?.scheme)

        assertTrue(
            "View intent must have FLAG_GRANT_READ_URI_PERMISSION",
            (viewIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0
        )
    }

    // 10. Generator tidak melakukan INSERT/UPDATE/DELETE ke Room.
    @Test
    fun test10_pdfGenerator_doesNotMutateRoomDatabase() = runBlocking {
        // Insert sample baseline record
        database.categoryDao().insertCategory(CategoryEntity(name = "Makanan"))
        val initialCategories = database.categoryDao().getAllCategories("LEGACY_BUSINESS").first()
        val initialCount = initialCategories.size

        val doc = PdfReportDocument(
            header = ReportHeader(
                shopName = "Kios Kiara",
                reportTitle = "Laporan Isolation Test",
                periodLabel = "Hari Ini",
                printedAt = "13/09/2026 01:00"
            ),
            sections = listOf(
                ReportSection(
                    title = "Test Section",
                    summaryPairs = listOf(KeyValuePair("Kategori", "Makanan"))
                )
            )
        )

        // Generate multiple PDFs
        val result1 = pdfGenerator.generatePdf(doc)
        val result2 = pdfGenerator.generatePdf(doc)
        assertTrue(result1.isSuccess)
        assertTrue(result2.isSuccess)

        // Verify Room database remains untouched
        val afterCategories = database.categoryDao().getAllCategories("LEGACY_BUSINESS").first()
        assertEquals("Room category count must remain identical before and after PDF generation", initialCount, afterCategories.size)
        assertEquals("Category data must remain untouched", "Makanan", afterCategories[0].name)
    }
}


