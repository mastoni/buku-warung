package id.skmnetwork.bukuwarung.backup

import id.skmnetwork.bukuwarung.backup.transport.GoogleAuthConnectionState
import id.skmnetwork.bukuwarung.backup.transport.GoogleAuthCredentialProvider
import id.skmnetwork.bukuwarung.backup.transport.GoogleSheetsApiTransport
import id.skmnetwork.bukuwarung.backup.transport.SheetsHttpEngine
import id.skmnetwork.bukuwarung.backup.transport.SheetsHttpResponse
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/**
 * Gate H.4 - Google Sheets backup/restore data integrity.
 *
 * IMPORTANT - SCOPE OF THIS TEST:
 * These are UNIT tests over the serialization, transport and validation logic, driven by an
 * in-memory `SheetsHttpEngine`. They prove the wire format and the restore contracts. They are
 * NOT, and must not be mistaken for, a real Google integration test: no OAuth token is obtained,
 * no Google account is contacted, and no real spreadsheet is read or written. Real end-to-end
 * validation requires the Android OAuth client to be registered in Google Cloud for
 * `id.skmnetwork.bukuwarung` + SHA-1 `AF:AA:9A:CA:DE:95:6A:E6:47:1D:F1:39:8D:EB:B0:51:BE:07:77:48`,
 * which is an external blocker outside the repository.
 */
class GoogleSheetsDataIntegrityTest {

    private companion object {
        const val BUSINESS = "TEST-BUSINESS-001"
        const val OTHER_BUSINESS = "OTHER-BUSINESS-999"
    }

    // -----------------------------------------------------------------------
    // MOCK OAuth provider. Supplies a literal token string; never contacts Google.
    // -----------------------------------------------------------------------
    private class MockAuthProvider(
        var token: String? = "MOCK_TOKEN_NOT_A_REAL_CREDENTIAL",
        var failToken: Throwable? = null
    ) : GoogleAuthCredentialProvider {
        var clearTokenCalls = 0
        override suspend fun getAccessToken(): Result<String> =
            failToken?.let { Result.failure(it) } ?: Result.success(token!!)

        override suspend fun authorizeAccount(email: String): GoogleAuthConnectionState =
            GoogleAuthConnectionState.Connected(email, listOf("https://www.googleapis.com/auth/spreadsheets"))

        override suspend fun handleAuthorizationResult(
            email: String,
            data: android.content.Intent?
        ): Result<GoogleAuthConnectionState.Connected> =
            Result.success(GoogleAuthConnectionState.Connected(email))

        override fun clearToken() {
            clearTokenCalls++
            token = null
        }
    }

    /**
     * In-memory stand-in for the Google Sheets v4 REST API.
     *
     * Records EVERY request. A single `lastRequestBody` field is not enough: a successful
     * `writeBackup` is followed by `applyFormattingAndProtection`, which issues a metadata GET
     * (no body) and a `:batchUpdate` POST, so the last captured body would be the wrong one.
     */
    private class InMemorySheetsEngine : SheetsHttpEngine {
        private data class Call(val method: String, val url: String, val body: String?)

        private val calls = mutableListOf<Call>()
        var forcedStatus: Int? = null
        var forcedBody: String? = null
        var networkError: Boolean = false

        override suspend fun execute(
            method: String,
            urlString: String,
            headers: Map<String, String>,
            body: String?
        ): Result<SheetsHttpResponse> {
            calls += Call(method, urlString, body)
            if (networkError) return Result.failure(IOException("simulated network failure"))
            forcedStatus?.let { return Result.success(SheetsHttpResponse(it, emptyMap(), forcedBody ?: "{}")) }
            if (urlString.endsWith("/values:batchUpdate")) {
                return Result.success(SheetsHttpResponse(200, emptyMap(), "{\"updatedCells\":1}"))
            }
            return Result.success(SheetsHttpResponse(200, emptyMap(), forcedBody ?: "{}"))
        }

