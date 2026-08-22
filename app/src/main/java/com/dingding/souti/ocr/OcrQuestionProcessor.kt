package com.dingding.souti.ocr

import com.dingding.souti.model.SearchResult

/** 三种 OCR 搜题入口共用的纯 Kotlin 查询准备与结果合并逻辑。 */
object OcrQuestionProcessor {
    private val answerMarker = Regex("答\\s*案\\s*[:：;；]?\\s*", RegexOption.IGNORE_CASE)
    private val numberedQuestion = Regex("(?m)^\\s*\\d{1,4}\\s*[.、．:：)）]\\s*\\S+")
    private val shortAnswerPayload = Regex(
        "^(?:[A-Ha-h]{1,8}|正确|错误|对|错|是|否|√|×|true|false)(?=\\s|$)",
        RegexOption.IGNORE_CASE
    )

    data class ScanResult(
        val normalizedText: String,
        val queries: List<String>,
        val matches: List<SearchResult>,
        val uniqueMatchCount: Int
    )

    fun normalizeText(text: String): String =
        text.replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun normalizeStructuredText(text: String): String =
        text.replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(Regex("[\\t ]+"), " ")
            .replace(Regex(" *\\n+ *"), "\n")
            .trim()

    /**
     * 优先按可靠题号行切分；否则使用“答案”锚点，并只消费可确认的短答案载荷。
     * 无法可靠切分时保留完整原文，不做固定长度截断、不删除括号/法规前缀/选项。
     */
    fun extractScanQueries(text: String): List<String> {
        val structured = normalizeStructuredText(text)
        if (structured.isBlank()) return emptyList()

        splitByNumberedLines(structured).takeIf { it.size >= 2 }
            ?.let { return it.map(::normalizeText) }
        splitByAnswerMarkers(structured).takeIf { it.size >= 2 }
            ?.let { return it.map(::normalizeText) }
        return listOf(normalizeText(structured))
    }

    fun processScanText(
        text: String,
        search: (query: String, limit: Int) -> List<SearchResult>,
        resultLimit: Int = 5,
        perQueryLimit: Int = 5
    ): ScanResult? {
        if (text.isBlank()) return null

        val normalizedText = normalizeText(text)
        val queries = extractScanQueries(text)
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

    fun splitScreenReadQuestions(text: String): List<String> = extractScanQueries(text)

    /** 保留完整题目语义，仅统一空白；名称保留以兼容现有调用。 */
    fun extractScreenReadStem(segment: String): String = normalizeText(segment)

    private fun splitByNumberedLines(text: String): List<String> {
        val matches = numberedQuestion.findAll(text).toList()
        if (matches.size < 2) return emptyList()
        return matches.mapIndexedNotNull { index, match ->
            val end = if (index + 1 < matches.size) matches[index + 1].range.first else text.length
            text.substring(match.range.first, end).trim().takeIf { it.length >= 4 }
        }
    }

    private fun splitByAnswerMarkers(text: String): List<String> {
        val markers = answerMarker.findAll(text).toList()
        if (markers.isEmpty()) return emptyList()

        val segments = mutableListOf<String>()
        var cursor = 0
        markers.forEach { marker ->
            text.substring(cursor, marker.range.first).trim()
                .takeIf { it.length >= 4 }
                ?.let(segments::add)

            val afterMarker = marker.range.last + 1
            val remainder = text.substring(afterMarker)
            val payload = shortAnswerPayload.find(remainder)
            cursor = afterMarker + (payload?.value?.length ?: 0)
            while (cursor < text.length && text[cursor].isWhitespace()) cursor++
        }
        text.substring(cursor).trim().takeIf { it.length >= 4 }?.let(segments::add)
        return segments
    }
}
