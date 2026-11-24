package id.vern.antigravity.viewmodel

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptTurnParserTest {

    @Test
    fun `extractPathFromParams resolves known path keys and falls back to path-like values`() {
        val primary = JSONObject().put("AbsolutePath", "\"/tmp/example/Main.kt\"")
        assertEquals("/tmp/example/Main.kt", TranscriptTurnParser.extractPathFromParams(primary))

        val fallback = JSONObject().put("customTarget", "app/src/Build.kt")
        assertEquals("app/src/Build.kt", TranscriptTurnParser.extractPathFromParams(fallback))

        assertEquals("", TranscriptTurnParser.extractPathFromParams(null))
    }

    @Test
    fun `extractFileNameFromPath handles empty and nested paths`() {
        assertEquals("Main.kt", TranscriptTurnParser.extractFileNameFromPath("/tmp/example/Main.kt"))
        assertEquals("file", TranscriptTurnParser.extractFileNameFromPath("   ", fallback = "file"))
    }

    @Test
    fun `parseIsoTimestamp handles valid and invalid ISO timestamps`() {
        assertTrue(TranscriptTurnParser.parseIsoTimestamp("2026-11-24T20:30:00Z") > 0L)
        assertEquals(0L, TranscriptTurnParser.parseIsoTimestamp("not-a-timestamp"))
        assertEquals(0L, TranscriptTurnParser.parseIsoTimestamp(null))
    }

    @Test
    fun `computeEditStepDiff handles edit_file write_file and multi_replace_file_content keys`() {
        val editFileParams = JSONObject()
            .put("Target", "line1\noldA\noldB\nline4")
            .put("Replacement", "line1\nnewA\nnewB\nnewC\nline4")
        assertEquals("+3 -2", TranscriptTurnParser.computeEditStepDiff(editFileParams))

        val writeFileParams = JSONObject()
            .put("Content", "pkg foo\n\nclass Bar\n")
        assertEquals("+4 -0", TranscriptTurnParser.computeEditStepDiff(writeFileParams))

        val multiParams = JSONObject().put(
            "ReplacementChunks",
            JSONArray()
                .put(JSONObject().put("TargetContent", "a").put("ReplacementContent", "a1\na2"))
                .put(JSONObject().put("TargetContent", "b1\nb2").put("ReplacementContent", "b"))
        )
        assertEquals("+3 -3", TranscriptTurnParser.computeEditStepDiff(multiParams))

        assertNull(TranscriptTurnParser.computeEditStepDiff(JSONObject().put("Path", "Foo.kt")))
    }
}
