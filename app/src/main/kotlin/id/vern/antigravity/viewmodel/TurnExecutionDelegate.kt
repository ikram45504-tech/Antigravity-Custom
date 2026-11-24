package id.vern.antigravity.viewmodel

import android.content.Context
import android.system.Os
import id.vern.antigravity.R
import id.vern.antigravity.data.AgentExecutionStep
import id.vern.antigravity.data.AgentStepKind
import id.vern.antigravity.data.AttachedFile
import id.vern.antigravity.data.ChangedFileDetail
import id.vern.antigravity.data.ChatMessage
import id.vern.antigravity.data.MessageRole
import id.vern.antigravity.data.MessageStatus
import id.vern.antigravity.data.RunningSubagent
import id.vern.antigravity.data.TurnFileChanges
import id.vern.antigravity.executor.EngineExecutor
import id.vern.antigravity.oauth.OAuthManager
import id.vern.antigravity.service.AgyModelsService
import id.vern.antigravity.service.PromptCompletionNotifier
import id.vern.antigravity.service.TerminalSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.coroutines.coroutineContext

/**
 * Coordinates per-conversation CLI / shell turn execution, stream-json parsing,
 * transcript fallback, and git turn diff calculation.
 */
class TurnExecutionDelegate {

    interface Callbacks {
        fun workspacePath(): String?
        fun selectedModelCliId(): String?
        fun securityPreset(): String
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

        var currentTurn = 0
        val maxAutoTurns = 8
        var autoContinue = true
        var exitCode = 0
        var authRequired = false

