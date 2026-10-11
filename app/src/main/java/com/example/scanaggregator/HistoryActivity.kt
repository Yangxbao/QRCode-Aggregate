package com.example.scanaggregator

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private lateinit var adapter: HistoryAdapter
    private lateinit var emptyView: TextView
    private lateinit var searchView: EditText
    private lateinit var exportButton: TextView
    private lateinit var clearButton: TextView
    private lateinit var multiSelectButton: TextView
    private lateinit var deleteSelectedButton: TextView
    private lateinit var cancelButton: TextView

    private var allEntries: List<HistoryStore.Entry> = emptyList()
    private var entries: List<HistoryStore.Entry> = emptyList()
    private var query: String = ""
    private var selectionMode = false
    private val selectedIds = LinkedHashSet<String>()

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        emptyView = findViewById(R.id.empty_view)
        searchView = findViewById(R.id.search_view)
        exportButton = findViewById(R.id.export_button)
        clearButton = findViewById(R.id.clear_all)
        multiSelectButton = findViewById(R.id.multi_select)
        deleteSelectedButton = findViewById(R.id.delete_selected)
        cancelButton = findViewById(R.id.cancel_selection)

        val recycler = findViewById<RecyclerView>(R.id.history_list)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = HistoryAdapter()
        recycler.adapter = adapter

        searchView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString().orEmpty()
                refresh()
            }
        })
        multiSelectButton.setOnClickListener { setSelectionMode(true) }
        cancelButton.setOnClickListener { setSelectionMode(false) }
        deleteSelectedButton.setOnClickListener { deleteSelected() }
        exportButton.setOnClickListener { exportHistory() }
        clearButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage(R.string.history_clear_confirm)
                .setPositiveButton(R.string.confirm) { _, _ ->
                    HistoryStore.clear(this)
                    refresh()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        refresh()
    }

    private fun refresh() {
        allEntries = HistoryStore.load(this)
        entries = if (query.isEmpty()) {
            allEntries
        } else {
            allEntries.filter { it.content.contains(query, ignoreCase = true) }
        }
        adapter.notifyDataSetChanged()
        emptyView.text = if (allEntries.isEmpty()) {
            getString(R.string.history_empty)
        } else {
            getString(R.string.history_search_empty)
        }
        emptyView.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun setSelectionMode(enabled: Boolean) {
        selectionMode = enabled
        if (!enabled) selectedIds.clear()
        updateActionBars()
        adapter.notifyDataSetChanged()
    }

    private fun toggleSelection(entry: HistoryStore.Entry) {
        if (!selectedIds.remove(entry.id)) selectedIds.add(entry.id)
        updateActionBars()
        adapter.notifyDataSetChanged()
    }

    private fun updateActionBars() {
        // 常规操作（导出/清空）与批量操作（删除所选/取消）互斥展示
        exportButton.visibility = if (selectionMode) View.GONE else View.VISIBLE
        clearButton.visibility = if (selectionMode) View.GONE else View.VISIBLE
        multiSelectButton.visibility = if (selectionMode) View.GONE else View.VISIBLE
        deleteSelectedButton.visibility = if (selectionMode) View.VISIBLE else View.GONE
        cancelButton.visibility = if (selectionMode) View.VISIBLE else View.GONE
        deleteSelectedButton.text = getString(R.string.history_delete_selected_fmt, selectedIds.size)
    }

    private fun deleteSelected() {
        if (selectedIds.isEmpty()) {
            Toast.makeText(this, R.string.history_none_selected, Toast.LENGTH_SHORT).show()
            return
        }
        val count = selectedIds.size
        HistoryStore.removeMany(this, selectedIds)
        setSelectionMode(false)
        refresh()
        Toast.makeText(this, getString(R.string.history_deleted_fmt, count), Toast.LENGTH_SHORT).show()
    }

    private fun copyEntry(entry: HistoryStore.Entry) {
        copyToClipboard(this, entry.content)
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    // 用当前扫码模式重新处理该链接：把内容带回 MainActivity 处理
    private fun reprocess(entry: HistoryStore.Entry) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_REPROCESS, entry.content))
        finish()
    }

    private fun showItemMenu(entry: HistoryStore.Entry) {
        val options = arrayOf(
            getString(R.string.history_menu_copy),
            getString(R.string.history_menu_reprocess),
            getString(R.string.delete)
        )
        AlertDialog.Builder(this)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> copyEntry(entry)
                    1 -> reprocess(entry)
                    2 -> {
                        HistoryStore.remove(this, entry.id)
                        refresh()
                    }
                }
            }
            .show()
    }

    private fun exportHistory() {
        if (allEntries.isEmpty()) {
            Toast.makeText(this, R.string.history_export_empty, Toast.LENGTH_SHORT).show()
            return
        }
        Thread {
            val path = try {
                doExport(allEntries)
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                if (path != null) {
                    Toast.makeText(
                        this,
                        getString(R.string.history_export_done_fmt, allEntries.size, path),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(this, R.string.history_export_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun doExport(entries: List<HistoryStore.Entry>): String? {
        val body = buildString {
            append("扫码历史导出（共 ").append(entries.size).append(" 条）\n")
            append("导出时间：").append(timeFormat.format(Date())).append('\n')
            append("========\n")
            entries.forEach { e ->
                append('[').append(timeFormat.format(Date(e.time))).append("] ")
                append("模式：").append(e.mode)
                e.app?.let { append(" · 应用：").append(it) }
                append('\n').append(e.content).append("\n----\n")
            }
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportViaMediaStore(body)
        } else {
            exportLegacy(body)
        }
    }

    // Android 10+ 写公共 Download 目录无需权限
    private fun exportViaMediaStore(body: String): String? {
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, "scan_history_${System.currentTimeMillis()}.txt")
            put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Download/ScanAggregator")
        }
        val uri = contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return null
        contentResolver.openOutputStream(uri)?.use {
            it.write(body.toByteArray(Charsets.UTF_8))
        } ?: return null
        return "Download/ScanAggregator"
    }

    // Android 9 及以下写到应用专属外部存储目录（免权限）
    private fun exportLegacy(body: String): String? {
        val dir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS) ?: return null
        val file = File(dir, "scan_history_${System.currentTimeMillis()}.txt")
        file.writeText(body, Charsets.UTF_8)
        return file.absolutePath
    }

    private inner class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val checkbox: CheckBox = view.findViewById(R.id.item_check)
            val content: TextView = view.findViewById(R.id.item_content)
            val meta: TextView = view.findViewById(R.id.item_meta)
            val delete: TextView = view.findViewById(R.id.item_delete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_history, parent, false)
            return ViewHolder(view)
        }

        override fun getItemCount() = entries.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val entry = entries[position]
            holder.content.text = entry.content
            holder.meta.text = buildString {
                append(timeFormat.format(Date(entry.time)))
                append(" · ")
                append(entry.mode)
                entry.app?.let { append(" · "); append(it) }
            }

            holder.checkbox.setOnCheckedChangeListener(null)
            holder.checkbox.isChecked = selectedIds.contains(entry.id)
            holder.checkbox.visibility = if (selectionMode) View.VISIBLE else View.GONE
            holder.checkbox.setOnCheckedChangeListener { _, checked ->
                if (checked) selectedIds.add(entry.id) else selectedIds.remove(entry.id)
                updateActionBars()
            }
            holder.delete.visibility = if (selectionMode) View.GONE else View.VISIBLE
            holder.itemView.setOnClickListener {
                if (selectionMode) toggleSelection(entry) else copyEntry(entry)
            }
            holder.itemView.setOnLongClickListener {
                if (!selectionMode) showItemMenu(entries[holder.bindingAdapterPosition])
                true
            }
            holder.delete.setOnClickListener {
                HistoryStore.remove(this@HistoryActivity, entry.id)
                refresh()
            }
        }
    }

    companion object {
        const val EXTRA_REPROCESS = "extra_reprocess"
    }
}
