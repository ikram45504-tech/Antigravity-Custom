package id.vern.antigravity.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import id.vern.antigravity.service.PromptCompletionNotifier
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import id.vern.antigravity.oauth.OAuthManager
import id.vern.antigravity.service.AgyModelsService
import id.vern.antigravity.service.RuleItem
import id.vern.antigravity.service.McpServerItem
import id.vern.antigravity.ui.components.InstallationProgressDialog
import id.vern.antigravity.ui.components.getModelQuotaPercentage
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.ui.graphics.SolidColor
import id.vern.antigravity.service.PermissionRule
import id.vern.antigravity.ui.components.DialogBlurEffect
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.vern.antigravity.data.ThemeMode
import id.vern.antigravity.data.ThemePreset
import id.vern.antigravity.data.ModelAvailability
import id.vern.antigravity.data.AvailableModelInfo
import id.vern.antigravity.data.SubscriptionTierType
import id.vern.antigravity.oauth.GitHubRepoItem
import id.vern.antigravity.oauth.StoredAccount
import id.vern.antigravity.ui.components.LogViewer
import id.vern.antigravity.ui.theme.AntigravityBlue
import id.vern.antigravity.ui.theme.AntigravityGreen
import id.vern.antigravity.ui.theme.AntigravityRed
import id.vern.antigravity.ui.theme.AntigravityAmber
import id.vern.antigravity.ui.theme.parseColorOrNull
import id.vern.antigravity.ui.theme.isColorDark
import id.vern.antigravity.viewmodel.AntigravityViewModel
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import id.vern.antigravity.R
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun GitHubSettingsPage(
    viewModel: AntigravityViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val githubAccount by viewModel.githubAccount.collectAsStateWithLifecycle()
    val githubAccounts by viewModel.githubAccounts.collectAsStateWithLifecycle()
    val githubVerification by viewModel.githubVerification.collectAsStateWithLifecycle()
    val isGithubVerifying by viewModel.isGithubVerifying.collectAsStateWithLifecycle()
    val githubRepos by viewModel.githubRepos.collectAsStateWithLifecycle()
    val isLoadingGithubRepos by viewModel.isLoadingGithubRepos.collectAsStateWithLifecycle()
    val githubReposError by viewModel.githubReposError.collectAsStateWithLifecycle()
    val isGitActionRunning by viewModel.isGitActionRunning.collectAsStateWithLifecycle()
    val deviceCode by viewModel.githubDeviceCode.collectAsStateWithLifecycle()
    val isAuthenticating by viewModel.isGithubAuthenticating.collectAsStateWithLifecycle()
    val authError by viewModel.githubAuthError.collectAsStateWithLifecycle()
    var patInput by remember { mutableStateOf("") }
    var showPatOption by remember { mutableStateOf(false) }
    var showAddAnotherAccount by remember { mutableStateOf(false) }
    var repoSearchQuery by remember { mutableStateOf("") }
    var repoToClone by remember { mutableStateOf<GitHubRepoItem?>(null) }
    var cloneParentPath by remember { mutableStateOf("/storage/emulated/0/Projects") }
    var cloneFolderName by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        viewModel.refreshGitHubState(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        SubSheetHeader(
            title = "GitHub",
            subtitle = "Connect your GitHub account for git & repositories",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
        ) {
            if (githubAccount != null) {
                val acc = githubAccount!!
                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(AntigravityBlue.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!acc.avatarUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = acc.avatarUrl,
                                        contentDescription = acc.login,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        text = acc.login.take(1).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        color = AntigravityBlue
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = acc.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "@${acc.login}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Connected",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AntigravityGreen,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Email: ${acc.email}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Git credentials are encrypted at rest and synced to ~/.gitconfig for all repositories.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )

                        if (githubVerification != null) {
                            val ver = githubVerification!!
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (ver.isValid) AntigravityGreen.copy(alpha = 0.12f) else AntigravityRed.copy(alpha = 0.12f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = ver.message,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (ver.isValid) AntigravityGreen else AntigravityRed
                                    )
                                    if (ver.scopes.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Scopes: ${ver.scopes.joinToString(", ")}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.verifyGitHubConnection(context) },
                                enabled = !isGithubVerifying,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isGithubVerifying) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text("Verify & Sync")
                            }
                            OutlinedButton(
                                onClick = { showAddAnotherAccount = !showAddAnotherAccount },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (showAddAnotherAccount) "Close" else "Add Account")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.disconnectGitHub(context) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AntigravityRed),
                            border = BorderStroke(1.dp, AntigravityRed.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Disconnect @${acc.login}")
                        }
                    }
                }

                val otherGithubAccounts = remember(githubAccounts, acc.login) {
                    githubAccounts.filter { !it.login.equals(acc.login, ignoreCase = true) }
                }
                if (otherGithubAccounts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    SettingsCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Switch GitHub Account",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            otherGithubAccounts.forEach { other ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { viewModel.switchGitHubAccount(context, other.login) }
                                        .padding(vertical = 8.dp, horizontal = 6.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = other.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "@${other.login}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    TextButton(onClick = { viewModel.switchGitHubAccount(context, other.login) }) {
                                        Text("Switch")
                                    }
                                    IconButton(
                                        onClick = { viewModel.removeGitHubAccount(context, other.login) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Remove @${other.login}",
                                            tint = AntigravityRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Your Repositories",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Browse and clone directly into your projects",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = { viewModel.loadGitHubRepositories(context) },
                                enabled = !isLoadingGithubRepos
                            ) {
                                if (isLoadingGithubRepos) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (githubRepos.isEmpty()) "Load" else "Refresh")
                            }
                        }

                        if (!githubReposError.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = githubReposError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = AntigravityRed
                            )
                        }

                        if (githubRepos.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = repoSearchQuery,
                                onValueChange = { repoSearchQuery = it },
                                label = { Text("Filter repositories") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            val filteredRepos = remember(githubRepos, repoSearchQuery) {
                                githubRepos.filter {
                                    repoSearchQuery.isBlank() ||
                                        it.fullName.contains(repoSearchQuery, ignoreCase = true) ||
                                        it.description.contains(repoSearchQuery, ignoreCase = true)
                                }
                            }
                            filteredRepos.take(25).forEachIndexed { idx, repo ->
                                if (idx > 0) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = repo.fullName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (repo.isPrivate) AntigravityAmber.copy(alpha = 0.16f) else AntigravityBlue.copy(alpha = 0.14f)
                                            ) {
                                                Text(
                                                    text = if (repo.isPrivate) "Private" else "Public",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (repo.isPrivate) AntigravityAmber else AntigravityBlue,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        if (repo.description.isNotBlank()) {
                                            Text(
                                                text = repo.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            repoToClone = repo
                                            cloneFolderName = repo.name
                                        },
                                        enabled = !isGitActionRunning,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Clone", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (githubAccount == null || showAddAnotherAccount) {
                if (githubAccount != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                }
                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Connect GitHub Account",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Sign in with GitHub Device Flow to clone, pull, and push across all your repositories.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (!authError.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = authError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = AntigravityRed
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (deviceCode != null) {
                            val code = deviceCode!!
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text(
                                        text = "Enter this one-time code on GitHub:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = code.userCode,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                cm.setPrimaryClip(ClipData.newPlainText("GitHub Code", code.userCode))
                                                Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Copy Code")
                                        }
                                        Button(
                                            onClick = {
                                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                cm.setPrimaryClip(ClipData.newPlainText("GitHub Code", code.userCode))
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(code.verificationUri))
                                                context.startActivity(intent)
                                            }
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Open GitHub")
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Waiting for authorization...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    TextButton(onClick = { viewModel.cancelGitHubDeviceFlow() }) {
                                        Text("Cancel")
                                    }
                                }
                            }
                        } else {
                            Button(
                                onClick = { viewModel.startGitHubDeviceFlow(context) },
                                enabled = !isAuthenticating,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isAuthenticating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text("Connect with GitHub")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(
                            onClick = { showPatOption = !showPatOption },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text(if (showPatOption) "Hide token input" else "Or use a Personal Access Token")
                        }

                        AnimatedVisibility(visible = showPatOption) {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                OutlinedTextField(
                                    value = patInput,
                                    onValueChange = { patInput = it },
                                    label = { Text("GitHub Token (ghp_... or github_pat_...)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        viewModel.connectGitHubWithPat(context, patInput)
                                        patInput = ""
                                    },
                                    enabled = !isAuthenticating && patInput.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Save Token")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (repoToClone != null) {
        val targetRepo = repoToClone!!
        AlertDialog(
            onDismissRequest = { repoToClone = null },
            title = { Text("Clone ${targetRepo.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = cloneParentPath,
                        onValueChange = { cloneParentPath = it },
                        label = { Text("Destination Folder") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = cloneFolderName,
                        onValueChange = { cloneFolderName = it },
                        label = { Text("Repository Folder Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parentDir = File(cloneParentPath.trim().ifBlank { "/storage/emulated/0/Projects" })
                        val folderName = cloneFolderName.trim().ifBlank { targetRepo.name }
                        repoToClone = null
                        Toast.makeText(context, "Cloning ${targetRepo.name}...", Toast.LENGTH_SHORT).show()
                        viewModel.cloneGitHubRepository(
                            context = context,
                            cloneUrl = targetRepo.cloneUrl,
                            destinationParentDir = parentDir,
                            repoName = folderName
                        ) { result, _ ->
                            Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                            if (result.success) {
                                onBack()
                            }
                        }
                    },
                    enabled = cloneFolderName.isNotBlank()
                ) {
                    Text("Clone & Open")
                }
            },
            dismissButton = {
                TextButton(onClick = { repoToClone = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

