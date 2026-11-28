package id.vern.antigravity.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgyModelsServiceTest {

    @Test
    fun `isRealAiModel filters internal disabled and embedding models`() {
        assertTrue(AgyModelsService.isRealAiModel("gemini-2.5-pro", "Gemini 2.5 Pro"))
        assertTrue(AgyModelsService.isRealAiModel("claude-sonnet-4", "Claude Sonnet 4"))
        assertTrue(AgyModelsService.isRealAiModel("gpt-5", "GPT-5"))

        assertFalse(AgyModelsService.isRealAiModel("chat_internal", "Internal"))
        assertFalse(AgyModelsService.isRealAiModel("tab_autocomplete", "Tab"))
        assertFalse(AgyModelsService.isRealAiModel("text-embedding-004", "Embedding"))
        assertFalse(AgyModelsService.isRealAiModel("gemini-pro-disabled", "Gemini Pro"))
        assertFalse(AgyModelsService.isRealAiModel("", ""))
    }

    @Test
    fun `providerForDisplayName classifies supported model providers`() {
        assertEquals("Google", AgyModelsService.providerForDisplayName("Gemini 4 Hydrogen"))
        assertEquals("Anthropic", AgyModelsService.providerForDisplayName("Claude 4 Opus"))
        assertEquals("OpenAI", AgyModelsService.providerForDisplayName("GPT-4o"))
        assertEquals("", AgyModelsService.providerForDisplayName("UnknownModel"))
    }

    @Test
    fun `normalizeEffort maps valid levels and rejects blanks`() {
        assertEquals("High", AgyModelsService.normalizeEffort("high"))
        assertEquals("Medium", AgyModelsService.normalizeEffort(" medium "))
        assertEquals("XHigh", AgyModelsService.normalizeEffort("xhigh"))
        assertNull(AgyModelsService.normalizeEffort(""))
        assertNull(AgyModelsService.normalizeEffort(null))
    }

    @Test
    fun `supportsEffortFlag disables --effort for claude and effort-suffixed models`() {
        assertFalse(AgyModelsService.supportsEffortFlag("claude-sonnet-4-6"))
        assertFalse(AgyModelsService.supportsEffortFlag("claude-opus-4-6-thinking"))
        assertFalse(AgyModelsService.supportsEffortFlag("gemini-4-hydrogen-high"))
        assertFalse(AgyModelsService.supportsEffortFlag("gpt-oss-120b-medium"))
        assertFalse(AgyModelsService.supportsEffortFlag(""))
        assertTrue(AgyModelsService.supportsEffortFlag("gemini-2.5-pro"))
    }
}
