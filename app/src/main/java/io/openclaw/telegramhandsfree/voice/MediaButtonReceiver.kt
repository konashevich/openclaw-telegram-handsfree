package io.openclaw.telegramhandsfree.voice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import androidx.core.content.ContextCompat

class MediaButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_MEDIA_BUTTON) return

        val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return
        if (!event.isPlayPauseLike()) return

        val action = pressState.resolveAction(event) ?: return
        Log.i(TAG, "MediaButtonReceiver dispatching action=$action keyCode=${event.keyCode} keyAction=${event.action}")

        ContextCompat.startForegroundService(
            context,
            ClawsfreeForegroundService.createIntent(context, action)
        )
    }

    private fun KeyEvent.isPlayPauseLike(): Boolean {
        return keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
            keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY
    }

    private class PressState {
        private var downAtMs: Long = 0L
        private var longPressTriggered: Boolean = false
        private var lastDispatchAtMs: Long = 0L
        private var lastDispatchedAction: String? = null

        fun resolveAction(event: KeyEvent): String? {
            return when (event.action) {
                KeyEvent.ACTION_DOWN -> onDown(event)
                KeyEvent.ACTION_UP -> onUp(event)
                else -> null
            }
        }

        private fun onDown(event: KeyEvent): String? {
            if (event.repeatCount == 0) {
                downAtMs = event.eventTime
                longPressTriggered = false
                return null
            }

            if (!longPressTriggered && event.eventTime - downAtMs >= LONG_PRESS_MS) {
                longPressTriggered = true
                return debounced(ClawsfreeForegroundService.ACTION_START_RECORDING)
            }
            return null
        }

        private fun onUp(event: KeyEvent): String? {
            val pressDuration = event.eventTime - downAtMs
            downAtMs = 0L

            if (longPressTriggered || pressDuration >= LONG_PRESS_MS) {
                longPressTriggered = false
                return debounced(ClawsfreeForegroundService.ACTION_START_RECORDING)
            }

            longPressTriggered = false
            return debounced(ClawsfreeForegroundService.ACTION_STOP_IF_RECORDING)
        }

        private fun debounced(action: String): String? {
            val now = SystemClock.elapsedRealtime()
            val sameActionTooSoon =
                action == lastDispatchedAction && now - lastDispatchAtMs < DEBOUNCE_MS

            if (sameActionTooSoon) return null

            lastDispatchedAction = action
            lastDispatchAtMs = now
            return action
        }

        companion object {
            private const val LONG_PRESS_MS = 650L
            private const val DEBOUNCE_MS = 250L
        }
    }

    companion object {
        private const val TAG = "MediaButtonReceiver"
        private val pressState = PressState()
    }
}
