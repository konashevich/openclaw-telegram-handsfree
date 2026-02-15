package io.openclaw.telegramhandsfree.config

import android.content.Context
import android.content.SharedPreferences

object NovaConfig {
    private const val PREFS_NAME = "nova_config"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    val TELEGRAM_API_ID: Int get() = prefs.getInt("api_id", 31058922)
    val TELEGRAM_API_HASH: String get() = prefs.getString("api_hash", "0be77c67e99960d0d2677963b25ddd55") ?: ""
    val TELEGRAM_PHONE_NUMBER: String get() = prefs.getString("phone_number", "+61414739692") ?: ""
    val TELEGRAM_AUTH_CODE: String get() = prefs.getString("auth_code", "") ?: ""
    val TELEGRAM_2FA_PASSWORD: String get() = prefs.getString("2fa_password", "") ?: ""
    val TELEGRAM_GROUP_ID: Long get() = prefs.getLong("group_id", -1003830049605L)
    val TELEGRAM_TOPIC_ID: Long get() = prefs.getLong("topic_id", 1L)
    val USE_BLUETOOTH_MIC: Boolean get() = prefs.getBoolean("use_bluetooth_mic", false)
    const val SILENCE_TIMEOUT_MS = 20_000L
    const val MAX_RECORDING_MS = 120_000L  // 2 minute hard cap

    fun setBluetoothMic(enabled: Boolean) {
        prefs.edit().putBoolean("use_bluetooth_mic", enabled).apply()
    }

    fun save(
        apiId: Int,
        apiHash: String,
        phoneNumber: String,
        authCode: String,
        twoFaPassword: String,
        groupId: Long,
        topicId: Long
    ) {
        prefs.edit()
            .putInt("api_id", apiId)
            .putString("api_hash", apiHash)
            .putString("phone_number", phoneNumber)
            .putString("auth_code", authCode)
            .putString("2fa_password", twoFaPassword)
            .putLong("group_id", groupId)
            .putLong("topic_id", topicId)
            .apply()
    }
}
