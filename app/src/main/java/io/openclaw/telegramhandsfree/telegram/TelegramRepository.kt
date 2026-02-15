package io.openclaw.telegramhandsfree.telegram

import io.openclaw.telegramhandsfree.config.NovaConfig
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class TelegramRepository(
    private val tdLibClient: TdLibClient
) {
    val status: StateFlow<TelegramStatus>
        get() = tdLibClient.status

    fun startMonitoring(onVoiceResponseReady: suspend (IncomingVoiceMessage) -> Unit) {
        tdLibClient.initialize()
        tdLibClient.subscribeToIncomingVoiceFromGroup(
            groupId = NovaConfig.TELEGRAM_GROUP_ID,
            onVoiceMessageReady = onVoiceResponseReady
        )
    }

    fun submitAuthCode() {
        tdLibClient.submitAuthCode()
    }

    fun submitPassword() {
        tdLibClient.submitPassword()
    }

    suspend fun sendVoiceMessage(file: File) {
        tdLibClient.sendGroupVoiceMessage(
            groupId = NovaConfig.TELEGRAM_GROUP_ID,
            file = file
        )
    }
}
