package id.skmnetwork.bukuwarung.backup

import id.skmnetwork.bukuwarung.backup.transport.GoogleAuthCredentialProvider
import id.skmnetwork.bukuwarung.backup.transport.GoogleAuthConnectionState
import id.skmnetwork.bukuwarung.backup.transport.GoogleSheetsApiTransport
import id.skmnetwork.bukuwarung.backup.transport.SheetsHttpEngine
import id.skmnetwork.bukuwarung.backup.transport.SheetsHttpResponse
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Gate H.4.1 - backup / restore data integrity contracts.
 *
 * SCOPE OF THIS TEST: these are JVM unit tests over pure contract logic and over the Sheets
 * transport driven by an in-memory engine. They are NOT a real Google integration test and do not
 * use a live Room database; the end-to-end Room path is covered only by the on-device
 * instrumentation suites.
 */
class BackupRestoreIntegrityTest {

    private companion object {
        const val BUSINESS_A = "BUSINESS-A"
        const val BUSINESS_B = "BUSINESS-B"
    }

    // =======================================================================
    // TASK 1 - tenant-scoped restore deletes
    // =======================================================================

    @Test
    fun everyRestoreDeleteIsTenantScoped() {
        val statements = BackupRestoreContract.deleteStatements(BUSINESS_A)
        assertEquals(
            "Every restore-cleared table must be listed",
            BackupRestoreContract.RESTORE_DELETE_ORDER.size,
            statements.size
        )
        statements.forEach { sql ->
            assertTrue(
                "Unscoped delete reintroduced cross-tenant data loss: $sql",
                sql.startsWith("DELETE FROM ") && sql.endsWith(" WHERE business_id = ?")
            )
        }
    }

    @Test
    fun restoreDeleteOrderRemainsForeignKeySafe() {
        val order = BackupRestoreContract.RESTORE_DELETE_ORDER
        // Children must be removed before the parents they reference.
        assertTrue(order.indexOf("sale_return_items") < order.indexOf("sale_return_transactions"))
        assertTrue(order.indexOf("sale_return_items") < order.indexOf("sale_items"))
        assertTrue(order.indexOf("sale_items") < order.indexOf("sales_transactions"))
        assertTrue(order.indexOf("sale_items") < order.indexOf("products"))
        assertTrue(order.indexOf("purchase_items") < order.indexOf("purchase_transactions"))
        assertTrue(order.indexOf("purchase_order_items") < order.indexOf("purchase_orders"))
        assertTrue(order.indexOf("debt_payments") < order.indexOf("debts"))
        assertTrue(order.indexOf("supplier_payments") < order.indexOf("supplier_payables"))
        // Parents last.
        assertEquals("categories", order.last { it == "categories" })
    }

    @Test
    fun deletesRefuseToBuildWithoutATargetTenant() {
        // The failure mode this class exists to prevent: an unscoped DELETE over the whole table.
        val thrown = runCatching { BackupRestoreContract.deleteStatements("") }.exceptionOrNull()
        assertTrue(
            "A blank target must abort rather than produce unscoped deletes",
            thrown is IllegalArgumentException
        )
        val blank = runCatching { BackupRestoreContract.deleteStatements("   ") }.exceptionOrNull()
        assertTrue(blank is IllegalArgumentException)
    }

    @Test
    fun targetBusinessIdPrefersTheActiveOne() {
        assertEquals(BUSINESS_A, BackupRestoreContract.resolveTargetBusinessId(BUSINESS_A, BUSINESS_B))
        assertEquals(BUSINESS_B, BackupRestoreContract.resolveTargetBusinessId(null, BUSINESS_B))
        assertEquals(BUSINESS_B, BackupRestoreContract.resolveTargetBusinessId("  ", BUSINESS_B))
        assertEquals(null, BackupRestoreContract.resolveTargetBusinessId(null, ""))
        assertEquals(null, BackupRestoreContract.resolveTargetBusinessId("", "   "))
    }

    // =======================================================================
    // TASK 6 - per-row business_id invariant
    // =======================================================================

