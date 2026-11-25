package id.vern.antigravity.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import id.vern.antigravity.data.MessageStatus
import id.vern.antigravity.data.RunningSubagent
import id.vern.antigravity.data.ThemeMode
import id.vern.antigravity.voice.LiveVoiceController
import id.vern.antigravity.voice.VoiceLanguage
import id.vern.antigravity.ui.components.AgentDetailsBottomSheet
import id.vern.antigravity.ui.components.DialogBlurEffect
import id.vern.antigravity.ui.components.InstallationProgressDialog
import id.vern.antigravity.ui.components.ProjectChatsDropdownMenu
import id.vern.antigravity.ui.components.RightSystemSetupPanel
import id.vern.antigravity.ui.components.SubagentChatBottomSheet
import id.vern.antigravity.viewmodel.ConversationSummary
import java.io.File
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.text.style.TextOverflow
import id.vern.antigravity.data.MessageRole
import id.vern.antigravity.data.ProjectSortOrder
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.vern.antigravity.R
import id.vern.antigravity.ui.components.ChatBubble
import id.vern.antigravity.ui.components.ChatInputBar
import id.vern.antigravity.ui.components.GoogleSignInDialog
import id.vern.antigravity.ui.components.ModelSelectorDialog
import id.vern.antigravity.ui.theme.AntigravityBlue
import id.vern.antigravity.ui.theme.AntigravityGreen
import id.vern.antigravity.ui.theme.AntigravityRed
import id.vern.antigravity.viewmodel.AntigravityViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    viewModel: AntigravityViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToFiles: () -> Unit = {},
    onNavigateToEditor: () -> Unit = {},
    onNavigateToTerminal: () -> Unit = {},
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var showProjectChatsDropdown by remember { mutableStateOf(false) }
    var showRightSetupPanel by remember { mutableStateOf(false) }

    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val activeConversationId by viewModel.activeConversationId.collectAsStateWithLifecycle()
    val executingConversationIds by viewModel.executingConversationIds.collectAsStateWithLifecycle()
    val conversationTitle by viewModel.conversationTitle.collectAsStateWithLifecycle()
    val conversationHistory by viewModel.conversationHistory.collectAsStateWithLifecycle()
    val attachedFiles by viewModel.attachedFiles.collectAsStateWithLifecycle()
    val isExecuting by viewModel.isExecuting.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val thinkingBudget by viewModel.thinkingBudget.collectAsStateWithLifecycle()
    val verboseThinking by viewModel.verboseThinking.collectAsStateWithLifecycle()
    val userEmail by viewModel.userEmail.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val userPicture by viewModel.userPicture.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val modelLimitReachedInfo by viewModel.modelLimitReachedInfo.collectAsStateWithLifecycle()
    val isAuthenticating by viewModel.isAuthenticating.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    val showAuthDialog by viewModel.showGoogleSignInDialog.collectAsStateWithLifecycle()
    val showModelSelector by viewModel.showModelSelector.collectAsStateWithLifecycle()
    val isPinned by viewModel.isConversationPinned.collectAsStateWithLifecycle()
    val userQuotaSummary by viewModel.userQuotaSummary.collectAsStateWithLifecycle()
    val codeAssistTier by viewModel.codeAssistTier.collectAsStateWithLifecycle()
    val cliModels by viewModel.cliModels.collectAsStateWithLifecycle()
    val quotaErrorMessage by viewModel.quotaErrorMessage.collectAsStateWithLifecycle()
    val queuedMessages by viewModel.queuedMessages.collectAsStateWithLifecycle()
    val runningTasks by viewModel.runningTasks.collectAsStateWithLifecycle()
    val runningSubagents by viewModel.runningSubagents.collectAsStateWithLifecycle()
    val slashActions by viewModel.slashActions.collectAsStateWithLifecycle()
    val appearance by viewModel.appearanceConfig.collectAsStateWithLifecycle()
    val themePreset by viewModel.themePreset.collectAsStateWithLifecycle()
    val pendingInputText by viewModel.pendingInputText.collectAsStateWithLifecycle()
    val securityPreset by viewModel.securityPreset.collectAsStateWithLifecycle()
    val voiceLanguage by viewModel.voiceLanguage.collectAsStateWithLifecycle()
    val voiceReplyInLanguage by viewModel.voiceReplyInLanguage.collectAsStateWithLifecycle()
    val voiceSpeechRate by viewModel.voiceSpeechRate.collectAsStateWithLifecycle()
    val voiceOnlyMode by viewModel.voiceOnlyMode.collectAsStateWithLifecycle()
    val latestVoiceTurnResult by viewModel.latestVoiceTurnResult.collectAsStateWithLifecycle()

    var showMenuDropdown by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDetailsBottomSheet by remember { mutableStateOf(false) }
    var detailsBottomSheetTab by remember { mutableIntStateOf(0) }
    var fileToOpenInEditor by remember { mutableStateOf<String?>(null) }
    var selectedSubagent by remember { mutableStateOf<RunningSubagent?>(null) }
    var showSettingsBottomSheet by remember { mutableStateOf(false) }
    var showSkillsDialog by remember { mutableStateOf(false) }
    var showPluginsDialog by remember { mutableStateOf(false) }
    var settingsInitialPage by remember { mutableStateOf(SettingsPage.MAIN) }
    val listState = rememberLazyListState()
    val terminalReq by viewModel.openTerminalRequest.collectAsStateWithLifecycle()

    var awaitingVoiceConvId by remember { mutableStateOf<String?>(null) }
    var awaitingVoiceTurnBaselineId by remember { mutableStateOf<String?>(null) }
    var awaitingVoiceResponse by remember { mutableStateOf(false) }
    var awaitingVoiceTurnId by remember { mutableStateOf<String?>(null) }

    val liveVoiceController = remember(context) {
        LiveVoiceController(context) { spokenText ->
            val voiceInstr = viewModel.voiceLanguage.value.buildVoicePromptInstruction(
                viewModel.voiceReplyInLanguage.value
            )
            if (viewModel.voiceOnlyMode.value) {
                awaitingVoiceConvId = null
                awaitingVoiceTurnBaselineId = null
                awaitingVoiceResponse = false
                awaitingVoiceTurnId = viewModel.sendVoiceTurn(context, spokenText, voiceInstruction = voiceInstr)
            } else {
                val targetConvId = viewModel.activeConversationId.value
                awaitingVoiceTurnId = null
                awaitingVoiceConvId = targetConvId
                awaitingVoiceTurnBaselineId = if (targetConvId != null) {
                    viewModel.getMessagesForConversation(targetConvId).lastOrNull()?.id
                } else {
                    viewModel.chatMessages.value.lastOrNull()?.id
                }
                awaitingVoiceResponse = true
                viewModel.sendMessage(
                    context,
                    spokenText,
                    voiceInstruction = voiceInstr,
                    suppressCompletionNotification = true
                )
            }
        }
    }
    val liveVoiceState by liveVoiceController.state.collectAsStateWithLifecycle()

    LaunchedEffect(voiceLanguage, voiceReplyInLanguage, voiceSpeechRate) {
        liveVoiceController.configure(
            language = voiceLanguage,
            replyInLanguage = voiceReplyInLanguage,
            speechRate = voiceSpeechRate
        )
    }

    DisposableEffect(liveVoiceController) {
        onDispose { liveVoiceController.destroy() }
    }

    DisposableEffect(lifecycleOwner, liveVoiceController) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                liveVoiceController.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            liveVoiceController.start()
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.chat_live_voice_permission_denied),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val toggleLiveVoice: () -> Unit = {
        if (liveVoiceState.active) {
            awaitingVoiceResponse = false
            awaitingVoiceConvId = null
            awaitingVoiceTurnId = null
            viewModel.cancelVoiceTurn()
            liveVoiceController.stop()
        } else {
            val hasMicPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (hasMicPerm) {
                liveVoiceController.start()
            } else {
                recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    LaunchedEffect(
        messages,
        isExecuting,
        activeConversationId,
        executingConversationIds,
        liveVoiceState.active,
        awaitingVoiceResponse,
        awaitingVoiceConvId
    ) {
        if (!liveVoiceState.active) {
            awaitingVoiceResponse = false
            awaitingVoiceConvId = null
            return@LaunchedEffect
        }
        if (!awaitingVoiceResponse) return@LaunchedEffect
        val targetConvId = awaitingVoiceConvId
        val targetMessages = if (targetConvId != null) {
            viewModel.getMessagesForConversation(targetConvId).ifEmpty {
                if (activeConversationId == targetConvId) messages else emptyList()
            }
        } else {
            messages
        }
        val targetExecuting = if (targetConvId != null) {
            targetConvId in executingConversationIds
        } else {
            isExecuting
        }
        val latestAssistant = targetMessages.lastOrNull { it.role == MessageRole.ASSISTANT }
        if (latestAssistant != null &&
            latestAssistant.id != awaitingVoiceTurnBaselineId &&
            latestAssistant.status != MessageStatus.SENDING &&
            !targetExecuting
        ) {
            awaitingVoiceResponse = false
            awaitingVoiceConvId = null
            liveVoiceController.onAssistantCompleted(
                markdown = latestAssistant.content,
                isSuccess = latestAssistant.status == MessageStatus.SENT
            )
        }
    }

    LaunchedEffect(latestVoiceTurnResult, liveVoiceState.active, awaitingVoiceTurnId) {
        if (!liveVoiceState.active) {
            awaitingVoiceTurnId = null
            return@LaunchedEffect
        }
        val expectedId = awaitingVoiceTurnId ?: return@LaunchedEffect
        val result = latestVoiceTurnResult ?: return@LaunchedEffect
        if (result.turnId == expectedId) {
            awaitingVoiceTurnId = null
            liveVoiceController.onAssistantCompleted(
                markdown = result.responseText,
                isSuccess = result.isSuccess
            )
        }
    }

    LaunchedEffect(terminalReq) {
        if (terminalReq != null) {
            showSettingsBottomSheet = false
            showSkillsDialog = false
            showPluginsDialog = false
            detailsBottomSheetTab = 2
            showDetailsBottomSheet = true
            viewModel.consumeOpenTerminalRequest()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshCliSlashCommands(context)
        viewModel.checkForCoreEngineUpdate(context)
        viewModel.checkForOtaUpdates(context)
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        val currentCount = attachedFiles.size
        val remaining = (5 - currentCount).coerceAtLeast(0)
        if (remaining <= 0) {
            Toast.makeText(context, context.getString(R.string.chat_attachment_limit_reached), Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        uris.take(remaining).forEach { uri ->
            viewModel.addAttachment(context, uri)
        }
        if (uris.size > remaining) {
            Toast.makeText(context, context.getString(R.string.chat_attachment_limit_reached), Toast.LENGTH_SHORT).show()
        }
    }

    val projectList by viewModel.projectList.collectAsStateWithLifecycle()
    val projectSortOrder by viewModel.projectSortOrder.collectAsStateWithLifecycle()
    val currentWorkspace by viewModel.workspacePath.collectAsStateWithLifecycle()

    var showSortMenu by remember { mutableStateOf(false) }
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }
    var chosenDestinationFolder by remember { mutableStateOf<File?>(null) }

    var isPinnedExpanded by remember { mutableStateOf(true) }
    val pinnedArrowRotation by animateFloatAsState(
        targetValue = if (isPinnedExpanded) 0f else -90f,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "pinnedChevronRotation"
    )

    var isProjectsExpanded by remember { mutableStateOf(true) }
    val projectsArrowRotation by animateFloatAsState(
        targetValue = if (isProjectsExpanded) 0f else -90f,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "projectsChevronRotation"
    )

    var isConversationsExpanded by remember { mutableStateOf(true) }
    val convosArrowRotation by animateFloatAsState(
        targetValue = if (isConversationsExpanded) 0f else -90f,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "convosChevronRotation"
    )
    var showPinnedMenu by remember { mutableStateOf(false) }

    var showDeleteConversationDialog by remember { mutableStateOf(false) }
    var showDeleteSpecificConversationDialog by remember { mutableStateOf(false) }
    var conversationToDelete by remember { mutableStateOf<ConversationSummary?>(null) }
    var showDeleteProjectDialog by remember { mutableStateOf(false) }
    var projectToDelete by remember { mutableStateOf<File?>(null) }
    var showCoreUpdateConfirmDialog by remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val folder = viewModel.addProjectFromTreeUri(context, uri)
            if (folder != null) {
                Toast.makeText(context, context.getString(R.string.toast_project_added, folder.name), Toast.LENGTH_SHORT).show()
                showNewProjectDialog = false
            } else {
                Toast.makeText(context, context.getString(R.string.toast_project_add_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val createProjectFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val parentDir = viewModel.resolveTreeUriToFolder(context, uri)
            if (parentDir != null && parentDir.exists()) {
                val name = newProjectName.trim()
                if (name.isNotEmpty()) {
                    val created = viewModel.createProjectInFolder(context, name, parentDir)
                    if (created != null) {
                        Toast.makeText(context, context.getString(R.string.toast_project_created, created.name), Toast.LENGTH_SHORT).show()
                        showNewProjectDialog = false
                        newProjectName = ""
                        chosenDestinationFolder = null
                    } else {
                        Toast.makeText(context, context.getString(R.string.toast_project_create_failed), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    chosenDestinationFolder = parentDir
                }
            } else {
                Toast.makeText(context, context.getString(R.string.toast_project_create_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showNewProjectDialog) {
        AlertDialog(
            onDismissRequest = {
                showNewProjectDialog = false
                newProjectName = ""
                chosenDestinationFolder = null
            },
            shape = RoundedCornerShape(16.dp),
            title = {
                DialogBlurEffect()
                Text(
                    text = stringResource(R.string.dialog_add_project_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.wrapContentHeight().animateContentSize()) {
                    Text(
                        text = stringResource(R.string.dialog_add_project_choose_existing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            folderPickerLauncher.launch(null)
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.dialog_add_project_choose_btn), fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = stringResource(R.string.dialog_add_project_or_create),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = newProjectName,
                        onValueChange = { newProjectName = it },
                        label = { Text(stringResource(R.string.dialog_add_project_name_label)) },
                        placeholder = { Text(stringResource(R.string.dialog_add_project_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            createProjectFolderPickerLauncher.launch(null)
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (chosenDestinationFolder != null) {
                                stringResource(R.string.dialog_create_project_dest_label, chosenDestinationFolder!!.name)
                            } else {
                                stringResource(R.string.dialog_create_project_pick_folder_btn)
                            },
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    shape = RoundedCornerShape(8.dp),
                    onClick = {
                        val name = newProjectName.trim()
                        if (name.isNotEmpty()) {
                            val dest = chosenDestinationFolder
                            if (dest != null) {
                                val created = viewModel.createProjectInFolder(context, name, dest)
                                if (created != null) {
                                    Toast.makeText(context, context.getString(R.string.toast_project_created, created.name), Toast.LENGTH_SHORT).show()
                                    showNewProjectDialog = false
                                    newProjectName = ""
                                    chosenDestinationFolder = null
                                } else {
                                    Toast.makeText(context, context.getString(R.string.toast_project_create_failed), Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                createProjectFolderPickerLauncher.launch(null)
                            }
                        }
                    },
                    enabled = newProjectName.isNotBlank()
                ) {
                    Text(
                        if (chosenDestinationFolder != null) stringResource(R.string.dialog_add_project_create_btn)
                        else stringResource(R.string.dialog_create_project_pick_and_create_btn)
                    )
                }
            }
        )
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            runCatching {
                listState.scrollToItem(messages.size)
            }
        }
    }

    val lastMessage = messages.lastOrNull()
    val lastMessageContentLength = lastMessage?.content?.length ?: 0
    val lastMessageThinkingLength = lastMessage?.thinking?.length ?: 0
    val lastMessageStepsCount = lastMessage?.steps?.size ?: 0

    LaunchedEffect(lastMessageContentLength, lastMessageThinkingLength, lastMessageStepsCount, isExecuting) {
        if (isExecuting && messages.isNotEmpty() && !listState.isScrollInProgress) {
            kotlinx.coroutines.delay(200)
            runCatching {
                listState.scrollToItem(messages.size)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                val activeProject = projectList.firstOrNull { it.absolutePath == currentWorkspace }
                val topBarTitleText = activeProject?.name
                    ?: if (conversationTitle.isNotBlank() && conversationTitle != "New Conversation" && conversationTitle != "Antigravity -Android") {
                        conversationTitle
                    } else {
                        stringResource(R.string.chat_new_conversation)
                    }

                TopAppBar(
                    title = {
                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { showProjectChatsDropdown = true }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = topBarTitleText,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 180.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            ProjectChatsDropdownMenu(
                                expanded = showProjectChatsDropdown,
                                onDismiss = { showProjectChatsDropdown = false },
                                projectList = projectList,
                                currentWorkspace = currentWorkspace,
                                activeConversationId = activeConversationId,
                                conversationHistory = conversationHistory,
                                executingConversationIds = executingConversationIds,
                                formatRelativeTime = ::formatRelativeTime,
                                onNewConversation = { projectPath ->
                                    if (projectPath != null) {
                                        viewModel.startNewConversationInProject(context, projectPath)
                                    } else {
                                        viewModel.startNewConversation(context)
                                    }
                                    showProjectChatsDropdown = false
                                },
                                onSelectProject = { project ->
                                    viewModel.selectProject(context, project)
                                    showProjectChatsDropdown = false
                                },
                                onSelectConversation = { convo ->
                                    viewModel.loadConversation(context, convo)
                                    showProjectChatsDropdown = false
                                },
                                onTogglePinConversation = { id ->
                                    viewModel.togglePinConversation(id, context = context)
                                },
                                onRequestDeleteConversation = { convo ->
                                    conversationToDelete = convo
                                    showDeleteSpecificConversationDialog = true
                                    showProjectChatsDropdown = false
                                },
                                onOpenProjectIntegrations = { project ->
                                    viewModel.selectProject(context, project)
                                    settingsInitialPage = SettingsPage.GITHUB
                                    showSettingsBottomSheet = true
                                    showProjectChatsDropdown = false
                                },
                                onRequestDeleteProject = { project ->
                                    projectToDelete = project
                                    showDeleteProjectDialog = true
                                    showProjectChatsDropdown = false
                                },
                                onRequestNewProject = {
                                    showNewProjectDialog = true
                                    showProjectChatsDropdown = false
                                },
                                onSelectSortOrder = { order ->
                                    viewModel.setProjectSortOrder(context, order)
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    navigationIcon = {
                        IconButton(onClick = {
                            if (onNavigateBack != null) {
                                onNavigateBack()
                            } else {
                                showProjectChatsDropdown = true
                            }
                        }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_launcher_logo),
                                contentDescription = stringResource(R.string.app_name),
                                tint = Color.Unspecified,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    actions = {
                        val coreState by viewModel.coreUpdateState.collectAsStateWithLifecycle()
                        AnimatedVisibility(
                            visible = coreState.isUpdateAvailable,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut()
                        ) {
                            Button(
                                onClick = { showCoreUpdateConfirmDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .height(28.dp)
                                    .padding(end = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.topbar_core_update_btn),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Box {
                            IconButton(onClick = { showMenuDropdown = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.common_options),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = showMenuDropdown,
                                onDismissRequest = { showMenuDropdown = false },
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_rename_conversation)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    onClick = {
                                        showMenuDropdown = false
                                        showRenameDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(if (isPinned) R.string.menu_unpin_conversation else R.string.menu_pin_conversation)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.PushPin,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    onClick = {
                                        showMenuDropdown = false
                                        val pinned = viewModel.togglePinConversation(context = context)
                                        Toast.makeText(
                                            context,
                                            context.getString(if (pinned) R.string.toast_conversation_pinned else R.string.toast_conversation_unpinned),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_archive_conversation)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Archive,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    onClick = {
                                        showMenuDropdown = false
                                        viewModel.archiveConversation(context = context)
                                        Toast.makeText(context, context.getString(R.string.toast_conversation_archived), Toast.LENGTH_SHORT).show()
                                    }
                                )

                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_delete_conversation), color = AntigravityRed) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = AntigravityRed,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    onClick = {
                                        showMenuDropdown = false
                                        showDeleteConversationDialog = true
                                    }
                                )

                                val currentProject = projectList.firstOrNull { it.absolutePath == currentWorkspace }
                                if (currentProject != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_delete_project, currentProject.name), color = AntigravityRed) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.DeleteForever,
                                                contentDescription = null,
                                                tint = AntigravityRed,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        },
                                        onClick = {
                                            showMenuDropdown = false
                                            projectToDelete = currentProject
                                            showDeleteProjectDialog = true
                                        }
                                    )
                                }
                            }
                        }

                        val isLightMode = appearance.themeMode == ThemeMode.LIGHT ||
                            (appearance.themeMode == ThemeMode.SYSTEM && !isSystemInDarkTheme())
                        IconButton(onClick = {
                            viewModel.setThemeMode(context, if (isLightMode) ThemeMode.DARK else ThemeMode.LIGHT)
                        }) {
                            Icon(
                                imageVector = if (isLightMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(onClick = {
                            detailsBottomSheetTab = 0
                            showDetailsBottomSheet = true
                        }) {
                            SidePanelIcon(
                                tint = if (showDetailsBottomSheet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = { showRightSetupPanel = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.common_settings),
                                tint = if (showRightSetupPanel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                )
            },
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            modifier = modifier.fillMaxSize()
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
                    .imePadding()
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AnimatedContent(
                        targetState = messages.isEmpty(),
                        transitionSpec = {
                            fadeIn(animationSpec = tween(250)) togetherWith fadeOut(animationSpec = tween(200))
                        },
                        label = "ChatMainContentTransition",
                        modifier = Modifier.fillMaxSize()
                    ) { isListEmpty ->
                        if (isListEmpty) {

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_launcher_logo),
                                        contentDescription = stringResource(id = R.string.app_name),
                                        modifier = Modifier.size(64.dp)
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Text(
                                        text = stringResource(R.string.chat_empty_headline),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = stringResource(R.string.chat_empty_subheadline),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 24.dp)
                                    )

                                    Spacer(modifier = Modifier.height(24.dp))

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .clickable { showSkillsDialog = true }
                                        ) {
                                            Text(
                                                text = stringResource(R.string.chat_empty_install_skills),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .clickable { showPluginsDialog = true }
                                        ) {
                                            Text(
                                                text = stringResource(R.string.chat_empty_install_plugins),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 4.dp)
                            ) {
                                val executingUserMsgIndex = if (isExecuting) messages.indexOfLast { it.role == MessageRole.USER } else -1

                                if (executingUserMsgIndex != -1 && isExecuting) {
                                    items(messages.subList(0, executingUserMsgIndex), key = { it.id }) { message ->
                                        ChatBubble(
                                            message = message,
                                            verboseThinking = verboseThinking,
                                            onOpenFile = { path ->
                                                fileToOpenInEditor = path
                                                detailsBottomSheetTab = 3
                                                showDetailsBottomSheet = true
                                            },
                                            onOpenTerminal = { cmd ->
                                                viewModel.requestOpenTerminal(cmd)
                                            },
                                            onUndoPrompt = { userMsg ->
                                                viewModel.undoPrompt(context, userMsg)
                                            },
                                            onProceedPlan = { msg ->
                                                viewModel.proceedWithPlan(context, msg.id)
                                            },
                                            isExecuting = isExecuting
                                        )
                                    }

                                    stickyHeader(key = messages[executingUserMsgIndex].id) {
                                        val userMsg = messages[executingUserMsgIndex]
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(MaterialTheme.colorScheme.background)
                                        ) {
                                            ChatBubble(
                                                message = userMsg,
                                                verboseThinking = verboseThinking,
                                                onOpenFile = { path ->
                                                    fileToOpenInEditor = path
                                                    detailsBottomSheetTab = 3
                                                    showDetailsBottomSheet = true
                                                },
                                                onOpenTerminal = { cmd ->
                                                    viewModel.requestOpenTerminal(cmd)
                                                },
                                                onUndoPrompt = { userMsgToUndo ->
                                                    viewModel.undoPrompt(context, userMsgToUndo)
                                                },
                                                onProceedPlan = { msg ->
                                                    viewModel.proceedWithPlan(context, msg.id)
                                                },
                                                isPinnedExecuting = true,
                                                isExecuting = isExecuting
                                            )
                                        }
                                    }

                                    items(messages.subList(executingUserMsgIndex + 1, messages.size), key = { it.id }) { message ->
                                        ChatBubble(
                                            message = message,
                                            verboseThinking = verboseThinking,
                                            onOpenFile = { path ->
                                                fileToOpenInEditor = path
                                                detailsBottomSheetTab = 3
                                                showDetailsBottomSheet = true
                                            },
                                            onOpenTerminal = { cmd ->
                                                viewModel.requestOpenTerminal(cmd)
                                            },
                                            onUndoPrompt = { userMsg ->
                                                viewModel.undoPrompt(context, userMsg)
                                            },
                                            onProceedPlan = { msg ->
                                                viewModel.proceedWithPlan(context, msg.id)
                                            },
                                            isExecuting = isExecuting
                                        )
                                    }
                                } else {
                                    items(messages, key = { it.id }) { message ->
                                        ChatBubble(
                                            message = message,
                                            verboseThinking = verboseThinking,
                                            onOpenFile = { path ->
                                                fileToOpenInEditor = path
                                                detailsBottomSheetTab = 3
                                                showDetailsBottomSheet = true
                                            },
                                            onOpenTerminal = { cmd ->
                                                viewModel.requestOpenTerminal(cmd)
                                            },
                                            onUndoPrompt = { userMsg ->
                                                viewModel.undoPrompt(context, userMsg)
                                            },
                                            onProceedPlan = { msg ->
                                                viewModel.proceedWithPlan(context, msg.id)
                                            },
                                            isExecuting = isExecuting
                                        )
                                    }
                                }
                                item(key = "chat_bottom_anchor") {
                                    Spacer(modifier = Modifier.height(1.dp))
                                }
                            }
                        }
                    }
                }

                ChatInputBar(
                    isExecuting = isExecuting,
                    selectedModel = selectedModel,
                    thinkingBudget = thinkingBudget,
                    attachedFiles = attachedFiles,
                    queuedMessages = queuedMessages,
                    runningTasks = runningTasks,
                    runningSubagents = runningSubagents,
                    externalInputText = pendingInputText,
                    onExternalInputTextConsumed = { viewModel.clearPendingInputText() },
                    onSendMessage = { text ->
                        val voiceInstr = if (liveVoiceState.active) {
                            val targetConvId = viewModel.activeConversationId.value
                            awaitingVoiceConvId = targetConvId
                            awaitingVoiceTurnBaselineId = if (targetConvId != null) {
                                viewModel.getMessagesForConversation(targetConvId).lastOrNull()?.id
                            } else {
                                messages.lastOrNull()?.id
                            }
                            awaitingVoiceResponse = true
                            liveVoiceController.onTurnStarted()
                            voiceLanguage.buildVoicePromptInstruction(voiceReplyInLanguage)
                        } else null
                        viewModel.sendMessage(
                            context,
                            text,
                            voiceInstruction = voiceInstr,
                            suppressCompletionNotification = liveVoiceState.active
                        )
                        scope.launch {
                            kotlinx.coroutines.delay(60)
                            runCatching {
                                listState.scrollToItem(messages.size)
                            }
                        }
                    },
                    onModelSelectorClick = { viewModel.showModelSelector() },
                    onAttachFileClick = {
                        if (attachedFiles.size >= 5) {
                            Toast.makeText(context, context.getString(R.string.chat_attachment_limit_reached), Toast.LENGTH_SHORT).show()
                        } else {
                            filePicker.launch("*/*")
                        }
                    },
                    onRemoveAttachment = { file -> viewModel.removeAttachment(file) },
                    onStopExecution = { viewModel.stopAiExecution() },
                    onProcessQueuedMessageNow = { id -> viewModel.processQueuedMessageNow(context, id) },
                    onEditQueuedMessage = { id -> viewModel.editQueuedMessage(id) },
                    onRemoveQueuedMessage = { id -> viewModel.removeQueuedMessage(id) },
                    onStopTask = { taskId -> viewModel.stopRunningTask(taskId) },
                    onTaskClick = { task -> viewModel.requestOpenTerminal(task.command) },
                    onSubagentClick = { subagent -> selectedSubagent = subagent },
                    onStopSubagent = { subagentId -> viewModel.stopSubagent(subagentId) },
                    onGetWorkspaceFiles = { query -> viewModel.getWorkspaceFiles(query) },
                    slashActions = slashActions,
                    onSlashMenuOpened = { viewModel.refreshCliSlashCommands(context) },
                    securityPreset = securityPreset,
                    onSecurityPresetChange = { preset -> viewModel.setSecurityPreset(context, preset) },
                    liveVoiceState = liveVoiceState,
                    onToggleLiveVoice = toggleLiveVoice,
                    onToggleLiveVoiceMute = { liveVoiceController.toggleMute() },
                    onSkipLiveVoiceSpeaking = { liveVoiceController.skipSpeaking() },
                    onRetryLiveVoice = { liveVoiceController.retryListening() },
                    voiceLanguage = voiceLanguage,
                    onVoiceLanguageChange = { lang -> viewModel.setVoiceLanguage(context, lang) },
                    onToggleVoiceReplyInLanguage = { enabled -> viewModel.setVoiceReplyInLanguage(context, enabled) },
                    onVoiceSpeechRateChange = { rate -> viewModel.setVoiceSpeechRate(context, rate) },
                    voiceOnlyMode = voiceOnlyMode,
                    onToggleVoiceOnlyMode = { enabled -> viewModel.setVoiceOnlyMode(context, enabled) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            bottom = if (WindowInsets.ime.asPaddingValues().calculateBottomPadding() > 0.dp) {
                                8.dp
                            } else {
                                maxOf(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 10.dp, 16.dp)
                            }
                        )
                )
            }
        }

        RightSystemSetupPanel(
            visible = showRightSetupPanel,
            userEmail = userEmail,
            userName = userName,
            userPicture = userPicture,
            hasProjectOpen = currentWorkspace != null,
            onDismiss = { showRightSetupPanel = false },
            onOpenAccountOrSignIn = {
                showRightSetupPanel = false
                if (userEmail != null) {
                    settingsInitialPage = SettingsPage.MAIN
                    showSettingsBottomSheet = true
                } else {
                    viewModel.startGoogleSignIn(context)
                }
            },
            onOpenWorkspaceTab = { tab ->
                showRightSetupPanel = false
                detailsBottomSheetTab = tab
                showDetailsBottomSheet = true
            },
            onOpenSettingsPage = { page ->
                showRightSetupPanel = false
                settingsInitialPage = page
                showSettingsBottomSheet = true
            }
        )
    }

    if (showModelSelector) {
        LaunchedEffect(Unit) {
            viewModel.refreshUserQuota(context)
            viewModel.refreshCliModels(context)
        }
        ModelSelectorDialog(
            selectedModel = selectedModel,
            selectedThinking = thinkingBudget,
            userQuotaSummary = userQuotaSummary,
            codeAssistTier = codeAssistTier,
            cliModels = cliModels,
            modelsErrorMessage = quotaErrorMessage,
            activeUserEmail = userEmail,
            accounts = accounts,
            onSwitchAccount = { acc ->
                viewModel.switchAccount(context, acc.id)
                Toast.makeText(context, context.getString(R.string.account_switched_toast, acc.email), Toast.LENGTH_SHORT).show()
            },
            onAddGoogleAccount = {
                viewModel.dismissModelSelector()
                viewModel.startGoogleSignIn(context)
            },
            onModelSelected = { model, thinking, cliId ->
                viewModel.setModel(context, model, thinking, cliId)
            },
            onDismissRequest = { viewModel.dismissModelSelector() }
        )
    }

    if (modelLimitReachedInfo != null) {
        ModelLimitSwitchAccountDialog(
            limitInfo = modelLimitReachedInfo!!,
            activeUserEmail = userEmail,
            accounts = accounts,
            onSwitchAndRetry = { acc ->
                Toast.makeText(context, context.getString(R.string.account_switched_toast, acc.email), Toast.LENGTH_SHORT).show()
                viewModel.switchGoogleAccountAndRetry(context, acc.id, retryFailedPrompt = true)
            },
            onAddGoogleAccount = {
                viewModel.dismissModelLimitPrompt()
                viewModel.startGoogleSignIn(context)
            },
            onOpenModelSelector = { viewModel.showModelSelector() },
            onDismiss = { viewModel.dismissModelLimitPrompt() }
        )
    }

    if (showAuthDialog) {
        GoogleSignInDialog(
            userEmail = userEmail,
            userName = userName,
            userPicture = userPicture,
            isAuthenticating = isAuthenticating,
            authError = authError,
            onDismissRequest = { viewModel.dismissGoogleSignInDialog() },
            onLaunchSignIn = { viewModel.startGoogleSignIn(context) }
        )
    }

    if (showRenameDialog) {
        RenameConversationDialog(
            currentTitle = conversationTitle,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newTitleText ->
                if (newTitleText.isNotBlank()) {
                    viewModel.renameConversation(newTitleText, context = context)
                    Toast.makeText(context, context.getString(R.string.toast_conversation_renamed), Toast.LENGTH_SHORT).show()
                }
                showRenameDialog = false
            }
        )
    }

    if (showDeleteConversationDialog) {
        ConfirmActionDialog(
            title = stringResource(R.string.dialog_delete_convo_title),
            message = stringResource(R.string.dialog_delete_convo_msg),
            confirmText = stringResource(R.string.dialog_delete_btn),
            onDismiss = { showDeleteConversationDialog = false },
            onConfirm = {
                viewModel.deleteCurrentConversation(context)
                showDeleteConversationDialog = false
                Toast.makeText(context, context.getString(R.string.toast_conversation_deleted), Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showDeleteSpecificConversationDialog && conversationToDelete != null) {
        ConfirmActionDialog(
            title = stringResource(R.string.dialog_delete_convo_title),
            message = stringResource(R.string.dialog_delete_specific_convo_msg, conversationToDelete?.title ?: ""),
            confirmText = stringResource(R.string.dialog_delete_btn),
            onDismiss = {
                showDeleteSpecificConversationDialog = false
                conversationToDelete = null
            },
            onConfirm = {
                conversationToDelete?.let {
                    viewModel.deleteConversation(context, it.id)
                }
                showDeleteSpecificConversationDialog = false
                conversationToDelete = null
                Toast.makeText(context, context.getString(R.string.toast_conversation_deleted), Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showDeleteProjectDialog && projectToDelete != null) {
        ConfirmActionDialog(
            title = stringResource(R.string.dialog_delete_project_title),
            message = stringResource(R.string.dialog_delete_project_msg, projectToDelete?.name ?: ""),
            confirmText = stringResource(R.string.dialog_delete_btn),
            onDismiss = {
                showDeleteProjectDialog = false
                projectToDelete = null
            },
            onConfirm = {
                projectToDelete?.let {
                    viewModel.removeProject(context, it.absolutePath)
                }
                showDeleteProjectDialog = false
                projectToDelete = null
                Toast.makeText(context, context.getString(R.string.toast_project_removed), Toast.LENGTH_SHORT).show()
            }
        )
    }

    val currentCoreState by viewModel.coreUpdateState.collectAsStateWithLifecycle()
    if (showCoreUpdateConfirmDialog && currentCoreState.isUpdateAvailable) {
        val latestVer = currentCoreState.latestVersion ?: ""
        val currentVer = currentCoreState.currentVersion
        ConfirmActionDialog(
            title = stringResource(R.string.dialog_core_update_title),
            message = stringResource(R.string.dialog_core_update_msg, latestVer, currentVer),
            confirmText = stringResource(R.string.core_update_btn_update),
            onDismiss = { showCoreUpdateConfirmDialog = false },
            onConfirm = {
                showCoreUpdateConfirmDialog = false
                viewModel.applyCoreEngineUpdate(context)
            }
        )
    }

    if (showDetailsBottomSheet) {
        AgentDetailsBottomSheet(
            viewModel = viewModel,
            initialTab = detailsBottomSheetTab,
            initialFileToOpen = fileToOpenInEditor,
            onDismissRequest = {
                showDetailsBottomSheet = false
                fileToOpenInEditor = null
            }
        )
    }

    if (selectedSubagent != null) {
        SubagentChatBottomSheet(
            subagent = selectedSubagent!!,
            viewModel = viewModel,
            onDismissRequest = { selectedSubagent = null },
            onOpenFullScreen = {
                val target = selectedSubagent
                selectedSubagent = null
                if (target != null) {
                    viewModel.openSubagentAsActiveConversation(context, target)
                }
            }
        )
    }

    if (showSettingsBottomSheet) {
        SettingsBottomSheet(
            viewModel = viewModel,
            initialPage = settingsInitialPage,
            onDismissRequest = {
                showSettingsBottomSheet = false
                settingsInitialPage = SettingsPage.MAIN
            }
        )
    }

    val activeInstallProgress by viewModel.activeInstallProgress.collectAsStateWithLifecycle()
    if (activeInstallProgress != null && !showSettingsBottomSheet) {
        InstallationProgressDialog(
            progress = activeInstallProgress!!,
            onDismissRequest = { viewModel.dismissInstallProgress() }
        )
    }

    if (showSkillsDialog) {
        SkillsManagementDialog(
            viewModel = viewModel,
            onDismiss = { showSkillsDialog = false }
        )
    }

    if (showPluginsDialog) {
        PluginsManagementDialog(
            viewModel = viewModel,
            onDismiss = { showPluginsDialog = false }
        )
    }
}

