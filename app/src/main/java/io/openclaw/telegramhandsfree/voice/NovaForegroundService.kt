package io.openclaw.telegramhandsfree.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.SystemClock
import android.media.AudioManager
import android.util.Log
import android.media.ToneGenerator
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import io.openclaw.telegramhandsfree.R
import io.openclaw.telegramhandsfree.audio.AudioPlaybackManager
import io.openclaw.telegramhandsfree.audio.AudioRecorder
import io.openclaw.telegramhandsfree.config.NovaConfig
import io.openclaw.telegramhandsfree.telegram.TdLibClient
import io.openclaw.telegramhandsfree.telegram.TelegramStatus
import io.openclaw.telegramhandsfree.telegram.TelegramRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class NovaForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var recorder: AudioRecorder
    private lateinit var playbackManager: AudioPlaybackManager
    private lateinit var repository: TelegramRepository
    private var mediaSession: MediaSession? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioManager: AudioManager? = null
    private var bluetoothScoActive = false
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // startForeground MUST be called immediately or Android kills the service.
        // On Android 14+ we must pass foregroundServiceType flags explicitly.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(isRecording = false),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification(isRecording = false))
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed: ${e.message}")
            stopSelf()
            return
        }

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        recorder = AudioRecorder(this) {
            stopRecordingAndSend()
        }
        playbackManager = AudioPlaybackManager(this)
        repository = TelegramRepository(TdLibClient(applicationContext))

        setupMediaSession()

        // Keep CPU awake while service runs (screen off during driving)
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "openclaw:foreground")
            .apply { acquire() }

        repository.startMonitoring { incomingVoice ->
            if (incomingVoice.chatId == NovaConfig.TELEGRAM_GROUP_ID) {
                playbackManager.playFile(incomingVoice.file)
            }
        }

        serviceScope.launch {
            repository.status.collectLatest { status ->
                val msg = when (status) {
                    TelegramStatus.Idle -> "idle"
                    TelegramStatus.Initializing -> "connecting"
                    TelegramStatus.WaitingCode -> "waiting_code"
                    TelegramStatus.WaitingPassword -> "waiting_password"
                    TelegramStatus.Ready -> "connected"
                    is TelegramStatus.NeedsConfiguration -> "needs_config:${status.reason}"
                    is TelegramStatus.NativeUnavailable -> "error:${status.reason}"
                    is TelegramStatus.Error -> "error:${status.reason}"
                }
                Log.i(TAG, "Telegram status: $msg")
                broadcastStatus(msg)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> startRecording()
            ACTION_STOP_IF_RECORDING -> stopRecordingAndSend()
            ACTION_TOGGLE_RECORDING -> {
                if (recorder.isRecording) stopRecordingAndSend() else startRecording()
            }
            ACTION_SUBMIT_AUTH -> {
                repository.submitAuthCode()
                repository.submitPassword()
            }
            ACTION_ENSURE_RUNNING, null -> Unit
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopBluetoothSco()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        mediaSession?.release()
        mediaSession = null
        recorder.release()
        playbackManager.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startRecording() {
        if (recorder.isRecording) return

        val useBt = NovaConfig.USE_BLUETOOTH_MIC
        recorder.useBluetoothSource = useBt
        if (useBt) startBluetoothSco()

        beepStartRecording()
        recorder.start()
        updateNotification(isRecording = true)
        broadcastActivity("recording")
    }

    private fun stopRecordingAndSend() {
        val recordingFile = recorder.stop() ?: return
        beepStopRecording()
        stopBluetoothSco()
        updateNotification(isRecording = false)
        broadcastActivity("sending")

        serviceScope.launch {
            repository.sendVoiceMessage(recordingFile)
            broadcastActivity("sent")
            // After a pause, go back to idle
            mainHandler.postDelayed({ broadcastActivity("idle") }, 2000)
        }
    }

    private fun startBluetoothSco() {
        val am = audioManager ?: return
        if (!bluetoothScoActive) {
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            am.startBluetoothSco()
            am.isBluetoothScoOn = true
            bluetoothScoActive = true
            Log.i(TAG, "Bluetooth SCO started")
        }
    }

    private fun stopBluetoothSco() {
        val am = audioManager ?: return
        if (bluetoothScoActive) {
            am.isBluetoothScoOn = false
            am.stopBluetoothSco()
            am.mode = AudioManager.MODE_NORMAL
            bluetoothScoActive = false
            Log.i(TAG, "Bluetooth SCO stopped")
        }
    }

    /** Single rising beep — "recording started" */
    private fun beepStartRecording() {
        val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        tg.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
        mainHandler.postDelayed({ tg.release() }, 300)
    }

    /** Double falling beep — "recording stopped, sending" */
    private fun beepStopRecording() {
        val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        tg.startTone(ToneGenerator.TONE_PROP_ACK, 300)
        mainHandler.postDelayed({ tg.release() }, 400)
    }

    /**
     * MediaSession is the only reliable way to receive Bluetooth media button
     * events on Android 8.0+. The static BroadcastReceiver approach stopped
     * working because the system delivers MEDIA_BUTTON to the active MediaSession.
     *
     * Short press routes to onPlay/onPause/onStop on many BT headsets.
     * Long press is intercepted by the OS and triggers the assistant slot.
     */
    private fun setupMediaSession() {
        mediaSession = MediaSession(this, "NovaWalkieTalkie").apply {
            setCallback(object : MediaSession.Callback() {

                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    val event = mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                        ?: return super.onMediaButtonEvent(mediaButtonIntent)

                    Log.i(TAG, "MediaButton: keyCode=${event.keyCode} action=${event.action} repeat=${event.repeatCount}")

                    if (!isMediaButtonKey(event)) return super.onMediaButtonEvent(mediaButtonIntent)

                    // Any media button press while recording → stop and send
                    if (event.action == KeyEvent.ACTION_UP && recorder.isRecording) {
                        Log.i(TAG, "MediaButton UP while recording → stop+send")
                        stopRecordingAndSend()
                        return true
                    }
                    // Any media button press while idle → start recording
                    if (event.action == KeyEvent.ACTION_UP && !recorder.isRecording) {
                        Log.i(TAG, "MediaButton UP while idle → start recording")
                        startRecording()
                        return true
                    }
                    return true
                }

                // Many BT headsets route short press through these callbacks
                // instead of onMediaButtonEvent
                override fun onPlay() {
                    Log.i(TAG, "MediaSession.onPlay → toggle")
                    if (recorder.isRecording) stopRecordingAndSend() else startRecording()
                }

                override fun onPause() {
                    Log.i(TAG, "MediaSession.onPause → stop if recording")
                    if (recorder.isRecording) stopRecordingAndSend()
                }

                override fun onStop() {
                    Log.i(TAG, "MediaSession.onStop → stop if recording")
                    if (recorder.isRecording) stopRecordingAndSend()
                }

                private fun isMediaButtonKey(event: KeyEvent): Boolean {
                    return event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                        event.keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
                        event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
                        event.keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE ||
                        event.keyCode == KeyEvent.KEYCODE_MEDIA_STOP
                }
            })

            // Must be active + have a playback state to receive media button events
            val state = PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP
                )
                .setState(PlaybackState.STATE_PLAYING, 0, 1f, SystemClock.elapsedRealtime())
                .build()
            setPlaybackState(state)
            isActive = true
        }
    }

    private fun buildNotification(isRecording: Boolean): Notification {
        val title = if (isRecording) R.string.notif_title_recording else R.string.notif_title_idle
        val text = if (isRecording) R.string.notif_text_recording else R.string.notif_text_idle

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(title))
            .setContentText(getString(text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(isRecording: Boolean) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(isRecording))
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notif_channel_desc)
        }
        manager.createNotificationChannel(channel)
    }

    private fun broadcastStatus(status: String) {
        // Persist so activity can read it on resume (broadcasts are fire-and-forget)
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LAST_STATUS, status).apply()

        val intent = Intent(ACTION_STATUS_UPDATE).apply {
            setPackage(packageName)
            putExtra(EXTRA_STATUS, status)
        }
        sendBroadcast(intent)
    }

    private fun broadcastActivity(activityState: String) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LAST_ACTIVITY, activityState).apply()

        val intent = Intent(ACTION_ACTIVITY_UPDATE).apply {
            setPackage(packageName)
            putExtra(EXTRA_ACTIVITY, activityState)
        }
        sendBroadcast(intent)
    }

    companion object {
        private const val TAG = "NovaForegroundService"

        const val ACTION_START_RECORDING = "io.openclaw.telegramhandsfree.action.START_RECORDING"
        const val ACTION_STOP_IF_RECORDING = "io.openclaw.telegramhandsfree.action.STOP_IF_RECORDING"
        const val ACTION_TOGGLE_RECORDING = "io.openclaw.telegramhandsfree.action.TOGGLE_RECORDING"
        const val ACTION_ENSURE_RUNNING = "io.openclaw.telegramhandsfree.action.ENSURE_RUNNING"
        const val ACTION_SUBMIT_AUTH = "io.openclaw.telegramhandsfree.action.SUBMIT_AUTH"
        const val ACTION_STATUS_UPDATE = "io.openclaw.telegramhandsfree.action.STATUS_UPDATE"
        const val ACTION_ACTIVITY_UPDATE = "io.openclaw.telegramhandsfree.action.ACTIVITY_UPDATE"
        const val EXTRA_STATUS = "status"
        const val EXTRA_ACTIVITY = "activity"

        private const val CHANNEL_ID = "nova_handsfree"
        private const val NOTIFICATION_ID = 1001
        const val PREFS_NAME = "nova_service_status"
        const val KEY_LAST_STATUS = "last_status"
        const val KEY_LAST_ACTIVITY = "last_activity"

        fun createIntent(context: Context, action: String): Intent {
            return Intent(context, NovaForegroundService::class.java).apply {
                this.action = action
            }
        }
    }
}
