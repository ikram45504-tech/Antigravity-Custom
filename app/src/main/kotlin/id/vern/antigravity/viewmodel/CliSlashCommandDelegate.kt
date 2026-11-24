package id.vern.antigravity.viewmodel

import android.content.Context
import android.content.SharedPreferences
import id.vern.antigravity.data.CliSlashCommand
import id.vern.antigravity.data.SYSTEM_SLASH_COMMANDS
import id.vern.antigravity.data.SlashAction
import id.vern.antigravity.data.SlashCommandParser
import id.vern.antigravity.data.mergeDynamicSlashActions
import id.vern.antigravity.executor.EngineExecutor
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

data class SlashRefreshResult(
    val actions: List<SlashAction>,
    val skillCount: Int,
    val workflowCount: Int,
    val cliCount: Int
)

object CliSlashCommandDelegate {
    private const val KEY_CLI_SLASH_COMMANDS = "ag_cli_slash_commands"
    private const val KEY_CLI_SLASH_VERSION = "ag_cli_slash_version"
    private const val KEY_CLI_SLASH_FETCHED_AT = "ag_cli_slash_fetched_at"

    suspend fun refreshSlashActions(
        context: Context,
        prefs: SharedPreferences,
        skills: List<SlashAction>,
        configDir: File,
        workspacePath: String?,
        isExecuting: Boolean
    ): SlashRefreshResult {
        val workflows = getDiscoveredWorkflows(context, configDir, workspacePath)
        val cached = loadCachedSlashCommands(prefs)
        val cachedVersion = prefs.getString(KEY_CLI_SLASH_VERSION, null)

        var cliCommands = cached
        if (!EngineExecutor.isAgyRunning() && !isExecuting) {
            val version = runAgyCapture(context, workspacePath, listOf("--version"), 10_000L)
                ?.second?.lines()?.firstOrNull { it.isNotBlank() }?.trim()

            if (version != null && (version != cachedVersion || cached.isEmpty())) {
                val help = runAgyCapture(context, workspacePath, listOf("--help"), 15_000L)
                val parsed = help?.second?.let { SlashCommandParser.parse(it) } ?: emptyList()
                if (parsed.isNotEmpty()) {
                    saveCachedSlashCommands(prefs, parsed, version)
                    cliCommands = parsed
                }
            }
        }

        val allActions = mergeDynamicSlashActions(
            systemCommands = SYSTEM_SLASH_COMMANDS,
            cliCommands = cliCommands,
            skills = skills,
            workflows = workflows
        )

        return SlashRefreshResult(
            actions = allActions,
            skillCount = skills.size,
            workflowCount = workflows.size,
            cliCount = cliCommands.size
        )
    }

    fun getDiscoveredWorkflows(
        context: Context,
        configDir: File,
        workspacePath: String?
    ): List<SlashAction> {
        val list = mutableListOf<SlashAction>()
        val seen = mutableSetOf<String>()
        val dirs = mutableListOf(
            File(configDir, "workflows"),
            File(context.filesDir, ".gemini/config/workflows"),
            File(context.filesDir, "home/.gemini/config/workflows")
        )
        if (!workspacePath.isNullOrBlank()) {
            val wsDir = File(workspacePath)
            dirs.add(File(wsDir, ".gemini/workflows"))
            dirs.add(File(wsDir, ".agents/workflows"))
            dirs.add(File(wsDir, ".agent/workflows"))
            dirs.add(File(wsDir, "workflows"))
        }
        for (dir in dirs) {
            if (dir.isDirectory) {
                dir.listFiles()?.filter { it.isFile && (it.extension == "md" || it.extension == "prompt" || it.extension == "yaml") }?.forEach { f ->
                    val name = f.nameWithoutExtension.lowercase()
                    if (!seen.contains(name)) {
                        seen.add(name)
                        var desc = "Workflow from ${f.name}"
                        try {
                            val firstLine = f.readLines().firstOrNull { it.isNotBlank() && !it.startsWith("---") && !it.startsWith("#") }
                            if (!firstLine.isNullOrBlank()) desc = firstLine.trim()
                        } catch (_: Exception) {}
                        list.add(SlashAction(name, desc, "Workflow"))
                    }
                }
            }
        }
        return list
    }

    private suspend fun runAgyCapture(
        context: Context,
        workspacePath: String?,
        args: List<String>,
        timeoutMs: Long,
    ): Pair<Int, String>? {
        val sb = StringBuilder()
        val wsDir = run {
            val dir = if (!workspacePath.isNullOrBlank()) File(workspacePath) else null
            if (dir != null && dir.exists() && dir.isDirectory) dir else null
        }
        val exit = withTimeoutOrNull(timeoutMs) {
            EngineExecutor.executeAgy(context, args, workingDir = wsDir) { line, _ ->
                sb.appendLine(line)
            }
        } ?: return null
        if (exit != 0) return null
        return exit to sb.toString()
    }

    private fun loadCachedSlashCommands(prefs: SharedPreferences): List<CliSlashCommand> {
        val raw = prefs.getString(KEY_CLI_SLASH_COMMANDS, null) ?: return emptyList()
        val out = ArrayList<CliSlashCommand>()
        for (line in raw.lines()) {
            if (line.isBlank()) continue
            val tab = line.indexOf('\t')
            if (tab <= 0) continue
            out.add(CliSlashCommand(line.substring(0, tab), line.substring(tab + 1)))
        }
        return out
    }

    private fun saveCachedSlashCommands(
        prefs: SharedPreferences,
        commands: List<CliSlashCommand>,
        version: String?,
    ) {
        val text = commands.joinToString("\n") { cmd ->
            val desc = cmd.description.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
            "${cmd.cmd}\t$desc"
        }
        prefs.edit()
            .putString(KEY_CLI_SLASH_COMMANDS, text)
            .putString(KEY_CLI_SLASH_VERSION, version)
            .putLong(KEY_CLI_SLASH_FETCHED_AT, System.currentTimeMillis())
            .apply()
    }
}
