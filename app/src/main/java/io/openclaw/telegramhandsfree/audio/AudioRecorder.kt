package io.openclaw.telegramhandsfree.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import io.openclaw.telegramhandsfree.config.ClawsfreeConfig
import java.io.File

class AudioRecorder(
    private val context: Context,
    private val onSilenceTimeout: () -> Unit
) {
    /** When true, use VOICE_COMMUNICATION (routes through SCO). Else MIC. */
    var useBluetoothSource: Boolean = false
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var lastSpeechTimestamp: Long = 0L
    private var recordingStartTimestamp: Long = 0L
    private val handler = Handler(Looper.getMainLooper())

    private val amplitudePollRunnable = object : Runnable {
        override fun run() {
            val recorder = mediaRecorder ?: return
            val amplitude = try {
                recorder.maxAmplitude
            } catch (_: IllegalStateException) {
                0
            }

            if (amplitude > 1200) {
                lastSpeechTimestamp = System.currentTimeMillis()
            }

            val silentFor = System.currentTimeMillis() - lastSpeechTimestamp
            if (silentFor >= ClawsfreeConfig.SILENCE_TIMEOUT_MS) {
                onSilenceTimeout.invoke()
                return
            }

            // Hard cap: stop recording after MAX_RECORDING_MS regardless of noise
            val recordingDuration = System.currentTimeMillis() - recordingStartTimestamp
            if (recordingDuration >= ClawsfreeConfig.MAX_RECORDING_MS) {
                onSilenceTimeout.invoke()
                return
            }

            handler.postDelayed(this, 500L)
        }
    }

    val isRecording: Boolean
        get() = mediaRecorder != null

    fun start(): File {
        if (isRecording) {
            return outputFile ?: throw IllegalStateException("Recorder output file missing")
        }

        outputFile = File(context.cacheDir, "clawsfree_${System.currentTimeMillis()}.ogg")
        lastSpeechTimestamp = System.currentTimeMillis()
        recordingStartTimestamp = System.currentTimeMillis()

        val audioSource = if (useBluetoothSource) {
            MediaRecorder.AudioSource.VOICE_COMMUNICATION
        } else {
            MediaRecorder.AudioSource.MIC
        }

        mediaRecorder = MediaRecorder(context).apply {
            setAudioSource(audioSource)
            setOutputFormat(MediaRecorder.OutputFormat.OGG)
            setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            setAudioEncodingBitRate(24_000)
            setAudioSamplingRate(48_000)
            setOutputFile(outputFile?.absolutePath)
            prepare()
            start()
        }

        handler.removeCallbacks(amplitudePollRunnable)
        handler.postDelayed(amplitudePollRunnable, 500L)
        return outputFile ?: throw IllegalStateException("Failed to create output file")
    }

    fun stop(): File? {
        handler.removeCallbacks(amplitudePollRunnable)
        val recorder = mediaRecorder ?: return null
        runCatching { recorder.stop() }
        recorder.reset()
        recorder.release()
        mediaRecorder = null
        return outputFile
    }

    fun release() {
        handler.removeCallbacks(amplitudePollRunnable)
        mediaRecorder?.run {
            runCatching { stop() }
            reset()
            release()
        }
        mediaRecorder = null
    }
}
