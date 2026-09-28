package id.skmnetwork.bukuwarung.backup

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Step 14A - backup tab completeness.
 *
 * `exportSnapshot` used to omit `19_DigitalTransactions`, `20_PurchaseOrders` and
 * `21_PurchaseOrderItems` whenever the merchant's profile lacked `CAP_DIGITAL_ITEMS` or
 * `ACTIVITY_WHOLESALE_PURCHASE`, and `BackupValidator` excused exactly those three from the
 * required-tab check to match. Neither digital sales nor purchase orders are capability-gated, so a
 * merchant could hold those rows and archive without them. Restoring that archive then deleted the
 * live rows through `RESTORE_DELETE_ORDER`, re-inserted nothing because the tab was absent, passed
 * validation, and reported success.
 *
 * The backup contract these tests pin: every one of the 21 data tabs is always exported and always
 * required. An empty tab is valid - a fresh shop legitimately has no digital transactions - but a
 * missing one is not.
 */
class BackupTabCompletenessTest {

    private companion object {
        const val BUSINESS = "BUSINESS-A"
        const val OTHER_BUSINESS = "BUSINESS-B"
        val COMPLETENESS_TABS = listOf(
            "19_DigitalTransactions",
            "20_PurchaseOrders",
            "21_PurchaseOrderItems"
        )
    }

    // =======================================================================
    // Contract: all 21 data tabs are required again
    // =======================================================================

    @Test
    fun everyDataTabIsRequiredIncludingDigitalAndPurchaseOrders() {
        assertEquals("All 21 data tabs must be required", 21, BackupValidator.REQUIRED_DATA_TAB_NAMES.size)
        assertEquals(
            "The required list must be exactly the declared data tabs",
            CanonicalSerializer.DATA_TAB_NAMES,
            BackupValidator.REQUIRED_DATA_TAB_NAMES
        )
        for (tab in COMPLETENESS_TABS) {
            assertTrue(
                "$tab must be required: a missing one deletes live rows on restore",
                tab in BackupValidator.REQUIRED_DATA_TAB_NAMES
            )
        }
    }

    // =======================================================================
    // Empty tabs are valid, missing tabs are not
    // =======================================================================

    @Test
    fun emptyDigitalAndPurchaseOrderTabsAreValid() {
        // A shop that has never sold pulsa and never raised a purchase order still produces all
        // three tabs, with zero rows. That is a valid fresh-shop archive.
        val snapshot = snapshotWithEmptyCompletenessTabs()
        runCatching { BackupValidator.validate(snapshot, BUSINESS) }
            .onFailure { fail("Empty-but-present tabs are valid, got $it") }
    }

    @Test
    fun missingDigitalOrPurchaseOrderTabIsRejected() {
        for (missing in COMPLETENESS_TABS) {
            val snapshot = snapshotWithEmptyCompletenessTabs(omit = setOf(missing))
            val thrown = runCatching { BackupValidator.validate(snapshot, BUSINESS) }.exceptionOrNull()
            assertTrue("A snapshot missing $missing must be rejected, got $thrown", thrown is CorruptedBackupException)
            assertTrue(
                "The error must name the missing tab",
                thrown!!.message!!.contains(missing)
            )
        }
    }

    // =======================================================================
    // Cross-category regression: capability is not a permission
    // =======================================================================

    @Test
    fun exportIsNoLongerGatedOnBusinessTypeCapabilityOrActivity() {
        // Guards the fix itself. If a capability or activity condition ever returns to the export
        // path, this fails, because those three tabs must be built the same way as the other
        // eighteen unconditional ones. The check is on code, not on prose: the comment explaining
        // why the gate was removed names both capabilities on purpose.
        val source = readBackupSource("BackupRestoreManager.kt")
        val code = source.lines()
            .filterNot { it.trimStart().startsWith("//") }
            .filterNot { it.trimStart().startsWith("*") }
            .joinToString("\n")

        for (gate in listOf("hasDigitalItems", "hasWholesalePurchase", "BusinessCapability", "BusinessActivity")) {
            assertTrue(
                "The backup export must not decide tab inclusion via '$gate'",
                !code.contains(gate)
            )
        }
        assertEquals(
            "No tab may be wrapped in a nullable conditional any more",
            0,
            Regex("val tab\\d+ = if \\(").findAll(code).count()
        )
        for (tab in COMPLETENESS_TABS) {
            assertTrue(
                "$tab must still be exported by name",
                code.contains("\"$tab\"")
            )
        }
    }

    // =======================================================================
    // Restore symmetry: the delete side must stay unconditional
    // =======================================================================

    @Test
    fun restoreDeletesAllThreeTablesUnconditionally() {
        // This is the half of the bug that made it destructive. The delete list has no capability
        // filter, so omitting a tab from an archive was always a one-way loss.
        val order = BackupRestoreContract.RESTORE_DELETE_ORDER
        for (table in listOf("digital_transactions", "purchase_orders", "purchase_order_items")) {
            assertTrue(
                "$table is cleared before a restore and must stay in the delete order",
                table in order
            )
        }
        val statements = BackupRestoreContract.deleteStatements(BUSINESS)
        for (table in listOf("digital_transactions", "purchase_orders", "purchase_order_items")) {
            assertTrue(
                "$table must be deleted under the active tenant only",
                "DELETE FROM $table WHERE business_id = ?" in statements
            )
        }
    }

