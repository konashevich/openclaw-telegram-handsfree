package io.openclaw.telegramhandsfree.telegram

import android.content.Context
import android.util.Log
import io.openclaw.telegramhandsfree.config.ClawsfreeConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class TdLibClient(
    private val context: Context
) {
    // TODO(MVP-next): keep this bridge/outbox path intact for now; finalize reconnect strategy and lifecycle shutdown after on-device end-to-end verification.
    private val outbox = VoiceMessageOutbox()
    private val _status = MutableStateFlow<TelegramStatus>(TelegramStatus.Idle)
    val status: StateFlow<TelegramStatus> = _status.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var bridge: TdLibReflectiveBridge? = null
    private var incomingHandler: (suspend (IncomingVoiceMessage) -> Unit)? = null

    fun initialize() {
        _status.value = TelegramStatus.Initializing

        if (ClawsfreeConfig.TELEGRAM_API_ID <= 0 || ClawsfreeConfig.TELEGRAM_API_HASH.isBlank()) {
            val reason = "TDLib not configured. Fill API ID and API Hash in Settings."
            _status.value = TelegramStatus.NeedsConfiguration(reason)
            Log.w(TAG, reason)
            return
        }

        val nativeLoaded = runCatching {
            System.loadLibrary("tdjni")
            true
        }.getOrElse { e ->
            // UnsatisfiedLinkError "already loaded" means it's fine
            if (e is UnsatisfiedLinkError && e.message?.contains("already loaded") == true) {
                true
            } else {
                Log.e(TAG, "loadLibrary tdjni failed: ${e.javaClass.simpleName}: ${e.message}")
                false
            }
        }

        if (!nativeLoaded) {
            val reason = "Native TDLib libtdjni.so failed to load. Check logcat for details."
            _status.value = TelegramStatus.NativeUnavailable(reason)
            Log.w(TAG, reason)
            return
        }

        val clientClassAvailable = runCatching {
            Class.forName("org.drinkless.tdlib.Client")
            true
        }.getOrElse {
            false
        }

        if (!clientClassAvailable) {
            val reason = "TDLib Java classes missing. Add org.drinkless:tdlib dependency or generated bindings."
            _status.value = TelegramStatus.NativeUnavailable(reason)
            Log.w(TAG, reason)
            return
        }

        bridge = TdLibReflectiveBridge(
            onReady = {
                _status.value = TelegramStatus.Ready
                Log.i(TAG, "TDLib authorization ready")
                flushOutboxIfReady(ClawsfreeConfig.TELEGRAM_GROUP_ID)
            },
            onStatus = { message ->
                Log.i(TAG, message)
            },
            onError = { reason ->
                _status.value = TelegramStatus.Error(reason)
                Log.e(TAG, reason)
            },
            onWaitingCode = {
                _status.value = TelegramStatus.WaitingCode
                Log.i(TAG, "TDLib waiting for auth code")
            },
            onWaitingPassword = {
                _status.value = TelegramStatus.WaitingPassword
                Log.i(TAG, "TDLib waiting for 2FA password")
            },
            onIncomingVoice = { chatId, voiceFile, senderUserId ->
                val handler = incomingHandler ?: return@TdLibReflectiveBridge
                scope.launch {
                    handler.invoke(
                        IncomingVoiceMessage(
                            chatId = chatId,
                            file = voiceFile,
                            senderUserId = senderUserId
                        )
                    )
                }
            }
        )

        val initialized = bridge?.initialize(
            appFilesDir = context.filesDir,
            appCacheDir = context.cacheDir
        ) == true

        if (!initialized) {
            _status.value = TelegramStatus.Error("TDLib bridge failed to initialize")
        }
    }

    fun submitAuthCode() {
        bridge?.submitAuthCode()
    }

    fun submitPassword() {
        bridge?.submitPassword()
    }

    fun refreshTargetChatBinding() {
        bridge?.refreshTargetChatBinding()
        Log.i(TAG, "Requested TDLib target chat refresh for groupId=${ClawsfreeConfig.TELEGRAM_GROUP_ID}")
    }

    suspend fun sendGroupVoiceMessage(groupId: Long, file: File) {
        if (_status.value != TelegramStatus.Ready) {
            outbox.enqueue(file)
            Log.w(TAG, "TDLib not ready, queued voice message file=${file.name}")
            return
        }

        val sent = bridge?.sendVoiceMessage(groupId, file) == true
        if (!sent) {
            outbox.enqueue(file)
            _status.value = TelegramStatus.Error("TDLib send failed, queued for retry")
            Log.w(TAG, "TDLib send failed, re-queued file=${file.name}")
        }
    }

    fun subscribeToIncomingVoiceFromGroup(groupId: Long, onVoiceMessageReady: suspend (IncomingVoiceMessage) -> Unit) {
        incomingHandler = onVoiceMessageReady
        Log.i(TAG, "TDLib incoming subscription active for groupId=$groupId")
    }

    private fun flushOutboxIfReady(groupId: Long) {
        if (_status.value != TelegramStatus.Ready) return
        while (!outbox.isEmpty()) {
            val pending = outbox.poll() ?: break
            val sent = bridge?.sendVoiceMessage(groupId, pending) == true
            if (!sent) {
                outbox.enqueue(pending)
                Log.w(TAG, "Outbox flush paused due to TDLib send failure")
                break
            }
            Log.i(TAG, "Flushed queued voice file=${pending.name} groupId=$groupId")
        }
    }

    companion object {
        private const val TAG = "TdLibClient"
    }
}