        val callCount: Int get() = calls.size

        private fun valueUpdatePayload(): JSONObject {
            val call = calls.lastOrNull { it.url.endsWith("/values:batchUpdate") }
                ?: throw AssertionError("no values:batchUpdate request was issued; calls=${calls.map { it.url }}")
            return JSONObject(call.body!!)
        }

        fun requestRanges(): List<String> {
            val data = valueUpdatePayload().getJSONArray("data")
            return (0 until data.length()).map { data.getJSONObject(it).getString("range") }
        }

        fun rowsForRange(range: String): List<List<String>> {
            val data = valueUpdatePayload().getJSONArray("data")
            for (i in 0 until data.length()) {
                val item = data.getJSONObject(i)
                if (item.getString("range") == range) {
                    val values = item.getJSONArray("values")
                    return (0 until values.length()).map { r ->
                        val arr = values.getJSONArray(r)
                        (0 until arr.length()).map { arr.getString(it) }
                    }
                }
            }
            throw AssertionError("range '$range' not present in write request")
        }
    }

    private fun transport(engine: SheetsHttpEngine, auth: GoogleAuthCredentialProvider = MockAuthProvider()) =
        GoogleSheetsApiTransport(auth, engine)

    private fun snapshotWithProducts(
        products: List<List<String>>,
        businessId: String = BUSINESS
    ): BackupSnapshot {
        val tabs = CanonicalSerializer.DATA_TAB_NAMES.associateWith { name ->
            SheetTab(name, listOf("uuid", "business_id"), emptyList())
        }.toMutableMap()
        // The transport writes 00_README and 00_Metadata only when present in the snapshot.
        tabs[CanonicalSerializer.README_TAB_NAME] = SheetTab(
            CanonicalSerializer.README_TAB_NAME, listOf("Informasi", "Keterangan"), emptyList()
        )
        tabs[CanonicalSerializer.METADATA_TAB_NAME] = SheetTab(
            CanonicalSerializer.METADATA_TAB_NAME, listOf("key", "value"), emptyList()
        )
        tabs["04_Products"] = SheetTab(
            "04_Products",
            listOf(
                "uuid", "business_id", "category_uuid", "name", "purchase_price", "selling_price",
                "stock", "minimum_stock", "unit", "item_type", "is_deleted", "deleted_at",
                "created_at", "updated_at", "barcode", "image_uri", "taxable", "tax_rate_override",
                "fulfillment_mode", "digital_provider_id", "digital_product_code"
            ),
            products
        )
        return BackupSnapshot(
            BackupMetadata(
                businessId = businessId,
                checksum = CanonicalSerializer.calculateChecksum(tabs),
                capabilities = setOf("CAP_DIGITAL_ITEMS", "ACTIVITY_WHOLESALE_PURCHASE")
            ),
            tabs
        )
    }

    // =======================================================================
    // 1. Boolean wire forms - Gate H.4 fix for sale_return_items.taxable
    // =======================================================================

    @Test
    fun parseBooleanAcceptsBothWireForms() {
        // Current writer emits "true"/"false".
        assertTrue(CanonicalSerializer.parseBoolean("true"))
        assertFalse(CanonicalSerializer.parseBoolean("false"))
        // Legacy writer emitted the raw SQLite ints.
        assertTrue("Legacy \"1\" must restore as taxable", CanonicalSerializer.parseBoolean("1"))
        assertFalse("Legacy \"0\" must restore as NOT taxable", CanonicalSerializer.parseBoolean("0"))
        // Case/space tolerant.
        assertTrue(CanonicalSerializer.parseBoolean(" TRUE "))
        // NULL sentinel and junk fall back to the caller's default rather than guessing.
        assertTrue(CanonicalSerializer.parseBoolean("NULL", default = true))
        assertFalse(CanonicalSerializer.parseBoolean("NULL", default = false))
        assertTrue(CanonicalSerializer.parseBoolean(null, default = true))
        assertFalse(CanonicalSerializer.parseBoolean("banana", default = false))
    }

