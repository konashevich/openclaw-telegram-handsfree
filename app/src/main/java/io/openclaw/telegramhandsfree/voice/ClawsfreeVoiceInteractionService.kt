package io.openclaw.telegramhandsfree.voice

import android.content.Intent
import android.service.voice.VoiceInteractionService
import androidx.core.content.ContextCompat

class ClawsfreeVoiceInteractionService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        // Just ensure the foreground service is running (for Telegram monitoring).
        // Do NOT start recording here — onReady fires once at system bind time,
        // not per button press. Recording is triggered by VoiceInteractionSession.onShow().
        ContextCompat.startForegroundService(
            this,
            ClawsfreeForegroundService.createIntent(this, ClawsfreeForegroundService.ACTION_ENSURE_RUNNING)
        )
    }

    override fun onShutdown() {
        super.onShutdown()
        stopService(Intent(this, ClawsfreeForegroundService::class.java))
    }
}
