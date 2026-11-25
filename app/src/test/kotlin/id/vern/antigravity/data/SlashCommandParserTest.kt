package id.vern.antigravity.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlashCommandParserTest {

    @Test
    fun `parse extracts slash commands and subcommands stripping ansi codes`() {
        val helpOutput = """
            \u001B[1mCommands:\u001B[0m
            /compact - Summarize conversation history
            /model : Switch active AI model

            Available subcommands:
              login    Authenticate with Google
              status   Show engine status
        """.trimIndent().replace("\\u001B", "\u001B")

        val parsed = SlashCommandParser.parse(helpOutput)
        val byCmd = parsed.associateBy { it.cmd }

        assertEquals("Summarize conversation history", byCmd["compact"]?.description)
        assertEquals("Switch active AI model", byCmd["model"]?.description)
        assertEquals("Authenticate with Google", byCmd["login"]?.description)
        assertEquals("Show engine status", byCmd["status"]?.description)
    }

    @Test
    fun `mergeDynamicSlashActions deduplicates prioritizing CLI commands`() {
        val merged = mergeDynamicSlashActions(
            systemCommands = listOf(SlashAction("help", "Sys help", "System")),
            cliCommands = listOf(CliSlashCommand("help", "CLI help"), CliSlashCommand("status", "CLI status")),
            skills = listOf(SlashAction("status", "Skill status", "Skill"), SlashAction("my-skill", "Custom skill", "Skill")),
            workflows = listOf(SlashAction("deploy", "Deploy workflow", "Workflow"))
        )

        val byCmd = merged.associateBy { it.cmd }
        assertEquals("CLI", byCmd["help"]?.category)
        assertEquals("CLI help", byCmd["help"]?.descText)
        assertEquals("CLI", byCmd["status"]?.category)
        assertTrue(byCmd.containsKey("deploy"))
        assertTrue(byCmd.containsKey("my-skill"))
    }

    @Test
    fun `parseHelpJson extracts real slash commands from agy json help output`() {
        val jsonOutput = """
            {"conversation_id":"","status":"SUCCESS","response":"/help\tShow help\n","command":{"name":"help","data":{"commands":[{"name":"agents","description":"List available custom agents"},{"name":"effort","description":"Set the reasoning effort"},{"name":"help","description":"Show available commands and keybindings"}]}}}
        """.trimIndent()

        val parsed = SlashCommandParser.parseHelpJson(jsonOutput)
        val byCmd = parsed.associateBy { it.cmd }

        assertEquals(3, parsed.size)
        assertEquals("List available custom agents", byCmd["agents"]?.description)
        assertEquals("Set the reasoning effort", byCmd["effort"]?.description)
        assertEquals("Show available commands and keybindings", byCmd["help"]?.description)
    }

    @Test
    fun `system slash commands match agy v1_5_17 command set`() {
        val cmds = SYSTEM_SLASH_COMMANDS.map { it.cmd }.toSet()
        assertTrue("plan" in cmds)
        assertTrue("boost" in cmds)
        assertTrue("grill-me" in cmds)
        assertTrue("swarm" in cmds)
        assertTrue("teamwork" in cmds)
        assertTrue("compact" in cmds)
        assertTrue("btw" in cmds)
        assertTrue("deep-plan" !in cmds)
        assertTrue("code-review" !in cmds)
    }

    @Test
    fun `formatCommandResultMarkdown formats usage and help cleanly`() {
        val helpCmd = org.json.JSONObject(
            """{"name":"help","data":{"commands":[{"name":"usage","aliases":["quota"],"description":"View model quota usage"}]}}"""
        )
        val helpMd = id.vern.antigravity.viewmodel.TurnExecutionDelegate.formatCommandResultMarkdown(helpCmd, null) ?: ""
        assertTrue(helpMd.contains("### Available CLI Commands"))
        assertTrue(helpMd.contains("`/usage` (`/quota`) — View model quota usage"))

        val usageCmd = org.json.JSONObject(
            """{"name":"usage","data":{"groups":[{"name":"Gemini Models","buckets":[{"name":"Five Hour Limit Remaining","remaining_fraction":0.5,"description":"Refreshes soon"}]}]}}"""
        )
        val usageMd = id.vern.antigravity.viewmodel.TurnExecutionDelegate.formatCommandResultMarkdown(usageCmd, null) ?: ""
        assertTrue(usageMd.contains("### Model Quota Usage"))
        assertTrue(usageMd.contains("**Gemini Models**"))
        assertTrue(usageMd.contains("`50%`"))
    }
}
