package com.example.scanaggregator

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * 「应用快捷启动」页：展示内置 + 自定义全部应用，点击直接尝试唤起。
 * 快捷跳转从这里走，扫码模式下拉不再承担跳转职能。
 */
class QuickLaunchActivity : AppCompatActivity() {

    private lateinit var adapter: AppAdapter
    private lateinit var platformActions: PlatformActions
    private var rules: List<ScanRule> = emptyList()

    private val jumpLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quick_launch)

        platformActions = PlatformActions(this, jumpLauncher)
        val recycler = findViewById<RecyclerView>(R.id.app_list)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = AppAdapter()
        recycler.adapter = adapter

        findViewById<TextView>(R.id.scan_apps).setOnClickListener {
            startActivity(Intent(this, CustomAppsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        // 从「扫描本机应用」返回时刷新列表，勾选的自定义应用立即出现
        rules = RuleConfigLoader.loadAll(this)
        adapter.notifyDataSetChanged()
    }

    private fun launchApp(rule: ScanRule) {
        // 内置应用：先做安装检测（包名优先 + scheme 回退），未安装直接提示
        if (rule.isBuiltIn && !platformActions.isAppInstalled(rule)) {
            Toast.makeText(this, getString(R.string.not_installed_short_fmt, rule.name), Toast.LENGTH_SHORT).show()
            return
        }
        // 自定义应用：跳过检测直接唤起；失败弹出调试弹窗（展示 Scheme 并可复制）
        val scheme = rule.target.orEmpty()
        if (!platformActions.tryLaunchApp(rule)) {
            showDebugDialog(rule, scheme)
        }
    }

    // 唤起失败调试弹窗：展示应用名与 Scheme，可一键复制 Scheme，底部给出常见原因
    private fun showDebugDialog(rule: ScanRule, scheme: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.debug_dialog_title)
            .setMessage(
                buildString {
                    append("应用：").append(rule.name).append('\n')
                    append("Scheme：").append(scheme.ifEmpty { getString(R.string.quick_launch_scheme_none) })
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

    private inner class AppAdapter : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.item_title)
            val subtitle: TextView = view.findViewById(R.id.item_subtitle)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_quick_app, parent, false)
            return ViewHolder(view)
        }

        override fun getItemCount() = rules.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val rule = rules[position]
            holder.title.text = rule.name
            holder.subtitle.text = buildString {
                if (!rule.isBuiltIn) append("自定义 · ")
                append(rule.target ?: getString(R.string.quick_launch_scheme_none))
            }
            holder.itemView.setOnClickListener { launchApp(rule) }
        }
    }
}
