package com.example.scanaggregator

/**
 * 本地规则引擎：按 rules.json 的配置顺序做匹配，返回第一条命中的规则；
 * 都没命中返回 null，由界面走"手动选择平台"分支。
 */
class RuleEngine(private val rules: List<ScanRule>) {

    fun match(content: String): ScanRule? {
        val target = UrlExtractor.extract(content)
        return rules.firstOrNull { it.matches(target) }
    }
}
