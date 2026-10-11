package com.example.scanaggregator

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 扫码历史：SharedPreferences 存 JSON 数组，新记录在最前，最多保留 100 条。
 */
object HistoryStore {

    private const val PREFS_NAME = "scan_history"
    private const val KEY_ITEMS = "items"
    private const val MAX_ITEMS = 100

    data class Entry(
        val id: String,
        val content: String,
        val time: Long,      // 扫码时间（epoch 毫秒）
        val mode: String,    // 自动识别 / 仅复制链接 / 指定应用
        val app: String?     // 匹配到的应用名，未匹配时为 null
    )

    fun load(context: Context): List<Entry> = try {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray(prefs.getString(KEY_ITEMS, "[]"))
        (0 until array.length()).mapNotNull { i ->
            val obj = array.getJSONObject(i)
            Entry(
                id = obj.optString("id"),
                content = obj.optString("content"),
                time = obj.optLong("time"),
                mode = obj.optString("mode"),
                app = obj.optString("app").ifEmpty { null }
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun add(context: Context, content: String, mode: String, app: String?) {
        val entry = Entry(UUID.randomUUID().toString(), content, System.currentTimeMillis(), mode, app)
        save(context, (listOf(entry) + load(context)).take(MAX_ITEMS))
    }

    fun remove(context: Context, id: String) {
        save(context, load(context).filterNot { it.id == id })
    }

    fun removeMany(context: Context, ids: Collection<String>) {
        save(context, load(context).filterNot { it.id in ids })
    }

    fun clear(context: Context) {
        save(context, emptyList())
    }

    private fun save(context: Context, entries: List<Entry>) {
        val array = JSONArray()
        entries.forEach { e ->
            array.put(JSONObject().apply {
                put("id", e.id)
                put("content", e.content)
                put("time", e.time)
                put("mode", e.mode)
                put("app", e.app ?: "")
            })
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_ITEMS, array.toString()).apply()
    }
}
