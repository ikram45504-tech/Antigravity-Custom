package id.vern.antigravity.oauth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.system.Os
import android.util.Log
import java.io.File
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Shared Android KeyStore AES-256-GCM helper and owner-only file permission utility
 * for credential vaults stored under files/.gemini/.
 */
object EncryptedStoreCrypto {
    private const val TAG = "EncryptedStoreCrypto"
    private const val ENC_PREFIX = "enc:v1:"

    fun isEncrypted(raw: String): Boolean = raw.trimStart().startsWith(ENC_PREFIX)

    private fun getOrCreateAesKey(alias: String): SecretKey? {
        return runCatching {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (ks.getKey(alias, null) as? SecretKey)?.let { return@runCatching it }
            val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            keyGen.init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            keyGen.generateKey()
        }.onFailure { err ->
            Log.e(TAG, "AndroidKeyStore key init failed for alias=$alias: ${err.message}")
        }.getOrNull()
    }

    fun encryptPayload(alias: String, plainJson: String): String {
        val key = getOrCreateAesKey(alias)
        if (key == null) {
            Log.w(TAG, "Falling back to 0600 plaintext storage for alias=$alias because KeyStore key is unavailable")
            return plainJson
        }
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val ivB64 = Base64.getEncoder().encodeToString(cipher.iv)
            val ctB64 = Base64.getEncoder().encodeToString(
                cipher.doFinal(plainJson.toByteArray(Charsets.UTF_8))
            )
            "$ENC_PREFIX$ivB64:$ctB64"
        }.onFailure { err ->
            Log.w(TAG, "AES/GCM encryption failed for alias=$alias, falling back to 0600 plaintext: ${err.message}")
        }.getOrElse { plainJson }
    }

    fun decryptPayload(alias: String, raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (!trimmed.startsWith(ENC_PREFIX)) {
            return trimmed
        }
        val body = trimmed.removePrefix(ENC_PREFIX)
        val sep = body.indexOf(':')
        if (sep <= 0) return null
        val key = getOrCreateAesKey(alias) ?: return null
        return runCatching {
            val iv = Base64.getDecoder().decode(body.substring(0, sep))
            val ct = Base64.getDecoder().decode(body.substring(sep + 1))
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        }.getOrNull()
    }

    fun applyOwnerOnlyPermissions(file: File) {
        file.setReadable(false, false)
        file.setReadable(true, true)
        file.setWritable(false, false)
        file.setWritable(true, true)
        try {
            Os.chmod(file.absolutePath, 384) // 0600
        } catch (_: Exception) {}
    }
}
