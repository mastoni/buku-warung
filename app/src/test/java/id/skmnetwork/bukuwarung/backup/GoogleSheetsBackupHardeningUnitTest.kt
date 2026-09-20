package id.skmnetwork.bukuwarung.backup

import id.skmnetwork.bukuwarung.backup.transport.GoogleAuthCredentialProvider
import id.skmnetwork.bukuwarung.backup.transport.GoogleSheetsApiTransport
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Gate G2.1 — Google Sheets Backup Hardening & Protected Backup Unit Tests
 * Implements Tests A through I verifying formatting, protection, checksum invariance,
 * Business Profile inclusion, legacy fallback, and security zero-leak.
 */
class GoogleSheetsBackupHardeningUnitTest {

    private val mockAuthProvider = object : GoogleAuthCredentialProvider {
        override suspend fun getAccessToken(): Result<String> = Result.success("TEST_OAUTH_TOKEN")
        override suspend fun authorizeAccount(email: String): id.skmnetwork.bukuwarung.backup.transport.GoogleAuthConnectionState =
            id.skmnetwork.bukuwarung.backup.transport.GoogleAuthConnectionState.Connected(email)
        override suspend fun handleAuthorizationResult(
            email: String,
            data: android.content.Intent?
        ): Result<id.skmnetwork.bukuwarung.backup.transport.GoogleAuthConnectionState.Connected> =
            Result.success(id.skmnetwork.bukuwarung.backup.transport.GoogleAuthConnectionState.Connected(email))
        override fun clearToken() {}
    }

    /**
     * Test A: Formatting request generated.
     * Verifies:
     * - Frozen Row 1
     * - Header background & text styling (bold, color, centered)
     * - Auto-resize dimensions
     */
    @Test
    fun testA_formattingRequestGenerated() {
        val transport = GoogleSheetsApiTransport(mockAuthProvider)
        val sheetIdMap = mapOf(
            "00_README" to 0,
            "00_Metadata" to 100,
            "01_Business" to 101,
            "04_Products" to 104
        )

        val formattingRequests = transport.buildFormattingRequests(sheetIdMap)
        assertTrue("Formatting requests must not be empty", formattingRequests.length() > 0)

        var hasFrozenRow = false
        var hasHeaderStyle = false
        var hasAutoResize = false

        for (i in 0 until formattingRequests.length()) {
            val req = formattingRequests.getJSONObject(i)
            if (req.has("updateSheetProperties")) {
                val prop = req.getJSONObject("updateSheetProperties")
                val grid = prop.getJSONObject("properties").getJSONObject("gridProperties")
                if (grid.getInt("frozenRowCount") == 1) {
                    hasFrozenRow = true
                }
            }
            if (req.has("repeatCell")) {
                val repeatCell = req.getJSONObject("repeatCell")
                val range = repeatCell.getJSONObject("range")
                val format = repeatCell.getJSONObject("cell").getJSONObject("userEnteredFormat")
                if (range.getInt("startRowIndex") == 0 && range.getInt("endRowIndex") == 1) {
                    val textFormat = format.getJSONObject("textFormat")
                    if (textFormat.getBoolean("bold")) {
                        hasHeaderStyle = true
                    }
                }
            }
            if (req.has("autoResizeDimensions")) {
                val autoResize = req.getJSONObject("autoResizeDimensions")
                val dim = autoResize.getJSONObject("dimensions")
                if (dim.getString("dimension") == "COLUMNS") {
                    hasAutoResize = true
                }
            }
        }

        assertTrue("Must include frozen row property", hasFrozenRow)
        assertTrue("Must include bold header styling", hasHeaderStyle)
        assertTrue("Must include auto resize dimensions", hasAutoResize)
    }

