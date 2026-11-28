package id.vern.antigravity.viewmodel

import android.content.Context
import android.system.Os
import id.vern.antigravity.R
import id.vern.antigravity.data.AgentExecutionStep
import id.vern.antigravity.data.AgentStepKind
import id.vern.antigravity.data.AttachedFile
import id.vern.antigravity.data.ChangedFileDetail
import id.vern.antigravity.data.ChatMessage
import id.vern.antigravity.data.InteractionOption
import id.vern.antigravity.data.MessageRole
import id.vern.antigravity.data.MessageStatus
import id.vern.antigravity.data.RunningSubagent
import id.vern.antigravity.data.TurnFileChanges
import id.vern.antigravity.executor.EngineExecutor
import id.vern.antigravity.oauth.OAuthManager
import id.vern.antigravity.service.AgyModelsService
import id.vern.antigravity.service.PromptCompletionNotifier
import id.vern.antigravity.service.TerminalSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

/**
 * Coordinates per-conversation CLI / shell turn execution, stream-json parsing,
 * transcript fallback, and git turn diff calculation.
 */
class TurnExecutionDelegate {

    companion object {
        private val quotaLimitPatterns = listOf(
            "resource_exhausted",
            "quota exceeded",
            "quota_exceeded",
            "rate limit",
            "rate_limit",
            "too many requests",
            "http 429",
            "status 429",
            "429 ",
            "usage limit",
            "limit reached",
            "weekly limit",
            "model capacity exhausted",
            "out of quota"
        )

        fun isQuotaLimitError(exitCode: Int, outputText: String): Boolean {
            if (outputText.isBlank()) return false
            val lower = outputText.lowercase()
            val matchesPattern = quotaLimitPatterns.any { lower.contains(it) }
            if (!matchesPattern) return false
            return exitCode != 0 ||
                lower.contains("resource_exhausted") ||
                lower.contains("quota exceeded") ||
                lower.contains("rate limit exceeded") ||
                lower.contains("rate limit reached") ||
                lower.contains("weekly limit reached") ||
                lower.contains("usage limit reached") ||
                lower.contains("model capacity exhausted") ||
                lower.contains("out of quota")
        }

        internal fun formatCommandResultMarkdown(commandObj: JSONObject?, fallbackResponse: String?): String? {
            if (commandObj == null) return null
            val name = commandObj.optString("name", "").trim()
            val data = commandObj.optJSONObject("data")
            if (name.isEmpty()) return null

            return buildString {
                when (name.lowercase()) {
                    "help" -> {
                        appendLine("### Available CLI Commands")
                        val cmds = data?.optJSONArray("commands")
                        if (cmds != null && cmds.length() > 0) {
                            for (i in 0 until cmds.length()) {
                                val c = cmds.optJSONObject(i) ?: continue
                                val cName = c.optString("name", "").trim()
                                val cDesc = c.optString("description", "").trim()
                                val aliasesArr = c.optJSONArray("aliases")
                                val aliases = if (aliasesArr != null && aliasesArr.length() > 0) {
                                    (0 until aliasesArr.length()).mapNotNull { idx ->
                                        aliasesArr.optString(idx, "").takeIf { it.isNotBlank() }?.let { "`/$it`" }
                                    }.joinToString(", ")
                                } else ""
                                val aliasSuffix = if (aliases.isNotEmpty()) " ($aliases)" else ""
                                if (cName.isNotEmpty()) {
                                    appendLine("- `/$cName`$aliasSuffix — $cDesc")
                                }
                            }
                        } else if (!fallbackResponse.isNullOrBlank()) {
                            append(fallbackResponse.replace("\t", " — "))
                        }
                    }
                    "usage", "quota" -> {
                        appendLine("### Model Quota Usage")
                        val desc = data?.optString("description", "")?.trim().orEmpty()
                        val groups = data?.optJSONArray("groups")
                        if (groups != null && groups.length() > 0) {
                            for (i in 0 until groups.length()) {
                                val g = groups.optJSONObject(i) ?: continue
                                val gName = g.optString("name", "").trim()
                                val gDesc = g.optString("description", "").trim()
                                if (gName.isNotEmpty()) {
                                    appendLine()
                                    appendLine("**$gName**")
                                    if (gDesc.isNotEmpty()) appendLine(gDesc)
                                }
                                val buckets = g.optJSONArray("buckets")
                                if (buckets != null) {
                                    for (j in 0 until buckets.length()) {
                                        val b = buckets.optJSONObject(j) ?: continue
                                        val bName = b.optString("name", "").trim()
                                        val frac = b.optDouble("remaining_fraction", -1.0)
                                        val pctStr = if (frac >= 0.0) "${(frac * 100.0).toInt()}%" else ""
                                        val bDesc = b.optString("description", "").trim()
                                        appendLine("- **$bName**: `$pctStr` — $bDesc")
                                    }
                                }
                            }
                        } else if (!fallbackResponse.isNullOrBlank()) {
                            append(fallbackResponse.replace("\t", " — "))
                        }
                        if (desc.isNotEmpty()) {
                            appendLine()
                            appendLine("> $desc")
                        }
                    }
                    "skills" -> {
                        appendLine("### Available Skills")
                        val skills = data?.optJSONArray("skills")
                        if (skills != null && skills.length() > 0) {
                            for (i in 0 until skills.length()) {
                                val s = skills.optJSONObject(i) ?: continue
                                val sName = s.optString("name", "").trim()
                                val sDesc = s.optString("description", "").trim()
                                val builtin = if (s.optBoolean("builtin", false)) " *(built-in)*" else ""
                                if (sName.isNotEmpty()) {
                                    appendLine("- `/$sName`$builtin — $sDesc")
                                }
                            }
                        } else {
                            appendLine("No skills available.")
                        }
                    }
                    "permissions" -> {
                        appendLine("### Tool Permissions")
                        val perms = data?.optJSONArray("permissions")
                        if (perms != null && perms.length() > 0) {
                            var anyRule = false
                            for (i in 0 until perms.length()) {
                                val p = perms.optJSONObject(i) ?: continue
                                val scope = p.optString("scope", "global")
                                for (mode in listOf("allow", "ask", "deny")) {
                                    val arr = p.optJSONArray(mode) ?: continue
                                    if (arr.length() > 0) {
                                        anyRule = true
                                        appendLine("- **$scope ($mode)**: " + (0 until arr.length()).joinToString(", ") { "`${arr.optString(it)}`" })
                                    }
                                }
                            }
                            if (!anyRule) appendLine("No explicit rules configured.")
                        } else if (!fallbackResponse.isNullOrBlank()) {
                            append(fallbackResponse.replace("\t", " — "))
                        }
                    }
                    "hooks" -> {
                        appendLine("### Configured Hooks")
                        val hooks = data?.optJSONArray("hooks")
                        if (hooks != null && hooks.length() > 0) {
                            for (i in 0 until hooks.length()) {
                                val h = hooks.optJSONObject(i)
                                if (h != null) {
                                    appendLine("- `${h.optString("event", "hook")}`: ${h.optString("command", h.toString())}")
                                }
                            }
                        } else {
                            appendLine("No hooks configured.")
                        }
                    }
                    "agents" -> {
                        appendLine("### Available Agents")
                        val agents = data?.optJSONArray("agents")
                        if (agents != null && agents.length() > 0) {
                            for (i in 0 until agents.length()) {
                                val a = agents.optJSONObject(i) ?: continue
                                appendLine("- **${a.optString("name", "agent")}** — ${a.optString("description", "")}")
                            }
                        } else {
                            appendLine("No custom agents configured.")
                        }
                    }
                    "config" -> {
                        appendLine("### Engine Configuration")
                        val cfg = data?.optJSONObject("config")
                        if (cfg != null) {
                            val keys = cfg.keys()
                            val sortedKeys = mutableListOf<String>()
                            while (keys.hasNext()) sortedKeys.add(keys.next())
                            sortedKeys.sort()
                            for (k in sortedKeys) {
                                if (k == "userSettings") continue
                                val v = cfg.opt(k)
                                if (v != null && v != JSONObject.NULL && v.toString().isNotBlank()) {
                                    appendLine("- **$k**: `$v`")
                                }
                            }
                        } else if (!fallbackResponse.isNullOrBlank()) {
                            append(fallbackResponse.replace("\t", " — "))
                        }
                    }
                    else -> {
                        appendLine("### /$name")
                        if (!fallbackResponse.isNullOrBlank()) {
                            append(fallbackResponse.trim().replace("\t", " — "))
                        } else if (data != null) {
                            appendLine("```json")
                            appendLine(data.toString(2))
                            appendLine("```")
                        }
                    }
                }
            }.trim()
        }
    }

