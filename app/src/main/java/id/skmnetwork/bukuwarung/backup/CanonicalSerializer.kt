package id.skmnetwork.bukuwarung.backup

import java.security.MessageDigest
import java.util.Locale

/**
 * Foundation Step 5 — Canonical Serializer
 * Provides deterministic tab ordering, row sorting, string formatting,
 * and SHA-256 cryptographic checksum calculation.
 */
object CanonicalSerializer {

    const val BACKUP_FORMAT_VERSION = "1.0"
    const val ROOM_SCHEMA_VERSION = 11

    const val README_TAB_NAME = "00_README"
    const val METADATA_TAB_NAME = "00_Metadata"

    val DATA_TAB_NAMES = listOf(
        "01_Business",
        "02_Device",
        "03_Categories",
        "04_Products",
        "05_Customers",
        "06_Sales",
        "07_SaleItems",
        "08_Purchases",
        "09_PurchaseItems",
        "10_Debts",
        "11_DebtPayments",
        "12_Suppliers",
        "13_SupplierPayables",
        "14_SupplierPayments",
        "15_CashTransactions",
        "16_StockMovements",
        "17_SaleReturns",
        "18_SaleReturnItems"
    )

    val ALL_TAB_NAMES = listOf(README_TAB_NAME, METADATA_TAB_NAME) + DATA_TAB_NAMES

    /**
     * Generates a user-friendly overview sheet tab (00_README) in Indonesian.
     * Note: 00_README is a human layer and is excluded from canonical SHA-256 checksum calculation.
     */
    fun generateReadmeTab(
        metadata: BackupMetadata,
        shopName: String,
        ownerName: String,
        primaryBusinessType: String,
        totalRecords: Int
    ): SheetTab {
        val dateFormat = java.text.SimpleDateFormat("dd MMMM yyyy HH:mm:ss", Locale.forLanguageTag("id-ID"))
        val formattedDate = try {
            dateFormat.format(java.util.Date(metadata.exportedAt))
        } catch (e: Exception) {
            metadata.exportedAt.toString()
        }

        val rows = listOf(
            listOf("BUKU WARUNG - SALINAN CADANGAN (BACKUP)", ""),
            listOf("PERINGATAN", "File spreadsheet ini dikelola otomatis oleh aplikasi Buku Warung. JANGAN mengedit, menghapus, atau mengubah struktur sel data secara manual agar proses pemulihan (restore) data tetap aman dan akurat."),
            listOf("---", "---"),
            listOf("PROFIL USAHA", ""),
            listOf("Nama Usaha", sanitize(shopName)),
            listOf("Nama Pemilik", sanitize(ownerName)),
            listOf("Tipe Usaha", sanitize(primaryBusinessType)),
            listOf("---", "---"),
            listOf("INFORMASI CADANGAN", ""),
            listOf("Waktu Cadangan", formattedDate),
            listOf("Versi Format", metadata.backupFormatVersion),
            listOf("Versi Skema DB", metadata.roomSchemaVersion.toString()),
            listOf("Versi Aplikasi", metadata.appVersion),
            listOf("Total Baris Data", totalRecords.toString()),
            listOf("Integritas Data", "SHA-256 Terverifikasi"),
            listOf("---", "---"),
            listOf("PANDUAN PEMULIHAN", "Buka Buku Warung -> Pengaturan -> Cadangan & Pemulihan -> Pulihkan Data.")
        )

        return SheetTab(
            name = README_TAB_NAME,
            headers = listOf("Informasi", "Keterangan"),
            rows = rows
        )
    }

    /**
     * Sanitizes string cell for tab-delimited matrix format.
     */
    fun sanitize(value: String?): String {
        if (value == null) return "NULL"
        return value
            .replace("\r\n", "\\n")
            .replace("\n", "\\n")
            .replace("\r", "\\n")
            .replace("\t", " ")
    }

    /**
     * Formats floating point values with standard US locale.
     */
    fun formatDouble(value: Double?): String {
        if (value == null) return "NULL"
        return String.format(Locale.US, "%.4f", value).trimEnd('0').let {
            if (it.endsWith(".")) it + "0" else it
        }
    }

    /**
     * Formats Long or returns "NULL"
     */
    fun formatLong(value: Long?): String {
        return value?.toString() ?: "NULL"
    }

    /**
     * Formats Boolean or returns "NULL"
     */
    fun formatBoolean(value: Boolean?): String {
        return value?.toString() ?: "NULL"
    }