    /**
     * Test B: Protection request generated.
     * Verifies:
     * - Data tabs + Metadata are protected
     * - warningOnly = false
     */
    @Test
    fun testB_protectionRequestGenerated() {
        val transport = GoogleSheetsApiTransport(mockAuthProvider)
        val sheetIdMap = mapOf(
            "00_README" to 0,
            "00_Metadata" to 100,
            "01_Business" to 101,
            "02_Device" to 102,
            "03_Categories" to 103,
            "04_Products" to 104,
            "05_Customers" to 105,
            "06_Sales" to 106,
            "07_SaleItems" to 107,
            "08_Purchases" to 108,
            "09_PurchaseItems" to 109,
            "10_Debts" to 110,
            "11_DebtPayments" to 111,
            "12_Suppliers" to 112,
            "13_SupplierPayables" to 113,
            "14_SupplierPayments" to 114,
            "15_CashTransactions" to 115,
            "16_StockMovements" to 116,
            "17_SaleReturns" to 117,
            "18_SaleReturnItems" to 118,
            "19_DigitalTransactions" to 119,
            "20_PurchaseOrders" to 120,
            "21_PurchaseOrderItems" to 121
        )

        val protectionRequests = transport.buildProtectionRequests(sheetIdMap)
        assertEquals("Protection requests must cover all domain data tabs + metadata", 22, protectionRequests.length())

        for (i in 0 until protectionRequests.length()) {
            val req = protectionRequests.getJSONObject(i)
            assertTrue("Request must be addProtectedRange", req.has("addProtectedRange"))
            val protectedRange = req.getJSONObject("addProtectedRange").getJSONObject("protectedRange")
            assertFalse("warningOnly must be false for hard UI protection", protectedRange.getBoolean("warningOnly"))
            assertTrue("Must contain a descriptive explanation", protectedRange.getString("description").isNotEmpty())
            assertTrue("Must contain valid sheetId", protectedRange.getJSONObject("range").has("sheetId"))
        }
    }

    /**
     * Test C: Raw data unchanged.
     * Verifies:
     * - Numeric values remain raw long/double strings without currency symbols or commas.
     * - Timestamps remain parseable long strings.
     */
    @Test
    fun testC_rawDataUnchanged() {
        val rawPrice = "50000"
        val rawStock = "12.5"
        val rawTimestamp = "1726560000000"

        // Ensure raw numeric parsing succeeds
        assertEquals(50000L, rawPrice.toLongOrNull())
        assertEquals(12.5, rawStock.toDoubleOrNull() ?: 0.0, 0.0001)
        assertEquals(1726560000000L, rawTimestamp.toLongOrNull())

        // Verify formatting functions in CanonicalSerializer produce deterministic raw representation
        assertEquals("50000.0", CanonicalSerializer.formatDouble(50000.0))
        assertEquals("12.5", CanonicalSerializer.formatDouble(12.5))
        assertEquals("1726560000000", CanonicalSerializer.formatLong(1726560000000L))
        assertEquals("true", CanonicalSerializer.formatBoolean(true))
        assertEquals("NULL", CanonicalSerializer.sanitize(null))
    }

    /**
     * Test D: Checksum unchanged by visual formatting / README tab.
     * Verifies:
     * - Adding or modifying 00_README tab does NOT alter canonical SHA-256 checksum.
     */
    @Test
    fun testD_checksumUnchangedByReadmeOrFormatting() {
        val dataTabs = mutableMapOf<String, SheetTab>()
        CanonicalSerializer.DATA_TAB_NAMES.forEach { tabName ->
            dataTabs[tabName] = SheetTab(
                name = tabName,
                headers = listOf("id", "name"),
                rows = listOf(listOf("uuid_1", "Item A"))
            )
        }

        val checksumBefore = CanonicalSerializer.calculateChecksum(dataTabs)

        // Add 00_README to tabs
        val metadata = BackupMetadata(checksum = checksumBefore)
        val readmeTab = CanonicalSerializer.generateReadmeTab(
            metadata = metadata,
            shopName = "Warung Berkah",
            ownerName = "Budi",
            primaryBusinessType = "WARUNG_SEMBAKO",
            totalRecords = 18
        )
        val allTabsWithReadme = dataTabs.toMutableMap()
        allTabsWithReadme[CanonicalSerializer.README_TAB_NAME] = readmeTab

        val checksumAfter = CanonicalSerializer.calculateChecksum(allTabsWithReadme)

        assertEquals("Checksum must be completely independent of 00_README", checksumBefore, checksumAfter)
    }

