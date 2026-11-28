package id.vern.antigravity.util

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import id.vern.antigravity.R
import id.vern.antigravity.ui.MainActivity
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class UpdateChecker(private val context: Context) {
    private val TAG = "UpdateChecker"

    companion object {
        const val OTA_NOTIFICATION_CHANNEL_ID = "ota_updates_channel"
        const val OTA_NOTIFICATION_ID = 1009
        const val GITHUB_OWNER = "ikram45504-tech"
        const val GITHUB_REPO = "Antigravity-Custom"
    }

    private fun fetchGitHubApi(apiUrl: String): Pair<Int, String?> {
        return NetworkDownloader.httpGetString(
            url = apiUrl,
            headers = mapOf("Accept" to "application/vnd.github.v3+json"),
            userAgent = "Antigravity-OTA-Checker",
            connectTimeout = 15000,
            readTimeout = 15000
        )
    }

    suspend fun checkForUpdates(
        currentVersion: String,
        isManualCheck: Boolean = false
    ): UpdateInfo? = withContext(Dispatchers.IO) {
        val owner = GITHUB_OWNER
        val repo = GITHUB_REPO

        var foundReleaseObj: JSONObject? = null

        val latestUrl = "https://api.github.com/repos/$owner/$repo/releases/latest"
        val (codeLatest, bodyLatest) = fetchGitHubApi(latestUrl)

        if (codeLatest == HttpURLConnection.HTTP_OK && !bodyLatest.isNullOrBlank()) {
            try {
                foundReleaseObj = JSONObject(bodyLatest)
            } catch (e: Exception) {
                Log.w(TAG, "Parse error for latest release: ${e.localizedMessage}")
            }
        }

        if (foundReleaseObj == null) {
            val listUrl = "https://api.github.com/repos/$owner/$repo/releases?per_page=5"
            val (codeList, bodyList) = fetchGitHubApi(listUrl)
            if (codeList == HttpURLConnection.HTTP_OK && !bodyList.isNullOrBlank()) {
                try {
                    val array = JSONArray(bodyList)
                    for (i in 0 until array.length()) {
                        val r = array.getJSONObject(i)
                        if (!r.optBoolean("draft", false)) {
                            foundReleaseObj = r
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Parse error for releases list: ${e.localizedMessage}")
                }
            }
        }

        if (foundReleaseObj == null) {
            return@withContext null
        }

        try {
            val tagName = foundReleaseObj.getString("tag_name")
            val body = foundReleaseObj.optString("body", "")
            val assets = foundReleaseObj.optJSONArray("assets") ?: JSONArray()

            var downloadUrl = ""
            var originalFileName = ""

            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    val assetApiUrl = asset.optString("url")
                    val browserDownloadUrl = asset.optString("browser_download_url")
                    downloadUrl = if (browserDownloadUrl.isNotBlank()) browserDownloadUrl else assetApiUrl
                    originalFileName = name
                    break
                }
            }

            if (downloadUrl.isBlank()) {
                return@withContext null
            }

            if (!isNewerVersion(currentVersion, tagName)) {
                return@withContext null
            }

            val finalFileName = if (originalFileName.isNotBlank()) {
                originalFileName
            } else {
                downloadUrl.substringAfterLast("/").substringBefore("?").ifBlank { "antigravity-update-$tagName.apk" }
            }

            val updateInfo = UpdateInfo(
                version = tagName,
                changelog = body,
                downloadUrl = downloadUrl,
                fileName = finalFileName
            )
            showUpdateNotification(updateInfo)
            return@withContext updateInfo
        } catch (e: Exception) {
            Log.e(TAG, "OTA check parse error", e)
            return@withContext null
        }
    }

    fun showUpdateNotification(updateInfo: UpdateInfo, force: Boolean = false) {
        try {
            val prefs = context.getSharedPreferences("antigravity_ota_prefs", Context.MODE_PRIVATE)
            val lastNotifiedVersion = prefs.getString("last_notified_ota_version", "") ?: ""
            if (!force && lastNotifiedVersion == updateInfo.version) {
                return
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channelName = context.getString(R.string.ota_notification_channel_name)
                val channelDesc = context.getString(R.string.ota_notification_channel_desc)
                val channel = NotificationChannel(OTA_NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_HIGH).apply {
                    description = channelDesc
                    enableLights(true)
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                OTA_NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val title = context.getString(R.string.ota_notification_title, updateInfo.version)
            val content = context.getString(R.string.ota_notification_desc, updateInfo.version)

            val bigText = buildString {
                append(content)
                if (updateInfo.changelog.isNotBlank()) {
                    append("\n\n")
                    append(updateInfo.changelog.take(300))
                }
            }

            val notification = NotificationCompat.Builder(context, OTA_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(OTA_NOTIFICATION_ID, notification)
            prefs.edit().putString("last_notified_ota_version", updateInfo.version).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post OTA update notification", e)
        }
    }

    fun resolveTargetFileName(info: UpdateInfo): String {
        val rawFileName = if (info.fileName.isNotBlank()) {
            info.fileName
        } else {
            info.downloadUrl.substringAfterLast("/").substringBefore("?").ifBlank { "antigravity-update-${info.version}.apk" }
        }
        return if (rawFileName.endsWith(".apk", ignoreCase = true)) rawFileName else "$rawFileName.apk"
    }

    /**
     * Checks DownloadManager and local app downloads directory to see if [info]
     * is already downloading or has already been downloaded.
     */
    fun getExistingUpdateState(info: UpdateInfo): AppUpdateState? {
        val targetFileName = resolveTargetFileName(info)
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        try {
            val query = DownloadManager.Query().setFilterByStatus(
                DownloadManager.STATUS_PENDING or
                    DownloadManager.STATUS_RUNNING or
                    DownloadManager.STATUS_PAUSED or
                    DownloadManager.STATUS_SUCCESSFUL
            )
            dm.query(query)?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
                val uriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_URI)
                val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val localUriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                val downloadedIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                while (cursor.moveToNext()) {
                    val rowUri = if (uriIdx >= 0) cursor.getString(uriIdx) else null
                    val rowLocalUri = if (localUriIdx >= 0) cursor.getString(localUriIdx) else null
                    val matchesUrl = !rowUri.isNullOrBlank() && rowUri == info.downloadUrl
                    val matchesName = !rowLocalUri.isNullOrBlank() && rowLocalUri.endsWith("/$targetFileName")
                    if (!matchesUrl && !matchesName) continue

                    val downloadId = if (idIdx >= 0) cursor.getLong(idIdx) else -1L
                    val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else -1
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            val file = localUriToFile(rowLocalUri)
                            if (file != null && file.exists() && file.length() > 0) {
                                return AppUpdateState.ReadyToInstall(downloadId = downloadId, filePath = file.absolutePath)
                            }
                        }
                        DownloadManager.STATUS_PENDING,
                        DownloadManager.STATUS_RUNNING,
                        DownloadManager.STATUS_PAUSED -> {
                            val downloaded = if (downloadedIdx >= 0) cursor.getLong(downloadedIdx) else 0L
                            val total = if (totalIdx >= 0) cursor.getLong(totalIdx) else 0L
                            val progress = if (total > 0) ((downloaded * 100L) / total).toInt() else 0
                            return AppUpdateState.Downloading(progress = progress, downloadId = downloadId)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed querying DownloadManager for existing update: ${e.message}")
        }

        val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (downloadDir != null) {
            val existingFile = File(downloadDir, targetFileName)
            if (existingFile.exists() && existingFile.isFile && existingFile.length() > 0) {
                return AppUpdateState.ReadyToInstall(downloadId = -1L, filePath = existingFile.absolutePath)
            }
        }
        return null
    }

    private fun localUriToFile(localUriString: String?): File? {
        if (localUriString.isNullOrBlank()) return null
        return if (localUriString.startsWith("file://")) {
            val path = Uri.parse(localUriString).path
            if (path.isNullOrBlank()) null else File(path)
        } else {
            File(localUriString)
        }
    }

    fun startDownload(info: UpdateInfo): Long {
        val existing = getExistingUpdateState(info)
        if (existing is AppUpdateState.Downloading && existing.downloadId > 0L) {
            return existing.downloadId
        }
        if (existing is AppUpdateState.ReadyToInstall && existing.downloadId > 0L) {
            return existing.downloadId
        }

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val effectiveUrl = info.downloadUrl
        val targetFileName = resolveTargetFileName(info)

        val request = DownloadManager.Request(Uri.parse(effectiveUrl)).apply {
            setTitle(context.getString(R.string.ota_download_title, targetFileName))
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, targetFileName)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setMimeType("application/vnd.android.package-archive")
        }
        return dm.enqueue(request)
    }

    fun installApk(downloadId: Long, filePath: String? = null) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return
                }
            }

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            var contentUri: Uri? = null

            if (!filePath.isNullOrBlank()) {
                val directFile = File(filePath)
                if (directFile.exists() && directFile.length() > 0) {
                    contentUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        directFile
                    )
                }
            }

            if (contentUri == null && downloadId > 0L) {
                try {
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    var localUriString: String? = null
                    dm.query(query)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val localUriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                            if (localUriIdx >= 0) {
                                localUriString = cursor.getString(localUriIdx)
                            }
                        }
                    }

                    val file = localUriToFile(localUriString)
                    if (file != null && file.exists() && file.length() > 0) {
                        contentUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed resolving FileProvider URI: ${e.message}")
                }
            }

            if (contentUri == null && downloadId > 0L) {
                contentUri = dm.getUriForDownloadedFile(downloadId)
            }

            if (contentUri == null) {
                return
            }

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "OTA install error: ${e.localizedMessage}", e)
        }
    }

    private fun isNewerVersion(currentVersion: String, remoteVersion: String): Boolean {
        try {
            val cleanCurrent = currentVersion.lowercase().replace("v", "")
            val cleanRemote = remoteVersion.lowercase().replace("v", "")
            val currentParts = cleanCurrent.split(".")
            val remoteParts = cleanRemote.split(".")
            val maxLength = maxOf(currentParts.size, remoteParts.size)
            for (i in 0 until maxLength) {
                val currentPart = if (i < currentParts.size) currentParts[i].toIntOrNull() ?: 0 else 0
                val remotePart = if (i < remoteParts.size) remoteParts[i].toIntOrNull() ?: 0 else 0
                if (remotePart > currentPart) return true
                else if (remotePart < currentPart) return false
            }
            return false
        } catch (e: Exception) {
            return remoteVersion.replace("v", "") > currentVersion.replace("v", "")
        }
    }

    suspend fun trackDownloadProgress(downloadId: Long, onState: (AppUpdateState) -> Unit) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        var downloading = true
        while (downloading) {
            val cursor = dm.query(DownloadManager.Query().setFilterById(downloadId))
            if (cursor == null) {
                onState(AppUpdateState.Idle)
                break
            }
            cursor.use { c ->
                if (!c.moveToFirst()) {
                    onState(AppUpdateState.Idle)
                    downloading = false
                } else {
                    val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            val localUriIdx = c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                            val localFile = if (localUriIdx >= 0) localUriToFile(c.getString(localUriIdx)) else null
                            onState(AppUpdateState.ReadyToInstall(downloadId, localFile?.absolutePath))
                            downloading = false
                        }
                        DownloadManager.STATUS_FAILED -> {
                            onState(AppUpdateState.Idle)
                            downloading = false
                        }
                        else -> {
                            val downloaded = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                            if (total > 0) {
                                val progress = ((downloaded * 100L) / total).toInt()
                                onState(AppUpdateState.Downloading(progress, downloadId))
                            }
                        }
                    }
                }
            }
            if (downloading) delay(300)
        }
    }

    data class UpdateInfo(
        val version: String,
        val changelog: String,
        val downloadUrl: String,
        val fileName: String = ""
    )
}

sealed class AppUpdateState {
    object Idle : AppUpdateState()
    data class Available(val info: UpdateChecker.UpdateInfo) : AppUpdateState()
    data class Downloading(val progress: Int, val downloadId: Long = -1L) : AppUpdateState()
    data class ReadyToInstall(val downloadId: Long, val filePath: String? = null) : AppUpdateState()
}

typealias OtaState = AppUpdateState
