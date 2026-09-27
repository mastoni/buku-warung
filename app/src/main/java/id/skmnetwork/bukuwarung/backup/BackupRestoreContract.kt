package id.skmnetwork.bukuwarung.backup

/**
 * Gate H.4.1 - the tenant contract for backup restore.
 *
 * Two invariants live here, deliberately as *pure* functions so they can be unit-tested without a
 * live Room database, and so there is exactly one definition of them rather than one per call site.
 *
 * 1. TENANT-SCOPED DELETE.
 *    The restore used to run twenty bare `DELETE FROM <table>` statements. On a device that has
 *    ever held more than one business id - a legacy `LEGACY_BUSINESS` set before first-run
 *    reconciliation, or an id changed through `setBusinessId` - a restore for Business A destroyed
 *    Business B's rows. Every one of those tables carries a `business_id` column, so the deletes
 *    are now parameterised by the restore target and the foreign keys that pointed at the deleted
 *    rows are removed with them in the same transaction.
 *
 * 2. SINGLE-TENANT SNAPSHOT.
 *    The per-row `business_id` was written into the database verbatim while only the *metadata*
 *    business id was validated. A hand-edited sheet, a tab copied from another tenant's sheet, or
 *    two spreadsheets merged by hand could therefore inject another tenant's rows into this
 *    device. Those rows are invisible to every repository (they all filter by businessId) and
 *    become visible the moment the device's business id is ever switched. The whole restore is
 *    rejected instead - never silently normalised, because normalising would mean rewriting the
 *    merchant's data behind their back.
 *
 * The tab order below is children-before-parents, which is what makes the deletes foreign-key
 * safe: a `sale_items` row is removed before the `sales_transactions` row it points at.
 */
object BackupRestoreContract {

    /** Every data tab carries business_id in column 1... */
    const val BUSINESS_ID_COLUMN_INDEX = 1

    /** ...except 01_Business, where the business id IS the row key in column 0. */
    const val BUSINESS_TAB_BUSINESS_ID_INDEX = 0

    const val BUSINESS_TAB = "01_Business"

    /**
     * Tables cleared before a restore, children before parents so no foreign key is ever left
     * dangling mid-transaction. All of them carry `business_id`.
     */
    val RESTORE_DELETE_ORDER: List<String> = listOf(
        "sale_return_items",
        "sale_return_transactions",
        "stock_movements",
        "cash_transactions",
        "supplier_payments",
        "supplier_payables",
        "debt_payments",
        "debts",
        "purchase_items",
        "purchase_transactions",
        "purchase_order_items",
        "purchase_orders",
        "digital_transactions",
        "sale_items",
        "sales_transactions",
        "products",
        "categories",
        "customers",
        "suppliers",
        "sync_queue"
    )

    /**
     * Resolves which tenant this restore is allowed to touch.
     *
     * The caller's active business id wins when present. A backup that carries its own business id
     * but is being restored onto a device with a different active id is rejected earlier by
     * [BackupValidator]; this only decides the value used to scope the deletes.
     */
    fun resolveTargetBusinessId(expected: String?, metadataBusinessId: String): String? =
        expected?.takeIf { it.isNotBlank() }
            ?: metadataBusinessId.takeIf { it.isNotBlank() }

    /**
     * The delete statements for [targetBusinessId], in foreign-key-safe order.
     *
     * @throws IllegalArgumentException when the target is blank, because an unscoped delete is
     * exactly the failure mode this class exists to prevent. A caller that cannot determine the
     * tenant must abort the restore rather than wipe the device.
     */
    fun deleteStatements(targetBusinessId: String): List<String> {
        require(targetBusinessId.isNotBlank()) {
            "Refusing to build an unscoped restore delete: target business id is blank"
        }
        return RESTORE_DELETE_ORDER.map { table -> "DELETE FROM $table WHERE business_id = ?" }
    }

    /** Column holding the business id for [tabName]. */
    fun businessIdIndexFor(tabName: String): Int =
        if (tabName == BUSINESS_TAB) BUSINESS_TAB_BUSINESS_ID_INDEX else BUSINESS_ID_COLUMN_INDEX

    /**
     * Rejects a snapshot that carries any row belonging to a different tenant.
     *
     * Called BEFORE the delete phase, so a cross-tenant archive is refused while the database is
     * still untouched.
     *
     * @throws CrossTenantBackupException when a row claims a different business.
     */
    fun assertSingleTenant(snapshot: BackupSnapshot, targetBusinessId: String) {
        require(targetBusinessId.isNotBlank()) {
            "Refusing to validate against a blank target business id"
        }
        for (tabName in CanonicalSerializer.DATA_TAB_NAMES) {
            val tab = snapshot.getTab(tabName) ?: continue
            val index = businessIdIndexFor(tabName)
            tab.rows.forEachIndexed { rowIndex, row ->
                val claimed = row.getOrNull(index)
                    ?: throw CrossTenantBackupException(
                        "Tab $tabName row ${rowIndex + 1} has no business_id column; refusing a restore that cannot be tenant-verified"
                    )
                if (claimed == CanonicalSerializer.NULL_SENTINEL) continue
                if (claimed != targetBusinessId) {
                    throw CrossTenantBackupException(
                        "Cross-tenant restore rejected: tab $tabName row ${rowIndex + 1} claims business_id " +
                            "'$claimed' but this device is restoring '$targetBusinessId'"
                    )
                }
            }
        }
    }
}

/**
 * Thrown when an archive contains rows belonging to a different business than the one being
 * restored. The restore is aborted before any database mutation.
 */
class CrossTenantBackupException(message: String) : BackupException(message)

/**
 * Gate H.4.1 - raised when the Room restore committed but the follow-up DataStore update failed.
 *
 * This is deliberately NOT a rollback signal: Room is already committed and authoritative, and
 * retrying the restore blindly would apply the snapshot twice. The caller must surface this as a
 * partial-success / reconciliation-required state rather than claiming a clean restore.
 */
class DataStoreReconciliationRequiredException(
    val dataStoreCause: Throwable
) : BackupException(
    "Room data was restored and committed, but the settings update failed. " +
        "Do NOT re-run the restore blindly - the database is already updated. " +
        "Reconciliation is required. Cause: ${dataStoreCause::class.simpleName}: ${dataStoreCause.message}",
    dataStoreCause
)