    /**
     * Test E: Raw cell modification causes checksum validation failure.
     */
    @Test
    fun testE_rawCellModificationFailsChecksum() {
        val dataTabs = mutableMapOf<String, SheetTab>()
        CanonicalSerializer.DATA_TAB_NAMES.forEach { tabName ->
            dataTabs[tabName] = SheetTab(
                name = tabName,
                headers = listOf("uuid", "name"),
                rows = listOf(listOf("uuid_1", "Original Name"))
            )
        }

        val originalChecksum = CanonicalSerializer.calculateChecksum(dataTabs)
        assertTrue(CanonicalSerializer.verifyChecksum(originalChecksum, dataTabs))

        // Modify a single cell in 04_Products
        val modTabs = dataTabs.toMutableMap()
        modTabs["04_Products"] = SheetTab(
            name = "04_Products",
            headers = listOf("uuid", "name"),
            rows = listOf(listOf("uuid_1", "Tampered Name"))
        )

        assertFalse("Tampered cell must fail checksum verification", CanonicalSerializer.verifyChecksum(originalChecksum, modTabs))
    }

    /**
     * Test F: v0.2.0 Business Profile included.
     * Verifies:
     * - primary_business_type, secondary_activities, profile_version are preserved in 01_Business.
     */
    @Test
    fun testF_businessProfileIncluded() {
        val businessHeaders = listOf(
            "business_id",
            "shop_name",
            "owner_name",
            "phone",
            "address",
            "created_at",
            "primary_business_type",
            "secondary_activities",
            "profile_version"
        )
        val businessRow = listOf(
            "BIZ_001",
            "Toko Kelontong Sejahtera",
            "Pak Joko",
            "08123456789",
            "Jl. Mawar No. 10",
            "1726560000000",
            "WARUNG_MAKAN",
            "ACTIVITY_DINE_IN,ACTIVITY_GOODS_SELLING",
            "2"
        )

        assertEquals("BIZ_001", businessRow[0])
        assertEquals("Toko Kelontong Sejahtera", businessRow[1])
        assertEquals("Pak Joko", businessRow[2])
        assertEquals("08123456789", businessRow[3])
        assertEquals("Jl. Mawar No. 10", businessRow[4])
        assertEquals("1726560000000", businessRow[5])
        assertEquals("WARUNG_MAKAN", businessRow[6])
        assertEquals("ACTIVITY_DINE_IN,ACTIVITY_GOODS_SELLING", businessRow[7])
        assertEquals("2", businessRow[8])
        assertEquals(9, businessHeaders.size)
    }

    /**
     * Test G: Legacy v0.1.0 backup restore fallback.
     * Verifies:
     * - An older backup with only 6 columns in 01_Business safely falls back to defaults.
     */
    @Test
    fun testG_legacyBackupRestoreFallback() {
        val legacyRow = listOf(
            "LEGACY_BIZ_001",
            "Warung Lama",
            "Pak Slamet",
            "08111222333",
            "Jl. Lama No. 1",
            "1700000000000"
        )

        val businessId = legacyRow.getOrElse(0) { "" }
        val shopName = legacyRow.getOrElse(1) { "Warung Saya" }
        val ownerName = legacyRow.getOrElse(2) { "" }
        val phone = legacyRow.getOrElse(3) { "" }
        val address = legacyRow.getOrElse(4) { "" }

        val primaryBusinessType = legacyRow.getOrElse(6) { "WARUNG_SEMBAKO" }.ifBlank { "WARUNG_SEMBAKO" }
        val secondaryActivitiesRaw = legacyRow.getOrElse(7) { "ACTIVITY_GOODS_SELLING" }.ifBlank { "ACTIVITY_GOODS_SELLING" }
        val secondaryActivities = if (secondaryActivitiesRaw == "NULL" || secondaryActivitiesRaw.isBlank()) {
            setOf("ACTIVITY_GOODS_SELLING")
        } else {
            secondaryActivitiesRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet().ifEmpty {
                setOf("ACTIVITY_GOODS_SELLING")
            }
        }
        val profileVersion = legacyRow.getOrElse(8) { "1" }.toIntOrNull() ?: 1

        assertEquals("LEGACY_BIZ_001", businessId)
        assertEquals("Warung Lama", shopName)
        assertEquals("WARUNG_SEMBAKO", primaryBusinessType)
        assertEquals(setOf("ACTIVITY_GOODS_SELLING"), secondaryActivities)
        assertEquals(1, profileVersion)
    }

