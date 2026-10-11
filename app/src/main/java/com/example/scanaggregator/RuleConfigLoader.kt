package com.example.scanaggregator

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * 规则表从 assets/rules.json 读取：改规则只改配置文件，不用动代码。
 * 顶层支持 {"rules":[...]} 或直接写 [...] 两种结构。
 */
object RuleConfigLoader {

    private const val TAG = "RuleConfigLoader"
    private const val ASSET_NAME = "rules.json"

    // 「识别到学习通链接」→「学习通」，取中间部分作为下拉框显示名
    private val TIP_NAME_REGEX = Regex("^识别到(.+)链接$")

    fun load(context: Context): List<ScanRule> = try {
        val text = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        parse(text)
    } catch (e: Exception) {
        Log.e(TAG, "读取 $ASSET_NAME 失败", e)
        emptyList()
    }

    /** 首页实际使用的完整规则表：内置 36 条 + 用户勾选添加的自定义规则 */
    fun loadAll(context: Context): List<ScanRule> = load(context) + CustomAppsStore.loadRules(context)

    internal fun parse(text: String): List<ScanRule> {
        val trimmed = text.trim()
        val array = if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else {
            JSONObject(trimmed).optJSONArray("rules") ?: return emptyList()
        }
        return (0 until array.length()).mapNotNull { index ->
            val obj = array.getJSONObject(index)

            // patterns 数组、pattern、keyword 三种写法等价；keyword 里可用 | 分隔多个关键词
            val patterns = buildList {
                obj.optJSONArray("patterns")?.let { ja ->
                    (0 until ja.length()).forEach { i -> add(ja.getString(i)) }
                }
                obj.optString("pattern").takeIf { it.isNotEmpty() }?.let { add(it) }
                obj.optString("keyword").takeIf { it.isNotEmpty() }
                    ?.split('|')
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.forEach { add(it) }
            }
            if (patterns.isEmpty()) return@mapNotNull null

            ScanRule(
                id = obj.optString("id", "rule_$index"),
                name = displayName(obj, index),
                matchType = if (obj.optString("matchType").equals("regex", true)) {
                    MatchType.REGEX
                } else {
                    MatchType.CONTAINS
                },
                patterns = patterns,
                action = if (obj.optString("action").equals("copy_hint", true)) {
                    RuleAction.COPY_HINT
                } else {
                    RuleAction.SCHEME
                },
                // target 和 appScheme 等价（appScheme 是简写风格）；package 为包名，安装检测优先用
                target = obj.optString("target").ifEmpty { obj.optString("appScheme") }.ifEmpty { null },
                packageName = obj.optString("package").ifEmpty { null },
                hint = obj.optString("hint").ifEmpty { null },
                tip = obj.optString("tip").ifEmpty { null }
            )
        }
    }

    // 下拉框显示名：name > id > tip（「识别到X链接」取 X，或「X链接」去掉后缀）> keyword
    private fun displayName(obj: JSONObject, index: Int): String {
        obj.optString("name").takeIf { it.isNotEmpty() }?.let { return it }
        obj.optString("id").takeIf { it.isNotEmpty() }?.let { return it }
        obj.optString("tip").takeIf { it.isNotEmpty() }
            ?.let { nameFromTip(it) }
            ?.let { return it }
        obj.optString("keyword").takeIf { it.isNotEmpty() }?.let { return it }
        return "rule_$index"
    }

    private fun nameFromTip(tip: String): String? {
        TIP_NAME_REGEX.find(tip)?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }?.let { return it }
        // 兼容不带「识别到」前缀的 tip（如「阿里云盘链接」）
        return tip.removeSuffix("链接").takeIf { it.isNotEmpty() }
    }
}
