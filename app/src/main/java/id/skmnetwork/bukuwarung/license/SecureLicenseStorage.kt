package id.skmnetwork.bukuwarung.license

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.CancellationException
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypted storage for the commercial license code.
 *
 * Gate H.5.0 finding P2-1: the previous implementation swallowed every AndroidKeyStore failure and
 * then wrote the license code as Base64 plaintext under a "FALLBACK:" prefix. The branch was
 * described as a JVM-test convenience but was selected at runtime, so a KeyStore fault on a real
 * device silently downgraded credential storage to readable plaintext.
 *
 * Gate H.5.1 behaviour:
 *  - There is NO plaintext fallback. If a key cannot be obtained, the write fails and the read
 *    returns null. That fails CLOSED: the manager reports [ValidationResult.CredentialUnavailable],
 *    which is transient and therefore preserves the entitlement instead of silently de-licensing
 *    the merchant.
 *  - JVM unit-testability is preserved through explicit seams rather than through a production
 *    downgrade: [obtainSecretKey] is an open method, and the file-level overloads take a plain
 *    [File] instead of a [Context] so a test can supply a temporary directory and a real AES key.
 */
open class SecureLicenseStorage(
    private val keyAlias: String = "bukuwarung_license_key"
) {
    companion object {
        const val credentialFileName = "bukuwarung_sec_lic.dat"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val GCM_IV_BYTES = 12
    }

    /** Where the encrypted credential lives. */
    open fun credentialFile(context: Context): File = File(context.filesDir, credentialFileName)

    /**
     * Obtains the AES key protecting the credential.
     *
     * Returns null when the key is unavailable. Subclasses may override this to inject a
     * deterministic key in JVM unit tests, which is how the storage is tested without weakening the
     * production path.
     */
    protected open fun obtainSecretKey(): SecretKey? = androidKeyStoreSecretKey()

    // ------------------------------------------------------------------ public API (Context based)

    /** Returns true when the code was persisted, false when the secure path was unavailable. */
    open fun saveEncryptedLicenseCode(context: Context, licenseCode: String): Boolean =
        saveEncryptedLicenseCode(credentialFile(context), licenseCode)

    /** Returns the decrypted license code, or null when it is absent or unreadable. */
    open fun getEncryptedLicenseCode(context: Context): String? =
        getEncryptedLicenseCode(credentialFile(context))

    open fun clearLicenseCode(context: Context) = clearLicenseCode(credentialFile(context))

    // ------------------------------------------------------------------ file-level API (test seam)

    /**
     * Fails closed. When no key is available nothing is written and false is returned; a previous
     * plaintext blob is never read back, so a downgrade can never be revived.
     */
    open fun saveEncryptedLicenseCode(file: File, licenseCode: String): Boolean {
        return try {
            val cleanCode = licenseCode.trim()
            if (cleanCode.isEmpty()) {
                file.delete()
                return true
            }
            val secretKey = obtainSecretKey() ?: return false
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(cleanCode.toByteArray(Charsets.UTF_8))
            val payload = ByteArray(iv.size + encrypted.size)
            System.arraycopy(iv, 0, payload, 0, iv.size)
            System.arraycopy(encrypted, 0, payload, iv.size, encrypted.size)
            file.writeBytes(payload)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    open fun getEncryptedLicenseCode(file: File): String? {
        return try {
            if (!file.exists()) return null
            val data = file.readBytes()
            if (data.size <= GCM_IV_BYTES) return null
            val secretKey = obtainSecretKey() ?: return null
            val iv = data.copyOfRange(0, GCM_IV_BYTES)
            val encrypted = data.copyOfRange(GCM_IV_BYTES, data.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            val decrypted = cipher.doFinal(encrypted)
            String(decrypted, Charsets.UTF_8)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    open fun clearLicenseCode(file: File) {
        try {
            file.delete()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best effort
        }
    }

    // ------------------------------------------------------------------ AndroidKeyStore

    /**
     * Builds or loads the AndroidKeyStore key. Returns null on any failure so the caller can fail
     * closed instead of downgrading to plaintext.
     */
    private fun androidKeyStoreSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = keyStore.getKey(keyAlias, null)
            if (existing is SecretKey) return existing

            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            keyGenerator.generateKey()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
}
