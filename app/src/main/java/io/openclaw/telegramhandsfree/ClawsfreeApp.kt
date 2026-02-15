package io.openclaw.telegramhandsfree

import android.app.Application
import io.openclaw.telegramhandsfree.config.ClawsfreeConfig

class ClawsfreeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ClawsfreeConfig.init(this)
    }
}
