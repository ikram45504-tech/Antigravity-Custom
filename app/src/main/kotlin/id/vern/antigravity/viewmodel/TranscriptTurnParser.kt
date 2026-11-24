package id.vern.antigravity.viewmodel

import android.content.Context
import id.vern.antigravity.R
import id.vern.antigravity.data.AgentExecutionStep
import id.vern.antigravity.data.AgentStepKind
import id.vern.antigravity.data.ChatMessage
import id.vern.antigravity.data.MessageRole
import id.vern.antigravity.data.MessageStatus
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ParsedTranscriptTurn(
    val thinking: String? = null,
    val steps: List<AgentExecutionStep>? = null,
    val assistantResponse: String? = null
)

object TranscriptTurnParser {
    val transcriptFileCache = ConcurrentHashMap<String, File>()

    private fun JSONObject.firstNonEmptyString(vararg keys: String): String {
        for (key in keys) {
            if (has(key) && !isNull(key)) {
                val value = optString(key)
                if (value.isNotEmpty()) return value
            }
        }
        return ""
    }

    private fun countChangedLines(target: String, replacement: String): Pair<Int, Int> {
        val oldLines = if (target.isEmpty()) emptyList() else target.split('\n')
        val newLines = if (replacement.isEmpty()) emptyList() else replacement.split('\n')
        if (oldLines.isEmpty() && newLines.isEmpty()) return 0 to 0
        if (oldLines.isEmpty()) return newLines.size to 0
        if (newLines.isEmpty()) return 0 to oldLines.size

        var prefix = 0
        val minLen = minOf(oldLines.size, newLines.size)
        while (prefix < minLen && oldLines[prefix] == newLines[prefix]) {
            prefix++
        }
        var suffix = 0
        while (
            suffix < (oldLines.size - prefix) &&
            suffix < (newLines.size - prefix) &&
            oldLines[oldLines.size - 1 - suffix] == newLines[newLines.size - 1 - suffix]
        ) {
            suffix++
        }
        val delCount = (oldLines.size - prefix - suffix).coerceAtLeast(0)
        val addCount = (newLines.size - prefix - suffix).coerceAtLeast(0)
        return addCount to delCount
    }

    /**
     * Computes "+add -del" summary for file-editing tool parameters across edit_file, write_file,
     * replace_file_content, write_to_file, and multi_replace_file_content.
     */
    fun computeEditStepDiff(params: JSONObject?): String? {
        if (params == null) return null

        val chunks = params.optJSONArray("ReplacementChunks") ?: params.optJSONArray("replacementChunks")
        if (chunks != null && chunks.length() > 0) {
            var totalAdd = 0
            var totalDel = 0
            for (idx in 0 until chunks.length()) {
                val chunk = chunks.optJSONObject(idx) ?: continue
                val t = chunk.firstNonEmptyString("TargetContent", "Target", "targetContent", "target")
                val r = chunk.firstNonEmptyString("ReplacementContent", "Replacement", "replacementContent", "replacement")
                val (add, del) = countChangedLines(t, r)
                totalAdd += add
                totalDel += del
            }
            return if (totalAdd > 0 || totalDel > 0) "+$totalAdd -$totalDel" else null
        }

        val targetContent = params.firstNonEmptyString("TargetContent", "Target", "targetContent", "target")
        val replacementContent = params.firstNonEmptyString(
            "ReplacementContent",
            "Replacement",
            "CodeContent",
            "Content",
            "replacementContent",
            "replacement",
            "codeContent",
            "content"
        )
        if (targetContent.isEmpty() && replacementContent.isEmpty()) return null
        val (addCount, delCount) = countChangedLines(targetContent, replacementContent)
        return "+$addCount -$delCount"
    }