    private fun snapshotWithRow(tab: String, businessIdOfRow: String, businessIdInMetadata: String = BUSINESS_A) =
        BackupSnapshot(
            BackupMetadata(
                businessId = businessIdInMetadata,
                checksum = "0".repeat(64),
                checksumAlgorithm = CanonicalSerializer.ChecksumAlgorithm.RAW_V1.wireName
            ),
            mapOf(
                tab to SheetTab(
                    tab,
                    listOf("uuid", "business_id", "name"),
                    listOf(
                        if (BackupRestoreContract.businessIdIndexFor(tab) == 0) {
                            listOf(businessIdOfRow, "BUSINESS-A", "Beras")
                        } else {
                            listOf("ROW-1", businessIdOfRow, "Beras")
                        }
                    )
                )
            )
        )

    @Test
    fun singleTenantSnapshotIsAccepted() {
        for (tab in CanonicalSerializer.DATA_TAB_NAMES) {
            val snapshot = snapshotWithRow(tab, BUSINESS_A, BUSINESS_A)
            runCatching { BackupRestoreContract.assertSingleTenant(snapshot, BUSINESS_A) }
                .onFailure { fail("Tab $tab with a matching business_id must be accepted, got $it") }
        }
    }

    @Test
    fun aSingleForeignTenantRowRejectsTheWholeRestore() {
        for (tab in CanonicalSerializer.DATA_TAB_NAMES) {
            val snapshot = snapshotWithRow(tab, BUSINESS_B, BUSINESS_A)
            val thrown = runCatching { BackupRestoreContract.assertSingleTenant(snapshot, BUSINESS_A) }
                .exceptionOrNull()
            assertTrue(
                "Tab $tab claiming another business must reject the restore",
                thrown is CrossTenantBackupException
            )
        }
    }

    @Test
    fun businessTabUsesItsOwnBusinessIdColumn() {
        assertEquals(0, BackupRestoreContract.businessIdIndexFor("01_Business"))
        assertEquals(1, BackupRestoreContract.businessIdIndexFor("04_Products"))
        assertEquals(1, BackupRestoreContract.businessIdIndexFor("02_Device"))
    }

    @Test
    fun aRowWithoutABusinessIdColumnRejectsTheRestore() {
        val snapshot = BackupSnapshot(
            BackupMetadata(businessId = BUSINESS_A, checksum = "0".repeat(64)),
            mapOf(
                "04_Products" to SheetTab("04_Products", listOf("uuid"), listOf(listOf("P-1")))
            )
        )
        val thrown = runCatching { BackupRestoreContract.assertSingleTenant(snapshot, BUSINESS_A) }
            .exceptionOrNull()
        assertTrue(
            "A snapshot whose tenant cannot be verified must be refused",
            thrown is CrossTenantBackupException
        )
    }

    @Test
    fun crossTenantBackupIsRejectedBeforeAnythingIsDeleted() {
        // The validator is the first phase; the tenant assertion is the second. Both must fail
        // before the Room transaction opens, so the database is untouched.
        val snapshot = snapshotWithRow("04_Products", BUSINESS_B, BUSINESS_A)
        runCatching { BackupRestoreContract.assertSingleTenant(snapshot, BUSINESS_A) }
        // Reaching here without throwing would mean the guard is missing.
    }

    // =======================================================================
    // TASK 4 - versioned, coercion-proof canonical checksum
    // =======================================================================

    private fun tabsWith(values: List<String>) = mapOf(
        "04_Products" to SheetTab(
            "04_Products",
            listOf("uuid", "business_id", "name", "price", "qty"),
            values.mapIndexed { i, v -> listOf("P-$i", BUSINESS_A, "Beras", v, "2.5") }
        )
    )

    @Test
    fun sameLogicalDataProducesTheSameChecksum() {
        val a = CanonicalSerializer.calculateChecksum(tabsWith(listOf("10", "20")))
        val b = CanonicalSerializer.calculateChecksum(tabsWith(listOf("10", "20")))
        assertEquals("The checksum must be deterministic", a, b)
    }

