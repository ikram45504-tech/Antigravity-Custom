package id.vern.antigravity.gateway

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.system.Os
import android.util.Log
import android.widget.Toast
import id.vern.antigravity.service.WakeLockHolder
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors

class SystemGatewayServer(
    private val context: Context,
    private val port: Int = 45161,
    private val wakeLockHolder: WakeLockHolder? = null
) {
    companion object {
        private const val TAG = "SystemGateway"
        const val TOKEN_HEADER = "X-Gateway-Token"
        const val TOKEN_FILE_NAME = ".gateway_token"
        const val MAX_CONTENT_LENGTH_BYTES = 1_048_576 // 1 MB
        private const val CLIENT_SO_TIMEOUT_MS = 5_000
    }

    private val executor = Executors.newFixedThreadPool(3)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var serverSocket: ServerSocket? = null
    @Volatile
    private var running = false
    @Volatile
    private var authToken: String = generateAndPersistToken()

    fun getToken(): String = authToken

    private fun generateAndPersistToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val token = bytes.joinToString("") { "%02x".format(it) }
        try {
            val tokenFile = File(context.filesDir, TOKEN_FILE_NAME)
            tokenFile.writeText(token, Charsets.UTF_8)
            try { Os.chmod(tokenFile.absolutePath, 384) } catch (_: Exception) {}
        } catch (e: Exception) {
            Log.w(TAG, "Could not persist gateway token: ${e.message}")
        }
        return token
    }

    @Synchronized
    fun start() {
        if (running) return
        authToken = generateAndPersistToken()
        running = true

        executor.execute {
            try {
                val socket = ServerSocket(port, 10, InetAddress.getByName("127.0.0.1"))
                serverSocket = socket
                Log.i(TAG, "Gateway server listening on 127.0.0.1:$port")

                while (running && !socket.isClosed) {
                    try {
                        val client = socket.accept()
                        executor.execute { handleClient(client) }
                    } catch (e: Exception) {
                        if (!running) break
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not bind gateway on $port: ${e.message}")
            }
        }
    }

    @Synchronized
    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        serverSocket = null
    }

    private fun handleClient(socket: Socket) {
        socket.use { client ->
            try {
                client.soTimeout = CLIENT_SO_TIMEOUT_MS
                val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
                val firstLine = reader.readLine() ?: return
                val parts = firstLine.split(" ")
                val method = parts.getOrNull(0) ?: "GET"
                val rawPath = parts.getOrNull(1) ?: "/"

                var contentLength = 0
                var providedToken = ""
                var headerLine = reader.readLine()
                while (!headerLine.isNullOrEmpty()) {
                    if (headerLine.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = headerLine.substringAfter(":").trim().toIntOrNull() ?: -1
                    } else if (headerLine.startsWith("$TOKEN_HEADER:", ignoreCase = true)) {
                        providedToken = headerLine.substringAfter(":").trim()
                    }
                    headerLine = reader.readLine()
                }

                val lengthError = validateContentLength(contentLength)
                val (statusLine, responseJson) = if (lengthError != null) {
                    lengthError
                } else {
                    val body = if (contentLength > 0) {
                        val buffer = CharArray(contentLength)
                        var read = 0
                        while (read < contentLength) {
                            val n = reader.read(buffer, read, contentLength - read)
                            if (n == -1) break
                            read += n
                        }
                        String(buffer, 0, read)
                    } else ""
                    routeRequest(rawPath, body, providedToken)
                }
                val responseBytes = responseJson.toString().toByteArray(Charsets.UTF_8)

                val out = client.getOutputStream()
                val header = "$statusLine\r\n" +
                        "Content-Type: application/json; charset=utf-8\r\n" +
                        "Content-Length: ${responseBytes.size}\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"

                out.write(header.toByteArray(Charsets.UTF_8))
                out.write(responseBytes)
                out.flush()
            } catch (e: Exception) {
                Log.w(TAG, "Gateway client handle error: ${e.message}")
            }
        }
    }

    internal fun validateContentLength(contentLength: Int): Pair<String, JSONObject>? {
        return when {
            contentLength < 0 -> "HTTP/1.1 400 Bad Request" to JSONObject().put("ok", false).put("error", "Invalid Content-Length")
            contentLength > MAX_CONTENT_LENGTH_BYTES -> "HTTP/1.1 413 Payload Too Large" to JSONObject().put("ok", false).put("error", "Payload too large")
            else -> null
        }
    }

    internal fun routeRequest(rawPath: String, body: String, providedToken: String): Pair<String, JSONObject> {
        val endpoint = rawPath.removePrefix("/").substringBefore("?").trim()
        val isHealthCheck = endpoint.isEmpty() || endpoint == "health"
        val tokenValid = providedToken.isNotEmpty() && MessageDigest.isEqual(
            providedToken.toByteArray(Charsets.UTF_8),
            authToken.toByteArray(Charsets.UTF_8)
        )
        return if (!isHealthCheck && !tokenValid) {
            "HTTP/1.1 401 Unauthorized" to JSONObject().put("ok", false).put("error", "Unauthorized")
        } else {
            "HTTP/1.1 200 OK" to dispatch(rawPath, body)
        }
    }

    internal fun isSupportedExternalScheme(url: String): Boolean {
        val scheme = runCatching { java.net.URI(url).scheme?.lowercase() }.getOrNull()
            ?: runCatching { Uri.parse(url)?.scheme?.lowercase() }.getOrNull()
        return scheme == "http" || scheme == "https"
    }

    private fun dispatch(path: String, body: String): JSONObject {
        val json = try {
            if (body.isNotBlank()) JSONObject(body) else JSONObject()
        } catch (e: Exception) {
            JSONObject()
        }

        val endpoint = path.removePrefix("/").substringBefore("?").trim()
        val result = JSONObject().put("ok", true)

        when (endpoint) {
            "toast" -> {
                val msg = json.optString("message", json.optString("text", "Antigravity"))
                mainHandler.post {
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
                result.put("toast", "shown")
            }
            "open_url" -> {
                val url = json.optString("url").trim()
                if (url.isNotBlank() && isSupportedExternalScheme(url)) {
                    val parsed = runCatching { Uri.parse(url) }.getOrNull()
                    if (parsed != null) {
                        mainHandler.post {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, parsed).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {}
                        }
                    }
                    result.put("opened", url)
                } else if (url.isNotBlank()) {
                    result.put("ok", false)
                    result.put("error", "Unsupported URL scheme")
                }
            }
            "clipboard" -> {
                if (json.has("text")) {
                    val text = json.getString("text")
                    mainHandler.post {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("agy", text))
                    }
                    result.put("set", true)
                } else {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val text = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                    result.put("clipboard", text)
                }
            }
            "wakelock" -> {
                val action = json.optString("action", "status")
                when (action) {
                    "acquire" -> wakeLockHolder?.acquire(context)
                    "release" -> wakeLockHolder?.release()
                }
                result.put("held", wakeLockHolder?.isHeld == true)
            }
            "system_info" -> {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
                result.put("battery", batteryPct)
                result.put("android", Build.VERSION.RELEASE)
                result.put("sdk", Build.VERSION.SDK_INT)
                result.put("model", Build.MODEL)
            }
            else -> {
                result.put("status", "running")
                result.put("gateway", "Antigravity Jetpack Gateway")
            }
        }
        return result
    }
}

