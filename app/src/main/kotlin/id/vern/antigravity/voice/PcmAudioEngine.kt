package id.vern.antigravity.voice

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Full-duplex PCM audio engine for Gemini Live:
 * - Captures 16 kHz mono 16-bit PCM with hardware AEC when available.
 * - Plays 24 kHz mono 16-bit PCM via a non-blocking queue with instant barge-in flush.
 * - Emits normalized mic and speaker RMS amplitudes for reactive UI animations.
 */
class PcmAudioEngine {

    companion object {
        const val CAPTURE_SAMPLE_RATE = 16000
        const val PLAYBACK_SAMPLE_RATE = 24000
        const val CAPTURE_CHUNK_BYTES = 1280 // 40 ms @ 16 kHz mono 16-bit

        /**
         * Computes normalized RMS amplitude in [0f, 1f] for little-endian 16-bit PCM bytes.
         */
        fun computeNormalizedRms(pcm16: ByteArray, length: Int = pcm16.size): Float {
            val sampleCount = length / 2
            if (sampleCount <= 0) return 0f
            var sumSq = 0.0
            var i = 0
            while (i + 1 < length) {
                val lo = pcm16[i].toInt() and 0xFF
                val hi = pcm16[i + 1].toInt()
                val sample = ((hi shl 8) or lo).toShort().toDouble()
                sumSq += sample * sample
                i += 2
            }
            val rms = sqrt(sumSq / sampleCount) / 32768.0
            return (rms * 3.2).toFloat().coerceIn(0f, 1f)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val mutableMicAmplitude = MutableStateFlow(0f)
    val micAmplitude: StateFlow<Float> = mutableMicAmplitude.asStateFlow()

    private val mutableSpeakerAmplitude = MutableStateFlow(0f)
    val speakerAmplitude: StateFlow<Float> = mutableSpeakerAmplitude.asStateFlow()

    @Volatile
    private var isMuted = false

    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var captureJob: Job? = null

    private var audioTrack: AudioTrack? = null
    private var playbackChannel: Channel<ByteArray>? = null
    private var playbackJob: Job? = null

    fun setMuted(muted: Boolean) {
        isMuted = muted
        if (muted) {
            mutableMicAmplitude.value = 0f
        }
    }

    @SuppressLint("MissingPermission")
    fun startCapture(onPcmChunk: (ByteArray) -> Unit) {
        stopCapture()
        val minBuf = AudioRecord.getMinBufferSize(
            CAPTURE_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(CAPTURE_CHUNK_BYTES * 4)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            CAPTURE_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IllegalStateException("AudioRecord failed to initialize")
        }

        if (AcousticEchoCanceler.isAvailable()) {
            runCatching {
                echoCanceler = AcousticEchoCanceler.create(recorder.audioSessionId)?.apply {
                    enabled = true
                }
            }
        }

        audioRecord = recorder
        recorder.startRecording()

        captureJob = scope.launch {
            val buf = ByteArray(CAPTURE_CHUNK_BYTES)
            while (isActive) {
                val read = recorder.read(buf, 0, buf.size)
                if (read > 0) {
                    if (isMuted) {
                        mutableMicAmplitude.value = 0f
                    } else {
                        val copy = buf.copyOf(read)
                        mutableMicAmplitude.value = computeNormalizedRms(copy, read)
                        onPcmChunk(copy)
                    }
                }
            }
        }
    }

    fun startPlayback() {
        stopPlayback()
        val minBuf = AudioTrack.getMinBufferSize(
            PLAYBACK_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(PLAYBACK_SAMPLE_RATE / 5 * 2)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(PLAYBACK_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack = track
        track.play()

        val channel = Channel<ByteArray>(Channel.UNLIMITED)
        playbackChannel = channel
        playbackJob = scope.launch {
            for (chunk in channel) {
                if (!isActive) break
                mutableSpeakerAmplitude.value = computeNormalizedRms(chunk, chunk.size)
                track.write(chunk, 0, chunk.size)
            }
            mutableSpeakerAmplitude.value = 0f
        }
    }

    fun enqueuePlayback(pcmBytes: ByteArray) {
        if (pcmBytes.isEmpty()) return
        playbackChannel?.trySend(pcmBytes)
    }

    fun flushPlayback() {
        val ch = playbackChannel
        if (ch != null) {
            while (ch.tryReceive().isSuccess) {
                // Drain queued audio frames immediately on barge-in
            }
        }
        mutableSpeakerAmplitude.value = 0f
        runCatching {
            audioTrack?.let { track ->
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.pause()
                    track.flush()
                    track.play()
                }
            }
        }
    }

    fun stopCapture() {
        captureJob?.cancel()
        captureJob = null
        mutableMicAmplitude.value = 0f
        runCatching { echoCanceler?.release() }
        echoCanceler = null
        runCatching {
            audioRecord?.let { rec ->
                if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    rec.stop()
                }
                rec.release()
            }
        }
        audioRecord = null
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        playbackChannel?.close()
        playbackChannel = null
        mutableSpeakerAmplitude.value = 0f
        runCatching {
            audioTrack?.let { track ->
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.pause()
                    track.flush()
                    track.stop()
                }
                track.release()
            }
        }
        audioTrack = null
    }

    fun stop() {
        stopCapture()
        stopPlayback()
    }
}
