package id.vern.antigravity.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import id.vern.antigravity.R
import id.vern.antigravity.data.ProjectSortOrder
import id.vern.antigravity.ui.screens.SettingsPage
import id.vern.antigravity.ui.theme.AntigravityBlue
import id.vern.antigravity.ui.theme.AntigravityGreen
import id.vern.antigravity.ui.theme.AntigravityRed
import id.vern.antigravity.viewmodel.ConversationSummary
import java.io.File

/**
 * Top-bar dropdown allowing switching between projects and chats within each project,
 * plus pinned/recent conversations and creating new projects or conversations.
 */
@Composable
fun ProjectChatsDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    projectList: List<File>,
    currentWorkspace: String?,
    activeConversationId: String?,
    conversationHistory: List<ConversationSummary>,
    executingConversationIds: Set<String>,
    formatRelativeTime: (Long) -> String,
    onNewConversation: (String?) -> Unit,
    onSelectProject: (File) -> Unit,
    onSelectConversation: (ConversationSummary) -> Unit,
    onTogglePinConversation: (String) -> Unit,
    onRequestDeleteConversation: (ConversationSummary) -> Unit,
    onOpenProjectIntegrations: (File) -> Unit,
    onRequestDeleteProject: (File) -> Unit,
    onRequestNewProject: () -> Unit,
    onSelectSortOrder: (ProjectSortOrder) -> Unit
) {
    var showSortMenu by remember { mutableStateOf(false) }
    val pinnedConversations = remember(conversationHistory) {
        conversationHistory.filter { it.isPinned }.sortedByDescending { it.timestamp }
    }
    val unassignedConversations = remember(conversationHistory, projectList) {
        val projectPaths = projectList.map { it.absolutePath }.toSet()
        conversationHistory
            .filter { !it.isPinned && (it.projectPath.isNullOrBlank() || it.projectPath !in projectPaths) }
            .sortedByDescending { it.timestamp }
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = DpOffset(0.dp, 6.dp),
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.width(300.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        onDismiss()
                        onNewConversation(null)
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.chat_new_conversation),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.chat_new_conversation),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                if (pinnedConversations.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.drawer_pinned_title),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                    items(pinnedConversations.size) { idx ->
                        val convo = pinnedConversations[idx]
                        DropdownConversationRow(
                            convo = convo,
                            isActive = convo.id == activeConversationId,
                            isExecuting = convo.id in executingConversationIds,
                            relativeTime = formatRelativeTime(convo.timestamp),
                            onClick = {
                                onDismiss()
                                onSelectConversation(convo)
                            },
                            onTogglePin = { onTogglePinConversation(convo.id) },
                            onDelete = {
                                onDismiss()
                                onRequestDeleteConversation(convo)
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(6.dp)) }
                }

                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.drawer_projects_title),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                IconButton(
                                    onClick = { showSortMenu = true },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FilterList,
                                        contentDescription = stringResource(R.string.drawer_sort_projects),
                                        modifier = Modifier.size(15.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false },
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sort_name_asc), fontSize = 13.sp) },
                                        onClick = { onSelectSortOrder(ProjectSortOrder.NAME_ASC); showSortMenu = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sort_name_desc), fontSize = 13.sp) },
                                        onClick = { onSelectSortOrder(ProjectSortOrder.NAME_DESC); showSortMenu = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sort_date_desc), fontSize = 13.sp) },
                                        onClick = { onSelectSortOrder(ProjectSortOrder.DATE_DESC); showSortMenu = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sort_date_asc), fontSize = 13.sp) },
                                        onClick = { onSelectSortOrder(ProjectSortOrder.DATE_ASC); showSortMenu = false }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(2.dp))
                            IconButton(
                                onClick = {
                                    onDismiss()
                                    onRequestNewProject()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CreateNewFolder,
                                    contentDescription = stringResource(R.string.drawer_add_project_folder),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (projectList.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.drawer_projects_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                } else {
                    items(projectList.size) { idx ->
                        val projectDir = projectList[idx]
                        val isCurrent = projectDir.absolutePath == currentWorkspace
                        val convoListForProject = conversationHistory
                            .filter { it.projectPath == projectDir.absolutePath }
                            .sortedByDescending { it.timestamp }

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isCurrent) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        onDismiss()
                                        onSelectProject(projectDir)
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Folder,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = projectDir.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                onDismiss()
                                                onNewConversation(projectDir.absolutePath)
                                            },
                                            modifier = Modifier.size(22.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = stringResource(R.string.menu_new_chat_in_project),
                                                modifier = Modifier.size(15.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        var showProjectMenu by remember { mutableStateOf(false) }
                                        Box {
                                            IconButton(
                                                onClick = { showProjectMenu = true },
                                                modifier = Modifier.size(22.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.MoreVert,
                                                    contentDescription = stringResource(R.string.menu_project_options),
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = showProjectMenu,
                                                onDismissRequest = { showProjectMenu = false },
                                                shape = RoundedCornerShape(14.dp),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.menu_new_chat_in_project)) },
                                                    leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(18.dp)) },
                                                    onClick = {
                                                        showProjectMenu = false
                                                        onDismiss()
                                                        onNewConversation(projectDir.absolutePath)
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.menu_project_integrations)) },
                                                    leadingIcon = { Icon(Icons.Default.Link, null, Modifier.size(18.dp)) },
                                                    onClick = {
                                                        showProjectMenu = false
                                                        onDismiss()
                                                        onOpenProjectIntegrations(projectDir)
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.menu_delete_project_plain), color = AntigravityRed) },
                                                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = AntigravityRed, modifier = Modifier.size(18.dp)) },
                                                    onClick = {
                                                        showProjectMenu = false
                                                        onDismiss()
                                                        onRequestDeleteProject(projectDir)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (convoListForProject.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.drawer_project_convos_empty),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(start = 30.dp, top = 2.dp, bottom = 6.dp)
                                )
                            } else {
                                convoListForProject.forEach { c ->
                                    DropdownConversationRow(
                                        convo = c,
                                        isActive = c.id == activeConversationId,
                                        isExecuting = c.id in executingConversationIds,
                                        relativeTime = formatRelativeTime(c.timestamp),
                                        indentStart = true,
                                        onClick = {
                                            onDismiss()
                                            onSelectConversation(c)
                                        },
                                        onTogglePin = { onTogglePinConversation(c.id) },
                                        onDelete = {
                                            onDismiss()
                                            onRequestDeleteConversation(c)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (unassignedConversations.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.drawer_recent_title),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                    items(unassignedConversations.size) { idx ->
                        val convo = unassignedConversations[idx]
                        DropdownConversationRow(
                            convo = convo,
                            isActive = convo.id == activeConversationId,
                            isExecuting = convo.id in executingConversationIds,
                            relativeTime = formatRelativeTime(convo.timestamp),
                            onClick = {
                                onDismiss()
                                onSelectConversation(convo)
                            },
                            onTogglePin = { onTogglePinConversation(convo.id) },
                            onDelete = {
                                onDismiss()
                                onRequestDeleteConversation(convo)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DropdownConversationRow(
    convo: ConversationSummary,
    isActive: Boolean,
    isExecuting: Boolean,
    relativeTime: String,
    indentStart: Boolean = false,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(
                start = if (indentStart) 28.dp else 8.dp,
                end = 4.dp,
                top = 4.dp,
                bottom = 4.dp
            )
        ) {
            if (isExecuting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(11.dp),
                    strokeWidth = 1.6.dp,
                    color = AntigravityBlue
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = convo.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = relativeTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                fontSize = 11.sp
            )
            var showConvoMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(
                    onClick = { showConvoMenu = true },
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.common_options),
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    )
                }
                DropdownMenu(
                    expanded = showConvoMenu,
                    onDismissRequest = { showConvoMenu = false },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(if (convo.isPinned) R.string.menu_unpin_conversation else R.string.chat_pin_conversation)) },
                        leadingIcon = { Icon(Icons.Default.PushPin, null, Modifier.size(18.dp)) },
                        onClick = {
                            showConvoMenu = false
                            onTogglePin()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_delete_conversation), color = AntigravityRed) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = AntigravityRed, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showConvoMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Right-side slide-over panel providing quick navigation to all workspace tools
 * and system setup pages available in the app.
 */
@Composable
fun RightSystemSetupPanel(
    visible: Boolean,
    userEmail: String?,
    userName: String,
    userPicture: String?,
    hasProjectOpen: Boolean,
    onDismiss: () -> Unit,
    onOpenAccountOrSignIn: () -> Unit,
    onOpenWorkspaceTab: (Int) -> Unit,
    onOpenSettingsPage: (SettingsPage) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

            Surface(
                shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                tonalElevation = 6.dp,
                modifier = Modifier
                    .width(296.dp)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = statusBarTop + 12.dp,
                            bottom = navBarBottom + 12.dp,
                            start = 16.dp,
                            end = 16.dp
                        )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.system_setup_panel_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.common_close),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onDismiss()
                                onOpenAccountOrSignIn()
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (userEmail != null) AntigravityGreen.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant),
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
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AntigravityGreen
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
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (userEmail != null) userName.ifBlank { "Google User" } else stringResource(R.string.account_sign_in_title),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (userEmail != null) stringResource(R.string.status_authenticated) else stringResource(R.string.account_tap_to_sign_in),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        item {
                            Text(
                                text = stringResource(R.string.system_setup_workspace_section),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                            )
                        }

                        if (hasProjectOpen) {
                            item {
                                SetupDestinationRow(
                                    icon = Icons.Default.Folder,
                                    label = stringResource(R.string.agent_tab_files),
                                    onClick = { onDismiss(); onOpenWorkspaceTab(3) }
                                )
                            }
                        }
                        item {
                            SetupDestinationRow(
                                icon = Icons.Default.Terminal,
                                label = stringResource(R.string.agent_tab_terminals),
                                onClick = { onDismiss(); onOpenWorkspaceTab(2) }
                            )
                        }
                        item {
                            SetupDestinationRow(
                                icon = Icons.Default.Language,
                                label = stringResource(R.string.agent_tab_preview),
                                onClick = { onDismiss(); onOpenWorkspaceTab(4) }
                            )
                        }
                        item {
                            SetupDestinationRow(
                                icon = Icons.Default.Difference,
                                label = stringResource(R.string.agent_tab_review),
                                onClick = { onDismiss(); onOpenWorkspaceTab(1) }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.system_setup_settings_section),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                            )
                        }

                        item {
                            SetupDestinationRow(
                                icon = Icons.Default.Tune,
                                label = stringResource(R.string.settings_general_title),
                                onClick = { onDismiss(); onOpenSettingsPage(SettingsPage.GENERAL) }
                            )
                        }
                        item {
                            SetupDestinationRow(
                                icon = Icons.Default.Palette,
                                label = stringResource(R.string.settings_appearance_title),
                                onClick = { onDismiss(); onOpenSettingsPage(SettingsPage.APPEARANCE) }
                            )
                        }
                        item {
                            SetupDestinationRow(
                                icon = Icons.Default.ViewInAr,
                                label = stringResource(R.string.settings_models_title),
                                onClick = { onDismiss(); onOpenSettingsPage(SettingsPage.MODELS_AND_USAGE) }
                            )
                        }
                        item {
                            SetupDestinationRow(
                                icon = Icons.Default.Extension,
                                label = stringResource(R.string.settings_customizations_title),
                                onClick = { onDismiss(); onOpenSettingsPage(SettingsPage.CUSTOMIZATIONS) }
                            )
                        }
                        item {
                            SetupDestinationRow(
                                icon = Icons.Default.Link,
                                label = stringResource(R.string.settings_github_title),
                                onClick = { onDismiss(); onOpenSettingsPage(SettingsPage.GITHUB) }
                            )
                        }
                        item {
                            SetupDestinationRow(
                                icon = Icons.Default.Cloud,
                                label = stringResource(R.string.settings_cloud_title),
                                onClick = { onDismiss(); onOpenSettingsPage(SettingsPage.CLOUD_INTEGRATIONS) }
                            )
                        }
                        item {
                            SetupDestinationRow(
                                icon = Icons.Default.Code,
                                label = stringResource(R.string.settings_developer_title),
                                onClick = { onDismiss(); onOpenSettingsPage(SettingsPage.DEVELOPER) }
                            )
                        }
                        item {
                            SetupDestinationRow(
                                icon = Icons.Default.Info,
                                label = stringResource(R.string.settings_about_title),
                                onClick = { onDismiss(); onOpenSettingsPage(SettingsPage.ABOUT) }
                            )
                        }
                        item {
                            SetupDestinationRow(
                                icon = Icons.Default.Settings,
                                label = stringResource(R.string.common_settings),
                                onClick = { onDismiss(); onOpenSettingsPage(SettingsPage.MAIN) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupDestinationRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
