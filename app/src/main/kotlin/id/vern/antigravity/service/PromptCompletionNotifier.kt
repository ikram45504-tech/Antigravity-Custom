package id.vern.antigravity.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import id.vern.antigravity.R
import id.vern.antigravity.ui.MainActivity
import id.vern.antigravity.viewmodel.AntigravityViewModel

object PromptCompletionNotifier {

    const val NOTIFICATION_ID = 2002
    private const val CHANNEL_BASE_ID = "agy_prompt_completion"

    const val SOUND_FLUTE = "Flute Chime"
    const val SOUND_SYSTEM = "Mobile Default"
    const val SOUND_CUSTOM = "Custom Sound"
    const val SOUND_SILENT = "Silent"

    const val BUZZ_DEFAULT = "Default Buzz"
    const val BUZZ_DOUBLE = "Double Buzz"
    const val BUZZ_GENTLE = "Gentle Buzz"
    const val BUZZ_LONG = "Long Buzz"
    const val BUZZ_NONE = "Off"

    val SOUND_OPTIONS = listOf(SOUND_FLUTE, SOUND_SYSTEM, SOUND_CUSTOM, SOUND_SILENT)
    val BUZZ_OPTIONS = listOf(BUZZ_DEFAULT, BUZZ_DOUBLE, BUZZ_GENTLE, BUZZ_LONG, BUZZ_NONE)

    private var activePreviewPlayer: MediaPlayer? = null

    fun getVibrationPattern(buzzType: String): LongArray? {
        return when (buzzType) {
            BUZZ_DEFAULT -> longArrayOf(0, 180, 100, 240)
            BUZZ_DOUBLE -> longArrayOf(0, 120, 100, 120)
            BUZZ_GENTLE -> longArrayOf(0, 140)
            BUZZ_LONG -> longArrayOf(0, 450)
            BUZZ_NONE -> null
            else -> longArrayOf(0, 180, 100, 240)
        }
    }

    fun getSoundUri(context: Context, soundType: String, customUriString: String? = null): Uri? {
        return when (soundType) {
            SOUND_FLUTE -> {
                Uri.parse("android.resource://${context.packageName}/${R.raw.flute_chime}")
            }
            SOUND_SYSTEM -> {
                Settings.System.DEFAULT_NOTIFICATION_URI
            }
            SOUND_CUSTOM -> {
                if (!customUriString.isNullOrBlank()) {
                    try {
                        Uri.parse(customUriString)
                    } catch (_: Exception) {
                        Settings.System.DEFAULT_NOTIFICATION_URI
                    }
                } else {
                    Settings.System.DEFAULT_NOTIFICATION_URI
                }
            }
            SOUND_SILENT -> null
            else -> {
                Uri.parse("android.resource://${context.packageName}/${R.raw.flute_chime}")
            }
        }
    }

    private fun getChannelId(soundType: String, buzzType: String): String {
        val soundKey = soundType.lowercase().replace(" ", "_")
        val buzzKey = buzzType.lowercase().replace(" ", "_")
        return "${CHANNEL_BASE_ID}_${soundKey}_${buzzKey}"
    }

