package id.vern.antigravity.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File
import java.net.URLDecoder

object FileResolver {
    private val lineAnchorRegex = Regex("""#L\d+(?:-L?\d+)?$""", RegexOption.IGNORE_CASE)

    internal fun normalizePathOrUri(raw: String): String {
        var cleaned = raw.trim().removeSurrounding("\"").removeSurrounding("'")
        if (cleaned.isBlank()) return ""
        cleaned = cleaned.replace(lineAnchorRegex, "")
        if (cleaned.startsWith("file://", ignoreCase = true)) {
            val parsedPath = runCatching { Uri.parse(cleaned).path }.getOrNull()
            cleaned = if (!parsedPath.isNullOrBlank()) {
                parsedPath
            } else {
                val stripped = cleaned.removePrefix("file://").removePrefix("FILE://")
                runCatching { URLDecoder.decode(stripped, "UTF-8") }.getOrDefault(stripped)
            }
        }
        return cleaned.trim()
    }

    fun resolveTargetFile(
        pathOrName: String,
        workspacePath: String?,
        projectList: List<File>,
        context: Context
    ): File? {
        val trimmed = normalizePathOrUri(pathOrName)
        if (trimmed.isBlank()) return null
        val direct = File(trimmed)
        if (direct.isAbsolute && direct.isFile && direct.exists()) {
            return direct
        }

        if (!workspacePath.isNullOrBlank()) {
            val wsDir = File(workspacePath)
            if (wsDir.isDirectory) {
                val candidate = File(wsDir, trimmed)
                if (candidate.isFile && candidate.exists()) return candidate
                val found = wsDir.walkTopDown().maxDepth(8).firstOrNull {
                    it.isFile && (it.name.equals(direct.name, ignoreCase = true) || it.absolutePath.endsWith(trimmed))
                }
                if (found != null) return found
            }
        }

        for (pDir in projectList) {
            if (pDir.isDirectory) {
                val candidate = File(pDir, trimmed)
                if (candidate.isFile && candidate.exists()) return candidate
                val found = pDir.walkTopDown().maxDepth(8).firstOrNull {
                    it.isFile && (it.name.equals(direct.name, ignoreCase = true) || it.absolutePath.endsWith(trimmed))
                }
                if (found != null) return found
            }
        }

        val commonRoots = listOfNotNull(
            File(context.filesDir, "home/.gemini/antigravity-cli/brain"),
            File(context.filesDir, ".gemini/antigravity-cli/brain"),
            File(context.filesDir, "home/.gemini/antigravity-app/brain"),
            File(context.filesDir, ".gemini/antigravity-app/brain"),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            File(context.filesDir, "projects"),
            File(context.filesDir, "home")
        )
        for (root in commonRoots) {
            if (root.isDirectory) {
                val found = root.walkTopDown().maxDepth(8).firstOrNull {
                    it.isFile && (it.name.equals(direct.name, ignoreCase = true) || it.absolutePath.endsWith(trimmed))
                }
                if (found != null) return found
            }
        }

        return null
    }
}
