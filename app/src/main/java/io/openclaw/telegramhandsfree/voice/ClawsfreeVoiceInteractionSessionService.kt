package io.openclaw.telegramhandsfree.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import androidx.core.content.ContextCompat

class ClawsfreeVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return ClawsfreeVoiceInteractionSession(this)
    }
}

private class ClawsfreeVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        ContextCompat.startForegroundService(
            context,
            ClawsfreeForegroundService.createIntent(context, ClawsfreeForegroundService.ACTION_START_RECORDING)
        )
    }

    override fun onHide() {
        super.onHide()
        context.startService(
            Intent(context, ClawsfreeForegroundService::class.java).apply {
                action = ClawsfreeForegroundService.ACTION_STOP_IF_RECORDING
            }
        )
    }
}
