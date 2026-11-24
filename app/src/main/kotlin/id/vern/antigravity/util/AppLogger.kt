package id.vern.antigravity.util

import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object AppLogger {
    private var logHandler: ((String, String, Boolean) -> Unit)? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val lock = Any()

    // DateTimeFormatter is immutable and thread-safe. A shared SimpleDateFormat
    // instance is not, and log() is called from arbitrary coroutine threads,
    // which intermittently produced garbled timestamps.
    private val timeFormat: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

    /** Rotate the on-disk log once it passes this size instead of growing forever. */
    private const val MAX_LOG_BYTES = 2L * 1024L * 1024L
    private const val MAX_ROTATED_FILES = 3

    fun setHandler(handler: (String, String, Boolean) -> Unit) {
        logHandler = handler
    }

    fun d(tag: String, message: String) {
        log(tag, message, false)
    }

    fun e(tag: String, message: String) {
        log(tag, message, true)
    }

    fun log(tag: String, message: String, isError: Boolean = false) {

        try {
            logHandler?.invoke(tag, message, isError)
        } catch (e: Throwable) {}

        scope.launch {
            try {
                val baseDir = Environment.getExternalStorageDirectory() ?: return@launch
                val logDir = File(baseDir, "Antigravity")
                if (!logDir.exists()) {
                    logDir.mkdirs()
                }
                val logFile = File(logDir, "logs.txt")
                val timeStr = timeFormat.format(Instant.now())
                val line = "[$timeStr] [$tag] $message\n"
                synchronized(lock) {
                    rotateIfNeeded(logFile)
                    logFile.appendText(line, Charsets.UTF_8)
                }
            } catch (e: Throwable) {

            }
        }
    }

    /**
     * Keeps the log bounded: `logs.txt` is renamed to `logs.1.txt`, older
     * files shift down, and anything past [MAX_ROTATED_FILES] is deleted.
     * Must be called while holding [lock].
     */
    private fun rotateIfNeeded(logFile: File) {
        if (logFile.isFile && logFile.length() > MAX_LOG_BYTES) {
            for (index in MAX_ROTATED_FILES downTo 1) {
                val older = File(logFile.parentFile, "logs.$index.txt")
                if (index == MAX_ROTATED_FILES) {
                    if (older.exists()) older.delete()
                } else if (older.exists()) {
                    older.renameTo(File(logFile.parentFile, "logs.${index + 1}.txt"))
                }
            }
            logFile.renameTo(File(logFile.parentFile, "logs.1.txt"))
        }
    }
}
