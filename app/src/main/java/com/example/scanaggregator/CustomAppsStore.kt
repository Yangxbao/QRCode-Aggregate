package com.example.scanaggregator

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 用户在「扫描本机应用」页勾选添加的自定义规则，存 SharedPreferences，重启不丢。
 *
 * keyword 固定用 "*"（公开 API 无法获取 App 官网域名，按需求兜底匹配任意内容）；
 * scheme 来自启发式检测，可能为 null——此时该应用仅支持 App内扫一扫的复制流程。
 */
object CustomAppsStore {

    private const val PREFS_NAME = "custom_apps"
    private const val KEY_RULES = "rules"

    data class CustomApp(
        val packageName: String,
        val name: String,
        val appScheme: String?
    )

    fun loadApps(context: Context): List<CustomApp> = try {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray(prefs.getString(KEY_RULES, "[]"))
        (0 until array.length()).mapNotNull { i ->
            val obj = array.getJSONObject(i)
            val pkg = obj.optString("packageName").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            CustomApp(
                packageName = pkg,
                name = obj.optString("name"),
                appScheme = obj.optString("appScheme").ifEmpty { null }
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun saveApps(context: Context, apps: List<CustomApp>) {
        val array = JSONArray()
        apps.forEach { app ->
            array.put(JSONObject().apply {
                put("packageName", app.packageName)
                put("name", app.name)
                put("appScheme", app.appScheme ?: "")
            })
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_RULES, array.toString()).apply()
    }

    fun loadRules(context: Context): List<ScanRule> = loadApps(context).map { app ->
        ScanRule(
            id = "custom:${app.packageName}",
            name = app.name,
            matchType = MatchType.CONTAINS,
            patterns = listOf("*"), // 通配：匹配任意二维码内容
            action = RuleAction.SCHEME,
            target = app.appScheme?.let { "$it://" },
            hint = null,
            tip = null,
            isBuiltIn = false,
            packageName = app.packageName
        )
    }
}
