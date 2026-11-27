package id.vern.antigravity.viewmodel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.isActive
import id.vern.antigravity.AntigravityApp
import id.vern.antigravity.R
import id.vern.antigravity.oauth.GitHubAccount
import id.vern.antigravity.oauth.GitHubDeviceCode
import id.vern.antigravity.oauth.GitHubRepoItem
import id.vern.antigravity.oauth.GitHubVerificationResult
import id.vern.antigravity.data.AccentColor
import id.vern.antigravity.data.AppearanceConfig
import id.vern.antigravity.data.AttachedFile
import id.vern.antigravity.data.ChatMessage
import id.vern.antigravity.data.QueuedMessage
import id.vern.antigravity.data.RunningTask
import id.vern.antigravity.data.RunningSubagent
import id.vern.antigravity.data.DaemonTask
import id.vern.antigravity.data.AgentExecutionStep
import id.vern.antigravity.data.AgentStepKind
import id.vern.antigravity.data.ChangedFileDetail
import id.vern.antigravity.data.ChangedFileItem
import id.vern.antigravity.data.OpenTab
import id.vern.antigravity.data.ArtifactFileItem
import id.vern.antigravity.data.TurnFileChanges
import id.vern.antigravity.data.VoiceTurnResult
import id.vern.antigravity.data.ModelLimitReachedInfo
import id.vern.antigravity.data.EngineStatus
import id.vern.antigravity.data.ExecutionLogEntry
import id.vern.antigravity.data.MessageRole
import id.vern.antigravity.data.MessageStatus
import id.vern.antigravity.data.ProjectSortOrder
import id.vern.antigravity.data.ThemeMode
import id.vern.antigravity.data.ThemePreset
import id.vern.antigravity.data.UserQuotaSummary
import id.vern.antigravity.data.CodeAssistTier
import id.vern.antigravity.data.CliSlashCommand
import id.vern.antigravity.data.ModelAvailability
import id.vern.antigravity.data.SlashAction
import id.vern.antigravity.data.SlashCommandParser
import id.vern.antigravity.data.SYSTEM_SLASH_COMMANDS
import id.vern.antigravity.data.mergeDynamicSlashActions
import id.vern.antigravity.service.AgyModelsService
import id.vern.antigravity.service.CliModel
import id.vern.antigravity.service.CloudCodeQuotaService
import id.vern.antigravity.service.PermissionConfigService
import id.vern.antigravity.service.PermissionGrantsState
import id.vern.antigravity.service.PermissionRule
import id.vern.antigravity.service.RuleConfigService
import id.vern.antigravity.service.RuleItem
import id.vern.antigravity.service.McpConfigService
import id.vern.antigravity.service.McpServerItem
import id.vern.antigravity.service.WakeLockHolder
import id.vern.antigravity.service.TerminalLine
import id.vern.antigravity.service.TerminalSessionManager
import id.vern.antigravity.service.PromptCompletionNotifier
import id.vern.antigravity.executor.EngineExecutor
import id.vern.antigravity.install.BootstrapInstaller
import id.vern.antigravity.install.CoreUpdateManager
import id.vern.antigravity.install.CoreUpdateInfo
import id.vern.antigravity.integrations.CloudAccount
import id.vern.antigravity.integrations.CloudCliStatus
import id.vern.antigravity.integrations.CloudProvider
import id.vern.antigravity.integrations.CloudVerificationResult
import id.vern.antigravity.integrations.CloudflareAccountItem
import id.vern.antigravity.integrations.CloudflarePagesDeploymentItem
import id.vern.antigravity.integrations.CloudflarePagesProjectItem
import id.vern.antigravity.integrations.ProjectIntegrationBinding
import id.vern.antigravity.integrations.ProjectIntegrationsManager
import id.vern.antigravity.integrations.SupabaseProjectItem
import id.vern.antigravity.integrations.VercelDeploymentItem
import id.vern.antigravity.integrations.VercelProjectItem
import id.vern.antigravity.integrations.VercelTeamItem
import id.vern.antigravity.oauth.GitHubAuthManager
import id.vern.antigravity.oauth.OAuthManager
import id.vern.antigravity.oauth.AccountManager
import id.vern.antigravity.oauth.StoredAccount
import id.vern.antigravity.service.CoreServerService
import id.vern.antigravity.util.AppLogger
import id.vern.antigravity.util.AppUpdateState
import id.vern.antigravity.util.UpdateChecker
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import android.provider.DocumentsContract
import android.util.Log
import android.system.Os
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject

data class CoreEngineUpdateState(
    val isChecking: Boolean = false,
    val currentVersion: String = "",
    val latestVersion: String? = null,
    val isUpdateAvailable: Boolean = false,
    val downloadUrl: String? = null,
    val sha512: String = "",
    val hasBackup: Boolean = false,
    val lastCheckedTime: Long = 0L,
    val error: String? = null
)

data class CustomizationInstallProgress(
    val title: String,
    val command: String,
    val lines: List<TerminalLine> = emptyList(),
    val isRunning: Boolean = true,
    val isSuccess: Boolean? = null
)

data class ConversationSummary(
    val id: String,
    val title: String,
    val timestamp: Long,
    val messageCount: Int,
    val isPinned: Boolean = false,
    val projectPath: String? = null
)

data class CustomizationItem(
    val id: String,
    val name: String,
    val description: String,
    val type: String,
    val isBuiltIn: Boolean = false,
    val path: String? = null
)

data class SkillCatalogueItem(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val isPlugin: Boolean = false,
    val defaultPrompt: String = "",
    val repoUrl: String = "",
    val subPath: String? = null
)

data class DiscoveredRepoSkill(
    val id: String,
    val name: String,
    val description: String,
    val subPath: String,
    val isSelected: Boolean = true
)

data class CustomizationRepoTrack(
    val id: String,
    val name: String,
    val repoUrl: String,
    val isPlugin: Boolean = false,
    val subPath: String? = null,
    val installedSha: String = "",
    val remoteSha: String? = null,
    val lastChecked: Long = 0L,
    val hasUpdate: Boolean = false
)

data class TokenUsageStats(
    val rulesTokens: Int,
    val rulesBreakdowns: List<Pair<String, Int>>,
    val skillsTokens: Int,
    val skillsBreakdowns: List<Pair<String, Int>>,
    val totalLimit: Int = 20000
)

class AntigravityViewModel : ViewModel() {

    companion object {
        const val PREFS_NAME = "antigravity_prefs"
        const val KEY_THEME_MODE = "ag_theme_mode"
        const val KEY_ACCENT_COLOR = "ag_accent_color"
        const val KEY_WORKSPACE_PATH = "ag_workspace_path"
        const val KEY_SELECTED_MODEL = "ag_selected_model"
        const val KEY_SELECTED_MODEL_CLI_ID = "ag_selected_model_cli_id"
        const val KEY_CLI_MODELS_JSON = "ag_cli_models_json"
        const val KEY_CLI_MODELS_FETCHED_AT = "ag_cli_models_fetched_at"
        const val KEY_CLI_SLASH_COMMANDS = "ag_cli_slash_commands"
        const val KEY_CLI_SLASH_VERSION = "ag_cli_slash_version"
        const val KEY_CLI_SLASH_FETCHED_AT = "ag_cli_slash_fetched_at"
        const val CLI_MODELS_THROTTLE_MS = 10L * 60L * 1000L
        const val KEY_THINKING_BUDGET = "ag_thinking_budget"
        const val KEY_VERBOSE_THINKING = "ag_verbose_thinking"
        const val KEY_CONVERSATION_WIDTH = "ag_conversation_width"
        const val KEY_THEME_PRESET = "ag_theme_preset"
        const val KEY_CUSTOM_THEME_BG = "ag_custom_theme_bg"
        const val KEY_CUSTOM_THEME_FG = "ag_custom_theme_fg"
        const val KEY_CUSTOM_THEME_ACCENT = "ag_custom_theme_accent"
        const val KEY_EDITOR_WORD_WRAP = "ag_editor_word_wrap"
        const val KEY_EDITOR_LINE_NUMBERS = "ag_editor_line_numbers"
        const val KEY_EDITOR_FONT_SIZE = "ag_editor_font_size"
        const val KEY_SKIP_PERMISSIONS = "ag_skip_permissions"
        const val KEY_PROJECT_SORT = "ag_project_sort"
        const val KEY_QUEUED_MESSAGES_MODE = "ag_queued_messages_mode"
        const val KEY_SECURITY_PRESET = "ag_security_preset"
        const val KEY_PLAN_REVIEW_POLICY = "ag_plan_review_policy"
        const val KEY_BROWSER_JS_POLICY = "ag_browser_js_policy"
        const val KEY_ENABLE_CREDIT_OVERAGES = "ag_enable_credit_overages"
        const val KEY_COMPLETION_NOTIFICATION_ENABLED = "ag_completion_notification_enabled"
        const val KEY_COMPLETION_BUZZ_TYPE = "ag_completion_buzz_type"
        const val KEY_COMPLETION_SOUND_TYPE = "ag_completion_sound_type"
        const val KEY_COMPLETION_CUSTOM_SOUND_URI = "ag_completion_custom_sound_uri"
        const val KEY_COMPLETION_CUSTOM_SOUND_TITLE = "ag_completion_custom_sound_title"
        const val KEY_VOICE_LANGUAGE = "ag_voice_language"
        const val KEY_VOICE_REPLY_IN_LANGUAGE = "ag_voice_reply_in_language"
        const val KEY_VOICE_SPEECH_RATE = "ag_voice_speech_rate"
        const val KEY_VOICE_ONLY_MODE = "ag_voice_only_mode"
        const val VOICE_ONLY_CONV_ID = "__voice_only__"
        const val KEY_USER_PROJECTS = "ag_user_projects"
        const val KEY_CONVERSATION_HISTORY = "ag_conversation_history"
        const val KEY_LAST_ACTIVE_CONV_ID = "ag_last_active_conv_id"
        const val KEY_CUSTOMIZATION_TRACKING = "ag_customization_tracking"
        const val KEY_CUSTOMIZATION_LAST_CHECK = "ag_customization_last_check"
    }

    private val projectWorkspaceDelegate = ProjectWorkspaceDelegate()
    private val conversationManagerDelegate = ConversationManagerDelegate()
    private val authDelegate = AuthDelegate()
    private val settingsPreferencesDelegate = SettingsPreferencesDelegate()
    private val customizationInstallerDelegate = CustomizationInstallerDelegate()
    private val customizationCatalogueDelegate = CustomizationCatalogueDelegate()
    private val integrationsDelegate = IntegrationsDelegate()

    private val mutableChatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = mutableChatMessages.asStateFlow()

    val conversationTitle: StateFlow<String> = conversationManagerDelegate.conversationTitle
    val activeConversationId: StateFlow<String?> = conversationManagerDelegate.activeConversationId
    val isConversationPinned: StateFlow<Boolean> = conversationManagerDelegate.isConversationPinned
    val conversationHistory: StateFlow<List<ConversationSummary>> = conversationManagerDelegate.conversationHistory

    val appearanceConfig: StateFlow<AppearanceConfig> = settingsPreferencesDelegate.appearanceConfig
    val workspacePath: StateFlow<String> = projectWorkspaceDelegate.workspacePath
    val selectedModel: StateFlow<String> = settingsPreferencesDelegate.selectedModel
    val selectedModelCliId: StateFlow<String?> = settingsPreferencesDelegate.selectedModelCliId
    val thinkingBudget: StateFlow<String> = settingsPreferencesDelegate.thinkingBudget
    val verboseThinking: StateFlow<Boolean> = settingsPreferencesDelegate.verboseThinking
    val conversationWidth: StateFlow<String> = settingsPreferencesDelegate.conversationWidth
    val themePreset: StateFlow<String> = settingsPreferencesDelegate.themePreset
    val customThemeBg: StateFlow<String> = settingsPreferencesDelegate.customThemeBg
    val customThemeFg: StateFlow<String> = settingsPreferencesDelegate.customThemeFg
    val customThemeAccent: StateFlow<String> = settingsPreferencesDelegate.customThemeAccent
    val editorWordWrap: StateFlow<Boolean> = settingsPreferencesDelegate.editorWordWrap
    val editorLineNumbers: StateFlow<Boolean> = settingsPreferencesDelegate.editorLineNumbers
    val editorFontSize: StateFlow<Int> = settingsPreferencesDelegate.editorFontSize
    val skipPermissions: StateFlow<Boolean> = settingsPreferencesDelegate.skipPermissions
    val queuedMessagesMode: StateFlow<String> = settingsPreferencesDelegate.queuedMessagesMode
    val securityPreset: StateFlow<String> = settingsPreferencesDelegate.securityPreset
    val planReviewPolicy: StateFlow<String> = settingsPreferencesDelegate.planReviewPolicy
    val browserJsPolicy: StateFlow<String> = settingsPreferencesDelegate.browserJsPolicy
    val enableCreditOverages: StateFlow<Boolean> = settingsPreferencesDelegate.enableCreditOverages
    val completionNotificationEnabled: StateFlow<Boolean> = settingsPreferencesDelegate.completionNotificationEnabled
    val completionBuzzType: StateFlow<String> = settingsPreferencesDelegate.completionBuzzType
    val completionSoundType: StateFlow<String> = settingsPreferencesDelegate.completionSoundType
    val completionCustomSoundUri: StateFlow<String?> = settingsPreferencesDelegate.completionCustomSoundUri
    val completionCustomSoundTitle: StateFlow<String> = settingsPreferencesDelegate.completionCustomSoundTitle
    val voiceLanguage: StateFlow<id.vern.antigravity.voice.VoiceLanguage> = settingsPreferencesDelegate.voiceLanguage
    val voiceReplyInLanguage: StateFlow<Boolean> = settingsPreferencesDelegate.voiceReplyInLanguage
    val voiceSpeechRate: StateFlow<Float> = settingsPreferencesDelegate.voiceSpeechRate
    val voiceOnlyMode: StateFlow<Boolean> = settingsPreferencesDelegate.voiceOnlyMode
    private val mutableLatestVoiceTurnResult = MutableStateFlow<VoiceTurnResult?>(null)
    val latestVoiceTurnResult: StateFlow<VoiceTurnResult?> = mutableLatestVoiceTurnResult.asStateFlow()
    private val mutableModelLimitReachedInfo = MutableStateFlow<ModelLimitReachedInfo?>(null)
    val modelLimitReachedInfo: StateFlow<ModelLimitReachedInfo?> = mutableModelLimitReachedInfo.asStateFlow()
    val permissionState: StateFlow<PermissionGrantsState> = settingsPreferencesDelegate.permissionState
    val installedRulesList: StateFlow<List<RuleItem>> = settingsPreferencesDelegate.installedRulesList
    val mcpServers: StateFlow<List<McpServerItem>> = settingsPreferencesDelegate.mcpServers

