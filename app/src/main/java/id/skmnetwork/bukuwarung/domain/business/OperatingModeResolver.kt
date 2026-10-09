package id.skmnetwork.bukuwarung.domain.business

/**
 * Release 3 — Operating Mode Resolver
 * Deterministically computes the OperatingMode from BusinessType and active BusinessActivities.
 *
 * Guarantees:
 * 1. Safe fallbacks for empty, legacy, or invalid inputs.
 * 2. PPOB is opt-in and global across all business types.
 * 3. PPOB_ONLY is resolved when digital activity is present without physical goods or service labor.
 * 4. BusinessType identity is never altered by activity changes.
 */
object OperatingModeResolver {

    /**
     * Resolves OperatingMode from strongly-typed BusinessType and Set<BusinessActivity>.
     */
    fun resolve(
        businessType: BusinessType?,
        activities: Set<BusinessActivity>?
    ): OperatingMode {
        val effectiveType = businessType ?: BusinessTaxonomyRegistry.DEFAULT_BUSINESS_TYPE
        val effectiveActivities = if (activities.isNullOrEmpty()) {
            BusinessTaxonomyRegistry.getPreset(effectiveType).defaultActivities
        } else {
            activities
        }

        val hasGoods = effectiveActivities.contains(BusinessActivity.ACTIVITY_GOODS_SELLING) ||
                       effectiveActivities.contains(BusinessActivity.ACTIVITY_WHOLESALE_PURCHASE) ||
                       effectiveActivities.contains(BusinessActivity.ACTIVITY_RAW_MATERIALS)
        val hasService = effectiveActivities.contains(BusinessActivity.ACTIVITY_SERVICE_LABOR)
        val hasDigital = effectiveActivities.contains(BusinessActivity.ACTIVITY_DIGITAL_VOUCHER)

        return when {
            // PPOB Only: digital active, no goods and no service
            hasDigital && !hasGoods && !hasService -> OperatingMode.PPOB_ONLY

            // Hybrid: digital combined with goods or service
            hasDigital && (hasGoods || hasService) -> OperatingMode.HYBRID_MIXED

            // Hybrid: goods combined with service (no digital)
            hasGoods && hasService -> OperatingMode.HYBRID_MIXED

            // Service Primary: only service active
            hasService && !hasGoods -> OperatingMode.SERVICE_PRIMARY

            // Retail Primary: default for goods selling or general retail fallback
            else -> OperatingMode.RETAIL_PRIMARY
        }
    }

    /**
     * Resolves OperatingMode from raw string identifiers (e.g. from DataStore/UserPreferences).
     */
    fun resolve(
        primaryType: String?,
        secondaryActivities: Set<String>? = emptySet()
    ): OperatingMode {
        val resolvedType = if (!primaryType.isNullOrBlank()) {
            BusinessType.fromId(primaryType) ?: BusinessTaxonomyRegistry.DEFAULT_BUSINESS_TYPE
        } else {
            BusinessTaxonomyRegistry.DEFAULT_BUSINESS_TYPE
        }

        val basePreset = BusinessTaxonomyRegistry.getPreset(resolvedType)

        val parsedSecondary = secondaryActivities
            ?.mapNotNull { BusinessActivity.fromId(it) }
            ?.toSet()
            ?: emptySet()

        // If secondaryActivities was passed as an explicit set, we aggregate with preset defaults
        val aggregatedActivities = if (secondaryActivities == null || secondaryActivities.isEmpty()) {
            basePreset.defaultActivities
        } else {
            basePreset.defaultActivities + parsedSecondary
        }

        return resolve(resolvedType, aggregatedActivities)
    }

    /**
     * Resolves OperatingMode directly from an explicit set of activities (e.g. customized in settings).
     */
    fun resolveFromExactActivities(
        businessType: BusinessType?,
        exactActivities: Set<BusinessActivity>
    ): OperatingMode {
        val effectiveType = businessType ?: BusinessTaxonomyRegistry.DEFAULT_BUSINESS_TYPE
        if (exactActivities.isEmpty()) {
            return resolve(effectiveType, null)
        }
        return resolve(effectiveType, exactActivities)
    }
}
