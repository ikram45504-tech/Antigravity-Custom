package id.vern.antigravity.viewmodel

import id.vern.antigravity.data.AttachedFile
import org.junit.Assert.assertEquals
import org.junit.Test

class UndoPromptRestoreTest {

    @Test
    fun `mergeRestoredAttachments restores undone files without duplicates and caps at 5`() {
        val a1 = AttachedFile(id = "1", name = "a.png", uri = "file:///a.png", mimeType = "image/png", sizeBytes = 10L)
        val a2 = AttachedFile(id = "2", name = "b.txt", uri = "file:///b.txt", mimeType = "text/plain", sizeBytes = 20L)
        val a3 = AttachedFile(id = "3", name = "c.kt", uri = "file:///c.kt", mimeType = "text/plain", sizeBytes = 30L)
        val a4 = AttachedFile(id = "4", name = "d.kt", uri = "file:///d.kt", mimeType = "text/plain", sizeBytes = 40L)
        val a5 = AttachedFile(id = "5", name = "e.kt", uri = "file:///e.kt", mimeType = "text/plain", sizeBytes = 50L)
        val a6 = AttachedFile(id = "6", name = "f.kt", uri = "file:///f.kt", mimeType = "text/plain", sizeBytes = 60L)

        val merged = mergeRestoredAttachments(
            undoneAttachments = listOf(a1, a2, a3),
            existingAttachments = listOf(a2, a4, a5, a6)
        )
        assertEquals(listOf(a1, a2, a3, a4, a5), merged)
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
