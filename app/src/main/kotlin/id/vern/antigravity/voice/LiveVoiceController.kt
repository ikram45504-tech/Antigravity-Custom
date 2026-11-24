package id.vern.antigravity.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
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

enum class VoiceLanguage(
    val key: String,
    val displayName: String,
    val shortLabel: String,
    val bcp47Tag: String?
) {
    AUTO("auto", "Auto (System)", "Auto", null),
    URDU_PK("ur-PK", "Urdu (Pakistan)", "اردو PK", "ur-PK"),
    URDU_IN("ur-IN", "Urdu (India)", "اردو IN", "ur-IN"),
    ENGLISH_US("en-US", "English (US)", "English", "en-US");

    val isUrdu: Boolean
        get() = this == URDU_PK || this == URDU_IN

    fun targetLocale(systemLocale: Locale = Locale.getDefault()): Locale {
        return when (this) {
            AUTO -> systemLocale
            URDU_PK -> Locale.forLanguageTag("ur-PK")
            URDU_IN -> Locale.forLanguageTag("ur-IN")
            ENGLISH_US -> Locale.US
        }
    }

    /**
     * Returns an optional instruction prepended only to the model payload during Live Voice turns
     * so TTS output is concise and clearly pronounceable (especially in Urdu script).
     */
    fun buildVoicePromptInstruction(replyInVoiceLanguage: Boolean): String? {
        if (!replyInVoiceLanguage) return null
        return when {
            isUrdu -> "[Live Voice Mode — Urdu: Reply concisely in clear, natural Urdu script (اردو) suitable for spoken audio. Avoid code blocks, file paths, or Markdown symbols unless explicitly asked.]"
            this == ENGLISH_US -> "[Live Voice Mode: Reply concisely in clear conversational English suitable for spoken audio. Avoid long code blocks or heavy Markdown unless explicitly asked.]"
            else -> "[Live Voice Mode: Keep your reply concise and natural for spoken voice playback. Avoid long code blocks or heavy Markdown unless explicitly asked.]"
        }
    }

    companion object {
        fun fromKey(key: String?): VoiceLanguage {
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: AUTO
        }
    }
}

enum class TtsAvailabilityStatus {
    CHECKING,
    READY,
    MISSING_DATA,
    NOT_SUPPORTED,
    UNAVAILABLE
}

data class LiveVoiceUiState(
    val active: Boolean = false,
    val phase: LiveVoicePhase = LiveVoicePhase.IDLE,
    val partialTranscript: String = "",
    val isMuted: Boolean = false,
    val statusDetail: String = "",
    val language: VoiceLanguage = VoiceLanguage.AUTO,
    val replyInLanguage: Boolean = true,
    val speechRate: Float = 0.95f,
    val ttsAvailability: TtsAvailabilityStatus = TtsAvailabilityStatus.CHECKING,
    val activeVoiceLabel: String = "",
    val lastSpokenText: String = ""
)

object LiveVoiceSanitizer {
    private val fencedCodeRegex = Regex("```[\\s\\S]*?```")
    private val inlineCodeRegex = Regex("`([^`]+)`")
    private val markdownLinkRegex = Regex("\\[([^\\]]+)]\\(([^)]+)\\)")
    private val urlRegex = Regex("https?://\\S+|file://\\S+", RegexOption.IGNORE_CASE)
    private val headingOrQuoteRegex = Regex("(?m)^\\s{0,3}(?:#{1,6}|>|[-*+]|\\d+\\.)\\s+")
    // Strip Markdown emphasis (*, ~, and flanking/standalone _) without breaking snake_case identifiers.
    private val emphasisRegex = Regex("(?<![\\p{L}\\p{N}])_+|_+(?![\\p{L}\\p{N}])|[*~]+")
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
            val sentenceEnd = maxOf(
                slice.lastIndexOf(". "),
                slice.lastIndexOf("! "),
                slice.lastIndexOf("? "),
                slice.lastIndexOf("۔ ")
            )
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
 * Coordinates continuous hands-free voice turns using Android SpeechRecognizer + TextToSpeech,
 * with explicit language/Urdu locale support, high-quality TTS voice selection, and barge-in/retry.
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
    private var consecutiveTransientErrors = 0

    companion object {
        val SPEECH_RATE_OPTIONS = listOf(0.85f, 0.95f, 1.15f)

        fun openTtsVoiceInstaller(context: Context): Boolean {
            val installIntent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val settingsIntent = Intent("com.android.settings.TTS_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return runCatching {
                context.startActivity(installIntent)
                true
            }.getOrElse {
                runCatching {
                    context.startActivity(settingsIntent)
                    true
                }.getOrDefault(false)
            }
        }
    }

    fun configure(
        language: VoiceLanguage,
        replyInLanguage: Boolean,
        speechRate: Float
    ) {
        val clampedRate = speechRate.coerceIn(0.6f, 1.6f)
        mainHandler.post {
            val prevLang = mutableState.value.language
            mutableState.update {
                it.copy(
                    language = language,
                    replyInLanguage = replyInLanguage,
                    speechRate = clampedRate
                )
            }
            if (ttsReady) {
                applyTtsConfiguration(language, clampedRate)
            }
            if (prevLang != language && mutableState.value.active &&
                !mutableState.value.isMuted &&
                mutableState.value.phase == LiveVoicePhase.LISTENING
            ) {
                startListeningInternal()
            }
        }
    }