    fun findActiveTranscriptFile(context: Context, cliConvId: String?): File? {
        if (!cliConvId.isNullOrBlank()) {
            transcriptFileCache[cliConvId]?.let { cached ->
                if (cached.exists() && cached.isFile && cached.length() > 0) return cached
            }
        }
        val filesDir = context.filesDir
        val homeDir = File(filesDir, "home")
        val envHome = System.getenv("HOME")?.let { File(it) }
        val envAppData = System.getenv("ANTIGRAVITY_APP_DATA_DIR")?.let { File(it) }
        val candidateBaseDirs = mutableListOf(
            File(homeDir, ".gemini/antigravity-cli/brain"),
            File(filesDir, ".gemini/antigravity-cli/brain"),
            File(filesDir, ".gemini/antigravity-app/brain"),
            File(homeDir, ".gemini/antigravity-app/brain")
        )
        if (envAppData != null && envAppData.exists()) {
            candidateBaseDirs.add(File(envAppData, "brain"))
        }
        if (envHome != null && envHome != homeDir && envHome != filesDir && envHome.exists()) {
            candidateBaseDirs.add(File(envHome, ".gemini/antigravity-cli/brain"))
            candidateBaseDirs.add(File(envHome, ".gemini/antigravity-app/brain"))
        }

        if (!cliConvId.isNullOrBlank()) {
            for (baseDir in candidateBaseDirs) {
                val f = File(baseDir, "$cliConvId/.system_generated/logs/transcript.jsonl")
                if (f.exists() && f.isFile && f.length() > 0) {
                    transcriptFileCache[cliConvId] = f
                    return f
                }
            }
        }

        var newestFile: File? = null
        var newestTime = 0L

        for (baseDir in candidateBaseDirs) {
            if (!baseDir.exists() || !baseDir.isDirectory) continue
            val convFolders = baseDir.listFiles() ?: continue
            for (convDir in convFolders) {
                if (!convDir.isDirectory) continue
                val tf = File(convDir, ".system_generated/logs/transcript.jsonl")
                if (tf.exists() && tf.isFile && tf.length() > 0) {
                    val lm = tf.lastModified()
                    if (lm > newestTime) {
                        newestTime = lm
                        newestFile = tf
                    }
                }
            }
        }
        if (newestFile != null && !cliConvId.isNullOrBlank()) {
            transcriptFileCache[cliConvId] = newestFile
        }
        return newestFile
    }