        while (autoContinue && currentTurn < maxAutoTurns && coroutineContext.isActive) {
            currentTurn++
            outputSb.clear()

            val args = buildCliArgs(
                cliId = cliId,
                securityPreset = callbacks.securityPreset(),
                validWsDir = validWsDir,
                sessionToResume = activeCliConvId ?: priorCliSession,
                currentTurn = currentTurn,
                prompt = prompt,
                stagedAttachments = stagedAttachments,
                voiceInstruction = voiceInstruction
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
                                        if (state != "ACTIVE" || now - lastStepUpdateMs > 300L) {
                                            lastStepUpdateMs = now
                                            val liveParsed = if (now - lastTranscriptPollMs > 3000L || state != "ACTIVE") {
                                                lastTranscriptPollMs = now
                                                callbacks.parseTranscriptTurn(context, activeCliConvId)
                                            } else null
                                            val liveThinking = liveParsed?.thinking
                                            val liveThoughts = liveParsed?.steps?.filter { it.kind == AgentStepKind.THOUGHT }.orEmpty()

                                            effectiveCallbacks.updateMessages(convId) { list ->
                                                list.map { msg ->
                                                    if (msg.id == assistantMsgId) {
                                                        val curSteps = msg.steps
                                                        val completedSteps = curSteps.map {
                                                            if (it.kind == AgentStepKind.THOUGHT && !it.isCompleted) it.copy(isCompleted = true) else it
                                                        }
                                                        val existingIdx = completedSteps.indexOfFirst {
                                                            it.id == parsedTool.id || (it.title == parsedTool.title && it.kind == parsedTool.kind)
                                                        }
                                                        val baseSteps = if (existingIdx >= 0) {
                                                            completedSteps.toMutableList().apply { set(existingIdx, parsedTool) }
                                                        } else {
                                                            completedSteps + parsedTool
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
                                        }
                                    } else if (stepType == "thought" || stepType == "reasoning") {
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
                                            val now = System.currentTimeMillis()
                                            if (now - lastStreamUpdateMs > 250L) {
                                                lastStreamUpdateMs = now
                                                val currentOutput = outputSb.toString()
                                                effectiveCallbacks.updateMessages(convId) { list ->
                                                    list.map { msg ->
                                                        if (msg.id == assistantMsgId) msg.copy(content = currentOutput) else msg
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            "result" -> {
                                val resultObj = json.optJSONObject("result")
                                val finalResponse = resultObj?.optString("response")
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
                        val now = System.currentTimeMillis()
                        if (now - lastStreamUpdateMs > 250L) {
                            lastStreamUpdateMs = now
                            val currentOutput = outputSb.toString()
                            effectiveCallbacks.updateMessages(convId) { list ->
                                list.map { msg ->
                                    if (msg.id == assistantMsgId) msg.copy(content = currentOutput) else msg
                                }
                            }
                        }
                    }
                } else if (trimmed.isNotEmpty()) {
                    if (!trimmed.startsWith("Get:") && !trimmed.startsWith("#") && !trimmed.startsWith("Unpacking")) {
                        TerminalSessionManager.appendAgyLine(line)
                        outputSb.append(line).append("\n")
                        val now = System.currentTimeMillis()
                        if (now - lastStreamUpdateMs > 250L) {
                            lastStreamUpdateMs = now
                            val currentOutput = outputSb.toString()
                            effectiveCallbacks.updateMessages(convId) { list ->
                                list.map { msg ->
                                    if (msg.id == assistantMsgId) msg.copy(content = currentOutput) else msg
                                }
                            }
                        }
                    }
                }
            }

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
        cliId: String,
        securityPreset: String,
        validWsDir: File?,
        sessionToResume: String?,
        currentTurn: Int,
        prompt: String,
        stagedAttachments: List<Triple<String, String, String>>,
        voiceInstruction: String? = null
    ): List<String> {
        val args = mutableListOf<String>()
        val currentSec = securityPreset.lowercase()
        args.add("--dangerously-skip-permissions")
        if (currentSec == "request review" || currentSec == "request_review") {
            args.add("--mode")
            args.add("plan")
        }
        args.add("--output-format")
        args.add("stream-json")
        if (cliId.isNotBlank()) {
            args.add("--model")
            args.add(cliId)
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
        val currentPrompt = if (currentTurn == 1 && stagedAttachments.isNotEmpty()) {
            buildString {
                if (voicePrefix.isNotEmpty()) append(voicePrefix)
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
                append("MANDATORY: You must inspect the attached file(s) with the `view_file` tool before answering.\n\n")
                append("### USER REQUEST\n")
                append(if (prompt.isNotBlank()) prompt else "Please inspect the attached file(s) and assist the user.")
            }
        } else if (currentTurn == 1) {
            if (voicePrefix.isNotEmpty()) "$voicePrefix$prompt" else prompt
        } else {
            "Continue and complete the task based on previous tool results. Provide thorough analysis or confirmation once finished."
        }
        args.add(currentPrompt)
        return args
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

    suspend fun computeTurnFileChanges(context: Context, workspacePath: String?): TurnFileChanges? = withContext(Dispatchers.IO) {
        val root = workspacePath?.let { File(it) } ?: return@withContext null
        if (!root.exists() || !root.isDirectory || !File(root, ".git").exists()) return@withContext null

        val fileList = mutableListOf<ChangedFileDetail>()
        var totalAdd = 0
        var totalDel = 0

        try {
            val statusMap = mutableMapOf<String, String>()
            EngineExecutor.executeCommand(context, "git status --porcelain 2>/dev/null", workingDir = root) { line, _ ->
                val trimmed = line.trim()
                if (trimmed.length >= 3) {
                    val st = trimmed.substring(0, 2).trim()
                    val path = trimmed.substring(2).trim().trim('"', '\'')
                    val statusDesc = when {
                        st.contains("D") -> "D"
                        st.contains("A") || st.contains("?") -> "A"
                        else -> "M"
                    }
                    statusMap[path] = statusDesc
                }
            }

            val numstats = mutableListOf<Triple<String, Int, Int>>()
            EngineExecutor.executeCommand(context, "git diff --numstat 2>/dev/null", workingDir = root) { line, _ ->
                val parts = line.trim().split(Regex("""\s+"""))
                if (parts.size >= 3) {
                    val add = parts[0].toIntOrNull() ?: 0
                    val del = parts[1].toIntOrNull() ?: 0
                    val path = parts[2].trim().trim('"', '\'')
                    numstats.add(Triple(path, add, del))
                }
            }

            val diffsMap = mutableMapOf<String, String>()
            if (numstats.isNotEmpty() && numstats.size <= 30) {
                val combinedDiffSb = StringBuilder()
                EngineExecutor.executeCommand(context, "git diff -U3 2>/dev/null | head -n 1200", workingDir = root) { line, _ ->
                    combinedDiffSb.append(line).append("\n")
                }
                val fullDiff = combinedDiffSb.toString()
                if (fullDiff.isNotBlank()) {
                    val fileBlocks = fullDiff.split(Regex("""(?=diff --git)"""))
                    for (block in fileBlocks) {
                        val headerMatch = Regex("""diff --git a/(.+?) b/""").find(block)
                        if (headerMatch != null) {
                            val p = headerMatch.groupValues[1].trim()
                            diffsMap[p] = block.take(5000)
                        }
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
