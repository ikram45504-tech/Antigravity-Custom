package id.vern.antigravity.service

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptCompletionNotifierTest {

    @Test
    fun `ten distinct small built-in tunes are registered with labels and descriptions`() {
        assertEquals(10, PromptCompletionNotifier.BUILT_IN_TONES.size)
        assertEquals(10, PromptCompletionNotifier.BUILT_IN_TONES.map { it.id }.distinct().size)
        assertEquals(10, PromptCompletionNotifier.BUILT_IN_TONES.map { it.rawResId }.distinct().size)
        assertEquals(PromptCompletionNotifier.SOUND_SIMPLE, PromptCompletionNotifier.SOUND_OPTIONS.first())
        assertTrue(PromptCompletionNotifier.SOUND_OPTIONS.containsAll(PromptCompletionNotifier.BUILT_IN_TONES.map { it.id }))
        assertTrue(PromptCompletionNotifier.SOUND_OPTIONS.contains(PromptCompletionNotifier.SOUND_SYSTEM))
        assertTrue(PromptCompletionNotifier.SOUND_OPTIONS.contains(PromptCompletionNotifier.SOUND_CUSTOM))
        assertTrue(PromptCompletionNotifier.SOUND_OPTIONS.contains(PromptCompletionNotifier.SOUND_SILENT))
        assertFalse(PromptCompletionNotifier.SOUND_OPTIONS.contains(PromptCompletionNotifier.SOUND_FLUTE))

        for (tone in PromptCompletionNotifier.BUILT_IN_TONES) {
            assertEquals(tone.shortLabel, PromptCompletionNotifier.getToneShortLabel(tone.id))
            assertEquals(tone.description, PromptCompletionNotifier.getToneDescription(tone.id))
        }
        assertEquals("Phone", PromptCompletionNotifier.getToneShortLabel(PromptCompletionNotifier.SOUND_SYSTEM))
        assertEquals("Custom: Bell", PromptCompletionNotifier.getToneDescription(PromptCompletionNotifier.SOUND_CUSTOM, "Bell"))
    }

    @Test
    fun `buzz options and vibration patterns stay simple and valid`() {
        assertEquals(PromptCompletionNotifier.BUZZ_GENTLE, PromptCompletionNotifier.BUZZ_OPTIONS.first())
        assertArrayEquals(
            longArrayOf(0, 140),
            PromptCompletionNotifier.getVibrationPattern(PromptCompletionNotifier.BUZZ_GENTLE)
        )
        assertArrayEquals(
            longArrayOf(0, 120, 100, 120),
            PromptCompletionNotifier.getVibrationPattern(PromptCompletionNotifier.BUZZ_DOUBLE)
        )
        assertNull(PromptCompletionNotifier.getVibrationPattern(PromptCompletionNotifier.BUZZ_NONE))
    }
}
