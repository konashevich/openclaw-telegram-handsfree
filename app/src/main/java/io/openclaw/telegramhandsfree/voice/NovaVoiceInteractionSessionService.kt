package io.openclaw.telegramhandsfree.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import androidx.core.content.ContextCompat

class NovaVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return NovaVoiceInteractionSession(this)
    }
}

private class NovaVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        ContextCompat.startForegroundService(
            context,
            NovaForegroundService.createIntent(context, NovaForegroundService.ACTION_START_RECORDING)
        )
    }

    override fun onHide() {
        super.onHide()
        context.startService(
            Intent(context, NovaForegroundService::class.java).apply {
                action = NovaForegroundService.ACTION_STOP_IF_RECORDING
            }
        )
    }
}
