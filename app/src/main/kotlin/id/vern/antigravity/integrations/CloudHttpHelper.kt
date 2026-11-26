package id.vern.antigravity.integrations

import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

internal data class HttpResponseResult(
    val statusCode: Int,
    val body: String
)

internal object CloudHttpHelper {
    private const val USER_AGENT = "Antigravity-Android"

    fun get(urlStr: String, bearerToken: String, timeoutMs: Int = 15_000): HttpResponseResult {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $bearerToken")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            HttpResponseResult(statusCode = code, body = text)
        } finally {
            conn.disconnect()
        }
    }

    fun postJson(
        urlStr: String,
        bearerToken: String,
        jsonBody: String = "{}",
        timeoutMs: Int = 20_000
    ): HttpResponseResult {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $bearerToken")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        return try {
            conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            HttpResponseResult(statusCode = code, body = text)
        } finally {
            conn.disconnect()
        }
    }

    fun extractErrorMessage(body: String, fallbackStatus: Int): String {
        val trimmed = body.trim()
        if (trimmed.startsWith("{")) {
            val obj = runCatching { JSONObject(trimmed) }.getOrNull()
            if (obj != null) {
                val errObj = obj.optJSONObject("error")
                val errMsg = errObj?.optString("message")?.takeIf { it.isNotBlank() }
                    ?: obj.optString("message").takeIf { it.isNotBlank() }
                    ?: obj.optJSONArray("errors")?.optJSONObject(0)?.optString("message")?.takeIf { it.isNotBlank() }
                if (!errMsg.isNullOrBlank()) return errMsg
            }
        }
        return "HTTP $fallbackStatus"
    }

    /**
     * Upserts key/value pairs in a workspace .env.local file while preserving unrelated keys.
     */
    fun upsertDotEnvKeys(envFile: File, entries: Map<String, String>) {
        if (entries.isEmpty()) return
        envFile.parentFile?.mkdirs()
        val existingLines = if (envFile.isFile) envFile.readLines(Charsets.UTF_8) else emptyList()
        val remaining = existingLines.filterNot { line ->
            val t = line.trimStart()
            if (t.isEmpty() || t.startsWith("#")) return@filterNot false
            val eqIdx = t.indexOf('=')
            if (eqIdx <= 0) return@filterNot false
            val key = t.substring(0, eqIdx).trim()
            entries.containsKey(key)
        }.toMutableList()
        entries.forEach { (k, v) ->
            val sanitized = v.replace("\r", "").replace("\n", "\\n")
            val formatted = if (sanitized.any { it.isWhitespace() || it == '#' || it == '"' || it == '\'' }) {
                "\"${sanitized.replace("\"", "\\\"")}\""
            } else {
                sanitized
            }
            remaining.add("$k=$formatted")
        }
        envFile.writeText(remaining.joinToString("\n") + "\n", Charsets.UTF_8)
        envFile.parentFile?.let { projectDir ->
            ensureGitIgnoreEntries(projectDir, listOf(envFile.name))
        }
    }

    /**
     * Ensures sensitive workspace files (.env.local, .vercel) are listed in .gitignore
     * whenever the workspace is a Git repository or already has a .gitignore file.
     */
    fun ensureGitIgnoreEntries(projectDir: File, patterns: List<String>) {
        if (patterns.isEmpty()) return
        val gitDir = File(projectDir, ".git")
        val gitIgnore = File(projectDir, ".gitignore")
        if (!gitDir.exists() && !gitIgnore.exists()) return
        val existingLines = if (gitIgnore.isFile) {
            runCatching { gitIgnore.readLines(Charsets.UTF_8) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val trimmedSet = existingLines.map { it.trim() }.toSet()
        val missing = patterns.filter { p -> p !in trimmedSet && "/$p" !in trimmedSet }
        if (missing.isEmpty()) return
        val sb = StringBuilder(existingLines.joinToString("\n").trimEnd())
        if (sb.isNotEmpty()) sb.append("\n")
        missing.forEach { sb.append(it).append("\n") }
        runCatching { gitIgnore.writeText(sb.toString(), Charsets.UTF_8) }
    }
}
