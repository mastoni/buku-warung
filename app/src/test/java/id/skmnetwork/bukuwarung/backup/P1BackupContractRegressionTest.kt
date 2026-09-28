package id.skmnetwork.bukuwarung.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Gate H.4.2-CODE-FIX.1 - regression coverage for the three P1 findings from the H.4.2-CODE audit.
 *
 * Scope:
 *   #1 fail-closed required tab presence
 *   #2 fail-closed business_id (no NULL / blank skip)
 *   #3 no mock-default transport and no fabricated spreadsheet id
 *
 * These are JVM unit tests over the pure validator/contract logic. The Room restore transaction
 * itself is covered only by instrumentation (no live Room on the JVM) - see the H.4.1 coverage
 * note. What IS proven here is that a missing tab or a bad business_id is rejected *before* the
 * restore is allowed to open its destructive phase, and that no implementation can fabricate a
 * spreadsheet id.
 */
class P1BackupContractRegressionTest {

    private companion object {
        const val BUSINESS = "BUSINESS-A"
        const val OTHER = "BUSINESS-B"
    }

    /** A snapshot with every data tab present, zero rows - the shape of a fresh shop. */
    private fun fullSnapshot(
        businessId: String = BUSINESS,
        omitTabs: Set<String> = emptySet(),
        rowsByTab: Map<String, List<List<String>>> = emptyMap(),
        checksumAlgorithm: String = CanonicalSerializer.ChecksumAlgorithm.RAW_V1.wireName
    ): BackupSnapshot {
        val tabs = LinkedHashMap<String, SheetTab>()
        tabs[CanonicalSerializer.METADATA_TAB_NAME] =
            SheetTab(CanonicalSerializer.METADATA_TAB_NAME, listOf("key", "value"), emptyList())
        for (name in CanonicalSerializer.DATA_TAB_NAMES) {
            if (name in omitTabs) continue
            tabs[name] = SheetTab(
                name,
                listOf("uuid", "business_id"),
                rowsByTab[name] ?: emptyList()
            )
        }
        return BackupSnapshot(
            BackupMetadata(
                backupFormatVersion = CanonicalSerializer.BACKUP_FORMAT_VERSION,
                roomSchemaVersion = CanonicalSerializer.ROOM_SCHEMA_VERSION,
                businessId = businessId,
                checksum = CanonicalSerializer.calculateChecksum(tabs, CanonicalSerializer.ChecksumAlgorithm.RAW_V1),
                checksumAlgorithm = checksumAlgorithm
            ),
            tabs
        )
    }

    private fun rowFor(tab: String, businessIdCell: String): List<String> =
        if (BackupRestoreContract.businessIdIndexFor(tab) == 0) {
            listOf(businessIdCell, "shop", "owner", "phone", "address", "0", "WARUNG_SEMBAKO", "", "1")
        } else {
            listOf("ROW-1", businessIdCell, "extra")
        }

    // =======================================================================
    // P1 #1 - required tab presence
    // =======================================================================

    @Test
    fun p1_1_allRequiredTabsPresentValidates() {
        runCatching { BackupValidator.validate(fullSnapshot(), BUSINESS) }
            .onFailure { fail("A snapshot with every required tab must validate, got $it") }
    }

    @Test
    fun p1_1_everyRequiredTabIsActuallyRequired() {
        // Step 14A: all 21 data tabs are required. 19/20/21 used to be exempt because
        // exportSnapshot omitted them without CAP_DIGITAL_ITEMS / ACTIVITY_WHOLESALE_PURCHASE, and
        // that made a normal backup destructive. They are now exported unconditionally.
        assertEquals(21, BackupValidator.REQUIRED_DATA_TAB_NAMES.size)
        assertTrue("01_Business is mandatory", "01_Business" in BackupValidator.REQUIRED_DATA_TAB_NAMES)
        assertTrue("18_SaleReturnItems is mandatory", "18_SaleReturnItems" in BackupValidator.REQUIRED_DATA_TAB_NAMES)
        assertTrue(
            "19_DigitalTransactions is mandatory: a missing one deletes live digital rows",
            "19_DigitalTransactions" in BackupValidator.REQUIRED_DATA_TAB_NAMES
        )
        assertTrue(
            "20_PurchaseOrders is mandatory: a missing one deletes live purchase orders",
            "20_PurchaseOrders" in BackupValidator.REQUIRED_DATA_TAB_NAMES
        )
        assertTrue(
            "21_PurchaseOrderItems is mandatory: a missing one deletes live PO items",
            "21_PurchaseOrderItems" in BackupValidator.REQUIRED_DATA_TAB_NAMES
        )
        assertEquals(
            "The required list must be exactly the declared data tabs",
            CanonicalSerializer.DATA_TAB_NAMES,
            BackupValidator.REQUIRED_DATA_TAB_NAMES
        )
    }

