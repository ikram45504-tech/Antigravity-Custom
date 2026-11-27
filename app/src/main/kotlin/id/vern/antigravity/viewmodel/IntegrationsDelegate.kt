package id.vern.antigravity.viewmodel

import android.content.Context
import android.widget.Toast
import id.vern.antigravity.integrations.CloudAccount
import id.vern.antigravity.integrations.CloudAccountVault
import id.vern.antigravity.integrations.CloudCliStatus
import id.vern.antigravity.integrations.CloudCliStatusHelper
import id.vern.antigravity.integrations.CloudProvider
import id.vern.antigravity.integrations.CloudVerificationResult
import id.vern.antigravity.integrations.CloudflareAccountItem
import id.vern.antigravity.integrations.CloudflareApiClient
import id.vern.antigravity.integrations.CloudflarePagesBinding
import id.vern.antigravity.integrations.CloudflarePagesDeploymentItem
import id.vern.antigravity.integrations.CloudflarePagesProjectItem
import id.vern.antigravity.integrations.ProjectIntegrationBinding
import id.vern.antigravity.integrations.ProjectIntegrationsManager
import id.vern.antigravity.integrations.SupabaseApiClient
import id.vern.antigravity.integrations.SupabaseProjectBinding
import id.vern.antigravity.integrations.SupabaseProjectItem
import id.vern.antigravity.integrations.VercelApiClient
import id.vern.antigravity.integrations.VercelDeploymentItem
import id.vern.antigravity.integrations.VercelProjectBinding
import id.vern.antigravity.integrations.VercelProjectItem
import id.vern.antigravity.integrations.VercelTeamItem
import id.vern.antigravity.executor.EngineExecutor
import id.vern.antigravity.oauth.GitHubAuthManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class IntegrationsDelegate {
    private val mutableTargetProjectPath = MutableStateFlow("")
    val targetProjectPath: StateFlow<String> = mutableTargetProjectPath.asStateFlow()

    private val mutableTargetBinding = MutableStateFlow(ProjectIntegrationBinding(""))
    val targetBinding: StateFlow<ProjectIntegrationBinding> = mutableTargetBinding.asStateFlow()

    private val mutableActiveWorkspaceBinding = MutableStateFlow<ProjectIntegrationBinding?>(null)
    val activeWorkspaceBinding: StateFlow<ProjectIntegrationBinding?> = mutableActiveWorkspaceBinding.asStateFlow()

    private val mutableAllProjectBindings = MutableStateFlow<Map<String, ProjectIntegrationBinding>>(emptyMap())
    val allProjectBindings: StateFlow<Map<String, ProjectIntegrationBinding>> = mutableAllProjectBindings.asStateFlow()

    private val mutableCloudAccounts = MutableStateFlow<List<CloudAccount>>(emptyList())
    val cloudAccounts: StateFlow<List<CloudAccount>> = mutableCloudAccounts.asStateFlow()

    private val mutableSelectedAccountIds = MutableStateFlow<Map<CloudProvider, String?>>(emptyMap())
    val selectedAccountIds: StateFlow<Map<CloudProvider, String?>> = mutableSelectedAccountIds.asStateFlow()

    private val mutableVerifications = MutableStateFlow<Map<CloudProvider, CloudVerificationResult?>>(emptyMap())
    val verifications: StateFlow<Map<CloudProvider, CloudVerificationResult?>> = mutableVerifications.asStateFlow()

    private val mutableIsVerifying = MutableStateFlow<Map<CloudProvider, Boolean>>(emptyMap())
    val isVerifying: StateFlow<Map<CloudProvider, Boolean>> = mutableIsVerifying.asStateFlow()

    private val mutableIsAuthenticating = MutableStateFlow<Map<CloudProvider, Boolean>>(emptyMap())
    val isAuthenticating: StateFlow<Map<CloudProvider, Boolean>> = mutableIsAuthenticating.asStateFlow()

    private val mutableAuthErrors = MutableStateFlow<Map<CloudProvider, String?>>(emptyMap())
    val authErrors: StateFlow<Map<CloudProvider, String?>> = mutableAuthErrors.asStateFlow()

    private val mutableVercelTeams = MutableStateFlow<List<VercelTeamItem>>(emptyList())
    val vercelTeams: StateFlow<List<VercelTeamItem>> = mutableVercelTeams.asStateFlow()

    private val mutableVercelSelectedTeamId = MutableStateFlow("")
    val vercelSelectedTeamId: StateFlow<String> = mutableVercelSelectedTeamId.asStateFlow()

    private val mutableVercelProjects = MutableStateFlow<List<VercelProjectItem>>(emptyList())
    val vercelProjects: StateFlow<List<VercelProjectItem>> = mutableVercelProjects.asStateFlow()

    private val mutableVercelDeployments = MutableStateFlow<Map<String, List<VercelDeploymentItem>>>(emptyMap())
    val vercelDeployments: StateFlow<Map<String, List<VercelDeploymentItem>>> = mutableVercelDeployments.asStateFlow()

    private val mutableSupabaseProjects = MutableStateFlow<List<SupabaseProjectItem>>(emptyList())
    val supabaseProjects: StateFlow<List<SupabaseProjectItem>> = mutableSupabaseProjects.asStateFlow()

    private val mutableCloudflareAccounts = MutableStateFlow<List<CloudflareAccountItem>>(emptyList())
    val cloudflareAccounts: StateFlow<List<CloudflareAccountItem>> = mutableCloudflareAccounts.asStateFlow()

    private val mutableCloudflareSelectedCfAccountId = MutableStateFlow("")
    val cloudflareSelectedCfAccountId: StateFlow<String> = mutableCloudflareSelectedCfAccountId.asStateFlow()

    private val mutableCloudflarePagesProjects = MutableStateFlow<List<CloudflarePagesProjectItem>>(emptyList())
    val cloudflarePagesProjects: StateFlow<List<CloudflarePagesProjectItem>> = mutableCloudflarePagesProjects.asStateFlow()

    private val mutableCloudflarePagesDeployments = MutableStateFlow<Map<String, List<CloudflarePagesDeploymentItem>>>(emptyMap())
    val cloudflarePagesDeployments: StateFlow<Map<String, List<CloudflarePagesDeploymentItem>>> = mutableCloudflarePagesDeployments.asStateFlow()

    private val mutableLoadingProviders = MutableStateFlow<Set<CloudProvider>>(emptySet())
    val loadingProviders: StateFlow<Set<CloudProvider>> = mutableLoadingProviders.asStateFlow()

    private val mutableProviderErrors = MutableStateFlow<Map<CloudProvider, String?>>(emptyMap())
    val providerErrors: StateFlow<Map<CloudProvider, String?>> = mutableProviderErrors.asStateFlow()

    private val mutableCliStatus = MutableStateFlow<CloudCliStatus?>(null)
    val cliStatus: StateFlow<CloudCliStatus?> = mutableCliStatus.asStateFlow()

    private val mutableInstallingCliProvider = MutableStateFlow<CloudProvider?>(null)
    val installingCliProvider: StateFlow<CloudProvider?> = mutableInstallingCliProvider.asStateFlow()

    private val mutableDeployingProvider = MutableStateFlow<CloudProvider?>(null)
    val deployingProvider: StateFlow<CloudProvider?> = mutableDeployingProvider.asStateFlow()

    fun refresh(context: Context, activeWorkspacePath: String, overrideTargetPath: String? = null) {
        val accounts = CloudAccountVault.getAccounts(context)
        mutableCloudAccounts.value = accounts

        val allBindings = ProjectIntegrationsManager.getAllBindings(context)
        mutableAllProjectBindings.value = allBindings.associateBy { ProjectIntegrationsManager.normalizePath(it.projectPath) }
        mutableActiveWorkspaceBinding.value = ProjectIntegrationsManager.getBindingForProject(context, activeWorkspacePath)

        val resolvedTarget = overrideTargetPath?.takeIf { it.isNotBlank() }
            ?: mutableTargetProjectPath.value.takeIf { it.isNotBlank() }
            ?: activeWorkspacePath
        mutableTargetProjectPath.value = resolvedTarget
        val resolvedBinding = ProjectIntegrationsManager.getExactBinding(context, resolvedTarget)
        mutableTargetBinding.value = resolvedBinding

        val currentSel = mutableSelectedAccountIds.value.toMutableMap()
        CloudProvider.entries.forEach { provider ->
            val providerAccounts = accounts.filter { it.provider == provider }
            val existing = currentSel[provider]
            if (existing != null && providerAccounts.none { it.id == existing }) {
                currentSel[provider] = null
            }
            if (currentSel[provider] == null) {
                val boundId = boundAccountIdFor(resolvedBinding, provider)
                if (boundId != null && providerAccounts.any { it.id == boundId }) {
                    currentSel[provider] = boundId
                }
            }
        }
        mutableSelectedAccountIds.value = currentSel
    }

    fun setTargetProjectPath(context: Context, path: String, activeWorkspacePath: String) {
        val targetChanged = ProjectIntegrationsManager.normalizePath(mutableTargetProjectPath.value) !=
            ProjectIntegrationsManager.normalizePath(path)
        mutableTargetProjectPath.value = path
        refresh(context, activeWorkspacePath, path)
        if (targetChanged) {
            clearTransientProviderState()
            syncSelectedAccountsToBinding(mutableTargetBinding.value, mutableCloudAccounts.value)
        }
    }

    fun onActiveWorkspaceChanged(context: Context, newWorkspacePath: String, scope: CoroutineScope) {
        val targetChanged = ProjectIntegrationsManager.normalizePath(mutableTargetProjectPath.value) !=
            ProjectIntegrationsManager.normalizePath(newWorkspacePath)
        mutableTargetProjectPath.value = newWorkspacePath
        refresh(context, newWorkspacePath, newWorkspacePath)
        if (targetChanged) {
            clearTransientProviderState()
            syncSelectedAccountsToBinding(mutableTargetBinding.value, mutableCloudAccounts.value)
        }
        scope.launch(Dispatchers.IO) {
            ProjectIntegrationsManager.syncActiveWorkspaceMcp(context, newWorkspacePath)
        }
    }

    private fun boundAccountIdFor(binding: ProjectIntegrationBinding, provider: CloudProvider): String? =
        when (provider) {
            CloudProvider.VERCEL -> binding.vercel?.accountId
            CloudProvider.SUPABASE -> binding.supabase?.accountId
            CloudProvider.CLOUDFLARE -> binding.cloudflare?.accountId
        }

    private fun syncSelectedAccountsToBinding(
        binding: ProjectIntegrationBinding,
        accounts: List<CloudAccount>
    ) {
        mutableSelectedAccountIds.value = CloudProvider.entries.associateWith { provider ->
            val boundId = boundAccountIdFor(binding, provider)
            if (boundId != null && accounts.any { it.provider == provider && it.id == boundId }) {
                boundId
            } else {
                null
            }
        }
    }

    private fun clearTransientProviderState() {
        mutableVerifications.value = emptyMap()
        mutableProviderErrors.value = emptyMap()
        mutableAuthErrors.value = emptyMap()
        mutableVercelTeams.value = emptyList()
        mutableVercelSelectedTeamId.value = ""
        mutableVercelProjects.value = emptyList()
        mutableVercelDeployments.value = emptyMap()
        mutableSupabaseProjects.value = emptyList()
        mutableCloudflareAccounts.value = emptyList()
        mutableCloudflareSelectedCfAccountId.value = ""
        mutableCloudflarePagesProjects.value = emptyList()
        mutableCloudflarePagesDeployments.value = emptyMap()
    }

    fun selectCloudAccount(provider: CloudProvider, accountId: String) {
        mutableSelectedAccountIds.value = mutableSelectedAccountIds.value + (provider to accountId)
        mutableVerifications.value = mutableVerifications.value - provider
        mutableProviderErrors.value = mutableProviderErrors.value - provider
        when (provider) {
            CloudProvider.VERCEL -> {
                mutableVercelTeams.value = emptyList()
                mutableVercelSelectedTeamId.value = ""
                mutableVercelProjects.value = emptyList()
                mutableVercelDeployments.value = emptyMap()
            }
            CloudProvider.SUPABASE -> {
                mutableSupabaseProjects.value = emptyList()
            }
            CloudProvider.CLOUDFLARE -> {
                mutableCloudflareAccounts.value = emptyList()
                mutableCloudflareSelectedCfAccountId.value = ""
                mutableCloudflarePagesProjects.value = emptyList()
                mutableCloudflarePagesDeployments.value = emptyMap()
            }
        }
    }

    fun addAndVerifyCloudAccount(
        context: Context,
        provider: CloudProvider,
        label: String,
        token: String,
        activeWorkspacePath: String,
        scope: CoroutineScope,
        onLog: (String, String, Boolean) -> Unit
    ) {
        val cleanToken = token.trim()
        if (cleanToken.isBlank()) {
            mutableAuthErrors.value = mutableAuthErrors.value + (provider to "API token cannot be empty")
            return
        }
        mutableAuthErrors.value = mutableAuthErrors.value - provider
        mutableIsAuthenticating.value = mutableIsAuthenticating.value + (provider to true)

        scope.launch {
            val verification = withContext(Dispatchers.IO) {
                when (provider) {
                    CloudProvider.VERCEL -> VercelApiClient.verifyToken(cleanToken)
                    CloudProvider.SUPABASE -> SupabaseApiClient.verifyToken(cleanToken)
                    CloudProvider.CLOUDFLARE -> CloudflareApiClient.verifyToken(cleanToken)
                }
            }

            if (verification.isValid) {
                val resolvedLabel = label.trim().ifBlank { verification.displayName.ifBlank { provider.displayName } }
                val saved = withContext(Dispatchers.IO) {
                    CloudAccountVault.saveAccount(
                        context,
                        CloudAccount(
                            id = UUID.randomUUID().toString(),
                            provider = provider,
                            label = resolvedLabel,
                            handleOrEmail = verification.detail.ifBlank { verification.displayName },
                            token = cleanToken
                        )
                    )
                }
                mutableSelectedAccountIds.value = mutableSelectedAccountIds.value + (provider to saved.id)
                mutableVerifications.value = mutableVerifications.value + (provider to verification)
                refresh(context, activeWorkspacePath)
                onLog(provider.displayName, "Connected ${saved.label}: ${verification.message}", false)
                Toast.makeText(context, "${provider.displayName} connected (${saved.label})", Toast.LENGTH_SHORT).show()
            } else {
                mutableAuthErrors.value = mutableAuthErrors.value + (provider to verification.message)
                onLog(provider.displayName, "Verification failed: ${verification.message}", true)
            }
            mutableIsAuthenticating.value = mutableIsAuthenticating.value + (provider to false)
        }
    }

    fun verifyCloudAccount(
        context: Context,
        provider: CloudProvider,
        accountId: String,
        scope: CoroutineScope,
        onLog: (String, String, Boolean) -> Unit
    ) {
        val account = CloudAccountVault.getAccountById(context, accountId) ?: return
        mutableIsVerifying.value = mutableIsVerifying.value + (provider to true)
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                when (provider) {
                    CloudProvider.VERCEL -> VercelApiClient.verifyToken(account.token)
                    CloudProvider.SUPABASE -> SupabaseApiClient.verifyToken(account.token)
                    CloudProvider.CLOUDFLARE -> CloudflareApiClient.verifyToken(account.token)
                }
            }
            mutableVerifications.value = mutableVerifications.value + (provider to res)
            mutableIsVerifying.value = mutableIsVerifying.value + (provider to false)
            onLog(provider.displayName, res.message, !res.isValid)
        }
    }

    fun removeCloudAccount(
        context: Context,
        provider: CloudProvider,
        accountId: String,
        activeWorkspacePath: String,
        scope: CoroutineScope,
        onLog: (String, String, Boolean) -> Unit
    ) {
        scope.launch {
            withContext(Dispatchers.IO) {
                CloudAccountVault.removeAccount(context, accountId)
                ProjectIntegrationsManager.removeBindingsForCloudAccount(context, accountId)
                ProjectIntegrationsManager.syncActiveWorkspaceMcp(context, activeWorkspacePath)
            }
            selectCloudAccount(provider, "")
            refresh(context, activeWorkspacePath)
            onLog(provider.displayName, "Removed ${provider.displayName} account", false)
            Toast.makeText(context, "Removed ${provider.displayName} account", Toast.LENGTH_SHORT).show()
        }
    }

    fun bindGitHubToProject(
        context: Context,
        projectPath: String,
        accountLogin: String,
        repoFullName: String = "",
        repoCloneUrl: String = "",
        activeWorkspacePath: String,
        onLog: (String, String, Boolean) -> Unit
    ) {
        if (projectPath.isBlank() || accountLogin.isBlank()) return
        ProjectIntegrationsManager.bindGitHub(context, projectPath, accountLogin, repoFullName, repoCloneUrl)
        GitHubAuthManager.syncGitCredentials(context)
        refresh(context, activeWorkspacePath, projectPath)
        val suffix = if (repoFullName.isNotBlank()) " ($repoFullName)" else ""
        onLog("GitHub", "Bound ${File(projectPath).name} to @$accountLogin$suffix", false)
        Toast.makeText(context, "Linked @$accountLogin to ${File(projectPath).name}", Toast.LENGTH_SHORT).show()
    }

    fun unbindGitHubFromProject(
        context: Context,
        projectPath: String,
        activeWorkspacePath: String,
        onLog: (String, String, Boolean) -> Unit
    ) {
        if (projectPath.isBlank()) return
        ProjectIntegrationsManager.unbindGitHub(context, projectPath)
        refresh(context, activeWorkspacePath, projectPath)
        onLog("GitHub", "Unlinked GitHub from ${File(projectPath).name}", false)
        Toast.makeText(context, "Unlinked GitHub from ${File(projectPath).name}", Toast.LENGTH_SHORT).show()
    }

    // ---- Vercel ----

    fun loadVercelResources(
        context: Context,
        accountId: String,
        teamId: String? = null,
        scope: CoroutineScope,
        onLog: (String, String, Boolean) -> Unit
    ) {
        val acc = CloudAccountVault.getAccountById(context, accountId) ?: return
        mutableLoadingProviders.value = mutableLoadingProviders.value + CloudProvider.VERCEL
        mutableProviderErrors.value = mutableProviderErrors.value - CloudProvider.VERCEL
        val chosenTeam = teamId ?: mutableVercelSelectedTeamId.value

        scope.launch {
            val teamsRes = withContext(Dispatchers.IO) { VercelApiClient.fetchTeams(acc.token) }
            teamsRes.onSuccess { mutableVercelTeams.value = it }

            val projectsRes = withContext(Dispatchers.IO) {
                VercelApiClient.fetchProjects(acc.token, chosenTeam.takeIf { it.isNotBlank() })
            }
            projectsRes.onSuccess { list ->
                mutableVercelSelectedTeamId.value = chosenTeam
                mutableVercelProjects.value = list
                onLog("Vercel", "Loaded ${list.size} Vercel project(s)", false)
            }.onFailure { err ->
                mutableProviderErrors.value = mutableProviderErrors.value + (CloudProvider.VERCEL to (err.message ?: "Failed to load Vercel projects"))
                onLog("Vercel", "Load failed: ${err.message}", true)
            }
            mutableLoadingProviders.value = mutableLoadingProviders.value - CloudProvider.VERCEL
        }
    }

    fun loadVercelDeployments(
        context: Context,
        accountId: String,
        projectId: String,
        teamId: String?,
        scope: CoroutineScope,
        onLog: (String, String, Boolean) -> Unit
    ) {
        val acc = CloudAccountVault.getAccountById(context, accountId) ?: return
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                VercelApiClient.fetchDeployments(acc.token, projectId, teamId)
            }
            res.onSuccess { deps ->
                mutableVercelDeployments.value = mutableVercelDeployments.value + (projectId to deps)
            }.onFailure { err ->
                onLog("Vercel", "Deployments load failed: ${err.message}", true)
            }
        }
    }

    fun bindVercelToProject(
        context: Context,
        projectPath: String,
        accountId: String,
        project: VercelProjectItem,
        injectEnv: Boolean,
        syncMcp: Boolean,
        activeWorkspacePath: String,
        scope: CoroutineScope,
        onLog: (String, String, Boolean) -> Unit
    ) {
        if (projectPath.isBlank()) return
        scope.launch {
            withContext(Dispatchers.IO) {
                ProjectIntegrationsManager.bindVercel(
                    context,
                    projectPath,
                    VercelProjectBinding(
                        accountId = accountId,
                        teamId = project.teamId.orEmpty(),
                        orgId = project.orgId,
                        projectId = project.id,
                        projectName = project.name,
                        injectEnv = injectEnv,
                        syncMcp = syncMcp
                    )
                )
                ProjectIntegrationsManager.syncActiveWorkspaceMcp(context, activeWorkspacePath)
            }
            refresh(context, activeWorkspacePath, projectPath)
            onLog("Vercel", "Linked ${project.name} to ${File(projectPath).name}", false)
            Toast.makeText(context, "Linked Vercel: ${project.name}", Toast.LENGTH_SHORT).show()
        }
    }

    fun updateVercelToggles(
        context: Context,
        projectPath: String,
        injectEnv: Boolean,
        syncMcp: Boolean,
        activeWorkspacePath: String,
        scope: CoroutineScope
    ) {
        val current = ProjectIntegrationsManager.getExactBinding(context, projectPath).vercel ?: return
        scope.launch {
            withContext(Dispatchers.IO) {
                ProjectIntegrationsManager.bindVercel(
                    context,
                    projectPath,
                    current.copy(injectEnv = injectEnv, syncMcp = syncMcp)
                )
                ProjectIntegrationsManager.syncActiveWorkspaceMcp(context, activeWorkspacePath)
            }
            refresh(context, activeWorkspacePath, projectPath)
        }
    }

    fun pullVercelEnvToWorkspace(
        context: Context,
        projectPath: String,
        accountId: String,
        projectId: String,
        teamId: String?,
        scope: CoroutineScope,
        onLog: (String, String, Boolean) -> Unit
    ) {
        val acc = CloudAccountVault.getAccountById(context, accountId) ?: return
        val dir = File(projectPath)
        if (!dir.isDirectory) return
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                VercelApiClient.fetchProjectEnvVars(acc.token, projectId, teamId)
            }
            res.onSuccess { vars ->
                val written = withContext(Dispatchers.IO) {
                    VercelApiClient.pullEnvToDotEnvLocal(dir, vars)
                }
                val msg = "Synced $written Vercel env var(s) to .env.local"
                onLog("Vercel", msg, false)
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                onLog("Vercel", "Env pull failed: ${err.message}", true)
                Toast.makeText(context, err.message ?: "Env pull failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun writeVercelProjectJson(
        context: Context,
        projectPath: String,
        orgId: String,
        projectId: String,
        onLog: (String, String, Boolean) -> Unit
    ) {
        val dir = File(projectPath)
        if (!dir.isDirectory) return
        runCatching {
            VercelApiClient.writeWorkspaceProjectJson(dir, orgId, projectId)
            onLog("Vercel", "Wrote .vercel/project.json in ${dir.name}", false)
            Toast.makeText(context, "Wrote .vercel/project.json", Toast.LENGTH_SHORT).show()
        }.onFailure { err ->
            onLog("Vercel", "Failed writing .vercel/project.json: ${err.message}", true)
        }
    }

    fun unbindVercelFromProject(
        context: Context,
        projectPath: String,
        activeWorkspacePath: String,
        scope: CoroutineScope,
        onLog: (String, String, Boolean) -> Unit
    ) {
        scope.launch {
            withContext(Dispatchers.IO) {
                ProjectIntegrationsManager.unbindVercel(context, projectPath)
                ProjectIntegrationsManager.syncActiveWorkspaceMcp(context, activeWorkspacePath)
            }
            refresh(context, activeWorkspacePath, projectPath)
            onLog("Vercel", "Unlinked Vercel from ${File(projectPath).name}", false)
        }
    }

    // ---- Supabase ----

    fun loadSupabaseProjects(
        context: Context,
        accountId: String,
        scope: CoroutineScope,
        onLog: (String, String, Boolean) -> Unit
    ) {
        val acc = CloudAccountVault.getAccountById(context, accountId) ?: return
        mutableLoadingProviders.value = mutableLoadingProviders.value + CloudProvider.SUPABASE
        mutableProviderErrors.value = mutableProviderErrors.value - CloudProvider.SUPABASE
        scope.launch {
            val res = withContext(Dispatchers.IO) { SupabaseApiClient.fetchProjects(acc.token) }
            res.onSuccess { list ->
                mutableSupabaseProjects.value = list
                onLog("Supabase", "Loaded ${list.size} Supabase project(s)", false)
            }.onFailure { err ->
                mutableProviderErrors.value = mutableProviderErrors.value + (CloudProvider.SUPABASE to (err.message ?: "Failed to load Supabase projects"))
                onLog("Supabase", "Load failed: ${err.message}", true)
            }
            mutableLoadingProviders.value = mutableLoadingProviders.value - CloudProvider.SUPABASE
        }
    }

    fun bindSupabaseToProject(
        context: Context,
        projectPath: String,
        accountId: String,
        project: SupabaseProjectItem,
        injectEnv: Boolean,
        syncMcp: Boolean,
        activeWorkspacePath: String,
        scope: CoroutineScope,
        onLog: (String, String, Boolean) -> Unit
    ) {
        val acc = CloudAccountVault.getAccountById(context, accountId) ?: return
        if (projectPath.isBlank()) return
        scope.launch {
            val anonKey = withContext(Dispatchers.IO) {
                SupabaseApiClient.fetchProjectApiKeys(acc.token, project.ref)
                    .getOrDefault(emptyList())
                    .firstOrNull { it.name.equals("anon", ignoreCase = true) }
                    ?.apiKey
                    .orEmpty()
            }
            withContext(Dispatchers.IO) {
                ProjectIntegrationsManager.bindSupabase(
                    context,
                    projectPath,
                    SupabaseProjectBinding(
                        accountId = accountId,
                        organizationId = project.organizationId,
                        projectRef = project.ref,
                        projectName = project.name,
                        apiUrl = project.apiUrl,
                        anonKey = anonKey,
                        injectEnv = injectEnv,
                        syncMcp = syncMcp
                    )
                )
                ProjectIntegrationsManager.syncActiveWorkspaceMcp(context, activeWorkspacePath)
            }
            refresh(context, activeWorkspacePath, projectPath)
            onLog("Supabase", "Linked ${project.name} (${project.ref}) to ${File(projectPath).name}", false)
            Toast.makeText(context, "Linked Supabase: ${project.name}", Toast.LENGTH_SHORT).show()
        }
    }

    fun updateSupabaseToggles(
        context: Context,
        projectPath: String,
        injectEnv: Boolean,
        syncMcp: Boolean,
        activeWorkspacePath: String,
        scope: CoroutineScope
    ) {
        val current = ProjectIntegrationsManager.getExactBinding(context, projectPath).supabase ?: return
        scope.launch {
            withContext(Dispatchers.IO) {
                ProjectIntegrationsManager.bindSupabase(
                    context,
                    projectPath,
                    current.copy(injectEnv = injectEnv, syncMcp = syncMcp)
                )
                ProjectIntegrationsManager.syncActiveWorkspaceMcp(context, activeWorkspacePath)
            }
            refresh(context, activeWorkspacePath, projectPath)
        }
    }

    fun pullSupabaseEnvToWorkspace(
        context: Context,
        projectPath: String,
        accountId: String,
        projectRef: String,
        apiUrl: String,
        includeServiceRole: Boolean,
        scope: CoroutineScope,
        onLog: (String, String, Boolean) -> Unit
    ) {
        val acc = CloudAccountVault.getAccountById(context, accountId) ?: return
        val dir = File(projectPath)
        if (!dir.isDirectory) return
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                SupabaseApiClient.fetchProjectApiKeys(acc.token, projectRef)
            }
            res.onSuccess { keys ->
                val anon = keys.firstOrNull { it.name.equals("anon", ignoreCase = true) }?.apiKey.orEmpty()
                val serviceRole = if (includeServiceRole) {
                    keys.firstOrNull { it.name.equals("service_role", ignoreCase = true) }?.apiKey
                } else null
                withContext(Dispatchers.IO) {
                    SupabaseApiClient.writeSupabaseEnvToWorkspace(dir, apiUrl, anon, serviceRole)
                }
                val msg = "Synced Supabase keys to .env.local"
                onLog("Supabase", msg, false)
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                onLog("Supabase", "Key fetch failed: ${err.message}", true)
                Toast.makeText(context, err.message ?: "Key fetch failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun unbindSupabaseFromProject(
        context: Context,
        projectPath: String,
        activeWorkspacePath: String,
        scope: CoroutineScope,
        onLog: (String, String, Boolean) -> Unit
    ) {
        scope.launch {
            withContext(Dispatchers.IO) {
                ProjectIntegrationsManager.unbindSupabase(context, projectPath)
                ProjectIntegrationsManager.syncActiveWorkspaceMcp(context, activeWorkspacePath)
            }
            refresh(context, activeWorkspacePath, projectPath)
            onLog("Supabase", "Unlinked Supabase from ${File(projectPath).name}", false)
        }
    }

    // ---- Cloudflare Pages ----

    fun loadCloudflarePages(
        context: Context,
        accountId: String,
        cfAccountId: String? = null,
        scope: CoroutineScope,
        onLog: (String, String, Boolean) -> Unit
    ) {
        val acc = CloudAccountVault.getAccountById(context, accountId) ?: return
        mutableLoadingProviders.value = mutableLoadingProviders.value + CloudProvider.CLOUDFLARE
        mutableProviderErrors.value = mutableProviderErrors.value - CloudProvider.CLOUDFLARE

        scope.launch {
            val accountsRes = withContext(Dispatchers.IO) { CloudflareApiClient.fetchAccounts(acc.token) }
            val cfAccounts = accountsRes.getOrElse { err ->
                mutableProviderErrors.value = mutableProviderErrors.value + (CloudProvider.CLOUDFLARE to (err.message ?: "Failed to load Cloudflare accounts"))
                mutableLoadingProviders.value = mutableLoadingProviders.value - CloudProvider.CLOUDFLARE
                onLog("Cloudflare", "Accounts load failed: ${err.message}", true)
                return@launch
            }
            mutableCloudflareAccounts.value = cfAccounts

            val targetCfAccountId = cfAccountId?.takeIf { it.isNotBlank() }
                ?: mutableCloudflareSelectedCfAccountId.value.takeIf { it.isNotBlank() }
                ?: cfAccounts.firstOrNull()?.id.orEmpty()

            if (targetCfAccountId.isBlank()) {
                mutableCloudflarePagesProjects.value = emptyList()
                mutableLoadingProviders.value = mutableLoadingProviders.value - CloudProvider.CLOUDFLARE
                return@launch
            }

            mutableCloudflareSelectedCfAccountId.value = targetCfAccountId
            val pagesRes = withContext(Dispatchers.IO) {
                CloudflareApiClient.fetchPagesProjects(acc.token, targetCfAccountId)
            }
            pagesRes.onSuccess { list ->
                mutableCloudflarePagesProjects.value = list
                onLog("Cloudflare", "Loaded ${list.size} Pages project(s)", false)
            }.onFailure { err ->
                mutableProviderErrors.value = mutableProviderErrors.value + (CloudProvider.CLOUDFLARE to (err.message ?: "Failed to load Pages projects"))
                onLog("Cloudflare", "Pages load failed: ${err.message}", true)
            }
            mutableLoadingProviders.value = mutableLoadingProviders.value - CloudProvider.CLOUDFLARE
        }
    }

    fun loadCloudflarePagesDeployments(
        context: Context,
        accountId: String,
        cfAccountId: String,
        projectName: String,
        scope: CoroutineScope,
        onLog: (String, String, Boolean) -> Unit
    ) {
        val acc = CloudAccountVault.getAccountById(context, accountId) ?: return
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                CloudflareApiClient.fetchPagesDeployments(acc.token, cfAccountId, projectName)
            }
            res.onSuccess { deps ->
                mutableCloudflarePagesDeployments.value = mutableCloudflarePagesDeployments.value + (projectName to deps)
            }.onFailure { err ->
                onLog("Cloudflare", "Deployments load failed: ${err.message}", true)
            }
        }
    }

    fun bindCloudflarePagesToProject(
        context: Context,
        projectPath: String,
        accountId: String,
        cfAccountId: String,
        cfAccountName: String,
        project: CloudflarePagesProjectItem,
        injectEnv: Boolean,
        syncMcp: Boolean,
        activeWorkspacePath: String,
        scope: CoroutineScope,
        onLog: (String, String, Boolean) -> Unit
    ) {
        if (projectPath.isBlank()) return
        scope.launch {
            withContext(Dispatchers.IO) {
                ProjectIntegrationsManager.bindCloudflare(
                    context,
                    projectPath,
                    CloudflarePagesBinding(
                        accountId = accountId,
                        cfAccountId = cfAccountId,
                        cfAccountName = cfAccountName,
                        pagesProjectName = project.name,
                        subdomain = project.subdomain,
                        injectEnv = injectEnv,
                        syncMcp = syncMcp
                    )
                )
                ProjectIntegrationsManager.syncActiveWorkspaceMcp(context, activeWorkspacePath)
            }
            refresh(context, activeWorkspacePath, projectPath)
            onLog("Cloudflare", "Linked Pages ${project.name} to ${File(projectPath).name}", false)
            Toast.makeText(context, "Linked Cloudflare Pages: ${project.name}", Toast.LENGTH_SHORT).show()
        }
    }

    fun updateCloudflareToggles(
        context: Context,
        projectPath: String,
        injectEnv: Boolean,
        syncMcp: Boolean,
        activeWorkspacePath: String,
        scope: CoroutineScope
    ) {
        val current = ProjectIntegrationsManager.getExactBinding(context, projectPath).cloudflare ?: return
        scope.launch {
            withContext(Dispatchers.IO) {
                ProjectIntegrationsManager.bindCloudflare(
                    context,
                    projectPath,
                    current.copy(injectEnv = injectEnv, syncMcp = syncMcp)
                )
                ProjectIntegrationsManager.syncActiveWorkspaceMcp(context, activeWorkspacePath)
            }
            refresh(context, activeWorkspacePath, projectPath)
        }
    }

    fun unbindCloudflareFromProject(
        context: Context,
        projectPath: String,
        activeWorkspacePath: String,
        scope: CoroutineScope,
        onLog: (String, String, Boolean) -> Unit
    ) {
        scope.launch {
            withContext(Dispatchers.IO) {
                ProjectIntegrationsManager.unbindCloudflare(context, projectPath)
                ProjectIntegrationsManager.syncActiveWorkspaceMcp(context, activeWorkspacePath)
            }
            refresh(context, activeWorkspacePath, projectPath)
            onLog("Cloudflare", "Unlinked Cloudflare Pages from ${File(projectPath).name}", false)
        }
    }

    fun refreshCliStatus(context: Context) {
        mutableCliStatus.value = CloudCliStatusHelper.inspect(context)
    }

    fun installProviderCli(
        context: Context,
        provider: CloudProvider,
        scope: CoroutineScope,
        onLog: (String, String, Boolean) -> Unit
    ) {
        if (mutableInstallingCliProvider.value != null) return
        val cmd = CloudCliStatusHelper.installCommandFor(provider)
        mutableInstallingCliProvider.value = provider
        scope.launch {
            val out = StringBuilder()
            val exitCode = EngineExecutor.executeCommand(context, cmd) { line, _ ->
                if (line.isNotBlank()) out.appendLine(line)
            }
            refreshCliStatus(context)
            mutableInstallingCliProvider.value = null
            if (exitCode == 0) {
                val msg = "${provider.displayName} CLI installed"
                onLog(provider.displayName, msg, false)
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            } else {
                val errLine = out.toString().trim().lines().lastOrNull()?.take(140) ?: "Exit code $exitCode"
                onLog(provider.displayName, "CLI install failed: $errLine", true)
                Toast.makeText(context, "Install failed: $errLine", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun triggerVercelRedeploy(
        context: Context,
        accountId: String,
        projectId: String,
        projectName: String,
        teamId: String?,
        scope: CoroutineScope,
        onLog: (String, String, Boolean) -> Unit
    ) {
        val acc = CloudAccountVault.getAccountById(context, accountId) ?: return
        if (mutableDeployingProvider.value != null) return
        mutableDeployingProvider.value = CloudProvider.VERCEL
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                VercelApiClient.triggerLatestRedeploy(acc.token, projectId, projectName, teamId)
            }
            mutableDeployingProvider.value = null
            res.onSuccess { dep ->
                val existing = mutableVercelDeployments.value[projectId].orEmpty()
                mutableVercelDeployments.value = mutableVercelDeployments.value + (projectId to (listOf(dep) + existing))
                val msg = "Triggered Vercel redeploy (${dep.state})"
                onLog("Vercel", msg, false)
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                onLog("Vercel", "Redeploy failed: ${err.message}", true)
                Toast.makeText(context, err.message ?: "Redeploy failed", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun triggerCloudflarePagesDeploy(
        context: Context,
        accountId: String,
        cfAccountId: String,
        projectName: String,
        scope: CoroutineScope,
        onLog: (String, String, Boolean) -> Unit
    ) {
        val acc = CloudAccountVault.getAccountById(context, accountId) ?: return
        if (mutableDeployingProvider.value != null) return
        mutableDeployingProvider.value = CloudProvider.CLOUDFLARE
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                CloudflareApiClient.triggerPagesDeployment(acc.token, cfAccountId, projectName)
            }
            mutableDeployingProvider.value = null
            res.onSuccess { dep ->
                val existing = mutableCloudflarePagesDeployments.value[projectName].orEmpty()
                mutableCloudflarePagesDeployments.value = mutableCloudflarePagesDeployments.value + (projectName to (listOf(dep) + existing))
                val msg = "Triggered Pages deploy (${dep.stageStatus})"
                onLog("Cloudflare", msg, false)
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                onLog("Cloudflare", "Pages deploy failed: ${err.message}", true)
                Toast.makeText(context, err.message ?: "Pages deploy failed", Toast.LENGTH_LONG).show()
            }
        }
    }
}
