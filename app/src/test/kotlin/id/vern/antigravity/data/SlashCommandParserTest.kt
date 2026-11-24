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
}
