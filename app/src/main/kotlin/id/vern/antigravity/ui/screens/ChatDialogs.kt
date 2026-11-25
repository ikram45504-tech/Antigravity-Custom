package id.vern.antigravity.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.vern.antigravity.R
import id.vern.antigravity.data.ModelLimitReachedInfo
import id.vern.antigravity.oauth.StoredAccount
import id.vern.antigravity.ui.components.DialogBlurEffect
import id.vern.antigravity.ui.components.GoogleAccountSwitcherCard
import id.vern.antigravity.ui.theme.AntigravityBlue

@Composable
internal fun RenameConversationDialog(
    currentTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newTitleText by remember(currentTitle) { mutableStateOf(currentTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = {
            DialogBlurEffect()
            Text(stringResource(R.string.dialog_rename_convo_title), fontWeight = FontWeight.Bold)
        },
        text = {
            OutlinedTextField(
                value = newTitleText,
                onValueChange = { newTitleText = it },
                label = { Text(stringResource(R.string.dialog_rename_convo_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().animateContentSize()
            )
        },
        confirmButton = {
            Button(
                shape = RoundedCornerShape(8.dp),
                onClick = { onConfirm(newTitleText) }
            ) {
                Text(stringResource(R.string.dialog_rename_convo_save))
            }
        }
    )
}

@Composable
internal fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = {
            DialogBlurEffect()
            Text(title, fontWeight = FontWeight.Bold)
        },
        text = { Text(message) },
        confirmButton = {
            Button(
                shape = RoundedCornerShape(8.dp),
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue)
            ) {
                Text(confirmText)
            }
        }
    )
}

@Composable
fun SidePanelIcon(
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier.size(20.dp)
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 1.8.dp.toPx()
        val cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        val padding = 2.dp.toPx()
        val w = size.width - (padding * 2)
        val h = size.height - (padding * 2)

        drawRoundRect(
            color = tint,
            topLeft = Offset(padding, padding),
            size = Size(w, h),
            cornerRadius = cornerRadius,
            style = Stroke(width = strokeWidth)
        )

        val splitX = padding + (w * 0.65f)
        drawLine(
            color = tint,
            start = Offset(splitX, padding),
            end = Offset(splitX, padding + h),
            strokeWidth = strokeWidth
        )
    }
}

internal fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = (now - timestamp).coerceAtLeast(0)
    val minutes = diff / (60 * 1000)
    val hours = diff / (60 * 60 * 1000)
    val days = diff / (24 * 60 * 60 * 1000)

    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "${hours}h"
        else -> "${days}d"
    }
}

@Composable
internal fun ModelLimitSwitchAccountDialog(
    limitInfo: ModelLimitReachedInfo,
    activeUserEmail: String?,
    accounts: List<StoredAccount>,
    onSwitchAndRetry: (StoredAccount) -> Unit,
    onAddGoogleAccount: () -> Unit,
    onOpenModelSelector: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = {
            DialogBlurEffect()
            Text("Model Limit Reached", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val resetSuffix = limitInfo.resetText?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
                Text(
                    text = "${limitInfo.modelName} has reached its usage limit$resetSuffix. Switch to another Google account, add a new account, or pick another model.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                GoogleAccountSwitcherCard(
                    activeUserEmail = activeUserEmail,
                    accounts = accounts,
                    onSwitchAccount = onSwitchAndRetry,
                    onAddGoogleAccount = onAddGoogleAccount,
                    actionLabel = if (!limitInfo.failedPrompt.isNullOrBlank()) "Switch & Retry" else "Switch"
                )
            }
        },
        confirmButton = {
            OutlinedButton(
                shape = RoundedCornerShape(8.dp),
                onClick = {
                    onDismiss()
                    onOpenModelSelector()
                }
            ) {
                Text("Switch Model")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    )
}
