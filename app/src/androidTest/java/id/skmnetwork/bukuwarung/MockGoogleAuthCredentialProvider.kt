package id.skmnetwork.bukuwarung

import android.app.PendingIntent
import android.content.Intent
import id.skmnetwork.bukuwarung.backup.transport.GoogleAuthConnectionState
import id.skmnetwork.bukuwarung.backup.transport.GoogleAuthCredentialProvider
import id.skmnetwork.bukuwarung.backup.transport.GoogleAuthorizationRequiredException
import id.skmnetwork.bukuwarung.backup.transport.GoogleConsentDeniedException
import java.io.IOException

class MockGoogleAuthCredentialProvider(
    var simulatedToken: String = "MOCK_ACCESS_TOKEN_123",
    var simulateResolutionRequired: Boolean = false,
    var simulateConsentDenied: Boolean = false,
    var simulateNetworkError: Boolean = false,
    var simulate401Error: Boolean = false,
    var simulate403Error: Boolean = false,
    var simulate404Error: Boolean = false,
    var mockPendingIntent: PendingIntent? = null
) : GoogleAuthCredentialProvider {

    var currentEmail: String? = null
    var cachedToken: String? = null

    override suspend fun getAccessToken(): Result<String> {
        if (simulateNetworkError) {
            return Result.failure(IOException("Simulated network failure while connecting to Google"))
        }
        if (simulate401Error) {
            return Result.failure(SecurityException("Google API autentikasi ditolak (HTTP 401)"))
        }
        if (simulate403Error) {
            return Result.failure(SecurityException("Google API otorisasi ditolak (HTTP 403)"))
        }
        if (simulateResolutionRequired) {
            val pi = mockPendingIntent
            return if (pi != null) {
                Result.failure(GoogleAuthorizationRequiredException(pi, currentEmail ?: "mock@test.com"))
            } else {
                Result.failure(SecurityException("Izin Google perlu diperbarui. Hubungkan kembali akun."))
            }
        }
        if (simulateConsentDenied) {
            return Result.failure(GoogleConsentDeniedException())
        }
        cachedToken = simulatedToken
        return Result.success(simulatedToken)
    }

    override suspend fun authorizeAccount(email: String): GoogleAuthConnectionState {
        currentEmail = email
        if (simulateNetworkError) {
            return GoogleAuthConnectionState.Error("Tidak dapat terhubung ke Google. Periksa koneksi internet.")
        }
        if (simulateResolutionRequired && mockPendingIntent != null) {
            return GoogleAuthConnectionState.AuthorizationRequired(mockPendingIntent!!, email)
        }
        if (simulateConsentDenied) {
            return GoogleAuthConnectionState.Error("Izin Google belum diberikan. Silakan hubungkan kembali untuk menggunakan backup Google Sheets.")
        }
        cachedToken = simulatedToken
        return GoogleAuthConnectionState.Connected(
            email = email,
            grantedScopes = listOf(
                "https://www.googleapis.com/auth/spreadsheets",
                "https://www.googleapis.com/auth/drive.file"
            )
        )
    }

    override suspend fun handleAuthorizationResult(email: String, data: Intent?): Result<GoogleAuthConnectionState.Connected> {
        if (simulateConsentDenied) {
            return Result.failure(GoogleConsentDeniedException())
        }
        currentEmail = email
        cachedToken = simulatedToken
        return Result.success(
            GoogleAuthConnectionState.Connected(
                email = email,
                grantedScopes = listOf(
                    "https://www.googleapis.com/auth/spreadsheets",
                    "https://www.googleapis.com/auth/drive.file"
                )
            )
        )
    }

    override fun clearToken() {
        cachedToken = null
        currentEmail = null
    }
}
