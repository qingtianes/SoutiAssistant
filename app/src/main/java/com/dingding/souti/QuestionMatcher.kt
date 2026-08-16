package com.dingding.souti

/**
 * 题目匹配打分组件：只负责比较 OCR 查询文本和题库题干的相关度。
 * 保持无状态、纯函数，方便单独测试。
 */
object QuestionMatcher {

    /**
     * 匹配打分：基于最长公共子串（LCS）。
     * - 比较前统一去除中英文括号和空白，避免排版差异导致漏匹配。
     * - 完全包含加 100 分。
     * - LCS 每个字符加 1 分。
     * - LCS 太短（小于 3）视为不匹配。
     */
    fun score(query: String, stem: String): Int {
        if (query.isBlank() || stem.isBlank()) return 0
        val normalizedQuery = normalize(query)
        val normalizedStem = normalize(stem)
        if (normalizedQuery.isBlank() || normalizedStem.isBlank()) return 0

        var score = 0
        if (normalizedQuery.length >= 4 &&
            (normalizedStem.contains(normalizedQuery) || normalizedQuery.contains(normalizedStem))
        ) {
            score += 100
        }

        val lcs = lcsLen(normalizedQuery, normalizedStem)
        if (lcs >= 3) score += lcs
        return score
    }

    private fun normalize(text: String): String =
        text.replace(Regex("[（()）]"), "").replace(Regex("\\s+"), "")

    /**
     * 最长公共子串长度（Longest Common Substring）。
     * OCR 识别结果可能比题库题干短，用 LCS 找最长匹配段。
     */
    private fun lcsLen(a: String, b: String): Int {
        if (a.isEmpty() || b.isEmpty()) return 0
        val rows = b.length + 1
        val prev = IntArray(rows)
        val curr = IntArray(rows)
        var maxLen = 0

        for (i in 1..a.length) {
            for (j in 1..b.length) {
                curr[j] = if (a[i - 1] == b[j - 1]) {
                    (if (j > 1) prev[j - 1] else 0) + 1
                } else {
                    0
                }
                if (curr[j] > maxLen) maxLen = curr[j]
            }
            for (j in 0..b.length) prev[j] = curr[j]
        }
        return maxLen
    }
}