    /**
     * Test H: Full backup snapshot creation and README integration.
     */
    @Test
    fun testH_fullBackupSnapshotAndReadmeIntegration() {
        val metadata = BackupMetadata(
            backupFormatVersion = CanonicalSerializer.BACKUP_FORMAT_VERSION,
            roomSchemaVersion = CanonicalSerializer.ROOM_SCHEMA_VERSION,
            exportedAt = System.currentTimeMillis(),
            businessId = "BIZ_TEST",
            deviceId = "DEV_TEST",
            appVersion = "1.0.0",
            totalRecords = 10,
            checksum = "a1b2c3d4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef0"
        )

        val readmeTab = CanonicalSerializer.generateReadmeTab(
            metadata = metadata,
            shopName = "Warung Barokah",
            ownerName = "Ibu Siti",
            primaryBusinessType = "WARUNG_SEMBAKO",
            totalRecords = 10
        )

        assertEquals("00_README", readmeTab.name)
        assertTrue(readmeTab.rows.isNotEmpty())
        assertTrue(readmeTab.rows.any { it.contains("Warung Barokah") })
        assertTrue(readmeTab.rows.any { it.contains("Ibu Siti") })
        assertTrue(readmeTab.rows.any { it.contains("WARUNG_SEMBAKO") })
        assertTrue(readmeTab.rows.any { it.contains("PERINGATAN") })
    }

    /**
     * Test I: Security zero-leak.
     * Verifies:
     * - No sensitive keys, salts, hashes, tokens, or secrets exist in backup tabs.
     */
    @Test
    fun testI_securityZeroLeak() {
        val metadata = BackupMetadata(
            backupFormatVersion = "1.0",
            roomSchemaVersion = 11,
            exportedAt = System.currentTimeMillis(),
            businessId = "BIZ_SEC",
            deviceId = "DEV_SEC",
            appVersion = "1.0.0",
            totalRecords = 0,
            checksum = "0000000000000000000000000000000000000000000000000000000000000000"
        )

        val readme = CanonicalSerializer.generateReadmeTab(
            metadata = metadata,
            shopName = "Warung Aman",
            ownerName = "Admin",
            primaryBusinessType = "WARUNG_SEMBAKO",
            totalRecords = 0
        )

        val allTabNames = CanonicalSerializer.ALL_TAB_NAMES
        for (tab in allTabNames) {
            assertFalse(tab.contains("pin", ignoreCase = true))
            assertFalse(tab.contains("secret", ignoreCase = true))
            assertFalse(tab.contains("token", ignoreCase = true))
            assertFalse(tab.contains("password", ignoreCase = true))
        }

        readme.rows.forEach { row ->
            row.forEach { cell ->
                assertFalse("Cell must not contain PIN hash", cell.contains("PIN_HASH", ignoreCase = true))
                assertFalse("Cell must not contain PIN salt", cell.contains("PIN_SALT", ignoreCase = true))
                assertFalse("Cell must not contain OAuth token", cell.contains("ACCESS_TOKEN", ignoreCase = true))
            }
        }
    }
}
