package com.dingding.souti.ocr

import com.dingding.souti.SearchResult

/**
 * OCR 文本到题库查询之间的纯 Kotlin 处理逻辑。
 *
 * 这里不依赖 Android 的 Context、View、Handler 或录屏资源，便于用本地单元测试锁定行为。
 */
object OcrQuestionProcessor {
    private val answerSeparator = Regex("答案\\s*[:：]")
    private val optionMarker = Regex("[A-E][.、．、)）]")

    data class ScanResult(
        val normalizedText: String,
        val queries: List<String>,
        val matches: List<SearchResult>,
        val uniqueMatchCount: Int
    )

    fun normalizeText(text: String): String =
        text.replace("\n", " ").replace(Regex("\\s+"), " ").trim()

    fun extractScanQueries(text: String): List<String> {
        val cleaned = normalizeText(text)
        if (cleaned.isBlank()) return emptyList()

        return cleaned.split(answerSeparator).mapNotNull { segment ->
            val trimmed = segment.trim()
            if (trimmed.length < 4) return@mapNotNull null

            val parenthesisIndex = maxOf(trimmed.lastIndexOf('('), trimmed.lastIndexOf('（'))
            val stem = if (parenthesisIndex > 3) {
                trimmed.substring(0, parenthesisIndex).trim()
            } else {
                trimmed.take(20)
            }
            stem.takeIf { it.isNotBlank() }
        }
    }

    fun processScanText(
        text: String,
        search: (query: String, limit: Int) -> List<SearchResult>,
        resultLimit: Int = 5,
        perQueryLimit: Int = 5
    ): ScanResult? {
        if (text.isBlank()) return null

        val normalizedText = normalizeText(text)
        val queries = extractScanQueries(normalizedText)
        val merged = LinkedHashMap<Long, SearchResult>()

        queries.forEach { query ->
            if (query.length < 3) return@forEach
            search(query, perQueryLimit).forEach { result ->
                val previous = merged[result.question.id]
                if (previous == null || result.score > previous.score) {
                    merged[result.question.id] = result
                }
            }
        }

        return ScanResult(
            normalizedText = normalizedText,
            queries = queries,
            matches = merged.values.sortedByDescending { it.score }.take(resultLimit),
            uniqueMatchCount = merged.size
        )
    }

    /**
     * 用“答案：”作为题目分隔锚点。只有成功得到至少两段时才进入多题模式；
     * 否则保持旧行为，把原文作为一个整体返回。
     */
    fun splitScreenReadQuestions(text: String): List<String> {
        val questions = text.split(answerSeparator).mapNotNull { part ->
            part.trim()
                .replace(Regex("^[A-Ea-e]\\s*"), "")
                .trim()
                .takeIf { it.length >= 4 }
        }
        return if (questions.size >= 2) questions else listOf(text)
    }

    /** 保持现有多题搜索规则：去选项、括号内容和开头的《……》依据前缀。 */
    fun extractScreenReadStem(segment: String): String {
        val option = optionMarker.find(segment)
        val stemPart = if (option != null) segment.substring(0, option.range.first) else segment
        return stemPart
            .replace(Regex("[（(][^（）()]*[）)]"), "")
            .replace(Regex("^[^《]*《[^》]*》"), "")
            .replace(Regex("^[,，。.、\\s]+"), "")
            .replace(Regex("\\s+"), "")
            .trim()
    }
}
