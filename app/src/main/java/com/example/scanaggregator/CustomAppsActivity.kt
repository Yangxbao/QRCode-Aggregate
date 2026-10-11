package com.example.scanaggregator

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * 「扫描本机应用」页：列出本机可启动的应用，内置 36 个置灰不可选，
 * 其余勾选保存为自定义规则（keyword=* 通配，scheme 靠启发式检测）。
 */
class CustomAppsActivity : AppCompatActivity() {

    private data class Row(
        val label: String,
        val packageName: String,
        val scheme: String?,
        val isBuiltin: Boolean,
        var checked: Boolean
    )

    private lateinit var adapter: AppAdapter
    private lateinit var emptyView: TextView
    private lateinit var progress: ProgressBar
    private lateinit var selectAllButton: TextView
    private var rows: List<Row> = emptyList()
    private var scanning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_local_apps)

        emptyView = findViewById(R.id.empty_view)
        progress = findViewById(R.id.progress)
        selectAllButton = findViewById(R.id.select_all)
        val recycler = findViewById<RecyclerView>(R.id.app_list)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = AppAdapter()
        recycler.adapter = adapter

        findViewById<TextView>(R.id.refresh).setOnClickListener { if (!scanning) reload() }
        selectAllButton.setOnClickListener { toggleSelectAll() }
        findViewById<TextView>(R.id.save).setOnClickListener { saveAndFinish() }

        reload()
    }

    private fun reload() {
        scanning = true
        progress.visibility = View.VISIBLE
        emptyView.text = getString(R.string.local_apps_scanning)
        emptyView.visibility = View.VISIBLE
        // 扫描 + scheme 逐候选检测较耗时，放工作线程
        Thread {
            val scanned = scanInstalledApps()
            runOnUiThread {
                scanning = false
                progress.visibility = View.GONE
                rows = scanned
                adapter.notifyDataSetChanged()
                emptyView.text = getString(R.string.local_apps_empty)
                emptyView.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
                updateSelectAllLabel()
            }
        }.start()
    }

    private fun scanInstalledApps(): List<Row> {
        val pm = packageManager
        val builtins = RuleConfigLoader.load(this)
        val builtinNames = builtins.map { it.name }.toSet()
        val builtinSchemes = builtins.mapNotNull { it.target?.removeSuffix("://") }.toSet()
        val savedPkgs = CustomAppsStore.loadApps(this).map { it.packageName }.toSet()
        val self = packageName

        return pm.getInstalledApplications(0)
            .filter { it.packageName != self && pm.getLaunchIntentForPackage(it.packageName) != null }
            .map { ai ->
                val label = ai.loadLabel(pm).toString()
                val scheme = detectScheme(pm, ai)
                val isBuiltin = label in builtinNames || (scheme != null && scheme in builtinSchemes)
                Row(
                    label = label,
                    packageName = ai.packageName,
                    scheme = scheme,
                    isBuiltin = isBuiltin,
                    checked = !isBuiltin && ai.packageName in savedPkgs
                )
            }
            .sortedWith(compareBy({ !it.isBuiltin }, { it.label }))
    }

    // Android 公开 API 无法枚举任意 App 注册的 scheme，用包名分段作为候选 scheme，
    // 逐个测试能否解析到该应用本身；测不到返回 null（该应用只能走复制流程）
    private fun detectScheme(pm: PackageManager, info: ApplicationInfo): String? {
        val candidates = LinkedHashSet<String>()
        info.packageName.split('.', '_', '-').forEach {
            if (it.length >= 2 && it != "com") candidates.add(it.lowercase())
        }
        for (candidate in candidates) {
            if (!candidate.matches(Regex("[a-zA-Z][a-zA-Z0-9+.-]*"))) continue
            try {
                val ri = pm.resolveActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$candidate://")), 0)
                if (ri?.activityInfo?.packageName == info.packageName) return candidate
            } catch (e: Exception) {
                // 单个候选失败继续尝试下一个
            }
        }
        return null
    }

    private fun toggleSelectAll() {
        val targets = rows.filter { !it.isBuiltin }
        if (targets.isEmpty()) return
        val allChecked = targets.all { it.checked }
        targets.forEach { it.checked = !allChecked }
        adapter.notifyDataSetChanged()
        updateSelectAllLabel()
    }

    private fun updateSelectAllLabel() {
        val targets = rows.filter { !it.isBuiltin }
        selectAllButton.text = getString(
            if (targets.isNotEmpty() && targets.all { it.checked }) {
                R.string.local_apps_deselect_all
            } else {
                R.string.local_apps_select_all
            }
        )
    }

    private fun saveAndFinish() {
        val apps = rows.filter { !it.isBuiltin && it.checked }
            .map { CustomAppsStore.CustomApp(it.packageName, it.label, it.scheme) }
        CustomAppsStore.saveApps(this, apps)
        Toast.makeText(this, getString(R.string.local_apps_saved_fmt, apps.size), Toast.LENGTH_SHORT).show()
        finish()
    }

    private inner class AppAdapter : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val checkbox: CheckBox = view.findViewById(R.id.item_checkbox)
            val title: TextView = view.findViewById(R.id.item_title)
            val subtitle: TextView = view.findViewById(R.id.item_subtitle)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_local_app, parent, false)
            return ViewHolder(view)
        }

        override fun getItemCount() = rows.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val row = rows[position]
            holder.title.text = row.label
            holder.subtitle.text = buildString {
                append(row.packageName)
                append(" · ")
                if (row.isBuiltin) {
                    append(getString(R.string.local_apps_builtin_tag))
                } else {
                    append(
                        getString(
                            R.string.local_apps_scheme_fmt,
                            row.scheme ?: getString(R.string.local_apps_scheme_none)
                        )
                    )
                }
            }
            holder.checkbox.setOnCheckedChangeListener(null)
            holder.checkbox.isChecked = row.checked
            holder.checkbox.isEnabled = !row.isBuiltin
            holder.checkbox.setOnCheckedChangeListener { _, checked ->
                row.checked = checked
                updateSelectAllLabel()
            }
            // 内置应用置灰不可勾选，避免重复添加
            holder.itemView.alpha = if (row.isBuiltin) 0.4f else 1f
            holder.itemView.setOnClickListener {
                if (!row.isBuiltin) {
                    row.checked = !row.checked
                    holder.checkbox.isChecked = row.checked
                    updateSelectAllLabel()
                }
            }
        }
    }
}
