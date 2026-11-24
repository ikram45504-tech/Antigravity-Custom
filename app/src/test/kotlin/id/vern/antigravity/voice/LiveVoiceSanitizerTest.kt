package id.vern.antigravity.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveVoiceSanitizerTest {

    @Test
    fun `sanitizeForSpeech strips markdown formatting code blocks and file URIs`() {
        val input = """
            ### Summary
            I updated [implementation_plan.md](file:///data/user/0/plan.md) and **fixed** `EngineExecutor.kt`.
            ```kotlin
            fun test() = Unit
            ```
            Check https://example.com for details.
        """.trimIndent()

        val spoken = LiveVoiceSanitizer.sanitizeForSpeech(input)
        assertFalse(spoken.contains("```"))
        assertFalse(spoken.contains("file://"))
        assertFalse(spoken.contains("https://"))
        assertFalse(spoken.contains("**"))
        assertTrue(spoken.contains("implementation_plan.md"))
        assertTrue(spoken.contains("Code snippet omitted."))
        assertTrue(spoken.contains("EngineExecutor.kt"))
    }

    @Test
    fun `sanitizeForSpeech truncates long output cleanly`() {
        val longText = "First sentence is here. " + "word ".repeat(300)
        val spoken = LiveVoiceSanitizer.sanitizeForSpeech(longText, maxChars = 80)
        assertTrue(spoken.length <= 83)
        assertEquals("", LiveVoiceSanitizer.sanitizeForSpeech("   "))
    }
}
