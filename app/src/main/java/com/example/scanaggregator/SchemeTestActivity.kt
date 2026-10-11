package com.example.scanaggregator

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * 「Scheme测试」页：输入任意 Scheme 链接尝试唤起，失败弹出统一调试弹窗（可复制 Scheme）。
 * 独立模块，不与扫码/规则引擎耦合。
 */
class SchemeTestActivity : AppCompatActivity() {

    private lateinit var inputView: EditText

    private val jumpLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scheme_test)

        inputView = findViewById(R.id.scheme_input)
        findViewById<Button>(R.id.launch_button).setOnClickListener { tryLaunch() }
    }

    private fun tryLaunch() {
        val raw = inputView.text.toString().trim()
        if (raw.isEmpty()) {
            Toast.makeText(this, R.string.scheme_test_input_empty, Toast.LENGTH_SHORT).show()
            return
        }
        // 只输 scheme 名（如 weixin）时自动补全为 weixin://
        val scheme = if (raw.contains("://")) raw else "$raw://"
        val launched = try {
            jumpLauncher.launch(Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(scheme)))
            true
        } catch (e: Exception) {
            false
        }
        if (launched) {
            Toast.makeText(this, R.string.scheme_test_success, Toast.LENGTH_SHORT).show()
        } else {
            showDebugDialog(scheme)
        }
    }

    // 与「应用快捷启动」页一致的统一调试弹窗：展示 Scheme、可复制、底部常见原因
    private fun showDebugDialog(scheme: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.debug_dialog_title)
            .setMessage(
                buildString {
                    append("Scheme：").append(scheme)
                    append("\n\n").append(getString(R.string.debug_dialog_hint))
                }
            )
            .setPositiveButton(R.string.debug_copy_scheme) { _, _ ->
                copyToClipboard(this, scheme)
                Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