    @Test
    fun p1_2_oneMissingRequiredTabFailsValidation() {
        for (missing in BackupValidator.REQUIRED_DATA_TAB_NAMES) {
            val snapshot = fullSnapshot(omitTabs = setOf(missing))
            val thrown = runCatching { BackupValidator.validate(snapshot, BUSINESS) }.exceptionOrNull()
            assertTrue(
                "A snapshot missing $missing must be rejected, got $thrown",
                thrown is CorruptedBackupException
            )
            assertTrue(
                "The error must name the missing tab",
                thrown!!.message!!.contains(missing)
            )
        }
    }

    @Test
    fun p1_2_missingTabIsReportedBeforeAnyOtherCheck() {
        // Tab presence runs first, so a structurally incomplete archive is rejected even if its
        // metadata would also fail a later check.
        val snapshot = fullSnapshot(businessId = "SOMETHING-ELSE", omitTabs = setOf("04_Products"))
        val thrown = runCatching { BackupValidator.validate(snapshot, BUSINESS) }.exceptionOrNull()
        assertTrue("Tab presence must be the first check", thrown is CorruptedBackupException)
    }

    @Test
    fun p1_3_missingTabIsRejectedBeforeTheDestructivePhase() {
        // BackupRestoreManager.restoreSnapshot calls BackupValidator.validate as its FIRST action,
        // before BackupRestoreContract.assertSingleTenant and before the first DELETE. Proving the
        // validator rejects the snapshot is therefore what keeps the deletes from running.
        val snapshot = fullSnapshot(omitTabs = setOf("15_CashTransactions"))
        val thrown = runCatching { BackupValidator.validate(snapshot, BUSINESS) }.exceptionOrNull()
        assertNotNull("A missing cash tab must abort before any DELETE", thrown)
        assertTrue(thrown is CorruptedBackupException)

        // This is precisely the hole being closed: the row-level tenant check is BLIND to a wholly
        // absent tab, because there is no row to inspect - it succeeds. Before this fix, validation
        // therefore passed, the cash_transactions table was DELETEd by RESTORE_DELETE_ORDER,
        // nothing was re-inserted, and the restore reported success. BackupValidator is the only
        // layer that can catch it, which is why assertRequiredTabsPresent runs first.
        runCatching { BackupRestoreContract.assertSingleTenant(snapshot, BUSINESS) }
            .onFailure { fail("The row-level tenant check should be blind to a missing tab, but it threw: $it") }
    }

    @Test
    fun p1_4_allRequiredTabsPresentWithZeroRowsValidates() {
        val snapshot = fullSnapshot() // every tab present, no rows at all
        runCatching { BackupValidator.validate(snapshot, BUSINESS) }
            .onFailure { fail("Empty-but-complete tabs are a valid fresh-shop archive, got $it") }
    }

    @Test
    fun p1_5_absentNonDataTabsDoNotMatter() {
        // 00_README is a human layer and is never required.
        val snapshot = fullSnapshot()
        assertTrue("README must not be required", "00_README" !in BackupValidator.REQUIRED_DATA_TAB_NAMES)
        runCatching { BackupValidator.validate(snapshot, BUSINESS) }
            .onFailure { fail("A missing README must not block a restore, got $it") }

        // A completely empty tab map is a different matter: everything is missing.
        val empty = BackupSnapshot(
            BackupMetadata(businessId = BUSINESS, checksum = ""),
            emptyMap()
        )
        val thrown = runCatching { BackupValidator.validate(empty, BUSINESS) }.exceptionOrNull()
        assertTrue("An empty snapshot must be rejected", thrown is CorruptedBackupException)
    }