    // =======================================================================
    // Cross-tenant: one business cannot influence another's archive
    // =======================================================================

    @Test
    fun anotherBusinessCannotInfluenceTabValidity() {
        // Tab inclusion is a pure function of the exported rows, so a foreign tenant's presence
        // can only ever show up as a row, and the fail-closed tenant check rejects it. The
        // referential checks are exercised elsewhere; this test is about tenant isolation.
        fun snapshotWithDigitalRowFor(business: String) = snapshotWithEmptyCompletenessTabs(
            extraRows = mapOf(
                "19_DigitalTransactions" to listOf(
                    listOf("DIG-1", business, "SALE-ITEM-1", "PROV", "CODE", "0812", "10000", "10000", "DRAFT", "NULL", "NULL", "NULL", "1", "1")
                )
            )
        )

        val foreign = snapshotWithDigitalRowFor(OTHER_BUSINESS)
        val thrown = runCatching { BackupRestoreContract.assertSingleTenant(foreign, BUSINESS) }.exceptionOrNull()
        assertTrue(
            "Another business's digital row must be refused before the restore is allowed to delete anything",
            thrown is CrossTenantBackupException
        )

        runCatching { BackupRestoreContract.assertSingleTenant(snapshotWithDigitalRowFor(BUSINESS), BUSINESS) }
            .onFailure { fail("The tenant's own digital row must be accepted, got $it") }
    }

    @Test
    fun anEmptyCompletenessTabFromAnotherBusinessDoesNotChangeInclusion() {
        // Business A's archive is decided by Business A's rows only. Whether B has digital or PO
        // data is not an input to A's export, which is exactly why the old capability gate could
        // not stand: it described the merchant, not the data.
        val a = snapshotWithEmptyCompletenessTabs()
        val b = snapshotWithEmptyCompletenessTabs(
            extraRows = mapOf(
                "19_DigitalTransactions" to listOf(
                    listOf("DIG-B", OTHER_BUSINESS, "SI-B", "PROV", "CODE", "0812", "5000", "5000", "DRAFT", "NULL", "NULL", "NULL", "2", "2")
                ),
                "20_PurchaseOrders" to listOf(
                    listOf("PO-B", OTHER_BUSINESS, "DEV-B", "PO-NO-B", "SUP-B", "Toko B", "0812", "DRAFT", "100", "NULL", "3", "3", "NULL", "NULL", "NULL")
                )
            )
        )
        val aTabs = a.tabs.keys
        assertEquals("A's tab set is 21 data tabs plus metadata", 22, aTabs.size)
        for (tab in COMPLETENESS_TABS) {
            assertTrue("A still carries $tab", tab in aTabs)
        }
        // B's own archive carries the rows; A's does not, and A is unaffected either way.
        assertEquals(1, b.tabs.getValue("19_DigitalTransactions").rows.size)
        assertEquals(1, b.tabs.getValue("20_PurchaseOrders").rows.size)
        runCatching { BackupValidator.validate(a, BUSINESS) }
            .onFailure { fail("A must validate regardless of B, got $it") }
    }

    // ---- helpers -----------------------------------------------------------

    /**
     * A complete, empty archive: every data tab present with zero rows, plus the metadata tab. That
     * is exactly what a freshly installed shop exports, and it is the baseline the cross-category
     * fixtures are built on. The checksum is the real canonical one so the validator reaches the
     * checks under test instead of stopping at a checksum mismatch.
     */
    private fun snapshotWithEmptyCompletenessTabs(
        businessId: String = BUSINESS,
        omit: Set<String> = emptySet(),
        extraRows: Map<String, List<List<String>>> = emptyMap()
    ): BackupSnapshot {
        val tabs = LinkedHashMap<String, SheetTab>()
        tabs[CanonicalSerializer.METADATA_TAB_NAME] =
            SheetTab(CanonicalSerializer.METADATA_TAB_NAME, listOf("key", "value"), emptyList())
        for (name in CanonicalSerializer.DATA_TAB_NAMES) {
            if (name in omit) continue
            tabs[name] = SheetTab(
                name,
                listOf("uuid", "business_id", "payload"),
                extraRows[name] ?: emptyList()
            )
        }
        return BackupSnapshot(
            BackupMetadata(
                backupFormatVersion = CanonicalSerializer.BACKUP_FORMAT_VERSION,
                roomSchemaVersion = CanonicalSerializer.ROOM_SCHEMA_VERSION,
                businessId = businessId,
                checksum = CanonicalSerializer.calculateChecksum(tabs, CanonicalSerializer.ChecksumAlgorithm.RAW_V1),
                checksumAlgorithm = CanonicalSerializer.ChecksumAlgorithm.RAW_V1.wireName
            ),
            tabs
        )
    }

    private fun readBackupSource(fileName: String): String {
        val relative = "src/main/java/id/skmnetwork/bukuwarung/backup/$fileName"
        val candidates = listOf(File(relative), File("app/$relative"), File("../$relative"))
        val file = candidates.firstOrNull { it.isFile }
            ?: error("$fileName not found; looked at ${candidates.joinToString { it.absolutePath }}")
        return file.readText()
    }
}
