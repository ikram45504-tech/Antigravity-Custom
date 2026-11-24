package id.vern.antigravity.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveVoiceControllerTest {

    @Test
    fun `LiveVoiceUiState defaults to inactive IDLE and unmuted`() {
        val state = LiveVoiceUiState()
        assertFalse(state.active)
        assertEquals(LiveVoicePhase.IDLE, state.phase)
        assertEquals("", state.partialTranscript)
        assertFalse(state.isMuted)
        assertEquals("", state.statusDetail)
    }

    @Test
    fun `LiveVoiceSanitizer handles tables quotes headings and sentence truncation`() {
        val markdown = """
            > Quoted note
            # Heading
            | ColA | ColB |
            | 1 | 2 |
            Read `MainActivity.kt` and check [docs](https://example.com).
        """.trimIndent()

        val cleaned = LiveVoiceSanitizer.sanitizeForSpeech(markdown)
        assertFalse(cleaned.contains("|"))
        assertFalse(cleaned.contains(">"))
        assertFalse(cleaned.contains("#"))
        assertFalse(cleaned.contains("https://"))
        assertTrue(cleaned.contains("MainActivity.kt"))
        assertTrue(cleaned.contains("docs"))

        val sentenceText = "First sentence ends cleanly. Second sentence continues with extra words that exceed the cap."
        val truncated = LiveVoiceSanitizer.sanitizeForSpeech(sentenceText, maxChars = 40)
        assertEquals("First sentence ends cleanly.", truncated)
    }
}
