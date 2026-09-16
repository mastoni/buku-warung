package id.skmnetwork.bukuwarung.backup.transport

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import id.skmnetwork.bukuwarung.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Production implementation of GoogleAuthCredentialProvider for Android.
 * Uses modern Google Play Services AuthorizationClient for Google Workspace / Sheets / Drive data authorization.
 * Adheres strictly to Least Privilege: spreadsheets + drive.file.
 */
class AndroidGoogleAuthCredentialProvider(
    private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository
) : GoogleAuthCredentialProvider {

    companion object {
        const val SCOPE_SPREADSHEETS = "https://www.googleapis.com/auth/spreadsheets"
        const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
    }

    @Volatile
    private var cachedAccessToken: String? = null

    @Volatile
    private var cachedAccountEmail: String? = null

    override suspend fun authorizeAccount(email: String): GoogleAuthConnectionState = withContext(Dispatchers.IO) {
        if (email.isBlank()) {
            return@withContext GoogleAuthConnectionState.Disconnected
        }

        try {
            val authClient = Identity.getAuthorizationClient(context)
            val requestedScopes = listOf(
                Scope(SCOPE_SPREADSHEETS),
                Scope(SCOPE_DRIVE_FILE)
            )
            val authRequest = AuthorizationRequest.builder()
                .setRequestedScopes(requestedScopes)
                .setAccount(Account(email, "com.google"))
                .build()

            val result: AuthorizationResult = authClient.authorize(authRequest).awaitTask()

            if (result.hasResolution()) {
                val pendingIntent = result.pendingIntent
                if (pendingIntent != null) {
                    return@withContext GoogleAuthConnectionState.AuthorizationRequired(pendingIntent, email)
                }
            }

            val token = result.accessToken
            if (!token.isNullOrBlank()) {
                val grantedScopeUris: List<String> = result.grantedScopes?.map { it.toString() } ?: emptyList()
                val hasSpreadsheetScope = grantedScopeUris.contains(SCOPE_SPREADSHEETS) || grantedScopeUris.any { it.contains("spreadsheets") }
                if (hasSpreadsheetScope || grantedScopeUris.isNotEmpty()) {
                    cachedAccessToken = token
                    cachedAccountEmail = email
                    return@withContext GoogleAuthConnectionState.Connected(email, grantedScopeUris)
                }
            }

            val pendingIntent = result.pendingIntent
            if (pendingIntent != null) {
                GoogleAuthConnectionState.AuthorizationRequired(pendingIntent, email)
            } else {
                GoogleAuthConnectionState.Error("Otorisasi Google belum lengkap. Silakan hubungkan ulang akun.")
            }
        } catch (e: ApiException) {
            if (e.statusCode == CommonStatusCodes.CANCELED || e.statusCode == 16) {
                GoogleAuthConnectionState.Error("Izin Google belum diberikan. Silakan hubungkan kembali untuk menggunakan backup Google Sheets.")
            } else if (e.status.hasResolution()) {
                val pendingIntent = e.status.resolution
                if (pendingIntent != null) {
                    GoogleAuthConnectionState.AuthorizationRequired(pendingIntent, email)
                } else {
                    GoogleAuthConnectionState.Error("Izin Google perlu diperbarui.")
                }
            } else {
                GoogleAuthConnectionState.Error("Gagal menghubungkan akun Google: ${e.localizedMessage ?: "Status ${e.statusCode}"}")
            }
        } catch (e: IOException) {
            GoogleAuthConnectionState.Error("Tidak dapat terhubung ke Google. Periksa koneksi internet.")
        } catch (e: Exception) {
            GoogleAuthConnectionState.Error("Gagal otorisasi Google: ${e.localizedMessage ?: "Error tidak diketahui"}")
        }
    }

    override suspend fun handleAuthorizationResult(email: String, data: Intent?): Result<GoogleAuthConnectionState.Connected> = withContext(Dispatchers.IO) {
        try {
            val authClient = Identity.getAuthorizationClient(context)
            val result = authClient.getAuthorizationResultFromIntent(data)
            val token = result.accessToken

            if (token.isNullOrBlank()) {
                // If token is not present directly in intent data, refresh silently since user gave consent
                val requestedScopes = listOf(
                    Scope(SCOPE_SPREADSHEETS),
                    Scope(SCOPE_DRIVE_FILE)
                )
                val authRequest = AuthorizationRequest.builder()
                    .setRequestedScopes(requestedScopes)
                    .setAccount(Account(email, "com.google"))
                    .build()
                val refreshedResult = authClient.authorize(authRequest).awaitTask()
                val refreshedToken = refreshedResult.accessToken
                if (!refreshedToken.isNullOrBlank()) {
                    val grantedUris: List<String> = refreshedResult.grantedScopes?.map { it.toString() } ?: emptyList()
                    cachedAccessToken = refreshedToken
                    cachedAccountEmail = email
                    return@withContext Result.success(GoogleAuthConnectionState.Connected(email, grantedUris))
                }
                return@withContext Result.failure(GoogleConsentDeniedException())
            }

            val grantedUris: List<String> = result.grantedScopes?.map { it.toString() } ?: emptyList()
            cachedAccessToken = token
            cachedAccountEmail = email
            Result.success(GoogleAuthConnectionState.Connected(email, grantedUris))
        } catch (e: Exception) {
            Result.failure(GoogleConsentDeniedException())
        }
    }

    override suspend fun getAccessToken(): Result<String> = withContext(Dispatchers.IO) {
        val userSettings = userPreferencesRepository.userSettings.first()
        val email = userSettings.googleAccountEmail.trim()

        if (email.isBlank()) {
            return@withContext Result.failure(
                SecurityException("Akun Google belum terhubung. Silakan hubungkan akun Google terlebih dahulu.")
            )
        }

        if (!cachedAccessToken.isNullOrBlank() && cachedAccountEmail == email) {
            return@withContext Result.success(cachedAccessToken!!)
        }

        try {
            val authClient = Identity.getAuthorizationClient(context)
            val requestedScopes = listOf(
                Scope(SCOPE_SPREADSHEETS),
                Scope(SCOPE_DRIVE_FILE)
            )
            val authRequest = AuthorizationRequest.builder()
                .setRequestedScopes(requestedScopes)
                .setAccount(Account(email, "com.google"))
                .build()

            val result = authClient.authorize(authRequest).awaitTask()

            if (result.hasResolution()) {
                val pendingIntent = result.pendingIntent
                if (pendingIntent != null) {
                    return@withContext Result.failure(
                        GoogleAuthorizationRequiredException(pendingIntent, email)
                    )
                }
            }

            val token = result.accessToken
            if (token.isNullOrBlank()) {
                val pendingIntent = result.pendingIntent
                if (pendingIntent != null) {
                    return@withContext Result.failure(GoogleAuthorizationRequiredException(pendingIntent, email))
                }
                return@withContext Result.failure(SecurityException("Izin Google perlu diperbarui. Hubungkan kembali akun."))
            }

            cachedAccessToken = token
            cachedAccountEmail = email
            Result.success(token)
        } catch (e: ApiException) {
            if (e.status.hasResolution()) {
                val pendingIntent = e.status.resolution
                if (pendingIntent != null) {
                    return@withContext Result.failure(GoogleAuthorizationRequiredException(pendingIntent, email))
                }
            }
            Result.failure(SecurityException("Izin Google perlu diperbarui. Hubungkan kembali akun."))
        } catch (e: IOException) {
            Result.failure(IOException("Tidak dapat terhubung ke Google. Periksa koneksi internet.", e))
        } catch (e: Exception) {
            Result.failure(SecurityException("Akses Google ditolak atau sesi telah berakhir.", e))
        }
    }

    override fun clearToken() {
        cachedAccessToken = null
        cachedAccountEmail = null
    }
}

/**
 * Extension to await Google Tasks in Coroutines without external dependencies.
 */
private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result ->
        cont.resume(result)
    }
    addOnFailureListener { exception ->
        cont.resumeWithException(exception)
    }
    addOnCanceledListener {
        cont.cancel()
    }
}

