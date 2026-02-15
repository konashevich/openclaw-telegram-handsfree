package io.openclaw.telegramhandsfree.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import java.io.File

class AudioPlaybackManager(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var mediaPlayer: MediaPlayer? = null

    private val audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setOnAudioFocusChangeListener { }
        .build()

    fun playFile(file: File) {
        stop()

        val granted = audioManager.requestAudioFocus(audioFocusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!granted) return

        mediaPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnCompletionListener {
                stop()
            }
            prepare()
            start()
        }
    }

    fun stop() {
        mediaPlayer?.runCatching {
            if (isPlaying) stop()
            reset()
            release()
        }
        mediaPlayer = null
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
    }
}