    @Test
    fun p1_missingDigitalAndPurchaseOrderTabsAreRejected() {
        // Step 14A reversal. This used to assert the opposite: that a snapshot without these three
        // tabs still validated. It did, because exportSnapshot omitted them whenever the merchant's
        // profile lacked CAP_DIGITAL_ITEMS / ACTIVITY_WHOLESALE_PURCHASE - and a merchant can hold
        // digital transactions and purchase orders without either, because nothing gates those
        // writes. The archive passed, the restore deleted the live rows, inserted nothing, and
        // reported success.
        for (missing in listOf("19_DigitalTransactions", "20_PurchaseOrders", "21_PurchaseOrderItems")) {
            val snapshot = fullSnapshot(omitTabs = setOf(missing))
            val thrown = runCatching { BackupValidator.validate(snapshot, BUSINESS) }.exceptionOrNull()
            assertTrue(
                "A snapshot missing $missing must be rejected, got $thrown",
                thrown is CorruptedBackupException
            )
            assertTrue(
                "The error must name the missing tab",
                thrown!!.message!!.contains(missing)
            )
        }

        val allThree = fullSnapshot(
            omitTabs = setOf("19_DigitalTransactions", "20_PurchaseOrders", "21_PurchaseOrderItems")
        )
        val thrown = runCatching { BackupValidator.validate(allThree, BUSINESS) }.exceptionOrNull()
        assertTrue("All three missing must be rejected", thrown is CorruptedBackupException)
    }

    // =======================================================================
    // P1 #2 - fail-closed business_id
    // =======================================================================

    @Test
    fun p2_1_matchingBusinessIdPasses() {
        for (tab in CanonicalSerializer.DATA_TAB_NAMES) {
            val snapshot = fullSnapshot(rowsByTab = mapOf(tab to listOf(rowFor(tab, BUSINESS))))
            runCatching { BackupRestoreContract.assertSingleTenant(snapshot, BUSINESS) }
                .onFailure { fail("Tab $tab with a matching business_id must pass, got $it") }
        }
    }

    @Test
    fun p2_2_foreignBusinessIdFails() {
        for (tab in CanonicalSerializer.DATA_TAB_NAMES) {
            val snapshot = fullSnapshot(rowsByTab = mapOf(tab to listOf(rowFor(tab, OTHER))))
            val thrown = runCatching { BackupRestoreContract.assertSingleTenant(snapshot, BUSINESS) }
                .exceptionOrNull()
            assertTrue("Tab $tab claiming another tenant must be rejected", thrown is CrossTenantBackupException)
        }
    }

    @Test
    fun p2_3_blankBusinessIdFails() {
        for (blank in listOf("", "   ")) {
            val snapshot = fullSnapshot(rowsByTab = mapOf("04_Products" to listOf(rowFor("04_Products", blank))))
            val thrown = runCatching { BackupRestoreContract.assertSingleTenant(snapshot, BUSINESS) }
                .exceptionOrNull()
            assertTrue("A blank business_id ('$blank') must be rejected", thrown is CrossTenantBackupException)
        }
    }

    @Test
    fun p2_4_nullSentinelBusinessIdFailsClosed() {
        // The pre-fix defect: the NULL sentinel was skipped, so the row passed verification
        // unverified and was then inserted verbatim into a pseudo-tenant.
        val snapshot = fullSnapshot(
            rowsByTab = mapOf(
                "04_Products" to listOf(rowFor("04_Products", CanonicalSerializer.NULL_SENTINEL))
            )
        )
        val thrown = runCatching { BackupRestoreContract.assertSingleTenant(snapshot, BUSINESS) }
            .exceptionOrNull()
        assertTrue(
            "A NULL_SENTINEL business_id must be rejected, not skipped",
            thrown is CrossTenantBackupException
        )
        assertTrue(thrown!!.message!!.contains("NULL business_id"))
    }

    @Test
    fun p2_5_missingBusinessIdColumnFails() {
        val tabs = fullSnapshot().tabs.toMutableMap()
        tabs["04_Products"] = SheetTab("04_Products", listOf("uuid", "business_id"), listOf(listOf("P-1")))
        val snapshot = BackupSnapshot(BackupMetadata(businessId = BUSINESS, checksum = ""), tabs)
        val thrown = runCatching { BackupRestoreContract.assertSingleTenant(snapshot, BUSINESS) }
            .exceptionOrNull()
        assertTrue("A row with no business_id cell must be rejected", thrown is CrossTenantBackupException)
    }