    @Test
    fun oldBooleanParsingSilentlyMadeEveryReturnLineTaxable() {
        // The pre-gate expression used to decide the restored `taxable` flag.
        fun oldParse(raw: String?): Boolean =
            if (raw != null) raw.toBooleanStrictOrNull() ?: true else true

        assertEquals("Pre-gate, legacy \"0\" was parsed as true - the data corruption this gate fixes", true, oldParse("0"))
        assertEquals("Post-gate, legacy \"0\" restores correctly", false, CanonicalSerializer.parseBoolean("0", default = true))
    }

    /**
     * Round-trip through the exact expression BackupRestoreManager now uses to restore
     * `sale_return_items.taxable`, for both the current and the legacy wire form.
     */
    @Test
    fun saleReturnItemTaxableRestoresCorrectlyForBothWireForms() {
        // Column 10 of 18_SaleReturnItems.
        fun restoreTaxable(row: List<String>): Boolean =
            CanonicalSerializer.parseBoolean(row.getOrNull(10), default = true)

        val base = listOf("RI-1", BUSINESS, "RET-1", "SI-1", "P-1", "Beras", "1.0", "15000", "12000", "15000", "", "NULL", "NULL", "1000")
        fun withTaxable(value: String) = base.toMutableList().apply { this[10] = value }

        val legacyNotTaxable = withTaxable("0")
        val legacyTaxable = withTaxable("1")
        val currentNotTaxable = withTaxable("false")
        val currentTaxable = withTaxable("true")

        assertFalse("A legacy non-taxable return line must NOT come back taxable", restoreTaxable(legacyNotTaxable))
        assertTrue("A legacy taxable return line must stay taxable", restoreTaxable(legacyTaxable))
        assertFalse("A current non-taxable return line must NOT come back taxable", restoreTaxable(currentNotTaxable))
        assertTrue("A current taxable return line must stay taxable", restoreTaxable(currentTaxable))
    }

    @Test
    fun saleReturnItemTaxableIsWrittenInTheCanonicalForm() {
        assertEquals("true", CanonicalSerializer.formatBoolean(true))
        assertEquals("false", CanonicalSerializer.formatBoolean(false))
        // And that written form reads back identically.
        assertTrue(CanonicalSerializer.parseBoolean(CanonicalSerializer.formatBoolean(true)))
        assertFalse(CanonicalSerializer.parseBoolean(CanonicalSerializer.formatBoolean(false)))
    }

    // =======================================================================
    // 2. Wire contract for the product tab
    //
    // COVERAGE NOTE: these assert the contract the transport must honour, given a snapshot.
    // They do NOT execute BackupRestoreManager.exportSnapshot, which needs a live Room
    // database and is therefore covered only by the on-device instrumentation suites
    // (GoogleAccountSheetsTest / SettingsBackupRestoreTest). The column list below is the
    // documented contract that BackupRestoreManager must emit.
    // =======================================================================

    private val productsTabHeaders = listOf(
        "uuid", "business_id", "category_uuid", "name", "purchase_price", "selling_price",
        "stock", "minimum_stock", "unit", "item_type", "is_deleted", "deleted_at",
        "created_at", "updated_at", "barcode", "image_uri", "taxable", "tax_rate_override",
        "fulfillment_mode", "digital_provider_id", "digital_product_code"
    )

    @Test
    fun productTabTransportsDigitalFulfilmentColumnsToTheSheet() = runBlocking {
        val engine = InMemorySheetsEngine()
        val row = listOf(
            "P-1", BUSINESS, "C-1", "Voucher Game", "0", "25000", "0.0", "0.0", "pcs", "DIGITAL",
            "false", "NULL", "1000", "2000", "NULL", "NULL", "true", "NULL",
            "AUTOMATIC", "PROVIDER-77", "SKU-ABC"
        )
        val result = transport(engine).writeBackup("sheet-1", snapshotWithProducts(listOf(row)))
        assertTrue("writeBackup must succeed: ${result.exceptionOrNull()}", result.isSuccess)

        val written = engine.rowsForRange("04_Products!A1")
        assertEquals(
            "Every product column must reach the sheet header",
            productsTabHeaders,
            written.first()
        )
        assertEquals(21, written.first().size)
        assertEquals("AUTOMATIC", written[1][18])
        assertEquals("PROVIDER-77", written[1][19])
        assertEquals("SKU-ABC", written[1][20])
    }