    interface Callbacks {
        fun workspacePath(): String?
        fun selectedModelCliId(): String?
        fun securityPreset(): String
        fun thinkingBudget(): String = ""
        fun skipPermissions(): Boolean = true
        fun enableTerminalSandbox(): Boolean = false
        fun getCliSessionId(convId: String): String?
        fun setCliSessionId(convId: String, cliSessionId: String)
        fun getMessages(convId: String): List<ChatMessage>
        fun updateMessages(convId: String, transform: (List<ChatMessage>) -> List<ChatMessage>): List<ChatMessage>
        fun addLog(tag: String, message: String, isError: Boolean = false)
        fun setPendingAuthUrl(url: String?)
        fun showGoogleSignInDialog()
        fun refreshAuthState(context: Context)
        fun addRunningTask(name: String, command: String, convId: String)
        fun removeRunningTask(stepId: String, cmdClean: String, convId: String)
        fun addRunningSubagent(subagent: RunningSubagent)
        fun updateSubagentConversationId(stableStepId: String, detectedSubConvId: String)
        fun parseTranscriptTurn(context: Context, cliConvId: String?): ParsedTranscriptTurn
        suspend fun requestUserInteraction(
            title: String,
            detail: String,
            options: List<InteractionOption>
        ): String? = null
        fun onModelLimitReached(prompt: String, reason: String) {}
    }

