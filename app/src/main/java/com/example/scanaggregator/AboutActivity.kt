package com.example.scanaggregator

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        val info = try {
            packageManager.getPackageInfo(packageName, 0)
        } catch (e: Exception) {
            null
        }
        val versionName = info?.versionName ?: "?"
        @Suppress("DEPRECATION")
        val versionCode = info?.versionCode ?: 0

        findViewById<TextView>(R.id.about_version).text =
            getString(R.string.about_version_fmt, versionName, versionCode)
        findViewById<TextView>(R.id.about_build).text =
            getString(R.string.about_build_fmt, BuildConfig.BUILD_TIME)
        findViewById<TextView>(R.id.about_rules).text =
            getString(R.string.about_rules_fmt, RuleConfigLoader.load(this).size)

        findViewById<TextView>(R.id.about_github).setOnClickListener {
            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW, android.net.Uri.parse(getString(R.string.github_url)))
                )
            } catch (e: Exception) {
                Toast.makeText(this, R.string.about_no_browser, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