    val isAuthenticating: StateFlow<Boolean> = authDelegate.isAuthenticating
    val authError: StateFlow<String?> = authDelegate.authError
    val showGoogleSignInDialog: StateFlow<Boolean> = authDelegate.showGoogleSignInDialog
    val pendingAuthUrl: StateFlow<String?> = authDelegate.pendingAuthUrl
    val userEmail: StateFlow<String?> = authDelegate.userEmail
    val userName: StateFlow<String> = authDelegate.userName
    val userPicture: StateFlow<String?> = authDelegate.userPicture
    val accounts: StateFlow<List<StoredAccount>> = authDelegate.accounts
    val githubAccount: StateFlow<GitHubAccount?> = authDelegate.githubAccount
    val githubAccounts: StateFlow<List<GitHubAccount>> = authDelegate.githubAccounts
    val githubVerification: StateFlow<GitHubVerificationResult?> = authDelegate.githubVerification
    val isGithubVerifying: StateFlow<Boolean> = authDelegate.isGithubVerifying
    val githubRepos: StateFlow<List<GitHubRepoItem>> = authDelegate.githubRepos
    val isLoadingGithubRepos: StateFlow<Boolean> = authDelegate.isLoadingGithubRepos
    val githubReposError: StateFlow<String?> = authDelegate.githubReposError
    val githubDeviceCode: StateFlow<GitHubDeviceCode?> = authDelegate.githubDeviceCode
    val isGithubAuthenticating: StateFlow<Boolean> = authDelegate.isGithubAuthenticating
    val githubAuthError: StateFlow<String?> = authDelegate.githubAuthError

    val projectList: StateFlow<List<File>> = projectWorkspaceDelegate.projectList
    val projectSortOrder: StateFlow<ProjectSortOrder> = projectWorkspaceDelegate.projectSortOrder

    val integrationTargetProjectPath: StateFlow<String> = integrationsDelegate.targetProjectPath
    val integrationTargetBinding: StateFlow<ProjectIntegrationBinding> = integrationsDelegate.targetBinding
    val activeWorkspaceBinding: StateFlow<ProjectIntegrationBinding?> = integrationsDelegate.activeWorkspaceBinding
    val allProjectBindings: StateFlow<Map<String, ProjectIntegrationBinding>> = integrationsDelegate.allProjectBindings
    val cloudAccounts: StateFlow<List<CloudAccount>> = integrationsDelegate.cloudAccounts
    val selectedCloudAccountIds: StateFlow<Map<CloudProvider, String?>> = integrationsDelegate.selectedAccountIds
    val cloudVerifications: StateFlow<Map<CloudProvider, CloudVerificationResult?>> = integrationsDelegate.verifications
    val isVerifyingCloudProvider: StateFlow<Map<CloudProvider, Boolean>> = integrationsDelegate.isVerifying
    val isAuthenticatingCloudProvider: StateFlow<Map<CloudProvider, Boolean>> = integrationsDelegate.isAuthenticating
    val cloudAuthErrors: StateFlow<Map<CloudProvider, String?>> = integrationsDelegate.authErrors
    val vercelTeams: StateFlow<List<VercelTeamItem>> = integrationsDelegate.vercelTeams
    val vercelSelectedTeamId: StateFlow<String> = integrationsDelegate.vercelSelectedTeamId
    val vercelProjects: StateFlow<List<VercelProjectItem>> = integrationsDelegate.vercelProjects
    val vercelDeployments: StateFlow<Map<String, List<VercelDeploymentItem>>> = integrationsDelegate.vercelDeployments
    val supabaseProjects: StateFlow<List<SupabaseProjectItem>> = integrationsDelegate.supabaseProjects
    val cloudflareAccounts: StateFlow<List<CloudflareAccountItem>> = integrationsDelegate.cloudflareAccounts
    val cloudflareSelectedCfAccountId: StateFlow<String> = integrationsDelegate.cloudflareSelectedCfAccountId
    val cloudflarePagesProjects: StateFlow<List<CloudflarePagesProjectItem>> = integrationsDelegate.cloudflarePagesProjects
    val cloudflarePagesDeployments: StateFlow<Map<String, List<CloudflarePagesDeploymentItem>>> = integrationsDelegate.cloudflarePagesDeployments
    val loadingCloudProviders: StateFlow<Set<CloudProvider>> = integrationsDelegate.loadingProviders
    val cloudProviderErrors: StateFlow<Map<CloudProvider, String?>> = integrationsDelegate.providerErrors
    val cloudCliStatus: StateFlow<CloudCliStatus?> = integrationsDelegate.cliStatus
    val installingCloudCliProvider: StateFlow<CloudProvider?> = integrationsDelegate.installingCliProvider
    val deployingCloudProvider: StateFlow<CloudProvider?> = integrationsDelegate.deployingProvider

    val installedSkills: StateFlow<List<CustomizationItem>> = customizationCatalogueDelegate.installedSkills
    val installedPlugins: StateFlow<List<CustomizationItem>> = customizationCatalogueDelegate.installedPlugins
    val customizationTracks: StateFlow<Map<String, CustomizationRepoTrack>> = customizationCatalogueDelegate.customizationTracks
    val isCheckingCustomizationUpdates: StateFlow<Boolean> = customizationCatalogueDelegate.isCheckingCustomizationUpdates
    val skillsCatalogue: StateFlow<List<SkillCatalogueItem>> = customizationCatalogueDelegate.skillsCatalogue
    val pluginsCatalogue: StateFlow<List<SkillCatalogueItem>> = customizationCatalogueDelegate.pluginsCatalogue
    val isFetchingCatalogue: StateFlow<Boolean> = customizationCatalogueDelegate.isFetchingCatalogue
    val installingItemId: StateFlow<String?> = customizationCatalogueDelegate.installingItemId
    val tokenUsageStats: StateFlow<TokenUsageStats> = customizationCatalogueDelegate.tokenUsageStats

    val activeInstallProgress: StateFlow<CustomizationInstallProgress?> = customizationInstallerDelegate.activeInstallProgress
    val isInspectingRepo: StateFlow<Boolean> = customizationInstallerDelegate.isInspectingRepo
    val discoveredRepoSkills: StateFlow<List<DiscoveredRepoSkill>> = customizationInstallerDelegate.discoveredRepoSkills
    val repoInspectionError: StateFlow<String?> = customizationInstallerDelegate.repoInspectionError
    private val mutableAttachedFiles = MutableStateFlow<List<AttachedFile>>(emptyList())
    val attachedFiles: StateFlow<List<AttachedFile>> = mutableAttachedFiles.asStateFlow()

    private val mutableExecutingConvIds = MutableStateFlow<Set<String>>(emptySet())
    val executingConversationIds: StateFlow<Set<String>> = mutableExecutingConvIds.asStateFlow()

    private val mutableIsExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = mutableIsExecuting.asStateFlow()

    private val queuedMessagesByConv = MutableStateFlow<Map<String, List<QueuedMessage>>>(emptyMap())
    private val mutableQueuedMessages = MutableStateFlow<List<QueuedMessage>>(emptyList())
    val queuedMessages: StateFlow<List<QueuedMessage>> = mutableQueuedMessages.asStateFlow()

    private val workingStatusByConv = MutableStateFlow<Map<String, String>>(emptyMap())
    private val mutableActiveWorkingStatus = MutableStateFlow<String?>(null)
    val activeWorkingStatus: StateFlow<String?> = mutableActiveWorkingStatus.asStateFlow()

    private val runningTasksByConv = MutableStateFlow<Map<String, List<RunningTask>>>(emptyMap())
    private val mutableRunningTasks = MutableStateFlow<List<RunningTask>>(emptyList())
    val runningTasks: StateFlow<List<RunningTask>> = mutableRunningTasks.asStateFlow()

    private val messagesByConv = ConcurrentHashMap<String, List<ChatMessage>>()
    private val jobsByConv = ConcurrentHashMap<String, Job>()

    fun isConversationExecuting(convId: String?): Boolean {
        return !convId.isNullOrBlank() && convId in mutableExecutingConvIds.value
    }

    fun getMessagesForConversation(convId: String): List<ChatMessage> {
        return messagesByConv[convId].orEmpty()
    }

    private fun syncForegroundExecutionState() {
        val cid = activeConversationId.value
        mutableIsExecuting.value = isConversationExecuting(cid)
        mutableQueuedMessages.value = if (cid != null) queuedMessagesByConv.value[cid].orEmpty() else emptyList()
        mutableRunningTasks.value = if (cid != null) runningTasksByConv.value[cid].orEmpty() else emptyList()
        mutableActiveWorkingStatus.value = if (cid != null) workingStatusByConv.value[cid] else null
    }

    private fun updateMessagesForConv(convId: String, transform: (List<ChatMessage>) -> List<ChatMessage>): List<ChatMessage> {
        val updated = messagesByConv.compute(convId) { _, existing ->
            transform(existing ?: emptyList())
        } ?: emptyList()
        if (activeConversationId.value == convId) {
            mutableChatMessages.value = updated
        }
        return updated
    }

    private val mutableRunningSubagents = MutableStateFlow<List<RunningSubagent>>(emptyList())
    val runningSubagents: StateFlow<List<RunningSubagent>> = mutableRunningSubagents.asStateFlow()

    private val mutableDaemonTasks = MutableStateFlow<List<DaemonTask>>(emptyList())
    val daemonTasks: StateFlow<List<DaemonTask>> = mutableDaemonTasks.asStateFlow()

    private val mutablePendingInteraction = MutableStateFlow<id.vern.antigravity.data.PendingInteractionPrompt?>(null)
    val pendingInteraction: StateFlow<id.vern.antigravity.data.PendingInteractionPrompt?> = mutablePendingInteraction.asStateFlow()

    fun resolvePendingInteraction(optionId: String?) {
        val current = mutablePendingInteraction.value
        mutablePendingInteraction.value = null
        current?.onSelect?.invoke(optionId)
    }

    private val mutableExecutionLogs = MutableStateFlow<List<ExecutionLogEntry>>(emptyList())
    val executionLogs: StateFlow<List<ExecutionLogEntry>> = mutableExecutionLogs.asStateFlow()

    private val editorSessionDelegate = EditorSessionDelegate()
    val openEditorTabs: StateFlow<List<OpenTab>> = editorSessionDelegate.openTabs
    val activeEditorTabId: StateFlow<String?> = editorSessionDelegate.activeTabId

    fun openFileInEditor(file: File) = editorSessionDelegate.openFile(file)
    fun closeEditorTab(tabId: String) = editorSessionDelegate.closeTab(tabId)
    fun selectEditorTab(tabId: String) = editorSessionDelegate.selectTab(tabId)
    fun updateEditorContent(tabId: String, newContent: String) = editorSessionDelegate.updateContent(tabId, newContent)
    fun setEditorTabDiffView(tabId: String, isDiff: Boolean) = editorSessionDelegate.setDiffView(tabId, isDiff)
    fun saveEditorTab(context: Context, tabId: String): Boolean = editorSessionDelegate.saveTab(context, tabId)

    private val artifactManagerDelegate = ArtifactManagerDelegate()
    val conversationArtifacts: StateFlow<List<ArtifactFileItem>> = artifactManagerDelegate.conversationArtifacts

    private val turnExecutionDelegate = TurnExecutionDelegate()

    fun refreshConversationArtifacts(context: Context) {
        viewModelScope.launch {
            val convId = activeConversationId.value
            val cliId = convId?.let { convIdToCliSessionId[it] }
            artifactManagerDelegate.refreshArtifacts(context, convId, cliId)
        }
    }

    private val gitStatusDelegate = GitStatusDelegate()
    val gitChangedFiles: StateFlow<List<ChangedFileItem>> = gitStatusDelegate.changedFiles
    val gitDiffCache: StateFlow<Map<String, String>> = gitStatusDelegate.diffCache
    val gitLoadingDiffs: StateFlow<Set<String>> = gitStatusDelegate.loadingDiffs
    val gitRepoSummary: StateFlow<GitRepoSummary> = gitStatusDelegate.repoSummary
    val isGitActionRunning: StateFlow<Boolean> = gitStatusDelegate.isGitActionRunning
    val gitActionStatus: StateFlow<GitActionResult?> = gitStatusDelegate.gitActionStatus

    suspend fun fetchGitChangedFiles(context: Context, workspacePath: String?): List<ChangedFileItem> =
        gitStatusDelegate.fetchGitChangedFiles(context, workspacePath)

    suspend fun fetchGitDiff(context: Context, workspacePath: String?, relativePath: String): String =
        gitStatusDelegate.fetchGitDiff(context, workspacePath, relativePath)

    fun clearGitDiffCache() = gitStatusDelegate.clearDiffCache()
    fun clearGitActionStatus() = gitStatusDelegate.clearGitActionStatus()

    fun commitGitChanges(context: Context, message: String, onDone: ((GitActionResult) -> Unit)? = null) {
        viewModelScope.launch {
            val res = gitStatusDelegate.commitChanges(context, workspacePath.value, message)
            onDone?.invoke(res)
        }
    }

    fun pullGitChanges(context: Context, onDone: ((GitActionResult) -> Unit)? = null) {
        viewModelScope.launch {
            val res = gitStatusDelegate.pullChanges(context, workspacePath.value)
            onDone?.invoke(res)
        }
    }

    fun pushGitChanges(context: Context, onDone: ((GitActionResult) -> Unit)? = null) {
        viewModelScope.launch {
            val res = gitStatusDelegate.pushChanges(context, workspacePath.value)
            onDone?.invoke(res)
        }
    }

    fun checkoutGitBranch(context: Context, branchName: String, createNew: Boolean = false, onDone: ((GitActionResult) -> Unit)? = null) {
        viewModelScope.launch {
            val res = gitStatusDelegate.checkoutBranch(context, workspacePath.value, branchName, createNew)
            onDone?.invoke(res)
        }
    }

