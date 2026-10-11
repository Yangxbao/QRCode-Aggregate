package com.example.scanaggregator

/** 规则匹配方式 */
enum class MatchType { CONTAINS, REGEX }

/** 命中规则后的动作 */
enum class RuleAction { SCHEME, COPY_HINT }

/**
 * 一条本地跳转规则，来自 assets/rules.json。
 * 规则按配置顺序生效，第一条命中的规则决定跳转行为。
 */
data class ScanRule(
    val id: String,
    val name: String,
    val matchType: MatchType,
    val patterns: List<String>,
    val action: RuleAction,
    val target: String?,          // SCHEME：跳转 URI 模板，可含 {url} / {url_encoded} 占位符
    val hint: String?,            // COPY_HINT：页面提示文案
    val tip: String?,             // 自定义提示：scheme 命中时替代默认「正在打开X…」
    val isBuiltIn: Boolean = true,   // 内置规则可做安装检测；本机勾选的自定义规则无法预检
    val packageName: String? = null  // 自定义规则对应的应用包名
) {
    fun matches(text: String): Boolean = patterns.any { pattern ->
        when (matchType) {
            // "*" 为通配：匹配任意内容（自定义规则的兜底关键词）
            MatchType.CONTAINS -> pattern == "*" || text.contains(pattern, ignoreCase = true)
            MatchType.REGEX -> runCatching {
                Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(text)
            }.getOrDefault(false)
        }
    }
}

/**
 * 从混合文本里提取第一个 http(s) 链接（没有链接时返回原文）再交给规则匹配，
 * 这样"分享文案 + 链接"混在一起的内容也能命中规则。
 */
object UrlExtractor {
    private val URL_REGEX = Regex("https?://\\S+")
    private val TRAILING_NOISE =
        charArrayOf(',', '，', '.', '。', ';', '；', ')', '）', ']', '】', '"', '\'', '>', '》')

    fun extract(content: String): String =
        URL_REGEX.find(content)?.value?.trimEnd(*TRAILING_NOISE) ?: content
}
