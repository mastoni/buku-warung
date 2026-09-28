package id.skmnetwork.bukuwarung.domain.business

import id.skmnetwork.bukuwarung.data.local.entity.ItemType

/**
 * Gate G3 — Business Preset Model
 * Standard preset template for a specific BusinessType.
 */
data class BusinessPreset(
    val businessType: BusinessType,
    val defaultActivities: Set<BusinessActivity>,
    val defaultCapabilities: Set<BusinessCapability>,
    val terminology: BusinessTerminology,
    val preferredUnits: List<String>,
    val defaultCategories: List<String>,
    /**
     * Step 2 (F) - the product type a new product starts with for this business.
     *
     * This is a STARTING POINT, never a restriction. A warung kelontong can still sell pulsa, and a
     * bengkel can still sell parts, so the merchant keeps the final say on the product form.
     */
    val suggestedItemType: ItemType = ItemType.PHYSICAL
)

/**
 * Gate G3 — Resolved Business Profile
 * Immutable outcome of resolving primary business type and secondary activities.
 */
data class ResolvedBusinessProfile(
    val category: BusinessCategory,
    val businessType: BusinessType,
    val activities: Set<BusinessActivity>,
    val capabilities: Set<BusinessCapability>,
    val terminology: BusinessTerminology,
    val preferredUnits: List<String>,
    val defaultCategories: List<String>,
    /** Step 2 (F) - see [BusinessPreset.suggestedItemType]. */
    val suggestedItemType: ItemType = ItemType.PHYSICAL
) {
    fun hasCapability(capability: BusinessCapability): Boolean = capabilities.contains(capability)
    fun hasActivity(activity: BusinessActivity): Boolean = activities.contains(activity)
}