    @Test
    fun p2_6_businessTabColumnZeroRuleIsIntact() {
        assertEquals(0, BackupRestoreContract.businessIdIndexFor("01_Business"))
        assertEquals(1, BackupRestoreContract.businessIdIndexFor("02_Device"))
        assertEquals(1, BackupRestoreContract.businessIdIndexFor("21_PurchaseOrderItems"))

        // 01_Business carries the tenant in column 0, so a row whose column 0 is right must pass
        // even though column 1 holds the shop name.
        val ok = fullSnapshot(rowsByTab = mapOf("01_Business" to listOf(rowFor("01_Business", BUSINESS))))
        runCatching { BackupRestoreContract.assertSingleTenant(ok, BUSINESS) }
            .onFailure { fail("01_Business column-0 rule must still pass, got $it") }

        val bad = fullSnapshot(rowsByTab = mapOf("01_Business" to listOf(rowFor("01_Business", OTHER))))
        assertTrue(
            "01_Business must still reject a foreign tenant in column 0",
            runCatching { BackupRestoreContract.assertSingleTenant(bad, BUSINESS) }.exceptionOrNull()
                is CrossTenantBackupException
        )
    }

    @Test
    fun p2_1_nullSentinelIsNeverNormalisedToTheTarget() {
        // The requirement is rejection, not repair. Confirm no code path rewrites the sentinel.
        val snapshot = fullSnapshot(
            rowsByTab = mapOf("05_Customers" to listOf(rowFor("05_Customers", CanonicalSerializer.NULL_SENTINEL)))
        )
        val thrown = runCatching { BackupRestoreContract.assertSingleTenant(snapshot, BUSINESS) }
            .exceptionOrNull()
        assertTrue(thrown is CrossTenantBackupException)
        // The original snapshot is untouched - nothing was rewritten in place.
        assertEquals(
            CanonicalSerializer.NULL_SENTINEL,
            snapshot.getTab("05_Customers")!!.rows.first()[1]
        )
    }

    // =======================================================================
    // P1 #3 - no mock default, no fabricated spreadsheet id
    // =======================================================================

    @Test
    fun p3_1_mockTransportImplementsTheRealCreationContract() {
        // The SPI now owns createSpreadsheet, so a test double must answer it explicitly rather
        // than letting the manager invent an id.
        val transport: SheetsBackupTransport = MockSheetsTransport()
        val id = kotlinx.coroutines.runBlocking { transport.createSpreadsheet("Shop") }.getOrThrow()
        assertTrue("A mock id must be obviously fake", id.startsWith(MockSheetsTransport.MOCK_SPREADSHEET_ID_PREFIX))
        assertFalse("A mock id must never look like the old fabricated wall-clock value", id.startsWith("SPREADSHEET_"))
    }

    @Test
    fun p3_1_mockTransportCanSimulateCreationFailure() {
        val transport = MockSheetsTransport().apply { simulateNetworkFailure = true }
        val result = kotlinx.coroutines.runBlocking { transport.createSpreadsheet("Shop") }
        assertTrue("A failing transport must report a failure, not invent an id", result.isFailure)
    }

    @Test
    fun p3_2_backupRestoreManagerHasNoDefaultTransport() {
        // Structural proof that the mock default is gone. Kotlin emits a synthetic constructor
        // carrying a bit-mask when a parameter has a default value, so a defaulted `transport`
        // would surface as an extra constructor. Exactly one 3-parameter constructor means the
        // dependency is mandatory and `BackupRestoreManager(db, prefs)` no longer compiles.
        val constructors = BackupRestoreManager::class.java.declaredConstructors
            .filterNot { it.isSynthetic }
        assertEquals(
            "BackupRestoreManager must expose exactly one constructor (no default transport)",
            1,
            constructors.size
        )
        assertEquals(3, constructors.first().parameterCount)
    }

    @Test
    fun p3_2_ensureSpreadsheetCreatedCannotFabricateAnId() {
        // The old body was:
        //     if (transport is GoogleSheetsApiTransport) transport.createSpreadsheet(title)
        //     else Result.success("SPREADSHEET_\${System.currentTimeMillis()}")
        // Assert no main source file still builds that fabricated literal.
        val offenders = mutableListOf<String>()
        mainSourceFiles().forEach { f ->
            f.readLines()
                .filterNot { l ->
                    val t = l.trimStart()
                    t.startsWith("*") || t.startsWith("//") || t.startsWith("/*")
                }
                .forEach { line ->
                    if (Regex("\"SPREADSHEET_").containsMatchIn(line)) {
                        offenders += "${f.name}: ${line.trim()}"
                    }
                }
        }
        assertTrue(
            "The fabricated SPREADSHEET_<millis> literal must not exist in main source: ${offenders.joinToString()}",
            offenders.isEmpty()
        )
    }