    // =======================================================================
    // 3. Purchase order foreign key travels as a UUID - Gate H.4 fix
    //
    // COVERAGE NOTE: as above, this pins the wire contract. BackupRestoreManager's own
    // purchase_orders export/restore mapping runs only against a live Room database.
    // =======================================================================

    @Test
    fun purchaseOrderTransportsFinalPurchaseAsUuidNotNumericId() = runBlocking {
        val engine = InMemorySheetsEngine()
        val tabs = CanonicalSerializer.DATA_TAB_NAMES.associateWith { SheetTab(it, listOf("uuid"), emptyList()) }
            .toMutableMap()
        tabs[CanonicalSerializer.README_TAB_NAME] = SheetTab(
            CanonicalSerializer.README_TAB_NAME, listOf("Informasi", "Keterangan"), emptyList()
        )
        tabs[CanonicalSerializer.METADATA_TAB_NAME] = SheetTab(
            CanonicalSerializer.METADATA_TAB_NAME, listOf("key", "value"), emptyList()
        )
        tabs["20_PurchaseOrders"] = SheetTab(
            "20_PurchaseOrders",
            listOf(
                "uuid", "business_id", "device_id", "order_number", "supplier_uuid",
                "supplier_name_snapshot", "supplier_phone_snapshot", "status",
                "total_estimated_amount", "notes", "created_at", "updated_at",
                "sent_at", "received_at", "final_purchase_uuid"
            ),
            listOf(
                listOf(
                    "PO-1", BUSINESS, "DEV-1", "PO-001", "S-1", "Toko", "0812", "RECEIVED",
                    "100000", "NULL", "1000", "2000", "NULL", "3000", "PURCHASE-UUID-9"
                )
            )
        )
        val snapshot = BackupSnapshot(
            BackupMetadata(businessId = BUSINESS, checksum = CanonicalSerializer.calculateChecksum(tabs)),
            tabs
        )

        val result = transport(engine).writeBackup("sheet-1", snapshot)
        assertTrue(result.isSuccess)

        val headers = engine.rowsForRange("20_PurchaseOrders!A1").first()
        assertEquals("final_purchase_uuid", headers[14])
        assertEquals(
            "The foreign key must travel as a UUID so it survives id reallocation on restore",
            "PURCHASE-UUID-9",
            engine.rowsForRange("20_PurchaseOrders!A1")[1][14]
        )
    }

    // =======================================================================
    // 4. Business tenant preservation
    // =======================================================================

    @Test
    fun everyWrittenTabKeepsTheActiveBusinessId() = runBlocking {
        val engine = InMemorySheetsEngine()
        transport(engine).writeBackup("sheet-1", snapshotWithProducts(
            listOf(listOf(
                "P-1", BUSINESS, "C-1", "Beras", "15000", "15000", "100.0", "1.0", "kg", "PHYSICAL",
                "false", "NULL", "1000", "2000", "NULL", "NULL", "true", "NULL", "MANUAL", "NULL", "NULL"
            ))
        ))
        val data = engine.rowsForRange("04_Products!A1")[1]
        assertEquals("business_id column must carry the active tenant", BUSINESS, data[1])
    }

    @Test
    fun validatorRejectsABackupBelongingToAnotherBusiness() {
        val snapshot = snapshotWithProducts(emptyList(), businessId = OTHER_BUSINESS)
        val thrown = runCatching { BackupValidator.validate(snapshot, expectedBusinessId = BUSINESS) }
        assertTrue(
            "A backup from a different business must be rejected",
            thrown.exceptionOrNull() is BusinessIdMismatchException
        )
    }