    fun cloneGitHubRepository(
        context: Context,
        cloneUrl: String,
        destinationParentDir: File,
        repoName: String,
        accountLogin: String? = null,
        repoFullName: String = "",
        onDone: ((GitActionResult, File?) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val effectiveLogin = accountLogin?.takeIf { it.isNotBlank() } ?: githubAccount.value?.login
            val gitEnv = withContext(Dispatchers.IO) {
                GitHubAuthManager.buildGitEnvForAccount(context, effectiveLogin)
            }
            val (res, clonedDir) = gitStatusDelegate.cloneRepository(
                context = context,
                cloneUrl = cloneUrl,
                destinationParentDir = destinationParentDir,
                repoName = repoName,
                gitEnvOverride = gitEnv
            )
            if (res.success && clonedDir != null) {
                if (!effectiveLogin.isNullOrBlank()) {
                    val detected = GitHubAuthManager.detectGitHubRemoteRepo(clonedDir)
                    val resolvedFullName = repoFullName.ifBlank { detected?.first.orEmpty() }
                    ProjectIntegrationsManager.bindGitHub(
                        context = context,
                        projectPath = clonedDir.absolutePath,
                        accountLogin = effectiveLogin,
                        repoFullName = resolvedFullName,
                        repoCloneUrl = cloneUrl
                    )
                }
                addProject(context, clonedDir.absolutePath)
                setWorkspacePath(context, clonedDir.absolutePath)
                integrationsDelegate.refresh(context, clonedDir.absolutePath, clonedDir.absolutePath)
            }
            onDone?.invoke(res, clonedDir)
        }
    }

    private val convIdToCliSessionId = java.util.concurrent.ConcurrentHashMap<String, String>()

    init {
        AppLogger.setHandler { tag, message, isError ->
            val entry = ExecutionLogEntry(
                tag = tag,
                message = message,
                isError = isError
            )
            mutableExecutionLogs.update { current ->
                val capacity = 1000
                if (current.size >= capacity) {
                    val dropCount = current.size - capacity + 1
                    ArrayList<ExecutionLogEntry>(capacity).apply {
                        addAll(current.subList(dropCount, current.size))
                        add(entry)
                    }
                } else {
                    ArrayList<ExecutionLogEntry>(current.size + 1).apply {
                        addAll(current)
                        add(entry)
                    }
                }
            }
        }
    }

    private val mutableEngineStatus = MutableStateFlow(EngineStatus())
    val engineStatus: StateFlow<EngineStatus> = mutableEngineStatus.asStateFlow()

    private val mutablePendingInputText = MutableStateFlow<String?>(null)
    val pendingInputText: StateFlow<String?> = mutablePendingInputText.asStateFlow()

    fun clearPendingInputText() {
        mutablePendingInputText.value = null
    }

    private val mutableIsBooting = MutableStateFlow(true)
    val isBooting: StateFlow<Boolean> = mutableIsBooting.asStateFlow()

    private val mutableSetupProgress = MutableStateFlow(0f)
    val setupProgress: StateFlow<Float> = mutableSetupProgress.asStateFlow()

    private val mutableBootStatusText = MutableStateFlow("Initializing Antigravity...")
    val bootStatusText: StateFlow<String> = mutableBootStatusText.asStateFlow()

    private val mutableBootError = MutableStateFlow<String?>(null)
    val bootError: StateFlow<String?> = mutableBootError.asStateFlow()

    private val mutableBootErrorDetail = MutableStateFlow<String?>(null)
    val bootErrorDetail: StateFlow<String?> = mutableBootErrorDetail.asStateFlow()

    private val mutableUserQuotaSummary = MutableStateFlow<UserQuotaSummary?>(null)
    val userQuotaSummary: StateFlow<UserQuotaSummary?> = mutableUserQuotaSummary.asStateFlow()

    private val mutableCodeAssistTier = MutableStateFlow<CodeAssistTier?>(null)
    val codeAssistTier: StateFlow<CodeAssistTier?> = mutableCodeAssistTier.asStateFlow()

    private val mutableIsQuotaLoading = MutableStateFlow(false)
    val isQuotaLoading: StateFlow<Boolean> = mutableIsQuotaLoading.asStateFlow()

    private val mutableQuotaErrorMessage = MutableStateFlow<String?>(null)
    val quotaErrorMessage: StateFlow<String?> = mutableQuotaErrorMessage.asStateFlow()

    private val mutableCliModels = MutableStateFlow<List<CliModel>>(emptyList())
    val cliModels: StateFlow<List<CliModel>> = mutableCliModels.asStateFlow()

    fun refreshCliModels(context: Context, force: Boolean = false) {
        viewModelScope.launch {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastFetch = prefs.getLong(KEY_CLI_MODELS_FETCHED_AT, 0L)
            if (!force && System.currentTimeMillis() - lastFetch < CLI_MODELS_THROTTLE_MS && mutableCliModels.value.isNotEmpty()) return@launch
            val result = AgyModelsService.fetchCliModels(context, force = force)
            if (result.models.isNotEmpty()) {
                adoptCliCatalog(context, result.models)
                val hasArgonInCli = result.models.any { CloudCodeQuotaService.isArgonModel(it.cliId, it.displayName) }
                val currentGroups = mutableUserQuotaSummary.value?.groups.orEmpty()
                val hasArgonGroup = currentGroups.any { it.displayName.contains("argon", ignoreCase = true) }
                if (hasArgonInCli && !hasArgonGroup) {
                    val refreshedQuota = CloudCodeQuotaService.fetchQuotaSummary(context, mutableCodeAssistTier.value)
                    if (refreshedQuota != null) {
                        mutableUserQuotaSummary.value = refreshedQuota
                    }
                }
            } else {
                val quotaFallback = mutableUserQuotaSummary.value?.pickerModels.orEmpty()
                if (mutableCliModels.value.isEmpty() && quotaFallback.isNotEmpty()) {
                    adoptCliCatalog(context, quotaFallback)
                    addLog("Models", "Adopted backend model catalog as fallback (${quotaFallback.size} models)")
                } else if (mutableCliModels.value.isNotEmpty()) {
                    addLog("Models", "CLI model query skipped or timed out, keeping active catalog (${mutableCliModels.value.size} models)")
                } else if (result.error != null) {
                    addLog("Models", "agy models failed: ${result.error}" + (result.detail?.let { " — $it" } ?: ""))
                }
            }
        }
    }

