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

enum class SettingsPage {
    MAIN,
    GENERAL,
    APPEARANCE,
    MODELS_AND_USAGE,
    CUSTOMIZATIONS,
    ACCOUNT,
    GITHUB,
    CLOUD_INTEGRATIONS,
    DEVELOPER,
    ABOUT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    viewModel: AntigravityViewModel,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    initialPage: SettingsPage = SettingsPage.MAIN
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pageStack = remember(initialPage) {
        mutableStateListOf<SettingsPage>().apply {
            add(SettingsPage.MAIN)
            if (initialPage != SettingsPage.MAIN) {
                add(initialPage)
            }
        }
    }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.refreshAuthState(context)
    }

    BackHandler(enabled = true) {
        if (pageStack.size > 1) {
            pageStack.removeAt(pageStack.lastIndex)
        } else {
            scope.launch {
                sheetState.hide()
                onDismissRequest()
            }
        }
    }

    val blockSheetScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset = Offset.Zero

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {

                return available
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {

                return available
            }
        }
    }

    val appearance by viewModel.appearanceConfig.collectAsStateWithLifecycle()
    val themePreset by viewModel.themePreset.collectAsStateWithLifecycle()
    val customBg by viewModel.customThemeBg.collectAsStateWithLifecycle()
    val customFg by viewModel.customThemeFg.collectAsStateWithLifecycle()
    val customAccent by viewModel.customThemeAccent.collectAsStateWithLifecycle()
    val isDark = when (appearance.themeMode) {
        id.vern.antigravity.data.ThemeMode.SYSTEM -> isSystemInDarkTheme()
        id.vern.antigravity.data.ThemeMode.LIGHT -> false
        id.vern.antigravity.data.ThemeMode.DARK -> true
    }

    id.vern.antigravity.ui.theme.AntigravityTheme(
        darkTheme = isDark,
        accentColor = appearance.accentColor,
        themePreset = themePreset,
        customBg = customBg,
        customFg = customFg,
        customAccent = customAccent
    ) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
            containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
            }
        },
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        BackHandler(enabled = pageStack.size > 1) {
            pageStack.removeAt(pageStack.lastIndex)
        }

        val activeInstallProgress by viewModel.activeInstallProgress.collectAsStateWithLifecycle()
        if (activeInstallProgress != null) {
            InstallationProgressDialog(
                progress = activeInstallProgress!!,
                onDismissRequest = { viewModel.dismissInstallProgress() }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .nestedScroll(blockSheetScrollConnection)
        ) {
            AnimatedContent(
                targetState = pageStack.last(),
                transitionSpec = {
                    if (targetState == SettingsPage.MAIN) {
                        (slideInHorizontally(animationSpec = tween(300), initialOffsetX = { -it }) + fadeIn(tween(300)))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(250), targetOffsetX = { it }) + fadeOut(tween(250)))
                    } else {
                        (slideInHorizontally(animationSpec = tween(300), initialOffsetX = { it }) + fadeIn(tween(300)))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(250), targetOffsetX = { -it }) + fadeOut(tween(250)))
                    }
                },
                label = "settings_navigation"
            ) { currentPage ->
                when (currentPage) {
                    SettingsPage.MAIN -> {
                        MainSettingsPage(
                            viewModel = viewModel,
                            onNavigateTo = { pageStack.add(it) }
                        )
                    }
                    SettingsPage.GENERAL -> {
                        GeneralSettingsPage(
                            viewModel = viewModel,
                            onBack = { pageStack.removeAt(pageStack.lastIndex) }
                        )
                    }
                    SettingsPage.APPEARANCE -> {
                        AppearanceSettingsPage(
                            viewModel = viewModel,
                            onBack = { pageStack.removeAt(pageStack.lastIndex) }
                        )
                    }
                    SettingsPage.MODELS_AND_USAGE -> {
                        ModelsAndUsageSettingsPage(
                            viewModel = viewModel,
                            onBack = { pageStack.removeAt(pageStack.lastIndex) }
                        )
                    }
                    SettingsPage.CUSTOMIZATIONS -> {
                        CustomizationsSettingsPage(
                            viewModel = viewModel,
                            onBack = { pageStack.removeAt(pageStack.lastIndex) }
                        )
                    }
                    SettingsPage.ACCOUNT -> {
                        AccountSettingsPage(
                            viewModel = viewModel,
                            onBack = { pageStack.removeAt(pageStack.lastIndex) },
                            onNavigateTo = { pageStack.add(it) }
                        )
                    }
                    SettingsPage.GITHUB -> {
                        GitHubSettingsPage(
                            viewModel = viewModel,
                            onBack = { pageStack.removeAt(pageStack.lastIndex) }
                        )
                    }
                    SettingsPage.CLOUD_INTEGRATIONS -> {
                        CloudIntegrationsSettingsPage(
                            viewModel = viewModel,
                            onBack = { pageStack.removeAt(pageStack.lastIndex) }
                        )
                    }
                    SettingsPage.DEVELOPER -> {
                        DeveloperSettingsPage(
                            viewModel = viewModel,
                            onBack = { pageStack.removeAt(pageStack.lastIndex) }
                        )
                    }
                    SettingsPage.ABOUT -> {
                        AboutSettingsPage(
                            viewModel = viewModel,
                            onBack = { pageStack.removeAt(pageStack.lastIndex) }
                        )
                    }
                }
            }
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AntigravityViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialPage: SettingsPage = SettingsPage.MAIN
) {
    SettingsBottomSheet(
        viewModel = viewModel,
        onDismissRequest = onNavigateBack,
        modifier = modifier,
        initialPage = initialPage
    )
}

