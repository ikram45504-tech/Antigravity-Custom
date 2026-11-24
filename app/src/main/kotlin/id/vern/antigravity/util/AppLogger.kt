package id.vern.antigravity.util

import android.content.Context
import android.system.Os
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private const val MAX_LOG_BYTES = 2L * 1024L * 1024L
    private const val LOG_FILE_NAME = "app.log"
    private const val ROTATED_LOG_FILE_NAME = "app.log.1"

    private val bearerRegex = Regex("""(?i)(Bearer\s+)[A-Za-z0-9._\-~+/]+=*""")
    private val tokenPrefixRegex = Regex("""\b(gh[po]_[A-Za-z0-9_]+|ya29\.[A-Za-z0-9._\-]+)""")

    private var logHandler: ((String, String, Boolean) -> Unit)? = null
    @Volatile
    private var appLogsDir: File? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val lock = Any()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun init(context: Context) {
        appLogsDir = File(context.applicationContext.filesDir, "logs")
    }

    fun setHandler(handler: (String, String, Boolean) -> Unit) {
        logHandler = handler
    }

    fun d(tag: String, message: String) {
        log(tag, message, false)
    }

    fun e(tag: String, message: String) {
        log(tag, message, true)
    }

    internal fun redactSecrets(input: String): String {
        return input
            .replace(bearerRegex, "$1[REDACTED]")
            .replace(tokenPrefixRegex, "[REDACTED]")
    }

    private fun applyOwnerOnlyPermissions(file: File) {
        file.setReadable(false, false)
        file.setReadable(true, true)
        file.setWritable(false, false)
        file.setWritable(true, true)
        try {
            Os.chmod(file.absolutePath, 384)
        } catch (_: Throwable) {}
    }

    fun log(tag: String, message: String, isError: Boolean = false) {
        val redactedMessage = redactSecrets(message)

        try {
            logHandler?.invoke(tag, redactedMessage, isError)
        } catch (e: Throwable) {}

        val logDir = appLogsDir ?: return
        scope.launch {
            try {
                synchronized(lock) {
                    if (!logDir.exists()) {
                        logDir.mkdirs()
                    }
                    val logFile = File(logDir, LOG_FILE_NAME)
                    val rotatedFile = File(logDir, ROTATED_LOG_FILE_NAME)
                    if (logFile.exists() && logFile.length() >= MAX_LOG_BYTES) {
                        if (rotatedFile.exists()) rotatedFile.delete()
                        logFile.renameTo(rotatedFile)
                    }
                    val timeStr = timeFormat.format(Date())
                    val line = "[$timeStr] [$tag] $redactedMessage\n"
                    val isNew = !logFile.exists()
                    logFile.appendText(line, Charsets.UTF_8)
                    if (isNew) {
                        applyOwnerOnlyPermissions(logFile)
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }
}

