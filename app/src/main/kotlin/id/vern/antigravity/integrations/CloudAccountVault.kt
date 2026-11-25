package id.vern.antigravity.integrations

import android.content.Context
import id.vern.antigravity.oauth.EncryptedStoreCrypto
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Encrypted multi-account vault for Vercel, Supabase, and Cloudflare API tokens.
 * Accounts are stored encrypted at rest via Android KeyStore AES-256-GCM.
 */
object CloudAccountVault {
    private const val STORE_FILE_NAME = "cloud_accounts.json"
    private const val KEYSTORE_ALIAS = "antigravity_cloud_accounts_aes_v1"
    private val lock = Any()

    private fun getStoreFile(context: Context): File {
        val dir = File(context.filesDir, ".gemini")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, STORE_FILE_NAME)
    }

    internal fun parseAccountsJson(jsonText: String): List<CloudAccount> {
        val trimmed = jsonText.trim()
        if (trimmed.isEmpty()) return emptyList()
        val obj = runCatching { JSONObject(trimmed) }.getOrNull() ?: return emptyList()
        val arr = obj.optJSONArray("accounts") ?: return emptyList()
        val result = mutableListOf<CloudAccount>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val id = item.optString("id").trim()
            val providerStr = item.optString("provider").trim()
            val provider = runCatching { CloudProvider.valueOf(providerStr) }.getOrNull() ?: continue
            val token = item.optString("token").trim()
            if (id.isBlank() || token.isBlank()) continue
            result.add(
                CloudAccount(
                    id = id,
                    provider = provider,
                    label = item.optString("label").ifBlank { provider.displayName },
                    handleOrEmail = item.optString("handleOrEmail"),
                    token = token,
                    defaultScopeId = item.optString("defaultScopeId")
                )
            )
        }
        return result
    }

    internal fun serializeAccountsJson(accounts: List<CloudAccount>): String {
        val arr = JSONArray()
        accounts.forEach { acc ->
            arr.put(
                JSONObject().apply {
                    put("id", acc.id)
                    put("provider", acc.provider.name)
                    put("label", acc.label)
                    put("handleOrEmail", acc.handleOrEmail)
                    put("token", acc.token)
                    put("defaultScopeId", acc.defaultScopeId)
                }
            )
        }
        return JSONObject().apply {
            put("accounts", arr)
        }.toString()
    }

    private fun readAllInternal(context: Context): List<CloudAccount> {
        val file = getStoreFile(context)
        if (!file.isFile || file.length() == 0L) return emptyList()
        val raw = runCatching { file.readText(Charsets.UTF_8) }.getOrNull().orEmpty()
        val plain = EncryptedStoreCrypto.decryptPayload(KEYSTORE_ALIAS, raw) ?: return emptyList()
        val parsed = parseAccountsJson(plain)
        if (!EncryptedStoreCrypto.isEncrypted(raw) && parsed.isNotEmpty()) {
            writeAllInternal(context, parsed)
        }
        return parsed
    }

    private fun writeAllInternal(context: Context, accounts: List<CloudAccount>) {
        val file = getStoreFile(context)
        if (accounts.isEmpty()) {
            if (file.exists()) file.delete()
            return
        }
        file.parentFile?.mkdirs()
        val json = serializeAccountsJson(accounts)
        file.writeText(EncryptedStoreCrypto.encryptPayload(KEYSTORE_ALIAS, json), Charsets.UTF_8)
        EncryptedStoreCrypto.applyOwnerOnlyPermissions(file)
    }

    fun getAccounts(context: Context, provider: CloudProvider? = null): List<CloudAccount> = synchronized(lock) {
        val all = readAllInternal(context)
        if (provider == null) all else all.filter { it.provider == provider }
    }

    fun getAccountById(context: Context, id: String?): CloudAccount? {
        if (id.isNullOrBlank()) return null
        return synchronized(lock) {
            readAllInternal(context).firstOrNull { it.id == id }
        }
    }

    fun saveAccount(context: Context, account: CloudAccount): CloudAccount = synchronized(lock) {
        val resolved = if (account.id.isBlank()) {
            account.copy(id = UUID.randomUUID().toString())
        } else {
            account
        }
        val existing = readAllInternal(context).filterNot { it.id == resolved.id }
        writeAllInternal(context, listOf(resolved) + existing)
        resolved
    }

    fun removeAccount(context: Context, accountId: String): Boolean = synchronized(lock) {
        val existing = readAllInternal(context)
        val remaining = existing.filterNot { it.id == accountId }
        if (remaining.size == existing.size) return@synchronized false
        writeAllInternal(context, remaining)
        true
    }
}
