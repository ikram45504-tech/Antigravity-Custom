package id.vern.antigravity.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import java.util.WeakHashMap

/**
 * Reference-counted coordinator for [WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON]
 * so multiple independent composables (e.g. Live Voice and standard voice input)
 * can request the screen to stay awake without clearing each other's flag.
 */
internal object KeepScreenOnCoordinator {
    private val activeTokensByWindow = WeakHashMap<Window, MutableSet<Any>>()

    @Synchronized
    fun acquire(window: Window, token: Any) {
        val tokens = activeTokensByWindow.getOrPut(window) { mutableSetOf() }
        val wasEmpty = tokens.isEmpty()
        tokens.add(token)
        if (wasEmpty) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    @Synchronized
    fun release(window: Window, token: Any) {
        val tokens = activeTokensByWindow[window] ?: return
        tokens.remove(token)
        if (tokens.isEmpty()) {
            activeTokensByWindow.remove(window)
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Keeps the device screen awake while [enabled] is true and releases the wake request
 * when [enabled] becomes false or the composable leaves composition.
 */
@Composable
fun KeepScreenOnEffect(enabled: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    val token = remember { Any() }
    val window = remember(context) { context.findActivity()?.window }

    DisposableEffect(enabled, window, view) {
        if (enabled) {
            if (window != null) {
                KeepScreenOnCoordinator.acquire(window, token)
            } else {
                view.keepScreenOn = true
            }
        }
        onDispose {
            if (enabled) {
                if (window != null) {
                    KeepScreenOnCoordinator.release(window, token)
                } else {
                    view.keepScreenOn = false
                }
            }
        }
    }
}