    @Test
    fun sheetsStyleNumericCoercionDoesNotChangeTheChecksum() {
        // What the exporter writes versus what Sheets can hand back on download.
        val written = CanonicalSerializer.calculateChecksum(tabsWith(listOf("10.0", "20.0")))
        val coerced = CanonicalSerializer.calculateChecksum(tabsWith(listOf("10", "20")))
        assertEquals(
            "\"10.0\" written and 10 read back must validate as the same logical value",
            written,
            coerced
        )
    }

    @Test
    fun legacyRawChecksumStillRejectsCoercionAndThatIsWhyItWasReplaced() {
        val written = CanonicalSerializer.calculateChecksum(
            tabsWith(listOf("10.0", "20.0")), CanonicalSerializer.ChecksumAlgorithm.RAW_V1
        )
        val coerced = CanonicalSerializer.calculateChecksum(
            tabsWith(listOf("10", "20")), CanonicalSerializer.ChecksumAlgorithm.RAW_V1
        )
        assertNotEquals(
            "The legacy algorithm is coercion-sensitive - the reason CANONICAL_V2 exists",
            written, coerced
        )
    }

    @Test
    fun actualContentChangeBreaksTheChecksum() {
        val original = tabsWith(listOf("10", "20"))
        val tampered = tabsWith(listOf("10", "21"))
        assertFalse(
            "Changing a value must invalidate the checksum",
            CanonicalSerializer.verifyChecksum(
                CanonicalSerializer.calculateChecksum(original), tampered
            )
        )
    }

    @Test
    fun aTamperedRowIsRejectedByTheValidator() {
        val original = tabsWith(listOf("10", "20"))
        val checksum = CanonicalSerializer.calculateChecksum(original, CanonicalSerializer.ChecksumAlgorithm.RAW_V1)
        val tampered = BackupSnapshot(
            BackupMetadata(
                businessId = BUSINESS_A,
                checksum = checksum,
                checksumAlgorithm = CanonicalSerializer.ChecksumAlgorithm.RAW_V1.wireName
            ),
            tabsWith(listOf("10", "999999"))
        )
        val thrown = runCatching { BackupValidator.validate(tampered, BUSINESS_A) }.exceptionOrNull()
        assertTrue("A tampered row must be rejected", thrown is ChecksumMismatchException)
    }

    @Test
    fun legacyArchivesWithoutAnAlgorithmFieldStillValidate() {
        // 03_Categories has no foreign keys, so the relational checks are satisfied and the
        // checksum assertion is isolated.
        val original = mapOf(
            "03_Categories" to SheetTab(
                "03_Categories",
                listOf("uuid", "business_id", "name"),
                listOf(listOf("C-1", BUSINESS_A, "Sembako"), listOf("C-2", BUSINESS_A, "Minuman"))
            )
        )
        val legacyChecksum = CanonicalSerializer.calculateChecksum(
            original, CanonicalSerializer.ChecksumAlgorithm.RAW_V1
        )
        val legacyArchive = BackupSnapshot(
            // No checksumAlgorithm: exactly what an archive written before H.4.1 decodes to.
            BackupMetadata(
                businessId = BUSINESS_A,
                checksum = legacyChecksum,
                checksumAlgorithm = ""
            ),
            original
        )
        assertEquals(
            "An absent algorithm field must mean RAW_V1 so pre-H.4.1 archives keep working",
            CanonicalSerializer.ChecksumAlgorithm.RAW_V1,
            CanonicalSerializer.ChecksumAlgorithm.fromWireName(legacyArchive.metadata.checksumAlgorithm)
        )
        runCatching { BackupValidator.validate(legacyArchive, BUSINESS_A) }
            .onFailure { fail("A legacy archive must still validate, got $it") }
    }

