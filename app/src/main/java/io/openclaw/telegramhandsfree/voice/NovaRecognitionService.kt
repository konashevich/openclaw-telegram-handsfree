package io.openclaw.telegramhandsfree.voice

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

class NovaRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, callback: Callback?) {
        callback ?: return
        callback.readyForSpeech(Bundle())
        callback.beginningOfSpeech()
    }

    override fun onStopListening(callback: Callback?) {
        callback?.error(SpeechRecognizer.ERROR_NO_MATCH)
    }

    override fun onCancel(callback: Callback?) {
        callback?.error(SpeechRecognizer.ERROR_CLIENT)
    }
}
