package id.vern.antigravity.voice

import android.content.Context
import android.util.Base64
import id.vern.antigravity.oauth.OAuthManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class GeminiLiveConnectionState {
    IDLE,
    CONNECTING,
    READY,
    LISTENING,
    SPEAKING,
    ERROR
}

data class LiveToolCall(
    val id: String,
    val name: String,
    val args: JSONObject
)

sealed class GeminiLiveEvent {
    data object SetupComplete : GeminiLiveEvent()
    data class AudioChunk(val base64Pcm: String) : GeminiLiveEvent()
    data class InputTranscription(val text: String) : GeminiLiveEvent()
    data class OutputTranscription(val text: String) : GeminiLiveEvent()
    data class ModelText(val text: String) : GeminiLiveEvent()
    data object Interrupted : GeminiLiveEvent()
    data object TurnComplete : GeminiLiveEvent()
    data class ToolCall(val calls: List<LiveToolCall>) : GeminiLiveEvent()
    data class Error(val message: String) : GeminiLiveEvent()
}

data class GeminiLiveUiState(
    val active: Boolean = false,
    val connectionState: GeminiLiveConnectionState = GeminiLiveConnectionState.IDLE,
    val isMuted: Boolean = false,
    val userTranscript: String = "",
    val modelTranscript: String = "",
    val activeToolName: String? = null,
    val statusMessage: String = ""
)

data class GeminiLiveConfig(
    val model: String = "gemini-2.5-flash-native-audio-preview-12-2025",
    val apiKey: String = "",
    val vertexProjectId: String = "",
    val vertexLocation: String = "us-central1",
    val voiceName: String = "Aoede",
    val systemInstruction: String = "You are Antigravity, a helpful voice coding assistant. " +
        "When the user asks for reviews, ratings, explanations, or suggestions, inspect files read-only without modifying anything. " +
        "Only call write_workspace_file or delegate_to_agy_agent when the user explicitly asks you to implement, edit, or execute changes. " +
        "Be concise, natural, and conversational."
)

/**
 * Bidirectional WebSocket client for Gemini Multimodal Live (`BidiGenerateContent`).
 * Authenticates via OAuthManager (same as official Antigravity) or an optional API key,
 * streams 16 kHz PCM audio, plays 24 kHz PCM responses, and bridges workspace tool calls.
 */
