package id.skmnetwork.bukuwarung.backup.transport

/**
 * Authentication credential provider for Google API access.
 * Decouples OAuth token retrieval from network transport.
 */
interface GoogleAuthCredentialProvider {
    suspend fun getAccessToken(): Result<String>
}