    private fun adoptCliCatalog(context: Context, models: List<CliModel>) {
        if (models.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        mutableCliModels.value = models
        prefs.edit()
            .putString(KEY_CLI_MODELS_JSON, AgyModelsService.encodeCliModels(models))
            .putLong(KEY_CLI_MODELS_FETCHED_AT, System.currentTimeMillis())
            .apply()
        reconcileSelectionWithCatalog(prefs, models)
    }

    private fun reconcileSelectionWithCatalog(prefs: android.content.SharedPreferences, catalog: List<CliModel>) {
        if (catalog.isEmpty()) return
        val currentId = selectedModelCliId.value
        if (currentId != null && catalog.any { it.cliId == currentId }) return
        val currentName = selectedModel.value
        val family = catalog.filter { it.displayName == currentName }
        val adopted = when {
            family.isNotEmpty() -> family.firstOrNull { it.effort.equals(thinkingBudget.value, ignoreCase = true) }
                ?: family.firstOrNull { it.effort.isEmpty() }
                ?: family.firstOrNull()
            currentName.isBlank() -> defaultCatalogEntry(catalog)
            else -> null
        }
        settingsPreferencesDelegate.setSelectedModelCliIdDirect(adopted?.cliId)
        val editor = prefs.edit()
        if (adopted != null) {
            editor.putString(KEY_SELECTED_MODEL_CLI_ID, adopted.cliId)
            if (currentName.isBlank()) {
                settingsPreferencesDelegate.setSelectedModelDirect(adopted.displayName)
                settingsPreferencesDelegate.setThinkingBudgetDirect(adopted.effort)
                editor.putString(KEY_SELECTED_MODEL, adopted.displayName)
                editor.putString(KEY_THINKING_BUDGET, adopted.effort)
            }
        } else {
            editor.remove(KEY_SELECTED_MODEL_CLI_ID)
        }
        editor.apply()
        addLog("Model", "CLI catalog changed; stored model id reconciled to ${adopted?.cliId ?: "(CLI default)"}")
    }

    private fun defaultCatalogEntry(catalog: List<CliModel> = mutableCliModels.value): CliModel? {
        if (catalog.isEmpty()) return null
        return catalog.firstOrNull { it.isDefault }
            ?: catalog.firstOrNull { it.effort.equals("High", ignoreCase = true) }
            ?: catalog.firstOrNull { it.effort.isEmpty() }
            ?: catalog.firstOrNull()
    }

    fun refreshUserQuota(context: Context) {
        viewModelScope.launch {
            mutableIsQuotaLoading.value = true
            mutableQuotaErrorMessage.value = null
            try {
                val tier = CloudCodeQuotaService.fetchCodeAssistTier(context)
                val quota = CloudCodeQuotaService.fetchQuotaSummary(context, tier)
                mutableCodeAssistTier.value = tier
                mutableUserQuotaSummary.value = quota
                if (mutableCliModels.value.isEmpty() && !quota?.pickerModels.isNullOrEmpty()) {
                    adoptCliCatalog(context, quota!!.pickerModels)
                }

                refreshCliModels(context)
                val currentSelected = selectedModel.value

                val currentAvailability = quota?.availableModels?.firstOrNull { it.matches(currentSelected) }?.availability ?: ModelAvailability.UNKNOWN
                if (currentAvailability == ModelAvailability.UNAVAILABLE) {

                    val fallback = defaultCatalogEntry()
                    settingsPreferencesDelegate.setSelectedModelDirect(fallback?.displayName ?: "")
                    settingsPreferencesDelegate.setSelectedModelCliIdDirect(fallback?.cliId)
                    settingsPreferencesDelegate.setThinkingBudgetDirect(fallback?.effort ?: "")
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val editor = prefs.edit()
                    if (fallback != null) {
                        editor.putString(KEY_SELECTED_MODEL, fallback.displayName)
                        editor.putString(KEY_SELECTED_MODEL_CLI_ID, fallback.cliId)
                        editor.putString(KEY_THINKING_BUDGET, fallback.effort)
                    } else {
                        editor.remove(KEY_SELECTED_MODEL).remove(KEY_SELECTED_MODEL_CLI_ID).remove(KEY_THINKING_BUDGET)
                    }
                    editor.apply()
                    val fallbackMsg = if (fallback != null) {
                        context.getString(
                            R.string.model_switched_fallback,
                            currentSelected,
                            fallback.displayName
                        )
                    } else {
                        context.getString(R.string.model_switched_none, currentSelected)
                    }
                    addLog("Model", fallbackMsg)
                    Toast.makeText(context, fallbackMsg, Toast.LENGTH_SHORT).show()
                }
                if (quota?.modelsFetchFailed == true || (quota == null && tier == null)) {
                    mutableQuotaErrorMessage.value = context.getString(R.string.quota_models_load_failed)
                }
            } catch (e: Exception) {
                mutableQuotaErrorMessage.value = e.localizedMessage ?: "Network error fetching quota."
            } finally {
                mutableIsQuotaLoading.value = false
            }
        }
    }

    private val mutableShowModelSelector = MutableStateFlow(false)
    val showModelSelector: StateFlow<Boolean> = mutableShowModelSelector.asStateFlow()

    fun loadPreferences(context: Context) {
        settingsPreferencesDelegate.loadPreferences(
            context = context,
            onCliModelsDecoded = { mutableCliModels.value = it },
            onWorkspaceLoaded = { projectWorkspaceDelegate.setWorkspacePathDirect(it) }
        )
        refreshAuthState(context)
        refreshPermissions(context)
        refreshRules(context)
        refreshSkills(context)
        refreshPlugins(context)
        refreshMcpServers(context)
        loadProjects(context)
        loadConversationHistory(context)
        integrationsDelegate.onActiveWorkspaceChanged(context, workspacePath.value, viewModelScope)
    }

    fun setModel(context: Context, model: String, budget: String = thinkingBudget.value, cliId: String? = null) =
        settingsPreferencesDelegate.setModel(context, model, budget, cliId, mutableUserQuotaSummary.value, ::addLog)

    fun setThinkingBudget(context: Context, budget: String) =
        settingsPreferencesDelegate.setThinkingBudget(context, budget)

    fun setVerboseThinking(context: Context, verbose: Boolean) =
        settingsPreferencesDelegate.setVerboseThinking(context, verbose)

    fun setConversationWidth(context: Context, width: String) =
        settingsPreferencesDelegate.setConversationWidth(context, width)

    fun setThemeMode(context: Context, mode: ThemeMode) =
        settingsPreferencesDelegate.setThemeMode(context, mode, ::addLog)

    fun setAccentColor(context: Context, accent: AccentColor) =
        settingsPreferencesDelegate.setAccentColor(context, accent, ::addLog)

    fun setThemePreset(context: Context, preset: String) =
        settingsPreferencesDelegate.setThemePreset(context, preset, ::addLog)

    fun updateCustomThemeColor(context: Context, target: String, newHex: String, currentPreset: ThemePreset) =
        settingsPreferencesDelegate.updateCustomThemeColor(context, target, newHex, currentPreset, ::addLog)

    fun setEditorWordWrap(context: Context, wrap: Boolean) =
        settingsPreferencesDelegate.setEditorWordWrap(context, wrap)

    fun setEditorLineNumbers(context: Context, show: Boolean) =
        settingsPreferencesDelegate.setEditorLineNumbers(context, show)

    fun setEditorFontSize(context: Context, size: Int) =
        settingsPreferencesDelegate.setEditorFontSize(context, size)

    fun setSkipPermissions(context: Context, skip: Boolean) =
        settingsPreferencesDelegate.setSkipPermissions(context, skip)

    fun setQueuedMessagesMode(context: Context, mode: String) =
        settingsPreferencesDelegate.setQueuedMessagesMode(context, mode, ::addLog)

    fun setSecurityPreset(context: Context, preset: String) =
        settingsPreferencesDelegate.setSecurityPreset(context, preset, viewModelScope, ::addLog)

    fun setPlanReviewPolicy(context: Context, policy: String) =
        settingsPreferencesDelegate.setPlanReviewPolicy(context, policy, viewModelScope, ::addLog)

    fun setBrowserJsPolicy(context: Context, policy: String) =
        settingsPreferencesDelegate.setBrowserJsPolicy(context, policy, ::addLog)

    fun setEnableCreditOverages(context: Context, enabled: Boolean) =
        settingsPreferencesDelegate.setEnableCreditOverages(context, enabled, ::addLog)

    fun setCompletionNotificationEnabled(context: Context, enabled: Boolean) =
        settingsPreferencesDelegate.setCompletionNotificationEnabled(context, enabled, ::addLog)

    fun setCompletionBuzzType(context: Context, buzzType: String) =
        settingsPreferencesDelegate.setCompletionBuzzType(context, buzzType, ::addLog)

    fun setCompletionSoundType(context: Context, soundType: String) =
        settingsPreferencesDelegate.setCompletionSoundType(context, soundType, ::addLog)

    fun setCompletionCustomSound(context: Context, uri: String?, title: String) =
        settingsPreferencesDelegate.setCompletionCustomSound(context, uri, title, ::addLog)

    fun setVoiceLanguage(context: Context, language: id.vern.antigravity.voice.VoiceLanguage) =
        settingsPreferencesDelegate.setVoiceLanguage(context, language, ::addLog)

    fun setVoiceReplyInLanguage(context: Context, enabled: Boolean) =
        settingsPreferencesDelegate.setVoiceReplyInLanguage(context, enabled, ::addLog)

    fun setVoiceSpeechRate(context: Context, rate: Float) =
        settingsPreferencesDelegate.setVoiceSpeechRate(context, rate, ::addLog)

    fun setVoiceOnlyMode(context: Context, enabled: Boolean) =
        settingsPreferencesDelegate.setVoiceOnlyMode(context, enabled, ::addLog)

    fun testPromptCompletionAlert(context: Context) {
        id.vern.antigravity.service.PromptCompletionNotifier.notifyPromptCompleted(
            context = context,
            conversationId = activeConversationId.value ?: "test",
            prompt = "Test prompt execution",
            response = "Antigravity prompt completed successfully! Buzz and notification alert verified.",
            isSuccess = true,
            isTest = true
        )
    }

    fun refreshPermissions(context: Context) =
        settingsPreferencesDelegate.refreshPermissions(context, viewModelScope)

    fun addPermissionRule(context: Context, action: String, pattern: String, mode: String) =
        settingsPreferencesDelegate.addPermissionRule(context, action, pattern, mode, viewModelScope, ::addLog)

    fun updatePermissionRule(context: Context, action: String, oldPattern: String, newPattern: String, newMode: String) =
        settingsPreferencesDelegate.updatePermissionRule(context, action, oldPattern, newPattern, newMode, viewModelScope, ::addLog)

    fun removePermissionRule(context: Context, action: String, pattern: String) =
        settingsPreferencesDelegate.removePermissionRule(context, action, pattern, viewModelScope, ::addLog)

    fun refreshRules(context: Context) =
        settingsPreferencesDelegate.refreshRules(context, workspacePath.value, viewModelScope)

    fun saveRuleContent(context: Context, filePath: String, content: String, onComplete: ((Boolean) -> Unit)? = null) =
        settingsPreferencesDelegate.saveRuleContent(context, filePath, content, workspacePath.value, viewModelScope, ::addLog, onComplete)

    fun createRule(context: Context, ruleName: String, content: String, onComplete: ((Boolean) -> Unit)? = null) =
        settingsPreferencesDelegate.createRule(context, ruleName, content, workspacePath.value, viewModelScope, ::addLog, onComplete)

    fun deleteRule(context: Context, filePath: String, onComplete: ((Boolean) -> Unit)? = null) =
        settingsPreferencesDelegate.deleteRule(context, filePath, workspacePath.value, viewModelScope, ::addLog, onComplete)

    fun refreshMcpServers(context: Context) =
        settingsPreferencesDelegate.refreshMcpServers(context, viewModelScope)

    fun saveMcpServer(context: Context, server: McpServerItem, onComplete: ((Boolean) -> Unit)? = null) =
        settingsPreferencesDelegate.saveMcpServer(context, server, viewModelScope, ::addLog, onComplete)

    fun deleteMcpServer(context: Context, serverName: String, onComplete: ((Boolean) -> Unit)? = null) =
        settingsPreferencesDelegate.deleteMcpServer(context, serverName, viewModelScope, ::addLog, onComplete)

    suspend fun loadRawMcpConfig(context: Context): String =
        settingsPreferencesDelegate.loadRawMcpConfig(context)

    fun saveRawMcpConfig(context: Context, rawJson: String, onComplete: ((Boolean) -> Unit)? = null) =
        settingsPreferencesDelegate.saveRawMcpConfig(context, rawJson, viewModelScope, ::addLog, onComplete)

    fun refreshAuthState(context: Context) =
        authDelegate.refreshAuthState(context, viewModelScope, ::refreshUserQuota)

    fun switchAccount(context: Context, accountId: String) =
        authDelegate.switchAccount(context, accountId, viewModelScope) {
            mutableUserQuotaSummary.value = null
            mutableCodeAssistTier.value = null
            mutableCliModels.value = emptyList()
            refreshAuthState(context)
            CloudCodeQuotaService.restartLocalLanguageServer(context)
            restartCoreService(context)
            refreshUserQuota(context)
            refreshCliModels(context, force = true)
        }

    fun removeAccount(context: Context, accountId: String) =
        authDelegate.removeAccount(context, accountId, viewModelScope) {
            refreshAuthState(context)
            val currentActive = AccountManager.getActiveAccount(context)
            if (currentActive != null) {
                mutableUserQuotaSummary.value = null
                mutableCodeAssistTier.value = null
                mutableCliModels.value = emptyList()
                CloudCodeQuotaService.restartLocalLanguageServer(context)
                restartCoreService(context)
                refreshUserQuota(context)
                refreshCliModels(context, force = true)
            }
        }

    fun startGoogleSignIn(context: Context) =
        authDelegate.startGoogleSignIn(
            context = context,
            scope = viewModelScope,
            onSuccess = { email ->
                mutableUserQuotaSummary.value = null
                mutableCodeAssistTier.value = null
                mutableCliModels.value = emptyList()
                AgyModelsService.invalidateCache()
                refreshAuthState(context)
                CloudCodeQuotaService.restartLocalLanguageServer(context)
                restartCoreService(context)
                refreshCliModels(context, force = true)
                addLog("Auth", "Google sign-in successful: $email")
                Toast.makeText(context, context.getString(R.string.auth_signed_in_success, email ?: ""), Toast.LENGTH_SHORT).show()
                viewModelScope.launch {
                    delay(2500)
                    refreshUserQuota(context)
                }
            },
            onLog = ::addLog
        )

    fun onActivityResume(context: Context) =
        authDelegate.onActivityResume(
            context = context,
            scope = viewModelScope,
            onCheckUpdates = {
                checkForCoreEngineUpdate(it)
                checkForOtaUpdates(it)
            },
            onAccountChangeDetected = { email ->
                mutableUserQuotaSummary.value = null
                mutableCodeAssistTier.value = null
                mutableCliModels.value = emptyList()
                AgyModelsService.invalidateCache()
                CloudCodeQuotaService.restartLocalLanguageServer(context)
                restartCoreService(context)
                addLog("Auth", "Account switch detected on app resume: $email")
                refreshAuthState(context)
                refreshCliModels(context, force = true)
            }
        )

    fun exchangeManualAuthCode(context: Context, rawCodeOrToken: String) =
        authDelegate.exchangeManualAuthCode(
            context = context,
            rawCodeOrToken = rawCodeOrToken,
            scope = viewModelScope,
            onStdinSent = {
                viewModelScope.launch {
                    delay(1500)
                    OAuthManager.checkAndImportExistingToken(context)
                    refreshAuthState(context)
                    restartCoreService(context)
                }
            },
            onSuccess = { email ->
                mutableUserQuotaSummary.value = null
                mutableCodeAssistTier.value = null
                mutableCliModels.value = emptyList()
                AgyModelsService.invalidateCache()
                refreshAuthState(context)
                restartCoreService(context)
                refreshCliModels(context, force = true)
                addLog("Auth", "Google sign-in successful: $email")
                Toast.makeText(context, context.getString(R.string.auth_signed_in_success, email ?: ""), Toast.LENGTH_SHORT).show()
                viewModelScope.launch {
                    delay(2500)
                    refreshUserQuota(context)
                }
            },
            onLog = ::addLog
        )

    fun signOutGoogle(context: Context) =
        authDelegate.signOutGoogle(
            context = context,
            onSignedOut = {
                mutableUserQuotaSummary.value = null
                mutableCodeAssistTier.value = null
                mutableCliModels.value = emptyList()
                settingsPreferencesDelegate.setSelectedModelCliIdDirect(null)
                settingsPreferencesDelegate.setSelectedModelDirect("")
                settingsPreferencesDelegate.setThinkingBudgetDirect("")
                CloudCodeQuotaService.restartLocalLanguageServer(context)
                restartCoreService(context)
            },
            onLog = ::addLog
        )

    fun showGoogleSignInDialog() = authDelegate.showGoogleSignInDialog()
    fun dismissGoogleSignInDialog() = authDelegate.dismissGoogleSignInDialog()

    fun dismissModelLimitPrompt() {
        mutableModelLimitReachedInfo.value = null
    }

    fun switchGoogleAccountAndRetry(
        context: Context,
        accountId: String,
        retryFailedPrompt: Boolean = true
    ) {
        val pendingPrompt = mutableModelLimitReachedInfo.value?.failedPrompt
        mutableModelLimitReachedInfo.value = null
        switchAccount(context, accountId)
        if (retryFailedPrompt && !pendingPrompt.isNullOrBlank()) {
            viewModelScope.launch {
                delay(1200)
                sendMessage(context, pendingPrompt)
            }
        }
    }

    fun refreshGitHubState(context: Context) {
        authDelegate.refreshGitHubState(context)
        integrationsDelegate.refresh(context, workspacePath.value)
    }
    fun switchGitHubAccount(context: Context, login: String) =
        authDelegate.switchGitHubAccount(context, login, ::addLog)
    fun removeGitHubAccount(context: Context, login: String) {
        authDelegate.removeGitHubAccount(context, login, ::addLog)
        integrationsDelegate.refresh(context, workspacePath.value)
    }
    fun verifyGitHubConnection(context: Context, login: String? = null) =
        authDelegate.verifyGitHubConnection(context, viewModelScope, ::addLog, login)
    fun loadGitHubRepositories(context: Context, login: String? = null) =
        authDelegate.loadGitHubRepositories(context, viewModelScope, ::addLog, login)
    fun startGitHubDeviceFlow(context: Context) =
        authDelegate.startGitHubDeviceFlow(context, viewModelScope, ::addLog)
    fun cancelGitHubDeviceFlow() = authDelegate.cancelGitHubDeviceFlow()
    fun connectGitHubWithPat(context: Context, token: String) =
        authDelegate.connectGitHubWithPat(context, token, viewModelScope, ::addLog)
    fun disconnectGitHub(context: Context) {
        authDelegate.disconnectGitHub(context, ::addLog)
        integrationsDelegate.refresh(context, workspacePath.value)
    }

    fun loadProjects(context: Context) = projectWorkspaceDelegate.loadProjects(context, viewModelScope)
    fun saveProjects(context: Context, list: List<File>) = projectWorkspaceDelegate.saveProjects(context, list)
    fun addProject(context: Context, path: String): File = projectWorkspaceDelegate.addProject(context, path, ::addLog)
    fun removeProject(context: Context, path: String) {
        projectWorkspaceDelegate.removeProject(context, path, ::addLog)
        ProjectIntegrationsManager.removeProjectBindings(context, path)
        integrationsDelegate.refresh(context, workspacePath.value)
    }
    fun resolveTreeUriToFolder(context: Context, treeUri: Uri): File? = projectWorkspaceDelegate.resolveTreeUriToFolder(context, treeUri)
    fun addProjectFromTreeUri(context: Context, treeUri: Uri): File? = projectWorkspaceDelegate.addProjectFromTreeUri(context, treeUri, ::addLog)
    fun setProjectSortOrder(context: Context, order: ProjectSortOrder) = projectWorkspaceDelegate.setProjectSortOrder(context, order)
    fun createProjectInFolder(context: Context, name: String, parentDir: File): File? = projectWorkspaceDelegate.createProjectInFolder(context, name, parentDir, ::addLog)
    fun createProjectFolder(context: Context, name: String, targetBase: File? = null): File? = projectWorkspaceDelegate.createProjectFolder(context, name, targetBase, ::addLog)
    fun setWorkspacePath(context: Context, path: String) {
        projectWorkspaceDelegate.setWorkspacePath(context, path, ::addLog)
        integrationsDelegate.onActiveWorkspaceChanged(context, path, viewModelScope)
    }

    // ---- Per-Project GitHub & Cloud Integrations ----

    fun refreshIntegrationsState(context: Context, overrideTargetPath: String? = null) =
        integrationsDelegate.refresh(context, workspacePath.value, overrideTargetPath)

    fun setIntegrationTargetProject(context: Context, path: String) =
        integrationsDelegate.setTargetProjectPath(context, path, workspacePath.value)

    fun selectCloudAccount(provider: CloudProvider, accountId: String) =
        integrationsDelegate.selectCloudAccount(provider, accountId)

    fun addAndVerifyCloudAccount(context: Context, provider: CloudProvider, label: String, token: String) =
        integrationsDelegate.addAndVerifyCloudAccount(context, provider, label, token, workspacePath.value, viewModelScope, ::addLog)

    fun verifyCloudAccount(context: Context, provider: CloudProvider, accountId: String) =
        integrationsDelegate.verifyCloudAccount(context, provider, accountId, viewModelScope, ::addLog)

    fun removeCloudAccount(context: Context, provider: CloudProvider, accountId: String) =
        integrationsDelegate.removeCloudAccount(context, provider, accountId, workspacePath.value, viewModelScope, ::addLog)

    fun bindGitHubToProject(
        context: Context,
        projectPath: String,
        accountLogin: String,
        repoFullName: String = "",
        repoCloneUrl: String = ""
    ) = integrationsDelegate.bindGitHubToProject(
        context, projectPath, accountLogin, repoFullName, repoCloneUrl, workspacePath.value, ::addLog
    )

    fun unbindGitHubFromProject(context: Context, projectPath: String) =
        integrationsDelegate.unbindGitHubFromProject(context, projectPath, workspacePath.value, ::addLog)

    fun loadVercelResources(context: Context, accountId: String, teamId: String? = null) =
        integrationsDelegate.loadVercelResources(context, accountId, teamId, viewModelScope, ::addLog)

    fun loadVercelDeployments(context: Context, accountId: String, projectId: String, teamId: String?) =
        integrationsDelegate.loadVercelDeployments(context, accountId, projectId, teamId, viewModelScope, ::addLog)

    fun bindVercelToProject(
        context: Context,
        projectPath: String,
        accountId: String,
        project: VercelProjectItem,
        injectEnv: Boolean = true,
        syncMcp: Boolean = false
    ) = integrationsDelegate.bindVercelToProject(
        context, projectPath, accountId, project, injectEnv, syncMcp, workspacePath.value, viewModelScope, ::addLog
    )

    fun updateVercelIntegrationToggles(context: Context, projectPath: String, injectEnv: Boolean, syncMcp: Boolean) =
        integrationsDelegate.updateVercelToggles(context, projectPath, injectEnv, syncMcp, workspacePath.value, viewModelScope)

    fun pullVercelEnvToWorkspace(context: Context, projectPath: String, accountId: String, projectId: String, teamId: String?) =
        integrationsDelegate.pullVercelEnvToWorkspace(context, projectPath, accountId, projectId, teamId, viewModelScope, ::addLog)

    fun writeVercelProjectJson(context: Context, projectPath: String, orgId: String, projectId: String) =
        integrationsDelegate.writeVercelProjectJson(context, projectPath, orgId, projectId, ::addLog)

    fun unbindVercelFromProject(context: Context, projectPath: String) =
        integrationsDelegate.unbindVercelFromProject(context, projectPath, workspacePath.value, viewModelScope, ::addLog)

    fun loadSupabaseProjects(context: Context, accountId: String) =
        integrationsDelegate.loadSupabaseProjects(context, accountId, viewModelScope, ::addLog)

    fun bindSupabaseToProject(
        context: Context,
        projectPath: String,
        accountId: String,
        project: SupabaseProjectItem,
        injectEnv: Boolean = true,
        syncMcp: Boolean = true
    ) = integrationsDelegate.bindSupabaseToProject(
        context, projectPath, accountId, project, injectEnv, syncMcp, workspacePath.value, viewModelScope, ::addLog
    )

    fun updateSupabaseIntegrationToggles(context: Context, projectPath: String, injectEnv: Boolean, syncMcp: Boolean) =
        integrationsDelegate.updateSupabaseToggles(context, projectPath, injectEnv, syncMcp, workspacePath.value, viewModelScope)

    fun pullSupabaseEnvToWorkspace(
        context: Context,
        projectPath: String,
        accountId: String,
        projectRef: String,
        apiUrl: String,
        includeServiceRole: Boolean
    ) = integrationsDelegate.pullSupabaseEnvToWorkspace(
        context, projectPath, accountId, projectRef, apiUrl, includeServiceRole, viewModelScope, ::addLog
    )

    fun unbindSupabaseFromProject(context: Context, projectPath: String) =
        integrationsDelegate.unbindSupabaseFromProject(context, projectPath, workspacePath.value, viewModelScope, ::addLog)

    fun loadCloudflarePages(context: Context, accountId: String, cfAccountId: String? = null) =
        integrationsDelegate.loadCloudflarePages(context, accountId, cfAccountId, viewModelScope, ::addLog)

    fun loadCloudflarePagesDeployments(context: Context, accountId: String, cfAccountId: String, projectName: String) =
        integrationsDelegate.loadCloudflarePagesDeployments(context, accountId, cfAccountId, projectName, viewModelScope, ::addLog)

    fun bindCloudflarePagesToProject(
        context: Context,
        projectPath: String,
        accountId: String,
        cfAccountId: String,
        cfAccountName: String,
        project: CloudflarePagesProjectItem,
        injectEnv: Boolean = true,
        syncMcp: Boolean = false
    ) = integrationsDelegate.bindCloudflarePagesToProject(
        context, projectPath, accountId, cfAccountId, cfAccountName, project, injectEnv, syncMcp, workspacePath.value, viewModelScope, ::addLog
    )

    fun updateCloudflareIntegrationToggles(context: Context, projectPath: String, injectEnv: Boolean, syncMcp: Boolean) =
        integrationsDelegate.updateCloudflareToggles(context, projectPath, injectEnv, syncMcp, workspacePath.value, viewModelScope)

    fun unbindCloudflareFromProject(context: Context, projectPath: String) =
        integrationsDelegate.unbindCloudflareFromProject(context, projectPath, workspacePath.value, viewModelScope, ::addLog)

    fun refreshCloudCliStatus(context: Context) =
        integrationsDelegate.refreshCliStatus(context)

    fun installCloudProviderCli(context: Context, provider: CloudProvider) =
        integrationsDelegate.installProviderCli(context, provider, viewModelScope, ::addLog)

    fun triggerVercelRedeploy(context: Context, accountId: String, projectId: String, projectName: String, teamId: String?) =
        integrationsDelegate.triggerVercelRedeploy(context, accountId, projectId, projectName, teamId, viewModelScope, ::addLog)

    fun triggerCloudflarePagesDeploy(context: Context, accountId: String, cfAccountId: String, projectName: String) =
        integrationsDelegate.triggerCloudflarePagesDeploy(context, accountId, cfAccountId, projectName, viewModelScope, ::addLog)

    fun saveConversationHistory(context: Context) = conversationManagerDelegate.saveConversationHistory(context)
    fun saveLastActiveConversationId(context: Context, id: String?) = conversationManagerDelegate.saveLastActiveConversationId(context, id)
    fun saveMessagesForConversation(context: Context, conversationId: String, messages: List<ChatMessage>) =
        conversationManagerDelegate.saveMessagesForConversation(context, conversationId, messages, viewModelScope)
    fun loadMessagesForConversation(context: Context, conversationId: String): List<ChatMessage> =
        conversationManagerDelegate.loadMessagesForConversation(context, conversationId)
    fun exportChatsBackup(context: Context): Int =
        ConversationPersistence.exportChatsToDirectory(context)
    fun restoreChatsBackup(context: Context): Int {
        val count = ConversationPersistence.importChatsFromDirectory(context)
        if (count > 0) {
            loadConversationHistory(context)
        }
        return count
    }
    fun loadConversationHistory(context: Context) =
        conversationManagerDelegate.loadConversationHistory(
            context = context,
            scope = viewModelScope,
            onWorkspaceChange = { setWorkspacePath(context, it) },
            onMessagesLoaded = { loaded ->
                val cid = activeConversationId.value
                if (cid != null) {
                    if (isConversationExecuting(cid)) {
                        mutableChatMessages.value = messagesByConv[cid].orEmpty()
                    } else {
                        messagesByConv[cid] = loaded
                        mutableChatMessages.value = loaded
                    }
                } else {
                    mutableChatMessages.value = loaded
                }
                syncForegroundExecutionState()
            }
        )
    fun startNewConversation(context: Context? = null, projectPath: String? = workspacePath.value) {
        val prevId = activeConversationId.value
        if (prevId != null && !isConversationExecuting(prevId)) {
            messagesByConv[prevId] = mutableChatMessages.value
        }
        conversationManagerDelegate.startNewConversation(
            context = context,
            projectPath = projectPath,
            currentMessages = mutableChatMessages.value,
            scope = viewModelScope,
            onWorkspaceChange = { if (context != null) setWorkspacePath(context, it) else projectWorkspaceDelegate.setWorkspacePathDirect(it) },
            onClearState = {
                mutableChatMessages.value = emptyList()
                mutableAttachedFiles.value = emptyList()
            }
        )
        syncForegroundExecutionState()
    }
    fun loadConversation(context: Context, summary: ConversationSummary) {
        val prevId = activeConversationId.value
        if (prevId != null && !isConversationExecuting(prevId)) {
            messagesByConv[prevId] = mutableChatMessages.value
        }
        val cachedLive = if (isConversationExecuting(summary.id)) messagesByConv[summary.id] else null
        conversationManagerDelegate.loadConversation(
            context = context,
            summary = summary,
            currentMessages = mutableChatMessages.value,
            scope = viewModelScope,
            onWorkspaceChange = { setWorkspacePath(context, it) },
            onMessagesLoaded = { loaded ->
                if (activeConversationId.value == summary.id) {
                    if (isConversationExecuting(summary.id)) {
                        mutableChatMessages.value = messagesByConv[summary.id] ?: loaded
                    } else {
                        messagesByConv[summary.id] = loaded
                        mutableChatMessages.value = loaded
                    }
                } else {
                    messagesByConv.putIfAbsent(summary.id, loaded)
                }
            },
            onLog = ::addLog
        )
        if (cachedLive != null) {
            mutableChatMessages.value = cachedLive
        }
        syncForegroundExecutionState()
    }
    fun loadConversation(summary: ConversationSummary) {
        conversationManagerDelegate.loadConversation(
            summary = summary,
            onWorkspaceChange = { projectWorkspaceDelegate.setWorkspacePathDirect(it) },
            onLog = ::addLog
        )
        val cid = summary.id
        if (messagesByConv.containsKey(cid)) {
            mutableChatMessages.value = messagesByConv[cid].orEmpty()
        }
        syncForegroundExecutionState()
    }
    fun renameConversation(newTitle: String, summaryId: String? = null, context: Context? = null) =
        conversationManagerDelegate.renameConversation(
            newTitle = newTitle,
            summaryId = summaryId,
            context = context,
            currentMessageCount = mutableChatMessages.value.size,
            currentWorkspacePath = workspacePath.value,
            onLog = ::addLog
        )
    fun togglePinConversation(summaryId: String? = null, context: Context? = null): Boolean =
        conversationManagerDelegate.togglePinConversation(summaryId, context, ::addLog)
    fun deleteConversation(id: String) {
        if (isConversationExecuting(id)) {
            stopAiExecution(id)
        }
        messagesByConv.remove(id)
        queuedMessagesByConv.update { it - id }
        runningTasksByConv.update { it - id }
        workingStatusByConv.update { it - id }
        conversationManagerDelegate.deleteConversation(
            context = null,
            id = id,
            onNextSummaryLoaded = { loadConversation(it) },
            onCleared = {
                mutableChatMessages.value = emptyList()
                mutableAttachedFiles.value = emptyList()
            },
            onLog = ::addLog
        )
        syncForegroundExecutionState()
    }
    fun deleteConversation(context: Context, id: String) {
        if (isConversationExecuting(id)) {
            stopAiExecution(id)
        }
        messagesByConv.remove(id)
        queuedMessagesByConv.update { it - id }
        runningTasksByConv.update { it - id }
        workingStatusByConv.update { it - id }
        conversationManagerDelegate.deleteConversation(
            context = context,
            id = id,
            onNextSummaryLoaded = { loadConversation(context, it) },
            onCleared = {
                mutableChatMessages.value = emptyList()
                mutableAttachedFiles.value = emptyList()
            },
            onLog = ::addLog
        )
        syncForegroundExecutionState()
    }
    fun deleteCurrentConversation(context: Context) {
        val currentId = activeConversationId.value
        if (currentId != null && isConversationExecuting(currentId)) {
            stopAiExecution(currentId)
        }
        if (currentId != null) {
            messagesByConv.remove(currentId)
            queuedMessagesByConv.update { it - currentId }
            runningTasksByConv.update { it - currentId }
            workingStatusByConv.update { it - currentId }
        }
        conversationManagerDelegate.deleteCurrentConversation(
            context = context,
            onNextSummaryLoaded = { loadConversation(context, it) },
            onCleared = {
                mutableChatMessages.value = emptyList()
                mutableAttachedFiles.value = emptyList()
            },
            onLog = ::addLog
        )
        syncForegroundExecutionState()
    }
    fun archiveConversation(context: Context? = null) =
        conversationManagerDelegate.archiveConversation(context, mutableChatMessages.value, workspacePath.value)

    fun clearRepoInspection() = customizationInstallerDelegate.clearRepoInspection()
    fun toggleRepoSkillSelection(skillId: String) = customizationInstallerDelegate.toggleRepoSkillSelection(skillId)
    fun setAllRepoSkillsSelection(selectAll: Boolean) = customizationInstallerDelegate.setAllRepoSkillsSelection(selectAll)
    fun normalizeGitRepoUrl(rawUrl: String): Pair<String, String?> = customizationInstallerDelegate.normalizeGitRepoUrl(rawUrl)
    fun inspectRepoForSkills(context: Context, rawRepoUrl: String) = customizationInstallerDelegate.inspectRepoForSkills(context, rawRepoUrl, viewModelScope)
    fun installSkillViaTerminal(context: Context, skillItem: SkillCatalogueItem) =
        customizationInstallerDelegate.installSkillViaTerminal(context, skillItem, getBuiltinSkillsDir(context), viewModelScope, ::fetchLatestRepoCommitSha, ::registerTrackedCustomization, ::refreshSkills)
    fun installPluginViaTerminal(context: Context, pluginItem: SkillCatalogueItem) =
        customizationInstallerDelegate.installPluginViaTerminal(context, pluginItem, viewModelScope, ::fetchLatestRepoCommitSha, ::registerTrackedCustomization, ::refreshPlugins)
    fun updateCustomizationViaTerminal(context: Context, track: CustomizationRepoTrack) =
        customizationInstallerDelegate.updateCustomizationViaTerminal(context, track, viewModelScope, ::getTrackedCustomizations, ::saveTrackedCustomizations, ::refreshSkills, ::refreshPlugins)
    fun installCustomFromRepoViaTerminal(context: Context, rawRepoUrl: String, customName: String, subPath: String?, isPlugin: Boolean) =
        customizationInstallerDelegate.installCustomFromRepoViaTerminal(context, rawRepoUrl, customName, subPath, isPlugin, viewModelScope, ::fetchLatestRepoCommitSha, ::registerTrackedCustomization, ::refreshSkills, ::refreshPlugins)
    fun installSelectedRepoSkillsViaTerminal(context: Context, rawRepoUrl: String, selectedSkills: List<DiscoveredRepoSkill>) =
        customizationInstallerDelegate.installSelectedRepoSkillsViaTerminal(context, rawRepoUrl, selectedSkills, viewModelScope, ::fetchLatestRepoCommitSha, ::registerTrackedCustomization, ::refreshSkills)
    fun dismissInstallProgress() = customizationInstallerDelegate.dismissInstallProgress()

    fun getTrackedCustomizations(context: Context): Map<String, CustomizationRepoTrack> = customizationCatalogueDelegate.getTrackedCustomizations(context)
    fun saveTrackedCustomizations(context: Context, tracks: Map<String, CustomizationRepoTrack>) = customizationCatalogueDelegate.saveTrackedCustomizations(context, tracks)
    fun registerTrackedCustomization(context: Context, track: CustomizationRepoTrack) = customizationCatalogueDelegate.registerTrackedCustomization(context, track)
    fun unregisterTrackedCustomization(context: Context, id: String) = customizationCatalogueDelegate.unregisterTrackedCustomization(context, id)
    fun loadTrackedCustomizations(context: Context) = customizationCatalogueDelegate.loadTrackedCustomizations(context)
    fun fetchLatestRepoCommitSha(repoUrl: String, subPath: String? = null): String? = customizationCatalogueDelegate.fetchLatestRepoCommitSha(repoUrl, subPath)
    fun seedCatalogueTrackingIfInstalled(context: Context) = customizationCatalogueDelegate.seedCatalogueTrackingIfInstalled(context)
    fun checkForCustomizationUpdates(context: Context, force: Boolean = false) = customizationCatalogueDelegate.checkForCustomizationUpdates(context, viewModelScope, force)
    fun getGeminiConfigDir(context: Context): File = customizationCatalogueDelegate.getGeminiConfigDir(context)
    fun syncConfigBidirectional(context: Context) = customizationCatalogueDelegate.syncConfigBidirectional(context)
    fun syncConfigToRoot(context: Context) = customizationCatalogueDelegate.syncConfigToRoot(context)
    fun getBuiltinSkillsDir(context: Context): File = customizationCatalogueDelegate.getBuiltinSkillsDir(context)
    fun getInstalledSkills(context: Context): List<CustomizationItem> = customizationCatalogueDelegate.getInstalledSkills(context, workspacePath.value)
    fun refreshSkills(context: Context) = customizationCatalogueDelegate.refreshSkills(context, viewModelScope, workspacePath.value)
    fun installSkill(context: Context, name: String, description: String, content: String): Boolean = customizationCatalogueDelegate.installSkill(context, name, description, content, viewModelScope, ::addLog)
    fun uninstallSkill(context: Context, name: String): Boolean = customizationCatalogueDelegate.uninstallSkill(context, name, viewModelScope, ::addLog)
    fun getInstalledPlugins(context: Context): List<CustomizationItem> = customizationCatalogueDelegate.getInstalledPlugins(context)
    fun refreshPlugins(context: Context) = customizationCatalogueDelegate.refreshPlugins(context, viewModelScope)
    fun installPlugin(context: Context, name: String, description: String): Boolean = customizationCatalogueDelegate.installPlugin(context, name, description, viewModelScope, ::addLog)
    fun uninstallPlugin(context: Context, name: String): Boolean = customizationCatalogueDelegate.uninstallPlugin(context, name, viewModelScope, ::addLog)
    fun getAvailableSkillsCatalogue(): List<SkillCatalogueItem> = customizationCatalogueDelegate.getAvailableSkillsCatalogue()
    fun getDefaultSkillsCatalogue(): List<SkillCatalogueItem> = customizationCatalogueDelegate.getDefaultSkillsCatalogue()
    fun getAvailablePluginsCatalogue(): List<SkillCatalogueItem> = customizationCatalogueDelegate.getAvailablePluginsCatalogue()
    fun getDefaultPluginsCatalogue(): List<SkillCatalogueItem> = customizationCatalogueDelegate.getDefaultPluginsCatalogue()
    fun loadCachedCatalogue(context: Context) = customizationCatalogueDelegate.loadCachedCatalogue(context, viewModelScope)
    fun fetchRemoteCatalogue(context: Context, force: Boolean = false) = customizationCatalogueDelegate.fetchRemoteCatalogue(context, viewModelScope, force, ::addLog)
    fun getInstalledRules(context: Context): List<Pair<String, String>> = customizationCatalogueDelegate.getInstalledRules(context)
    fun installPluginFromCatalogueAsync(context: Context, pluginId: String, onComplete: (Boolean) -> Unit) = customizationCatalogueDelegate.installPluginFromCatalogueAsync(context, pluginId, viewModelScope, ::addLog, onComplete)
    fun installSkillFromCatalogueAsync(context: Context, skillId: String, onComplete: (Boolean) -> Unit) = customizationCatalogueDelegate.installSkillFromCatalogueAsync(context, skillId, viewModelScope, ::addLog, onComplete)
    fun installSkillFromCatalogue(context: Context, skillId: String): Boolean {
        installSkillFromCatalogueAsync(context, skillId) {}
        return true
    }
    fun installPluginFromCatalogue(context: Context, pluginId: String): Boolean {
        installPluginFromCatalogueAsync(context, pluginId) {}
        return true
    }
    fun calculateTokenUsage(context: Context): TokenUsageStats = customizationCatalogueDelegate.calculateTokenUsage(context)
    private val mutableOpenTerminalRequest = MutableStateFlow<String?>(null)
    val openTerminalRequest: StateFlow<String?> = mutableOpenTerminalRequest.asStateFlow()

    fun requestOpenTerminal(cmd: String? = null) {
        mutableOpenTerminalRequest.value = cmd ?: ""
    }

    fun consumeOpenTerminalRequest() {
        mutableOpenTerminalRequest.value = null
    }

    val coreUpdateState = MutableStateFlow(CoreEngineUpdateState())

    val otaState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)

