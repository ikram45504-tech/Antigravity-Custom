package id.vern.antigravity.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import id.vern.antigravity.R
import id.vern.antigravity.gateway.SystemGatewayServer
import id.vern.antigravity.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

class CoreServerService : Service() {

    companion object {
        private const val TAG = "CoreServerService"
        const val ACTION_START = "id.ikram.antigravity.action.START"
        const val ACTION_STOP = "id.ikram.antigravity.action.STOP"
        const val ACTION_RESTART = "id.ikram.antigravity.action.RESTART"

        fun startService(context: Context) {
            val intent = Intent(context, CoreServerService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, CoreServerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val wakeLockHolder = WakeLockHolder()
    private var gatewayServer: SystemGatewayServer? = null

    inner class LocalBinder : Binder() {
        fun getService(): CoreServerService = this@CoreServerService
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Creating CoreServerService...")
        ForegroundNotification.start(this, getString(R.string.notification_engine_active))
        wakeLockHolder.acquire(this, 15_000L)

        gatewayServer = SystemGatewayServer(this, 45161, wakeLockHolder)
        gatewayServer?.start()

        startBackgroundDaemon()
        AppLogger.d("Engine", "CoreServerService running on ports 45158 & 45161")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.i(TAG, "Stop action requested")
                dismissNotification()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RESTART -> {
                Log.i(TAG, "Restart action requested")
                serviceScope.launch {
                    stopBackgroundDaemonInternal()
                    startBackgroundDaemonInternal()
                }
                ForegroundNotification.update(this, getString(R.string.notification_engine_running))
            }
            else -> {
                ForegroundNotification.update(this, getString(R.string.notification_engine_running))
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        Log.i(TAG, "Destroying CoreServerService...")
        dismissNotification()
        serviceScope.cancel()
        Thread {
            stopBackgroundDaemonInternal()
            DevServerManager.stopAll()
        }.apply {
            isDaemon = true
            start()
        }
        gatewayServer?.stop()
        wakeLockHolder.release()
        AppLogger.d("Engine", "CoreServerService stopped")
        super.onDestroy()
    }

    private fun dismissNotification() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.cancel(ForegroundNotification.NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.w(TAG, "Error dismissing notification: ${e.message}")
        }
    }

    private fun configureDaemonEnv(pb: ProcessBuilder, homeDir: File) {
        val env = pb.environment()
        env["PATH"] = "${filesDir.absolutePath}/usr/bin:${filesDir.absolutePath}/engine/bin:/system/bin"
        env["HOME"] = homeDir.absolutePath
        env["PREFIX"] = "${filesDir.absolutePath}/usr"
        env["LD_LIBRARY_PATH"] = "${filesDir.absolutePath}/usr/lib:/system/lib64"
        env["AGY_APPLICATION_ID"] = packageName
        env["APPLICATION_ID"] = packageName
        gatewayServer?.getToken()?.takeIf { it.isNotBlank() }?.let { token ->
            env["AGY_GATEWAY_TOKEN"] = token
        }
    }

    private fun startBackgroundDaemon() {
        serviceScope.launch {
            delay(1500)
            startBackgroundDaemonInternal()
        }
    }

    private fun startBackgroundDaemonInternal() {
        try {
            val daemonBin = File(filesDir, "usr/bin/agy-daemon")
            if (daemonBin.canExecute()) {
                Log.i(TAG, "Starting autonomous background daemon...")
                val homeDir = File(filesDir, "home").apply { mkdirs() }
                val pb = ProcessBuilder(daemonBin.absolutePath, "start")
                pb.directory(homeDir)
                configureDaemonEnv(pb, homeDir)
                pb.start()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to launch agy-daemon: ${e.message}")
        }
    }

    private fun stopBackgroundDaemonInternal() {
        try {
            val daemonBin = File(filesDir, "usr/bin/agy-daemon")
            if (daemonBin.canExecute()) {
                Log.i(TAG, "Stopping background daemon...")
                val homeDir = File(filesDir, "home").apply { mkdirs() }
                val pb = ProcessBuilder(daemonBin.absolutePath, "stop")
                pb.directory(homeDir)
                configureDaemonEnv(pb, homeDir)
                val p = pb.start()
                if (!p.waitFor(1500, TimeUnit.MILLISECONDS)) {
                    p.destroyForcibly()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping daemon: ${e.message}")
        }
    }
}