    fun start() {
        mainHandler.post {
            if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
                mutableState.update {
                    it.copy(
                        active = true,
                        phase = LiveVoicePhase.ERROR,
                        statusDetail = "Speech recognition service unavailable on this device"
                    )
                }
                return@post
            }
            consecutiveTransientErrors = 0
            requestAudioFocus()
            ensureTts()
            if (ttsReady) {
                applyTtsConfiguration(mutableState.value.language, mutableState.value.speechRate)
            }
            mutableState.update {
                it.copy(
                    active = true,
                    phase = LiveVoicePhase.LISTENING,
                    partialTranscript = "",
                    isMuted = false,
                    statusDetail = ""
                )
            }
            startListeningInternal()
        }
    }

    fun stop() {
        mainHandler.post {
            mainHandler.removeCallbacksAndMessages(null)
            pendingSpeechAfterInit = null
            consecutiveTransientErrors = 0
            stopRecognizerInternal()
            tts?.stop()
            abandonAudioFocus()
            mutableState.update {
                LiveVoiceUiState(
                    language = it.language,
                    replyInLanguage = it.replyInLanguage,
                    speechRate = it.speechRate
                )
            }
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
            mutableState.update {
                LiveVoiceUiState(
                    language = it.language,
                    replyInLanguage = it.replyInLanguage,
                    speechRate = it.speechRate
                )
            }
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
                mutableState.update { it.copy(isMuted = false, statusDetail = "") }
                if (cur.phase == LiveVoicePhase.PAUSED || cur.phase == LiveVoicePhase.LISTENING || cur.phase == LiveVoicePhase.ERROR) {
                    requestAudioFocus()
                    mutableState.update { it.copy(phase = LiveVoicePhase.LISTENING) }
                    startListeningInternal()
                }
            }
        }
    }

    fun retryListening() {
        mainHandler.post {
            val cur = mutableState.value
            if (!cur.active) return@post
            consecutiveTransientErrors = 0
            pendingSpeechAfterInit = null
            tts?.stop()
            requestAudioFocus()
            mutableState.update {
                it.copy(
                    isMuted = false,
                    phase = LiveVoicePhase.LISTENING,
                    partialTranscript = "",
                    statusDetail = ""
                )
            }
            startListeningInternal()
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
                mutableState.update { it.copy(phase = LiveVoicePhase.LISTENING, partialTranscript = "", statusDetail = "") }
                startListeningInternal()
            }
        }
    }

    fun onTurnStarted() {
        mainHandler.post {
            val cur = mutableState.value
            if (!cur.active) return@post
            stopRecognizerInternal()
            mutableState.update { it.copy(phase = LiveVoicePhase.THINKING, partialTranscript = "", statusDetail = "") }
        }
    }

    fun onAssistantCompleted(markdown: String, isSuccess: Boolean) {
        mainHandler.post {
            val cur = mutableState.value
            if (!cur.active) return@post
            val speakable = if (isSuccess) {
                LiveVoiceSanitizer.sanitizeForSpeech(markdown)
            } else if (cur.language.isUrdu) {
                "اس مرحلے میں کوئی خرابی پیش آ گئی۔"
            } else {
                "There was an error running that step."
            }
            if (speakable.isBlank()) {
                resumeListeningIfUnmuted()
                return@post
            }
            mutableState.update { it.copy(lastSpokenText = speakable) }
            speakTextInternal(speakable)
        }
    }

    fun refreshTtsStatus() {
        mainHandler.post {
            if (ttsReady) {
                applyTtsConfiguration(mutableState.value.language, mutableState.value.speechRate)
            } else {
                ensureTts()
            }
        }
    }

    private fun resumeListeningIfUnmuted() {
        val cur = mutableState.value
        if (!cur.active) return
        if (cur.isMuted) {
            mutableState.update { it.copy(phase = LiveVoicePhase.PAUSED, partialTranscript = "") }
        } else {
            mutableState.update { it.copy(phase = LiveVoicePhase.LISTENING, partialTranscript = "", statusDetail = "") }
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
        mutableState.update { it.copy(ttsAvailability = TtsAvailabilityStatus.CHECKING) }
        tts = TextToSpeech(appContext) { status ->
            mainHandler.post {
                if (status == TextToSpeech.SUCCESS) {
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
                    applyTtsConfiguration(mutableState.value.language, mutableState.value.speechRate)
                    val queued = pendingSpeechAfterInit
                    pendingSpeechAfterInit = null
                    if (!queued.isNullOrBlank() && mutableState.value.active) {
                        speakTextInternal(queued)
                    }
                } else {
                    ttsReady = false
                    mutableState.update {
                        it.copy(
                            ttsAvailability = TtsAvailabilityStatus.UNAVAILABLE,
                            activeVoiceLabel = ""
                        )
                    }
                }
            }
        }
    }

    private fun applyTtsConfiguration(language: VoiceLanguage, speechRate: Float) {
        val engine = tts ?: return
        engine.setSpeechRate(speechRate)

        val candidateLocales = buildList {
            val primary = language.targetLocale()
            add(primary)
            if (language.isUrdu) {
                add(Locale.forLanguageTag("ur-PK"))
                add(Locale.forLanguageTag("ur-IN"))
                add(Locale.forLanguageTag("ur"))
            }
        }.distinctBy { it.toLanguageTag() }

        var chosenLocale: Locale? = null
        var bestAvailability = TextToSpeech.LANG_NOT_SUPPORTED
        for (loc in candidateLocales) {
            val res = runCatching { engine.isLanguageAvailable(loc) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
            if (res >= TextToSpeech.LANG_AVAILABLE) {
                chosenLocale = loc
                bestAvailability = res
                break
            }
            if (res == TextToSpeech.LANG_MISSING_DATA && bestAvailability == TextToSpeech.LANG_NOT_SUPPORTED) {
                bestAvailability = res
            }
        }

        val effectiveLocale = chosenLocale ?: candidateLocales.first()
        runCatching { engine.language = effectiveLocale }

        var selectedVoiceName = ""
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && chosenLocale != null) {
            val voices = runCatching { engine.voices }.getOrNull().orEmpty()
            val bestVoice = selectBestVoiceForLocale(voices, chosenLocale)
            if (bestVoice != null) {
                runCatching { engine.voice = bestVoice }
                selectedVoiceName = "${bestVoice.locale.displayLanguage} (${bestVoice.locale.country.ifBlank { bestVoice.name.take(8) }})"
            }
        }

        val availabilityStatus = when {
            chosenLocale != null -> TtsAvailabilityStatus.READY
            bestAvailability == TextToSpeech.LANG_MISSING_DATA -> TtsAvailabilityStatus.MISSING_DATA
            else -> TtsAvailabilityStatus.NOT_SUPPORTED
        }

        mutableState.update {
            it.copy(
                ttsAvailability = availabilityStatus,
                activeVoiceLabel = selectedVoiceName.ifBlank { effectiveLocale.displayName }
            )
        }
    }

    private fun selectBestVoiceForLocale(voices: Set<Voice>, target: Locale): Voice? {
        val targetLang = target.language.lowercase(Locale.ROOT)
        val targetCountry = target.country.lowercase(Locale.ROOT)
        return voices
            .filter { voice ->
                voice.locale.language.equals(targetLang, ignoreCase = true) &&
                    !voice.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
            }
            .sortedWith(
                compareByDescending<Voice> { it.locale.country.equals(targetCountry, ignoreCase = true) }
                    .thenByDescending { it.quality }
                    .thenBy { it.latency }
            )
            .firstOrNull()
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
                override fun onReadyForSpeech(params: Bundle?) {
                    consecutiveTransientErrors = 0
                }
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
                    consecutiveTransientErrors = 0
                    if (spoken.isNotBlank()) {
                        mutableState.update { it.copy(phase = LiveVoicePhase.THINKING, partialTranscript = spoken, statusDetail = "") }
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
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> scheduleRelisten(delayMs = 300L)
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                        SpeechRecognizer.ERROR_CLIENT -> {
                            consecutiveTransientErrors++
                            if (consecutiveTransientErrors <= 3) {
                                scheduleRelisten(delayMs = 700L * consecutiveTransientErrors)
                            } else {
                                mutableState.update {
                                    it.copy(
                                        phase = LiveVoicePhase.ERROR,
                                        statusDetail = "Mic busy or recognizer reset needed. Tap Retry."
                                    )
                                }
                            }
                        }
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                            mutableState.update {
                                it.copy(
                                    active = false,
                                    phase = LiveVoicePhase.ERROR,
                                    statusDetail = "Microphone permission required"
                                )
                            }
                            abandonAudioFocus()
                        }
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                            consecutiveTransientErrors++
                            if (consecutiveTransientErrors <= 2) {
                                scheduleRelisten(delayMs = 1000L)
                            } else {
                                mutableState.update {
                                    it.copy(
                                        phase = LiveVoicePhase.ERROR,
                                        statusDetail = "Network issue during speech recognition. Tap Retry."
                                    )
                                }
                            }
                        }
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                            mutableState.update {
                                it.copy(
                                    phase = LiveVoicePhase.ERROR,
                                    statusDetail = "${latest.language.displayName} speech pack unavailable. Switch language or install offline voice."
                                )
                            }
                        }
                        else -> scheduleRelisten(delayMs = 800L)
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
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
                val bcp47 = cur.language.bcp47Tag
                if (!bcp47.isNullOrBlank()) {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, bcp47)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, bcp47)
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, bcp47)
                }
            }
            recognizer.startListening(intent)
        } catch (e: Exception) {
            mutableState.update {
                it.copy(
                    phase = LiveVoicePhase.ERROR,
                    statusDetail = e.message ?: "Unable to start voice recognition"
                )
            }
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