    /**
     * Sorts tab rows deterministically based on canonical ordering rules.
     */
    fun sortTabRows(tabName: String, rows: List<List<String>>): List<List<String>> {
        return when (tabName) {
            "01_Business" -> rows.sortedWith(
                compareBy<List<String>> { it.getOrElse(5) { "0" }.toLongOrNull() ?: 0L }
                    .thenBy { it.getOrElse(0) { "" } }
            )
            "02_Device" -> rows.sortedBy { it.getOrElse(0) { "" } }
            "03_Categories" -> rows.sortedWith(
                compareBy<List<String>> { it.getOrElse(5) { "0" }.toLongOrNull() ?: 0L }
                    .thenBy { it.getOrElse(0) { "" } }
            )
            "04_Products" -> rows.sortedWith(
                compareBy<List<String>> { it.getOrElse(12) { "0" }.toLongOrNull() ?: 0L }
                    .thenBy { it.getOrElse(0) { "" } }
            )
            "05_Customers" -> rows.sortedWith(
                compareBy<List<String>> { it.getOrElse(7) { "0" }.toLongOrNull() ?: 0L }
                    .thenBy { it.getOrElse(0) { "" } }
            )
            "06_Sales" -> rows.sortedWith(
                compareBy<List<String>> { it.getOrElse(7) { "0" }.toLongOrNull() ?: 0L }
                    .thenBy { it.getOrElse(0) { "" } }
            )
            "07_SaleItems" -> rows.sortedWith(
                compareBy<List<String>> { it.getOrElse(2) { "" } } // sale_uuid
                    .thenBy { it.getOrElse(0) { "" } } // uuid
            )
            "08_Purchases" -> rows.sortedWith(
                compareBy<List<String>> { it.getOrElse(7) { "0" }.toLongOrNull() ?: 0L }
                    .thenBy { it.getOrElse(0) { "" } }
            )
            "09_PurchaseItems" -> rows.sortedWith(
                compareBy<List<String>> { it.getOrElse(2) { "" } } // purchase_uuid
                    .thenBy { it.getOrElse(0) { "" } } // uuid
            )
            "10_Debts" -> rows.sortedWith(
                compareBy<List<String>> { it.getOrElse(7) { "0" }.toLongOrNull() ?: 0L }
                    .thenBy { it.getOrElse(0) { "" } }
            )
            "11_DebtPayments" -> rows.sortedWith(
                compareBy<List<String>> { it.getOrElse(7) { "0" }.toLongOrNull() ?: 0L }
                    .thenBy { it.getOrElse(0) { "" } }
            )
            "12_Suppliers" -> rows.sortedWith(
                compareBy<List<String>> { it.getOrElse(7) { "0" }.toLongOrNull() ?: 0L }
                    .thenBy { it.getOrElse(0) { "" } }
            )
            "13_SupplierPayables" -> rows.sortedWith(
                compareBy<List<String>> { it.getOrElse(7) { "0" }.toLongOrNull() ?: 0L }
                    .thenBy { it.getOrElse(0) { "" } }
            )
            "14_SupplierPayments" -> rows.sortedWith(
                compareBy<List<String>> { it.getOrElse(7) { "0" }.toLongOrNull() ?: 0L }
                    .thenBy { it.getOrElse(0) { "" } }
            )
            "15_CashTransactions" -> rows.sortedWith(
                compareBy<List<String>> { it.getOrElse(7) { "0" }.toLongOrNull() ?: 0L }
                    .thenBy { it.getOrElse(0) { "" } }
            )
            "16_StockMovements" -> rows.sortedWith(
                compareBy<List<String>> { it.getOrElse(9) { "0" }.toLongOrNull() ?: 0L }
                    .thenBy { it.getOrElse(0) { "" } }
            )
            "17_SaleReturns" -> rows.sortedWith(
                compareBy<List<String>> { it.getOrElse(5) { "0" }.toLongOrNull() ?: 0L }
                    .thenBy { it.getOrElse(0) { "" } }
            )
            "18_SaleReturnItems" -> rows.sortedWith(
                compareBy<List<String>> { it.getOrElse(2) { "" } } // return_uuid
                    .thenBy { it.getOrElse(0) { "" } } // uuid
            )
            else -> rows
        }
    }

    /**
     * Builds the deterministic canonical payload across all 16 data tabs (01..16).
     */
    fun buildCanonicalPayload(tabs: Map<String, SheetTab>): String {
        val sb = StringBuilder()
        for (tabName in DATA_TAB_NAMES) {
            val tab = tabs[tabName]
            sb.append("[TAB:").append(tabName).append("]\n")
            if (tab != null) {
                val sortedRows = sortTabRows(tabName, tab.rows)
                for (row in sortedRows) {
                    sb.append(row.joinToString(separator = "\t")).append("\n")
                }
            }
        }
        return sb.toString()
    }

    /**
     * Computes SHA-256 checksum in hexadecimal lowercase.
     */
    fun calculateChecksum(tabs: Map<String, SheetTab>): String {
        val payload = buildCanonicalPayload(tabs)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Verifies checksum using constant-time comparison.
     */
    fun verifyChecksum(expectedChecksum: String, tabs: Map<String, SheetTab>): Boolean {
        val calculated = calculateChecksum(tabs)
        return MessageDigest.isEqual(
            expectedChecksum.trim().lowercase().toByteArray(Charsets.UTF_8),
            calculated.trim().lowercase().toByteArray(Charsets.UTF_8)
        )
    }
}
