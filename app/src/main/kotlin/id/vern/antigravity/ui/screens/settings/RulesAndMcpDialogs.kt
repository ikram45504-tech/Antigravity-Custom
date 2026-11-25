package id.vern.antigravity.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
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
import id.vern.antigravity.service.McpServerItem
import id.vern.antigravity.service.RuleItem
import id.vern.antigravity.ui.components.DialogBlurEffect
import id.vern.antigravity.ui.theme.AntigravityBlue
import id.vern.antigravity.viewmodel.AntigravityViewModel

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
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
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
                            textAlign = TextAlign.Center
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
    var headersText by remember { mutableStateOf("") }

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
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = headersText,
                        onValueChange = { headersText = it },
                        label = { Text(stringResource(R.string.settings_mcp_headers_label)) },
                        placeholder = { Text(stringResource(R.string.settings_mcp_headers_hint)) },
                        minLines = 2,
                        maxLines = 4,
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
                        val parsedHeaders = if (isRemote && headersText.isNotBlank()) {
                            headersText.lines().mapNotNull { line ->
                                val idx = line.indexOf(':')
                                if (idx <= 0) null else {
                                    val k = line.substring(0, idx).trim()
                                    val v = line.substring(idx + 1).trim()
                                    if (k.isNotEmpty() && v.isNotEmpty()) k to v else null
                                }
                            }.toMap()
                        } else emptyMap()
                        val item = McpServerItem(
                            name = serverName.trim(),
                            command = command.trim(),
                            args = argsList,
                            url = urlText.trim(),
                            headers = parsedHeaders,
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