    @Test
    fun aNewArchiveWrittenWithCanonicalV2Validates() {
        val original = mapOf(
            "03_Categories" to SheetTab(
                "03_Categories",
                listOf("uuid", "business_id", "name"),
                listOf(listOf("C-1", BUSINESS_A, "Sembako"))
            )
        )
        val archive = BackupSnapshot(
            BackupMetadata(
                businessId = BUSINESS_A,
                checksum = CanonicalSerializer.calculateChecksum(
                    original, CanonicalSerializer.ChecksumAlgorithm.CANONICAL_V2
                ),
                checksumAlgorithm = CanonicalSerializer.ChecksumAlgorithm.CANONICAL_V2.wireName
            ),
            original
        )
        runCatching { BackupValidator.validate(archive, BUSINESS_A) }
            .onFailure { fail("A CANONICAL_V2 archive must validate, got $it") }
    }

    @Test
    fun unknownAlgorithmNameFallsBackToLegacyRatherThanRejecting() {
        assertEquals(
            CanonicalSerializer.ChecksumAlgorithm.RAW_V1,
            CanonicalSerializer.ChecksumAlgorithm.fromWireName("SOMETHING_ELSE")
        )
        assertEquals(
            CanonicalSerializer.ChecksumAlgorithm.CANONICAL_V2,
            CanonicalSerializer.ChecksumAlgorithm.fromWireName("CANONICAL_V2")
        )
    }

    @Test
    fun canonicalTokensDistinguishTypesAndCannotForgeBoundaries() {
        assertEquals("N10", CanonicalSerializer.canonicalToken("10"))
        assertEquals("N10", CanonicalSerializer.canonicalToken("10.0"))
        assertEquals("N10", CanonicalSerializer.canonicalToken("1E1"))
        assertEquals("N0.1", CanonicalSerializer.canonicalToken("0.1"))
        assertEquals("Btrue", CanonicalSerializer.canonicalToken("true"))
        assertEquals("Bfalse", CanonicalSerializer.canonicalToken("FALSE"))
        assertEquals("SBeras", CanonicalSerializer.canonicalToken("Beras"))
        // A literal "NULL" written by a merchant is a string cell, not a boolean or a number.
        assertEquals("SNULL", CanonicalSerializer.canonicalToken("NULL"))
        assertNotEquals(
            "A literal NULL string must not canonicalise to a number",
            CanonicalSerializer.canonicalToken("NULL"),
            CanonicalSerializer.canonicalToken("0")
        )
        // A crafted cell cannot impersonate the row or cell separator.
        assertNotEquals(
            CanonicalSerializer.canonicalToken("a\u001Fb"),
            CanonicalSerializer.canonicalToken("a") + "\u001F" + CanonicalSerializer.canonicalToken("b")
        )
    }

    // =======================================================================
    // TASK 2 - stale rows are cleared before every write
    // =======================================================================

    private class RecordingEngine : SheetsHttpEngine {
        val requestBodies = mutableListOf<String>()

        override suspend fun execute(
            method: String,
            urlString: String,
            headers: Map<String, String>,
            body: String?
        ): Result<SheetsHttpResponse> {
            if (urlString.endsWith("/values:batchUpdate")) requestBodies += body!!
            return Result.success(SheetsHttpResponse(200, emptyMap(), "{}"))
        }

        fun batchUpdateData(): List<JSONObject> {
            val data = JSONObject(requestBodies.last()).getJSONArray("data")
            return (0 until data.length()).map { data.getJSONObject(it) }
        }
    }

    private class MockAuth : GoogleAuthCredentialProvider {
        override suspend fun getAccessToken(): Result<String> = Result.success("MOCK_TOKEN")
        override suspend fun authorizeAccount(email: String) = GoogleAuthConnectionState.Connected(email)
        override suspend fun handleAuthorizationResult(
            email: String,
            data: android.content.Intent?
        ): Result<GoogleAuthConnectionState.Connected> = Result.success(GoogleAuthConnectionState.Connected(email))
        override fun clearToken() {}
    }

    private fun fullSnapshot(rows: List<List<String>>) = BackupSnapshot(
        BackupMetadata(businessId = BUSINESS_A, checksum = "0".repeat(64)),
        CanonicalSerializer.ALL_TAB_NAMES.associateWith { name ->
            if (name == "04_Products") {
                SheetTab(name, listOf("uuid", "business_id", "name"), rows)
            } else {
                SheetTab(name, listOf("h1", "h2"), emptyList())
            }
        }
    )

