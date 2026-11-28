package id.vern.antigravity.service

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.util.Log

class WakeLockHolder {

    companion object {
        private const val TAG = "WakeLockHolder"
        private const val LOCK_TAG = "id.vern.antigravity:wakelock"
        private const val WIFI_LOCK_TAG = "id.vern.antigravity:wifilock"
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    fun acquire(context: Context, timeoutMs: Long = 10 * 60 * 1000L) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm != null) {
                if (wakeLock == null) {
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, LOCK_TAG).apply {
                        setReferenceCounted(false)
                    }
                }
                wakeLock?.let {
                    if (it.isHeld) it.release()
                    it.acquire(timeoutMs)
                }
                Log.i(TAG, "Partial WakeLock acquired (timeout=${timeoutMs}ms)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire WakeLock: ${e.message}")
        }

        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wm != null) {
                if (wifiLock == null) {
                    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                    } else {
                        @Suppress("DEPRECATION")
                        WifiManager.WIFI_MODE_FULL_HIGH_PERF
                    }
                    wifiLock = wm.createWifiLock(mode, WIFI_LOCK_TAG).apply {
                        setReferenceCounted(false)
                    }
                }
                if (wifiLock?.isHeld == false) {
                    wifiLock?.acquire()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire WifiLock: ${e.message}")
        }
    }

    fun release() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.i(TAG, "Partial WakeLock released")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release WakeLock: ${e.message}")
        } finally {
            wakeLock = null
        }

        try {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release WifiLock: ${e.message}")
        } finally {
            wifiLock = null
        }
    }

    val isHeld: Boolean
        get() = wakeLock?.isHeld == true
}

