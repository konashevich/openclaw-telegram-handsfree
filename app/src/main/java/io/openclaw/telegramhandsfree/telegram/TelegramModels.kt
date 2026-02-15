package io.openclaw.telegramhandsfree.telegram

import java.io.File

sealed interface TelegramStatus {
    data object Idle : TelegramStatus
    data object Initializing : TelegramStatus
    data object WaitingCode : TelegramStatus
    data object WaitingPassword : TelegramStatus
    data object Ready : TelegramStatus
    data class NeedsConfiguration(val reason: String) : TelegramStatus
    data class NativeUnavailable(val reason: String) : TelegramStatus
    data class Error(val reason: String) : TelegramStatus
}

data class IncomingVoiceMessage(
    val chatId: Long,
    val file: File,
    val senderUserId: Long?
)