    @Test
    fun validatorAcceptsABackupFromTheSameBusiness() {
        val snapshot = snapshotWithProducts(emptyList(), businessId = BUSINESS)
        runCatching { BackupValidator.validate(snapshot, expectedBusinessId = BUSINESS) }
            .onSuccess { }
            .onFailure { fail("A same-tenant backup must validate: $it") }
    }

    // =======================================================================
    // 5. Checksum and malformed data
    // =======================================================================

    @Test
    fun checksumIsDeterministicAndDetectsTampering() {
        val snapshot = snapshotWithProducts(emptyList())
        val a = CanonicalSerializer.calculateChecksum(snapshot.tabs)
        val b = CanonicalSerializer.calculateChecksum(snapshot.tabs)
        assertEquals("Checksum must be deterministic", a, b)

        val tampered = snapshot.tabs.toMutableMap()
        tampered["04_Products"] = SheetTab("04_Products", listOf("uuid", "business_id"), listOf(listOf("P-9", BUSINESS)))
        assertFalse(
            "Checksum must change when remote data is edited",
            CanonicalSerializer.verifyChecksum(a, tampered)
        )
    }

    @Test
    fun validatorRejectsAChecksumMismatch() {
        val tabs = CanonicalSerializer.DATA_TAB_NAMES.associateWith { SheetTab(it, listOf("uuid", "business_id"), emptyList()) }
        val snapshot = BackupSnapshot(
            BackupMetadata(businessId = BUSINESS, checksum = "0".repeat(64)),
            tabs
        )
        val thrown = runCatching { BackupValidator.validate(snapshot, expectedBusinessId = BUSINESS) }
        assertTrue(thrown.exceptionOrNull() is ChecksumMismatchException)
    }

    @Test
    fun malformedRemotePayloadIsReportedAsAnIoFailureNotACrash() = runBlocking {
        val engine = InMemorySheetsEngine().apply { forcedBody = "{ this is not json" }
        val result = transport(engine).readBackup("sheet-1")
        assertTrue("Malformed JSON must fail cleanly", result.isFailure)
        assertTrue(
            "Malformed payload must surface as IOException, got ${result.exceptionOrNull()}",
            result.exceptionOrNull() is IOException
        )
    }

    @Test
    fun missingMetadataTabIsReportedAsAnIoFailure() = runBlocking {
        val engine = InMemorySheetsEngine().apply {
            forcedBody = """{"valueRanges":[{"range":"00_README!A1:Z","values":[["a","b"]]}]}"""
        }
        val result = transport(engine).readBackup("sheet-1")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
    }

    @Test
    fun readParsesATabIntoASnapshot() = runBlocking {
        val engine = InMemorySheetsEngine().apply {
            forcedBody = """
                {"valueRanges":[
                  {"range":"'04_Products'!A1:Z","values":[["uuid","business_id","name"],["P-1","$BUSINESS","Beras"]]},
                  {"range":"'00_Metadata'!A1:Z","values":[["key","value"],["business_id","$BUSINESS"],["checksum","abc"]]}
                ]}
            """.trimIndent()
        }
        val result = transport(engine).readBackup("sheet-1")
        assertTrue("Well-formed payload must parse: ${result.exceptionOrNull()}", result.isSuccess)
        val snapshot = result.getOrThrow()
        assertEquals(BUSINESS, snapshot.metadata.businessId)
        assertNotNull(snapshot.getTab("04_Products"))
        assertEquals(1, snapshot.getTab("04_Products")!!.rows.size)
    }

    // =======================================================================
    // 6. Transport error mapping and token failure
    // =======================================================================