    fun ensureNotificationChannel(
        context: Context,
        soundType: String,
        buzzType: String,
        customUriString: String? = null
    ): String {
        val channelId = getChannelId(soundType, buzzType)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return channelId

            val existing = manager.getNotificationChannel(channelId)
            if (existing == null) {
                val soundUri = getSoundUri(context, soundType, customUriString)
                val pattern = getVibrationPattern(buzzType)

                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_DELAYED)
                    .build()

                val channel = NotificationChannel(
                    channelId,
                    context.getString(R.string.prompt_completion_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.prompt_completion_channel_desc)
                    setShowBadge(true)
                    if (soundUri != null) {
                        setSound(soundUri, audioAttributes)
                    } else {
                        setSound(null, null)
                    }
                    if (pattern != null && pattern.isNotEmpty()) {
                        enableVibration(true)
                        vibrationPattern = pattern
                    } else {
                        enableVibration(false)
                    }
                }
                manager.createNotificationChannel(channel)
            }
        }
        return channelId
    }

    fun buzz(context: Context, buzzType: String) {
        val pattern = getVibrationPattern(buzzType) ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                if (vibrator != null && vibrator.hasVibrator()) {
                    val effect = VibrationEffect.createWaveform(pattern, -1)
                    vibrator.vibrate(effect)
                    return
                }
            }

            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createWaveform(pattern, -1)
                    vibrator.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, -1)
                }
            }
        } catch (_: Exception) {}
    }

    fun playSoundPreview(
        context: Context,
        soundType: String,
        customUriString: String? = null,
        onComplete: (() -> Unit)? = null
    ) {
        stopSoundPreview()
        if (soundType == SOUND_SILENT) {
            buzz(context, BUZZ_DEFAULT)
            onComplete?.invoke()
            return
        }

        val uri = getSoundUri(context, soundType, customUriString) ?: return

        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                )
                setDataSource(context, uri)
                setOnCompletionListener { mp ->
                    mp.release()
                    if (activePreviewPlayer == mp) {
                        activePreviewPlayer = null
                    }
                    onComplete?.invoke()
                }
                setOnErrorListener { mp, _, _ ->
                    mp.release()
                    if (activePreviewPlayer == mp) {
                        activePreviewPlayer = null
                    }
                    false
                }
                prepare()
                start()
            }
            activePreviewPlayer = player
        } catch (e: Exception) {
            try {
                val ringtone = RingtoneManager.getRingtone(context, uri)
                ringtone?.play()
            } catch (_: Exception) {}
        }
    }

    fun stopSoundPreview() {
        try {
            activePreviewPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (_: Exception) {}
        activePreviewPlayer = null
    }

    fun isPreviewPlaying(): Boolean {
        return try {
            activePreviewPlayer?.isPlaying == true
        } catch (_: Exception) {
            false
        }
    }

    fun notifyPromptCompleted(
        context: Context,
        conversationId: String,
        prompt: String,
        response: String,
        isSuccess: Boolean = true,
        isTest: Boolean = false
    ) {
        val prefs = context.getSharedPreferences(AntigravityViewModel.PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(AntigravityViewModel.KEY_COMPLETION_NOTIFICATION_ENABLED, true)
        if (!enabled && !isTest) return

        val buzzType = prefs.getString(AntigravityViewModel.KEY_COMPLETION_BUZZ_TYPE, BUZZ_DEFAULT) ?: BUZZ_DEFAULT
        val soundType = prefs.getString(AntigravityViewModel.KEY_COMPLETION_SOUND_TYPE, SOUND_FLUTE) ?: SOUND_FLUTE
        val customUri = prefs.getString(AntigravityViewModel.KEY_COMPLETION_CUSTOM_SOUND_URI, null)

        buzz(context, buzzType)

        val channelId = ensureNotificationChannel(context, soundType, buzzType, customUri)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("conversation_id", conversationId)
            putExtra("open_chat", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID + 10,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val iconRes = context.resources.getIdentifier("ic_notification", "drawable", context.packageName)
            .takeIf { it != 0 } ?: android.R.drawable.stat_notify_chat

        val title = when {
            isTest -> "Antigravity Alert: Test Prompt Completed"
            isSuccess -> context.getString(R.string.prompt_completion_notif_title)
            else -> context.getString(R.string.prompt_completion_notif_title_error)
        }

        val cleanPrompt = prompt.replace(Regex("\\s+"), " ").trim().take(80)
        val cleanResponse = response.replace(Regex("\\s+"), " ").trim().take(150)
        val bodyText = if (cleanResponse.isNotBlank()) cleanResponse else context.getString(R.string.prompt_completion_notif_ready)

        val bigText = buildString {
            if (cleanPrompt.isNotBlank()) {
                append("Prompt: \"").append(cleanPrompt).append("\"\n\n")
            }
            append(if (cleanResponse.isNotBlank()) cleanResponse else context.getString(R.string.prompt_completion_notif_ready))
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val pattern = getVibrationPattern(buzzType)
        if (pattern != null && pattern.isNotEmpty()) {
            builder.setVibrate(pattern)
        }

        val soundUri = getSoundUri(context, soundType, customUri)
        if (soundUri != null) {
            builder.setSound(soundUri)
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, builder.build())
    }
}
