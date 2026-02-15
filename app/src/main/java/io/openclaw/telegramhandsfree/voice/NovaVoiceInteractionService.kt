package io.openclaw.telegramhandsfree.voice

import android.content.Intent
import android.service.voice.VoiceInteractionService
import androidx.core.content.ContextCompat

class NovaVoiceInteractionService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        // Just ensure the foreground service is running (for Telegram monitoring).
        // Do NOT start recording here — onReady fires once at system bind time,
        // not per button press. Recording is triggered by VoiceInteractionSession.onShow().
        ContextCompat.startForegroundService(
            this,
            NovaForegroundService.createIntent(this, NovaForegroundService.ACTION_ENSURE_RUNNING)
        )
    }

    override fun onShutdown() {
        super.onShutdown()
        stopService(Intent(this, NovaForegroundService::class.java))
    }
}
