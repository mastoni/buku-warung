package id.skmnetwork.bukuwarung.backup

import java.security.MessageDigest
import java.util.Locale

/**
 * Foundation Step 5 — Canonical Serializer
 * Provides deterministic tab ordering, row sorting, string formatting,
 * and SHA-256 cryptographic checksum calculation.
 */
object CanonicalSerializer {

    const val BACKUP_FORMAT_VERSION = "1.2"
    const val ROOM_SCHEMA_VERSION = 16

    const val README_TAB_NAME = "00_README"
    const val METADATA_TAB_NAME = "00_Metadata"

    /** Gate H.4.1 - the literal written into a cell to represent SQL NULL. */
    const val NULL_SENTINEL = "NULL"

    /**
     * Gate H.4.1 - checksum algorithms.
     *
     * [RAW_V1] is the historical behaviour: the checksum was taken over the exact strings written
     * into the sheet. It is retained byte-for-byte so every archive written before H.4.1 still
     * validates.
     *
     * [CANONICAL_V2] is used for new backups. It hashes a *typed* token per cell instead of the
     * raw string, so the checksum no longer depends on how Google Sheets chooses to type or echo a
     * cell. A value written as "10.0" and read back as 10, or "1" and read back as true, now
     * produce the same token - previously that difference was enough to fail a restore of the
     * user's own, untouched backup with ChecksumMismatchException.
     */
    enum class ChecksumAlgorithm(val wireName: String) {
        RAW_V1("RAW_V1"),
        CANONICAL_V2("CANONICAL_V2");

        companion object {
            fun fromWireName(value: String?): ChecksumAlgorithm =
                entries.firstOrNull { it.wireName == value } ?: RAW_V1
        }
    }

    /** The algorithm new backups declare. */
    val CURRENT_CHECKSUM_ALGORITHM: ChecksumAlgorithm = ChecksumAlgorithm.CANONICAL_V2

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
        "18_SaleReturnItems",
        "19_DigitalTransactions",
        "20_PurchaseOrders",
        "21_PurchaseOrderItems"
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
     * Gate H.4 - tolerant boolean parsing for a spreadsheet cell.
     *
     * Booleans have been written into `18_SaleReturnItems.taxable` as the raw ints "1"/"0"
     * while every other tab writes "true"/"false". Restoring that cell with
     * `String.toBooleanStrictOrNull()` returned null for both "1" and "0", so every restored
     * return line silently fell back to the default and was marked taxable.
     *
     * Accepts both wire forms (and is case-insensitive) so backups written by either version
     * restore correctly. Returns [default] for null, the SQL NULL sentinel, or anything
     * unrecognised rather than guessing.
     */
    fun parseBoolean(value: String?, default: Boolean = false): Boolean {
        return when (value?.trim()?.lowercase()) {
            "true", "1" -> true
            "false", "0" -> false
            else -> default
        }
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
            "19_DigitalTransactions" -> rows.sortedWith(
                compareBy<List<String>> { it.getOrElse(7) { "0" }.toLongOrNull() ?: 0L }
                    .thenBy { it.getOrElse(0) { "" } }
            )
            "20_PurchaseOrders" -> rows.sortedWith(
                compareBy<List<String>> { it.getOrElse(7) { "0" }.toLongOrNull() ?: 0L }
                    .thenBy { it.getOrElse(0) { "" } }
            )
            "21_PurchaseOrderItems" -> rows.sortedWith(
                compareBy<List<String>> { it.getOrElse(2) { "" } } // po_uuid
                    .thenBy { it.getOrElse(0) { "" } } // uuid
            )
            else -> rows
        }
    }

    /**
     * Builds the deterministic canonical payload across all data tabs (01..21).
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
     * Gate H.4.1 - canonicalises one cell to a typed token.
     *
     * The point is to make the checksum independent of Google Sheets' cell typing:
     *   - a numeric string becomes a Long or a normalised BigDecimal token, so "10.0", "10" and
     *     10 all collapse to the same value;
     *   - "true"/"false" become an explicit boolean token, so "1"/"0" and true/false are
     *     distinguishable but stable;
     *   - the NULL sentinel becomes its own token, so a literal "NULL" string written by a
     *     merchant is not silently equal to SQL NULL;
     *   - everything else - uuid, ISO or epoch timestamp, product name, address - is an escaped
     *     string token.
     *
     * Escape the token separator and the escape character itself so a crafted cell value can
     * never forge the boundary between two cells or two rows.
     */
    fun canonicalToken(value: String): String {
        val trimmed = value.trim().removePrefix("+")
        // One numeric token for every number, whatever its scale.
        //
        // It is tempting to emit "L10" for integers and "D10.0" for decimals, but that is exactly
        // what breaks under Google Sheets: the exporter writes "10.0" and the API may hand back
        // 10, and the two must canonicalise identically or a restore of the user's own untouched
        // backup fails. stripTrailingZeros + toPlainString makes 10, 10.0, 1E1 and 0.10E+2 all
        // collapse to the same token.
        //
        // BigDecimal throws on anything non-numeric, so the parse must stay guarded by a
        // successful runCatching - a product name such as "Beras" falls through to the string token.
        runCatching { java.math.BigDecimal(trimmed).stripTrailingZeros().toPlainString() }
            .getOrNull()
            ?.let { return "N$it" }
        return when (value.trim().lowercase()) {
            "true" -> "Btrue"
            "false" -> "Bfalse"
            else -> "S" + escapeToken(value)
        }
    }

    private fun escapeToken(value: String): String {
        val sb = StringBuilder(value.length + 8)
        for (ch in value) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '\t' -> sb.append("\\t")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * Builds the [ChecksumAlgorithm.CANONICAL_V2] payload: typed tokens, tab and row boundaries
     * made explicit so no cell content can imitate a structural delimiter.
     */
    fun buildCanonicalPayloadV2(tabs: Map<String, SheetTab>): String {
        val sb = StringBuilder()
        for (tabName in DATA_TAB_NAMES) {
            sb.append("[TAB:").append(tabName).append("]\n")
            val tab = tabs[tabName] ?: continue
            for (row in sortTabRows(tabName, tab.rows)) {
                for (cell in row) {
                    sb.append(canonicalToken(cell)).append('\u001F')
                }
                sb.append('\u001E')
            }
        }
        return sb.toString()
    }

    /**
     * Computes SHA-256 checksum in hexadecimal lowercase.
     */
    fun calculateChecksum(
        tabs: Map<String, SheetTab>,
        algorithm: ChecksumAlgorithm = CURRENT_CHECKSUM_ALGORITHM
    ): String {
        val payload = when (algorithm) {
            ChecksumAlgorithm.RAW_V1 -> buildCanonicalPayload(tabs)
            ChecksumAlgorithm.CANONICAL_V2 -> buildCanonicalPayloadV2(tabs)
        }
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Verifies checksum using constant-time comparison.
     */
    fun verifyChecksum(
        expectedChecksum: String,
        tabs: Map<String, SheetTab>,
        algorithm: ChecksumAlgorithm = CURRENT_CHECKSUM_ALGORITHM
    ): Boolean {
        val calculated = calculateChecksum(tabs, algorithm)
        return MessageDigest.isEqual(
            expectedChecksum.trim().lowercase().toByteArray(Charsets.UTF_8),
            calculated.trim().lowercase().toByteArray(Charsets.UTF_8)
        )
    }
}
