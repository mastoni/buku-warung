package id.skmnetwork.bukuwarung.domain.business

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
    val defaultCategories: List<String>
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
    val defaultCategories: List<String>
) {
    fun hasCapability(capability: BusinessCapability): Boolean = capabilities.contains(capability)
    fun hasActivity(activity: BusinessActivity): Boolean = activities.contains(activity)
}
