package id.vern.antigravity.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiLiveClientTest {

    @Test
    fun parseServerMessage_setupComplete() {
        val events = GeminiLiveClient.parseServerMessage("""{"setupComplete": {}}""")
        assertEquals(listOf(GeminiLiveEvent.SetupComplete), events)
    }

    @Test
    fun parseServerMessage_audioAndTranscriptionsAndInterrupted() {
        val json = """
            {
              "serverContent": {
                "interrupted": true,
                "inputTranscription": {"text": "check package json"},
                "outputTranscription": {"text": "Sure, reading it now."},
                "modelTurn": {
                  "parts": [
                    {"inlineData": {"mimeType": "audio/pcm;rate=24000", "data": "AAAA"}},
                    {"text": "Reading file"}
                  ]
                },
                "turnComplete": true
              }
            }
        """.trimIndent()
        val events = GeminiLiveClient.parseServerMessage(json)
        assertTrue(events.contains(GeminiLiveEvent.Interrupted))
        assertTrue(events.contains(GeminiLiveEvent.InputTranscription("check package json")))
        assertTrue(events.contains(GeminiLiveEvent.OutputTranscription("Sure, reading it now.")))
        assertTrue(events.contains(GeminiLiveEvent.AudioChunk("AAAA")))
        assertTrue(events.contains(GeminiLiveEvent.ModelText("Reading file")))
        assertTrue(events.contains(GeminiLiveEvent.TurnComplete))
    }

    @Test
    fun parseServerMessage_toolCall() {
        val json = """
            {
              "toolCall": {
                "functionCalls": [
                  {"id": "call_1", "name": "read_workspace_file", "args": {"path": "README.md"}}
                ]
              }
            }
        """.trimIndent()
        val events = GeminiLiveClient.parseServerMessage(json)
        assertEquals(1, events.size)
        val tc = events[0] as GeminiLiveEvent.ToolCall
        assertEquals("call_1", tc.calls[0].id)
        assertEquals("read_workspace_file", tc.calls[0].name)
        assertEquals("README.md", tc.calls[0].args.getString("path"))
    }

    @Test
    fun parseServerMessage_writeAndDelegateToolCalls() {
        val json = """
            {
              "toolCall": {
                "functionCalls": [
                  {"id": "call_2", "name": "write_workspace_file", "args": {"path": "notes.txt", "content": "hi"}},
                  {"id": "call_3", "name": "delegate_to_agy_agent", "args": {"task": "Fix navbar swipe"}}
                ]
              }
            }
        """.trimIndent()
        val events = GeminiLiveClient.parseServerMessage(json)
        assertEquals(1, events.size)
        val tc = events[0] as GeminiLiveEvent.ToolCall
        assertEquals(2, tc.calls.size)
        assertEquals("write_workspace_file", tc.calls[0].name)
        assertEquals("notes.txt", tc.calls[0].args.getString("path"))
        assertEquals("delegate_to_agy_agent", tc.calls[1].name)
        assertEquals("Fix navbar swipe", tc.calls[1].args.getString("task"))
    }

    @Test
    fun computeNormalizedRms_silenceAndSignal() {
        val silence = ByteArray(16)
        assertEquals(0f, PcmAudioEngine.computeNormalizedRms(silence), 0.0001f)

        val loud = ByteArray(4) { 0 }
        // Little-endian 16384 = 0x4000
        loud[0] = 0x00
        loud[1] = 0x40
        loud[2] = 0x00
        loud[3] = 0x40
        val rms = PcmAudioEngine.computeNormalizedRms(loud)
        assertTrue(rms > 0.5f && rms <= 1.0f)
    }
}
