package id.vern.antigravity.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelSelectorDialogTest {

    @Test
    fun `resolvedEffortFor clears effort for models without effort variants`() {
        val claude = DisplayAiModel(
            id = "Claude Sonnet 4.6",
            name = "Claude Sonnet 4.6",
            provider = "Anthropic",
            description = "",
            cliId = "claude-sonnet-4-6",
            effortCliIds = emptyMap(),
            efforts = emptyList()
        )
        assertEquals("", claude.resolvedEffortFor("High"))
        assertEquals("claude-sonnet-4-6", claude.cliIdFor(claude.resolvedEffortFor("High")))
    }

    @Test
    fun `resolvedEffortFor preserves supported effort or falls back cleanly`() {
        val hydrogen = DisplayAiModel(
            id = "Gemini 4 Hydrogen",
            name = "Gemini 4 Hydrogen",
            provider = "Google",
            description = "",
            effortCliIds = mapOf(
                "Low" to "gemini-4-hydrogen-low",
                "Medium" to "gemini-4-hydrogen-medium",
                "High" to "gemini-4-hydrogen-high"
            ),
            efforts = listOf("Low", "Medium", "High")
        )
        assertEquals("High", hydrogen.resolvedEffortFor("High"))
        assertEquals("Medium", hydrogen.resolvedEffortFor(""))

        val gptOss = DisplayAiModel(
            id = "GPT-OSS 120B",
            name = "GPT-OSS 120B",
            provider = "OpenAI",
            description = "",
            effortCliIds = mapOf("Medium" to "gpt-oss-120b-medium"),
            efforts = listOf("Medium")
        )
        assertEquals("Medium", gptOss.resolvedEffortFor("High"))
    }
}
