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
import id.vern.antigravity.voice.LiveVoiceController
import id.vern.antigravity.voice.VoiceLanguage
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

enum class GeneralSubPage {
    MAIN,
    TOOL_PERMISSIONS,
    FILE_PERMISSIONS,
    TERMINAL_COMMANDS,
    MCP_TOOLS,
    NETWORK_PERMISSIONS
}

@Composable
fun PermissionRulesCard(
    title: String,
    subtitle: String,
    rules: List<PermissionRule>,
    placeholder: String,
    onAddRule: (pattern: String, mode: String) -> Unit,
    onUpdateRule: (oldPattern: String, newPattern: String, newMode: String) -> Unit,
    onRemoveRule: (pattern: String) -> Unit
) {
    var isAdding by remember { mutableStateOf(false) }
    var addText by remember { mutableStateOf("") }
    var addMode by remember { mutableStateOf("allow") }
    var showAddModeDropdown by remember { mutableStateOf(false) }

    SettingsCard {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = { isAdding = true },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(stringResource(R.string.common_add), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            if (rules.isNotEmpty() || isAdding) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(14.dp)
                ) {
                    if (isAdding) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { showAddModeDropdown = true }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = addMode.replaceFirstChar { it.uppercase() },
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = showAddModeDropdown,
                                    onDismissRequest = { showAddModeDropdown = false },
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                ) {
                                    listOf("allow", "ask", "deny").forEach { m ->
                                        DropdownMenuItem(
                                            text = { Text(m.replaceFirstChar { it.uppercase() }) },
                                            onClick = {
                                                addMode = m
                                                showAddModeDropdown = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .border(1.dp, AntigravityBlue, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (addText.isEmpty()) {
                                    Text(
                                        text = placeholder,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                                BasicTextField(
                                    value = addText,
                                    onValueChange = { addText = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = MaterialTheme.typography.bodySmall.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    cursorBrush = SolidColor(AntigravityBlue),
                                    singleLine = true
                                )
                            }

                            Spacer(modifier = Modifier.width(4.dp))

                            IconButton(
                                onClick = {
                                    if (addText.isNotBlank()) {
                                        onAddRule(addText.trim(), addMode)
                                        addText = ""
                                        isAdding = false
                                    }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = stringResource(R.string.common_confirm),
                                    tint = AntigravityGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    isAdding = false
                                    addText = ""
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.common_cancel),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    rules.forEach { rule ->
                        var isEditing by remember(rule.pattern, rule.mode) { mutableStateOf(false) }
                        var editText by remember(rule.pattern) { mutableStateOf(rule.pattern) }
                        var showRuleModeDropdown by remember { mutableStateOf(false) }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { showRuleModeDropdown = true }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = rule.mode.replaceFirstChar { it.uppercase() },
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = when (rule.mode.lowercase()) {
                                                "allow" -> AntigravityGreen
                                                "deny" -> MaterialTheme.colorScheme.error
                                                else -> Color(0xFFEAB308)
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = showRuleModeDropdown,
                                    onDismissRequest = { showRuleModeDropdown = false },
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                ) {
                                    listOf("allow", "ask", "deny").forEach { m ->
                                        DropdownMenuItem(
                                            text = { Text(m.replaceFirstChar { it.uppercase() }) },
                                            onClick = {
                                                onUpdateRule(rule.pattern, rule.pattern, m)
                                                showRuleModeDropdown = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            if (isEditing) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .border(1.dp, AntigravityBlue, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    BasicTextField(
                                        value = editText,
                                        onValueChange = { editText = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        textStyle = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        cursorBrush = SolidColor(AntigravityBlue),
                                        singleLine = true
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                IconButton(
                                    onClick = {
                                        if (editText.isNotBlank()) {
                                            onUpdateRule(rule.pattern, editText.trim(), rule.mode)
                                            isEditing = false
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = stringResource(R.string.common_save),
                                        tint = AntigravityGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        isEditing = false
                                        editText = rule.pattern
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.common_cancel),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = rule.pattern,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                IconButton(
                                    onClick = { isEditing = true },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = stringResource(R.string.common_edit),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { onRemoveRule(rule.pattern) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.common_remove),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GeneralSettingsPage(
    viewModel: AntigravityViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val pageStack = remember { mutableStateListOf(GeneralSubPage.MAIN) }
    val currentSubPage = pageStack.last()

    val queuedMode by viewModel.queuedMessagesMode.collectAsStateWithLifecycle()
    val securityPreset by viewModel.securityPreset.collectAsStateWithLifecycle()
    val planReviewPolicy by viewModel.planReviewPolicy.collectAsStateWithLifecycle()
    val permissionState by viewModel.permissionState.collectAsStateWithLifecycle()

    val completionNotifEnabled by viewModel.completionNotificationEnabled.collectAsStateWithLifecycle()
    val completionBuzzType by viewModel.completionBuzzType.collectAsStateWithLifecycle()
    val completionSoundType by viewModel.completionSoundType.collectAsStateWithLifecycle()
    val completionCustomSoundTitle by viewModel.completionCustomSoundTitle.collectAsStateWithLifecycle()
    val completionCustomSoundUri by viewModel.completionCustomSoundUri.collectAsStateWithLifecycle()
    val voiceLanguage by viewModel.voiceLanguage.collectAsStateWithLifecycle()
    val voiceReplyInLanguage by viewModel.voiceReplyInLanguage.collectAsStateWithLifecycle()
    val voiceSpeechRate by viewModel.voiceSpeechRate.collectAsStateWithLifecycle()
    val voiceOnlyMode by viewModel.voiceOnlyMode.collectAsStateWithLifecycle()
    val liveUseCliFallback by viewModel.liveUseCliFallback.collectAsStateWithLifecycle()
    val geminiLiveApiKey by viewModel.geminiLiveApiKey.collectAsStateWithLifecycle()
    val geminiLiveVertexProject by viewModel.geminiLiveVertexProject.collectAsStateWithLifecycle()
    val showGeminiLiveButton by viewModel.showGeminiLiveButton.collectAsStateWithLifecycle()
    val showSpeechToTextButton by viewModel.showSpeechToTextButton.collectAsStateWithLifecycle()
    var showVoiceLanguageMenu by remember { mutableStateOf(false) }

    var isPreviewPlaying by remember { mutableStateOf(false) }
    var showSoundMenu by remember { mutableStateOf(false) }

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            if (uri != null) {
                val ringtone = RingtoneManager.getRingtone(context, uri)
                val title = ringtone?.getTitle(context) ?: "Custom Sound"
                viewModel.setCompletionCustomSound(context, uri.toString(), title)
            }
        }
    }

    var showSecurityMenu by remember { mutableStateOf(false) }
    var showPlanReviewMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshPermissions(context)
    }

    BackHandler(enabled = pageStack.size > 1) {
        pageStack.removeAt(pageStack.lastIndex)
    }

    when (currentSubPage) {
        GeneralSubPage.MAIN -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                SubSheetHeader(
                    title = "General",
                    subtitle = "Configure agent execution, queued message delivery, and permissions.",
                    onBack = onBack
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                ) {

                    SettingsSectionTitle(title = "Execution")
                    SettingsCard {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Queued Messages",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Configure when follow-up messages are sent.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(2.dp)
                            ) {
                                listOf("Queue", "Send Immediately").forEach { opt ->
                                    val selected = queuedMode == opt
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { viewModel.setQueuedMessagesMode(context, opt) }
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = opt,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    SettingsSectionTitle(title = stringResource(R.string.settings_alert_section_title))
                    SettingsCard {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = stringResource(R.string.settings_alert_toggle_title),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_alert_toggle_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = completionNotifEnabled,
                                    onCheckedChange = { viewModel.setCompletionNotificationEnabled(context, it) }
                                )
                            }

                            if (completionNotifEnabled) {
                                Spacer(modifier = Modifier.height(14.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(14.dp))

                                val vibrateEnabled = completionBuzzType != PromptCompletionNotifier.BUZZ_NONE
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(
                                            text = stringResource(R.string.settings_alert_buzz_title),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = stringResource(R.string.settings_alert_buzz_desc),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Switch(
                                        checked = vibrateEnabled,
                                        onCheckedChange = { enabled ->
                                            val nextBuzz = if (enabled) PromptCompletionNotifier.BUZZ_GENTLE else PromptCompletionNotifier.BUZZ_NONE
                                            viewModel.setCompletionBuzzType(context, nextBuzz)
                                            if (enabled) {
                                                PromptCompletionNotifier.buzz(context, nextBuzz)
                                            }
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(
                                            text = stringResource(R.string.settings_alert_sound_title),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = PromptCompletionNotifier.getToneDescription(
                                                soundType = completionSoundType,
                                                customSoundTitle = completionCustomSoundTitle
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Box {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { showSoundMenu = true }
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = PromptCompletionNotifier.getToneShortLabel(
                                                        soundType = completionSoundType,
                                                        customSoundTitle = completionCustomSoundTitle
                                                    ),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.ArrowDropDown,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }

                                        DropdownMenu(
                                            expanded = showSoundMenu,
                                            onDismissRequest = { showSoundMenu = false }
                                        ) {
                                            PromptCompletionNotifier.BUILT_IN_TONES.forEach { tone ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Column {
                                                            Text(
                                                                text = tone.shortLabel,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                fontWeight = if (completionSoundType == tone.id) FontWeight.SemiBold else FontWeight.Normal
                                                            )
                                                            Text(
                                                                text = tone.description,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    },
                                                    onClick = {
                                                        viewModel.setCompletionSoundType(context, tone.id)
                                                        isPreviewPlaying = true
                                                        PromptCompletionNotifier.playSoundPreview(
                                                            context = context,
                                                            soundType = tone.id,
                                                            onComplete = { isPreviewPlaying = false }
                                                        )
                                                        showSoundMenu = false
                                                    },
                                                    trailingIcon = if (completionSoundType == tone.id) {
                                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                                    } else null
                                                )
                                            }
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.settings_alert_sound_system)) },
                                                onClick = {
                                                    viewModel.setCompletionSoundType(context, PromptCompletionNotifier.SOUND_SYSTEM)
                                                    showSoundMenu = false
                                                },
                                                trailingIcon = if (completionSoundType == PromptCompletionNotifier.SOUND_SYSTEM) {
                                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                                } else null
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.settings_alert_sound_custom)) },
                                                onClick = {
                                                    showSoundMenu = false
                                                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION or RingtoneManager.TYPE_RINGTONE)
                                                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Completion Sound")
                                                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                                                        val curUri = completionCustomSoundUri?.let { Uri.parse(it) } ?: Settings.System.DEFAULT_NOTIFICATION_URI
                                                        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, curUri)
                                                    }
                                                    ringtonePickerLauncher.launch(intent)
                                                },
                                                trailingIcon = if (completionSoundType == PromptCompletionNotifier.SOUND_CUSTOM) {
                                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                                } else null
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.settings_alert_sound_silent)) },
                                                onClick = {
                                                    viewModel.setCompletionSoundType(context, PromptCompletionNotifier.SOUND_SILENT)
                                                    showSoundMenu = false
                                                },
                                                trailingIcon = if (completionSoundType == PromptCompletionNotifier.SOUND_SILENT) {
                                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                                } else null
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            if (isPreviewPlaying) {
                                                PromptCompletionNotifier.stopSoundPreview()
                                                isPreviewPlaying = false
                                            } else {
                                                isPreviewPlaying = true
                                                PromptCompletionNotifier.playSoundPreview(
                                                    context = context,
                                                    soundType = completionSoundType,
                                                    customUriString = completionCustomSoundUri,
                                                    onComplete = { isPreviewPlaying = false }
                                                )
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isPreviewPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isPreviewPlaying) "Stop" else stringResource(R.string.settings_alert_preview_sound),
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            Toast.makeText(context, context.getString(R.string.settings_alert_test_toast), Toast.LENGTH_SHORT).show()
                                            viewModel.testPromptCompletionAlert(context)
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(stringResource(R.string.settings_alert_test_button), style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    SettingsSectionTitle(title = "Live Voice & Urdu")
                    SettingsCard {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = "Voice & Dictation Language",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Sets speech recognition and TTS language (e.g. Urdu PK/IN).",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Box {
                                    OutlinedButton(
                                        onClick = { showVoiceLanguageMenu = true },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(voiceLanguage.shortLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                    DropdownMenu(
                                        expanded = showVoiceLanguageMenu,
                                        onDismissRequest = { showVoiceLanguageMenu = false },
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                    ) {
                                        VoiceLanguage.entries.forEach { lang ->
                                            DropdownMenuItem(
                                                text = { Text("${lang.displayName} (${lang.shortLabel})", fontSize = 13.sp) },
                                                trailingIcon = if (lang == voiceLanguage) {
                                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                                } else null,
                                                onClick = {
                                                    viewModel.setVoiceLanguage(context, lang)
                                                    showVoiceLanguageMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = "Voice-Only Mode (Hide from Chat)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Speak back and forth without writing voice messages into chat history.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = voiceOnlyMode,
                                    onCheckedChange = { viewModel.setVoiceOnlyMode(context, it) }
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = "Use Agent Voice (CLI) Fallback",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Off = Official-style real-time WebSocket audio (OAuth / API key). On = Local SpeechRecognizer + agy CLI.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = liveUseCliFallback,
                                    onCheckedChange = { viewModel.setLiveUseCliFallback(context, it) }
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = "Show Gemini Live Button",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Show or hide the Gemini Live button in the chat input bar.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = showGeminiLiveButton,
                                    onCheckedChange = { viewModel.setShowGeminiLiveButton(context, it) }
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = "Show Speech-to-Text Mic Button",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Hidden by default. Enable to show the dictation mic icon in the chat input bar.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = showSpeechToTextButton,
                                    onCheckedChange = { viewModel.setShowSpeechToTextButton(context, it) }
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            val isVertexExpressKey = geminiLiveApiKey.startsWith("AQ.")
                            OutlinedTextField(
                                value = geminiLiveApiKey,
                                onValueChange = { viewModel.setGeminiLiveApiKey(context, it) },
                                label = { Text("Gemini Live API Key (optional)") },
                                placeholder = { Text("Paste AIza... key from aistudio.google.com") },
                                isError = isVertexExpressKey,
                                supportingText = {
                                    Text(
                                        text = when {
                                            isVertexExpressKey -> "Vertex Express keys (AQ...) do not support Live WebSocket. Use an AIza... key from aistudio.google.com/apikey."
                                            geminiLiveApiKey.isNotBlank() -> "Saved automatically"
                                            else -> "Leave blank to use Google OAuth"
                                        }
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = geminiLiveVertexProject,
                                onValueChange = { viewModel.setGeminiLiveVertexProject(context, it) },
                                label = { Text("Vertex AI Project ID (optional)") },
                                placeholder = { Text("Optional GCP project for OAuth Live") },
                                supportingText = {
                                    Text(
                                        text = if (geminiLiveVertexProject.isNotBlank()) "Saved automatically" else "Optional"
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = "Clear Urdu Script Replies",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Guides Live Voice replies into concise Urdu script (اردو) for clear pronunciation.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = voiceReplyInLanguage,
                                    onCheckedChange = { viewModel.setVoiceReplyInLanguage(context, it) }
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "Speech Playback Speed",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                LiveVoiceController.SPEECH_RATE_OPTIONS.forEach { rate ->
                                    val selected = kotlin.math.abs(voiceSpeechRate - rate) < 0.02f
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                        },
                                        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { viewModel.setVoiceSpeechRate(context, rate) }
                                    ) {
                                        Text(
                                            text = "${rate}x",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { LiveVoiceController.openTtsVoiceInstaller(context) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Install / Manage Urdu TTS Voice Data", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    SettingsSectionTitle(title = "Global Permissions")
                    SettingsCard {
                        Column(modifier = Modifier.padding(14.dp)) {

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = stringResource(R.string.chat_security_preset),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_security_preset_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                val secPresetDisplay = when {
                                    securityPreset.startsWith("Turbo", ignoreCase = true) -> stringResource(R.string.security_preset_turbo_title)
                                    securityPreset.equals("Auto", ignoreCase = true) || securityPreset.equals("Permissive", ignoreCase = true) -> stringResource(R.string.security_preset_auto_title)
                                    securityPreset.equals("Default", ignoreCase = true) || securityPreset.equals("Standard", ignoreCase = true) -> stringResource(R.string.security_preset_default_title)
                                    securityPreset.equals("Request Review", ignoreCase = true) -> stringResource(R.string.security_preset_request_review_title)
                                    else -> securityPreset
                                }
                                Box {
                                    OutlinedButton(
                                        onClick = { showSecurityMenu = true },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(secPresetDisplay, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                    DropdownMenu(
                                        expanded = showSecurityMenu,
                                        onDismissRequest = { showSecurityMenu = false },
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                    ) {
                                        val presetsList = listOf(
                                            Pair("Request Review", R.string.security_preset_request_review_title),
                                            Pair("Default", R.string.security_preset_default_title),
                                            Pair("Auto", R.string.security_preset_auto_title),
                                            Pair("Turbo", R.string.security_preset_turbo_title)
                                        )
                                        presetsList.forEach { (key, titleRes) ->
                                            DropdownMenuItem(
                                                text = { Text(stringResource(titleRes), fontSize = 13.sp) },
                                                onClick = {
                                                    viewModel.setSecurityPreset(context, key)
                                                    showSecurityMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Tool Permissions",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (permissionState.toolPermissionsCount > 0) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            BadgePill(text = "${permissionState.toolPermissionsCount}")
                                        }
                                    }
                                    Text(
                                        text = "Modify permissions for file, terminal, and MCP tools.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = { pageStack.add(GeneralSubPage.TOOL_PERMISSIONS) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(stringResource(R.string.common_btn_open), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    SettingsSectionTitle(title = "Agent Behavior")
                    SettingsCard {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = "Plan Review Policy",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Determine when the agent should pause and ask you to review its plan.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Box {
                                    OutlinedButton(
                                        onClick = { showPlanReviewMenu = true },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(planReviewPolicy, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                    DropdownMenu(
                                        expanded = showPlanReviewMenu,
                                        onDismissRequest = { showPlanReviewMenu = false },
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                    ) {
                                        listOf("Always Ask", "Inherit Global", "Always Proceed").forEach { opt ->
                                            DropdownMenuItem(
                                                text = { Text(opt, fontSize = 13.sp) },
                                                onClick = {
                                                    viewModel.setPlanReviewPolicy(context, opt)
                                                    showPlanReviewMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    SettingsSectionTitle(title = "Network Permissions")
                    SettingsCard {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Network Access Rules",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (permissionState.networkPermissionsCount > 0) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            BadgePill(text = "${permissionState.networkPermissionsCount}")
                                        }
                                    }
                                    Text(
                                        text = "Configure allowed and denied URLs for reading.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = { pageStack.add(GeneralSubPage.NETWORK_PERMISSIONS) },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(stringResource(R.string.common_btn_open), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    SettingsCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Chat Backup & Restore",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Save or restore all project chats in /storage/emulated/0/Projects/antigravity_chat_backup.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val count = viewModel.exportChatsBackup(context)
                                        Toast.makeText(context, "Exported $count chat(s) to antigravity_chat_backup", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Export Chats", fontSize = 12.sp)
                                }
                                Button(
                                    onClick = {
                                        val count = viewModel.restoreChatsBackup(context)
                                        val msg = if (count > 0) "Restored $count chat(s)" else "No backup chats found"
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Restore Chats", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }

        GeneralSubPage.TOOL_PERMISSIONS -> {
            val toolScrollState = rememberScrollState()
            val fileRulesCount = permissionState.getCountForAction("read_file") + permissionState.getCountForAction("write_file")
            val commandRulesCount = permissionState.getCountForAction("command")
            val mcpRulesCount = permissionState.getCountForAction("mcp")

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                SubSheetHeader(
                    title = "Tool Permissions",
                    subtitle = "Modify permissions for file, terminal, and MCP tools.",
                    onBack = { pageStack.removeAt(pageStack.lastIndex) }
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(toolScrollState)
                ) {
                    SettingsCard {
                        Column(modifier = Modifier.padding(14.dp)) {

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "File Access Rules",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (fileRulesCount > 0) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            BadgePill(text = "$fileRulesCount")
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Configure allowed and denied paths for file reads and writes.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = { pageStack.add(GeneralSubPage.FILE_PERMISSIONS) },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(stringResource(R.string.common_open), fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Terminal Commands",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (commandRulesCount > 0) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            BadgePill(text = "$commandRulesCount")
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Configure allowed terminal commands.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = { pageStack.add(GeneralSubPage.TERMINAL_COMMANDS) },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(stringResource(R.string.common_open), fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "MCP Tools",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (mcpRulesCount > 0) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            BadgePill(text = "$mcpRulesCount")
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Configure external tools via Model Context Protocol.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = { pageStack.add(GeneralSubPage.MCP_TOOLS) },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(stringResource(R.string.common_open), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }

        GeneralSubPage.FILE_PERMISSIONS -> {
            val fileScrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                SubSheetHeader(
                    title = "File Permissions",
                    subtitle = "Configure allowed and denied paths for file reads and writes.",
                    onBack = { pageStack.removeAt(pageStack.lastIndex) }
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(fileScrollState)
                ) {
                    PermissionRulesCard(
                        title = "File Reads",
                        subtitle = "Allow/deny agent read access to specific files or directories.",
                        rules = permissionState.getRulesForAction("read_file"),
                        placeholder = "e.g., /path/to/file",
                        onAddRule = { pattern, mode ->
                            viewModel.addPermissionRule(context, "read_file", pattern, mode)
                        },
                        onUpdateRule = { oldPattern, newPattern, newMode ->
                            viewModel.updatePermissionRule(context, "read_file", oldPattern, newPattern, newMode)
                        },
                        onRemoveRule = { pattern ->
                            viewModel.removePermissionRule(context, "read_file", pattern)
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    PermissionRulesCard(
                        title = "File Writes",
                        subtitle = "Allow/deny agent write access to specific files or directories.",
                        rules = permissionState.getRulesForAction("write_file"),
                        placeholder = "e.g., /path/to/file",
                        onAddRule = { pattern, mode ->
                            viewModel.addPermissionRule(context, "write_file", pattern, mode)
                        },
                        onUpdateRule = { oldPattern, newPattern, newMode ->
                            viewModel.updatePermissionRule(context, "write_file", oldPattern, newPattern, newMode)
                        },
                        onRemoveRule = { pattern ->
                            viewModel.removePermissionRule(context, "write_file", pattern)
                        }
                    )
                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }

        GeneralSubPage.TERMINAL_COMMANDS -> {
            val termScrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                SubSheetHeader(
                    title = "Terminal Commands",
                    subtitle = "Configure allowed terminal commands.",
                    onBack = { pageStack.removeAt(pageStack.lastIndex) }
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(termScrollState)
                ) {
                    PermissionRulesCard(
                        title = "Terminal Commands",
                        subtitle = "Allow/deny specific terminal commands.",
                        rules = permissionState.getRulesForAction("command"),
                        placeholder = "e.g., npm test",
                        onAddRule = { pattern, mode ->
                            viewModel.addPermissionRule(context, "command", pattern, mode)
                        },
                        onUpdateRule = { oldPattern, newPattern, newMode ->
                            viewModel.updatePermissionRule(context, "command", oldPattern, newPattern, newMode)
                        },
                        onRemoveRule = { pattern ->
                            viewModel.removePermissionRule(context, "command", pattern)
                        }
                    )
                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }

        GeneralSubPage.MCP_TOOLS -> {
            val mcpScrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                SubSheetHeader(
                    title = "MCP Tools",
                    subtitle = "External tools the agent can call via Model Context Protocol.",
                    onBack = { pageStack.removeAt(pageStack.lastIndex) }
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(mcpScrollState)
                ) {
                    PermissionRulesCard(
                        title = "MCP Tools",
                        subtitle = "External tools the agent can call via Model Context Protocol.",
                        rules = permissionState.getRulesForAction("mcp"),
                        placeholder = "Enter tool name or server...",
                        onAddRule = { pattern, mode ->
                            viewModel.addPermissionRule(context, "mcp", pattern, mode)
                        },
                        onUpdateRule = { oldPattern, newPattern, newMode ->
                            viewModel.updatePermissionRule(context, "mcp", oldPattern, newPattern, newMode)
                        },
                        onRemoveRule = { pattern ->
                            viewModel.removePermissionRule(context, "mcp", pattern)
                        }
                    )
                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }

        GeneralSubPage.NETWORK_PERMISSIONS -> {
            val netScrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                SubSheetHeader(
                    title = "Network Permissions",
                    subtitle = "Configure allowed and denied URLs for reading.",
                    onBack = { pageStack.removeAt(pageStack.lastIndex) }
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(netScrollState)
                ) {
                    PermissionRulesCard(
                        title = "Read URLs",
                        subtitle = "Allow/deny agent read access to specific URLs or domains.",
                        rules = permissionState.getRulesForAction("read_url"),
                        placeholder = "e.g., https://example.com",
                        onAddRule = { pattern, mode ->
                            viewModel.addPermissionRule(context, "read_url", pattern, mode)
                        },
                        onUpdateRule = { oldPattern, newPattern, newMode ->
                            viewModel.updatePermissionRule(context, "read_url", oldPattern, newPattern, newMode)
                        },
                        onRemoveRule = { pattern ->
                            viewModel.removePermissionRule(context, "read_url", pattern)
                        }
                    )
                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        }

    }
}

