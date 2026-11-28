package id.vern.antigravity.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class UndoPromptRestoreTest {

    @Test
    fun `mergeByUniqueKey restores undone items without duplicates and caps at 5`() {
        val merged = mergeByUniqueKey(
            undone = listOf("1:a.png", "2:b.txt", "3:c.kt"),
            existing = listOf("2:b.txt", "4:d.kt", "5:e.kt", "6:f.kt"),
            maxItems = 5
        ) { it.substringBefore(':') }

        assertEquals(listOf("1:a.png", "2:b.txt", "3:c.kt", "4:d.kt", "5:e.kt"), merged)
    }

    @Test
    fun `resolveRestoredPromptText clears placeholder when attachment-only prompt is undone`() {
        assertEquals(
            "",
            resolveRestoredPromptText(
                promptText = "Please analyze the attached file(s).",
                attachmentOnlyPlaceholder = "Please analyze the attached file(s).",
                hasAttachments = true
            )
        )
        assertEquals(
            "Check this screenshot",
            resolveRestoredPromptText(
                promptText = "Check this screenshot",
                attachmentOnlyPlaceholder = "Please analyze the attached file(s).",
                hasAttachments = true
            )
        )
    }
}
