package io.openclaw.telegramhandsfree

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import io.openclaw.telegramhandsfree.voice.ClawsfreeForegroundService

/**
 * Invisible activity that handles ASSIST / VOICE_COMMAND intents.
 * When the user long-presses the Bluetooth media button (or selects this app
 * as their assistant), Android launches this activity which immediately
 * starts recording via the foreground service and finishes itself.
 */
class AssistActivity : Activity() {
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i(TAG, "AssistActivity launched with action=${intent?.action}")

        // Keep this top activity alive briefly while recording starts so Android
        // still considers the app in an eligible foreground state for
        // microphone foreground-service promotion.
        ContextCompat.startForegroundService(
            this,
            ClawsfreeForegroundService.createIntent(this, ClawsfreeForegroundService.ACTION_ENSURE_RUNNING)
        )

        mainHandler.postDelayed(
            {
                ContextCompat.startForegroundService(
                    this,
                    ClawsfreeForegroundService.createIntent(this, ClawsfreeForegroundService.ACTION_TOGGLE_RECORDING)
                )
            },
            900L
        )

        mainHandler.postDelayed(
            { finish() },
            2600L
        )
    }

    companion object {
        private const val TAG = "AssistActivity"
    }
}
