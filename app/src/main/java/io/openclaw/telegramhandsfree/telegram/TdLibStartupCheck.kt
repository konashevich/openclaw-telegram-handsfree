package io.openclaw.telegramhandsfree.telegram

import io.openclaw.telegramhandsfree.config.ClawsfreeConfig

data class StartupCheckResult(
    val isReady: Boolean,
    val messages: List<String>
)

object TdLibStartupCheck {
    fun run(): StartupCheckResult {
        val messages = mutableListOf<String>()

        if (ClawsfreeConfig.TELEGRAM_API_ID <= 0) {
            messages += "Set API ID in Settings"
        }
        if (ClawsfreeConfig.TELEGRAM_API_HASH.isBlank()) {
            messages += "Set API Hash in Settings"
        }
        if (ClawsfreeConfig.TELEGRAM_GROUP_ID == 0L) {
            messages += "Set Group ID in Settings"
        }

        val tdJavaPresent = runCatching {
            Class.forName("org.drinkless.tdlib.Client")
            true
        }.getOrDefault(false)

        if (!tdJavaPresent) {
            messages += "Missing TDLib Java bindings (Client/TdApi). Add JAR/AAR to app/libs"
        }

        val nativeLoaded = runCatching {
            // Don't call loadLibrary here - it's loaded by TdLibClient
            System.loadLibrary("tdjni")
            true
        }.getOrElse { e ->
            e is UnsatisfiedLinkError && e.message?.contains("already loaded") == true
        }

        if (!nativeLoaded) {
            messages += "Missing native libtdjni.so for device ABI. Put it under app/src/main/jniLibs/<abi>/libtdjni.so"
        }

        return StartupCheckResult(
            isReady = messages.isEmpty(),
            messages = if (messages.isEmpty()) listOf("TDLib setup looks good") else messages
        )
    }
}