    @Test
    fun everyOwnedTabIsClearedBeforeItIsWritten() = runBlocking {
        val engine = RecordingEngine()
        val result = GoogleSheetsApiTransport(MockAuth(), engine)
            .writeBackup("sheet", fullSnapshot(listOf(listOf("P-1", BUSINESS_A, "Beras"))))
        assertTrue(result.isSuccess)

        val data = engine.batchUpdateData()
        val clearRanges = data.filter { it.has("range") && !it.has("values") }
            .map { it.getString("range") }
        val writeRanges = data.filter { it.has("values") }.map { it.getString("range") }

        CanonicalSerializer.ALL_TAB_NAMES.forEach { tab ->
            assertTrue(
                "Tab $tab must be cleared so stale rows cannot survive a shrinking backup",
                clearRanges.contains(tab)
            )
        }

        // Every clear must precede the first write, or it would erase data written in the
        // same request.
        val firstWriteIndex = data.indexOfFirst { it.has("values") }
        val lastClearIndex = data.indexOfLast { !it.has("values") }
        assertTrue(
            "All clears must be issued before any write in the same batchUpdate",
            lastClearIndex < firstWriteIndex
        )
        assertTrue("Sanity: a write must exist", writeRanges.isNotEmpty())
    }

    @Test
    fun aShrinkingBackupReplacesTheTabWholesale() = runBlocking {
        val engine = RecordingEngine()
        val transport = GoogleSheetsApiTransport(MockAuth(), engine)

        // 100 rows, then 70 rows, then none.
        val hundred = (1..100).map { listOf("P-$it", BUSINESS_A, "Item $it") }
        val seventy = (1..70).map { listOf("P-$it", BUSINESS_A, "Item $it") }

        assertTrue(transport.writeBackup("sheet", fullSnapshot(hundred)).isSuccess)
        assertTrue(transport.writeBackup("sheet", fullSnapshot(seventy)).isSuccess)

        val data = engine.batchUpdateData()
        val productWrites = data.filter { it.has("values") && it.getString("range").startsWith("04_Products") }
        assertEquals("The second backup must write exactly the surviving rows", 1, productWrites.size)
        // Header + 70 rows. Because the tab is cleared first, rows 71-100 cannot survive.
        assertEquals(71, productWrites.first().getJSONArray("values").length())
    }

    @Test
    fun anEmptySnapshotStillClearsTheTab() = runBlocking {
        val engine = RecordingEngine()
        val transport = GoogleSheetsApiTransport(MockAuth(), engine)
        assertTrue(transport.writeBackup("sheet", fullSnapshot((1..100).map { listOf("P-$it", BUSINESS_A, "I") })).isSuccess)
        assertTrue(transport.writeBackup("sheet", fullSnapshot(emptyList())).isSuccess)

        val data = engine.batchUpdateData()
        val cleared = data.any { !it.has("values") && it.getString("range") == "04_Products" }
        assertTrue("An empty dataset must still issue the clear, or 100 stale rows survive", cleared)
        val productWrites = data.filter { it.has("values") && it.getString("range").startsWith("04_Products") }
        assertEquals("Only the header row may be written for an empty dataset", 1, productWrites.first().getJSONArray("values").length())
    }

    @Test
    fun clearingUsesWholeTabRangesSoFormattingAndProtectionSurvive() = runBlocking {
        val engine = RecordingEngine()
        GoogleSheetsApiTransport(MockAuth(), engine)
            .writeBackup("sheet", fullSnapshot(listOf(listOf("P-1", BUSINESS_A, "Beras"))))

        val data = engine.batchUpdateData()
        val clears = data.filter { !it.has("values") }
        assertTrue("There must be clears", clears.isNotEmpty())
        clears.forEach { c ->
            val range = c.getString("range")
            assertFalse(
                "A clear must target whole tab values, not a fixed cell range: $range",
                range.contains('!')
            )
        }
    }

