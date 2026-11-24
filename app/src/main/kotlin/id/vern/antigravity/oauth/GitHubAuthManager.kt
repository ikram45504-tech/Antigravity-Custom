package id.vern.antigravity.oauth

import android.content.Context
import android.system.Os
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
    private const val STORE_FILE_NAME = "github_account.json"

    private val lock = Any()

    private fun getStoreFile(context: Context): File {
        val dir = File(context.filesDir, ".gemini")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, STORE_FILE_NAME)
    }

    fun getAccount(context: Context): GitHubAccount? = synchronized(lock) {
        val file = getStoreFile(context)
        if (!file.isFile || file.length() == 0L) return null
        return try {
            val obj = JSONObject(file.readText(Charsets.UTF_8))
            val login = obj.optString("login")
            val token = obj.optString("accessToken")
            if (login.isBlank() || token.isBlank()) return null
            GitHubAccount(
                login = login,
                name = obj.optString("name").ifBlank { login },
                email = obj.optString("email").ifBlank { "$login@users.noreply.github.com" },
                avatarUrl = obj.optString("avatarUrl").takeIf { it.isNotBlank() },
                accessToken = token
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to read GitHub account: ${e.message}")
            null
        }
    }

    private fun applyOwnerOnlyPermissions(file: File) {
        file.setReadable(false, false)
        file.setReadable(true, true)
        file.setWritable(false, false)
        file.setWritable(true, true)
        try { Os.chmod(file.absolutePath, 384) } catch (_: Exception) {}
    }

    internal fun cleanGitConfigLines(lines: List<String>, removeCredentialHelper: Boolean = false): List<String> {
        val cleaned = mutableListOf<String>()
        var skipNextInsteadOf = false
        var skipNextCredentialHelper = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("[url \"https://") && trimmed.contains("@github.com/\"]")) {
                skipNextInsteadOf = true
                skipNextCredentialHelper = false
                continue
            }
            if (skipNextInsteadOf && trimmed.startsWith("insteadOf")) {
                skipNextInsteadOf = false
                continue
            }
            skipNextInsteadOf = false

            if (removeCredentialHelper && trimmed == "[credential \"https://github.com\"]") {
                skipNextCredentialHelper = true
                continue
            }
            if (skipNextCredentialHelper && trimmed.startsWith("helper")) {
                skipNextCredentialHelper = false
                continue
            }
            skipNextCredentialHelper = false
            cleaned.add(line)
        }
        return cleaned
    }

    internal fun buildSyncedGitConfig(existingLines: List<String>, name: String, email: String): String {
        val cleaned = cleanGitConfigLines(existingLines, removeCredentialHelper = false)
        val sb = StringBuilder(cleaned.joinToString("\n"))
        if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append("\n")
        if (!sb.contains("[credential \"https://github.com\"]")) {
            sb.append("[credential \"https://github.com\"]\n\thelper = store\n")
        }
        if (!sb.contains("[user]")) {
            sb.append("[user]\n\tname = $name\n\temail = $email\n")
        }
        return sb.toString()
    }

    fun saveAccount(context: Context, account: GitHubAccount) {
        synchronized(lock) {
            val file = getStoreFile(context)
            val obj = JSONObject().apply {
                put("login", account.login)
                put("name", account.name)
                put("email", account.email)
                put("avatarUrl", account.avatarUrl ?: "")
                put("accessToken", account.accessToken)
            }
            file.parentFile?.mkdirs()
            file.writeText(obj.toString(2), Charsets.UTF_8)
            applyOwnerOnlyPermissions(file)
        }
        syncGitCredentials(context)
    }

    fun signOut(context: Context) {
        synchronized(lock) {
            val file = getStoreFile(context)
            if (file.exists()) file.delete()
        }
        removeGitCredentials(context)
    }

    fun syncGitCredentials(context: Context) {
        val account = getAccount(context) ?: return
        try {
            val homeDir = File(context.filesDir, "home").apply { mkdirs() }
            val credFile = File(homeDir, ".git-credentials")
            val existingLines = if (credFile.isFile) {
                credFile.readLines(Charsets.UTF_8).filterNot { it.contains("@github.com") }
            } else {
                emptyList()
            }
            val newLine = "https://${account.login}:${account.accessToken}@github.com"
            credFile.writeText((existingLines + newLine).joinToString("\n") + "\n", Charsets.UTF_8)
            applyOwnerOnlyPermissions(credFile)

            val gitConfig = File(homeDir, ".gitconfig")
            val currentLines = if (gitConfig.isFile) gitConfig.readLines(Charsets.UTF_8) else emptyList()
            gitConfig.writeText(buildSyncedGitConfig(currentLines, account.name, account.email), Charsets.UTF_8)
            applyOwnerOnlyPermissions(gitConfig)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to sync git credentials: ${e.message}")
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
                val cleaned = cleanGitConfigLines(currentLines, removeCredentialHelper = true)
                val content = if (cleaned.isEmpty()) "" else cleaned.joinToString("\n").trimEnd() + "\n"
                gitConfig.writeText(content, Charsets.UTF_8)
                applyOwnerOnlyPermissions(gitConfig)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to remove git credentials: ${e.message}")
        }
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
