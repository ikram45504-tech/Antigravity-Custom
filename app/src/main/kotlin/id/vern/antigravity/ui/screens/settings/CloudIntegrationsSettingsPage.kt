package id.vern.antigravity.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.vern.antigravity.integrations.CloudAccount
import id.vern.antigravity.integrations.CloudProvider
import id.vern.antigravity.integrations.CloudVerificationResult
import id.vern.antigravity.integrations.CloudflarePagesDeploymentItem
import id.vern.antigravity.integrations.CloudflarePagesProjectItem
import id.vern.antigravity.integrations.ProjectIntegrationBinding
import id.vern.antigravity.integrations.SupabaseProjectItem
import id.vern.antigravity.integrations.VercelDeploymentItem
import id.vern.antigravity.integrations.VercelProjectItem
import id.vern.antigravity.ui.theme.AntigravityAmber
import id.vern.antigravity.ui.theme.AntigravityBlue
import id.vern.antigravity.ui.theme.AntigravityGreen
import id.vern.antigravity.ui.theme.AntigravityRed
import id.vern.antigravity.viewmodel.AntigravityViewModel
import java.io.File

@Composable
fun CloudIntegrationsSettingsPage(
    viewModel: AntigravityViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val workspacePath by viewModel.workspacePath.collectAsStateWithLifecycle()
    val projectList by viewModel.projectList.collectAsStateWithLifecycle()
    val targetProjectPath by viewModel.integrationTargetProjectPath.collectAsStateWithLifecycle()
    val targetBinding by viewModel.integrationTargetBinding.collectAsStateWithLifecycle()
    val cloudAccounts by viewModel.cloudAccounts.collectAsStateWithLifecycle()
    val selectedAccountIds by viewModel.selectedCloudAccountIds.collectAsStateWithLifecycle()
    val verifications by viewModel.cloudVerifications.collectAsStateWithLifecycle()
    val isVerifyingMap by viewModel.isVerifyingCloudProvider.collectAsStateWithLifecycle()
    val isAuthenticatingMap by viewModel.isAuthenticatingCloudProvider.collectAsStateWithLifecycle()
    val authErrorsMap by viewModel.cloudAuthErrors.collectAsStateWithLifecycle()
    val loadingProviders by viewModel.loadingCloudProviders.collectAsStateWithLifecycle()
    val providerErrors by viewModel.cloudProviderErrors.collectAsStateWithLifecycle()

    var selectedProvider by remember { mutableStateOf(CloudProvider.VERCEL) }
    var showProjectPicker by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val effectiveProjectPath = targetProjectPath.ifBlank { workspacePath }

    LaunchedEffect(Unit) {
        viewModel.refreshIntegrationsState(context, effectiveProjectPath)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        SubSheetHeader(
            title = "Cloud & Deploy",
            subtitle = "Per-project Vercel, Supabase & Cloudflare Pages",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
        ) {
            ProjectSelectorSummaryCard(
                effectiveProjectPath = effectiveProjectPath,
                projectList = projectList,
                binding = targetBinding,
                showPicker = showProjectPicker,
                onTogglePicker = { showProjectPicker = it },
                onSelectProject = { path -> viewModel.setIntegrationTargetProject(context, path) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Provider tab selector
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(4.dp)) {
                    CloudProvider.entries.forEach { provider ->
                        val isSelected = selectedProvider == provider
                        val label = when (provider) {
                            CloudProvider.VERCEL -> "Vercel"
                            CloudProvider.SUPABASE -> "Supabase"
                            CloudProvider.CLOUDFLARE -> "CF Pages"
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) AntigravityBlue else Color.Transparent)
                                .clickable { selectedProvider = provider }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val providerAccounts = remember(cloudAccounts, selectedProvider) {
                cloudAccounts.filter { it.provider == selectedProvider }
            }
            val activeAccountId = selectedAccountIds[selectedProvider]
            val activeAccount = remember(providerAccounts, activeAccountId) {
                providerAccounts.firstOrNull { it.id == activeAccountId }
            }

            when (selectedProvider) {
                CloudProvider.VERCEL -> {
                    VercelTabContent(
                        context = context,
                        viewModel = viewModel,
                        effectiveProjectPath = effectiveProjectPath,
                        binding = targetBinding,
                        accounts = providerAccounts,
                        activeAccount = activeAccount,
                        verification = verifications[CloudProvider.VERCEL],
                        isVerifying = isVerifyingMap[CloudProvider.VERCEL] == true,
                        isAuthenticating = isAuthenticatingMap[CloudProvider.VERCEL] == true,
                        authError = authErrorsMap[CloudProvider.VERCEL],
                        isLoadingResources = loadingProviders.contains(CloudProvider.VERCEL),
                        resourceError = providerErrors[CloudProvider.VERCEL]
                    )
                }
                CloudProvider.SUPABASE -> {
                    SupabaseTabContent(
                        context = context,
                        viewModel = viewModel,
                        effectiveProjectPath = effectiveProjectPath,
                        binding = targetBinding,
                        accounts = providerAccounts,
                        activeAccount = activeAccount,
                        verification = verifications[CloudProvider.SUPABASE],
                        isVerifying = isVerifyingMap[CloudProvider.SUPABASE] == true,
                        isAuthenticating = isAuthenticatingMap[CloudProvider.SUPABASE] == true,
                        authError = authErrorsMap[CloudProvider.SUPABASE],
                        isLoadingResources = loadingProviders.contains(CloudProvider.SUPABASE),
                        resourceError = providerErrors[CloudProvider.SUPABASE]
                    )
                }
                CloudProvider.CLOUDFLARE -> {
                    CloudflarePagesTabContent(
                        context = context,
                        viewModel = viewModel,
                        effectiveProjectPath = effectiveProjectPath,
                        binding = targetBinding,
                        accounts = providerAccounts,
                        activeAccount = activeAccount,
                        verification = verifications[CloudProvider.CLOUDFLARE],
                        isVerifying = isVerifyingMap[CloudProvider.CLOUDFLARE] == true,
                        isAuthenticating = isAuthenticatingMap[CloudProvider.CLOUDFLARE] == true,
                        authError = authErrorsMap[CloudProvider.CLOUDFLARE],
                        isLoadingResources = loadingProviders.contains(CloudProvider.CLOUDFLARE),
                        resourceError = providerErrors[CloudProvider.CLOUDFLARE]
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ProjectSelectorSummaryCard(
    effectiveProjectPath: String,
    projectList: List<File>,
    binding: ProjectIntegrationBinding,
    showPicker: Boolean,
    onTogglePicker: (Boolean) -> Unit,
    onSelectProject: (String) -> Unit
) {
    SettingsCard {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Target Project",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (effectiveProjectPath.isNotBlank()) File(effectiveProjectPath).name else "No active project",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (projectList.isNotEmpty()) {
                    Box {
                        OutlinedButton(
                            onClick = { onTogglePicker(true) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Switch", fontSize = 12.sp)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(
                            expanded = showPicker,
                            onDismissRequest = { onTogglePicker(false) }
                        ) {
                            projectList.forEach { proj ->
                                DropdownMenuItem(
                                    text = { Text(proj.name) },
                                    onClick = {
                                        onTogglePicker(false)
                                        onSelectProject(proj.absolutePath)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                IntegrationStatusChip("Vercel", binding.vercel != null)
                IntegrationStatusChip("Supabase", binding.supabase != null)
                IntegrationStatusChip("CF Pages", binding.cloudflare != null)
            }
        }
    }
}

@Composable
private fun IntegrationStatusChip(label: String, isLinked: Boolean) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isLinked) AntigravityGreen.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = if (isLinked) Icons.Default.Check else Icons.Default.LinkOff,
                contentDescription = null,
                tint = if (isLinked) AntigravityGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isLinked) AntigravityGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ProviderAccountVaultSection(
    context: Context,
    provider: CloudProvider,
    accounts: List<CloudAccount>,
    activeAccount: CloudAccount?,
    verification: CloudVerificationResult?,
    isVerifying: Boolean,
    isAuthenticating: Boolean,
    authError: String?,
    onSelectAccount: (String) -> Unit,
    onAddAccount: (label: String, token: String) -> Unit,
    onVerifyAccount: (String) -> Unit,
    onRemoveAccount: (String) -> Unit
) {
    var showAddForm by remember(provider) { mutableStateOf(accounts.isEmpty()) }
    var labelInput by remember(provider) { mutableStateOf("") }
    var tokenInput by remember(provider) { mutableStateOf("") }
    var showAccountMenu by remember { mutableStateOf(false) }
    var wasAuthenticating by remember(provider) { mutableStateOf(false) }

    LaunchedEffect(provider, isAuthenticating, authError) {
        if (isAuthenticating) {
            wasAuthenticating = true
        } else if (wasAuthenticating) {
            wasAuthenticating = false
            if (authError.isNullOrBlank()) {
                labelInput = ""
                tokenInput = ""
                showAddForm = false
            }
        }
    }

    SettingsCard {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${provider.displayName} Account Vault",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Tokens are encrypted with Android KeyStore.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { showAddForm = !showAddForm }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (showAddForm) "Close" else "Add")
                }
            }

            if (accounts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { showAccountMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = activeAccount?.label ?: "Select account...",
                                color = if (activeAccount != null) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(
                            expanded = showAccountMenu,
                            onDismissRequest = { showAccountMenu = false }
                        ) {
                            accounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(acc.label, fontWeight = FontWeight.Medium)
                                            if (acc.handleOrEmail.isNotBlank()) {
                                                Text(acc.handleOrEmail, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    },
                                    onClick = {
                                        showAccountMenu = false
                                        onSelectAccount(acc.id)
                                    }
                                )
                            }
                        }
                    }

                    if (activeAccount != null) {
                        OutlinedButton(
                            onClick = { onVerifyAccount(activeAccount.id) },
                            enabled = !isVerifying,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            if (isVerifying) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Verify", fontSize = 12.sp)
                            }
                        }

                        IconButton(
                            onClick = { onRemoveAccount(activeAccount.id) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Remove account",
                                tint = AntigravityRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (verification != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (verification.isValid) AntigravityGreen.copy(alpha = 0.12f) else AntigravityRed.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = verification.message,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (verification.isValid) AntigravityGreen else AntigravityRed,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            AnimatedVisibility(visible = showAddForm) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    OutlinedTextField(
                        value = labelInput,
                        onValueChange = { labelInput = it },
                        label = { Text("Account Label (e.g. Personal, Work)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text("${provider.displayName} API Token") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!authError.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(authError, style = MaterialTheme.typography.bodySmall, color = AntigravityRed)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(provider.tokenPortalUrl))
                                context.startActivity(intent)
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Get Token")
                        }
                        Button(
                            onClick = {
                                onAddAccount(labelInput, tokenInput)
                            },
                            enabled = !isAuthenticating && tokenInput.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isAuthenticating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text("Verify & Save")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BindingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun VercelTabContent(
    context: Context,
    viewModel: AntigravityViewModel,
    effectiveProjectPath: String,
    binding: ProjectIntegrationBinding,
    accounts: List<CloudAccount>,
    activeAccount: CloudAccount?,
    verification: CloudVerificationResult?,
    isVerifying: Boolean,
    isAuthenticating: Boolean,
    authError: String?,
    isLoadingResources: Boolean,
    resourceError: String?
) {
    val teams by viewModel.vercelTeams.collectAsStateWithLifecycle()
    val selectedTeamId by viewModel.vercelSelectedTeamId.collectAsStateWithLifecycle()
    val projects by viewModel.vercelProjects.collectAsStateWithLifecycle()
    val deploymentsMap by viewModel.vercelDeployments.collectAsStateWithLifecycle()
    var searchQuery by remember(effectiveProjectPath) { mutableStateOf("") }
    var showTeamPicker by remember(effectiveProjectPath) { mutableStateOf(false) }
    var deploymentsDialogProject by remember(effectiveProjectPath) { mutableStateOf<VercelProjectItem?>(null) }

    val bound = binding.vercel
    SettingsCard {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Project Binding", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            if (bound != null) {
                Text(
                    text = "Linked: ${bound.projectName}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AntigravityGreen
                )
                Text(
                    text = "Project ID: ${bound.projectId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                BindingToggleRow(
                    title = "Inject VERCEL_* Env Vars",
                    subtitle = "Expose VERCEL_TOKEN, VERCEL_ORG_ID, VERCEL_PROJECT_ID to this project's shell & agent.",
                    checked = bound.injectEnv,
                    onCheckedChange = {
                        viewModel.updateVercelIntegrationToggles(context, effectiveProjectPath, it, bound.syncMcp)
                    }
                )
                BindingToggleRow(
                    title = "Sync Vercel MCP",
                    subtitle = "Update managed vercel-project entry in mcp_config.json when this workspace is active.",
                    checked = bound.syncMcp,
                    onCheckedChange = {
                        viewModel.updateVercelIntegrationToggles(context, effectiveProjectPath, bound.injectEnv, it)
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            viewModel.pullVercelEnvToWorkspace(
                                context,
                                effectiveProjectPath,
                                bound.accountId,
                                bound.projectId,
                                bound.teamId.takeIf { it.isNotBlank() }
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Pull .env.local", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.writeVercelProjectJson(
                                context,
                                effectiveProjectPath,
                                bound.orgId.ifBlank { bound.teamId },
                                bound.projectId
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Link .vercel", fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.unbindVercelFromProject(context, effectiveProjectPath) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AntigravityRed),
                    border = BorderStroke(1.dp, AntigravityRed.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Unlink Vercel from Project")
                }
            } else {
                Text(
                    text = "Not linked to this project (strict isolation — no Vercel tokens injected).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    ProviderAccountVaultSection(
        context = context,
        provider = CloudProvider.VERCEL,
        accounts = accounts,
        activeAccount = activeAccount,
        verification = verification,
        isVerifying = isVerifying,
        isAuthenticating = isAuthenticating,
        authError = authError,
        onSelectAccount = { viewModel.selectCloudAccount(CloudProvider.VERCEL, it) },
        onAddAccount = { label, token -> viewModel.addAndVerifyCloudAccount(context, CloudProvider.VERCEL, label, token) },
        onVerifyAccount = { viewModel.verifyCloudAccount(context, CloudProvider.VERCEL, it) },
        onRemoveAccount = { viewModel.removeCloudAccount(context, CloudProvider.VERCEL, it) }
    )

    if (activeAccount != null) {
        Spacer(modifier = Modifier.height(12.dp))
        SettingsCard {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Vercel Projects", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("Browse projects and link one to this workspace.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(
                        onClick = { viewModel.loadVercelResources(context, activeAccount.id, selectedTeamId) },
                        enabled = !isLoadingResources
                    ) {
                        if (isLoadingResources) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (projects.isEmpty()) "Load" else "Refresh")
                    }
                }

                if (teams.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box {
                        val teamName = teams.firstOrNull { it.id == selectedTeamId }?.name ?: "Personal Account"
                        OutlinedButton(onClick = { showTeamPicker = true }) {
                            Text("Scope: $teamName", fontSize = 12.sp)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(expanded = showTeamPicker, onDismissRequest = { showTeamPicker = false }) {
                            DropdownMenuItem(
                                text = { Text("Personal Account") },
                                onClick = {
                                    showTeamPicker = false
                                    viewModel.loadVercelResources(context, activeAccount.id, "")
                                }
                            )
                            teams.forEach { t ->
                                DropdownMenuItem(
                                    text = { Text(t.name) },
                                    onClick = {
                                        showTeamPicker = false
                                        viewModel.loadVercelResources(context, activeAccount.id, t.id)
                                    }
                                )
                            }
                        }
                    }
                }

                if (!resourceError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(resourceError, style = MaterialTheme.typography.bodySmall, color = AntigravityRed)
                }

                if (projects.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Filter Vercel projects") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val filtered = remember(projects, searchQuery) {
                        projects.filter { searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) }
                    }
                    filtered.take(30).forEachIndexed { idx, proj ->
                        if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(proj.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                val sub = listOfNotNull(
                                    proj.framework.takeIf { it.isNotBlank() },
                                    proj.productionUrl.takeIf { it.isNotBlank() }
                                ).joinToString(" • ")
                                if (sub.isNotBlank()) {
                                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            TextButton(
                                onClick = {
                                    deploymentsDialogProject = proj
                                    viewModel.loadVercelDeployments(context, activeAccount.id, proj.id, proj.teamId)
                                }
                            ) {
                                Text("Deploys", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    if (effectiveProjectPath.isBlank()) {
                                        Toast.makeText(context, "Select a project workspace first", Toast.LENGTH_SHORT).show()
                                    } else {
                                        viewModel.bindVercelToProject(context, effectiveProjectPath, activeAccount.id, proj)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Link", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    deploymentsDialogProject?.let { proj ->
        val deps: List<VercelDeploymentItem> = deploymentsMap[proj.id].orEmpty()
        AlertDialog(
            onDismissRequest = { deploymentsDialogProject = null },
            title = { Text("${proj.name} Deployments") },
            text = {
                Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    if (deps.isEmpty()) {
                        Text("Loading or no recent deployments found.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        deps.forEach { d ->
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                Text("${d.state} • ${d.target.ifBlank { "preview" }}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                Text(d.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { deploymentsDialogProject = null }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun SupabaseTabContent(
    context: Context,
    viewModel: AntigravityViewModel,
    effectiveProjectPath: String,
    binding: ProjectIntegrationBinding,
    accounts: List<CloudAccount>,
    activeAccount: CloudAccount?,
    verification: CloudVerificationResult?,
    isVerifying: Boolean,
    isAuthenticating: Boolean,
    authError: String?,
    isLoadingResources: Boolean,
    resourceError: String?
) {
    val projects by viewModel.supabaseProjects.collectAsStateWithLifecycle()
    var searchQuery by remember(effectiveProjectPath) { mutableStateOf("") }
    var includeServiceRole by remember(effectiveProjectPath) { mutableStateOf(false) }

    val bound = binding.supabase
    SettingsCard {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Project Binding", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            if (bound != null) {
                Text(
                    text = "Linked: ${bound.projectName} (${bound.projectRef})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AntigravityGreen
                )
                Text(
                    text = bound.apiUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                BindingToggleRow(
                    title = "Inject SUPABASE_* Env Vars",
                    subtitle = "Expose SUPABASE_ACCESS_TOKEN, SUPABASE_PROJECT_REF, SUPABASE_URL, SUPABASE_ANON_KEY.",
                    checked = bound.injectEnv,
                    onCheckedChange = {
                        viewModel.updateSupabaseIntegrationToggles(context, effectiveProjectPath, it, bound.syncMcp)
                    }
                )
                BindingToggleRow(
                    title = "Sync Supabase MCP",
                    subtitle = "Configure supabase-project remote MCP server for this project's ref when active.",
                    checked = bound.syncMcp,
                    onCheckedChange = {
                        viewModel.updateSupabaseIntegrationToggles(context, effectiveProjectPath, bound.injectEnv, it)
                    }
                )
                BindingToggleRow(
                    title = "Include service_role on .env.local pull",
                    subtitle = "Off by default for safety; enable only if backend scripts need SUPABASE_SERVICE_ROLE_KEY.",
                    checked = includeServiceRole,
                    onCheckedChange = { includeServiceRole = it }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            viewModel.pullSupabaseEnvToWorkspace(
                                context,
                                effectiveProjectPath,
                                bound.accountId,
                                bound.projectRef,
                                bound.apiUrl,
                                includeServiceRole
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Pull Keys to .env.local", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { viewModel.unbindSupabaseFromProject(context, effectiveProjectPath) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AntigravityRed),
                        border = BorderStroke(1.dp, AntigravityRed.copy(alpha = 0.4f))
                    ) {
                        Text("Unlink")
                    }
                }
            } else {
                Text(
                    text = "Not linked to this project (strict isolation — no Supabase credentials injected).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    ProviderAccountVaultSection(
        context = context,
        provider = CloudProvider.SUPABASE,
        accounts = accounts,
        activeAccount = activeAccount,
        verification = verification,
        isVerifying = isVerifying,
        isAuthenticating = isAuthenticating,
        authError = authError,
        onSelectAccount = { viewModel.selectCloudAccount(CloudProvider.SUPABASE, it) },
        onAddAccount = { label, token -> viewModel.addAndVerifyCloudAccount(context, CloudProvider.SUPABASE, label, token) },
        onVerifyAccount = { viewModel.verifyCloudAccount(context, CloudProvider.SUPABASE, it) },
        onRemoveAccount = { viewModel.removeCloudAccount(context, CloudProvider.SUPABASE, it) }
    )

    if (activeAccount != null) {
        Spacer(modifier = Modifier.height(12.dp))
        SettingsCard {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Supabase Projects", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("Link a Supabase project ref & API URL to this workspace.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(
                        onClick = { viewModel.loadSupabaseProjects(context, activeAccount.id) },
                        enabled = !isLoadingResources
                    ) {
                        if (isLoadingResources) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (projects.isEmpty()) "Load" else "Refresh")
                    }
                }

                if (!resourceError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(resourceError, style = MaterialTheme.typography.bodySmall, color = AntigravityRed)
                }

                if (projects.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Filter Supabase projects") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val filtered = remember(projects, searchQuery) {
                        projects.filter {
                            searchQuery.isBlank() ||
                                it.name.contains(searchQuery, ignoreCase = true) ||
                                it.ref.contains(searchQuery, ignoreCase = true)
                        }
                    }
                    filtered.take(30).forEachIndexed { idx, proj: SupabaseProjectItem ->
                        if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(proj.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "${proj.ref} • ${proj.region} • ${proj.status}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    if (effectiveProjectPath.isBlank()) {
                                        Toast.makeText(context, "Select a project workspace first", Toast.LENGTH_SHORT).show()
                                    } else {
                                        viewModel.bindSupabaseToProject(context, effectiveProjectPath, activeAccount.id, proj)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Link", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudflarePagesTabContent(
    context: Context,
    viewModel: AntigravityViewModel,
    effectiveProjectPath: String,
    binding: ProjectIntegrationBinding,
    accounts: List<CloudAccount>,
    activeAccount: CloudAccount?,
    verification: CloudVerificationResult?,
    isVerifying: Boolean,
    isAuthenticating: Boolean,
    authError: String?,
    isLoadingResources: Boolean,
    resourceError: String?
) {
    val cfAccounts by viewModel.cloudflareAccounts.collectAsStateWithLifecycle()
    val selectedCfAccountId by viewModel.cloudflareSelectedCfAccountId.collectAsStateWithLifecycle()
    val pagesProjects by viewModel.cloudflarePagesProjects.collectAsStateWithLifecycle()
    val deploymentsMap by viewModel.cloudflarePagesDeployments.collectAsStateWithLifecycle()
    var searchQuery by remember(effectiveProjectPath) { mutableStateOf("") }
    var showCfAccountPicker by remember(effectiveProjectPath) { mutableStateOf(false) }
    var deploymentsDialogProject by remember(effectiveProjectPath) { mutableStateOf<CloudflarePagesProjectItem?>(null) }

    val bound = binding.cloudflare
    SettingsCard {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Project Binding", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            if (bound != null) {
                Text(
                    text = "Linked Pages: ${bound.pagesProjectName}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = AntigravityGreen
                )
                val subText = listOfNotNull(
                    bound.cfAccountName.takeIf { it.isNotBlank() },
                    bound.subdomain.takeIf { it.isNotBlank() }?.let { "$it.pages.dev" }
                ).joinToString(" • ")
                if (subText.isNotBlank()) {
                    Text(subText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(8.dp))
                BindingToggleRow(
                    title = "Inject CLOUDFLARE_* Env Vars",
                    subtitle = "Expose CLOUDFLARE_API_TOKEN, CLOUDFLARE_ACCOUNT_ID, CF_PAGES_PROJECT_NAME for wrangler.",
                    checked = bound.injectEnv,
                    onCheckedChange = {
                        viewModel.updateCloudflareIntegrationToggles(context, effectiveProjectPath, it, bound.syncMcp)
                    }
                )
                BindingToggleRow(
                    title = "Sync Cloudflare MCP",
                    subtitle = "Update managed cloudflare-pages-project entry in mcp_config.json when active.",
                    checked = bound.syncMcp,
                    onCheckedChange = {
                        viewModel.updateCloudflareIntegrationToggles(context, effectiveProjectPath, bound.injectEnv, it)
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.unbindCloudflareFromProject(context, effectiveProjectPath) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AntigravityRed),
                    border = BorderStroke(1.dp, AntigravityRed.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Unlink Cloudflare Pages from Project")
                }
            } else {
                Text(
                    text = "Not linked to this project (strict isolation — no Cloudflare tokens injected).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    ProviderAccountVaultSection(
        context = context,
        provider = CloudProvider.CLOUDFLARE,
        accounts = accounts,
        activeAccount = activeAccount,
        verification = verification,
        isVerifying = isVerifying,
        isAuthenticating = isAuthenticating,
        authError = authError,
        onSelectAccount = { viewModel.selectCloudAccount(CloudProvider.CLOUDFLARE, it) },
        onAddAccount = { label, token -> viewModel.addAndVerifyCloudAccount(context, CloudProvider.CLOUDFLARE, label, token) },
        onVerifyAccount = { viewModel.verifyCloudAccount(context, CloudProvider.CLOUDFLARE, it) },
        onRemoveAccount = { viewModel.removeCloudAccount(context, CloudProvider.CLOUDFLARE, it) }
    )

    if (activeAccount != null) {
        Spacer(modifier = Modifier.height(12.dp))
        SettingsCard {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Cloudflare Pages", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("Browse Pages projects & deployments.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.loadCloudflarePages(
                                context,
                                activeAccount.id,
                                selectedCfAccountId.takeIf { it.isNotBlank() }
                            )
                        },
                        enabled = !isLoadingResources
                    ) {
                        if (isLoadingResources) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (pagesProjects.isEmpty()) "Load" else "Refresh")
                    }
                }

                if (cfAccounts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val activeCfName = cfAccounts.firstOrNull { it.id == selectedCfAccountId }?.name ?: selectedCfAccountId
                    Box {
                        OutlinedButton(onClick = { showCfAccountPicker = true }) {
                            Text("Account: $activeCfName", fontSize = 12.sp)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(expanded = showCfAccountPicker, onDismissRequest = { showCfAccountPicker = false }) {
                            cfAccounts.forEach { cfAcc ->
                                DropdownMenuItem(
                                    text = { Text(cfAcc.name) },
                                    onClick = {
                                        showCfAccountPicker = false
                                        viewModel.loadCloudflarePages(context, activeAccount.id, cfAcc.id)
                                    }
                                )
                            }
                        }
                    }
                }

                if (!resourceError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(resourceError, style = MaterialTheme.typography.bodySmall, color = AntigravityRed)
                }

                if (pagesProjects.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Filter Pages projects") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val filtered = remember(pagesProjects, searchQuery) {
                        pagesProjects.filter { searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) }
                    }
                    val cfAccName = cfAccounts.firstOrNull { it.id == selectedCfAccountId }?.name.orEmpty()
                    filtered.take(30).forEachIndexed { idx, proj ->
                        if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(proj.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                val sub = listOfNotNull(
                                    proj.subdomain.takeIf { it.isNotBlank() }?.let { "$it.pages.dev" },
                                    proj.productionBranch.takeIf { it.isNotBlank() },
                                    proj.latestDeploymentState.takeIf { it.isNotBlank() }
                                ).joinToString(" • ")
                                if (sub.isNotBlank()) {
                                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            TextButton(
                                onClick = {
                                    deploymentsDialogProject = proj
                                    viewModel.loadCloudflarePagesDeployments(context, activeAccount.id, proj.accountId, proj.name)
                                }
                            ) {
                                Text("Deploys", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    if (effectiveProjectPath.isBlank()) {
                                        Toast.makeText(context, "Select a project workspace first", Toast.LENGTH_SHORT).show()
                                    } else {
                                        viewModel.bindCloudflarePagesToProject(
                                            context = context,
                                            projectPath = effectiveProjectPath,
                                            accountId = activeAccount.id,
                                            cfAccountId = proj.accountId,
                                            cfAccountName = cfAccName,
                                            project = proj
                                        )
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Link", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    deploymentsDialogProject?.let { proj ->
        val deps: List<CloudflarePagesDeploymentItem> = deploymentsMap[proj.name].orEmpty()
        AlertDialog(
            onDismissRequest = { deploymentsDialogProject = null },
            title = { Text("${proj.name} Pages Deploys") },
            text = {
                Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    if (deps.isEmpty()) {
                        Text("Loading or no recent deployments found.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        deps.forEach { d ->
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                Text("${d.stageStatus.ifBlank { "unknown" }} • ${d.environment} (${d.branch})", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                Text(d.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { deploymentsDialogProject = null }) { Text("Close") }
            }
        )
    }
}
