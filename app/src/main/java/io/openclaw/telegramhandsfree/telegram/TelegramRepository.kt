package io.openclaw.telegramhandsfree.telegram

import io.openclaw.telegramhandsfree.config.ClawsfreeConfig
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
            groupId = ClawsfreeConfig.TELEGRAM_GROUP_ID,
            onVoiceMessageReady = onVoiceResponseReady
        )
    }

    fun submitAuthCode() {
        tdLibClient.submitAuthCode()
    }

    fun submitPassword() {
        tdLibClient.submitPassword()
    }

    fun refreshTargetChatBinding() {
        tdLibClient.refreshTargetChatBinding()
    }

    suspend fun sendVoiceMessage(file: File) {
        tdLibClient.sendGroupVoiceMessage(
            groupId = ClawsfreeConfig.TELEGRAM_GROUP_ID,
            file = file
        )
    }
}
