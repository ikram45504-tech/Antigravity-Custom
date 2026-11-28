package id.vern.antigravity.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPreferencesDelegateTest {

    @Test
    fun `applyConversationModel updates active model and sanitizes effort for Claude`() {
        val delegate = SettingsPreferencesDelegate()

        delegate.applyConversationModel(
            context = null,
            modelName = "Gemini 4 Hydrogen",
            cliId = "gemini-4-hydrogen-medium",
            budget = "Medium"
        )
        assertEquals("Gemini 4 Hydrogen", delegate.selectedModel.value)
        assertEquals("gemini-4-hydrogen-medium", delegate.selectedModelCliId.value)
        assertEquals("Medium", delegate.thinkingBudget.value)

        delegate.applyConversationModel(
            context = null,
            modelName = "Claude Sonnet 4.6 (Thinking)",
            cliId = "claude-sonnet-4-6",
            budget = "High"
        )
        assertEquals("Claude Sonnet 4.6 (Thinking)", delegate.selectedModel.value)
        assertEquals("claude-sonnet-4-6", delegate.selectedModelCliId.value)
        assertEquals("", delegate.thinkingBudget.value)
    }
}
