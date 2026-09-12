package id.skmnetwork.bukuwarung.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import id.skmnetwork.bukuwarung.backup.BackupRestoreManager
import id.skmnetwork.bukuwarung.backup.ChecksumMismatchException
import id.skmnetwork.bukuwarung.backup.CorruptedBackupException
import id.skmnetwork.bukuwarung.backup.IncompatibleBackupFormatException
import id.skmnetwork.bukuwarung.backup.IncompatibleSchemaVersionException
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import id.skmnetwork.bukuwarung.data.preferences.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val userSettings: StateFlow<UserSettings> = userPreferencesRepository.userSettings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserSettings()
        )

    private val _backupState = MutableStateFlow<BackupOpState>(BackupOpState.Idle)
    val backupState: StateFlow<BackupOpState> = _backupState.asStateFlow()

    private val _restoreState = MutableStateFlow<RestoreOpState>(RestoreOpState.Idle)
    val restoreState: StateFlow<RestoreOpState> = _restoreState.asStateFlow()

    fun performBackup(spreadsheetId: String = "BUKU_WARUNG_BACKUP") {
        if (_backupState.value is BackupOpState.Loading) return

        _backupState.value = BackupOpState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            val result = backupRestoreManager.performBackup(spreadsheetId)
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = {
                        val now = System.currentTimeMillis()
                        viewModelScope.launch(Dispatchers.IO) {
                            userPreferencesRepository.updateBackupInfo(
                                lastBackupTimestamp = now,
                                backupSpreadsheetId = spreadsheetId
                            )
                        }
                        _backupState.value = BackupOpState.Success("Data warung berhasil dicadangkan ke Google Sheets.")
                    },
                    onFailure = { error ->
                        val friendlyMessage = mapToUserFriendlyErrorMessage(error, isBackup = true)
                        _backupState.value = BackupOpState.Error(friendlyMessage)
                    }
                )
            }
        }
    }

    fun performRestore(spreadsheetId: String = "BUKU_WARUNG_BACKUP") {
        if (_restoreState.value is RestoreOpState.Loading) return

        _restoreState.value = RestoreOpState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            val result = backupRestoreManager.performRestore(spreadsheetId)
            withContext(Dispatchers.Main) {
                result.fold(
                    onSuccess = {
                        _restoreState.value = RestoreOpState.Success("Data warung berhasil dipulihkan dari Google Sheets.")
                    },
                    onFailure = { error ->
                        val friendlyMessage = mapToUserFriendlyErrorMessage(error, isBackup = false)
                        _restoreState.value = RestoreOpState.Error(friendlyMessage)
                    }
                )
            }
        }
    }

    fun resetBackupState() {
        _backupState.value = BackupOpState.Idle
    }

    fun resetRestoreState() {
        _restoreState.value = RestoreOpState.Idle
    }

    private fun mapToUserFriendlyErrorMessage(throwable: Throwable, isBackup: Boolean): String {
        return when (throwable) {
            is ChecksumMismatchException -> "Data cadangan di Google Sheets telah diubah atau rusak. Pemulihan dibatalkan demi keamanan data."
            is IncompatibleBackupFormatException -> "Format cadangan tidak kompatibel dengan versi aplikasi saat ini."
            is IncompatibleSchemaVersionException -> "Versi struktur database cadangan berbeda dengan aplikasi."
            is CorruptedBackupException -> "Data cadangan tidak lengkap atau ada bagian yang hilang."
            is IOException -> if (isBackup) {
                "Gagal mencadangkan data. Periksa koneksi internet Anda atau akses Google Sheets."
            } else {
                "Gagal membaca data dari Google Sheets. Periksa koneksi internet Anda."
            }
            is SecurityException -> "Akses akun Google ditolak atau sesi telah berakhir."
            else -> throwable.localizedMessage?.takeIf { it.isNotBlank() }
                ?: (if (isBackup) "Gagal melakukan pencadangan data." else "Gagal memulihkan data cadangan.")
        }
    }
}

class BackupViewModelFactory(
    private val backupRestoreManager: BackupRestoreManager,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BackupViewModel::class.java)) {
            return BackupViewModel(backupRestoreManager, userPreferencesRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
