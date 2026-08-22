package com.dingding.souti.import

import com.dingding.souti.model.Question

/** 将解析器输出的“一题整块文本”拆成可搜索的结构化题目。 */
object QuestionChunkParser {
    private val optionMarker = Regex("(?:^|[\\s\\u00A0])([A-Ha-h])[\\s\\u00A0]*[.、．:：)）][\\s\\u00A0]*")
    private val numberPrefix = Regex("^\\s*\\d+\\s*[.、．:：)）]\\s*")
    private val embeddedJudgement = Regex(
        "^\\s*([（(][\\s\\u00A0]*\\d{3,}[\\s\\u00A0]*[）)])?[\\s\\u00A0]*[（(][\\s\\u00A0]*([√×xX对错])[\\s\\u00A0]*[）)][\\s\\u00A0]*(.*)$"
    )

    fun parse(chunk: String, source: String, id: Long): Question? {
        val rawText = chunk.trim()
        val lines = rawText.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return null

        val stemLines = mutableListOf<String>()
        val options = mutableListOf<String>()
        val answerLines = mutableListOf<String>()
        var implicitAnswer = ""
        var inAnswer = false

        lines.forEachIndexed { index, originalLine ->
            var line = originalLine
            if (index == 0) {
                val withoutNumber = line.replaceFirst(numberPrefix, "")
                embeddedJudgement.find(withoutNumber)?.let { match ->
                    val resourceId = match.groupValues[1].trim()
                    val marker = match.groupValues[2]
                    val body = match.groupValues[3].trim()
                    line = listOf(resourceId, body).filter(String::isNotBlank).joinToString("")
                    implicitAnswer = when (marker.lowercase()) {
                        "√", "对" -> "√ 正确"
                        else -> "× 错误"
                    }
                }
            }

            val answerPayload = QuestionSyntax.answerPayload(line)
            val parsedOptions = if (!inAnswer) parseOptions(line) else emptyList()
            when {
                answerPayload != null -> {
                    inAnswer = true
                    answerPayload.takeIf { it.isNotEmpty() }?.let(answerLines::add)
                }
                parsedOptions.isNotEmpty() -> options.addAll(parsedOptions)
                inAnswer -> answerLines.add(line)
                else -> stemLines.add(line)
            }
        }

        val stem = stemLines.joinToString("\n").trim().replaceFirst(numberPrefix, "")
        if (stem.isBlank()) return null
        return Question(
            id = id,
            bankId = 0,
            stem = stem,
            options = options,
            answer = answerLines.joinToString("\n").trim().ifBlank { implicitAnswer },
            source = source,
            rawText = rawText
        )
    }

    /** 支持 PDF 将 A/B/C/D 多个选项排在同一行，同时保留原始标签。 */
    private fun parseOptions(line: String): List<String> {
        val matches = optionMarker.findAll(line).toList()
        if (matches.isEmpty() || matches.first().range.first != 0) return emptyList()
        return matches.mapIndexedNotNull { index, match ->
            val bodyStart = match.range.last + 1
            val bodyEnd = matches.getOrNull(index + 1)?.range?.first ?: line.length
            line.substring(bodyStart, bodyEnd).trim().takeIf(String::isNotBlank)?.let { body ->
                "${match.groupValues[1].uppercase()}. $body"
            }
        }
    }
}