    private fun mainSourceFiles(): List<java.io.File> {
        val dir = java.io.File("src/main/java")
        if (!dir.exists()) return emptyList()
        return dir.walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }.toList()
    }

    @Test
    fun p3_2_mockTransportIsNeverReferencedFromMainSource() {
        // Structural proof by scan: MockSheetsTransport may only appear in its own declaration and
        // in prose. No main source file may reference it in code.
        val offenders = mainSourceFiles()
            .filter { it.name != "MockSheetsTransport.kt" }
            .filter { f ->
                f.readLines()
                    .filterNot { l ->
                        val t = l.trimStart()
                        t.startsWith("*") || t.startsWith("//") || t.startsWith("/*")
                    }
                    .joinToString("\n")
                    .contains("MockSheetsTransport")
            }
            .map { it.name }
        assertTrue(
            "No main source file may reference MockSheetsTransport in code: ${offenders.joinToString()}",
            offenders.isEmpty()
        )
    }

    @Test
    fun p3_3_googleSheetsApiTransportRemainsTheProductionImplementation() {
        val provider = object : id.skmnetwork.bukuwarung.backup.transport.GoogleAuthCredentialProvider {
            override suspend fun getAccessToken(): Result<String> = Result.success("t")
            override suspend fun authorizeAccount(email: String) =
                id.skmnetwork.bukuwarung.backup.transport.GoogleAuthConnectionState.Connected(email)

            override suspend fun handleAuthorizationResult(
                email: String,
                data: android.content.Intent?
            ) = Result.success(id.skmnetwork.bukuwarung.backup.transport.GoogleAuthConnectionState.Connected(email))

            override fun clearToken() = Unit
        }
        val production = id.skmnetwork.bukuwarung.backup.transport.GoogleSheetsApiTransport(provider)
        val viaSpi: SheetsBackupTransport = production
        assertNotNull(viaSpi)
        assertTrue(
            "The production transport must satisfy the creation contract on the SPI",
            SheetsBackupTransport::class.java.isAssignableFrom(production.javaClass)
        )
    }

    @Test
    fun p3_3_settingsScreenBackupViewModelIsRequiredNotDefaulted() {
        // SettingsScreen used to default backupViewModel to null and then build
        // `BackupRestoreManager(db, prefsRepo)` with no transport. Assert on the declaration,
        // ignoring comments (the KDoc names the old call on purpose).
        val file = mainSourceFiles().firstOrNull { it.name == "SettingsScreen.kt" }
        assertNotNull("SettingsScreen.kt not found under src/main/java", file)
        val codeLines = file!!.readLines()
            .filterNot { l ->
                val t = l.trimStart()
                t.startsWith("*") || t.startsWith("//") || t.startsWith("/*")
            }
        val start = codeLines.indexOfFirst { it.trimStart().startsWith("fun SettingsScreen(") }
        assertTrue("SettingsScreen declaration not found", start >= 0)
        val signature = codeLines.drop(start).take(12).joinToString("\n")
        assertTrue("The SettingsScreen signature must declare backupViewModel", signature.contains("backupViewModel"))
        assertFalse(
            "backupViewModel must not be nullable: $signature",
            signature.contains("backupViewModel: BackupViewModel?")
        )
        assertFalse(
            "backupViewModel must not default to null: $signature",
            signature.contains("backupViewModel: BackupViewModel = null")
        )
        assertFalse(
            "The mock-transport fallback constructor must be gone: $signature",
            signature.contains("BackupRestoreManager(db, prefsRepo)")
        )
    }

    @Test
    fun p3_4_mockTransportRemainsUsableForTests() {
        // The test double must keep working: existing instrumentation tests depend on it.
        val transport = MockSheetsTransport()
        val snapshot = fullSnapshot()
        val id = kotlinx.coroutines.runBlocking { transport.createSpreadsheet("Shop") }.getOrThrow()
        kotlinx.coroutines.runBlocking { transport.writeBackup(id, snapshot) }
            .onFailure { fail("MockSheetsTransport must still write for tests, got $it") }
        val read = kotlinx.coroutines.runBlocking { transport.readBackup(id) }.getOrThrow()
        assertEquals(BUSINESS, read.metadata.businessId)
    }
}