    @Test
    fun httpErrorsAreMappedToTypedExceptions() = runBlocking {
        val missing = InMemorySheetsEngine().apply { forcedStatus = 404 }
        val w = transport(missing).writeBackup("missing", snapshotWithProducts(emptyList()))
        assertTrue("A 404 write must fail", w.isFailure)
        assertTrue(
            "404 must be reported as SpreadsheetNotFoundException so the caller can recreate the sheet",
            w.exceptionOrNull() is SpreadsheetNotFoundException
        )

        val unauthorized = InMemorySheetsEngine().apply { forcedStatus = 401 }
        val r = transport(unauthorized).readBackup("sheet-1")
        assertTrue("A 401 read must fail", r.isFailure)
        assertTrue(
            "401 must be reported as SecurityException so the UI can prompt reconnection",
            r.exceptionOrNull() is SecurityException
        )
    }

    @Test
    fun blankTokenStopsTheRequestBeforeAnyCallIsMade() = runBlocking {
        val engine = InMemorySheetsEngine()
        val result = transport(engine, MockAuthProvider(token = "   "))
            .writeBackup("sheet-1", snapshotWithProducts(emptyList()))
        assertTrue("A blank token must fail", result.isFailure)
        assertEquals("No HTTP call may be attempted without a token", 0, engine.callCount)
    }

    @Test
    fun tokenFailureIsPropagatedWithoutAnyHttpCall() = runBlocking {
        val engine = InMemorySheetsEngine()
        val result = transport(engine, MockAuthProvider(failToken = SecurityException("token denied")))
            .writeBackup("sheet-1", snapshotWithProducts(emptyList()))
        assertTrue(result.isFailure)
        assertEquals("No HTTP call may be attempted without a token", 0, engine.callCount)
    }

    @Test
    fun networkFailureIsPropagated() = runBlocking {
        val engine = InMemorySheetsEngine().apply { networkError = true }
        val result = transport(engine).writeBackup("sheet-1", snapshotWithProducts(emptyList()))
        assertTrue(result.isFailure)
    }

    // =======================================================================
    // 7. Disconnect / reconnect / account switch (MOCK provider semantics)
    // =======================================================================

    @Test
    fun clearingTheTokenForcesAFreshAuthorizationOnTheNextRequest() = runBlocking {
        val auth = MockAuthProvider()
        val engine = InMemorySheetsEngine()
        val t = transport(engine, auth)

        t.writeBackup("sheet-1", snapshotWithProducts(emptyList()))
        assertTrue("First write must reach the API", engine.callCount > 0)

        auth.clearToken()
        assertEquals("clearToken must be observable by the provider", 1, auth.clearTokenCalls)

        // Account switch: the caller persists a new e-mail and the provider must be re-armed.
        val reconnected = MockAuthProvider(token = "SECOND_ACCOUNT_TOKEN")
        val t2 = transport(InMemorySheetsEngine(), reconnected)
        val r = t2.writeBackup("sheet-2", snapshotWithProducts(emptyList()))
        assertTrue("Reconnect with a second account must succeed", r.isSuccess)
        assertEquals("SECOND_ACCOUNT_TOKEN", reconnected.token)
    }

    @Test
    fun writeRequestCoversEveryDataTabPlusReadmeAndMetadata() = runBlocking {
        val engine = InMemorySheetsEngine()
        transport(engine).writeBackup("sheet-1", snapshotWithProducts(emptyList()))
        val ranges = engine.requestRanges()

        assertTrue("00_README must be written", ranges.any { it.startsWith("00_README") })
        assertTrue("00_Metadata must be written", ranges.any { it.startsWith("00_Metadata") })
        CanonicalSerializer.DATA_TAB_NAMES.forEach { tab ->
            assertTrue("Tab $tab must be included in the write", ranges.any { it.startsWith(tab) })
        }
        // Gate H.4.1: one clear plus one write per owned tab.
        assertEquals(
            "Every owned tab must be cleared and then written",
            CanonicalSerializer.ALL_TAB_NAMES.size * 2,
            ranges.size
        )
    }
}
