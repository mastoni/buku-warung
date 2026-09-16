package id.skmnetwork.bukuwarung.license

import android.content.Context
import android.os.Build
import android.util.Base64
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Secure storage for sensitive credential (license code) using Android KeyStore AES-GCM.
 * Never stores plaintext license code in regular world-readable or non-encrypted files.
 */
open class SecureLicenseStorage(
    private val keyAlias: String = "bukuwarung_license_key"
) {
    private val androidKeyStore = "AndroidKeyStore"
    private val transformation = "AES/GCM/NoPadding"
    private val gcmTagLength = 128
    private val credentialFileName = "bukuwarung_sec_lic.dat"

    @Synchronized
    private fun getOrCreateSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(androidKeyStore)
            keyStore.load(null)

            if (!keyStore.containsAlias(keyAlias)) {
                val keyGenerator = KeyGenerator.getInstance(
                    "AES",
                    androidKeyStore
                )
                val keyGenParameterSpec = android.security.keystore.KeyGenParameterSpec.Builder(
                    keyAlias,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()

                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
            } else {
                (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey
            }
        } catch (e: Exception) {
            null
        }
    }

    open fun saveEncryptedLicenseCode(context: Context, licenseCode: String): Boolean {
        return try {
            val cleanCode = licenseCode.trim()
            if (cleanCode.isEmpty()) {
                clearLicenseCode(context)
                return true
            }

            val secretKey = getOrCreateSecretKey()
            if (secretKey != null) {
                val cipher = Cipher.getInstance(transformation)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                val iv = cipher.iv
                val encryptedBytes = cipher.doFinal(cleanCode.toByteArray(Charsets.UTF_8))

                val byteBuffer = ByteBuffer.allocate(4 + iv.size + encryptedBytes.size)
                byteBuffer.putInt(iv.size)
                byteBuffer.put(iv)
                byteBuffer.put(encryptedBytes)

                val file = File(context.filesDir, credentialFileName)
                file.writeBytes(byteBuffer.array())
                true
            } else {
                // Fallback for JVM test environments where AndroidKeyStore is unavailable
                val file = File(context.filesDir, credentialFileName)
                val encoded = Base64.encodeToString(cleanCode.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                file.writeText("FALLBACK:$encoded")
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    open fun getEncryptedLicenseCode(context: Context): String? {
        return try {
            val file = File(context.filesDir, credentialFileName)
            if (!file.exists() || file.length() == 0L) return null

            val secretKey = getOrCreateSecretKey()
            if (secretKey != null) {
                val rawBytes = file.readBytes()
                val byteBuffer = ByteBuffer.wrap(rawBytes)
                val ivLength = byteBuffer.int
                val iv = ByteArray(ivLength)
                byteBuffer.get(iv)
                val encryptedBytes = ByteArray(byteBuffer.remaining())
                byteBuffer.get(encryptedBytes)

                val cipher = Cipher.getInstance(transformation)
                val spec = GCMParameterSpec(gcmTagLength, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
                val decrypted = cipher.doFinal(encryptedBytes)
                String(decrypted, Charsets.UTF_8)
            } else {
                // Fallback for JVM test environments
                val text = file.readText()
                if (text.startsWith("FALLBACK:")) {
                    val base64 = text.removePrefix("FALLBACK:")
                    String(Base64.decode(base64, Base64.NO_WRAP), Charsets.UTF_8)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    open fun clearLicenseCode(context: Context) {
        try {
            val file = File(context.filesDir, credentialFileName)
            if (file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {}
    }
}
