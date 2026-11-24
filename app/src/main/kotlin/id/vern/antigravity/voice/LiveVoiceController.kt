package id.vern.antigravity.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import java.util.UUID

enum class LiveVoicePhase {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    PAUSED,
    ERROR
}

data class LiveVoiceUiState(
    val active: Boolean = false,
    val phase: LiveVoicePhase = LiveVoicePhase.IDLE,
    val partialTranscript: String = "",
    val isMuted: Boolean = false,
    val statusDetail: String = ""
)

object LiveVoiceSanitizer {
    private val fencedCodeRegex = Regex("```[\\s\\S]*?```")
    private val inlineCodeRegex = Regex("`([^`]+)`")
    private val markdownLinkRegex = Regex("\\[([^\\]]+)]\\(([^)]+)\\)")
    private val urlRegex = Regex("https?://\\S+|file://\\S+", RegexOption.IGNORE_CASE)
    private val headingOrQuoteRegex = Regex("(?m)^\\s{0,3}(?:#{1,6}|>|[-*+]|\\d+\\.)\\s+")
    private val emphasisRegex = Regex("[*_~]+")
    private val horizontalRuleRegex = Regex("(?m)^\\s*[-*_]{3,}\\s*$")
    private val multiSpaceRegex = Regex("\\s+")

    /**
     * Converts assistant Markdown into concise, natural plain text suitable for Text-to-Speech.
     */
    fun sanitizeForSpeech(markdown: String, maxChars: Int = 1200): String {
        if (markdown.isBlank()) return ""
        var text = markdown
            .replace(fencedCodeRegex, " Code snippet omitted. ")
            .replace(markdownLinkRegex) { it.groupValues[1] }
            .replace(urlRegex, "")
            .replace(inlineCodeRegex) { it.groupValues[1] }
            .replace(horizontalRuleRegex, " ")
            .replace(headingOrQuoteRegex, "")
            .replace(emphasisRegex, "")
            .replace("|", " ")
            .replace(multiSpaceRegex, " ")
            .trim()

        if (text.length > maxChars) {
            val slice = text.substring(0, maxChars)
            val sentenceEnd = maxOf(slice.lastIndexOf(". "), slice.lastIndexOf("! "), slice.lastIndexOf("? "))
            text = if (sentenceEnd > maxChars / 2) {
                slice.substring(0, sentenceEnd + 1).trim()
            } else {
                slice.trimEnd() + "..."
            }
        }
        return text
    }
}

/**
 * Coordinates continuous hands-free voice turns using Android SpeechRecognizer + TextToSpeech.
 */
