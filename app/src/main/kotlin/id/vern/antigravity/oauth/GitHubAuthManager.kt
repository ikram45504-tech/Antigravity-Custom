package id.vern.antigravity.oauth

import android.content.Context
import id.vern.antigravity.integrations.ProjectIntegrationsManager
import id.vern.antigravity.util.AppLogger
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class GitHubAccount(
    val login: String,
    val name: String,
    val email: String,
    val avatarUrl: String? = null,
    val accessToken: String
)

data class GitHubDeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresIn: Int,
    val interval: Int
)

data class GitHubVerificationResult(
    val isValid: Boolean,
    val login: String? = null,
    val scopes: List<String> = emptyList(),
    val statusCode: Int = 0,
    val message: String = ""
)

data class GitHubRepoItem(
    val id: Long,
    val name: String,
    val fullName: String,
    val cloneUrl: String,
    val htmlUrl: String,
    val description: String,
    val isPrivate: Boolean,
    val defaultBranch: String,
    val updatedAt: String
)

sealed class GitHubPollResult {
    data class Success(val account: GitHubAccount) : GitHubPollResult()
    data class Pending(val slowDown: Boolean = false) : GitHubPollResult()
    data object Expired : GitHubPollResult()
    data object Denied : GitHubPollResult()
    data class Error(val message: String) : GitHubPollResult()
}

object GitHubAuthManager {
    private const val TAG = "GitHubAuthManager"
    const val CLIENT_ID = "Ov23lixOehLDPMiSbYd6"
    private const val DEVICE_CODE_URL = "https://github.com/login/device/code"
    private const val ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token"
    private const val USER_API_URL = "https://api.github.com/user"
    private const val USER_EMAILS_API_URL = "https://api.github.com/user/emails"
    private const val USER_REPOS_API_URL = "https://api.github.com/user/repos?sort=updated&per_page=50&affiliation=owner,collaborator,organization_member"
    private const val LEGACY_STORE_FILE_NAME = "github_account.json"
    private const val MULTI_STORE_FILE_NAME = "github_accounts.json"
    private const val KEYSTORE_ALIAS = "antigravity_github_token_aes_v1"
    private const val ENC_PREFIX = "enc:v1:"

    private val lock = Any()

