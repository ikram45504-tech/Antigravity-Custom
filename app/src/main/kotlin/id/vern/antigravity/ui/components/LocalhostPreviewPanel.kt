package id.vern.antigravity.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.MotionEvent
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.vern.antigravity.service.DevServerManager
import id.vern.antigravity.ui.theme.AntigravityBlue
import id.vern.antigravity.ui.theme.AntigravityGreen
import id.vern.antigravity.ui.theme.AntigravityRed
import java.io.File

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun LocalhostPreviewPanel(
    workspacePath: String,
    modifier: Modifier = Modifier,
    initialUrl: String? = null
) {
    val context = LocalContext.current
    val servers by DevServerManager.servers.collectAsStateWithLifecycle()
    val detectedUrls by DevServerManager.detectedUrls.collectAsStateWithLifecycle()

    val projectDir = remember(workspacePath) {
        workspacePath.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.isDirectory }
    }
    var commandInput by remember(workspacePath) {
        mutableStateOf(DevServerManager.suggestDefaultCommand(projectDir))
    }
    var showServerControls by remember { mutableStateOf(true) }
    var expandedLogsServerId by remember { mutableStateOf<String?>(null) }

    val defaultUrl = initialUrl?.takeIf { it.isNotBlank() }
        ?: detectedUrls.firstOrNull()
        ?: "http://127.0.0.1:3000"
    var urlInput by remember { mutableStateOf(defaultUrl) }
    var activeUrl by remember { mutableStateOf(defaultUrl) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank()) {
            val normalized = DevServerManager.normalizeLocalhostUrl(initialUrl)
            urlInput = normalized
            activeUrl = normalized
            loadError = null
            webViewRef?.loadUrl(normalized)
        }
    }

    LaunchedEffect(detectedUrls.firstOrNull()) {
        val newest = detectedUrls.firstOrNull()
        if (newest != null && loadError != null) {
            urlInput = newest
            activeUrl = newest
            loadError = null
            webViewRef?.loadUrl(newest)
        }
    }

    fun navigateTo(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else if (trimmed.all { it.isDigit() }) {
            "http://127.0.0.1:$trimmed"
        } else {
            "http://$trimmed"
        }
        val normalized = DevServerManager.normalizeLocalhostUrl(withScheme)
        urlInput = normalized
        activeUrl = normalized
        loadError = null
        webViewRef?.loadUrl(normalized)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Collapsible Dev Server Runner Header
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showServerControls = !showServerControls }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val runningCount = servers.count { it.isRunning }
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (runningCount > 0) AntigravityGreen else Color(0xFF64748B))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (runningCount > 0) {
                                "Dev Server ($runningCount running)"
                            } else {
                                "Dev Server (${projectDir?.name ?: "Workspace"})"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Icon(
                        imageVector = if (showServerControls) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }

                AnimatedVisibility(visible = showServerControls) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = commandInput,
                                onValueChange = { commandInput = it },
                                label = { Text("Start command", fontSize = 11.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(onGo = {
                                    DevServerManager.startServer(context, workspacePath, commandInput)
                                }),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    DevServerManager.startServer(context, workspacePath, commandInput)
                                },
                                enabled = commandInput.isNotBlank(),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AntigravityBlue)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Run", fontSize = 12.sp)
                            }
                        }

                        if (servers.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            servers.take(3).forEach { srv ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(if (srv.isRunning) AntigravityGreen else AntigravityRed)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "${srv.projectName}: ${srv.command}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                srv.detectedUrl?.let { url ->
                                                    Text(
                                                        text = url,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = AntigravityBlue,
                                                        modifier = Modifier.clickable { navigateTo(url) }
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = {
                                                    expandedLogsServerId = if (expandedLogsServerId == srv.id) null else srv.id
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.Terminal, contentDescription = "Logs", modifier = Modifier.size(15.dp))
                                            }
                                            if (srv.isRunning) {
                                                IconButton(
                                                    onClick = { DevServerManager.stopServer(srv.id) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.Stop, contentDescription = "Stop", tint = AntigravityRed, modifier = Modifier.size(16.dp))
                                                }
                                            } else {
                                                IconButton(
                                                    onClick = { DevServerManager.removeServer(srv.id) },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(15.dp))
                                                }
                                            }
                                        }
                                        AnimatedVisibility(visible = expandedLogsServerId == srv.id) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFF090D14),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 6.dp)
                                                    .heightIn(max = 120.dp)
                                            ) {
                                                Text(
                                                    text = srv.logs.takeLast(25).joinToString("\n"),
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 10.sp,
                                                    color = Color(0xFFE2E8F0),
                                                    modifier = Modifier
                                                        .padding(8.dp)
                                                        .verticalScroll(rememberScrollState())
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
        }

        // URL Bar & Port Shortcuts
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            IconButton(
                onClick = { if (webViewRef?.canGoBack() == true) webViewRef?.goBack() },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(18.dp))
            }
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { navigateTo(urlInput) }),
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    loadError = null
                    if (urlInput != activeUrl) navigateTo(urlInput) else webViewRef?.reload()
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reload", modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(activeUrl)))
                    }
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open in Browser", modifier = Modifier.size(18.dp))
            }
        }

        val quickUrls = remember(detectedUrls) {
            (detectedUrls + listOf(
                "http://127.0.0.1:3000",
                "http://127.0.0.1:5173",
                "http://127.0.0.1:8080",
                "http://127.0.0.1:8000"
            )).distinct()
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 2.dp)
        ) {
            quickUrls.forEach { qUrl ->
                val isSelected = activeUrl == qUrl
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isSelected) AntigravityBlue.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = if (isSelected) BorderStroke(1.dp, AntigravityBlue.copy(alpha = 0.5f)) else null,
                    modifier = Modifier.clickable { navigateTo(qUrl) }
                ) {
                    Text(
                        text = qUrl.removePrefix("http://"),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = if (isSelected) AntigravityBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        setOnTouchListener { v, event ->
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                                    v.parent?.requestDisallowInterceptTouchEvent(true)
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                                    v.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                            false
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                if (request?.isForMainFrame == true) {
                                    loadError = error?.description?.toString() ?: "Unable to connect to $activeUrl"
                                }
                            }
                        }
                        webViewRef = this
                        loadUrl(activeUrl)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (loadError != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = "Localhost not reachable yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${loadError}\n\nStart your dev server above (bound to 127.0.0.1) and tap Reload.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = {
                            loadError = null
                            webViewRef?.loadUrl(activeUrl)
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry $activeUrl")
                        }
                    }
                }
            }
        }
    }
}
