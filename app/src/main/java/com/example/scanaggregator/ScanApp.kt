package com.example.scanaggregator

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

/**
 * 进程启动时恢复上次选择的主题，保证任何页面（含进程被杀后重建的页面）都用对主题。
 */
class ScanApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE)
        AppCompatDelegate.setDefaultNightMode(
            prefs.getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_NO)
        )
    }

    companion object {
        const val PREFS_SETTINGS = "settings"
        const val KEY_NIGHT_MODE = "night_mode"
        const val KEY_AUTO_COPY = "auto_copy"
        const val KEY_BEEP = "beep"
    }
}
