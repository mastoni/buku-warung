package id.skmnetwork.bukuwarung.backup.transport

import android.app.PendingIntent
import android.content.Intent

sealed class GoogleAuthConnectionState {
    object Disconnected : GoogleAuthConnectionState()
    object Authorizing : GoogleAuthConnectionState()
    data class Connected(val email: String, val grantedScopes: List<String> = emptyList()) : GoogleAuthConnectionState()
    data class AuthorizationRequired(val resolutionIntent: PendingIntent, val email: String) : GoogleAuthConnectionState()
    data class Error(val message: String) : GoogleAuthConnectionState()
}

class GoogleAuthorizationRequiredException(
    val resolutionIntent: PendingIntent,
    val email: String,
    message: String = "Izin Google perlu diperbarui. Hubungkan kembali akun."
) : SecurityException(message)

class GoogleConsentDeniedException(
    message: String = "Izin Google belum diberikan. Silakan hubungkan kembali untuk menggunakan backup Google Sheets."
) : SecurityException(message)

/**
 * Modern Authorization & Credential provider for Google Workspace / Sheets API access.
 * Decouples OAuth token retrieval, authorization resolution, and network transport.
 */
interface GoogleAuthCredentialProvider {
    suspend fun getAccessToken(): Result<String>
    suspend fun authorizeAccount(email: String): GoogleAuthConnectionState
    suspend fun handleAuthorizationResult(email: String, data: Intent?): Result<GoogleAuthConnectionState.Connected>
    fun clearToken()
}

