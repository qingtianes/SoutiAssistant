package com.dingding.souti.import

import com.dingding.souti.model.Question

/** 将解析器输出的“一题整块文本”拆成可搜索的结构化题目。 */
object QuestionChunkParser {
    private val optionPattern = Regex("^([A-Za-z])\\s*[.、．)）]\\s*(.*)$")
    private val answerPattern = Regex("^\\s*(答案|正确答案)\\s*[:：]?\\s*(.*)$", RegexOption.IGNORE_CASE)
    private val numberPrefix = Regex("^\\d+[.、]\\s*")

    fun parse(chunk: String, source: String, id: Long): Question? {
        val lines = chunk.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return null

        val stemLines = mutableListOf<String>()
        val options = mutableListOf<String>()
        val answerLines = mutableListOf<String>()
        var inAnswer = false

        for (line in lines) {
            val answerMatch = answerPattern.find(line)
            val optionMatch = optionPattern.find(line)
            when {
                answerMatch != null -> {
                    inAnswer = true
                    answerMatch.groupValues[2].trim().takeIf { it.isNotEmpty() }?.let(answerLines::add)
                }
                !inAnswer && optionMatch != null -> {
                    optionMatch.groupValues[2].trim().takeIf { it.isNotEmpty() }?.let(options::add)
                }
                inAnswer -> answerLines.add(line)
                else -> stemLines.add(line)
            }
        }

        val stem = stemLines.joinToString("\n").trim().replace(numberPrefix, "")
        if (stem.isBlank()) return null
        return Question(
            id = id,
            bankId = 0,
            stem = stem,
            options = options,
            answer = answerLines.joinToString("\n").trim(),
            source = source
        )
    }
}
