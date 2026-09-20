package id.skmnetwork.bukuwarung.ui.settings

import android.app.Activity
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import id.skmnetwork.bukuwarung.backup.BackupRestoreManager
import id.skmnetwork.bukuwarung.backup.BusinessIdMismatchException
import id.skmnetwork.bukuwarung.backup.ChecksumMismatchException
import id.skmnetwork.bukuwarung.backup.CorruptedBackupException
import id.skmnetwork.bukuwarung.backup.IncompatibleBackupFormatException
import id.skmnetwork.bukuwarung.backup.IncompatibleSchemaVersionException
import id.skmnetwork.bukuwarung.backup.SpreadsheetNotFoundException
import id.skmnetwork.bukuwarung.backup.transport.GoogleAuthConnectionState
import id.skmnetwork.bukuwarung.backup.transport.GoogleAuthCredentialProvider
import id.skmnetwork.bukuwarung.backup.transport.GoogleAuthorizationRequiredException
import id.skmnetwork.bukuwarung.backup.transport.GoogleConsentDeniedException
import id.skmnetwork.bukuwarung.backup.transport.GoogleSheetsApiTransport
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import id.skmnetwork.bukuwarung.data.preferences.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

sealed class BackupOpState {
    object Idle : BackupOpState()
    object Loading : BackupOpState()
    data class Success(val message: String) : BackupOpState()
    data class Error(val message: String) : BackupOpState()
}

sealed class RestoreOpState {
    object Idle : RestoreOpState()
    object Loading : RestoreOpState()
    data class Success(val message: String) : RestoreOpState()
    data class Error(val message: String) : RestoreOpState()
}

