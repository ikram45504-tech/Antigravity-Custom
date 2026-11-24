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
import android.util.Log
import android.widget.Toast
import id.vern.antigravity.service.WakeLockHolder
import org.json.JSONObject
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

class SystemGatewayServer(
    private val context: Context,
    private val port: Int = 45161,
    private val wakeLockHolder: WakeLockHolder? = null
) {
    companion object {
        private const val TAG = "SystemGateway"

        /**
         * Hard ceiling for a request body. Without this, a single request with a
         * huge `Content-Length` header could allocate arbitrarily large buffers
         * and OOM the whole process.
         */
        private const val MAX_BODY_BYTES = 64 * 1024

        /** Request head ceiling, so a client cannot stream headers forever. */
        private const val MAX_HEAD_BYTES = 8 * 1024
    }

    private val executor = Executors.newFixedThreadPool(3)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var serverSocket: ServerSocket? = null
    @Volatile
    private var running = false

    @Synchronized
    fun start() {
        if (running) return
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
                // Parse the request head at the BYTE level. Using a
                // BufferedReader(InputStreamReader(UTF_8)) hid body bytes inside
                // the reader's internal buffer, so the body could not be read
                // back from the raw stream afterwards.
                val raw = client.getInputStream()
                val head = readRequestHead(raw, MAX_HEAD_BYTES) ?: return
                val lines = head.split("\r\n", "\n")
                val requestLine = lines.firstOrNull().orEmpty()
                val parts = requestLine.split(" ")
                val method = parts.getOrNull(0) ?: "GET"
                val rawPath = parts.getOrNull(1) ?: "/"

                var contentLength = 0
                for (i in 1 until lines.size) {
                    val headerLine = lines[i]
                    if (headerLine.isEmpty()) break
                    if (headerLine.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = headerLine.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                }

                // Reject absurd lengths instead of trying to allocate them.
                if (contentLength < 0 || contentLength > MAX_BODY_BYTES) {
                    Log.w(TAG, "Rejecting gateway request with Content-Length=$contentLength")
                    writeJson(client, "{\"ok\":false,\"error\":\"body_too_large\"}", "413 Payload Too Large")
                    return
                }

                // Content-Length is a byte count; read exactly that many bytes.
                val body = if (contentLength > 0) {
                    val bytes = ByteArray(contentLength)
                    var read = 0
                    while (read < contentLength) {
                        val n = raw.read(bytes, read, contentLength - read)
                        if (n == -1) break
                        read += n
                    }
                    String(bytes, 0, read, Charsets.UTF_8)
                } else ""

                if (method.equals("GET", ignoreCase = true) && rawPath.trimEnd('/').endsWith("/clipboard")) {
                    // Reading the clipboard from an unauthenticated localhost
                    // endpoint leaks whatever the user last copied (passwords,
                    // 2FA codes). Require an explicit POST to opt in.
                    writeJson(
                        client,
                        JSONObject().put("ok", false).put("error", "clipboard_read_requires_post").toString(),
                        "405 Method Not Allowed"
                    )
                    return
                }

                val responseJson = dispatch(rawPath, body)
                writeJson(client, responseJson.toString(), "200 OK")
            } catch (e: Exception) {
                Log.w(TAG, "Gateway client handle error: ${e.message}")
            }
        }
    }

    /** Reads bytes until the end of the HTTP request head (CRLFCRLF). */
    private fun readRequestHead(input: java.io.InputStream, limit: Int): String? {
        val buffer = java.io.ByteArrayOutputStream()
        var prev = -1
        while (buffer.size() < limit) {
            val b = input.read()
            if (b == -1) break
            buffer.write(b)
            // Terminate on CRLFCRLF or a bare LFLF.
            if (b == '\n'.code && (prev == '\n'.code || prev == '\r'.code)) {
                val seen = buffer.toString(Charsets.UTF_8.name())
                if (seen.endsWith("\r\n\r\n") || seen.endsWith("\n\n")) break
            }
            prev = b
        }
        if (buffer.size() >= limit) return null
        return buffer.toString(Charsets.UTF_8.name())
    }

    private fun writeJson(client: Socket, json: String, status: String) {
        try {
            val responseBytes = json.toByteArray(Charsets.UTF_8)
            val out = client.getOutputStream()
            val header = "HTTP/1.1 $status\r\n" +
                    "Content-Type: application/json; charset=utf-8\r\n" +
                    "Content-Length: ${responseBytes.size}\r\n" +
                    "Connection: close\r\n" +
                    "\r\n"
            out.write(header.toByteArray(Charsets.UTF_8))
            out.write(responseBytes)
            out.flush()
        } catch (e: Exception) {
            Log.w(TAG, "Gateway response write error: ${e.message}")
        }
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
                val url = json.optString("url")
                if (url.isNotBlank()) {
                    mainHandler.post {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {}
                    }
                    result.put("opened", url)
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