    fun extractPathFromParams(params: JSONObject?): String {
        if (params == null) return ""
        val knownKeys = listOf(
            "AbsolutePath", "absolute_path", "Path", "path",
            "TargetFile", "target_file", "File", "file",
            "FilePath", "filePath", "filepath", "target", "uri", "location"
        )
        for (k in knownKeys) {
            val v = params.optString(k).trim().removeSurrounding("\"").removeSurrounding("'")
            if (v.isNotEmpty()) return v
        }
        val keys = params.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = params.optString(k).trim().removeSurrounding("\"").removeSurrounding("'")
            if (v.isNotEmpty() && (v.contains('/') || v.contains('.')) && !v.contains('\n') && v.length < 250) {
                return v
            }
        }
        return ""
    }

    fun extractFileNameFromPath(path: String, fallback: String = "file"): String {
        val clean = path.trim().removeSurrounding("\"").removeSurrounding("'")
        if (clean.isBlank()) return fallback
        val name = clean.substringAfterLast('/')
        return name.ifBlank { fallback }
    }

    fun parseIsoTimestamp(isoString: String?): Long {
        if (isoString.isNullOrBlank()) return 0L
        return try {
            Instant.parse(isoString).toEpochMilli()
        } catch (e: Exception) {
            0L
        }
    }

    fun readTranscriptTurnLines(file: File, maxTailBytes: Long = 512 * 1024L): List<String> {
        return try {
            val length = file.length()
            val rawLines: List<String> = if (length <= maxTailBytes) {
                file.readLines()
            } else {
                RandomAccessFile(file, "r").use { raf ->
                    val seekPos = length - maxTailBytes
                    raf.seek(seekPos)
                    val buffer = ByteArray(maxTailBytes.toInt())
                    raf.readFully(buffer)
                    val chunk = String(buffer, StandardCharsets.UTF_8)
                    val split = chunk.lines()
                    if (split.size > 1) split.subList(1, split.size) else split
                }
            }

            var lastUserIdx = -1
            for (i in rawLines.indices) {
                val l = rawLines[i]
                if (l.contains("\"USER_INPUT\"")) {
                    try {
                        val obj = JSONObject(l)
                        if (obj.optString("type") == "USER_INPUT") {
                            lastUserIdx = i
                        }
                    } catch (e: Throwable) {}
                }
            }

            if (lastUserIdx >= 0) {
                rawLines.subList(lastUserIdx, rawLines.size).filter { it.isNotBlank() }
            } else {
                rawLines.filter { it.isNotBlank() }
            }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    fun parseTranscriptTurn(context: Context, cliConvId: String?): ParsedTranscriptTurn {
        val transcriptFile = findActiveTranscriptFile(context, cliConvId) ?: return ParsedTranscriptTurn()
        return try {
            val turnLines = readTranscriptTurnLines(transcriptFile)
            if (turnLines.isEmpty()) return ParsedTranscriptTurn()

            val steps = mutableListOf<AgentExecutionStep>()
            val thoughts = mutableListOf<String>()
            var assistantResponse: String? = null
            var prevTimestampMs = 0L

            for (i in turnLines.indices) {
                val line = turnLines[i]
                if (line.isBlank()) continue
                val obj = try { JSONObject(line) } catch (e: Throwable) { continue }
                val type = obj.optString("type")
                val createdAt = obj.optString("created_at")
                val currTimestampMs = parseIsoTimestamp(createdAt)

                if (type == "PLANNER_RESPONSE") {
                    val content = obj.optString("content").trim()
                    if (content.isNotBlank()) {
                        assistantResponse = content
                    }

                    val thinking = obj.optString("thinking").trim()
                    if (thinking.isNotBlank()) {
                        if (!thoughts.contains(thinking)) {
                            thoughts.add(thinking)
                        }
                        val durationSec = if (prevTimestampMs > 0L && currTimestampMs > prevTimestampMs) {
                            ((currTimestampMs - prevTimestampMs) / 1000).toInt().coerceIn(1, 300)
                        } else 6

                        val safeThinking = thinking
                        steps.add(
                            AgentExecutionStep(
                                id = "step_${i}_thought",
                                kind = AgentStepKind.THOUGHT,
                                title = "Thought",
                                detail = safeThinking,
                                durationSeconds = durationSec,
                                isCompleted = true
                            )
                        )
                    }

                    val toolCalls = obj.optJSONArray("tool_calls")
                    if (toolCalls != null && toolCalls.length() > 0) {
                        for (t in 0 until toolCalls.length()) {
                            val tc = toolCalls.optJSONObject(t) ?: continue
                            val toolName = tc.optString("name").substringAfterLast(":")
                            val args = tc.optJSONObject("args")
                                ?: tc.optJSONObject("parameters")
                                ?: tc.optJSONObject("arguments")

                            val nextGenericContent = if (i + 1 < turnLines.size) {
                                try {
                                    val nextObj = JSONObject(turnLines[i + 1])
                                    if (nextObj.optString("type") == "GENERIC") {
                                        nextObj.optString("content").ifBlank { null }
                                    } else null
                                } catch (e: Throwable) { null }
                            } else null

                            when (toolName) {
                                "read_file", "view_file" -> {
                                    val path = extractPathFromParams(args)
                                    val fileName = extractFileNameFromPath(path, fallback = "file")
                                    val start = args?.optInt("StartLine", -1) ?: -1
                                    val end = args?.optInt("EndLine", -1) ?: -1
                                    val linesStr = if (start > 0 && end >= start) "#L$start-$end" else if (start > 0) "#L$start" else null
                                    steps.add(
                                        AgentExecutionStep(
                                            id = "step_${i}_tool_${t}",
                                            kind = AgentStepKind.ANALYZED,
                                            title = fileName,
                                            filePath = path.ifEmpty { null },
                                            lines = linesStr,
                                            isCompleted = true
                                        )
                                    )
                                }
                                "write_file", "write_to_file", "edit_file", "replace_file_content", "multi_replace_file_content" -> {
                                    val path = extractPathFromParams(args)
                                    val fileName = extractFileNameFromPath(path, fallback = "file")
                                    steps.add(
                                        AgentExecutionStep(
                                            id = "step_${i}_tool_${t}",
                                            kind = AgentStepKind.EDITED,
                                            title = fileName,
                                            filePath = path.ifEmpty { null },
                                            diff = computeEditStepDiff(args),
                                            isCompleted = true
                                        )
                                    )
                                }
                                "run_command" -> {
                                    val action = args?.optString("toolAction")?.trim()?.removeSurrounding("\"")?.removeSurrounding("'") ?: ""
                                    val summary = args?.optString("toolSummary")?.trim()?.removeSurrounding("\"")?.removeSurrounding("'") ?: ""
                                    val cmd = args?.optString("CommandLine")?.trim()?.removeSurrounding("\"")?.removeSurrounding("'") ?: ""
                                    val title = when {
                                        action.isNotBlank() -> action
                                        summary.isNotBlank() -> summary
                                        cmd.isNotBlank() -> cmd
                                        else -> "Command"
                                    }
                                    val safeCmdDetail = nextGenericContent
                                    steps.add(
                                        AgentExecutionStep(
                                            id = "step_${i}_tool_${t}",
                                            kind = AgentStepKind.COMMAND,
                                            title = title,
                                            detail = safeCmdDetail,
                                            isCompleted = true
                                        )
                                    )
                                }
                                "schedule" -> {
                                    val dur = args?.optInt("DurationSeconds", 0) ?: 0
                                    steps.add(
                                        AgentExecutionStep(
                                            id = "step_${i}_tool_${t}",
                                            kind = AgentStepKind.COMMAND,
                                            title = if (dur > 0) "schedule (${dur}s)" else "schedule task",
                                            isCompleted = true
                                        )
                                    )
                                }
                                "search_web" -> {
                                    val q = args?.optString("query")?.trim()?.removeSurrounding("\"") ?: ""
                                    steps.add(
                                        AgentExecutionStep(
                                            id = "step_${i}_tool_${t}",
                                            kind = AgentStepKind.SEARCH,
                                            title = q.ifEmpty { "web" },
                                            isCompleted = true
                                        )
                                    )
                                }
                                else -> {
                                    steps.add(
                                        AgentExecutionStep(
                                            id = "step_${i}_tool_${t}",
                                            kind = AgentStepKind.COMMAND,
                                            title = toolName.replace('_', ' '),
                                            detail = nextGenericContent,
                                            isCompleted = true
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                if (currTimestampMs > 0L) {
                    prevTimestampMs = currTimestampMs
                }
            }
            ParsedTranscriptTurn(
                thinking = if (thoughts.isNotEmpty()) thoughts.joinToString("\n\n") else null,
                steps = if (steps.isNotEmpty()) steps else null,
                assistantResponse = assistantResponse
            )
        } catch (e: Throwable) {
            ParsedTranscriptTurn()
        }
    }

    fun parseTranscriptToMessages(context: Context, cliConvId: String?): List<ChatMessage> {
        val transcriptFile = findActiveTranscriptFile(context, cliConvId) ?: return emptyList()
        return try {
            val lines = transcriptFile.readLines().filter { it.isNotBlank() }
            if (lines.isEmpty()) return emptyList()

            val messages = mutableListOf<ChatMessage>()
            var currentAssistantSteps = mutableListOf<AgentExecutionStep>()
            var currentThinking = StringBuilder()
            var currentResponseText: String? = null
            var currentAssistantTimestamp = 0L

            fun flushAssistantIfNeeded() {
                if (currentAssistantSteps.isNotEmpty() || currentThinking.isNotEmpty() || !currentResponseText.isNullOrBlank()) {
                    val finalThinking = if (currentThinking.isNotEmpty()) currentThinking.toString().trim() else null
                    val resp = currentResponseText ?: ""
                    messages.add(
                        ChatMessage(
                            id = UUID.randomUUID().toString(),
                            role = MessageRole.ASSISTANT,
                            content = resp,
                            timestamp = if (currentAssistantTimestamp > 0L) currentAssistantTimestamp else System.currentTimeMillis(),
                            thinking = finalThinking,
                            steps = currentAssistantSteps.toList()
                        )
                    )
                    currentAssistantSteps = mutableListOf()
                    currentThinking = StringBuilder()
                    currentResponseText = null
                    currentAssistantTimestamp = 0L
                }
            }

            for (i in lines.indices) {
                val raw = lines[i]
                val obj = try { JSONObject(raw) } catch (e: Throwable) { continue }
                val type = obj.optString("type")
                val createdAt = obj.optString("created_at")
                val timestamp = parseIsoTimestamp(createdAt).let { if (it > 0L) it else System.currentTimeMillis() }

                if (type == "USER_INPUT") {
                    flushAssistantIfNeeded()
                    val text = obj.optString("content").trim()
                    if (text.isNotEmpty()) {
                        messages.add(
                            ChatMessage(
                                id = UUID.randomUUID().toString(),
                                role = MessageRole.USER,
                                content = text,
                                timestamp = timestamp
                            )
                        )
                    }
                } else if (type == "SYSTEM_MESSAGE") {
                    flushAssistantIfNeeded()
                    var content = obj.optString("content").trim()
                    if (content.contains("<SYSTEM_MESSAGE>")) {
                        content = content.substringAfter("<SYSTEM_MESSAGE>").substringBefore("</SYSTEM_MESSAGE>").trim()
                    }
                    if (content.contains("content=")) {
                        content = content.substringAfter("content=").trim()
                    }
                    if (content.isNotEmpty()) {
                        messages.add(
                            ChatMessage(
                                id = UUID.randomUUID().toString(),
                                role = MessageRole.USER,
                                content = content,
                                timestamp = timestamp
                            )
                        )
                    }
                } else if (type == "PLANNER_RESPONSE") {
                    if (currentAssistantTimestamp == 0L) {
                        currentAssistantTimestamp = timestamp
                    }
                    val text = obj.optString("content").trim()
                    if (text.isNotEmpty()) {
                        currentResponseText = text
                    }
                    val thinking = obj.optString("thinking").trim()
                    if (thinking.isNotEmpty()) {
                        if (currentThinking.isNotEmpty()) currentThinking.append("\n\n")
                        currentThinking.append(thinking)
                        currentAssistantSteps.add(
                            AgentExecutionStep(
                                id = "step_${i}_thought",
                                kind = AgentStepKind.THOUGHT,
                                title = context.getString(R.string.chat_thought_process),
                                detail = thinking,
                                durationSeconds = 6,
                                isCompleted = true
                            )
                        )
                    }
                    val toolCalls = obj.optJSONArray("tool_calls")
                    if (toolCalls != null && toolCalls.length() > 0) {
                        for (t in 0 until toolCalls.length()) {
                            val tc = toolCalls.optJSONObject(t) ?: continue
                            val toolName = tc.optString("name").substringAfterLast(":")
                            val args = tc.optJSONObject("args")
                                ?: tc.optJSONObject("parameters")
                                ?: tc.optJSONObject("arguments")

                            val nextGenericContent = if (i + 1 < lines.size) {
                                try {
                                    val nextObj = JSONObject(lines[i + 1])
                                    if (nextObj.optString("type") == "GENERIC") {
                                        nextObj.optString("content").ifBlank { null }
                                    } else null
                                } catch (e: Throwable) { null }
                            } else null

                            when (toolName) {
                                "read_file", "view_file" -> {
                                    val path = extractPathFromParams(args)
                                    val fileName = extractFileNameFromPath(path, fallback = "file")
                                    val start = args?.optInt("StartLine", -1) ?: -1
                                    val end = args?.optInt("EndLine", -1) ?: -1
                                    val linesStr = if (start > 0 && end >= start) "#L$start-$end" else if (start > 0) "#L$start" else null
                                    currentAssistantSteps.add(
                                        AgentExecutionStep(
                                            id = "step_${i}_tool_${t}",
                                            kind = AgentStepKind.ANALYZED,
                                            title = fileName,
                                            filePath = path.ifEmpty { null },
                                            lines = linesStr,
                                            detail = nextGenericContent,
                                            isCompleted = true
                                        )
                                    )
                                }
                                "write_file", "write_to_file", "edit_file", "replace_file_content", "multi_replace_file_content" -> {
                                    val path = extractPathFromParams(args)
                                    val fileName = extractFileNameFromPath(path, fallback = "file")
                                    currentAssistantSteps.add(
                                        AgentExecutionStep(
                                            id = "step_${i}_tool_${t}",
                                            kind = AgentStepKind.EDITED,
                                            title = fileName,
                                            filePath = path.ifEmpty { null },
                                            diff = computeEditStepDiff(args),
                                            detail = nextGenericContent,
                                            isCompleted = true
                                        )
                                    )
                                }
                                "run_command" -> {
                                    val action = args?.optString("toolAction")?.trim()?.removeSurrounding("\"")?.removeSurrounding("'") ?: ""
                                    val summary = args?.optString("toolSummary")?.trim()?.removeSurrounding("\"")?.removeSurrounding("'") ?: ""
                                    val cmd = args?.optString("CommandLine")?.trim()?.removeSurrounding("\"")?.removeSurrounding("'") ?: ""
                                    val title = when {
                                        action.isNotBlank() -> action
                                        summary.isNotBlank() -> summary
                                        cmd.isNotBlank() -> cmd
                                        else -> "Command"
                                    }
                                    currentAssistantSteps.add(
                                        AgentExecutionStep(
                                            id = "step_${i}_tool_${t}",
                                            kind = AgentStepKind.COMMAND,
                                            title = title,
                                            detail = nextGenericContent,
                                            isCompleted = true
                                        )
                                    )
                                }
                                "invoke_subagent" -> {
                                    val subList = args?.optJSONArray("Subagents")
                                    val firstSub = subList?.optJSONObject(0)
                                    val role = firstSub?.optString("Role") ?: args?.optString("Role") ?: context.getString(R.string.subagent_default_title)
                                    currentAssistantSteps.add(
                                        AgentExecutionStep(
                                            id = "step_${i}_tool_${t}",
                                            kind = AgentStepKind.WORKING,
                                            title = role,
                                            detail = nextGenericContent,
                                            isCompleted = true
                                        )
                                    )
                                }
                                else -> {
                                    currentAssistantSteps.add(
                                        AgentExecutionStep(
                                            id = "step_${i}_tool_${t}",
                                            kind = AgentStepKind.COMMAND,
                                            title = toolName.replace('_', ' '),
                                            detail = nextGenericContent,
                                            isCompleted = true
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
            flushAssistantIfNeeded()
            messages
        } catch (e: Throwable) {
            emptyList()
        }
    }
}
