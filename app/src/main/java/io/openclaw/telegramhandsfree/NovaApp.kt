package io.openclaw.telegramhandsfree

import android.app.Application
import io.openclaw.telegramhandsfree.config.NovaConfig

class NovaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NovaConfig.init(this)
    }
}