class LiveVoiceController(
    private val context: Context,
    private val onFinalUtterance: (String) -> Unit
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val mutableState = MutableStateFlow(LiveVoiceUiState())
    val state: StateFlow<LiveVoiceUiState> = mutableState.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeechAfterInit: String? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    fun start() {
        mainHandler.post {
            if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
                mutableState.value = LiveVoiceUiState(
                    active = false,
                    phase = LiveVoicePhase.ERROR,
                    statusDetail = "Speech recognition service unavailable on this device"
                )
                return@post
            }
            requestAudioFocus()
            ensureTts()
            mutableState.value = LiveVoiceUiState(
                active = true,
                phase = LiveVoicePhase.LISTENING,
                partialTranscript = "",
                isMuted = false
            )
            startListeningInternal()
        }
    }

    fun stop() {
        mainHandler.post {
            mainHandler.removeCallbacksAndMessages(null)
            pendingSpeechAfterInit = null
            stopRecognizerInternal()
            tts?.stop()
            abandonAudioFocus()
            mutableState.value = LiveVoiceUiState()
        }
    }

    /**
     * Releases microphone and audio focus when the UI leaves the foreground while keeping session state paused.
     */
    fun pause() {
        mainHandler.post {
            val cur = mutableState.value
            if (!cur.active) return@post
            mainHandler.removeCallbacksAndMessages(null)
            pendingSpeechAfterInit = null
            stopRecognizerInternal()
            tts?.stop()
            abandonAudioFocus()
            mutableState.update {
                it.copy(
                    isMuted = true,
                    phase = LiveVoicePhase.PAUSED,
                    partialTranscript = ""
                )
            }
        }
    }

    fun destroy() {
        mainHandler.post {
            mainHandler.removeCallbacksAndMessages(null)
            pendingSpeechAfterInit = null
            stopRecognizerInternal()
            tts?.stop()
            tts?.shutdown()
            tts = null
            ttsReady = false
            abandonAudioFocus()
            mutableState.value = LiveVoiceUiState()
        }
    }

    fun toggleMute() {
        mainHandler.post {
            val cur = mutableState.value
            if (!cur.active) return@post
            val nextMuted = !cur.isMuted
            if (nextMuted) {
                stopRecognizerInternal()
                mutableState.update {
                    it.copy(
                        isMuted = true,
                        phase = if (it.phase == LiveVoicePhase.LISTENING) LiveVoicePhase.PAUSED else it.phase,
                        partialTranscript = ""
                    )
                }
            } else {
                mutableState.update { it.copy(isMuted = false) }
                if (cur.phase == LiveVoicePhase.PAUSED || cur.phase == LiveVoicePhase.LISTENING) {
                    requestAudioFocus()
                    mutableState.update { it.copy(phase = LiveVoicePhase.LISTENING) }
                    startListeningInternal()
                }
            }
        }
    }

    fun skipSpeaking() {
        mainHandler.post {
            val cur = mutableState.value
            if (!cur.active) return@post
            pendingSpeechAfterInit = null
            tts?.stop()
            if (cur.isMuted) {
                mutableState.update { it.copy(phase = LiveVoicePhase.PAUSED) }
            } else {
                mutableState.update { it.copy(phase = LiveVoicePhase.LISTENING, partialTranscript = "") }
                startListeningInternal()
            }
        }
    }

    fun onTurnStarted() {
        mainHandler.post {
            val cur = mutableState.value
            if (!cur.active) return@post
            stopRecognizerInternal()
            mutableState.update { it.copy(phase = LiveVoicePhase.THINKING, partialTranscript = "") }
        }
    }

    fun onAssistantCompleted(markdown: String, isSuccess: Boolean) {
        mainHandler.post {
            val cur = mutableState.value
            if (!cur.active) return@post
            val speakable = if (isSuccess) {
                LiveVoiceSanitizer.sanitizeForSpeech(markdown)
            } else {
                "There was an error running that step."
            }
            if (speakable.isBlank()) {
                resumeListeningIfUnmuted()
                return@post
            }
            speakTextInternal(speakable)
        }
    }

    private fun resumeListeningIfUnmuted() {
        val cur = mutableState.value
        if (!cur.active) return
        if (cur.isMuted) {
            mutableState.update { it.copy(phase = LiveVoicePhase.PAUSED, partialTranscript = "") }
        } else {
            mutableState.update { it.copy(phase = LiveVoicePhase.LISTENING, partialTranscript = "") }
            mainHandler.postDelayed({
                val latest = mutableState.value
                if (latest.active && !latest.isMuted && latest.phase == LiveVoicePhase.LISTENING) {
                    startListeningInternal()
                }
            }, 250L)
        }
    }

    private fun ensureTts() {
        if (tts != null) return
        tts = TextToSpeech(appContext) { status ->
            mainHandler.post {
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.getDefault()
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            mainHandler.post { resumeListeningIfUnmuted() }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            mainHandler.post { resumeListeningIfUnmuted() }
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            mainHandler.post { resumeListeningIfUnmuted() }
                        }
                    })
                    ttsReady = true
                    val queued = pendingSpeechAfterInit
                    pendingSpeechAfterInit = null
                    if (!queued.isNullOrBlank() && mutableState.value.active) {
                        speakTextInternal(queued)
                    }
                } else {
                    ttsReady = false
                }
            }
        }
    }

    private fun speakTextInternal(text: String) {
        stopRecognizerInternal()
        ensureTts()
        mutableState.update { it.copy(phase = LiveVoicePhase.SPEAKING, partialTranscript = "") }
        if (!ttsReady || tts == null) {
            pendingSpeechAfterInit = text
            return
        }
        val utteranceId = UUID.randomUUID().toString()
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result == TextToSpeech.ERROR) {
            resumeListeningIfUnmuted()
        }
    }

    private fun stopRecognizerInternal() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        } finally {
            speechRecognizer = null
        }
    }

    private fun startListeningInternal() {
        val cur = mutableState.value
        if (!cur.active || cur.isMuted) return
        stopRecognizerInternal()
        try {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (partial.isNotBlank() && mutableState.value.active) {
                        mutableState.update { it.copy(partialTranscript = partial) }
                    }
                }

                override fun onResults(results: Bundle?) {
                    val spoken = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (!mutableState.value.active) return
                    if (spoken.isNotBlank()) {
                        mutableState.update { it.copy(phase = LiveVoicePhase.THINKING, partialTranscript = spoken) }
                        onFinalUtterance(spoken)
                    } else {
                        scheduleRelisten()
                    }
                }

                override fun onError(error: Int) {
                    val latest = mutableState.value
                    if (!latest.active || latest.isMuted) return
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> scheduleRelisten()
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                            mutableState.value = LiveVoiceUiState(
                                active = false,
                                phase = LiveVoicePhase.ERROR,
                                statusDetail = "Microphone permission required"
                            )
                            abandonAudioFocus()
                        }
                        else -> scheduleRelisten(delayMs = 600L)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            speechRecognizer = recognizer

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
            }
            recognizer.startListening(intent)
        } catch (e: Exception) {
            mutableState.value = LiveVoiceUiState(
                active = false,
                phase = LiveVoicePhase.ERROR,
                statusDetail = e.message ?: "Unable to start voice recognition"
            )
        }
    }

    private fun scheduleRelisten(delayMs: Long = 350L) {
        mainHandler.postDelayed({
            val latest = mutableState.value
            if (latest.active && !latest.isMuted && latest.phase == LiveVoicePhase.LISTENING) {
                startListeningInternal()
            }
        }, delayMs)
    }

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        if (audioFocusRequest != null) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { }
            .build()
        audioFocusRequest = req
        runCatching { am.requestAudioFocus(req) }
    }

    private fun abandonAudioFocus() {
        val req = audioFocusRequest ?: return
        runCatching { audioManager?.abandonAudioFocusRequest(req) }
        audioFocusRequest = null
    }
}
