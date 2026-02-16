package io.openclaw.telegramhandsfree.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import java.io.File

class AudioPlaybackManager(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var mediaPlayer: MediaPlayer? = null

    private val audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setOnAudioFocusChangeListener { }
        .build()

    fun playFile(file: File) {
        stop()

        val granted = audioManager.requestAudioFocus(audioFocusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        Log.i(TAG, "playFile path=${file.absolutePath} exists=${file.exists()} focusGranted=$granted")

        mediaPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error what=$what extra=$extra path=${file.absolutePath}")
                stop()
                true
            }
            setOnCompletionListener {
                Log.i(TAG, "Playback complete path=${file.absolutePath}")
                stop()
            }
            prepare()
            start()
            Log.i(TAG, "Playback started path=${file.absolutePath}")
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

    companion object {
        private const val TAG = "AudioPlaybackManager"
    }
}
