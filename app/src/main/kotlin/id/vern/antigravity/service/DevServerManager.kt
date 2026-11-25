package id.vern.antigravity.service

import android.content.Context
import id.vern.antigravity.executor.EngineExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class DevServerSession(
    val id: String,
    val projectPath: String,
    val projectName: String,
    val command: String,
    val isRunning: Boolean = true,
    val detectedPort: Int? = null,
    val detectedUrl: String? = null,
    val logs: List<String> = emptyList(),
    val lastExitCode: Int? = null
)

object DevServerManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()

    private val mutableServers = MutableStateFlow<List<DevServerSession>>(emptyList())
    val servers: StateFlow<List<DevServerSession>> = mutableServers.asStateFlow()

    private val mutableDetectedUrls = MutableStateFlow<List<String>>(emptyList())
    val detectedUrls: StateFlow<List<String>> = mutableDetectedUrls.asStateFlow()

    private val URL_REGEX = Regex(
        """https?://(?:localhost|127\.0\.0\.1|0\.0\.0\.0|\[::1?\]):(\d{2,5})(/[^\s"'<>)\]]*)?""",
        RegexOption.IGNORE_CASE
    )
    private val LISTEN_PORT_REGEX = Regex(
        """(?:listening|started|running|ready|server)\b[^\n]*?(?:\bon\b|\bat\b|:)\s*(?:port\s+)?(\d{4,5})\b""",
        RegexOption.IGNORE_CASE
    )

    internal fun normalizeLocalhostUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim().trimEnd('.', ',', ';', ')', ']')
        return trimmed
            .replace("://0.0.0.0:", "://127.0.0.1:", ignoreCase = true)
            .replace("://[::]:", "://127.0.0.1:", ignoreCase = true)
            .replace("://[::1]:", "://127.0.0.1:", ignoreCase = true)
            .replace("://localhost:", "://127.0.0.1:", ignoreCase = true)
    }

    internal fun extractLocalhostUrls(line: String): List<Pair<Int, String>> {
        val clean = TerminalSessionManager.cleanAnsi(line)
        val found = mutableListOf<Pair<Int, String>>()
        URL_REGEX.findAll(clean).forEach { m ->
            val port = m.groupValues[1].toIntOrNull()
            if (port != null && port in 80..65535) {
                found.add(port to normalizeLocalhostUrl(m.value))
            }
        }
        if (found.isEmpty()) {
            LISTEN_PORT_REGEX.find(clean)?.let { m ->
                val port = m.groupValues[1].toIntOrNull()
                if (port != null && port in 1024..65535) {
                    found.add(port to "http://127.0.0.1:$port")
                }
            }
        }
        return found
    }

    internal fun suggestDefaultCommand(projectDir: File?): String {
        if (projectDir != null && projectDir.isDirectory) {
            val pkgJson = File(projectDir, "package.json")
            if (pkgJson.isFile) {
                val scripts = runCatching {
                    JSONObject(pkgJson.readText(Charsets.UTF_8)).optJSONObject("scripts")
                }.getOrNull()
                if (scripts != null) {
                    if (scripts.has("dev")) return "npm run dev"
                    if (scripts.has("start")) return "npm start"
                    if (scripts.has("serve")) return "npm run serve"
                }
                return "npm run dev"
            }
            if (File(projectDir, "manage.py").isFile) {
                return "python3 manage.py runserver 127.0.0.1:8000"
            }
        }
        return "python3 -m http.server 8000 --bind 127.0.0.1"
    }

    fun recordDetectedUrlFromOutput(line: String): String? {
        val matches = extractLocalhostUrls(line)
        if (matches.isEmpty()) return null
        val first = matches.first().second
        mutableDetectedUrls.update { current ->
            val updated = (matches.map { it.second } + current).distinct().take(8)
            updated
        }
        return first
    }

    fun startServer(
        context: Context,
        projectPath: String,
        command: String
    ): DevServerSession? {
        val cleanCmd = command.trim()
        if (cleanCmd.isEmpty()) return null
        val dir = projectPath.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.isDirectory }
        val resolvedPath = dir?.absolutePath ?: ""
        val projectName = dir?.name ?: "Home"
        val id = UUID.randomUUID().toString().take(8)
        val commandKey = "devserver_$id"

        val session = DevServerSession(
            id = id,
            projectPath = resolvedPath,
            projectName = projectName,
            command = cleanCmd,
            isRunning = true,
            logs = listOf("$ $cleanCmd")
        )
        mutableServers.update { listOf(session) + it }

        val job = scope.launch {
            val exitCode = EngineExecutor.executeCommandWithUpdates(
                context = context,
                command = cleanCmd,
                workingDir = dir,
                commandKey = commandKey
            ) { rawLine, _ , _ ->
                val clean = TerminalSessionManager.cleanAnsi(rawLine)
                if (clean.isBlank()) return@executeCommandWithUpdates
                val detected = extractLocalhostUrls(clean).firstOrNull()
                if (detected != null) {
                    recordDetectedUrlFromOutput(clean)
                }
                mutableServers.update { list ->
                    list.map { item ->
                        if (item.id != id) item else {
                            val nextLogs = (item.logs + clean).takeLast(300)
                            item.copy(
                                logs = nextLogs,
                                detectedPort = detected?.first ?: item.detectedPort,
                                detectedUrl = detected?.second ?: item.detectedUrl
                            )
                        }
                    }
                }
            }

            mutableServers.update { list ->
                list.map { item ->
                    if (item.id != id) item else item.copy(
                        isRunning = false,
                        lastExitCode = exitCode,
                        logs = (item.logs + "[Process exited with code $exitCode]").takeLast(300)
                    )
                }
            }
            jobs.remove(id)
        }
        jobs[id] = job
        return session
    }

    fun stopServer(id: String) {
        EngineExecutor.stopCommandProcess("devserver_$id")
        jobs.remove(id)?.cancel()
        mutableServers.update { list ->
            list.map { item ->
                if (item.id != id) item else item.copy(
                    isRunning = false,
                    logs = (item.logs + "[Stopped by user]").takeLast(300)
                )
            }
        }
    }

    fun removeServer(id: String) {
        stopServer(id)
        mutableServers.update { list -> list.filterNot { it.id == id } }
    }

    fun stopAll() {
        val ids = jobs.keys.toList()
        ids.forEach { stopServer(it) }
    }
}
