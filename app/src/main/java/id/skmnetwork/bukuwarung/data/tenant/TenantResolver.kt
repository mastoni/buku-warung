package id.skmnetwork.bukuwarung.data.tenant

/**
 * Step 3A - tenant resolution.
 *
 * The tenant a repository may read is decided in exactly one place, so it can be unit tested and
 * cannot drift per screen.
 *
 * Why this exists: `AppNavigation` used to build every repository from
 * `userSettings.businessId.ifBlank { "LEGACY_BUSINESS" }` inside an unkeyed `remember { }`, while
 * `userSettings` was collected with `initialValue = UserSettings()`. On the first frame the business
 * id is therefore blank, the repositories bound to `LEGACY_BUSINESS`, and they were never rebuilt
 * afterwards. `reconcileLegacyBusinessIdentity` then moved every `LEGACY_BUSINESS` row onto the real
 * business id, so the database held the merchant's data while every screen read a tenant that was
 * guaranteed to be empty.
 *
 * Rules:
 *  - [resolve] returns the real business id, or null when there is not one yet. There is deliberately
 *    NO fallback tenant: a repository must either know its tenant or not be created at all.
 *  - `LEGACY_BUSINESS` is retained ONLY as a migration/reconciliation sentinel. It is never a runtime
 *    tenant. [isMigrationOnlySentinel] exists so that fact is explicit and testable rather than a
 *    magic string comparison scattered across the code base.
 */
object TenantResolver {

    /** Migration/reconciliation sentinel. Never a runtime tenant. */
    const val MIGRATION_ONLY_SENTINEL: String = "LEGACY_BUSINESS"

    /**
     * The tenant to bind to, or null when the business identity is not available yet.
     *
     * Callers must not create repositories when this returns null; they must wait.
     */
    fun resolve(rawBusinessId: String?): String? =
        rawBusinessId?.trim()?.ifBlank { null }

    /** True when the value is the migration sentinel, which must never be used as a runtime tenant. */
    fun isMigrationOnlySentinel(businessId: String?): Boolean =
        businessId?.trim() == MIGRATION_ONLY_SENTINEL

    /**
     * True when the value is safe to bind a repository to: a real, non-blank tenant that is not the
     * migration sentinel.
     */
    fun isBindable(businessId: String?): Boolean {
        val resolved = resolve(businessId) ?: return false
        return !isMigrationOnlySentinel(resolved)
    }
}
