package id.vern.antigravity.data

import org.json.JSONObject

data class SlashAction(
    val cmd: String,
    val descText: String? = null,
    val category: String = "",
)

data class CliSlashCommand(
    val cmd: String,
    val description: String,
)

val SYSTEM_SLASH_COMMANDS: List<SlashAction> = listOf(
    SlashAction("plan", "Create an implementation plan before making edits", "System"),
    SlashAction("boost", "Deep thinking, strategic planning, and rigorous verification", "System"),
    SlashAction("grill-me", "Interactive interview to align on plan and design decisions", "System"),
    SlashAction("teamwork", "Coordinate autonomous subagents working together in parallel", "System"),
    SlashAction("swarm", "Run a multi-agent swarm collaborating on a shared message board", "System"),
    SlashAction("goal", "Run long-running task until completion", "System"),
    SlashAction("schedule", "Schedule a one-shot timer or recurring background task", "System"),
    SlashAction("browser", "Web browsing, searching, or interacting with web applications", "System"),
    SlashAction("learn", "Persist current guidelines, corrections, or setup for future tasks", "System"),
    SlashAction("compact", "Summarize conversation history to free context window", "System"),
    SlashAction("btw", "Ask a quick side question without altering main context", "System"),
    SlashAction("clear", "Clear chat conversation history", "System"),
    SlashAction("help", "Show available commands and guidance", "System"),
)

object SlashCommandParser {
    private val ansiRegex = Regex("\\u001B\\[[0-9;]*[A-Za-z]")
    private val slashLineRegex = Regex("""^/([A-Za-z][A-Za-z0-9_-]*)\b\s*(.*)$""")
    private val subcommandRegex = Regex("""^\s{2,}([a-z][a-z0-9_-]*)\s{2,}(.*)$""")
    private val leadingSepRegex = Regex("""^[,;:–—\-]+\s*""")

    fun parseHelpJson(text: String): List<CliSlashCommand> {
        val seen = LinkedHashMap<String, String>()
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        // Find JSON object line emitted by `agy --output-format json -p /help`
        val jsonLine = trimmed.lines().firstOrNull { line ->
            val t = line.trim()
            t.startsWith("{") && t.endsWith("}")
        } ?: trimmed.takeIf { it.startsWith("{") && it.endsWith("}") }

        if (jsonLine != null) {
            try {
                val root = JSONObject(jsonLine)
                val cmdObj = root.optJSONObject("command")
                val dataObj = cmdObj?.optJSONObject("data")
                val cmdsArr = dataObj?.optJSONArray("commands")
                if (cmdsArr != null) {
                    for (i in 0 until cmdsArr.length()) {
                        val entry = cmdsArr.optJSONObject(i) ?: continue
                        val name = entry.optString("name", "").trim().lowercase().trimStart('/')
                        val desc = entry.optString("description", "").trim()
                        if (name.isNotEmpty() && !seen.containsKey(name)) {
                            seen[name] = desc
                        }
                    }
                }
                if (seen.isEmpty()) {
                    val responseText = root.optString("response", "")
                    for (line in responseText.lines()) {
                        val t = line.trim()
                        if (!t.startsWith("/")) continue
                        val tabIdx = t.indexOf('\t')
                        if (tabIdx > 1) {
                            val namePart = t.substring(1, tabIdx).substringBefore(" ").trim().lowercase()
                            val descPart = t.substring(tabIdx + 1).trim()
                            if (namePart.isNotEmpty() && !seen.containsKey(namePart)) {
                                seen[namePart] = descPart
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        return seen.map { (cmd, desc) -> CliSlashCommand(cmd, desc) }
    }

    fun parse(text: String): List<CliSlashCommand> {
        val clean = ansiRegex.replace(text, "")
        val seen = LinkedHashMap<String, String>()
        var inSubcommands = false

        for (rawLine in clean.lines()) {
            val trimmed = rawLine.trim()
            if (trimmed.equals("Available subcommands:", ignoreCase = true) || trimmed.equals("Subcommands:", ignoreCase = true)) {
                inSubcommands = true
                continue
            }
            if (inSubcommands) {
                if (rawLine.isNotBlank() && !rawLine.startsWith(" ")) {
                    inSubcommands = false
                } else {
                    val subMatch = subcommandRegex.matchEntire(rawLine)
                    if (subMatch != null) {
                        val subCmd = subMatch.groupValues[1].lowercase()
                        val desc = subMatch.groupValues[2].trim()
                        if (subCmd.isNotEmpty() && !seen.containsKey(subCmd)) {
                            seen[subCmd] = desc
                        }
                        continue
                    }
                }
            }

            if (!trimmed.startsWith("/")) continue
            val match = slashLineRegex.matchEntire(trimmed) ?: continue
            val cmd = match.groupValues[1].lowercase()
            var rest = match.groupValues[2].trim()
            rest = leadingSepRegex.replace(rest, "").trim()
            if (cmd.isNotEmpty() && !seen.containsKey(cmd)) {
                seen[cmd] = rest
            }
        }
        return seen.map { (cmd, desc) -> CliSlashCommand(cmd, desc) }
    }
}

fun mergeDynamicSlashActions(
    systemCommands: List<SlashAction>,
    cliCommands: List<CliSlashCommand>,
    skills: List<SlashAction>,
    workflows: List<SlashAction>
): List<SlashAction> {
    val seen = LinkedHashMap<String, SlashAction>()

    for (cli in cliCommands) {
        val key = cli.cmd.lowercase().trimStart('/')
        if (key.isNotEmpty()) {
            seen[key] = SlashAction(key, cli.description.ifBlank { null }, "CLI")
        }
    }

    for (sys in systemCommands) {
        val key = sys.cmd.lowercase().trimStart('/')
        if (key.isNotEmpty() && !seen.containsKey(key)) {
            seen[key] = sys.copy(cmd = key)
        }
    }

    for (wf in workflows) {
        val key = wf.cmd.lowercase().trimStart('/')
        if (key.isNotEmpty() && !seen.containsKey(key)) {
            seen[key] = wf.copy(cmd = key)
        }
    }

    for (skill in skills) {
        val key = skill.cmd.lowercase().trimStart('/')
        if (key.isNotEmpty() && !seen.containsKey(key)) {
            seen[key] = skill.copy(cmd = key)
        }
    }

    return seen.values.toList()
}

fun cliSlashActions(cli: List<CliSlashCommand>): List<SlashAction> {
    return cli.distinctBy { it.cmd }.map { SlashAction(it.cmd, it.description.ifBlank { null }, "CLI") }
}