class BackupViewModel(
    private val backupRestoreManager: BackupRestoreManager,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val authProvider: GoogleAuthCredentialProvider? = null
) : ViewModel() {

    val userSettings: StateFlow<UserSettings> = userPreferencesRepository.userSettings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserSettings()
        )

    private val _googleAuthState = MutableStateFlow<GoogleAuthConnectionState>(GoogleAuthConnectionState.Disconnected)
    val googleAuthState: StateFlow<GoogleAuthConnectionState> = _googleAuthState.asStateFlow()

    private val _backupState = MutableStateFlow<BackupOpState>(BackupOpState.Idle)
    val backupState: StateFlow<BackupOpState> = _backupState.asStateFlow()

    private val _restoreState = MutableStateFlow<RestoreOpState>(RestoreOpState.Idle)
    val restoreState: StateFlow<RestoreOpState> = _restoreState.asStateFlow()

    init {
        viewModelScope.launch {
            userPreferencesRepository.userSettings.collect { settings ->
                val email = settings.googleAccountEmail.trim()
                if (email.isNotBlank()) {
                    if (_googleAuthState.value !is GoogleAuthConnectionState.Connected) {
                        _googleAuthState.value = GoogleAuthConnectionState.Connected(email)
                    }
                } else {
                    _googleAuthState.value = GoogleAuthConnectionState.Disconnected
                }
            }
        }
    }

    fun initiateAccountAuthorization(email: String) {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isBlank()) {
            _googleAuthState.value = GoogleAuthConnectionState.Disconnected
            return
        }

        val provider = authProvider
            ?: (backupRestoreManager.transport as? GoogleSheetsApiTransport)?.authProvider

        if (provider == null) {
            viewModelScope.launch {
                userPreferencesRepository.setGoogleAccount(trimmedEmail)
                _googleAuthState.value = GoogleAuthConnectionState.Connected(trimmedEmail)
            }
            return
        }

        _googleAuthState.value = GoogleAuthConnectionState.Authorizing
        viewModelScope.launch {
            val state = provider.authorizeAccount(trimmedEmail)
            if (state is GoogleAuthConnectionState.Connected) {
                userPreferencesRepository.setGoogleAccount(trimmedEmail)
            }
            _googleAuthState.value = state
        }
    }

    fun onAuthorizationResolutionResult(email: String, resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) {
            _googleAuthState.value = GoogleAuthConnectionState.Error(
                "Izin Google belum diberikan. Silakan hubungkan kembali untuk menggunakan backup Google Sheets."
            )
            return
        }

        val provider = authProvider
            ?: (backupRestoreManager.transport as? GoogleSheetsApiTransport)?.authProvider

        if (provider == null) {
            viewModelScope.launch {
                userPreferencesRepository.setGoogleAccount(email)
                _googleAuthState.value = GoogleAuthConnectionState.Connected(email)
            }
            return
        }

        _googleAuthState.value = GoogleAuthConnectionState.Authorizing
        viewModelScope.launch {
            val result = provider.handleAuthorizationResult(email, data)
            if (result.isSuccess) {
                userPreferencesRepository.setGoogleAccount(email)
                _googleAuthState.value = result.getOrThrow()
            } else {
                val error = result.exceptionOrNull()
                _googleAuthState.value = GoogleAuthConnectionState.Error(
                    error?.localizedMessage
                        ?: "Izin Google belum diberikan. Silakan hubungkan kembali untuk menggunakan backup Google Sheets."
                )
            }
        }
    }

    fun connectGoogleAccount(email: String) {
        initiateAccountAuthorization(email)
    }

    fun disconnectGoogleAccount() {
        viewModelScope.launch {
            authProvider?.clearToken()
            (backupRestoreManager.transport as? GoogleSheetsApiTransport)?.authProvider?.clearToken()
            userPreferencesRepository.clearGoogleAccount()
            _googleAuthState.value = GoogleAuthConnectionState.Disconnected
            _backupState.value = BackupOpState.Idle
            _restoreState.value = RestoreOpState.Idle
        }
    }

    fun performBackup(customSpreadsheetId: String? = null) {
        if (_backupState.value is BackupOpState.Loading) return

        _backupState.value = BackupOpState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            val settings = userPreferencesRepository.userSettings.first()
            val email = settings.googleAccountEmail.trim()

            if (email.isBlank()) {
                withContext(Dispatchers.Main) {
                    _backupState.value = BackupOpState.Error("Hubungkan akun Google terlebih dahulu untuk mencadangkan data.")
                }
                return@launch
            }

            // Determine target spreadsheet ID & Title
            val shopName = settings.shopName.ifBlank { "Warung Saya" }
            val spreadsheetTitle = "Buku Warung - $shopName"
            var targetSpreadsheetId = customSpreadsheetId?.takeIf { it.isNotBlank() }
                ?: settings.backupSpreadsheetId.takeIf { it.isNotBlank() }

            if (targetSpreadsheetId.isNullOrBlank()) {
                // Auto-create new spreadsheet on Google Drive / Transport
                val createResult = backupRestoreManager.ensureSpreadsheetCreated(spreadsheetTitle)
                if (createResult.isFailure) {
                    val ex = createResult.exceptionOrNull()
                    if (ex is GoogleAuthorizationRequiredException) {
                        withContext(Dispatchers.Main) {
                            _googleAuthState.value = GoogleAuthConnectionState.AuthorizationRequired(ex.resolutionIntent, ex.email)
                            _backupState.value = BackupOpState.Error("Izin Google perlu diperbarui. Hubungkan kembali akun.")
                        }
                        return@launch
                    }
                    val msg = mapToUserFriendlyErrorMessage(ex ?: IOException("Gagal membuat spreadsheet baru di Google Drive"), isBackup = true)
                    withContext(Dispatchers.Main) {
                        _backupState.value = BackupOpState.Error(msg)
                    }
                    return@launch
                }
                targetSpreadsheetId = createResult.getOrThrow()
            }

            var result = backupRestoreManager.performBackup(targetSpreadsheetId)

            // Safe 404 auto-healing: If the target spreadsheet is not found (HTTP 404),
            // auto-create a new spreadsheet with all 19 canonical tabs and retry backup.
            val firstError = result.exceptionOrNull()
            if (result.isFailure && (firstError is SpreadsheetNotFoundException || firstError?.cause is SpreadsheetNotFoundException)) {
                val createResult = backupRestoreManager.ensureSpreadsheetCreated(spreadsheetTitle)
                if (createResult.isSuccess) {
                    val newSpreadsheetId = createResult.getOrThrow()
                    val retryResult = backupRestoreManager.performBackup(newSpreadsheetId)
                    if (retryResult.isSuccess) {
                        targetSpreadsheetId = newSpreadsheetId
                        result = retryResult
                    } else {
                        result = retryResult
                    }
                }
            }

            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = {
                        val now = System.currentTimeMillis()
                        viewModelScope.launch(Dispatchers.IO) {
                            userPreferencesRepository.updateBackupInfo(
                                lastBackupTimestamp = now,
                                backupSpreadsheetId = targetSpreadsheetId,
                                backupSpreadsheetName = spreadsheetTitle,
                                googleAccountEmail = email
                            )
                        }
                        _backupState.value = BackupOpState.Success("Data warung berhasil dicadangkan ke Google Sheets.")
                    },
                    onFailure = { error ->
                        if (error is GoogleAuthorizationRequiredException) {
                            _googleAuthState.value = GoogleAuthConnectionState.AuthorizationRequired(error.resolutionIntent, error.email)
                        }
                        val friendlyMessage = mapToUserFriendlyErrorMessage(error, isBackup = true)
                        _backupState.value = BackupOpState.Error(friendlyMessage)
                    }
                )
            }
        }
    }

    fun performRestore(customSpreadsheetId: String? = null) {
        if (_restoreState.value is RestoreOpState.Loading) return

        _restoreState.value = RestoreOpState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            val settings = userPreferencesRepository.userSettings.first()
            val email = settings.googleAccountEmail.trim()
            val expectedBusinessId = settings.businessId.ifBlank { null }

            if (email.isBlank()) {
                withContext(Dispatchers.Main) {
                    _restoreState.value = RestoreOpState.Error("Hubungkan akun Google terlebih dahulu untuk memulihkan data.")
                }
                return@launch
            }

            val targetSpreadsheetId = customSpreadsheetId?.takeIf { it.isNotBlank() }
                ?: settings.backupSpreadsheetId.takeIf { it.isNotBlank() }

            if (targetSpreadsheetId.isNullOrBlank()) {
                withContext(Dispatchers.Main) {
                    _restoreState.value = RestoreOpState.Error("ID Spreadsheet cadangan belum tersedia. Lakukan pencadangan terlebih dahulu.")
                }
                return@launch
            }

            val result = backupRestoreManager.performRestore(targetSpreadsheetId, expectedBusinessId)
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = {
                        _restoreState.value = RestoreOpState.Success("Data warung berhasil dipulihkan dari Google Sheets.")
                    },
                    onFailure = { error ->
                        if (error is GoogleAuthorizationRequiredException) {
                            _googleAuthState.value = GoogleAuthConnectionState.AuthorizationRequired(error.resolutionIntent, error.email)
                        }
                        val friendlyMessage = mapToUserFriendlyErrorMessage(error, isBackup = false)
                        _restoreState.value = RestoreOpState.Error(friendlyMessage)
                    }
                )
            }
        }
    }

    fun getSpreadsheetWebUrl(spreadsheetId: String): String {
        return "https://docs.google.com/spreadsheets/d/$spreadsheetId"
    }

    fun resetBackupState() {
        _backupState.value = BackupOpState.Idle
    }

    fun resetRestoreState() {
        _restoreState.value = RestoreOpState.Idle
    }

    private fun mapToUserFriendlyErrorMessage(throwable: Throwable, isBackup: Boolean): String {
        return when (throwable) {
            is SpreadsheetNotFoundException -> "Spreadsheet cadangan tidak ditemukan di Google Drive."
            is BusinessIdMismatchException -> "Cadangan ini berasal dari usaha yang berbeda. Pemulihan dibatalkan untuk melindungi data usaha Anda."
            is GoogleConsentDeniedException -> "Izin Google belum diberikan. Silakan hubungkan kembali untuk menggunakan backup Google Sheets."
            is GoogleAuthorizationRequiredException -> "Izin Google perlu diperbarui. Hubungkan kembali akun."
            is ChecksumMismatchException -> "Data cadangan di Google Sheets telah diubah atau rusak. Pemulihan dibatalkan demi keamanan data."
            is IncompatibleBackupFormatException -> "Format cadangan tidak kompatibel dengan versi aplikasi saat ini."
            is IncompatibleSchemaVersionException -> "Versi struktur database cadangan berbeda dengan aplikasi."
            is CorruptedBackupException -> "Data cadangan tidak lengkap atau ada bagian yang hilang."
            is IOException -> {
                val msg = throwable.message ?: ""
                if (msg.contains("404")) {
                    "Spreadsheet cadangan tidak ditemukan di Google Drive."
                } else if (msg.contains("Tidak dapat terhubung") || msg.contains("koneksi") || msg.contains("network", ignoreCase = true)) {
                    "Tidak dapat terhubung ke Google. Periksa koneksi internet."
                } else if (isBackup) {
                    "Gagal mencadangkan data. Periksa koneksi internet Anda atau akses Google Sheets."
                } else {
                    "Gagal membaca data dari Google Sheets. Periksa koneksi internet Anda."
                }
            }
            is SecurityException -> {
                val msg = throwable.message ?: ""
                if (msg.contains("401") || msg.contains("diperbarui") || msg.contains("sesi")) {
                    "Izin Google perlu diperbarui. Hubungkan kembali akun."
                } else if (msg.contains("403") || msg.contains("ditolak") || msg.contains("izin")) {
                    "Akses Google Sheets ditolak. Pastikan izin aplikasi telah disetujui di akun Google Anda."
                } else {
                    "Akses akun Google ditolak atau sesi telah berakhir."
                }
            }
            else -> throwable.localizedMessage?.takeIf { it.isNotBlank() }
                ?: (if (isBackup) "Gagal melakukan pencadangan data." else "Gagal memulihkan data cadangan.")
        }
    }
}

class BackupViewModelFactory(
    private val backupRestoreManager: BackupRestoreManager,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val authProvider: GoogleAuthCredentialProvider? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BackupViewModel::class.java)) {
            return BackupViewModel(backupRestoreManager, userPreferencesRepository, authProvider) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}


