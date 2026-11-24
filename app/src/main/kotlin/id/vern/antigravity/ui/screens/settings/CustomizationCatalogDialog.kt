package id.vern.antigravity.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.vern.antigravity.R
import id.vern.antigravity.ui.components.DialogBlurEffect
import id.vern.antigravity.ui.theme.AntigravityAmber
import id.vern.antigravity.ui.theme.AntigravityBlue
import id.vern.antigravity.ui.theme.AntigravityGreen
import id.vern.antigravity.viewmodel.AntigravityViewModel

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
                                    val (_, normSub) = viewModel.normalizeGitRepoUrl(input)
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