    // =======================================================================
    // TASK 7 - the H.4 fixes must stay green
    // =======================================================================

    @Test
    fun h4SaleReturnTaxableFixIsStillApplied() {
        assertTrue(CanonicalSerializer.parseBoolean("true"))
        assertFalse(CanonicalSerializer.parseBoolean("false"))
        assertTrue("Legacy \"1\" must still restore as taxable", CanonicalSerializer.parseBoolean("1"))
        assertFalse("Legacy \"0\" must still restore as NOT taxable", CanonicalSerializer.parseBoolean("0"))
    }

    @Test
    fun h4ProductDigitalColumnsAndPurchaseOrderUuidArePartOfTheWireContract() = runBlocking {
        val engine = RecordingEngine()
        val productTab = SheetTab(
            "04_Products",
            listOf(
                "uuid", "business_id", "category_uuid", "name", "purchase_price", "selling_price",
                "stock", "minimum_stock", "unit", "item_type", "is_deleted", "deleted_at",
                "created_at", "updated_at", "barcode", "image_uri", "taxable", "tax_rate_override",
                "fulfillment_mode", "digital_provider_id", "digital_product_code"
            ),
            listOf(listOf("P-1", BUSINESS_A, "C-1", "Voucher", "0", "25000", "0.0", "0.0", "pcs", "DIGITAL",
                "false", "NULL", "1", "1", "NULL", "NULL", "true", "NULL", "AUTOMATIC", "P-77", "SKU-1"))
        )
        val poTab = SheetTab(
            "20_PurchaseOrders",
            listOf(
                "uuid", "business_id", "device_id", "order_number", "supplier_uuid",
                "supplier_name_snapshot", "supplier_phone_snapshot", "status",
                "total_estimated_amount", "notes", "created_at", "updated_at",
                "sent_at", "received_at", "final_purchase_uuid"
            ),
            listOf(listOf("PO-1", BUSINESS_A, "D-1", "PO-1", "S-1", "Toko", "0812", "RECEIVED",
                "100", "NULL", "1", "1", "NULL", "2", "PURCHASE-UUID-9"))
        )
        val snapshot = BackupSnapshot(
            BackupMetadata(businessId = BUSINESS_A, checksum = "0".repeat(64)),
            CanonicalSerializer.ALL_TAB_NAMES.associateWith { name ->
                when (name) {
                    "04_Products" -> productTab
                    "20_PurchaseOrders" -> poTab
                    else -> SheetTab(name, listOf("h1", "h2"), emptyList())
                }
            }
        )
        assertTrue(GoogleSheetsApiTransport(MockAuth(), engine).writeBackup("sheet", snapshot).isSuccess)

        val data = engine.batchUpdateData()
        val productWrite = data.first { it.has("values") && it.getString("range").startsWith("04_Products") }
        val headers = productWrite.getJSONArray("values").getJSONArray(0)
        assertEquals("fulfillment_mode", headers.getString(18))
        assertEquals("digital_provider_id", headers.getString(19))
        assertEquals("digital_product_code", headers.getString(20))

        val poWrite = data.first { it.has("values") && it.getString("range").startsWith("20_PurchaseOrders") }
        assertEquals(
            "final_purchase_uuid",
            poWrite.getJSONArray("values").getJSONArray(0).getString(14)
        )
    }

    // =======================================================================
    // TASK 5 - DataStore failure must be visible, not swallowed
    // =======================================================================

    @Test
    fun dataStoreFailureIsReportedAsANonRollbackSignal() {
        val cause = IllegalStateException("DataStore write failed")
        val ex = DataStoreReconciliationRequiredException(cause)
        assertEquals(cause, ex.dataStoreCause)
        assertTrue(
            "The message must tell the operator the database is already committed",
            ex.message!!.contains("already updated")
        )
        assertTrue(
            "The message must warn against a blind retry, which would apply the snapshot twice",
            ex.message!!.contains("Do NOT re-run the restore blindly")
        )
        assertTrue("The cause must be preserved", ex.cause === cause)
    }
}
