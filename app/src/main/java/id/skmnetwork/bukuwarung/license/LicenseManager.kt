package id.skmnetwork.bukuwarung.license

import android.content.Context
import id.skmnetwork.bukuwarung.BuildConfig
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
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
 * Authoritative commercial license provider connected to C.2 License Server.
 * Checks persistent local entitlement from UserPreferencesRepository.
 * Validates online against POST /v1/license/activate and POST /v1/license/validate.
 */
class ProductionLicenseProvider(
    private val userPreferencesRepository: UserPreferencesRepository? = null,
    private val enableOwnerTest: Boolean = BuildConfig.ENABLE_OWNER_TEST
) : LicenseProvider {
    override suspend fun checkLicense(): LicenseStatus {
        // 1. Check Commercial License Entitlement in DataStore
        val entitlement = userPreferencesRepository?.getLicenseEntitlement()
        if (entitlement != null && entitlement.isEntitled) {
            return LicenseStatus.ACTIVE
        }

        // 2. Check Owner Test Entitlement (if enabled in build)
        if (enableOwnerTest && userPreferencesRepository?.isOwnerTestActivated() == true) {
            return LicenseStatus.ACTIVE
        }

        return LicenseStatus.UNLICENSED
    }

    override suspend fun getLicenseTier(): LicenseTier {
        return LicenseTier.WARUNG
    }
}

open class LicenseManager(
    private val userPreferencesRepository: UserPreferencesRepository? = null,
    private val provider: LicenseProvider = ProductionLicenseProvider(userPreferencesRepository),
    private val apiClient: LicenseApiClient = LicenseApiClient(),
    private val secureStorage: SecureLicenseStorage = SecureLicenseStorage(),
    private val context: Context? = null,
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

    /**
     * Activates a license using owner email and license code.
     * Binds to this device's persistent UUID.
     */
    suspend fun activateLicense(
        licenseCode: String,
        ownerEmail: String
    ): ActivationResult {
        if (userPreferencesRepository == null) {
            return ActivationResult.ServerError("Preferences repository tidak tersedia")
        }

        val deviceId = userPreferencesRepository.getOrCreateDeviceId()
        val result = apiClient.activateLicense(
            licenseCode = licenseCode,
            ownerEmail = ownerEmail,
            deviceBinding = deviceId
        )

        if (result is ActivationResult.Active) {
            val now = System.currentTimeMillis()
            // Persist encrypted credential if context is available
            context?.let { ctx ->
                secureStorage.saveEncryptedLicenseCode(ctx, licenseCode)
            }
            // Persist entitlement metadata in DataStore
            userPreferencesRepository.saveLicenseEntitlement(
                status = "ACTIVE",
                ownerEmail = ownerEmail.trim().lowercase(),
                activatedAt = now,
                lastValidatedAt = now
            )
            refreshLicense()
        }

        return result
    }

    /**
     * Performs online validation against the license server.
     * Authoritative server validation determines status.
     * Transient network failures do NOT erase local entitlement.
     */
    suspend fun validateOnline(): ValidationResult {
        if (userPreferencesRepository == null) {
            return ValidationResult.ServerError("Preferences repository tidak tersedia")
        }

        val entitlement = userPreferencesRepository.getLicenseEntitlement()
        if (!entitlement.isEntitled) {
            return ValidationResult.Invalid("Aplikasi belum diaktivasi")
        }

        val deviceId = userPreferencesRepository.getOrCreateDeviceId()
        val licenseCode = getStoredLicenseCode()

        if (licenseCode.isBlank()) {
            return ValidationResult.Invalid("Kredensial lisensi tidak ditemukan")
        }

        val result = apiClient.validateLicense(
            licenseCode = licenseCode,
            ownerEmail = entitlement.ownerEmail,
            deviceBinding = deviceId
        )

        when (result) {
            is ValidationResult.Valid -> {
                userPreferencesRepository.updateLicenseStatus("ACTIVE", System.currentTimeMillis())
            }
            is ValidationResult.Revoked,
            is ValidationResult.DeviceMismatch,
            is ValidationResult.EmailMismatch,
            is ValidationResult.Invalid -> {
                // Invalidate local entitlement per authoritative server verdict
                userPreferencesRepository.clearLicenseEntitlement()
                clearStoredLicenseCode()
                refreshLicense()
            }
            is ValidationResult.NetworkError,
            is ValidationResult.ServerError -> {
                // Transient network / server failure: DO NOT erase local entitlement
            }
        }

        return result
    }

    /** Retrieves the stored license code. Overridable for unit testing without Android Context. */
    protected open fun getStoredLicenseCode(): String {
        return context?.let { secureStorage.getEncryptedLicenseCode(it) } ?: ""
    }

    /** Clears the stored license code. Overridable for unit testing without Android Context. */
    protected open fun clearStoredLicenseCode() {
        context?.let { secureStorage.clearLicenseCode(it) }
    }

    suspend fun getEntitlementInfo(): LicenseEntitlementData {
        return userPreferencesRepository?.getLicenseEntitlement() ?: LicenseEntitlementData()
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