    private fun getLegacyStoreFile(context: Context): File {
        val dir = File(context.filesDir, ".gemini")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, LEGACY_STORE_FILE_NAME)
    }

    private fun getStoreFile(context: Context): File {
        val dir = File(context.filesDir, ".gemini")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, MULTI_STORE_FILE_NAME)
    }

    private fun encryptPayload(plainJson: String): String =
        EncryptedStoreCrypto.encryptPayload(KEYSTORE_ALIAS, plainJson)

    private fun decryptPayload(raw: String): String? =
        EncryptedStoreCrypto.decryptPayload(KEYSTORE_ALIAS, raw)

    private fun parseAccountJson(obj: JSONObject?): GitHubAccount? {
        if (obj == null) return null
        val login = obj.optString("login")
        val token = obj.optString("accessToken")
        if (login.isBlank() || token.isBlank()) return null
        return GitHubAccount(
            login = login,
            name = obj.optString("name").ifBlank { login },
            email = obj.optString("email").ifBlank { "$login@users.noreply.github.com" },
            avatarUrl = obj.optString("avatarUrl").takeIf { it.isNotBlank() },
            accessToken = token
        )
    }

    private fun accountToJson(account: GitHubAccount): JSONObject = JSONObject().apply {
        put("login", account.login)
        put("name", account.name)
        put("email", account.email)
        put("avatarUrl", account.avatarUrl ?: "")
        put("accessToken", account.accessToken)
    }

    private data class AccountStoreState(
        val activeLogin: String?,
        val accounts: List<GitHubAccount>
    )

    private fun readStoreState(context: Context): AccountStoreState {
        val multiFile = getStoreFile(context)
        if (multiFile.isFile && multiFile.length() > 0L) {
            val raw = runCatching { multiFile.readText(Charsets.UTF_8) }.getOrNull().orEmpty()
            val jsonStr = decryptPayload(raw)
            if (!jsonStr.isNullOrBlank()) {
                val parsed = runCatching {
                    val obj = JSONObject(jsonStr)
                    val arr = obj.optJSONArray("accounts") ?: JSONArray()
                    val list = mutableListOf<GitHubAccount>()
                    for (i in 0 until arr.length()) {
                        parseAccountJson(arr.optJSONObject(i))?.let { list.add(it) }
                    }
                    val active = obj.optString("activeLogin").takeIf { it.isNotBlank() }
                        ?: list.firstOrNull()?.login
                    AccountStoreState(active, list)
                }.getOrNull()
                if (parsed != null) {
                    if (!raw.trimStart().startsWith(ENC_PREFIX) && parsed.accounts.isNotEmpty()) {
                        writeStoreState(context, parsed)
                    }
                    return parsed
                }
            }
        }

        // Migrate legacy single-account file if present
        val legacyFile = getLegacyStoreFile(context)
        if (legacyFile.isFile && legacyFile.length() > 0L) {
            val raw = runCatching { legacyFile.readText(Charsets.UTF_8) }.getOrNull().orEmpty()
            val jsonStr = decryptPayload(raw)
            val legacyAcc = jsonStr?.let { runCatching { parseAccountJson(JSONObject(it)) }.getOrNull() }
            if (legacyAcc != null) {
                val migrated = AccountStoreState(legacyAcc.login, listOf(legacyAcc))
                writeStoreState(context, migrated)
                runCatching { legacyFile.delete() }
                return migrated
            }
        }

        return AccountStoreState(null, emptyList())
    }

    private fun writeStoreState(context: Context, state: AccountStoreState) {
        val file = getStoreFile(context)
        if (state.accounts.isEmpty()) {
            if (file.exists()) file.delete()
            val legacy = getLegacyStoreFile(context)
            if (legacy.exists()) legacy.delete()
            return
        }
        val arr = JSONArray()
        state.accounts.forEach { arr.put(accountToJson(it)) }
        val root = JSONObject().apply {
            put("activeLogin", state.activeLogin ?: state.accounts.first().login)
            put("accounts", arr)
        }
        file.parentFile?.mkdirs()
        file.writeText(encryptPayload(root.toString()), Charsets.UTF_8)
        applyOwnerOnlyPermissions(file)
        val legacy = getLegacyStoreFile(context)
        if (legacy.exists()) runCatching { legacy.delete() }
    }

    fun getAccounts(context: Context): List<GitHubAccount> = synchronized(lock) {
        readStoreState(context).accounts
    }

    fun getAccount(context: Context): GitHubAccount? = synchronized(lock) {
        val state = readStoreState(context)
        if (state.accounts.isEmpty()) return null
        val active = state.activeLogin
        state.accounts.firstOrNull { it.login.equals(active, ignoreCase = true) }
            ?: state.accounts.firstOrNull()
    }

    private fun applyOwnerOnlyPermissions(file: File) {
        EncryptedStoreCrypto.applyOwnerOnlyPermissions(file)
    }

    internal fun cleanGitConfigLines(
        lines: List<String>,
        removeCredentialHelper: Boolean = false,
        removeUserSection: Boolean = false
    ): List<String> {
        val cleaned = mutableListOf<String>()
        var skipNextInsteadOf = false
        var skipNextCredentialHelper = false
        var skipUserEntries = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[url \"https://") && trimmed.contains("@github.com/\"]")) {
                skipNextInsteadOf = true
                skipNextCredentialHelper = false
                skipUserEntries = false
                continue
            }
            if (skipNextInsteadOf && trimmed.startsWith("insteadOf")) {
                skipNextInsteadOf = false
                continue
            }
            skipNextInsteadOf = false

            if (removeCredentialHelper && trimmed == "[credential \"https://github.com\"]") {
                skipNextCredentialHelper = true
                skipUserEntries = false
                continue
            }
            if (skipNextCredentialHelper && trimmed.startsWith("helper")) {
                skipNextCredentialHelper = false
                continue
            }
            skipNextCredentialHelper = false

            if (removeUserSection && trimmed == "[user]") {
                skipUserEntries = true
                continue
            }
            if (skipUserEntries) {
                if (trimmed.startsWith("[")) {
                    skipUserEntries = false
                } else {
                    continue
                }
            }

            cleaned.add(line)
        }
        return cleaned
    }

    internal fun buildSyncedGitConfig(existingLines: List<String>, name: String, email: String): String {
        val cleaned = cleanGitConfigLines(
            lines = existingLines,
            removeCredentialHelper = false,
            removeUserSection = true
        )
        val sb = StringBuilder(cleaned.joinToString("\n").trimEnd())
        if (sb.isNotEmpty()) sb.append("\n")
        if (!sb.contains("[credential \"https://github.com\"]")) {
            sb.append("[credential \"https://github.com\"]\n\thelper = store\n")
        }
        sb.append("[user]\n\tname = $name\n\temail = $email\n")
        return sb.toString()
    }

    internal fun safeProfileDirName(login: String): String =
        login.trim().lowercase().replace(Regex("[^a-z0-9._-]"), "_").ifBlank { "default" }

    internal fun buildProfileGitConfig(
        certPath: String,
        credentialsFilePath: String?,
        name: String?,
        email: String?
    ): String {
        val sb = StringBuilder()
        sb.append("[safe]\n\tdirectory = *\n")
        if (certPath.isNotBlank()) {
            sb.append("[http]\n\tsslCAInfo = ").append(certPath).append("\n")
        }
        if (!credentialsFilePath.isNullOrBlank()) {
            sb.append("[credential \"https://github.com\"]\n\thelper = store --file ")
                .append(credentialsFilePath)
                .append("\n")
        }
        if (!name.isNullOrBlank() || !email.isNullOrBlank()) {
            sb.append("[user]\n")
            if (!name.isNullOrBlank()) sb.append("\tname = ").append(name).append("\n")
            if (!email.isNullOrBlank()) sb.append("\temail = ").append(email).append("\n")
        }
        return sb.toString()
    }

    internal fun parseGitHubRemoteRepo(rawUrl: String): Pair<String, String>? {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return null
        val sshMatch = Regex("^git@github\\.com:([^/\\s]+)/([^/\\s]+?)(?:\\.git)?/?$", RegexOption.IGNORE_CASE)
            .find(trimmed)
        if (sshMatch != null) {
            val owner = sshMatch.groupValues[1]
            val repo = sshMatch.groupValues[2]
            val fullName = "$owner/$repo"
            return fullName to "https://github.com/$fullName.git"
        }
        val httpsMatch = Regex("^https?://(?:[^@/\\s]+@)?github\\.com/([^/\\s]+)/([^/\\s]+?)(?:\\.git)?/?$", RegexOption.IGNORE_CASE)
            .find(trimmed)
        if (httpsMatch != null) {
            val owner = httpsMatch.groupValues[1]
            val repo = httpsMatch.groupValues[2]
            val fullName = "$owner/$repo"
            return fullName to "https://github.com/$fullName.git"
        }
        return null
    }

    fun detectGitHubRemoteRepo(projectDir: File): Pair<String, String>? {
        val gitConfig = File(projectDir, ".git/config")
        if (!gitConfig.isFile) return null
        return runCatching {
            val lines = gitConfig.readLines(Charsets.UTF_8)
            var inRemoteOrigin = false
            var firstFound: Pair<String, String>? = null
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.startsWith("[")) {
                    inRemoteOrigin = trimmed.equals("[remote \"origin\"]", ignoreCase = true)
                    continue
                }
                if (trimmed.startsWith("url") && trimmed.contains("=")) {
                    val rawUrl = trimmed.substringAfter("=").trim()
                    val parsed = parseGitHubRemoteRepo(rawUrl)
                    if (parsed != null) {
                        if (inRemoteOrigin) return@runCatching parsed
                        if (firstFound == null) firstFound = parsed
                    }
                }
            }
            firstFound
        }.getOrNull()
    }

    private fun getProfilesRootDir(context: Context): File =
        File(context.filesDir, ".gemini/git-profiles").apply { mkdirs() }

    private fun ensureUnboundProfileConfig(context: Context): File {
        val certPath = File(context.filesDir, "usr/etc/tls/cert.pem").absolutePath
        val unboundDir = File(getProfilesRootDir(context), "_unbound").apply { mkdirs() }
        val cfgFile = File(unboundDir, "gitconfig")
        cfgFile.writeText(buildProfileGitConfig(certPath, null, null, null), Charsets.UTF_8)
        applyOwnerOnlyPermissions(cfgFile)
        return cfgFile
    }

    private fun writeAccountProfile(context: Context, account: GitHubAccount): File {
        val certPath = File(context.filesDir, "usr/etc/tls/cert.pem").absolutePath
        val profileDir = File(getProfilesRootDir(context), safeProfileDirName(account.login)).apply { mkdirs() }
        val credFile = File(profileDir, "credentials")
        credFile.writeText("https://${account.login}:${account.accessToken}@github.com\n", Charsets.UTF_8)
        applyOwnerOnlyPermissions(credFile)

        val cfgFile = File(profileDir, "gitconfig")
        cfgFile.writeText(
            buildProfileGitConfig(
                certPath = certPath,
                credentialsFilePath = credFile.absolutePath,
                name = account.name,
                email = account.email
            ),
            Charsets.UTF_8
        )
        applyOwnerOnlyPermissions(cfgFile)
        return cfgFile
    }

    private fun deleteAccountProfile(context: Context, login: String) {
        runCatching {
            val profileDir = File(getProfilesRootDir(context), safeProfileDirName(login))
            if (profileDir.exists()) profileDir.deleteRecursively()
        }
    }

    fun buildGitEnvForAccount(context: Context, login: String?): Map<String, String> {
        val unbound = ensureUnboundProfileConfig(context)
        val cleanLogin = login?.trim().orEmpty()
        if (cleanLogin.isEmpty()) {
            return mapOf("GIT_CONFIG_GLOBAL" to unbound.absolutePath)
        }
        val account = getAccounts(context).firstOrNull { it.login.equals(cleanLogin, ignoreCase = true) }
            ?: return mapOf("GIT_CONFIG_GLOBAL" to unbound.absolutePath)
        val cfg = writeAccountProfile(context, account)
        return mapOf("GIT_CONFIG_GLOBAL" to cfg.absolutePath)
    }

    fun buildGitEnvForProject(context: Context, workingDir: File?): Map<String, String> {
        val unbound = ensureUnboundProfileConfig(context)
        val dirPath = workingDir?.absolutePath ?: return mapOf("GIT_CONFIG_GLOBAL" to unbound.absolutePath)
        val boundLogin = ProjectIntegrationsManager.getBindingForProject(context, dirPath)?.github?.accountLogin
            ?: return mapOf("GIT_CONFIG_GLOBAL" to unbound.absolutePath)
        return buildGitEnvForAccount(context, boundLogin)
    }

    fun saveAccount(context: Context, account: GitHubAccount) {
        synchronized(lock) {
            val existing = readStoreState(context).accounts
                .filterNot { it.login.equals(account.login, ignoreCase = true) }
            writeStoreState(
                context,
                AccountStoreState(
                    activeLogin = account.login,
                    accounts = listOf(account) + existing
                )
            )
        }
        syncGitCredentials(context)
    }

    fun switchAccount(context: Context, login: String): Boolean {
        val switched = synchronized(lock) {
            val state = readStoreState(context)
            val target = state.accounts.firstOrNull { it.login.equals(login, ignoreCase = true) }
                ?: return@synchronized false
            writeStoreState(context, state.copy(activeLogin = target.login))
            true
        }
        if (switched) {
            syncGitCredentials(context)
        }
        return switched
    }

    fun removeAccount(context: Context, login: String): Boolean {
        val removed = synchronized(lock) {
            val state = readStoreState(context)
            val remaining = state.accounts.filterNot { it.login.equals(login, ignoreCase = true) }
            if (remaining.size == state.accounts.size) return@synchronized false
            val nextActive = if (state.activeLogin.equals(login, ignoreCase = true)) {
                remaining.firstOrNull()?.login
            } else {
                state.activeLogin
            }
            writeStoreState(context, AccountStoreState(nextActive, remaining))
            true
        }
        if (removed) {
            deleteAccountProfile(context, login)
            ProjectIntegrationsManager.removeBindingsForGitHubLogin(context, login)
            syncGitCredentials(context)
        }
        return removed
    }

    fun signOut(context: Context) {
        val activeLogin = getAccount(context)?.login
        if (activeLogin != null) {
            removeAccount(context, activeLogin)
        } else {
            synchronized(lock) {
                writeStoreState(context, AccountStoreState(null, emptyList()))
            }
            removeGitCredentials(context)
        }
    }

    fun syncGitCredentials(context: Context) {
        try {
            // Remove legacy global GitHub credentials so unlinked projects remain strictly isolated.
            removeGitCredentials(context)
            ensureUnboundProfileConfig(context)
            getAccounts(context).forEach { account ->
                writeAccountProfile(context, account)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to sync isolated git profiles: ${e.message}")
        }
    }

    private fun removeGitCredentials(context: Context) {
        try {
            val homeDir = File(context.filesDir, "home")
            val credFile = File(homeDir, ".git-credentials")
            if (credFile.isFile) {
                val remaining = credFile.readLines(Charsets.UTF_8)
                    .filterNot { it.contains("@github.com") }
                if (remaining.isEmpty()) {
                    credFile.delete()
                } else {
                    credFile.writeText(remaining.joinToString("\n") + "\n", Charsets.UTF_8)
                    applyOwnerOnlyPermissions(credFile)
                }
            }
            val gitConfig = File(homeDir, ".gitconfig")
            if (gitConfig.isFile) {
                val currentLines = gitConfig.readLines(Charsets.UTF_8)
                val cleaned = cleanGitConfigLines(currentLines, removeCredentialHelper = true, removeUserSection = true)
                val content = if (cleaned.isEmpty()) "" else cleaned.joinToString("\n").trimEnd() + "\n"
                gitConfig.writeText(content, Charsets.UTF_8)
                applyOwnerOnlyPermissions(gitConfig)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to remove git credentials: ${e.message}")
        }
    }

    fun verifyConnection(context: Context, login: String? = null): GitHubVerificationResult {
        val account = if (!login.isNullOrBlank()) {
            getAccounts(context).firstOrNull { it.login.equals(login, ignoreCase = true) }
        } else {
            getAccount(context)
        } ?: return GitHubVerificationResult(isValid = false, message = "No GitHub account connected.")
        return try {
            val conn = (URL(USER_API_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("Authorization", "Bearer ${account.accessToken}")
                setRequestProperty("User-Agent", "Antigravity-Android")
            }
            val code = conn.responseCode
            val scopesHeader = conn.getHeaderField("X-OAuth-Scopes").orEmpty()
            val scopes = parseScopesHeader(scopesHeader)
            if (code in 200..299) {
                val body = conn.inputStream.bufferedReader().readText()
                val login = runCatching { JSONObject(body).optString("login") }.getOrNull()
                    ?.takeIf { it.isNotBlank() } ?: account.login
                GitHubVerificationResult(
                    isValid = true,
                    login = login,
                    scopes = scopes,
                    statusCode = code,
                    message = "Verified as @$login"
                )
            } else if (code == 401) {
                GitHubVerificationResult(
                    isValid = false,
                    login = account.login,
                    statusCode = code,
                    message = "Token expired or revoked (HTTP 401). Reconnect GitHub."
                )
            } else {
                GitHubVerificationResult(
                    isValid = false,
                    login = account.login,
                    statusCode = code,
                    message = "GitHub returned HTTP $code."
                )
            }
        } catch (e: Exception) {
            GitHubVerificationResult(
                isValid = false,
                login = account.login,
                message = e.message ?: "Network error verifying GitHub token."
            )
        }
    }

    internal fun parseScopesHeader(header: String): List<String> =
        header.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    fun fetchUserRepositories(accessToken: String, page: Int = 1): Result<List<GitHubRepoItem>> {
        return try {
            val url = "$USER_REPOS_API_URL&page=${page.coerceAtLeast(1)}"
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("User-Agent", "Antigravity-Android")
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                return Result.failure(Exception("GitHub API error (HTTP $code)"))
            }
            val text = conn.inputStream.bufferedReader().readText()
            Result.success(parseRepositoriesJson(text))
        } catch (e: Exception) {
            AppLogger.e(TAG, "fetchUserRepositories failed: ${e.message}")
            Result.failure(e)
        }
    }

    internal fun parseRepositoriesJson(jsonText: String): List<GitHubRepoItem> {
        val arr = JSONArray(jsonText)
        val repos = mutableListOf<GitHubRepoItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val name = obj.optString("name")
            val fullName = obj.optString("full_name")
            val cloneUrl = obj.optString("clone_url")
            if (name.isBlank() || cloneUrl.isBlank()) continue
            repos.add(
                GitHubRepoItem(
                    id = obj.optLong("id", 0L),
                    name = name,
                    fullName = fullName.ifBlank { name },
                    cloneUrl = cloneUrl,
                    htmlUrl = obj.optString("html_url"),
                    description = obj.optString("description").takeIf { it != "null" }.orEmpty(),
                    isPrivate = obj.optBoolean("private", false),
                    defaultBranch = obj.optString("default_branch", "main"),
                    updatedAt = obj.optString("updated_at")
                )
            )
        }
        return repos
    }

    fun requestDeviceCode(): Result<GitHubDeviceCode> {
        return try {
            val body = "client_id=${enc(CLIENT_ID)}&scope=${enc("repo workflow read:user user:email")}"
            val json = postForm(DEVICE_CODE_URL, body)
            val err = json.optString("error")
            if (err.isNotBlank()) {
                return Result.failure(Exception(json.optString("error_description").ifBlank { err }))
            }
            Result.success(
                GitHubDeviceCode(
                    deviceCode = json.getString("device_code"),
                    userCode = json.getString("user_code"),
                    verificationUri = json.optString("verification_uri", "https://github.com/login/device"),
                    expiresIn = json.optInt("expires_in", 900),
                    interval = json.optInt("interval", 5)
                )
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "requestDeviceCode failed: ${e.message}")
            Result.failure(e)
        }
    }

    fun pollAccessToken(deviceCode: String): GitHubPollResult {
        return try {
            val body = "client_id=${enc(CLIENT_ID)}" +
                "&device_code=${enc(deviceCode)}" +
                "&grant_type=${enc("urn:ietf:params:oauth:grant-type:device_code")}"
            val json = postForm(ACCESS_TOKEN_URL, body)
            val accessToken = json.optString("access_token")
            if (accessToken.isNotBlank()) {
                val account = fetchUserProfile(accessToken)
                    ?: return GitHubPollResult.Error("Failed to fetch GitHub user profile")
                return GitHubPollResult.Success(account)
            }
            when (val error = json.optString("error")) {
                "authorization_pending" -> GitHubPollResult.Pending(slowDown = false)
                "slow_down" -> GitHubPollResult.Pending(slowDown = true)
                "expired_token" -> GitHubPollResult.Expired
                "access_denied" -> GitHubPollResult.Denied
                else -> GitHubPollResult.Error(
                    json.optString("error_description").ifBlank { error.ifBlank { "Unknown OAuth error" } }
                )
            }
        } catch (e: Exception) {
            GitHubPollResult.Error(e.message ?: "Network error")
        }
    }

    fun fetchUserProfile(accessToken: String): GitHubAccount? {
        return try {
            val userJson = getJson(USER_API_URL, accessToken) ?: return null
            val login = userJson.optString("login")
            if (login.isBlank()) return null
            val name = userJson.optString("name").takeIf { it.isNotBlank() && it != "null" } ?: login
            val avatarUrl = userJson.optString("avatar_url").takeIf { it.isNotBlank() && it != "null" }
            var email = userJson.optString("email").takeIf { it.isNotBlank() && it != "null" }
            if (email == null) {
                email = fetchPrimaryEmail(accessToken)
            }
            GitHubAccount(
                login = login,
                name = name,
                email = email ?: "$login@users.noreply.github.com",
                avatarUrl = avatarUrl,
                accessToken = accessToken
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "fetchUserProfile failed: ${e.message}")
            null
        }
    }

    private fun fetchPrimaryEmail(accessToken: String): String? {
        return try {
            val conn = (URL(USER_EMAILS_API_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("User-Agent", "Antigravity-Android")
            }
            if (conn.responseCode !in 200..299) return null
            val arr = JSONArray(conn.inputStream.bufferedReader().readText())
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                if (item.optBoolean("primary", false)) {
                    return item.optString("email").takeIf { it.isNotBlank() }
                }
            }
            if (arr.length() > 0) arr.getJSONObject(0).optString("email").takeIf { it.isNotBlank() } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun postForm(urlStr: String, body: String): JSONObject {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("User-Agent", "Antigravity-Android")
        }
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.readText().orEmpty()
        return JSONObject(text)
    }

    private fun getJson(urlStr: String, accessToken: String): JSONObject? {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("User-Agent", "Antigravity-Android")
        }
        if (conn.responseCode !in 200..299) return null
        return JSONObject(conn.inputStream.bufferedReader().readText())
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
