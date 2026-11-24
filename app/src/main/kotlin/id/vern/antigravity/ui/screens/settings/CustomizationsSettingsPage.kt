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
fun CustomizationsSettingsPage(
    viewModel: AntigravityViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var showRulesBreakdown by remember { mutableStateOf(false) }
    var showOtherBreakdown by remember { mutableStateOf(false) }
    var showAddMcpDialog by remember { mutableStateOf(false) }
    var showRawMcpConfigDialog by remember { mutableStateOf(false) }
    var showSkillsDialog by remember { mutableStateOf(false) }
    var showRulesDialog by remember { mutableStateOf(false) }
    var showPluginsDialog by remember { mutableStateOf(false) }

    val installedSkills by viewModel.installedSkills.collectAsStateWithLifecycle()
    val installedPlugins by viewModel.installedPlugins.collectAsStateWithLifecycle()
    val installedRulesList by viewModel.installedRulesList.collectAsStateWithLifecycle()
    val mcpServers by viewModel.mcpServers.collectAsStateWithLifecycle()
    val tokenStats by viewModel.tokenUsageStats.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refreshRules(context)
        viewModel.refreshSkills(context)
        viewModel.refreshPlugins(context)
        viewModel.refreshMcpServers(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        SubSheetHeader(
            title = "Customizations",
            subtitle = "Configure default behaviors, skills, and MCP servers. Learn more.",
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
        ) {

            SettingsSectionTitle(title = "Token Usage")
            SettingsCard {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Context token footprint of loaded customizations.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    val rulesPct = if (tokenStats.totalLimit > 0) (tokenStats.rulesTokens.toFloat() / tokenStats.totalLimit * 100f) else 0f
                    TokenUsageItem(
                        label = "Rules",
                        usedTokens = tokenStats.rulesTokens,
                        totalTokens = tokenStats.totalLimit,
                        percentage = rulesPct,
                        color = AntigravityBlue,
                        isExpanded = showRulesBreakdown,
                        breakdownLabel = "Show ${tokenStats.rulesBreakdowns.size} breakdowns",
                        onToggleBreakdown = { showRulesBreakdown = !showRulesBreakdown },
                        breakdowns = tokenStats.rulesBreakdowns
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val skillsPct = if (tokenStats.totalLimit > 0) (tokenStats.skillsTokens.toFloat() / tokenStats.totalLimit * 100f) else 0f
                    TokenUsageItem(
                        label = "Skills",
                        usedTokens = tokenStats.skillsTokens,
                        totalTokens = tokenStats.totalLimit,
                        percentage = skillsPct,
                        color = AntigravityGreen,
                        isExpanded = showOtherBreakdown,
                        breakdownLabel = "Show ${tokenStats.skillsBreakdowns.size} breakdowns",
                        onToggleBreakdown = { showOtherBreakdown = !showOtherBreakdown },
                        breakdowns = tokenStats.skillsBreakdowns
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsCard {
                Column {

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSkillsDialog = true }
                            .padding(horizontal = 14.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_customization_skills_label),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${installedSkills.size}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showRulesDialog = true }
                            .padding(horizontal = 14.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_customization_rules_label),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${installedRulesList.size}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showPluginsDialog = true }
                            .padding(horizontal = 14.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_customization_plugins_label),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${installedPlugins.size}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Installed MCP Servers",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (mcpServers.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        BadgePill(text = "${mcpServers.size}")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            viewModel.refreshMcpServers(context)
                            Toast.makeText(context, "MCP servers refreshed (${mcpServers.size})", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.mcp_btn_refresh),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    OutlinedButton(
                        onClick = { showRawMcpConfigDialog = true },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(stringResource(R.string.mcp_btn_config), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = { showAddMcpDialog = true },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(stringResource(R.string.mcp_btn_add), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            SettingsCard {
                if (mcpServers.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp, horizontal = 16.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No MCP servers installed",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Use Add MCP to configure a local tool or remote service, or edit mcp_config.json directly.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        mcpServers.forEach { server ->
                            var showDeleteServerConfirm by remember { mutableStateOf(false) }

                            if (showDeleteServerConfirm) {
                                AlertDialog(
                                    onDismissRequest = { showDeleteServerConfirm = false },
                                    shape = RoundedCornerShape(16.dp),
                                    title = {
                                        DialogBlurEffect()
                                        Text(stringResource(R.string.settings_mcp_delete_title), fontWeight = FontWeight.Bold)
                                    },
                                    text = { Text(stringResource(R.string.settings_mcp_delete_confirm, server.name)) },
                                    confirmButton = {
                                        Button(
                                            onClick = {
                                                viewModel.deleteMcpServer(context, server.name) { ok ->
                                                    if (ok) Toast.makeText(context, context.getString(R.string.settings_mcp_deleted_toast, server.name), Toast.LENGTH_SHORT).show()
                                                }
                                                showDeleteServerConfirm = false
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue)
                                        ) {
                                            Text(stringResource(R.string.settings_mcp_delete_btn))
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showDeleteServerConfirm = false }) { Text(stringResource(R.string.btn_cancel)) }
                                    }
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = server.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = if (server.isRemote) Color(0xFF6366F1).copy(alpha = 0.2f) else AntigravityBlue.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = if (server.isRemote) "Remote HTTP" else "Stdio",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (server.isRemote) Color(0xFF818CF8) else AntigravityBlue,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = AntigravityGreen.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = "Active",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = AntigravityGreen,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = server.displayTarget,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (server.source != "Global") {
                                        Text(
                                            text = "Source: ${server.source}",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { showDeleteServerConfirm = true },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.mcp_btn_delete_server),
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    if (showAddMcpDialog) {
        AddMcpServerDialog(
            onConfirm = { server ->
                viewModel.saveMcpServer(context, server) { ok ->
                    if (ok) {
                        Toast.makeText(context, "MCP server '${server.name}' configured!", Toast.LENGTH_SHORT).show()
                        showAddMcpDialog = false
                    } else {
                        Toast.makeText(context, "Failed to configure MCP server", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { showAddMcpDialog = false }
        )
    }

    if (showRawMcpConfigDialog) {
        var rawJsonText by remember { mutableStateOf("") }
        var isRawLoading by remember { mutableStateOf(true) }

        LaunchedEffect(Unit) {
            rawJsonText = viewModel.loadRawMcpConfig(context)
            isRawLoading = false
        }

        if (!isRawLoading) {
            RawMcpConfigDialog(
                initialJson = rawJsonText,
                onSave = { updatedJson ->
                    viewModel.saveRawMcpConfig(context, updatedJson) { ok ->
                        if (ok) {
                            Toast.makeText(context, "mcp_config.json saved!", Toast.LENGTH_SHORT).show()
                            showRawMcpConfigDialog = false
                        } else {
                            Toast.makeText(context, "Invalid JSON format", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onDismiss = { showRawMcpConfigDialog = false }
            )
        }
    }

    if (showSkillsDialog) {
        SkillsManagementDialog(
            viewModel = viewModel,
            onDismiss = { showSkillsDialog = false }
        )
    }

    if (showRulesDialog) {
        RulesManagementDialog(
            viewModel = viewModel,
            onDismiss = { showRulesDialog = false }
        )
    }

    if (showPluginsDialog) {
        PluginsManagementDialog(
            viewModel = viewModel,
            onDismiss = { showPluginsDialog = false }
        )
    }
}

@Composable
fun RulesManagementDialog(
    viewModel: AntigravityViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val rulesList by viewModel.installedRulesList.collectAsStateWithLifecycle()
    var editingRule by remember { mutableStateOf<RuleItem?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }
    var newRuleName by remember { mutableStateOf("") }
    var newRuleContent by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.refreshRules(context)
    }

    if (editingRule != null) {
        RuleEditorDialog(
            rule = editingRule!!,
            onSave = { updatedContent ->
                viewModel.saveRuleContent(context, editingRule!!.filePath, updatedContent) { ok ->
                    if (ok) {
                        Toast.makeText(context, "Rule saved successfully", Toast.LENGTH_SHORT).show()
                        editingRule = null
                    } else {
                        Toast.makeText(context, "Failed to save rule", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDelete = {
                viewModel.deleteRule(context, editingRule!!.filePath) { ok ->
                    if (ok) {
                        Toast.makeText(context, "Rule deleted", Toast.LENGTH_SHORT).show()
                        editingRule = null
                    }
                }
            },
            onDismiss = { editingRule = null }
        )
    }

    if (isCreatingNew) {
        AlertDialog(
            onDismissRequest = { isCreatingNew = false },
            shape = RoundedCornerShape(16.dp),
            title = {
                DialogBlurEffect()
                Text(stringResource(R.string.settings_rules_create_title), fontWeight = FontWeight.Bold)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.settings_rules_create_filename_hint), style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newRuleName,
                        onValueChange = { newRuleName = it },
                        label = { Text(stringResource(R.string.settings_rules_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(stringResource(R.string.settings_rules_content_hint), style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = newRuleContent,
                        onValueChange = { newRuleContent = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        maxLines = 10
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newRuleName.isNotBlank()) {
                            viewModel.createRule(context, newRuleName.trim(), newRuleContent) { ok ->
                                if (ok) {
                                    Toast.makeText(context, context.getString(R.string.settings_rules_created_toast), Toast.LENGTH_SHORT).show()
                                    isCreatingNew = false
                                    newRuleName = ""
                                    newRuleContent = ""
                                } else {
                                    Toast.makeText(context, context.getString(R.string.settings_rules_create_failed_toast), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue)
                ) {
                    Text(stringResource(R.string.settings_rules_btn_create))
                }
            },
            dismissButton = {
                TextButton(onClick = { isCreatingNew = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = {
            DialogBlurEffect()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_rules_mgmt_title, rulesList.size),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Button(
                    onClick = { isCreatingNew = true },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(stringResource(R.string.settings_rules_btn_new), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (rulesList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.settings_rules_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    rulesList.forEach { rule ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f))
                                .clickable { editingRule = rule }
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rule.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = AntigravityBlue.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = rule.source,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = AntigravityBlue,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { editingRule = rule },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.rules_edit_rule), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                    }
                                    if (rule.source != "Global") {
                                        IconButton(
                                            onClick = {
                                                viewModel.deleteRule(context, rule.filePath) { ok ->
                                                    if (ok) Toast.makeText(context, context.getString(R.string.settings_rules_deleted_named_toast, rule.name), Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.rules_delete_rule), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${rule.lineCount} lines • ${rule.byteSize} bytes • ${rule.filePath}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
fun RuleEditorDialog(
    rule: RuleItem,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var contentText by remember(rule.content) { mutableStateOf(rule.content) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            shape = RoundedCornerShape(16.dp),
            title = {
                DialogBlurEffect()
                Text(stringResource(R.string.settings_rules_delete_title), fontWeight = FontWeight.Bold)
            },
            text = { Text(stringResource(R.string.settings_rules_delete_confirm, rule.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue)
                ) {
                    Text(stringResource(R.string.settings_rules_btn_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = {
            DialogBlurEffect()
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_rules_edit_title, rule.name),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (rule.source != "Global") {
                        IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.common_delete), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.settings_rules_size_format, rule.filePath, contentText.toByteArray(Charsets.UTF_8).size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                ) {
                    BasicTextField(
                        value = contentText,
                        onValueChange = { contentText = it },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState()),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        ),
                        cursorBrush = SolidColor(AntigravityBlue)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(contentText) },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue)
            ) {
                Text(stringResource(R.string.settings_rules_btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}

@Composable
fun AddMcpServerDialog(
    onConfirm: (McpServerItem) -> Unit,
    onDismiss: () -> Unit
) {
    var serverName by remember { mutableStateOf("") }
    var transportType by remember { mutableIntStateOf(0) }
    var command by remember { mutableStateOf("") }
    var argsText by remember { mutableStateOf("") }
    var urlText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = {
            DialogBlurEffect()
            Text(stringResource(R.string.settings_mcp_add_title), fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.settings_mcp_add_desc), style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(3.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (transportType == 0) AntigravityBlue else Color.Transparent)
                                .clickable { transportType = 0 }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(stringResource(R.string.settings_mcp_tab_stdio), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (transportType == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (transportType == 1) AntigravityBlue else Color.Transparent)
                                .clickable { transportType = 1 }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(stringResource(R.string.settings_mcp_tab_http), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (transportType == 1) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = serverName,
                    onValueChange = { serverName = it },
                    label = { Text(stringResource(R.string.settings_mcp_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (transportType == 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it },
                        label = { Text(stringResource(R.string.settings_mcp_command_label)) },
                        placeholder = { Text(stringResource(R.string.settings_mcp_command_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = argsText,
                        onValueChange = { argsText = it },
                        label = { Text(stringResource(R.string.settings_mcp_args_label)) },
                        placeholder = { Text(stringResource(R.string.settings_mcp_args_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = urlText,
                        onValueChange = { urlText = it },
                        label = { Text(stringResource(R.string.settings_mcp_url_label)) },
                        placeholder = { Text(stringResource(R.string.settings_mcp_url_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (serverName.isNotBlank()) {
                        val isRemote = transportType == 1
                        val argsList = if (argsText.isNotBlank()) argsText.trim().split(Regex("\\s+")) else emptyList()
                        val item = McpServerItem(
                            name = serverName.trim(),
                            command = command.trim(),
                            args = argsList,
                            url = urlText.trim(),
                            isRemote = isRemote
                        )
                        onConfirm(item)
                    }
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue)
            ) {
                Text(stringResource(R.string.settings_mcp_btn_install))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}

@Composable
fun RawMcpConfigDialog(
    initialJson: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var rawText by remember(initialJson) { mutableStateOf(initialJson) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = {
            DialogBlurEffect()
            Text(stringResource(R.string.settings_mcp_raw_title), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth().height(320.dp)
                ) {
                    BasicTextField(
                        value = rawText,
                        onValueChange = { rawText = it },
                        modifier = Modifier.fillMaxSize().padding(10.dp).verticalScroll(rememberScrollState()),
                        textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        cursorBrush = SolidColor(AntigravityBlue)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(rawText) },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue)
            ) {
                Text(stringResource(R.string.settings_mcp_btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}

enum class CustomizationKind(
    val titleRes: Int,
    val emptyRes: Int,
    val addTitleRes: Int,
    val addDescRes: Int,
    val nameLabelRes: Int,
    val descLabelRes: Int,
    val isSkill: Boolean
) {
    SKILLS(
        titleRes = R.string.customization_skills_title,
        emptyRes = R.string.customization_skills_empty,
        addTitleRes = R.string.customization_add_skill_title,
        addDescRes = R.string.customization_add_skill_desc,
        nameLabelRes = R.string.customization_skill_name_label,
        descLabelRes = R.string.customization_desc_label,
        isSkill = true
    ),
    PLUGINS(
        titleRes = R.string.customization_plugins_title,
        emptyRes = R.string.customization_plugins_empty,
        addTitleRes = R.string.customization_add_plugin_title,
        addDescRes = R.string.customization_add_plugin_desc,
        nameLabelRes = R.string.customization_plugin_name_label,
        descLabelRes = R.string.customization_plugin_desc_label,
        isSkill = false
    )
}

@Composable
fun CustomizationManagementDialog(
    kind: CustomizationKind,
    viewModel: AntigravityViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val installedSkills by viewModel.installedSkills.collectAsStateWithLifecycle()
    val installedPlugins by viewModel.installedPlugins.collectAsStateWithLifecycle()
    val customizationTracks by viewModel.customizationTracks.collectAsStateWithLifecycle()
    val isCheckingUpdates by viewModel.isCheckingCustomizationUpdates.collectAsStateWithLifecycle()
    val itemsList = if (kind.isSkill) installedSkills else installedPlugins
    val skillsCatalogue by viewModel.skillsCatalogue.collectAsStateWithLifecycle()
    val pluginsCatalogue by viewModel.pluginsCatalogue.collectAsStateWithLifecycle()
    val catalogueItems = if (kind.isSkill) skillsCatalogue else pluginsCatalogue
    val installingItemId by viewModel.installingItemId.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var addDialogRepoUrl by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadCachedCatalogue(context)
        viewModel.fetchRemoteCatalogue(context, force = false)
        viewModel.checkForCustomizationUpdates(context, force = false)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = {
            DialogBlurEffect()
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(kind.titleRes),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Button(
                        onClick = { showAddDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(stringResource(R.string.customization_btn_custom), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(3.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selectedTab == 0) AntigravityBlue else Color.Transparent)
                                .clickable { selectedTab = 0 }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${stringResource(R.string.customization_tab_installed)} (${itemsList.size})",
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selectedTab == 1) AntigravityBlue else Color.Transparent)
                                .clickable { selectedTab = 1 }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${stringResource(R.string.customization_tab_marketplace)} (${catalogueItems.size})",
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == 1) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (selectedTab == 0) {
                    val pendingUpdatesCount = itemsList.count { item ->
                        val track = customizationTracks[item.name.lowercase()] ?: customizationTracks[item.id.lowercase()]
                        track?.hasUpdate == true
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp, start = 2.dp, end = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isCheckingUpdates) stringResource(R.string.customization_checking_updates)
                            else if (pendingUpdatesCount > 0) stringResource(R.string.customization_updates_found, pendingUpdatesCount)
                            else stringResource(R.string.customization_up_to_date),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = { viewModel.checkForCustomizationUpdates(context, force = true) },
                            enabled = !isCheckingUpdates,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            if (isCheckingUpdates) {
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(4.dp))
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(stringResource(R.string.customization_btn_check_updates), fontSize = 11.sp)
                        }
                    }

                    if (itemsList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(kind.emptyRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        itemsList.forEach { item ->
                            val track = customizationTracks[item.name.lowercase()] ?: customizationTracks[item.id.lowercase()]
                            val hasUpdate = track?.hasUpdate == true

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        val isFromPlugin = item.path?.contains("/plugins/") == true
                                        val badgeText = when {
                                            item.isBuiltIn -> stringResource(R.string.customization_badge_builtin)
                                            isFromPlugin -> stringResource(R.string.customization_badge_plugin)
                                            else -> stringResource(R.string.customization_badge_custom)
                                        }
                                        val badgeColor = when {
                                            item.isBuiltIn -> MaterialTheme.colorScheme.surfaceVariant
                                            isFromPlugin -> AntigravityGreen.copy(alpha = 0.2f)
                                            else -> AntigravityBlue.copy(alpha = 0.2f)
                                        }
                                        val badgeTextColor = when {
                                            item.isBuiltIn -> MaterialTheme.colorScheme.onSurfaceVariant
                                            isFromPlugin -> AntigravityGreen
                                            else -> AntigravityBlue
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = badgeColor
                                        ) {
                                            Text(
                                                text = badgeText,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = badgeTextColor,
                                                maxLines = 1,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        if (hasUpdate) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = AntigravityAmber.copy(alpha = 0.2f)
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.customization_badge_update_available),
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = AntigravityAmber,
                                                    maxLines = 1,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (track != null && track.hasUpdate) {
                                            Button(
                                                onClick = {
                                                    viewModel.updateCustomizationViaTerminal(context, track)
                                                    onDismiss()
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = AntigravityAmber),
                                                modifier = Modifier.height(28.dp)
                                            ) {
                                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(stringResource(R.string.customization_btn_update), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        if (!item.isBuiltIn) {
                                            IconButton(
                                                onClick = {
                                                    if (kind.isSkill) {
                                                        viewModel.uninstallSkill(context, item.name)
                                                    } else {
                                                        viewModel.uninstallPlugin(context, item.name)
                                                    }
                                                    val typeName = if (kind.isSkill) "Skill" else "Plugin"
                                                    Toast.makeText(context, context.getString(R.string.customization_removed_toast, typeName, item.name), Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = stringResource(R.string.customization_uninstall_desc, item.name),
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    catalogueItems.forEach { item ->
                        val isInstalled = itemsList.any { it.name.equals(item.name, ignoreCase = true) || it.id.equals(item.id, ignoreCase = true) }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(10.dp)
                        ) {

                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = item.category,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                if (kind.isSkill && item.repoUrl.isNotBlank() && (item.subPath.isNullOrBlank() || item.id == "antigravity-skills-manager")) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = AntigravityBlue.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.customization_vault_badge),
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AntigravityBlue,
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = item.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isInstalled) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = AntigravityGreen.copy(alpha = 0.15f)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = AntigravityGreen,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = stringResource(R.string.customization_btn_installed),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AntigravityGreen
                                            )
                                        }
                                    }
                                } else if (installingItemId == item.id) {
                                    Button(
                                        onClick = {},
                                        enabled = false,
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue.copy(alpha = 0.6f)),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(12.dp),
                                            strokeWidth = 2.dp,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(stringResource(R.string.customization_btn_installing), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    val isMultiSkillVault = kind.isSkill && item.repoUrl.isNotBlank() && (item.subPath.isNullOrBlank() || item.id == "antigravity-skills-manager")
                                    if (isMultiSkillVault) {
                                        OutlinedButton(
                                            onClick = {
                                                addDialogRepoUrl = item.repoUrl
                                                showAddDialog = true
                                                viewModel.inspectRepoForSkills(context, item.repoUrl)
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(stringResource(R.string.customization_btn_browse_skills), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Button(
                                            onClick = {
                                                viewModel.installSkillViaTerminal(context, item)
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(stringResource(R.string.customization_btn_install_all), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                if (kind.isSkill) {
                                                    viewModel.installSkillViaTerminal(context, item)
                                                } else {
                                                    viewModel.installPluginViaTerminal(context, item)
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(stringResource(R.string.customization_btn_install), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )

    if (showAddDialog) {
        var addTab by remember { mutableIntStateOf(0) }
        var repoUrl by remember { mutableStateOf(addDialogRepoUrl) }
        var repoSubPath by remember { mutableStateOf("") }
        var itemName by remember { mutableStateOf("") }
        var itemDesc by remember { mutableStateOf("") }
        var itemPrompt by remember { mutableStateOf("") }

        val isInspectingRepo by viewModel.isInspectingRepo.collectAsStateWithLifecycle()
        val discoveredSkills by viewModel.discoveredRepoSkills.collectAsStateWithLifecycle()
        val inspectionError by viewModel.repoInspectionError.collectAsStateWithLifecycle()

        AlertDialog(
            onDismissRequest = {
                viewModel.clearRepoInspection()
                addDialogRepoUrl = ""
                showAddDialog = false
            },
            shape = RoundedCornerShape(16.dp),
            title = {
                DialogBlurEffect()
                Column {
                    Text(stringResource(kind.addTitleRes), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(3.dp)) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (addTab == 0) AntigravityBlue else Color.Transparent)
                                    .clickable { addTab = 0 }
                                    .padding(vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.customization_tab_git_repo),
                                    fontSize = 11.sp,
                                    fontWeight = if (addTab == 0) FontWeight.Bold else FontWeight.Medium,
                                    color = if (addTab == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (addTab == 1) AntigravityBlue else Color.Transparent)
                                    .clickable { addTab = 1 }
                                    .padding(vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.customization_tab_manual),
                                    fontSize = 11.sp,
                                    fontWeight = if (addTab == 1) FontWeight.Bold else FontWeight.Medium,
                                    color = if (addTab == 1) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (addTab == 0) {
                        Text(
                            stringResource(kind.addDescRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = repoUrl,
                                onValueChange = { input ->
                                    val (cleanRepoUrl, normSub) = viewModel.normalizeGitRepoUrl(input)
                                    if (normSub != null && repoSubPath.isBlank()) {
                                        repoSubPath = normSub
                                    }
                                    repoUrl = input
                                    if (discoveredSkills.isNotEmpty()) viewModel.clearRepoInspection()
                                },
                                label = { Text(stringResource(R.string.customization_repo_url_label)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            if (kind.isSkill) {
                                Button(
                                    onClick = {
                                        if (repoUrl.isNotBlank()) {
                                            viewModel.inspectRepoForSkills(context, repoUrl)
                                        } else {
                                            Toast.makeText(context, context.getString(R.string.customization_repo_empty_url_error), Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    enabled = repoUrl.isNotBlank() && !isInspectingRepo,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier.height(52.dp)
                                ) {
                                    if (isInspectingRepo) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = Color.White
                                        )
                                    } else {
                                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.customization_btn_inspect_repo), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        if (isInspectingRepo) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = AntigravityBlue)
                                Text(
                                    stringResource(R.string.customization_inspecting_repo),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AntigravityBlue
                                )
                            }
                        }

                        if (!inspectionError.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.customization_repo_scan_error, inspectionError ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        if (discoveredSkills.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    stringResource(R.string.customization_repo_skills_found, discoveredSkills.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val allSelected = discoveredSkills.all { it.isSelected }
                                TextButton(
                                    onClick = { viewModel.setAllRepoSkillsSelection(!allSelected) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        if (allSelected) stringResource(R.string.customization_btn_deselect_all)
                                        else stringResource(R.string.customization_btn_select_all),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                discoveredSkills.forEach { skill ->
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.toggleRepoSkillSelection(skill.id) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = skill.isSelected,
                                                onCheckedChange = { viewModel.toggleRepoSkillSelection(skill.id) },
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = skill.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                if (skill.description.isNotBlank()) {
                                                    Text(
                                                        text = skill.description,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                if (skill.subPath.isNotBlank()) {
                                                    Text(
                                                        text = skill.subPath,
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = AntigravityBlue
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (!kind.isSkill || discoveredSkills.isEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = itemName,
                                onValueChange = { itemName = it },
                                label = { Text(stringResource(kind.nameLabelRes)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = repoSubPath,
                                onValueChange = { repoSubPath = it },
                                label = { Text(stringResource(R.string.customization_repo_subpath_label)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Text(
                            stringResource(kind.addDescRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = itemName,
                            onValueChange = { itemName = it },
                            label = { Text(stringResource(kind.nameLabelRes)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = itemDesc,
                            onValueChange = { itemDesc = it },
                            label = { Text(stringResource(kind.descLabelRes)) },
                            singleLine = !kind.isSkill,
                            minLines = if (kind.isSkill) 1 else 3,
                            maxLines = if (kind.isSkill) 1 else 6,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (kind.isSkill) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = itemPrompt,
                                onValueChange = { itemPrompt = it },
                                label = { Text(stringResource(R.string.customization_skill_prompt_label)) },
                                minLines = 4,
                                maxLines = 8,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                val selectedCount = discoveredSkills.count { it.isSelected }
                Button(
                    onClick = {
                        if (addTab == 0) {
                            if (repoUrl.isBlank()) {
                                Toast.makeText(context, context.getString(R.string.customization_repo_empty_url_error), Toast.LENGTH_SHORT).show()
                            } else if (kind.isSkill && discoveredSkills.isNotEmpty()) {
                                val toInstall = discoveredSkills.filter { it.isSelected }
                                if (toInstall.isNotEmpty()) {
                                    viewModel.installSelectedRepoSkillsViaTerminal(context, repoUrl, toInstall)
                                    viewModel.clearRepoInspection()
                                    showAddDialog = false
                                }
                            } else if (kind.isSkill && repoSubPath.isBlank() && discoveredSkills.isEmpty()) {
                                viewModel.inspectRepoForSkills(context, repoUrl)
                            } else {
                                viewModel.installCustomFromRepoViaTerminal(
                                    context = context,
                                    rawRepoUrl = repoUrl,
                                    customName = itemName,
                                    subPath = repoSubPath.ifBlank { null },
                                    isPlugin = !kind.isSkill
                                )
                                viewModel.clearRepoInspection()
                                showAddDialog = false
                            }
                        } else {
                            if (itemName.isNotBlank()) {
                                val success = if (kind.isSkill) {
                                    viewModel.installSkill(context, itemName, itemDesc, itemPrompt)
                                } else {
                                    viewModel.installPlugin(context, itemName, itemDesc)
                                }
                                val typeName = if (kind.isSkill) "Skill" else "Plugin"
                                if (success) {
                                    Toast.makeText(context, context.getString(R.string.customization_installed_toast, typeName, itemName), Toast.LENGTH_SHORT).show()
                                    showAddDialog = false
                                } else {
                                    Toast.makeText(context, context.getString(R.string.customization_install_failed_toast, typeName), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    enabled = if (addTab == 0 && kind.isSkill && discoveredSkills.isNotEmpty()) selectedCount > 0 else true
                ) {
                    Text(
                        if (addTab == 0 && kind.isSkill && discoveredSkills.isNotEmpty()) {
                            stringResource(R.string.customization_btn_install_selected, selectedCount)
                        } else if (addTab == 0) {
                            stringResource(R.string.customization_btn_install_repo)
                        } else {
                            stringResource(R.string.customization_btn_install)
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.clearRepoInspection()
                        addDialogRepoUrl = ""
                        showAddDialog = false
                    }
                ) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }
}

@Composable
fun SkillsManagementDialog(
    viewModel: AntigravityViewModel,
    onDismiss: () -> Unit
) {
    CustomizationManagementDialog(
        kind = CustomizationKind.SKILLS,
        viewModel = viewModel,
        onDismiss = onDismiss
    )
}

@Composable
fun PluginsManagementDialog(
    viewModel: AntigravityViewModel,
    onDismiss: () -> Unit
) {
    CustomizationManagementDialog(
        kind = CustomizationKind.PLUGINS,
        viewModel = viewModel,
        onDismiss = onDismiss
    )
}