@Composable
fun MainSettingsPage(
    viewModel: AntigravityViewModel,
    onNavigateTo: (SettingsPage) -> Unit
) {
    val context = LocalContext.current
    val userEmail by viewModel.userEmail.collectAsStateWithLifecycle()
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val userPicture by viewModel.userPicture.collectAsStateWithLifecycle()
    val githubAccount by viewModel.githubAccount.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 6.dp)
    ) {

        Text(
            text = "Settings",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
        ) {
            SettingsMenuRow(
                title = "General",
                onClick = { onNavigateTo(SettingsPage.GENERAL) }
            )
            SettingsMenuRow(
                title = "Appearance",
                onClick = { onNavigateTo(SettingsPage.APPEARANCE) }
            )
            SettingsMenuRow(
                title = "Models",
                onClick = { onNavigateTo(SettingsPage.MODELS_AND_USAGE) }
            )
            SettingsMenuRow(
                title = "Customizations",
                onClick = { onNavigateTo(SettingsPage.CUSTOMIZATIONS) }
            )
            SettingsMenuRow(
                title = if (githubAccount != null) "GitHub (@${githubAccount!!.login})" else "GitHub",
                onClick = { onNavigateTo(SettingsPage.GITHUB) }
            )
            SettingsMenuRow(
                title = "Other Integrations",
                onClick = { onNavigateTo(SettingsPage.CLOUD_INTEGRATIONS) }
            )
            SettingsMenuRow(
                title = "Developer & System",
                onClick = { onNavigateTo(SettingsPage.DEVELOPER) }
            )
            SettingsMenuRow(
                title = stringResource(R.string.settings_about_title),
                onClick = { onNavigateTo(SettingsPage.ABOUT) }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
        Spacer(modifier = Modifier.height(14.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    if (userEmail != null) {
                        onNavigateTo(SettingsPage.ACCOUNT)
                    } else {
                        viewModel.startGoogleSignIn(context)
                    }
                }
                .padding(horizontal = 4.dp, vertical = 8.dp)
        ) {

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (userEmail != null) AntigravityBlue.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!userPicture.isNullOrBlank()) {
                    AsyncImage(
                        model = userPicture,
                        contentDescription = userName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (userEmail != null) {
                    Text(
                        text = (userName.takeIf { it.isNotBlank() } ?: "U").take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = AntigravityBlue
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (userEmail != null) userName.ifBlank { "Google User" } else stringResource(R.string.account_sign_in_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (userEmail != null) stringResource(R.string.status_authenticated) else stringResource(R.string.account_tap_to_sign_in),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (userEmail != null) stringResource(R.string.account_title) else stringResource(R.string.account_btn_sign_in),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