    suspend fun runAiTurn(
        context: Context,
        prompt: String,
        convId: String,
        attachments: List<AttachedFile>,
        callbacks: Callbacks,
        voiceInstruction: String? = null,
        ephemeralVoiceOnly: Boolean = false,
        suppressCompletionNotification: Boolean = false,
        onEphemeralResult: ((String, Boolean) -> Unit)? = null
    ) {
        val effectiveCallbacks = if (ephemeralVoiceOnly) {
            object : Callbacks by callbacks {
                override fun getMessages(convId: String): List<ChatMessage> = emptyList()
                override fun updateMessages(
                    convId: String,
                    transform: (List<ChatMessage>) -> List<ChatMessage>
                ): List<ChatMessage> = emptyList()
            }
        } else {
            callbacks
        }
        val turnStartTime = System.currentTimeMillis()
        val assistantMsgId = UUID.randomUUID().toString()
        if (!ephemeralVoiceOnly) {
            val initialAssistantMsg = ChatMessage(
                id = assistantMsgId,
                role = MessageRole.ASSISTANT,
                content = "",
                status = MessageStatus.SENDING,
                thinking = null
            )
            effectiveCallbacks.updateMessages(convId) { it + initialAssistantMsg }
        }

        val currentWorkspace = callbacks.workspacePath()
        val wsDir = if (!currentWorkspace.isNullOrBlank()) File(currentWorkspace) else null
        val validWsDir = if (wsDir != null && wsDir.exists() && wsDir.isDirectory) wsDir else null

        if (prompt.startsWith("!") || prompt.startsWith("$ ")) {
            val cmd = if (prompt.startsWith("!")) prompt.substring(1).trim() else prompt.substring(2).trim()
            effectiveCallbacks.updateMessages(convId) { list ->
                list.map { msg ->
                    if (msg.id == assistantMsgId) {
                        msg.copy(commandRun = cmd, thinking = "Executing command in Linux shell...")
                    } else msg
                }
            }

            val outputSb = StringBuilder()
            val exitCode = EngineExecutor.executeCommand(context, cmd, workingDir = validWsDir) { line, _ ->
                outputSb.append(line).append("\n")
                effectiveCallbacks.updateMessages(convId) { list ->
                    list.map { msg ->
                        if (msg.id == assistantMsgId) {
                            msg.copy(commandOutput = outputSb.toString())
                        } else msg
                    }
                }
            }

            effectiveCallbacks.updateMessages(convId) { list ->
                list.map { msg ->
                    if (msg.id == assistantMsgId) {
                        msg.copy(
                            status = if (exitCode == 0) MessageStatus.SENT else MessageStatus.ERROR,
                            content = "",
                            thinking = null
                        )
                    } else msg
                }
            }
            val shellSummary = outputSb.toString().ifBlank {
                if (exitCode == 0) "Command completed successfully" else "Command failed with code $exitCode"
            }
            onEphemeralResult?.invoke(shellSummary, exitCode == 0)
            if (!suppressCompletionNotification) {
                PromptCompletionNotifier.notifyPromptCompleted(
                    context = context,
                    conversationId = convId,
                    prompt = cmd,
                    response = shellSummary,
                    isSuccess = exitCode == 0
                )
            }
            return
        }

        val stagedAttachments = stageAttachments(context, convId, attachments)
        val outputSb = StringBuilder()
        val rawCliId = callbacks.selectedModelCliId()?.trim().orEmpty()
        val cliId = AgyModelsService.cleanModelId(rawCliId)
        val priorCliSession = callbacks.getCliSessionId(convId)

        callbacks.addLog(
            "Agent",
            "Turn started: prompt='${prompt.take(120)}', attachments=${attachments.size}, model='${cliId.ifEmpty { "default" }}'"
        )
        TerminalSessionManager.ensureAgyLiveSession(autoSwitch = false)
        TerminalSessionManager.appendAgyLine("[AGY] Turn started (model: ${cliId.ifEmpty { "default" }})", isCommand = true)
        if (validWsDir != null) {
            callbacks.addLog("Agent", "Workspace: ${validWsDir.absolutePath}")
            TerminalSessionManager.appendAgyLine("Working Directory: ${validWsDir.absolutePath}")
        }

        OAuthManager.checkAndImportExistingToken(context)
        OAuthManager.refreshTokenIfNeeded(context)
        OAuthManager.syncCompanionStateFiles(context)

        var activeCliConvId: String? = priorCliSession
        var lastStreamUpdateMs = 0L
        var lastStepUpdateMs = 0L
        var lastTranscriptPollMs = 0L
        val authUrlRegex = "(https://accounts\\.google\\.com/o/oauth2/auth\\S+)".toRegex()
        var toolDetectedPlanPath: String? = null
        var toolRequestedFeedback = false
        var commandResultObj: JSONObject? = null

        var currentTurn = 0
        val maxAutoTurns = 8
        var autoContinue = true
        var exitCode = 0
        var authRequired = false

        val trimmedPrompt = prompt.trimStart()
        val isInteractiveSlash = trimmedPrompt.startsWith("/grill-me", ignoreCase = true) ||
            trimmedPrompt.startsWith("/automation", ignoreCase = true) ||
            prompt.contains(" /grill-me", ignoreCase = true)
        val isReadOnlyCliCmd = listOf(
            "/help", "/usage", "/quota", "/skills", "/permissions", "/hooks",
            "/agents", "/config", "/settings", "/credits", "/changelog", "/effort", "/model", "/plugin"
        ).any { trimmedPrompt.equals(it, ignoreCase = true) || trimmedPrompt.startsWith("$it ", ignoreCase = true) }

        val shouldTryAcp = !ephemeralVoiceOnly && !isReadOnlyCliCmd && (!callbacks.skipPermissions() || isInteractiveSlash)
        if (shouldTryAcp) {
            val acpResult = tryRunAcpTurn(
                context = context,
                prompt = prompt,
                convId = convId,
                assistantMsgId = assistantMsgId,
                stagedAttachments = stagedAttachments,
                callbacks = effectiveCallbacks,
                validWsDir = validWsDir,
                priorCliSession = priorCliSession,
                voiceInstruction = voiceInstruction,
                outputSb = outputSb
            )
            if (acpResult != null) {
                activeCliConvId = acpResult.first ?: activeCliConvId
                exitCode = acpResult.second
                autoContinue = false
            }
        }

        val isRefreshingFileChanges = AtomicBoolean(false)
        var liveFileChangesJob: Job? = null
        var streamFlushJob: Job? = null
        val turnCoroutineContext = coroutineContext

        fun flushStreamOutputNow() {
            streamFlushJob?.cancel()
            streamFlushJob = null
            val currentOutput = outputSb.toString()
            if (currentOutput.isEmpty()) return
            lastStreamUpdateMs = System.currentTimeMillis()
            effectiveCallbacks.updateMessages(convId) { list ->
                list.map { msg ->
                    if (msg.id == assistantMsgId && msg.content != currentOutput) {
                        msg.copy(content = currentOutput)
                    } else msg
                }
            }
        }

        fun scheduleStreamFlush() {
            val now = System.currentTimeMillis()
            if (now - lastStreamUpdateMs > 250L) {
                flushStreamOutputNow()
            } else if (streamFlushJob?.isActive != true) {
                streamFlushJob = CoroutineScope(turnCoroutineContext + Dispatchers.IO).launch {
                    kotlinx.coroutines.delay(250L)
                    flushStreamOutputNow()
                }
            }
        }

        while (autoContinue && currentTurn < maxAutoTurns && coroutineContext.isActive) {
            currentTurn++
            outputSb.clear()

            val priorHistory = if (currentTurn == 1 && (activeCliConvId ?: priorCliSession).isNullOrBlank() && !ephemeralVoiceOnly) {
                effectiveCallbacks.getMessages(convId)
                    .filter { (it.role == MessageRole.USER || it.role == MessageRole.ASSISTANT) && it.content.isNotBlank() && it.status != MessageStatus.SENDING }
                    .dropLast(1)
                    .takeLast(6)
            } else {
                emptyList()
            }

            val args = buildCliArgs(
                context = context,
                cliId = cliId,
                securityPreset = callbacks.securityPreset(),
                thinkingBudget = callbacks.thinkingBudget(),
                skipPermissions = callbacks.skipPermissions(),
                enableTerminalSandbox = callbacks.enableTerminalSandbox(),
                validWsDir = validWsDir,
                sessionToResume = activeCliConvId ?: priorCliSession,
                currentTurn = currentTurn,
                prompt = prompt,
                stagedAttachments = stagedAttachments,
                voiceInstruction = voiceInstruction,
                priorMessages = priorHistory
            )

            if (currentTurn > 1) {
                callbacks.addLog("Agent", "Autonomous turn $currentTurn started...")
            }

            exitCode = EngineExecutor.executeAgy(context, args, workingDir = validWsDir, executionKey = convId) { line, _ ->
                val trimmed = line.trim()
                val match = authUrlRegex.find(trimmed)
                if (match != null) {
                    callbacks.setPendingAuthUrl(match.value)
                    callbacks.showGoogleSignInDialog()
                }

                if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                    try {
                        val json = JSONObject(trimmed)
                        val event = json.optString("event")
                        val detectedConv = json.optString("conversation_id").ifBlank {
                            json.optString("conversationId").ifBlank {
                                json.optString("session_id").ifBlank {
                                    json.optString("sessionId")
                                }
                            }
                        }
                        if (detectedConv.isNotBlank() && activeCliConvId != detectedConv) {
                            activeCliConvId = detectedConv
                            callbacks.setCliSessionId(convId, detectedConv)
                            callbacks.addLog("Agent", "Linked conversation session: $detectedConv")
                        }

                        when (event) {
                            "step_update" -> {
                                val stepUpdate = json.optJSONObject("step_update")
                                if (stepUpdate != null) {
                                    val stepType = stepUpdate.optString("step_type")
                                    val state = stepUpdate.optString("state")
                                    if (stepType == "tool") {
                                        flushStreamOutputNow()
                                        val parsedTool = parseToolStep(
                                            context = context,
                                            stepUpdate = stepUpdate,
                                            state = state,
                                            convId = convId,
                                            validWsDir = validWsDir,
                                            callbacks = callbacks,
                                            onPlanDetected = { path, requestedFeedback ->
                                                if (path != null) toolDetectedPlanPath = path
                                                if (requestedFeedback) toolRequestedFeedback = true
                                            }
                                        )
                                        val now = System.currentTimeMillis()
                                        if (state != "ACTIVE" || parsedTool.kind == AgentStepKind.EDITED || now - lastStepUpdateMs > 300L) {
                                            lastStepUpdateMs = now
                                            val liveParsed = if (now - lastTranscriptPollMs > 3000L || state != "ACTIVE") {
                                                lastTranscriptPollMs = now
                                                callbacks.parseTranscriptTurn(context, activeCliConvId)
                                            } else null
                                            val liveThinking = liveParsed?.thinking
                                            val liveThoughts = liveParsed?.steps?.filter { it.kind == AgentStepKind.THOUGHT }.orEmpty()
                                            val resolvedTool = if (parsedTool.kind == AgentStepKind.EDITED && parsedTool.diff.isNullOrBlank()) {
                                                val fallbackDiff = liveParsed?.steps
                                                    ?.lastOrNull {
                                                        it.kind == AgentStepKind.EDITED &&
                                                            !it.diff.isNullOrBlank() &&
                                                            (it.filePath == parsedTool.filePath || it.title == parsedTool.title)
                                                    }
                                                    ?.diff
                                                if (!fallbackDiff.isNullOrBlank()) parsedTool.copy(diff = fallbackDiff) else parsedTool
                                            } else {
                                                parsedTool
                                            }

                                            effectiveCallbacks.updateMessages(convId) { list ->
                                                list.map { msg ->
                                                    if (msg.id == assistantMsgId) {
                                                        val curSteps = msg.steps
                                                        val completedSteps = curSteps.map {
                                                            if (it.kind == AgentStepKind.THOUGHT && !it.isCompleted) it.copy(isCompleted = true) else it
                                                        }
                                                        val existingIdx = completedSteps.indexOfFirst {
                                                            it.id == resolvedTool.id || (it.title == resolvedTool.title && it.kind == resolvedTool.kind)
                                                        }
                                                        val baseSteps = if (existingIdx >= 0) {
                                                            completedSteps.toMutableList().apply { set(existingIdx, resolvedTool) }
                                                        } else {
                                                            completedSteps + resolvedTool
                                                        }
                                                        val updatedSteps = if (liveThoughts.isNotEmpty() && baseSteps.none { it.kind == AgentStepKind.THOUGHT }) {
                                                            liveThoughts + baseSteps
                                                        } else {
                                                            baseSteps
                                                        }
                                                        msg.copy(steps = updatedSteps, thinking = liveThinking ?: msg.thinking)
                                                    } else msg
                                                }
                                            }

                                            if (resolvedTool.kind == AgentStepKind.EDITED &&
                                                state != "ACTIVE" &&
                                                !ephemeralVoiceOnly &&
                                                isRefreshingFileChanges.compareAndSet(false, true)
                                            ) {
                                                liveFileChangesJob = CoroutineScope(turnCoroutineContext + Dispatchers.IO).launch {
                                                    try {
                                                        val liveChanges = computeTurnFileChanges(context, callbacks.workspacePath())
                                                        if (liveChanges != null) {
                                                            effectiveCallbacks.updateMessages(convId) { list ->
                                                                list.map { msg ->
                                                                    if (msg.id == assistantMsgId) msg.copy(fileChanges = liveChanges) else msg
                                                                }
                                                            }
                                                        }
                                                    } finally {
                                                        isRefreshingFileChanges.set(false)
                                                    }
                                                }
                                            }
                                        }
                                    } else if (stepType == "thought" || stepType == "reasoning") {
                                        flushStreamOutputNow()
                                        val thoughtText = stepUpdate.optString("text", "")
                                        val duration = stepUpdate.optInt("duration_seconds", 6).coerceAtLeast(1)
                                        val thoughtStep = AgentExecutionStep(
                                            id = "step_live_thought_${assistantMsgId}",
                                            kind = AgentStepKind.THOUGHT,
                                            title = "Thought",
                                            detail = thoughtText,
                                            durationSeconds = duration,
                                            isCompleted = false
                                        )
                                        val now = System.currentTimeMillis()
                                        if (now - lastStepUpdateMs > 300L) {
                                            lastStepUpdateMs = now
                                            if (thoughtText.isNotBlank()) {
                                                TerminalSessionManager.appendAgyLine("* Thinking: ${thoughtText.replace('\n', ' ')}")
                                            }
                                            effectiveCallbacks.updateMessages(convId) { list ->
                                                list.map { msg ->
                                                    if (msg.id == assistantMsgId) {
                                                        val curSteps = msg.steps
                                                        val updated = if (curSteps.isNotEmpty() && curSteps.last().kind == AgentStepKind.THOUGHT && !curSteps.last().isCompleted) {
                                                            curSteps.dropLast(1) + curSteps.last().copy(
                                                                detail = thoughtText,
                                                                durationSeconds = duration
                                                            )
                                                        } else {
                                                            curSteps.map { it.copy(isCompleted = true) } + thoughtStep
                                                        }
                                                        msg.copy(thinking = thoughtText.ifBlank { msg.thinking }, steps = updated)
                                                    } else msg
                                                }
                                            }
                                        }
                                    } else if (stepType == "agent_response") {
                                        val textDelta = stepUpdate.optString("text_delta", "")
                                        if (textDelta.isNotEmpty()) {
                                            outputSb.append(textDelta)
                                            scheduleStreamFlush()
                                        }
                                    }
                                }
                            }
                            "command_result" -> {
                                commandResultObj = json.optJSONObject("command") ?: commandResultObj
                            }
                            "result" -> {
                                streamFlushJob?.cancel()
                                streamFlushJob = null
                                val resultObj = json.optJSONObject("result")
                                val cmdObj = resultObj?.optJSONObject("command") ?: commandResultObj
                                val rawResponse = resultObj?.optString("response")
                                val formattedCommand = formatCommandResultMarkdown(cmdObj, rawResponse)
                                val finalResponse = formattedCommand ?: rawResponse
                                if (!finalResponse.isNullOrBlank()) {
                                    TerminalSessionManager.appendAgyLine(finalResponse)
                                    outputSb.clear()
                                    outputSb.append(finalResponse)
                                    val currentOutput = outputSb.toString().trim()
                                    val liveParsed = callbacks.parseTranscriptTurn(context, activeCliConvId)
                                    val liveThinking = liveParsed.thinking
                                    val liveTranscriptSteps = liveParsed.steps
                                    effectiveCallbacks.updateMessages(convId) { list ->
                                        list.map { msg ->
                                            if (msg.id == assistantMsgId) {
                                                msg.copy(
                                                    content = currentOutput,
                                                    thinking = liveThinking ?: msg.thinking,
                                                    steps = liveTranscriptSteps ?: msg.steps
                                                )
                                            } else msg
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {
                        TerminalSessionManager.appendAgyLine(line)
                        outputSb.append(line).append("\n")
                        scheduleStreamFlush()
                    }
                } else if (trimmed.isNotEmpty()) {
                    val isModeFallbackWarning = trimmed.startsWith("warning: --mode ", ignoreCase = true) ||
                        trimmed.equals("continuing in the default mode.", ignoreCase = true)
                    if (isModeFallbackWarning) {
                        TerminalSessionManager.appendAgyLine(line)
                    } else if (!trimmed.startsWith("Get:") && !trimmed.startsWith("#") && !trimmed.startsWith("Unpacking")) {
                        TerminalSessionManager.appendAgyLine(line)
                        outputSb.append(line).append("\n")
                        scheduleStreamFlush()
                    }
                }
            }

            streamFlushJob?.cancel()
            streamFlushJob = null
            if (outputSb.isNotEmpty()) {
                val finalCurrentOutput = outputSb.toString()
                effectiveCallbacks.updateMessages(convId) { list ->
                    list.map { msg ->
                        if (msg.id == assistantMsgId && msg.content != finalCurrentOutput) {
                            msg.copy(content = finalCurrentOutput)
                        } else msg
                    }
                }
            }

            val outputStr = outputSb.toString()
            authRequired = outputStr.contains("authentication required", ignoreCase = true) ||
                outputStr.contains("authentication failed", ignoreCase = true) ||
                outputStr.contains("visit the url to log in", ignoreCase = true)

            val intermediateParsed = callbacks.parseTranscriptTurn(context, activeCliConvId)
            val transcriptResponse = intermediateParsed.assistantResponse
            val hasTextOutput = outputSb.isNotBlank() || !transcriptResponse.isNullOrBlank()
            val hasExecutedSteps = !intermediateParsed.steps.isNullOrEmpty()

            if (exitCode != 0 || authRequired || hasTextOutput || !hasExecutedSteps) {
                autoContinue = false
            } else {
                callbacks.addLog("Agent", "Turn $currentTurn completed with intermediate tool execution. Continuing to next turn for completion...")
            }
        }

        callbacks.setPendingAuthUrl(null)

        if (exitCode == 0) {
            OAuthManager.checkAndImportExistingToken(context)
            OAuthManager.syncCompanionStateFiles(context)
            callbacks.refreshAuthState(context)
        } else if (authRequired) {
            callbacks.showGoogleSignInDialog()
        }

        liveFileChangesJob?.cancel()
        liveFileChangesJob?.join()

        val elapsedSec = ((System.currentTimeMillis() - turnStartTime) / 1000).coerceAtLeast(1)
        val durText = if (elapsedSec >= 60) "${elapsedSec / 60}m" else "${elapsedSec}s"
        val turnChanges = if (ephemeralVoiceOnly) null else computeTurnFileChanges(context, callbacks.workspacePath())

        val currentSteps = effectiveCallbacks.getMessages(convId).find { it.id == assistantMsgId }?.steps.orEmpty()
        val finalParsed = callbacks.parseTranscriptTurn(context, activeCliConvId)
        val finalThinking = finalParsed.thinking
            ?: currentSteps.find { it.kind == AgentStepKind.THOUGHT }?.detail

        val transcriptSteps = finalParsed.steps
        val finalSteps = if (!transcriptSteps.isNullOrEmpty()) {
            transcriptSteps
        } else if (currentSteps.isNotEmpty()) {
            val hasThought = currentSteps.any { it.kind == AgentStepKind.THOUGHT }
            if (!hasThought && finalThinking != null) {
                val thoughtStep = AgentExecutionStep(
                    id = "step_thought_final",
                    kind = AgentStepKind.THOUGHT,
                    title = "Thought",
                    detail = finalThinking,
                    durationSeconds = elapsedSec.toInt(),
                    isCompleted = true
                )
                listOf(thoughtStep) + currentSteps.map { it.copy(isCompleted = true) }
            } else {
                currentSteps.map { it.copy(isCompleted = true) }
            }
        } else if (finalThinking != null) {
            listOf(
                AgentExecutionStep(
                    id = "step_thought_final",
                    kind = AgentStepKind.THOUGHT,
                    title = "Thought",
                    detail = finalThinking,
                    durationSeconds = elapsedSec.toInt(),
                    isCompleted = true
                )
            )
        } else {
            emptyList()
        }

        val transcriptResponse = finalParsed.assistantResponse
        val finalContent = if (exitCode != 0 && authRequired) {
            context.getString(R.string.chat_auth_required_error)
        } else if (outputSb.isNotEmpty()) {
            outputSb.toString().trim()
        } else if (!transcriptResponse.isNullOrBlank()) {
            transcriptResponse
        } else if (finalSteps.isNotEmpty()) {
            val executedNames = finalSteps.filter { it.kind != AgentStepKind.THOUGHT }.map { it.title }
            if (executedNames.isNotEmpty()) {
                context.getString(R.string.chat_step_execution_finished, executedNames.joinToString(", "))
            } else {
                context.getString(R.string.chat_inspection_finished)
            }
        } else if (exitCode != 0) {
            context.getString(R.string.chat_core_execution_error, exitCode)
        } else {
            context.getString(R.string.chat_task_completed)
        }

        val (detectedPlanPath, requiresPlanReview) = detectPlanArtifactInfo(
            context = context,
            finalContent = finalContent,
            finalSteps = finalSteps,
            activeCliConvId = activeCliConvId,
            toolDetectedPlanPath = toolDetectedPlanPath,
            toolRequestedFeedback = toolRequestedFeedback
        )

        callbacks.addLog("Agent", "Turn completed ($durText). Output: ${finalContent.take(150)}")
        TerminalSessionManager.appendAgyLine("[AGY] Turn $currentTurn completed ($durText)")

        effectiveCallbacks.updateMessages(convId) { list ->
            list.map { msg ->
                if (msg.id == assistantMsgId) {
                    val summaryParts = mutableListOf<String>()
                    if (finalSteps.any { it.kind == AgentStepKind.ANALYZED }) summaryParts.add("Explored file")
                    if (finalSteps.any { it.kind == AgentStepKind.COMMAND }) summaryParts.add("ran commands")
                    if (finalSteps.any { it.kind == AgentStepKind.EDITED } || (turnChanges != null && turnChanges.totalFiles > 0)) {
                        summaryParts.add("edited file")
                    }
                    val actSummary = if (summaryParts.isNotEmpty()) summaryParts.joinToString(", ") else null

                    msg.copy(
                        status = if (exitCode == 0) MessageStatus.SENT else MessageStatus.ERROR,
                        content = finalContent,
                        thinking = finalThinking,
                        durationText = durText,
                        actionSummary = actSummary,
                        fileChanges = turnChanges,
                        steps = finalSteps,
                        planArtifactPath = detectedPlanPath,
                        requiresPlanReview = requiresPlanReview
                    )
                } else msg
            }
        }
        onEphemeralResult?.invoke(finalContent, exitCode == 0)
        if (isQuotaLimitError(exitCode, finalContent)) {
            callbacks.onModelLimitReached(prompt, finalContent.take(220))
        }
        if (!suppressCompletionNotification) {
            PromptCompletionNotifier.notifyPromptCompleted(
                context = context,
                conversationId = convId,
                prompt = prompt,
                response = finalContent,
                isSuccess = exitCode == 0
            )
        }
    }

    private fun stageAttachments(
        context: Context,
        convId: String,
        attachments: List<AttachedFile>
    ): List<Triple<String, String, String>> {
        val stagedAttachments = mutableListOf<Triple<String, String, String>>()
        if (attachments.isEmpty()) return stagedAttachments

        val targetDirs = listOf(
            File(context.filesDir, ".gemini/antigravity-app/brain/$convId/.user_uploaded"),
            File(context.filesDir, "home/.gemini/antigravity-app/brain/$convId/.user_uploaded")
        )
        for (dir in targetDirs) {
            dir.mkdirs()
            dir.setReadable(true, false)
            dir.setExecutable(true, false)
            try { Os.chmod(dir.absolutePath, 493) } catch (_: Exception) {}
        }
        for (att in attachments) {
            val safeName = att.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            var finalPath: String? = null
            for (dir in targetDirs) {
                val destFile = File(dir, safeName)
                val sourceFile = att.localPath?.let { File(it) }
                if (sourceFile != null && sourceFile.exists()) {
                    runCatching { sourceFile.copyTo(destFile, overwrite = true) }
                } else {
                    runCatching {
                        context.contentResolver.openInputStream(att.uri)?.use { input ->
                            destFile.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                }
                destFile.setReadable(true, false)
                try { Os.chmod(destFile.absolutePath, 420) } catch (_: Exception) {}
                if (destFile.exists() && finalPath == null) {
                    finalPath = destFile.absolutePath
                }
            }
            stagedAttachments.add(Triple(att.name, finalPath ?: (att.localPath ?: att.name), att.mimeType))
        }
        return stagedAttachments
    }

    private fun buildCliArgs(
        context: Context,
        cliId: String,
        securityPreset: String,
        thinkingBudget: String,
        skipPermissions: Boolean,
        enableTerminalSandbox: Boolean,
        validWsDir: File?,
        sessionToResume: String?,
        currentTurn: Int,
        prompt: String,
        stagedAttachments: List<Triple<String, String, String>>,
        voiceInstruction: String? = null,
        priorMessages: List<ChatMessage> = emptyList()
    ): List<String> {
        val args = mutableListOf<String>()
        val currentSec = securityPreset.lowercase()
        val isAutoOrTurbo = currentSec == "turbo" || currentSec == "turbo mode" || currentSec == "auto" || currentSec == "permissive"
        if (skipPermissions && isAutoOrTurbo) {
            args.add("--dangerously-skip-permissions")
        }
        val mode = when (currentSec) {
            "request review", "request_review" -> "plan"
            "default", "standard" -> "accept-edits"
            else -> "auto"
        }
        args.add("--mode")
        args.add(mode)
        if (enableTerminalSandbox) {
            args.add("--sandbox")
        }
        args.add("--output-format")
        args.add("stream-json")
        if (cliId.isNotBlank()) {
            args.add("--model")
            args.add(cliId)
        }
        val effortArg = thinkingBudget.trim().lowercase()
        if (effortArg in setOf("low", "medium", "high", "xhigh", "max") &&
            AgyModelsService.supportsEffortFlag(cliId)
        ) {
            args.add("--effort")
            args.add(effortArg)
        }
        if (validWsDir != null) {
            args.add("--add-dir")
            args.add(validWsDir.absolutePath)
        }
        if (!sessionToResume.isNullOrBlank()) {
            args.add("--conversation")
            args.add(sessionToResume)
        }
        args.add("--print")
        val voicePrefix = if (!voiceInstruction.isNullOrBlank() && !prompt.trimStart().startsWith("/")) {
            "${voiceInstruction.trim()}\n\n"
        } else {
            ""
        }
        val expandedPrompt = if (currentTurn == 1) expandMentionsContext(context, prompt, validWsDir) else prompt
        val basePrompt = if (currentTurn == 1 && priorMessages.isNotEmpty() && !expandedPrompt.trimStart().startsWith("/")) {
            buildString {
                append("[Previous conversation context]\n")
                priorMessages.forEach { msg ->
                    val speaker = if (msg.role == MessageRole.USER) "User" else "Assistant"
                    append("$speaker: ${msg.content.trim().take(600)}\n")
                }
                append("\n[Current request]\n")
                append(expandedPrompt)
            }
        } else {
            expandedPrompt
        }
        val currentPrompt = if (currentTurn == 1 && stagedAttachments.isNotEmpty()) {
            buildString {
                if (basePrompt.trimStart().startsWith("/")) {
                    append(basePrompt)
                    append("\n\n")
                } else if (voicePrefix.isNotEmpty()) {
                    append(voicePrefix)
                }
                append("### USER ATTACHMENTS\n")
                append("The user has attached the following ${stagedAttachments.size} file(s):\n")
                stagedAttachments.forEachIndexed { idx, (name, path, mime) ->
                    append("${idx + 1}. [Filename: $name]\n")
                    append("   Path: $path\n")
                    append("   MIME: $mime\n")
                    if (mime.startsWith("image/")) {
                        append("   Action: Call the `view_file` tool with AbsolutePath=\"$path\" to inspect this image.\n")
                    } else {
                        append("   Action: Call the `view_file` tool with AbsolutePath=\"$path\" to inspect this file.\n")
                    }
                }
                append("MANDATORY: You must inspect the attached file(s) with the `view_file` tool before answering.\n")
                if (!basePrompt.trimStart().startsWith("/")) {
                    append("\n### USER REQUEST\n")
                    append(if (basePrompt.isNotBlank()) basePrompt else "Please inspect the attached file(s) and assist the user.")
                }
            }
        } else if (currentTurn == 1) {
            if (voicePrefix.isNotEmpty()) "$voicePrefix$basePrompt" else basePrompt
        } else {
            "Continue and complete the task based on previous tool results. Provide thorough analysis or confirmation once finished."
        }
        args.add(currentPrompt)
        return args
    }

    private fun expandMentionsContext(
        context: Context,
        prompt: String,
        validWsDir: File?
    ): String {
        if (!prompt.contains("@")) return prompt
        val mentionRegex = Regex("""(?:^|\s)@(workspace|terminal|git)(?=\s|$|[.,;:!?])""", RegexOption.IGNORE_CASE)
        val found = mentionRegex.findAll(prompt).map { it.groupValues[1].lowercase() }.toSet()
        if (found.isEmpty()) return prompt

        val extraSections = mutableListOf<String>()

        if ("workspace" in found && validWsDir != null && validWsDir.isDirectory) {
            val entries = validWsDir.listFiles()
                ?.filter { !it.name.startsWith(".") }
                ?.sortedBy { it.name.lowercase() }
                ?.take(40)
                ?.joinToString(", ") { if (it.isDirectory) "${it.name}/" else it.name }
                .orEmpty()
            extraSections.add("[Workspace Context: ${validWsDir.absolutePath}]\nTop-level entries: $entries")
        }

        if ("terminal" in found) {
            val recentLines = TerminalSessionManager.lines.toList()
                .takeLast(40)
                .joinToString("\n") { it.text }
                .trim()
            if (recentLines.isNotEmpty()) {
                extraSections.add("[Terminal Context (${TerminalSessionManager.currentSessionName.value})]\n```\n$recentLines\n```")
            }
        }

        if ("git" in found && validWsDir != null && File(validWsDir, ".git").exists()) {
            val gitSb = StringBuilder()
            try {
                val proc = ProcessBuilder("git", "status", "-sb")
                    .directory(validWsDir)
                    .redirectErrorStream(true)
                    .start()
                val statusText = proc.inputStream.bufferedReader().readText().trim()
                proc.waitFor()
                if (statusText.isNotEmpty()) gitSb.appendLine(statusText)
                val diffProc = ProcessBuilder("git", "diff", "--stat")
                    .directory(validWsDir)
                    .redirectErrorStream(true)
                    .start()
                val diffStat = diffProc.inputStream.bufferedReader().readText().trim()
                diffProc.waitFor()
                if (diffStat.isNotEmpty()) gitSb.appendLine(diffStat)
            } catch (_: Exception) {}
            if (gitSb.isNotEmpty()) {
                extraSections.add("[Git Context]\n```\n${gitSb.toString().trim()}\n```")
            }
        }

        if (extraSections.isEmpty()) return prompt
        return "$prompt\n\n<attached_context>\n${extraSections.joinToString("\n\n")}\n</attached_context>"
    }

    private suspend fun tryRunAcpTurn(
        context: Context,
        prompt: String,
        convId: String,
        assistantMsgId: String,
        stagedAttachments: List<Triple<String, String, String>>,
        callbacks: Callbacks,
        validWsDir: File?,
        priorCliSession: String?,
        voiceInstruction: String?,
        outputSb: StringBuilder
    ): Pair<String?, Int>? = withContext(Dispatchers.IO) {
        val acpArgs = mutableListOf<String>()
        if (validWsDir != null) {
            acpArgs.add("--add-dir")
            acpArgs.add(validWsDir.absolutePath)
        }
        if (callbacks.enableTerminalSandbox()) {
            acpArgs.add("--sandbox")
        }
        val process = EngineExecutor.startAcpProcess(context, acpArgs, validWsDir) ?: return@withContext null
        var sessionStarted = false
        var resolvedSessionId: String? = priorCliSession
        var exitCode = 0
        val thoughtSb = StringBuilder()

        try {
            val writer = process.outputStream.bufferedWriter(Charsets.UTF_8)
            val reader = process.inputStream.bufferedReader(Charsets.UTF_8)

            fun sendRpc(obj: JSONObject) {
                writer.write(obj.toString())
                writer.newLine()
                writer.flush()
            }

            // 1. initialize
            sendRpc(JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "initialize")
                put("params", JSONObject().apply { put("protocolVersion", 1) })
            })

            var initOk = false
            while (coroutineContext.isActive) {
                val line = reader.readLine() ?: break
                val t = line.trim()
                if (!t.startsWith("{")) continue
                val msg = runCatching { JSONObject(t) }.getOrNull() ?: continue
                if (msg.optInt("id", -1) == 1) {
                    initOk = msg.has("result")
                    break
                }
            }
            if (!initOk) {
                process.destroyForcibly()
                return@withContext null
            }

            // 2. session/load or session/new
            val cwdStr = validWsDir?.absolutePath ?: context.filesDir.absolutePath
            val sessionMethod = if (!priorCliSession.isNullOrBlank()) "session/load" else "session/new"
            sendRpc(JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 2)
                put("method", sessionMethod)
                put("params", JSONObject().apply {
                    if (!priorCliSession.isNullOrBlank()) put("sessionId", priorCliSession)
                    put("cwd", cwdStr)
                    put("mcpServers", org.json.JSONArray())
                })
            })

            while (coroutineContext.isActive) {
                val line = reader.readLine() ?: break
                val t = line.trim()
                if (!t.startsWith("{")) continue
                val msg = runCatching { JSONObject(t) }.getOrNull() ?: continue
                if (msg.optInt("id", -1) == 2) {
                    val res = msg.optJSONObject("result")
                    val sid = res?.optString("sessionId")?.takeIf { it.isNotBlank() } ?: priorCliSession
                    if (!sid.isNullOrBlank()) {
                        resolvedSessionId = sid
                        callbacks.setCliSessionId(convId, sid)
                        callbacks.addLog("Agent", "ACP session linked: $sid")
                    }
                    break
                }
            }

            val sid = resolvedSessionId
            if (sid.isNullOrBlank()) {
                process.destroyForcibly()
                return@withContext null
            }

            // 3. session/prompt
            val basePrompt = expandMentionsContext(context, prompt, validWsDir)
            val fullText = if (stagedAttachments.isNotEmpty()) {
                buildString {
                    append(basePrompt)
                    append("\n\nAttached files:\n")
                    stagedAttachments.forEach { (n, p, _) -> append("- $n: $p\n") }
                }
            } else if (!voiceInstruction.isNullOrBlank() && !basePrompt.trimStart().startsWith("/")) {
                "${voiceInstruction.trim()}\n\n$basePrompt"
            } else {
                basePrompt
            }

            sessionStarted = true
            sendRpc(JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 3)
                put("method", "session/prompt")
                put("params", JSONObject().apply {
                    put("sessionId", sid)
                    put("prompt", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", fullText)
                        })
                    })
                })
            })