    fun checkForOtaUpdates(context: Context, isManualCheck: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val newState = CoreUpdateDelegate.checkForOtaUpdates(context, isManualCheck)
            if (newState != null) {
                otaState.value = newState
            }
        }
    }

    fun startOtaDownload(context: Context, info: UpdateChecker.UpdateInfo) {
        otaState.value = AppUpdateState.Downloading(0)
        viewModelScope.launch(Dispatchers.IO) {
            CoreUpdateDelegate.startOtaDownload(context, info) { state ->
                otaState.value = state
            }
        }
    }

    fun dismissOtaUpdate() {
        otaState.value = AppUpdateState.Idle
    }

    fun checkForCoreEngineUpdate(context: Context, force: Boolean = false) {
        val current = CoreUpdateManager.getCurrentVersion(context)
        val hasBackup = CoreUpdateManager.hasBackup(context)
        coreUpdateState.value = coreUpdateState.value.copy(
            isChecking = true,
            currentVersion = current,
            hasBackup = hasBackup,
            error = null
        )
        viewModelScope.launch(Dispatchers.IO) {
            val updated = CoreUpdateDelegate.checkForCoreEngineUpdate(context, force, coreUpdateState.value)
            if (updated != null) {
                coreUpdateState.value = updated
            } else {
                coreUpdateState.value = coreUpdateState.value.copy(isChecking = false)
            }
        }
    }

    fun rollbackCoreEngine(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = CoreUpdateDelegate.rollbackCoreEngine(context)
            if (result.isSuccess) {
                val ver = result.getOrNull() ?: CoreUpdateManager.getCurrentVersion(context)
                coreUpdateState.value = coreUpdateState.value.copy(
                    currentVersion = ver,
                    hasBackup = CoreUpdateManager.hasBackup(context),
                    isUpdateAvailable = coreUpdateState.value.latestVersion?.let { CoreUpdateManager.isVersionNewer(it, ver) } ?: false,
                    error = null
                )
                if (engineStatus.value.isRunning) {
                    restartCoreService(context)
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.core_update_rollback_success, ver), Toast.LENGTH_SHORT).show()
                }
            } else {
                val err = result.exceptionOrNull()?.message ?: "Unknown error"
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.core_update_rollback_failed, err), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun applyCoreEngineUpdate(context: Context) {
        val state = coreUpdateState.value
        val latestVer = state.latestVersion ?: return
        val url = state.downloadUrl ?: return

        val initialLines = listOf(
            TerminalLine(text = "$ " + context.getString(R.string.core_update_cmd_title), isCommand = true),
            TerminalLine(text = context.getString(R.string.core_update_checking))
        )
        customizationInstallerDelegate.setActiveInstallProgressDirect(CustomizationInstallProgress(
            title = context.getString(R.string.core_update_dialog_title),
            command = "agy-upgrade core ($latestVer)",
            lines = initialLines,
            isRunning = true
        ))

        viewModelScope.launch(Dispatchers.IO) {
            val outputLines = mutableListOf<TerminalLine>().apply { addAll(initialLines) }
            CoreUpdateDelegate.applyCoreEngineUpdate(
                context = context,
                state = state,
                onOutputLine = { line ->
                    synchronized(outputLines) { outputLines.add(line) }
                    customizationInstallerDelegate.setActiveInstallProgressDirect(activeInstallProgress.value?.copy(
                        lines = outputLines.toList()
                    ))
                },
                onSuccess = { newVer ->
                    coreUpdateState.value = coreUpdateState.value.copy(
                        currentVersion = newVer,
                        isUpdateAvailable = false,
                        latestVersion = newVer,
                        hasBackup = CoreUpdateManager.hasBackup(context)
                    )
                    customizationInstallerDelegate.setActiveInstallProgressDirect(activeInstallProgress.value?.copy(
                        lines = outputLines.toList(),
                        isRunning = false,
                        isSuccess = true
                    ))
                    if (engineStatus.value.isRunning) {
                        restartCoreService(context)
                    }
                },
                onError = { err ->
                    customizationInstallerDelegate.setActiveInstallProgressDirect(activeInstallProgress.value?.copy(
                        lines = outputLines.toList(),
                        isRunning = false,
                        isSuccess = false
                    ))
                }
            )
        }
    }

    fun bootEngine(context: Context, onReady: () -> Unit) {
        viewModelScope.launch {
            mutableIsBooting.value = true
            mutableBootError.value = null
            mutableSetupProgress.value = 0.05f
            loadPreferences(context)

            mutableBootStatusText.value = "Checking Linux userspace bootstrap..."
            addLog("Boot", "Checking Linux userspace installation...")

            val installOk = BootstrapInstaller.installIfNeeded(context) { step, progress ->
                mutableBootStatusText.value = step
                mutableSetupProgress.value = progress
                addLog("Setup", step)
            }

            if (!installOk) {
                val detail = BootstrapInstaller.lastStackTrace
                    ?: BootstrapInstaller.lastError
                    ?: "Failed to extract bootstrap. Please ensure device internal storage is sufficient."
                mutableBootError.value = BootstrapInstaller.lastError ?: "Failed to extract Linux bootstrap & engine."
                mutableBootErrorDetail.value = detail
                addLog("Boot", "Bootstrap installation failed: $detail", isError = true)
                mutableIsBooting.value = false
                return@launch
            }

            mutableSetupProgress.value = 0.90f
            mutableBootStatusText.value = "Starting Antigravity Core Service..."
            addLog("Boot", "Launching CoreServerService (port 45158 & 45161)...")
            startCoreService(context)

            delay(1000)
            mutableSetupProgress.value = 1.0f
            mutableBootStatusText.value = "Antigravity ready"
            addLog("Boot", "Antigravity ready")
            mutableIsBooting.value = false
            onReady()
        }
    }

    fun startCoreService(context: Context) {
        CoreServerService.startService(context)
        mutableEngineStatus.update { it.copy(isRunning = true) }
        addLog("Engine", "Core Server started")
    }

    fun stopCoreService(context: Context) {
        CoreServerService.stopService(context)
        mutableEngineStatus.update { it.copy(isRunning = false) }
        addLog("Engine", "Core Server stopped")
    }

    fun restartCoreService(context: Context) {
        viewModelScope.launch {
            addLog("Engine", "Restarting Core Server...")
            stopCoreService(context)
            delay(500)
            startCoreService(context)
        }
    }

    fun addLog(tag: String, message: String, isError: Boolean = false) {
        AppLogger.log(tag, message, isError)
    }

    fun clearLogs() {
        mutableExecutionLogs.value = emptyList()
    }

    fun copyLogsToClipboard(context: Context) {
        val logs = mutableExecutionLogs.value
        val logText = if (logs.isEmpty()) {
            context.getString(R.string.settings_logs_empty)
        } else {
            logs.joinToString("\n") { "[${it.formattedTime}] [${it.tag}] ${it.message}" }
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Terminal Logs", logText)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, context.getString(R.string.splash_logs_copied), Toast.LENGTH_SHORT).show()
    }

    fun exportLogsToFile(context: Context) {
        viewModelScope.launch {
            try {
                val logs = mutableExecutionLogs.value
                val logDir = File(context.filesDir, "logs").apply { mkdirs() }
                val logFile = File(logDir, "execution_${System.currentTimeMillis()}.log")
                val logText = logs.joinToString("\n") { "[${it.formattedTime}] [${it.tag}] ${it.message}" }
                logFile.writeText(logText, Charsets.UTF_8)
                Toast.makeText(context, "Logs exported: ${logFile.name}", Toast.LENGTH_SHORT).show()
                addLog("Log", "Exported logs to ${logFile.absolutePath}")
            } catch (e: Exception) {
                addLog("Log", "Failed to export logs: ${e.message}", isError = true)
            }
        }
    }

    fun addAttachment(context: Context, uri: Uri): Boolean {
        if (mutableAttachedFiles.value.size >= 5) {
            return false
        }
        try {
            var name = "file_${System.currentTimeMillis()}"
            var size = 0L
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex != -1) name = it.getString(nameIndex)
                    if (sizeIndex != -1) size = it.getLong(sizeIndex)
                }
            }
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

            val uploadsDir = File(context.filesDir, "uploads").apply { mkdirs() }
            uploadsDir.setReadable(true, false)
            uploadsDir.setExecutable(true, false)
            try { android.system.Os.chmod(uploadsDir.absolutePath, 493) } catch (e: Exception) {}

            val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val targetFile = File(uploadsDir, "${System.currentTimeMillis()}_$safeName")
            context.contentResolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output -> input.copyTo(output) }
            }
            targetFile.setReadable(true, false)
            try { android.system.Os.chmod(targetFile.absolutePath, 420) } catch (e: Exception) {}

            val file = AttachedFile(
                id = UUID.randomUUID().toString(),
                uri = uri,
                name = name,
                sizeBytes = if (size > 0L) size else targetFile.length(),
                mimeType = mimeType,
                localPath = targetFile.absolutePath
            )
            mutableAttachedFiles.update { if (it.size < 5) it + file else it }
            addLog("Chat", "Attached file: $name (${file.formattedSize})")
            return true
        } catch (e: Exception) {
            addLog("Chat", "Error attaching file: ${e.message}", isError = true)
            return false
        }
    }

    fun removeAttachment(file: AttachedFile) {
        file.localPath?.let { path ->
            runCatching {
                val f = File(path)
                if (f.exists() && f.parentFile?.name == "uploads") {
                    f.delete()
                }
            }
        }
        mutableAttachedFiles.update { it.filter { item -> item.id != file.id } }
    }

    fun showModelSelector() {
        mutableShowModelSelector.value = true
    }

    fun dismissModelSelector() {
        mutableShowModelSelector.value = false
    }

    fun queueMessage(content: String, attachments: List<AttachedFile> = emptyList(), convId: String? = activeConversationId.value) {
        if (content.isBlank() && attachments.isEmpty()) return
        val targetConvId = convId ?: return
        val item = QueuedMessage(
            content = content.trim(),
            attachments = attachments
        )
        queuedMessagesByConv.update { map ->
            val current = map[targetConvId].orEmpty()
            map + (targetConvId to (current + item))
        }
        syncForegroundExecutionState()
    }

    fun addRunningTask(name: String, command: String, convId: String? = activeConversationId.value): RunningTask {
        val task = RunningTask(name = name, command = command)
        val targetConvId = convId
        if (targetConvId != null) {
            runningTasksByConv.update { map ->
                map + (targetConvId to (map[targetConvId].orEmpty() + task))
            }
            syncForegroundExecutionState()
        } else {
            mutableRunningTasks.update { it + task }
        }
        return task
    }

    fun removeRunningTask(id: String, convId: String? = activeConversationId.value) {
        if (convId != null) {
            runningTasksByConv.update { map ->
                val updated = map[convId].orEmpty().filter { it.id != id }
                if (updated.isEmpty()) map - convId else map + (convId to updated)
            }
            syncForegroundExecutionState()
        } else {
            mutableRunningTasks.update { list -> list.filter { it.id != id } }
        }
    }

    fun stopRunningTask(id: String, convId: String? = activeConversationId.value) {
        removeRunningTask(id, convId)
        val remaining = if (convId != null) runningTasksByConv.value[convId].orEmpty() else mutableRunningTasks.value
        if (remaining.isEmpty()) {
            stopAiExecution(convId)
        }
    }

    fun addRunningSubagent(subagent: RunningSubagent) {
        mutableRunningSubagents.update { list ->
            val idx = list.indexOfFirst { it.id == subagent.id || (it.conversationId != null && it.conversationId == subagent.conversationId) }
            if (idx >= 0) {
                list.toMutableList().apply { set(idx, subagent) }
            } else {
                list + subagent
            }
        }
    }

    fun completeSubagent(convIdOrId: String) {
        mutableRunningSubagents.update { list ->
            list.map { if (it.conversationId == convIdOrId || it.id == convIdOrId) it.copy(isRunning = false) else it }
        }
    }

    fun removeRunningSubagent(id: String) {
        mutableRunningSubagents.update { list -> list.filter { it.id != id && it.conversationId != id } }
    }

    fun stopSubagent(id: String) {
        removeRunningSubagent(id)
    }

    fun loadSubagentMessages(context: Context, convId: String?): List<ChatMessage> {
        if (convId.isNullOrBlank()) return emptyList()
        val persisted = ConversationPersistence.loadMessagesForConversation(context, convId)
        if (persisted.isNotEmpty()) return persisted
        return TranscriptTurnParser.parseTranscriptToMessages(context, convId)
    }

    fun openSubagentAsActiveConversation(context: Context, subagent: RunningSubagent) {
        val convId = subagent.conversationId ?: subagent.id
        val title = subagent.role.ifEmpty { subagent.typeName.ifEmpty { context.getString(R.string.subagent_default_title) } }
        val summary = ConversationSummary(
            id = convId,
            title = title,
            timestamp = System.currentTimeMillis(),
            messageCount = 1,
            isPinned = false,
            projectPath = workspacePath.value
        )
        viewModelScope.launch(Dispatchers.IO) {
            val history = ConversationPersistence.loadConversationHistory(context)
            if (history.none { it.id == convId }) {
                ConversationPersistence.saveConversationHistory(context, listOf(summary) + history)
            }
            withContext(Dispatchers.Main) {
                loadConversation(context, summary)
            }
        }
    }

    fun loadDaemonTasks(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val tasks = mutableListOf<DaemonTask>()
            val candidates = listOf(
                File(context.filesDir, ".gemini/antigravity-app/daemon/tasks.json"),
                File(context.filesDir, "home/.gemini/antigravity-app/daemon/tasks.json")
            )
            for (file in candidates) {
                if (file.exists() && file.isFile) {
                    try {
                        val text = file.readText()
                        val array = org.json.JSONArray(text)
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            tasks.add(
                                DaemonTask(
                                    id = obj.optString("id", "task_$i"),
                                    name = obj.optString("name", obj.optString("id", "Task")),
                                    command = obj.optString("command", obj.optString("prompt", "")),
                                    triggerType = obj.optString("trigger_type", "interval"),
                                    intervalSec = obj.optInt("interval", 0),
                                    cron = obj.optString("cron", ""),
                                    enabled = obj.optBoolean("enabled", true),
                                    lastStatus = obj.optString("last_status", "idle")
                                )
                            )
                        }
                        break
                    } catch (e: Exception) {

                    }
                }
            }
            mutableDaemonTasks.value = tasks
        }
    }

    private fun daemonTaskFiles(context: Context): List<File> = listOf(
        File(context.filesDir, "home/.gemini/antigravity-app/daemon/tasks.json"),
        File(context.filesDir, ".gemini/antigravity-app/daemon/tasks.json")
    )

    private fun readDaemonTasksJsonArray(context: Context): org.json.JSONArray {
        val existing = daemonTaskFiles(context).firstOrNull { it.exists() && it.isFile }
        if (existing != null) {
            try {
                val raw = existing.readText().trim()
                if (raw.startsWith("[")) return org.json.JSONArray(raw)
            } catch (_: Exception) {}
        }
        return org.json.JSONArray()
    }

    private fun writeDaemonTasksJsonArray(context: Context, array: org.json.JSONArray) {
        val formatted = array.toString(2)
        for (target in daemonTaskFiles(context)) {
            try {
                target.parentFile?.mkdirs()
                target.writeText(formatted, Charsets.UTF_8)
                target.setReadable(true, false)
                target.setWritable(true, false)
            } catch (_: Exception) {}
        }
    }

    fun toggleDaemonTask(context: Context, taskId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val arr = readDaemonTasksJsonArray(context)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                if (obj.optString("id") == taskId) {
                    obj.put("enabled", !obj.optBoolean("enabled", true))
                    break
                }
            }
            writeDaemonTasksJsonArray(context, arr)
            loadDaemonTasks(context)
        }
    }

    fun deleteDaemonTask(context: Context, taskId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val arr = readDaemonTasksJsonArray(context)
            val next = org.json.JSONArray()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                if (obj.optString("id") != taskId) next.put(obj)
            }
            writeDaemonTasksJsonArray(context, next)
            loadDaemonTasks(context)
        }
    }

    fun addDaemonTask(
        context: Context,
        name: String,
        payload: String,
        isAiPrompt: Boolean,
        triggerType: String,
        intervalSec: Int = 3600,
        cron: String = ""
    ) {
        val cleanName = name.trim()
        val cleanPayload = payload.trim()
        if (cleanName.isEmpty() || cleanPayload.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val arr = readDaemonTasksJsonArray(context)
            val obj = org.json.JSONObject().apply {
                put("id", "task_${System.currentTimeMillis()}")
                put("name", cleanName)
                if (isAiPrompt) {
                    put("prompt", cleanPayload)
                } else {
                    put("command", cleanPayload)
                }
                put("trigger_type", triggerType)
                if (triggerType == "cron" && cron.isNotBlank()) {
                    put("cron", cron.trim())
                } else {
                    put("interval", intervalSec.coerceAtLeast(10))
                }
                val ws = workspacePath.value
                if (ws.isNotBlank()) put("project", ws)
                put("enabled", true)
                put("last_status", "idle")
            }
            arr.put(obj)
            writeDaemonTasksJsonArray(context, arr)
            loadDaemonTasks(context)
        }
    }

    fun removeQueuedMessage(id: String, convId: String? = activeConversationId.value) {
        val targetConvId = convId ?: return
        queuedMessagesByConv.update { map ->
            val updated = map[targetConvId].orEmpty().filter { it.id != id }
            if (updated.isEmpty()) map - targetConvId else map + (targetConvId to updated)
        }
        syncForegroundExecutionState()
    }

    fun editQueuedMessage(id: String, convId: String? = activeConversationId.value): QueuedMessage? {
        val targetConvId = convId ?: return null
        val item = queuedMessagesByConv.value[targetConvId].orEmpty().find { it.id == id } ?: return null
        removeQueuedMessage(id, targetConvId)
        return item
    }

    fun processQueuedMessageNow(context: Context, id: String, convId: String? = activeConversationId.value) {
        val targetConvId = convId ?: return
        val item = queuedMessagesByConv.value[targetConvId].orEmpty().find { it.id == id } ?: return
        removeQueuedMessage(id, targetConvId)
        if (isConversationExecuting(targetConvId)) {
            stopAiExecution(targetConvId)
        }
        sendMessageForConversation(context, targetConvId, item.content, item.attachments)
    }

    fun processNextQueuedMessage(context: Context, convId: String? = activeConversationId.value) {
        val targetConvId = convId ?: return
        if (isConversationExecuting(targetConvId)) return
        val next = queuedMessagesByConv.value[targetConvId].orEmpty().firstOrNull() ?: return
        removeQueuedMessage(next.id, targetConvId)
        sendMessageForConversation(context, targetConvId, next.content, next.attachments)
    }

    fun getWorkspaceFiles(query: String = ""): List<String> {
        val rootPath = workspacePath.value
        val rootDir = if (!rootPath.isNullOrEmpty()) File(rootPath) else null
        val result = mutableListOf<String>()

        if (rootDir != null && rootDir.exists() && rootDir.isDirectory) {
            try {
                fun scan(dir: File, depth: Int) {
                    if (depth > 4 || result.size > 50) return
                    dir.listFiles()?.forEach { file ->
                        if (file.name.startsWith(".") || file.name == "build" || file.name == "node_modules" || file.name == ".git") return@forEach
                        val relPath = file.relativeTo(rootDir).path
                        if (query.isBlank() || relPath.contains(query, ignoreCase = true) || file.name.contains(query, ignoreCase = true)) {
                            result.add(relPath)
                        }
                        if (file.isDirectory) scan(file, depth + 1)
                    }
                }
                scan(rootDir, 0)
            } catch (e: Exception) {}
        }
        if (result.isEmpty()) {
            listOf("README.md", "build.gradle.kts", "AndroidManifest.xml", "settings.gradle.kts", "package.json").forEach {
                if (query.isBlank() || it.contains(query, ignoreCase = true)) result.add(it)
            }
        }
        return result.take(15)
    }

    private val mutableSlashActions = MutableStateFlow<List<SlashAction>>(SYSTEM_SLASH_COMMANDS)
    val slashActions: StateFlow<List<SlashAction>> = mutableSlashActions.asStateFlow()
    private var slashRefreshJob: Job? = null

    fun refreshCliSlashCommands(context: Context) {
        if (slashRefreshJob?.isActive == true) return
        slashRefreshJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val skills = getInstalledSkills(context).map {
                    SlashAction(
                        cmd = it.name.lowercase().trimStart('/'),
                        descText = it.description.ifBlank { null },
                        category = if (it.isBuiltIn) "Built-in Skill" else "Skill"
                    )
                }

                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val result = CliSlashCommandDelegate.refreshSlashActions(
                    context = context,
                    prefs = prefs,
                    skills = skills,
                    configDir = getGeminiConfigDir(context),
                    workspacePath = workspacePath.value,
                    isExecuting = mutableIsExecuting.value
                )

                mutableSlashActions.value = result.actions
                addLog("Slash", "Loaded dynamic slash menu (${result.actions.size} actions: ${result.skillCount} skills, ${result.workflowCount} workflows, ${result.cliCount} CLI)")
            } catch (e: Exception) {
                try {
                    val fallbackSkills = getInstalledSkills(context).map {
                        SlashAction(it.name.lowercase().trimStart('/'), it.description.ifBlank { null }, "Skill")
                    }
                    mutableSlashActions.value = mergeDynamicSlashActions(
                        systemCommands = SYSTEM_SLASH_COMMANDS,
                        cliCommands = emptyList(),
                        skills = fallbackSkills,
                        workflows = emptyList()
                    )
                } catch (_: Exception) {}
                addLog("Slash", "Failed to load slash menu: ${e.message}", isError = true)
            }
        }
    }

    fun sendMessage(
        context: Context,
        content: String,
        attachments: List<AttachedFile> = emptyList(),
        voiceInstruction: String? = null,
        suppressCompletionNotification: Boolean = false
    ) {
        val currentAttachments = if (attachments.isNotEmpty()) attachments else mutableAttachedFiles.value
        if (content.isBlank() && currentAttachments.isEmpty()) return

        if (content.trim().equals("/clear", ignoreCase = true)) {
            val convId = activeConversationId.value
            mutableChatMessages.value = emptyList()
            if (convId != null) {
                messagesByConv[convId] = emptyList()
                saveMessagesForConversation(context, convId, emptyList())
                conversationManagerDelegate.updateConversationHistoryDirect { list ->
                    list.map { if (it.id == convId) it.copy(messageCount = 0) else it }
                }
                saveConversationHistory(context)
            }
            return
        }

        if (content.trim().equals("/model", ignoreCase = true)) {
            showModelSelector()
            return
        }

        val existingConvId = activeConversationId.value
        if (existingConvId != null && isConversationExecuting(existingConvId)) {
            if (queuedMessagesMode.value.equals("Send Immediately", ignoreCase = true)) {
                stopAiExecution(existingConvId)
            } else {
                queueMessage(content, currentAttachments, existingConvId)
                mutableAttachedFiles.value = emptyList()
                return
            }
        }

        var convId = existingConvId
        if (convId == null) {
            convId = UUID.randomUUID().toString()
            conversationManagerDelegate.setActiveConversationIdDirect(convId)
        }
        mutableAttachedFiles.value = emptyList()
        sendMessageForConversation(context, convId, content, currentAttachments, voiceInstruction, suppressCompletionNotification)
    }

    /**
     * Runs an ephemeral voice-only turn without writing user or assistant messages into chat history.
     */
    fun sendVoiceTurn(
        context: Context,
        content: String,
        voiceInstruction: String? = null
    ): String? {
        val trimmed = content.trim()
        if (trimmed.isBlank()) return null

        val turnId = UUID.randomUUID().toString()
        val isShellCommand = trimmed.startsWith("!") || trimmed.startsWith("$ ")
        addLog("Voice", "Voice-only prompt: ${trimmed.take(150)}")

        if (!isShellCommand && !OAuthManager.hasToken(context)) {
            authDelegate.showGoogleSignInDialog()
            mutableLatestVoiceTurnResult.value = VoiceTurnResult(
                turnId = turnId,
                responseText = context.getString(R.string.chat_auth_required_error),
                isSuccess = false
            )
            return turnId
        }

        executeAiTurn(
            context = context,
            prompt = trimmed,
            convId = VOICE_ONLY_CONV_ID,
            attachments = emptyList(),
            voiceInstruction = voiceInstruction,
            ephemeralVoiceOnly = true,
            suppressCompletionNotification = true,
            onEphemeralResult = { response, isSuccess ->
                mutableLatestVoiceTurnResult.value = VoiceTurnResult(
                    turnId = turnId,
                    responseText = response,
                    isSuccess = isSuccess
                )
            }
        )
        return turnId
    }

    fun cancelVoiceTurn() {
        if (VOICE_ONLY_CONV_ID in mutableExecutingConvIds.value || jobsByConv.containsKey(VOICE_ONLY_CONV_ID)) {
            stopAiExecution(VOICE_ONLY_CONV_ID)
        }
    }

    fun proceedWithPlan(context: Context, messageId: String) {
        val convId = activeConversationId.value ?: return
        val updated = updateMessagesForConv(convId) { list ->
            list.map { msg ->
                if (msg.id == messageId) msg.copy(requiresPlanReview = false) else msg
            }
        }
        saveMessagesForConversation(context, convId, updated)
        sendMessage(context, "Proceed")
    }

    private fun sendMessageForConversation(
        context: Context,
        convId: String,
        content: String,
        currentAttachments: List<AttachedFile>,
        voiceInstruction: String? = null,
        suppressCompletionNotification: Boolean = false
    ) {
        if (isConversationExecuting(convId)) {
            queueMessage(content, currentAttachments, convId)
            return
        }

        val isForeground = (activeConversationId.value == convId)
        val existingMessages = if (isForeground) {
            mutableChatMessages.value
        } else {
            messagesByConv[convId] ?: loadMessagesForConversation(context, convId)
        }

        val currentTitle = if (isForeground) {
            conversationTitle.value
        } else {
            conversationHistory.value.find { it.id == convId }?.title ?: "New Conversation"
        }
        var resolvedTitle = currentTitle
        if (currentTitle == "New Conversation" || currentTitle.isBlank()) {
            val titleCandidate = content.lines().firstOrNull { it.isNotBlank() }?.trim()?.take(35)
                ?: currentAttachments.firstOrNull()?.name?.take(35)
            if (!titleCandidate.isNullOrEmpty()) {
                resolvedTitle = titleCandidate
                if (isForeground) {
                    conversationManagerDelegate.setConversationTitleDirect(titleCandidate)
                }
            }
        }

        val effectiveContent = if (content.isBlank() && currentAttachments.isNotEmpty()) {
            context.getString(R.string.chat_attached_files_prompt)
        } else {
            content
        }

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = MessageRole.USER,
            content = effectiveContent,
            attachments = currentAttachments
        )
        val updatedMessages = existingMessages + userMessage
        messagesByConv[convId] = updatedMessages
        if (isForeground) {
            mutableChatMessages.value = updatedMessages
        }

        val currentSummary = ConversationSummary(
            id = convId,
            title = resolvedTitle,
            timestamp = System.currentTimeMillis(),
            messageCount = updatedMessages.size,
            isPinned = if (isForeground) isConversationPinned.value else (conversationHistory.value.find { it.id == convId }?.isPinned ?: false),
            projectPath = workspacePath.value
        )
        conversationManagerDelegate.updateConversationHistoryDirect { list ->
            listOf(currentSummary) + list.filter { it.id != convId }
        }
        saveConversationHistory(context)
        saveMessagesForConversation(context, convId, updatedMessages)
        if (isForeground) {
            saveLastActiveConversationId(context, convId)
        }

        val isShellCommand = content.startsWith("!") || content.startsWith("$ ")
        addLog("Chat", "User prompt: ${effectiveContent.take(150)}")
        if (!isShellCommand && !OAuthManager.hasToken(context)) {
            authDelegate.showGoogleSignInDialog()
            val assistantMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                role = MessageRole.ASSISTANT,
                content = context.getString(R.string.chat_auth_required_error),
                status = MessageStatus.SENT,
                thinking = null
            )
            val finalMessages = updatedMessages + assistantMsg
            messagesByConv[convId] = finalMessages
            if (activeConversationId.value == convId) {
                mutableChatMessages.value = finalMessages
            }
            conversationManagerDelegate.updateConversationHistoryDirect { list ->
                list.map { if (it.id == convId) it.copy(messageCount = finalMessages.size) else it }
            }
            saveConversationHistory(context)
            saveMessagesForConversation(context, convId, finalMessages)
            return
        }

        executeAiTurn(
            context = context,
            prompt = effectiveContent,
            convId = convId,
            attachments = currentAttachments,
            voiceInstruction = voiceInstruction,
            suppressCompletionNotification = suppressCompletionNotification
        )
    }

    private val executionWakeLock = WakeLockHolder()

    fun stopAiExecution(targetConvId: String? = activeConversationId.value) {
        val convId = targetConvId
        if (convId == null) {
            jobsByConv.values.forEach { it.cancel() }
            jobsByConv.clear()
            EngineExecutor.stopAgyProcess()
            mutableExecutingConvIds.value = emptySet()
            runningTasksByConv.value = emptyMap()
            workingStatusByConv.value = emptyMap()
            executionWakeLock.release()
            syncForegroundExecutionState()
            return
        }
        addLog("Chat", "AI generation stopped by user (conv=$convId)")
        jobsByConv.remove(convId)?.cancel()
        EngineExecutor.stopAgyProcess(convId)
        mutableExecutingConvIds.update { it - convId }
        runningTasksByConv.update { it - convId }
        workingStatusByConv.update { it - convId }
        if (mutableExecutingConvIds.value.isEmpty()) {
            executionWakeLock.release()
        }
        if (convId != VOICE_ONLY_CONV_ID) {
            updateMessagesForConv(convId) { list ->
                list.map { msg ->
                    if (msg.status == MessageStatus.SENDING) {
                        msg.copy(
                            status = MessageStatus.SENT,
                            content = if (msg.content.isBlank()) "[Generation stopped by user]" else "${msg.content}\n\n*[Stopped by user]*",
                            thinking = null
                        )
                    } else msg
                }
            }
        }
        syncForegroundExecutionState()
    }

    fun undoPrompt(context: Context, userMessage: ChatMessage) {
        val activeId = activeConversationId.value
        if (activeId != null && isConversationExecuting(activeId)) {
            stopAiExecution(activeId)
        }
        val promptText = userMessage.content
        mutablePendingInputText.value = promptText

        val current = mutableChatMessages.value.toMutableList()
        val userIdx = current.indexOfFirst { it.id == userMessage.id }
        if (userIdx != -1) {
            var endIdx = userIdx + 1
            while (endIdx < current.size && current[endIdx].role != MessageRole.USER) {
                endIdx++
            }
            current.subList(userIdx, endIdx).clear()
            mutableChatMessages.value = current
            if (activeId != null) {
                messagesByConv[activeId] = current
                saveMessagesForConversation(context, activeId, current)
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            val root = File(workspacePath.value)
            if (root.exists() && File(root, ".git").exists()) {
                EngineExecutor.executeCommand(
                    context,
                    "(git restore --staged . 2>/dev/null || true) && (git restore . 2>/dev/null || git checkout -- . 2>/dev/null || true)",
                    workingDir = root
                ) { _, _ -> }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, context.getString(R.string.chat_toast_prompt_undone), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun executeAiTurn(
        context: Context,
        prompt: String,
        convId: String,
        attachments: List<AttachedFile> = emptyList(),
        voiceInstruction: String? = null,
        ephemeralVoiceOnly: Boolean = false,
        suppressCompletionNotification: Boolean = false,
        onEphemeralResult: ((String, Boolean) -> Unit)? = null
    ) {
        jobsByConv.remove(convId)?.cancel()
        val job = viewModelScope.launch(Dispatchers.IO) {
            executionWakeLock.acquire(context, 30 * 60 * 1000L)
            mutableExecutingConvIds.update { it + convId }
            workingStatusByConv.update { it + (convId to "Working") }
            syncForegroundExecutionState()

            val callbacks = object : TurnExecutionDelegate.Callbacks {
                override fun workspacePath(): String? = workspacePath.value
                override fun selectedModelCliId(): String? = selectedModelCliId.value
                override fun securityPreset(): String = securityPreset.value
                override fun thinkingBudget(): String = thinkingBudget.value
                override fun skipPermissions(): Boolean = skipPermissions.value
                override fun enableTerminalSandbox(): Boolean = permissionState.value.enableTerminalSandbox
                override fun getCliSessionId(convId: String): String? = convIdToCliSessionId[convId]
                override fun setCliSessionId(convId: String, cliSessionId: String) {
                    convIdToCliSessionId[convId] = cliSessionId
                }
                override fun getMessages(convId: String): List<ChatMessage> = messagesByConv[convId].orEmpty()
                override fun updateMessages(convId: String, transform: (List<ChatMessage>) -> List<ChatMessage>): List<ChatMessage> =
                    updateMessagesForConv(convId, transform)
                override fun addLog(tag: String, message: String, isError: Boolean) =
                    this@AntigravityViewModel.addLog(tag, message, isError)
                override fun setPendingAuthUrl(url: String?) = authDelegate.setPendingAuthUrl(url)
                override fun showGoogleSignInDialog() = authDelegate.showGoogleSignInDialog()
                override fun refreshAuthState(context: Context) = this@AntigravityViewModel.refreshAuthState(context)
                override fun addRunningTask(name: String, command: String, convId: String) {
                    val existing = runningTasksByConv.value[convId].orEmpty()
                    if (existing.none { it.command == command }) {
                        this@AntigravityViewModel.addRunningTask(name = name, command = command, convId = convId)
                    }
                }
                override fun removeRunningTask(stepId: String, cmdClean: String, convId: String) {
                    this@AntigravityViewModel.removeRunningTask(stepId, convId)
                    if (cmdClean.isNotBlank()) {
                        runningTasksByConv.update { map ->
                            val updated = map[convId].orEmpty().filter { it.command != cmdClean }
                            if (updated.isEmpty()) map - convId else map + (convId to updated)
                        }
                        syncForegroundExecutionState()
                    }
                }
                override fun addRunningSubagent(subagent: RunningSubagent) =
                    this@AntigravityViewModel.addRunningSubagent(subagent)
                override fun updateSubagentConversationId(stableStepId: String, detectedSubConvId: String) {
                    mutableRunningSubagents.update { list ->
                        list.map {
                            if (it.id.startsWith(stableStepId) && it.conversationId == null) {
                                it.copy(conversationId = detectedSubConvId)
                            } else it
                        }
                    }
                }
                override fun parseTranscriptTurn(context: Context, cliConvId: String?): ParsedTranscriptTurn =
                    this@AntigravityViewModel.parseTranscriptTurn(context, cliConvId)
                override suspend fun requestUserInteraction(
                    title: String,
                    detail: String,
                    options: List<id.vern.antigravity.data.InteractionOption>
                ): String? {
                    val deferred = kotlinx.coroutines.CompletableDeferred<String?>()
                    mutablePendingInteraction.value = id.vern.antigravity.data.PendingInteractionPrompt(
                        id = UUID.randomUUID().toString(),
                        title = title,
                        detail = detail,
                        options = options,
                        onSelect = { chosen ->
                            if (!deferred.isCompleted) deferred.complete(chosen)
                        }
                    )
                    return try {
                        deferred.await()
                    } finally {
                        if (mutablePendingInteraction.value?.onSelect != null && deferred.isCompleted) {
                            mutablePendingInteraction.value = null
                        }
                    }
                }
                override fun onModelLimitReached(prompt: String, reason: String) {
                    val modelName = selectedModel.value.ifBlank { "Current model" }
                    val (_, resetSubtext) = id.vern.antigravity.ui.components.getModelQuotaPercentage(
                        modelName,
                        mutableUserQuotaSummary.value
                    )
                    mutableModelLimitReachedInfo.value = ModelLimitReachedInfo(
                        modelName = modelName,
                        resetText = resetSubtext,
                        failedPrompt = prompt.takeIf { it.isNotBlank() },
                        reason = reason.takeIf { it.isNotBlank() }
                    )
                }
            }

            try {
                turnExecutionDelegate.runAiTurn(
                    context = context,
                    prompt = prompt,
                    convId = convId,
                    attachments = attachments,
                    callbacks = callbacks,
                    voiceInstruction = voiceInstruction,
                    ephemeralVoiceOnly = ephemeralVoiceOnly,
                    suppressCompletionNotification = suppressCompletionNotification,
                    onEphemeralResult = onEphemeralResult
                )
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) {
                    if (!ephemeralVoiceOnly) {
                        updateMessagesForConv(convId) { list ->
                            list.filter { it.role != MessageRole.ASSISTANT || it.status != MessageStatus.SENDING || it.content.isNotBlank() }
                        }
                    }
                } else {
                    val errorText = "Execution error: ${e.message}"
                    if (!ephemeralVoiceOnly) {
                        updateMessagesForConv(convId) { list ->
                            val lastSendingIdx = list.indexOfLast { it.role == MessageRole.ASSISTANT && it.status == MessageStatus.SENDING }
                            if (lastSendingIdx >= 0) {
                                list.mapIndexed { idx, msg ->
                                    if (idx == lastSendingIdx) {
                                        msg.copy(
                                            status = MessageStatus.ERROR,
                                            content = errorText,
                                            thinking = null
                                        )
                                    } else msg
                                }
                            } else list
                        }
                    }
                    onEphemeralResult?.invoke(errorText, false)
                    if (!suppressCompletionNotification) {
                        PromptCompletionNotifier.notifyPromptCompleted(
                            context = context,
                            conversationId = convId,
                            prompt = prompt,
                            response = errorText,
                            isSuccess = false
                        )
                    }
                }
            } finally {
                jobsByConv.remove(convId)
                mutableExecutingConvIds.update { it - convId }
                runningTasksByConv.update { it - convId }
                workingStatusByConv.update { it - convId }
                if (mutableExecutingConvIds.value.isEmpty()) {
                    executionWakeLock.release()
                }
                mutableRunningSubagents.update { list -> list.map { it.copy(isRunning = false) } }
                syncForegroundExecutionState()
                if (!ephemeralVoiceOnly) {
                    val currentList = messagesByConv[convId].orEmpty()
                    conversationManagerDelegate.updateConversationHistoryDirect { list ->
                        list.map { if (it.id == convId) it.copy(messageCount = currentList.size, timestamp = System.currentTimeMillis()) else it }
                    }
                    saveConversationHistory(context)
                    saveMessagesForConversation(context, convId, currentList)
                    processNextQueuedMessage(context, convId)
                }
                refreshUserQuota(context)
            }
        }
        jobsByConv[convId] = job
    }

    suspend fun computeTurnFileChanges(context: Context, workspacePath: String?): TurnFileChanges? =
        turnExecutionDelegate.computeTurnFileChanges(context, workspacePath)

    private fun extractPathFromParams(params: JSONObject?): String =
        TranscriptTurnParser.extractPathFromParams(params)

    private fun extractFileNameFromPath(path: String, fallback: String = "file"): String =
        TranscriptTurnParser.extractFileNameFromPath(path, fallback)

    fun findActiveTranscriptFile(context: Context, cliConvId: String?): File? =
        TranscriptTurnParser.findActiveTranscriptFile(context, cliConvId)

    fun parseTranscriptTurn(context: Context, cliConvId: String?): ParsedTranscriptTurn =
        TranscriptTurnParser.parseTranscriptTurn(context, cliConvId)

    fun extractStepsFromCliTranscript(context: Context, cliConvId: String?): List<AgentExecutionStep>? =
        TranscriptTurnParser.parseTranscriptTurn(context, cliConvId).steps

    fun extractAllThinkingFromCliTranscript(context: Context, cliConvId: String?): String? =
        TranscriptTurnParser.parseTranscriptTurn(context, cliConvId).thinking

    fun extractAssistantResponseFromCliTranscript(context: Context, cliConvId: String?): String? =
        TranscriptTurnParser.parseTranscriptTurn(context, cliConvId).assistantResponse
}
