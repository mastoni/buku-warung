package id.skmnetwork.bukuwarung.license

import id.skmnetwork.bukuwarung.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class LicenseTier(val label: String) {
    WARUNG("Buku Warung (Satu Perangkat)"),
    WARUNG_PRO("Buku Warung Pro (Multi Perangkat)")
}

enum class LicenseStatus {
    UNKNOWN,
    CHECKING,
    ACTIVE,
    UNLICENSED,
    ERROR
}

interface LicenseProvider {
    suspend fun checkLicense(): LicenseStatus
    suspend fun getLicenseTier(): LicenseTier
}

/**
 * DebugLicenseProvider:
 * Bypasses license check ONLY when isDebug is explicitly true (development/debug build).
 * In release builds or when isDebug is false, it returns UNLICENSED.
 */
class DebugLicenseProvider(
    private val isDebug: Boolean = BuildConfig.DEBUG
) : LicenseProvider {
    override suspend fun checkLicense(): LicenseStatus {
        return if (isDebug) {
            LicenseStatus.ACTIVE
        } else {
            LicenseStatus.UNLICENSED
        }
    }

    override suspend fun getLicenseTier(): LicenseTier {
        return LicenseTier.WARUNG
    }
}

/**
 * ProductionLicenseProvider:
 * Production abstraction for Google Play Billing / offline hardware entitlement / Owner Testing.
 * Checks persistent Owner/Internal Testing entitlement from UserPreferencesRepository when available.
 * Default is UNLICENSED until verified by billing service or activated for owner testing.
 */
class ProductionLicenseProvider(
    private val userPreferencesRepository: id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository? = null,
    private val enableOwnerTest: Boolean = BuildConfig.ENABLE_OWNER_TEST
) : LicenseProvider {
    override suspend fun checkLicense(): LicenseStatus {
        if (enableOwnerTest && userPreferencesRepository?.isOwnerTestActivated() == true) {
            return LicenseStatus.ACTIVE
        }
        return LicenseStatus.UNLICENSED
    }

    override suspend fun getLicenseTier(): LicenseTier {
        return LicenseTier.WARUNG
    }
}

class LicenseManager(
    private val userPreferencesRepository: id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository? = null,
    private val provider: LicenseProvider = ProductionLicenseProvider(userPreferencesRepository),
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    // Production default is CHECKING / UNLICENSED - NEVER HARDCODED ACTIVE
    private val _licenseStatus = MutableStateFlow(LicenseStatus.CHECKING)
    val licenseStatus: StateFlow<LicenseStatus> = _licenseStatus.asStateFlow()

    private val _licenseTier = MutableStateFlow(LicenseTier.WARUNG)
    val licenseTier: StateFlow<LicenseTier> = _licenseTier.asStateFlow()

    init {
        scope.launch {
            refreshLicense()
        }
    }

    suspend fun refreshLicense() {
        _licenseStatus.value = LicenseStatus.CHECKING
        try {
            val status = provider.checkLicense()
            val tier = provider.getLicenseTier()
            _licenseTier.value = tier
            _licenseStatus.value = status
        } catch (e: Exception) {
            _licenseStatus.value = LicenseStatus.ERROR
        }
    }

    suspend fun activateOwnerTest(): Boolean {
        if (!BuildConfig.ENABLE_OWNER_TEST) {
            return false
        }
        userPreferencesRepository?.setOwnerTestActivated(true)
        refreshLicense()
        return _licenseStatus.value == LicenseStatus.ACTIVE
    }

    fun isProFeatureEnabled(featureName: String): Boolean {
        return _licenseStatus.value == LicenseStatus.ACTIVE && _licenseTier.value == LicenseTier.WARUNG_PRO
    }
}