            var lastUiMs = 0L
            var acpFlushJob: Job? = null
            val acpScope = CoroutineScope(coroutineContext + Dispatchers.IO)

            fun flushAcpBuffersNow() {
                acpFlushJob?.cancel()
                acpFlushJob = null
                val curOut = outputSb.toString()
                val curThought = thoughtSb.toString().takeIf { it.isNotBlank() }
                if (curOut.isEmpty() && curThought == null) return
                lastUiMs = System.currentTimeMillis()
                callbacks.updateMessages(convId) { list ->
                    list.map { m ->
                        if (m.id == assistantMsgId) {
                            m.copy(
                                content = curOut.ifEmpty { m.content },
                                thinking = curThought ?: m.thinking
                            )
                        } else m
                    }
                }
            }

            fun scheduleAcpFlush() {
                val now = System.currentTimeMillis()
                if (now - lastUiMs > 200L) {
                    flushAcpBuffersNow()
                } else if (acpFlushJob?.isActive != true) {
                    acpFlushJob = acpScope.launch {
                        kotlinx.coroutines.delay(200L)
                        flushAcpBuffersNow()
                    }
                }
            }

            while (coroutineContext.isActive) {
                val line = reader.readLine() ?: break
                val t = line.trim()
                if (!t.startsWith("{")) continue
                val msg = runCatching { JSONObject(t) }.getOrNull() ?: continue
                val method = msg.optString("method", "")
                val idVal = if (msg.has("id") && !msg.isNull("id")) msg.opt("id") else null

                if (idVal != null && (msg.optInt("id", -1) == 3) && method.isEmpty()) {
                    if (msg.has("error")) exitCode = 1
                    break
                }

                when (method) {
                    "session/update" -> {
                        val update = msg.optJSONObject("params")?.optJSONObject("update") ?: continue
                        when (update.optString("sessionUpdate")) {
                            "agent_thought_chunk" -> {
                                val txt = update.optJSONObject("content")?.optString("text", "").orEmpty()
                                if (txt.isNotEmpty()) {
                                    thoughtSb.append(txt)
                                    scheduleAcpFlush()
                                }
                            }
                            "agent_message_chunk" -> {
                                val txt = update.optJSONObject("content")?.optString("text", "").orEmpty()
                                if (txt.isNotEmpty()) {
                                    outputSb.append(txt)
                                    scheduleAcpFlush()
                                }
                            }
                            "tool_call", "tool_call_update" -> {
                                flushAcpBuffersNow()
                                val callId = update.optString("toolCallId", UUID.randomUUID().toString())
                                val title = update.optString("title", "Tool")
                                val status = update.optString("status", "")
                                val step = AgentExecutionStep(
                                    id = "acp_$callId",
                                    kind = AgentStepKind.ANALYZED,
                                    title = title,
                                    detail = status,
                                    isCompleted = status == "completed" || status == "failed"
                                )
                                callbacks.updateMessages(convId) { list ->
                                    list.map { m ->
                                        if (m.id == assistantMsgId) {
                                            val idx = m.steps.indexOfFirst { it.id == step.id }
                                            val nextSteps = if (idx >= 0) {
                                                m.steps.toMutableList().apply { set(idx, step) }
                                            } else {
                                                m.steps + step
                                            }
                                            m.copy(steps = nextSteps)
                                        } else m
                                    }
                                }
                            }
                        }
                    }
                    "session/request_permission" -> {
                        val params = msg.optJSONObject("params") ?: JSONObject()
                        val toolCall = params.optJSONObject("toolCall")
                        val title = toolCall?.optString("title")?.takeIf { it.isNotBlank() } ?: "Agent Request"
                        val rawInput = toolCall?.opt("rawInput")?.toString()?.take(600).orEmpty()
                        val optsArr = params.optJSONArray("options")
                        val opts = mutableListOf<InteractionOption>()
                        if (optsArr != null) {
                            for (i in 0 until optsArr.length()) {
                                val o = optsArr.optJSONObject(i) ?: continue
                                val oid = o.optString("optionId", "").trim()
                                val oName = o.optString("name", oid).trim()
                                val oKind = o.optString("kind", "").trim()
                                if (oid.isNotEmpty()) opts.add(InteractionOption(oid, oName, oKind))
                            }
                        }
                        if (opts.isEmpty()) {
                            opts.add(InteractionOption("allow_once", "Allow Once", "allow_once"))
                            opts.add(InteractionOption("reject_once", "Reject", "reject_once"))
                        }
                        val chosen = callbacks.requestUserInteraction(title, rawInput, opts)
                        val outcomeObj = if (!chosen.isNullOrBlank()) {
                            JSONObject().apply {
                                put("outcome", "selected")
                                put("optionId", chosen)
                            }
                        } else {
                            JSONObject().apply { put("outcome", "cancelled") }
                        }
                        sendRpc(JSONObject().apply {
                            put("jsonrpc", "2.0")
                            put("id", idVal)
                            put("result", JSONObject().apply { put("outcome", outcomeObj) })
                        })
                    }
                }
            }