class GeminiLiveClient(
    private val context: Context,
    private val audioEngine: PcmAudioEngine = PcmAudioEngine(),
    private val onExecuteTool: suspend (LiveToolCall) -> JSONObject = { JSONObject().put("error", "No tool handler") },
    private val onTurnFinished: (userText: String, modelText: String) -> Unit = { _, _ -> },
    private val onAuthFallbackRequired: (reason: String) -> Unit = {}
) {
    companion object {
        private const val GEMINI_LIVE_WS_BASE =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"

        /**
         * Pure JSON parser for Gemini Live `BidiGenerateContent` server frames.
         */
        fun parseServerMessage(jsonText: String): List<GeminiLiveEvent> {
            val events = mutableListOf<GeminiLiveEvent>()
            val root = runCatching { JSONObject(jsonText) }.getOrNull() ?: return events

            root.optJSONObject("error")?.let { err ->
                val msg = err.optString("message", "Server error")
                events.add(GeminiLiveEvent.Error(msg))
                return events
            }

            if (root.has("setupComplete")) {
                events.add(GeminiLiveEvent.SetupComplete)
            }

            root.optJSONObject("toolCall")?.let { tc ->
                val callsArr = tc.optJSONArray("functionCalls")
                if (callsArr != null && callsArr.length() > 0) {
                    val parsed = mutableListOf<LiveToolCall>()
                    for (i in 0 until callsArr.length()) {
                        val c = callsArr.optJSONObject(i) ?: continue
                        val id = c.optString("id", "")
                        val name = c.optString("name", "")
                        val args = c.optJSONObject("args") ?: JSONObject()
                        if (name.isNotBlank()) parsed.add(LiveToolCall(id = id, name = name, args = args))
                    }
                    if (parsed.isNotEmpty()) events.add(GeminiLiveEvent.ToolCall(parsed))
                }
            }

            root.optJSONObject("serverContent")?.let { sc ->
                if (sc.optBoolean("interrupted", false)) {
                    events.add(GeminiLiveEvent.Interrupted)
                }

                sc.optJSONObject("inputTranscription")?.optString("text")?.takeIf { it.isNotEmpty() }?.let { txt ->
                    events.add(GeminiLiveEvent.InputTranscription(txt))
                }
                sc.optJSONObject("outputTranscription")?.optString("text")?.takeIf { it.isNotEmpty() }?.let { txt ->
                    events.add(GeminiLiveEvent.OutputTranscription(txt))
                }

                sc.optJSONObject("modelTurn")?.optJSONArray("parts")?.let { parts ->
                    for (i in 0 until parts.length()) {
                        val part = parts.optJSONObject(i) ?: continue
                        val inlineData = part.optJSONObject("inlineData")
                        if (inlineData != null) {
                            val mime = inlineData.optString("mimeType", "")
                            val data = inlineData.optString("data", "")
                            if (data.isNotEmpty() && (mime.isEmpty() || mime.startsWith("audio/"))) {
                                events.add(GeminiLiveEvent.AudioChunk(data))
                            }
                        }
                        part.optString("text").takeIf { it.isNotEmpty() }?.let { txt ->
                            events.add(GeminiLiveEvent.ModelText(txt))
                        }
                    }
                }

                if (sc.optBoolean("turnComplete", false)) {
                    events.add(GeminiLiveEvent.TurnComplete)
                }
            }

            return events
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val mutableState = MutableStateFlow(GeminiLiveUiState())
    val state: StateFlow<GeminiLiveUiState> = mutableState.asStateFlow()

    val micAmplitude: StateFlow<Float> = audioEngine.micAmplitude
    val speakerAmplitude: StateFlow<Float> = audioEngine.speakerAmplitude

    @Volatile
    private var webSocket: WebSocket? = null
    private val userTurnSb = StringBuilder()
    private val modelTurnSb = StringBuilder()

    fun start(config: GeminiLiveConfig) {
        stop()
        userTurnSb.clear()
        modelTurnSb.clear()
        mutableState.value = GeminiLiveUiState(
            active = true,
            connectionState = GeminiLiveConnectionState.CONNECTING,
            statusMessage = "Connecting to Live..."
        )

        scope.launch {
            val apiKey = config.apiKey.trim()
            val vertexProj = config.vertexProjectId.trim()
            var bearerToken: String? = null

            if (apiKey.isEmpty()) {
                OAuthManager.refreshTokenIfNeeded(context)
                bearerToken = OAuthManager.getAccessToken(context)
                if (bearerToken.isNullOrBlank()) {
                    mutableState.update {
                        it.copy(
                            active = false,
                            connectionState = GeminiLiveConnectionState.ERROR,
                            statusMessage = "No OAuth token or API key available"
                        )
                    }
                    onAuthFallbackRequired("No OAuth token or API key available")
                    return@launch
                }
            }

            val wsUrl = when {
                apiKey.isNotEmpty() -> "$GEMINI_LIVE_WS_BASE?key=$apiKey"
                vertexProj.isNotEmpty() ->
                    "wss://${config.vertexLocation}-aiplatform.googleapis.com/ws/google.cloud.aiplatform.v1beta1.LlmBidiService/BidiGenerateContent"
                else -> GEMINI_LIVE_WS_BASE
            }

            val modelResource = when {
                vertexProj.isNotEmpty() ->
                    "projects/$vertexProj/locations/${config.vertexLocation}/publishers/google/models/${config.model}"
                config.model.startsWith("models/") -> config.model
                else -> "models/${config.model}"
            }

            val reqBuilder = Request.Builder().url(wsUrl)
            if (!bearerToken.isNullOrBlank() && apiKey.isEmpty()) {
                reqBuilder.addHeader("Authorization", "Bearer $bearerToken")
            }

            webSocket = httpClient.newWebSocket(reqBuilder.build(), object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    val setupFrame = buildSetupFrame(modelResource, config)
                    ws.send(setupFrame.toString())
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    handleServerPayload(text)
                }

                override fun onMessage(ws: WebSocket, bytes: ByteString) {
                    handleServerPayload(bytes.utf8())
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    val code = response?.code ?: -1
                    val msg = t.message ?: "Connection failed ($code)"
                    audioEngine.stop()
                    if (code == 401 || code == 403) {
                        mutableState.update {
                            it.copy(
                                active = false,
                                connectionState = GeminiLiveConnectionState.ERROR,
                                statusMessage = "Auth rejected ($code); falling back"
                            )
                        }
                        onAuthFallbackRequired("Live API auth rejected ($code)")
                    } else {
                        mutableState.update {
                            it.copy(
                                connectionState = GeminiLiveConnectionState.ERROR,
                                statusMessage = msg
                            )
                        }
                    }
                }

                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    audioEngine.stop()
                    mutableState.update {
                        it.copy(
                            active = false,
                            connectionState = GeminiLiveConnectionState.IDLE,
                            statusMessage = ""
                        )
                    }
                }
            })
        }
    }

    fun toggleMute(): Boolean {
        val next = !mutableState.value.isMuted
        audioEngine.setMuted(next)
        mutableState.update { it.copy(isMuted = next) }
        return next
    }

    fun stop() {
        runCatching { webSocket?.close(1000, "User ended session") }
        webSocket = null
        audioEngine.stop()
        mutableState.value = GeminiLiveUiState(
            active = false,
            connectionState = GeminiLiveConnectionState.IDLE
        )
    }

    private fun handleServerPayload(jsonText: String) {
        for (event in parseServerMessage(jsonText)) {
            when (event) {
                is GeminiLiveEvent.SetupComplete -> {
                    runCatching {
                        audioEngine.startPlayback()
                        audioEngine.startCapture { pcmChunk ->
                            sendAudioChunk(pcmChunk)
                        }
                        mutableState.update {
                            it.copy(
                                connectionState = GeminiLiveConnectionState.LISTENING,
                                statusMessage = "Listening..."
                            )
                        }
                    }.onFailure { err ->
                        mutableState.update {
                            it.copy(
                                connectionState = GeminiLiveConnectionState.ERROR,
                                statusMessage = err.message ?: "Mic error"
                            )
                        }
                    }
                }
                is GeminiLiveEvent.AudioChunk -> {
                    val pcmBytes = runCatching {
                        Base64.decode(event.base64Pcm, Base64.DEFAULT)
                    }.getOrNull()
                    if (pcmBytes != null && pcmBytes.isNotEmpty()) {
                        audioEngine.enqueuePlayback(pcmBytes)
                        mutableState.update {
                            it.copy(
                                connectionState = GeminiLiveConnectionState.SPEAKING,
                                statusMessage = "Speaking..."
                            )
                        }
                    }
                }
                is GeminiLiveEvent.InputTranscription -> {
                    userTurnSb.append(event.text)
                    mutableState.update { it.copy(userTranscript = userTurnSb.toString().trim()) }
                }
                is GeminiLiveEvent.OutputTranscription -> {
                    modelTurnSb.append(event.text)
                    mutableState.update { it.copy(modelTranscript = modelTurnSb.toString().trim()) }
                }
                is GeminiLiveEvent.ModelText -> {
                    modelTurnSb.append(event.text)
                    mutableState.update { it.copy(modelTranscript = modelTurnSb.toString().trim()) }
                }
                is GeminiLiveEvent.Interrupted -> {
                    audioEngine.flushPlayback()
                    mutableState.update {
                        it.copy(
                            connectionState = GeminiLiveConnectionState.LISTENING,
                            statusMessage = "Listening..."
                        )
                    }
                }
                is GeminiLiveEvent.TurnComplete -> {
                    val finalUser = userTurnSb.toString().trim()
                    val finalModel = modelTurnSb.toString().trim()
                    userTurnSb.clear()
                    modelTurnSb.clear()
                    if (finalUser.isNotEmpty() || finalModel.isNotEmpty()) {
                        onTurnFinished(finalUser, finalModel)
                    }
                    mutableState.update {
                        it.copy(
                            connectionState = GeminiLiveConnectionState.LISTENING,
                            activeToolName = null,
                            statusMessage = "Listening..."
                        )
                    }
                }
                is GeminiLiveEvent.ToolCall -> {
                    handleToolCalls(event.calls)
                }
                is GeminiLiveEvent.Error -> {
                    mutableState.update {
                        it.copy(
                            connectionState = GeminiLiveConnectionState.ERROR,
                            statusMessage = event.message
                        )
                    }
                }
            }
        }
    }

    private fun handleToolCalls(calls: List<LiveToolCall>) {
        scope.launch {
            val responses = JSONArray()
            for (call in calls) {
                mutableState.update { it.copy(activeToolName = call.name, statusMessage = "Running ${call.name}...") }
                val result = runCatching { onExecuteTool(call) }
                    .getOrElse { JSONObject().put("error", it.message ?: "Tool failed") }
                responses.put(
                    JSONObject().apply {
                        put("id", call.id)
                        put("name", call.name)
                        put("response", result)
                    }
                )
            }
            mutableState.update { it.copy(activeToolName = null) }
            val payload = JSONObject().apply {
                put("toolResponse", JSONObject().apply {
                    put("functionResponses", responses)
                })
            }
            webSocket?.send(payload.toString())
        }
    }

    private fun sendAudioChunk(pcmBytes: ByteArray) {
        val ws = webSocket ?: return
        val b64 = Base64.encodeToString(pcmBytes, Base64.NO_WRAP)
        val frame = JSONObject().apply {
            put("realtimeInput", JSONObject().apply {
                put("mediaChunks", JSONArray().apply {
                    put(JSONObject().apply {
                        put("mimeType", "audio/pcm;rate=${PcmAudioEngine.CAPTURE_SAMPLE_RATE}")
                        put("data", b64)
                    })
                })
            })
        }
        ws.send(frame.toString())
    }

    private fun buildSetupFrame(modelResource: String, config: GeminiLiveConfig): JSONObject {
        val toolDecls = JSONArray().apply {
            put(JSONObject().apply {
                put("name", "list_workspace_files")
                put("description", "List files in the active project workspace")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("subdir", JSONObject().put("type", "STRING").put("description", "Optional relative subdirectory"))
                    })
                })
            })
            put(JSONObject().apply {
                put("name", "read_workspace_file")
                put("description", "Read a text file from the active project workspace")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("path", JSONObject().put("type", "STRING").put("description", "Relative path inside workspace"))
                    })
                    put("required", JSONArray().put("path"))
                })
            })
            put(JSONObject().apply {
                put("name", "run_workspace_command")
                put("description", "Run a read-only or build shell command in the active workspace")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("command", JSONObject().put("type", "STRING").put("description", "Shell command to execute"))
                    })
                    put("required", JSONArray().put("command"))
                })
            })
            put(JSONObject().apply {
                put("name", "write_workspace_file")
                put("description", "Create or overwrite a file in the active workspace. Only use when the user explicitly asks to implement or modify a file.")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("path", JSONObject().put("type", "STRING").put("description", "Relative path inside workspace"))
                        put("content", JSONObject().put("type", "STRING").put("description", "Full UTF-8 file content to write"))
                    })
                    put("required", JSONArray().put("path").put("content"))
                })
            })
            put(JSONObject().apply {
                put("name", "delegate_to_agy_agent")
                put("description", "Delegate a multi-file coding, refactoring, or build implementation task to the agy coding agent. Only use when the user explicitly asks to implement or execute changes.")
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        put("task", JSONObject().put("type", "STRING").put("description", "Detailed coding or implementation instruction"))
                    })
                    put("required", JSONArray().put("task"))
                })
            })
        }

        return JSONObject().apply {
            put("setup", JSONObject().apply {
                put("model", modelResource)
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().put("AUDIO"))
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", config.voiceName)
                            })
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", config.systemInstruction)))
                })
                put("inputAudioTranscription", JSONObject())
                put("outputAudioTranscription", JSONObject())
                put("tools", JSONArray().put(JSONObject().put("functionDeclarations", toolDecls)))
            })
        }
    }
}
