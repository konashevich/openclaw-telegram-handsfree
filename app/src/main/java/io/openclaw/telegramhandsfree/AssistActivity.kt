package io.openclaw.telegramhandsfree

import android.app.Activity
import android.os.Bundle
import androidx.core.content.ContextCompat
import io.openclaw.telegramhandsfree.voice.NovaForegroundService

/**
 * Invisible activity that handles ASSIST / VOICE_COMMAND intents.
 * When the user long-presses the Bluetooth media button (or selects this app
 * as their assistant), Android launches this activity which immediately
 * starts recording via the foreground service and finishes itself.
 */
class AssistActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure the foreground service is running, then toggle recording.
        // Two intents: first ENSURE_RUNNING (creates service if needed),
        // then START_RECORDING (actual action).
        ContextCompat.startForegroundService(
            this,
            NovaForegroundService.createIntent(this, NovaForegroundService.ACTION_START_RECORDING)
        )

        // This activity has no UI — finish immediately
        finish()
    }
}