            acpFlushJob?.cancel()
            acpFlushJob = null
            if (outputSb.isNotEmpty()) {
                val finalOut = outputSb.toString()
                val finalThought = thoughtSb.toString().takeIf { it.isNotBlank() }
                callbacks.updateMessages(convId) { list ->
                    list.map { m ->
                        if (m.id == assistantMsgId) {
                            m.copy(content = finalOut, thinking = finalThought ?: m.thinking)
                        } else m
                    }
                }
            }
            resolvedSessionId to exitCode
        } catch (e: Exception) {
            callbacks.addLog("Agent", "ACP session fallback: ${e.message}")
            if (sessionStarted) (resolvedSessionId to 1) else null
        } finally {
            runCatching { process.destroyForcibly() }
        }
    }

    private fun parseToolStep(
        context: Context,
        stepUpdate: JSONObject,
        state: String,
        convId: String,
        validWsDir: File?,
        callbacks: Callbacks,
        onPlanDetected: (String?, Boolean) -> Unit
    ): AgentExecutionStep {
        val rawToolName = stepUpdate.optString("tool_name")
        val toolName = rawToolName.substringAfterLast(":")
        val toolInfo = stepUpdate.optJSONObject("tool_info")
        val params = toolInfo?.optJSONObject("parameters")
            ?: toolInfo?.optJSONObject("args")
            ?: toolInfo?.optJSONObject("arguments")
            ?: stepUpdate.optJSONObject("parameters")
            ?: stepUpdate.optJSONObject("args")
            ?: toolInfo

        val stepKind: AgentStepKind
        val stepTitle: String
        val stepLines: String?
        val stepDiff: String?
        val stepFilePath: String?

        when (toolName) {
            "read_file", "view_file" -> {
                val path = TranscriptTurnParser.extractPathFromParams(params)
                stepFilePath = path.ifEmpty { null }
                stepTitle = TranscriptTurnParser.extractFileNameFromPath(path, fallback = "file")
                val start = params?.optInt("StartLine", -1) ?: -1
                val end = params?.optInt("EndLine", -1) ?: -1
                stepLines = if (start > 0 && end >= start) "#L$start-$end" else if (start > 0) "#L$start" else null
                stepDiff = null
                stepKind = AgentStepKind.ANALYZED
            }
            "write_file", "write_to_file", "edit_file", "replace_file_content", "multi_replace_file_content" -> {
                val path = TranscriptTurnParser.extractPathFromParams(params)
                stepFilePath = path.ifEmpty { null }
                stepTitle = TranscriptTurnParser.extractFileNameFromPath(path, fallback = "file")
                stepLines = null
                stepDiff = TranscriptTurnParser.computeEditStepDiff(params)
                stepKind = AgentStepKind.EDITED
                val artMeta = params?.optJSONObject("ArtifactMetadata")
                    ?: params?.optJSONObject("artifactMetadata")
                if (artMeta != null) {
                    val reqFeedback = artMeta.optBoolean("RequestFeedback", false) ||
                        artMeta.optBoolean("requestFeedback", false)
                    val detectedPath = if (path.isNotBlank() && (reqFeedback || path.endsWith("implementation_plan.md", ignoreCase = true))) {
                        path
                    } else null
                    onPlanDetected(detectedPath, reqFeedback)
                } else if (path.endsWith("implementation_plan.md", ignoreCase = true)) {
                    onPlanDetected(path, false)
                }
            }
            "run_command" -> {
                val action = params?.optString("toolAction")?.trim()?.removeSurrounding("\"")?.removeSurrounding("'") ?: ""
                val summary = params?.optString("toolSummary")?.trim()?.removeSurrounding("\"")?.removeSurrounding("'") ?: ""
                val cmd = params?.optString("CommandLine")?.trim()?.removeSurrounding("\"")?.removeSurrounding("'") ?: ""
                val cleanCmd = cmd.trim()
                stepTitle = when {
                    action.isNotBlank() -> action
                    summary.isNotBlank() -> summary
                    cleanCmd.isNotBlank() -> cleanCmd
                    else -> "Command"
                }
                stepFilePath = null
                stepLines = null
                stepDiff = null
                stepKind = AgentStepKind.COMMAND
            }
            "invoke_subagent" -> {
                val subList = params?.optJSONArray("Subagents")
                val firstSub = subList?.optJSONObject(0) ?: params
                val role = firstSub?.optString("Role")?.ifEmpty { firstSub.optString("role") } ?: ""
                val type = firstSub?.optString("TypeName")?.ifEmpty { firstSub.optString("typeName") } ?: ""
                stepTitle = role.ifEmpty { type.ifEmpty { context.getString(R.string.subagent_default_title) } }
                stepFilePath = null
                stepLines = null
                stepDiff = null
                stepKind = AgentStepKind.WORKING
            }
            "manage_task" -> {
                val action = params?.optString("Action") ?: "status"
                val taskId = params?.optString("TaskId") ?: ""
                stepTitle = if (taskId.isNotBlank()) "manage_task $action $taskId" else "manage_task $action"
                stepFilePath = null
                stepLines = null
                stepDiff = null
                stepKind = AgentStepKind.COMMAND
            }
            "schedule" -> {
                val dur = params?.optInt("DurationSeconds", 0) ?: 0
                stepTitle = if (dur > 0) "schedule (${dur}s)" else "schedule task"
                stepFilePath = null
                stepLines = null
                stepDiff = null
                stepKind = AgentStepKind.COMMAND
            }
            "search_web", "grep_search" -> {
                val q = params?.optString("query")?.trim()?.removeSurrounding("\"") ?: ""
                stepTitle = q.ifEmpty { if (toolName == "search_web") "web" else "code" }
                stepFilePath = null
                stepLines = null
                stepDiff = null
                stepKind = AgentStepKind.SEARCH
            }
            "list_dir" -> {
                val path = TranscriptTurnParser.extractPathFromParams(params)
                stepFilePath = path.ifEmpty { null }
                stepTitle = TranscriptTurnParser.extractFileNameFromPath(path, fallback = "directory")
                stepLines = null
                stepDiff = null
                stepKind = AgentStepKind.ANALYZED
            }
            else -> {
                stepFilePath = null
                stepTitle = toolName.replace('_', ' ')
                stepLines = null
                stepDiff = null
                stepKind = AgentStepKind.COMMAND
            }
        }

        val stepDetail = stepUpdate.optString("output").ifEmpty {
            stepUpdate.optString("result").ifEmpty { null }
        }
        val stableStepId = "step_live_${stepKind.name}_${stepFilePath ?: stepTitle}_${stepLines ?: ""}".replace(" ", "_")
        val execStep = AgentExecutionStep(
            id = stableStepId,
            kind = stepKind,
            title = stepTitle,
            lines = stepLines,
            diff = stepDiff,
            filePath = stepFilePath,
            detail = stepDetail,
            isCompleted = state != "ACTIVE"
        )

        if (toolName == "run_command") {
            val cmdClean = params?.optString("CommandLine")?.trim()?.removeSurrounding("\"")?.removeSurrounding("'") ?: ""
            if (state == "ACTIVE") {
                if (cmdClean.isNotBlank()) {
                    callbacks.addRunningTask(stepTitle, cmdClean, convId)
                }
            } else {
                callbacks.removeRunningTask(stableStepId, cmdClean, convId)
            }
        } else if (toolName == "invoke_subagent") {
            if (state == "ACTIVE") {
                val subList = params?.optJSONArray("Subagents")
                if (subList != null && subList.length() > 0) {
                    for (s in 0 until subList.length()) {
                        val sObj = subList.optJSONObject(s) ?: continue
                        val r = sObj.optString("Role").ifEmpty { sObj.optString("role") }
                        val t = sObj.optString("TypeName").ifEmpty { sObj.optString("typeName") }
                        val p = sObj.optString("Prompt").ifEmpty { sObj.optString("prompt") }
                        val subId = "${stableStepId}_$s"
                        callbacks.addRunningSubagent(RunningSubagent(id = subId, typeName = t, role = r, prompt = p, isRunning = true))
                    }
                } else {
                    val r = params?.optString("Role")?.ifEmpty { params.optString("role") } ?: context.getString(R.string.subagent_default_title)
                    val t = params?.optString("TypeName")?.ifEmpty { params.optString("typeName") } ?: ""
                    val p = params?.optString("Prompt")?.ifEmpty { params.optString("prompt") } ?: ""
                    callbacks.addRunningSubagent(RunningSubagent(id = stableStepId, typeName = t, role = r, prompt = p, isRunning = true))
                }
            } else {
                val detectedSubConvId = try {
                    if (!stepDetail.isNullOrBlank()) {
                        val detailJson = JSONObject(stepDetail)
                        val res = detailJson.optJSONObject("result")
                        res?.optString("conversationId")?.ifEmpty { null }
                            ?: detailJson.optString("conversationId").ifEmpty { null }
                    } else null
                } catch (_: Exception) {
                    val regex = "([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})".toRegex()
                    stepDetail?.let { regex.find(it)?.value }
                }
                if (!detectedSubConvId.isNullOrBlank()) {
                    callbacks.updateSubagentConversationId(stableStepId, detectedSubConvId)
                    val summary = ConversationSummary(
                        id = detectedSubConvId,
                        title = stepTitle,
                        timestamp = System.currentTimeMillis(),
                        messageCount = 1,
                        isPinned = false,
                        projectPath = validWsDir?.absolutePath
                    )
                    val history = ConversationPersistence.loadConversationHistory(context)
                    if (history.none { it.id == detectedSubConvId }) {
                        ConversationPersistence.saveConversationHistory(context, listOf(summary) + history)
                    }
                }
            }
        }

        if (state == "ACTIVE") {
            val toolDesc = when {
                stepKind == AgentStepKind.COMMAND -> stepTitle
                stepKind == AgentStepKind.EDITED -> "Edit $stepTitle ${stepDiff ?: ""}".trim()
                stepKind == AgentStepKind.ANALYZED -> "Read $stepTitle ${stepLines ?: ""}".trim()
                stepKind == AgentStepKind.SEARCH -> "Search $stepTitle"
                else -> "$toolName $stepTitle"
            }
            TerminalSessionManager.appendAgyLine("> $toolDesc", isCommand = true)
        } else if (!stepDetail.isNullOrBlank()) {
            TerminalSessionManager.appendAgyLine(stepDetail)
        }

        return execStep
    }

    fun detectPlanArtifactInfo(
        context: Context,
        finalContent: String,
        finalSteps: List<AgentExecutionStep>,
        activeCliConvId: String?,
        toolDetectedPlanPath: String?,
        toolRequestedFeedback: Boolean
    ): Pair<String?, Boolean> {
        val stepPlanPath = finalSteps.firstOrNull {
            it.kind == AgentStepKind.EDITED &&
                it.filePath?.endsWith("implementation_plan.md", ignoreCase = true) == true
        }?.filePath
        val linkRegex = Regex("""file://([^\s)\]"']+\.md)""", RegexOption.IGNORE_CASE)
        val linkedMdPath = linkRegex.findAll(finalContent)
            .map { it.groupValues[1] }
            .firstOrNull { it.endsWith("implementation_plan.md", ignoreCase = true) }
            ?: linkRegex.find(finalContent)?.groupValues?.getOrNull(1)

        var resolvedPath = toolDetectedPlanPath ?: stepPlanPath ?: linkedMdPath
        if (resolvedPath == null && activeCliConvId != null) {
            val candidates = listOf(
                File(context.filesDir, "home/.gemini/antigravity-cli/brain/$activeCliConvId/implementation_plan.md"),
                File(context.filesDir, ".gemini/antigravity-cli/brain/$activeCliConvId/implementation_plan.md"),
                File(context.filesDir, "home/.gemini/antigravity-app/brain/$activeCliConvId/implementation_plan.md"),
                File(context.filesDir, ".gemini/antigravity-app/brain/$activeCliConvId/implementation_plan.md")
            )
            resolvedPath = candidates.firstOrNull { it.isFile && it.exists() }?.absolutePath
        }

        val mentionsProceed = finalContent.contains("Proceed", ignoreCase = true) &&
            (finalContent.contains("plan", ignoreCase = true) || resolvedPath != null)
        val mentionsPlanFile = finalContent.contains("implementation_plan.md", ignoreCase = true)
        val requiresReview = toolRequestedFeedback ||
            (resolvedPath != null && (mentionsProceed || mentionsPlanFile)) ||
            (mentionsPlanFile && mentionsProceed)

        return resolvedPath to requiresReview
    }

    internal fun parseGitPorcelainLine(line: String): Pair<String, String>? {
        val trimmed = line.trimEnd()
        if (trimmed.length < 3) return null
        val st = trimmed.substring(0, 2).trim()
        val rawPath = trimmed.substring(2).trim().trim('"', '\'')
        val path = if (rawPath.contains(" -> ")) rawPath.substringAfterLast(" -> ").trim('"', '\'') else rawPath
        if (path.isBlank()) return null
        val statusDesc = when {
            st.contains("D") -> "D"
            st.contains("A") || st.contains("?") -> "A"
            else -> "M"
        }
        return path to statusDesc
    }

    internal fun parseGitNumstatLine(line: String): Triple<String, Int, Int>? {
        val parts = line.trim().split(Regex("""\s+"""), limit = 3)
        if (parts.size < 3) return null
        val add = parts[0].toIntOrNull() ?: 0
        val del = parts[1].toIntOrNull() ?: 0
        val path = parts[2].trim().trim('"', '\'')
        if (path.isBlank()) return null
        return Triple(path, add, del)
    }

    suspend fun computeTurnFileChanges(context: Context, workspacePath: String?): TurnFileChanges? = withContext(Dispatchers.IO) {
        val root = workspacePath?.let { File(it) } ?: return@withContext null
        if (!root.exists() || !root.isDirectory) return@withContext null

        val repoTopSb = StringBuilder()
        val gitCheckExit = EngineExecutor.executeCommand(
            context,
            "git rev-parse --show-toplevel 2>/dev/null",
            workingDir = root
        ) { line, _ ->
            if (line.isNotBlank()) repoTopSb.append(line.trim())
        }
        val repoRoot = repoTopSb.toString().trim()
            .takeIf { gitCheckExit == 0 && it.isNotEmpty() }
            ?.let { File(it) }
            ?.takeIf { it.isDirectory }
            ?: return@withContext null

        val fileList = mutableListOf<ChangedFileDetail>()
        var totalAdd = 0
        var totalDel = 0

        try {
            val statusMap = mutableMapOf<String, String>()
            EngineExecutor.executeCommand(context, "git status --porcelain 2>/dev/null", workingDir = repoRoot) { line, _ ->
                parseGitPorcelainLine(line)?.let { (path, statusDesc) ->
                    statusMap[path] = statusDesc
                }
            }

            val numstats = mutableListOf<Triple<String, Int, Int>>()
            EngineExecutor.executeCommand(context, "git diff --numstat 2>/dev/null", workingDir = repoRoot) { line, _ ->
                parseGitNumstatLine(line)?.let { numstats.add(it) }
            }

            val diffsMap = mutableMapOf<String, String>()
            if (numstats.isNotEmpty()) {
                for ((path, _, _) in numstats.take(30)) {
                    val fileDiffSb = StringBuilder()
                    val escapedPath = path.replace("'", "'\\''")
                    EngineExecutor.executeCommand(
                        context,
                        "git diff -U3 -- '$escapedPath' 2>/dev/null",
                        workingDir = repoRoot
                    ) { line, _ ->
                        if (fileDiffSb.length < 5000) {
                            fileDiffSb.append(line).append("\n")
                        }
                    }
                    val diffStr = fileDiffSb.toString().take(5000)
                    if (diffStr.isNotBlank()) {
                        diffsMap[path] = diffStr
                    }
                }
            }

            for ((path, add, del) in numstats) {
                totalAdd += add
                totalDel += del
                val st = statusMap[path] ?: "M"
                val diffContent = diffsMap[path] ?: "[File modified (${add} additions, ${del} deletions)]"
                fileList.add(
                    ChangedFileDetail(
                        name = path.substringAfterLast('/'),
                        relativePath = path,
                        status = st,
                        additions = add,
                        deletions = del,
                        diff = diffContent
                    )
                )
            }

            for ((path, st) in statusMap) {
                if (fileList.none { it.relativePath == path }) {
                    val diffContent = diffsMap[path] ?: if (st == "A") "[New file created]" else "[File deleted]"
                    fileList.add(
                        ChangedFileDetail(
                            name = path.substringAfterLast('/'),
                            relativePath = path,
                            status = st,
                            additions = if (st == "A") 1 else 0,
                            deletions = if (st == "D") 1 else 0,
                            diff = diffContent
                        )
                    )
                    if (st == "A") totalAdd++
                    if (st == "D") totalDel++
                }
            }
        } catch (_: Throwable) {}

        if (fileList.isNotEmpty()) {
            TurnFileChanges(
                totalFiles = fileList.size,
                totalAdditions = totalAdd,
                totalDeletions = totalDel,
                files = fileList
            )
        } else null
    }
}
