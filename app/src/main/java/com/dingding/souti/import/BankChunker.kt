package com.dingding.souti.import

/**
 * 题库切块核心：把文档解析出的纯文本行，按题目边界切成“每块一道题”。
 * 只做行级分类和切块，不负责读取文件。
 */
object BankChunker {
    private val answerStart = Regex("^\\s*(答案|正确答案)\\s*[:：]?\\s*", RegexOption.IGNORE_CASE)

    fun isSectionTitle(t: String): Boolean {
        if (t.isBlank()) return true
        if (Regex("^[一二三四五六七八九十]+、").containsMatchIn(t)) return true
        if (t.contains("作业") && t.length < 10 && !t.contains("（") && !t.contains("(") &&
            !Regex("^\\d+[.、]").containsMatchIn(t)) return true
        if (Regex("^\\d+[.、]\\S{1,6}$").containsMatchIn(t) && !t.contains("（") && !t.contains("(")) return true
        if (Regex("^\\d+/\\d+$").containsMatchIn(t)) return true
        if (Regex("^[一二三四五六七八九十]+、.+共\\d+道").containsMatchIn(t)) return true
        return false
    }

    fun isQuestionStart(t: String): Boolean {
        val m = Regex("^(\\d+)[.、]\\s*(.+)$").find(t) ?: return false
        val stem = m.groupValues[2].trim()
        if (stem.length < 4 && !stem.contains("（") && !stem.contains("(")) return false
        return true
    }

    fun isOptionLine(t: String): Boolean {
        return Regex("^[A-Da-d]\\s*[.、．)）]").containsMatchIn(t)
    }

    private fun isAnswerStart(t: String): Boolean = answerStart.containsMatchIn(t)

    /**
     * TXT 导出通常是“一道题一个空行块”，而简答题答案内部也会使用“1、2、3、”编号。
     * 当大多数空行块都带答案标记时，优先使用空行作为唯一题目边界，避免把答案步骤误切成题目。
     */
    private fun chunkAnswerBlocks(txts: List<String>): Importer.ParseResult? {
        val blocks = mutableListOf<MutableList<String>>()
        var block = mutableListOf<String>()
        fun flush() {
            if (block.any { it.isNotBlank() }) blocks.add(block)
            block = mutableListOf()
        }
        txts.forEach { raw ->
            val line = raw.trim()
            if (line.isBlank()) flush() else block.add(line)
        }
        flush()
        if (blocks.size < 2) return null

        fun stripHeader(lines: List<String>): List<String> {
            var index = 0
            while (index < lines.size) {
                val line = lines[index]
                val metadata = line.startsWith("搜题助手") ||
                    line.startsWith("题目数") ||
                    line == "---" ||
                    (isSectionTitle(line) && !isQuestionStart(line) && !isOptionLine(line) &&
                        !line.contains("（") && !line.contains("("))
                if (!metadata) break
                index++
            }
            return lines.drop(index)
        }

        val cleaned = blocks.map(::stripHeader).filter { lines ->
            lines.isNotEmpty() && (lines.any(::isAnswerStart) ||
                lines.any(::isQuestionStart) || lines.any(::isOptionLine) ||
                lines.any { it.contains("（") || it.contains("(") })
        }
        val answerBlockCount = cleaned.count { lines -> lines.any(::isAnswerStart) }
        val answerBlockThreshold = maxOf(2, (cleaned.size * 0.6).toInt())
        if (cleaned.size < 2 || answerBlockCount < answerBlockThreshold) return null

        val chunks = cleaned.map { it.joinToString("\n") }
        return Importer.ParseResult(
            chunks = chunks,
            hasNumbered = cleaned.any { lines -> lines.any(::isQuestionStart) },
            noNumberWithOption = cleaned.any { lines ->
                lines.any(::isOptionLine) && lines.none(::isQuestionStart)
            },
            blankSeparated = true
        )
    }

    fun chunkLines(txts: List<String>): Importer.ParseResult {
        chunkAnswerBlocks(txts)?.let { return it }

        val chunks = mutableListOf<String>()
        var current = mutableListOf<String>()
        var hasNumbered = txts.any { isQuestionStart(it) }
        var noNumberWithOption = false
        var blankSeparated = false
        var inAnswer = false

        var start = txts.size
        for (i in txts.indices) {
            val t = txts[i]
            if (t.isBlank() || isSectionTitle(t)) continue
            if (isQuestionStart(t) || t.contains("（") || t.contains("(") || isOptionLine(t)) {
                start = i
                break
            }
        }
        if (start == txts.size) start = 0

        fun flush() {
            if (current.isNotEmpty()) {
                if (!(current.size == 1 && !current[0].contains("（") && !current[0].contains("(") && !isOptionLine(current[0]))) {
                    chunks.add(current.joinToString("\n"))
                }
                current = mutableListOf()
            }
            inAnswer = false
        }

        for (i in txts.indices) {
            val t = txts[i].trim()
            if (i < start && !isQuestionStart(t) && !isOptionLine(t)) continue
            if (t.isBlank()) {
                flush()
                blankSeparated = true
                continue
            }
            if (isSectionTitle(t) && !inAnswer) continue
            when {
                isAnswerStart(t) -> {
                    current.add(t)
                    inAnswer = true
                }
                isQuestionStart(t) && !inAnswer -> {
                    flush()
                    current.add(t)
                }
                isOptionLine(t) && current.isNotEmpty() -> {
                    current.add(t)
                    if (current.none { isQuestionStart(it) }) noNumberWithOption = true
                }
                else -> current.add(t)
            }
        }
        flush()

        if (!hasNumbered && !noNumberWithOption && !blankSeparated) {
            return Importer.ParseResult(emptyList(), error = "题库题目无序号且无空行分隔，无法可靠识别题目边界。请为每道题添加序号或用空行分隔后重新导入。")
        }
        if (chunks.isEmpty()) {
            return Importer.ParseResult(emptyList(), error = "未识别到任何题目")
        }
        return Importer.ParseResult(
            chunks = chunks,
            hasNumbered = hasNumbered,
            noNumberWithOption = noNumberWithOption,
            blankSeparated = blankSeparated
        )
    }
}
