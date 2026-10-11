package com.example.scanaggregator

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch

/**
 * 「设置」页：自动复制、扫码提示音两个开关，写入 SharedPreferences 全局生效。
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences(ScanApp.PREFS_SETTINGS, MODE_PRIVATE)

        val autoCopySwitch = findViewById<MaterialSwitch>(R.id.switch_auto_copy)
        val beepSwitch = findViewById<MaterialSwitch>(R.id.switch_beep)

        autoCopySwitch.isChecked = prefs.getBoolean(ScanApp.KEY_AUTO_COPY, false)
        beepSwitch.isChecked = prefs.getBoolean(ScanApp.KEY_BEEP, false)

        autoCopySwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(ScanApp.KEY_AUTO_COPY, checked).apply()
        }
        beepSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(ScanApp.KEY_BEEP, checked).apply()
        }

        findViewById<TextView>(R.id.settings_auto_copy_desc).text =
            getString(R.string.settings_auto_copy_desc)
    }
}
