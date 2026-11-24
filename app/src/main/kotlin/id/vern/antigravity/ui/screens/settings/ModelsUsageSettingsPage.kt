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
fun CircularQuotaRow(
    label: String,
    refreshText: String,
    percentage: Int,
    disabled: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (disabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            if (refreshText.isNotBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = refreshText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (disabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(32.dp)
            ) {
                val sweepAngle = (percentage / 100f) * 360f
                val strokeColor = when {
                    disabled -> Color(0xFF3F3F46)
                    percentage == 0 -> Color(0xFFEF5350)
                    percentage < 50 -> Color(0xFFFFB300)
                    else -> AntigravityGreen
                }
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp)
                ) {
                    drawArc(
                        color = Color(0xFF27272A),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = strokeColor,
                        startAngle = -90f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}

@Composable
fun ModelsAndUsageSettingsPage(
    viewModel: AntigravityViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val userEmail by viewModel.userEmail.collectAsStateWithLifecycle()
    val enableCreditOverages by viewModel.enableCreditOverages.collectAsStateWithLifecycle()
    val userQuotaSummary by viewModel.userQuotaSummary.collectAsStateWithLifecycle()
    val codeAssistTier by viewModel.codeAssistTier.collectAsStateWithLifecycle()
    val isQuotaLoading by viewModel.isQuotaLoading.collectAsStateWithLifecycle()
    val quotaErrorMessage by viewModel.quotaErrorMessage.collectAsStateWithLifecycle()
    val cliModels by viewModel.cliModels.collectAsStateWithLifecycle()

    LaunchedEffect(Unit, userEmail) {
        if (userEmail != null) {
            viewModel.refreshUserQuota(context)
            viewModel.refreshCliModels(context)
        }
    }

    if (userEmail == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            SubSheetHeader(
                title = "Models & Usage",
                subtitle = "Manage your model quota and account.",
                onBack = onBack
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SettingsCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Sign In Required",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Connect your Google account with OAuth 2.0 to access Gemini models, synchronize live usage, and view model quotas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { viewModel.startGoogleSignIn(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.account_sign_in_google), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        return
    }

    val userName = remember(userEmail) { id.vern.antigravity.oauth.OAuthManager.getUserName(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        SubSheetHeader(
            title = "Models & Usage",
            subtitle = "Manage your model quota and credits.",
            onBack = onBack,
            trailingAction = {
                IconButton(
                    onClick = {
                        viewModel.refreshUserQuota(context)
                        viewModel.refreshCliModels(context)
                        Toast.makeText(context, "Refreshing live quota...", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.file_manager_refresh),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
        ) {

            if (isQuotaLoading && userQuotaSummary == null && codeAssistTier == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = AntigravityBlue,
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Fetching live model quota from Google Cloud Code...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (quotaErrorMessage != null && userQuotaSummary == null && !isQuotaLoading) {
                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Quota Unavailable",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = quotaErrorMessage ?: "Check your connection or sign-in state.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.refreshUserQuota(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(stringResource(R.string.btn_retry), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            val curTier = codeAssistTier?.currentTier
            if (curTier != null && curTier.name.isNotBlank()) {
                val planName = curTier.name
                val upgradeDesc = curTier.upgradeSubscriptionText.ifBlank { curTier.description }
                val upgradeUri = curTier.upgradeSubscriptionUri.ifBlank {
                    codeAssistTier?.paidTier?.upgradeSubscriptionUri ?: ""
                }

                SettingsSectionTitle(title = "Plan")
                SettingsCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Your Plan: $planName",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (upgradeDesc.isNotBlank()) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = upgradeDesc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                        if (upgradeUri.isNotBlank()) {
                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(upgradeUri))
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.common_btn_upgrade),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            SettingsSectionTitle(title = "Model Credits")
            SettingsCard {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "Enable AI Credit Overages",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "When toggled on, Antigravity will use your AI credits to fulfill model requests once you're out of model quota. Antigravity will always use your model quota first before using AI credits.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                        Switch(
                            checked = enableCreditOverages,
                            onCheckedChange = { viewModel.setEnableCreditOverages(context, it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AntigravityBlue
                            )
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))

            val groups = userQuotaSummary?.groups ?: emptyList()
            if (groups.isNotEmpty()) {
                groups.forEach { group ->
                    SettingsSectionTitleWithInfo(
                        title = group.displayName,
                        info = group.description ?: ""
                    )
                    SettingsCard {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)) {
                            group.buckets.forEachIndexed { idx, bucket ->
                                if (idx > 0) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                }
                                CircularQuotaRow(
                                    label = bucket.displayName,
                                    refreshText = bucket.description,
                                    percentage = bucket.percentage,
                                    disabled = bucket.disabled
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            } else if (!isQuotaLoading && quotaErrorMessage == null) {
                SettingsCard {
                    Text(
                        text = "No quota information available for this account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            val availableModels = userQuotaSummary?.availableModels ?: emptyList()
            val effectiveModels = remember(userQuotaSummary, availableModels, cliModels) {
                if (cliModels.isNotEmpty()) {
                    cliModels
                        .filter { AgyModelsService.isRealAiModel(it.cliId, it.displayName) }
                        .map { cli ->
                            val matchingAvail = availableModels.firstOrNull { avail ->
                                avail.modelId.equals(cli.cliId, ignoreCase = true) ||
                                avail.displayName.equals(cli.label, ignoreCase = true) ||
                                avail.displayName.equals(cli.displayName, ignoreCase = true) ||
                                avail.matches(cli.cliId) ||
                                avail.matches(cli.displayName)
                            }
                            val pct = matchingAvail?.percentage
                                ?: getModelQuotaPercentage(cli.cliId, userQuotaSummary).first
                                ?: getModelQuotaPercentage(cli.displayName, userQuotaSummary).first
                            val isArgon = cli.cliId.contains("argon", ignoreCase = true) || cli.displayName.contains("argon", ignoreCase = true)
                            val resetT = matchingAvail?.resetTime ?: (
                                if (isArgon) {
                                    userQuotaSummary?.groups?.firstOrNull { it.displayName.contains("argon", ignoreCase = true) }?.buckets?.firstOrNull()?.resetTime
                                        ?: userQuotaSummary?.groups?.firstOrNull { it.displayName.contains("gemini", ignoreCase = true) && !it.displayName.contains("argon", ignoreCase = true) }?.buckets?.firstOrNull { it.window.equals("weekly", ignoreCase = true) || it.bucketId.contains("weekly", ignoreCase = true) }?.resetTime
                                } else {
                                    userQuotaSummary?.groups?.firstOrNull { it.displayName.contains("gemini", ignoreCase = true) && !it.displayName.contains("argon", ignoreCase = true) }?.buckets?.firstOrNull()?.resetTime
                                }
                            )
                            val availStatus = matchingAvail?.availability ?: ModelAvailability.AVAILABLE
                            AvailableModelInfo(
                                modelId = cli.cliId,
                                displayName = cli.label.ifBlank { cli.displayName },
                                provider = cli.provider.ifBlank { AgyModelsService.providerForDisplayName(cli.displayName).ifBlank { "Google" } },
                                remainingFraction = pct?.let { it / 100.0 },
                                resetTime = resetT,
                                availability = availStatus
                            )
                        }
                } else {
                    availableModels
                        .filter { AgyModelsService.isRealAiModel(it.modelId, it.displayName) }
                }
            }

            if (effectiveModels.isNotEmpty()) {
                val modelsByProvider = effectiveModels
                    .distinctBy { it.displayName }
                    .groupBy { it.provider }

                if (modelsByProvider.isNotEmpty()) {
                    SettingsSectionTitle(title = "Models & Providers")
                    SettingsCard {
                        Column(modifier = Modifier.padding(14.dp)) {
                            modelsByProvider.entries.forEachIndexed { pIdx, (provider, modelsList) ->
                                if (pIdx > 0) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                                        modifier = Modifier.padding(vertical = 10.dp)
                                    )
                                }
                                val pColor = when (provider) {
                                    "Google" -> AntigravityBlue
                                    "OpenAI" -> AntigravityGreen
                                    else -> Color(0xFFD97706)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(pColor)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "$provider Models",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                modelsList.forEach { m ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = m.displayName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                                        )
                                        if (m.availability == ModelAvailability.UNAVAILABLE) {
                                            Text(
                                                text = stringResource(R.string.model_status_unavailable_account),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        } else if (m.percentage != null) {
                                            Text(
                                                text = stringResource(R.string.status_remaining_pct, m.percentage ?: 0),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if ((m.percentage ?: 0) > 20) AntigravityGreen else MaterialTheme.colorScheme.error
                                            )
                                        } else if (m.resetTime != null) {
                                            val countdown = id.vern.antigravity.service.CloudCodeQuotaService.formatResetCountdown(m.resetTime)
                                            Text(
                                                text = "${stringResource(R.string.status_weekly_limit_reached)} · $countdown",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        } else {
                                            Text(
                                                text = stringResource(R.string.status_model_available),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Medium,
                                                color = AntigravityGreen
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

