package com.dingding.souti.import


/**
 * 题库切块核心：把文档解析出的纯文本行，按题目边界切成“每块一道题”。
 * 只做行级分类和切块，不负责读取文件。
 */
object BankChunker {

    fun isSectionTitle(t: String): Boolean {
        if (t.isBlank()) return true
        if (Regex("^[一二三四五六七八九十]+、.*(判断题|单选题|多选题|选择题|简答题|计算题|理论知识|专业知识|基本要求|相关知识)").containsMatchIn(t)) return true
        if (t.contains("作业") && t.length < 10 && !t.contains("（") && !t.contains("(") &&
            !Regex("^\\d+[.、]").containsMatchIn(t)) return true
        if (Regex("^\\d+[.、]\\S{1,6}$").containsMatchIn(t) && !t.contains("（") && !t.contains("(")) return true
        if (Regex("^\\d+/\\d+$").containsMatchIn(t)) return true
        if (Regex("^[一二三四五六七八九十]+、.+共\\d+道").containsMatchIn(t)) return true
        return false
    }

    fun isQuestionStart(t: String): Boolean {
        val m = QuestionSyntax.questionStart.find(t) ?: return false
        val stem = m.groupValues[2].trim()
        if (stem.length < 4 && !stem.contains("（") && !stem.contains("(")) return false
        return true
    }

    fun isOptionLine(t: String): Boolean {
        return QuestionSyntax.optionLine.containsMatchIn(t)
    }

    private fun isAnswerStart(t: String): Boolean = QuestionSyntax.isAnswerStart(t)

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

    /**
     * 部分文字版 PDF 在题干、选项和答案之间也会产生排版空行，不能把空行当作题目边界。
     * 当文档反复出现“题号 +（资源编号）”这一强特征时，以它作为唯一可靠边界。
     */
    private fun chunkStrongNumberedQuestions(lines: List<String>): Importer.ParseResult? {
        val starts = lines.indices.filter { index ->
            QuestionSyntax.strongNumberedQuestion.containsMatchIn(lines[index].trim())
        }
        if (starts.size < 2) return null

        val chunks = starts.mapIndexedNotNull { position, start ->
            val end = starts.getOrElse(position + 1) { lines.size }
            val content = lines.subList(start, end)
                .map(String::trim)
                .filter { line ->
                    line.isNotBlank() &&
                        !Regex("^\\d+\\s*/\\s*\\d+$").matches(line) &&
                        !isSectionTitle(line)
                }
            content.takeIf { it.isNotEmpty() }?.joinToString("\n")
        }
        if (chunks.size < 2) return null

        return Importer.ParseResult(
            chunks = chunks,
            hasNumbered = true,
            noNumberWithOption = false,
            blankSeparated = false
        )
    }

    fun chunkLines(txts: List<String>): Importer.ParseResult {
        val lines = txts
        chunkStrongNumberedQuestions(lines)?.let { return it }
        chunkAnswerBlocks(lines)?.let { return it }

        val chunks = mutableListOf<String>()
        var current = mutableListOf<String>()
        var hasNumbered = lines.any { isQuestionStart(it) }
        var noNumberWithOption = false
        var blankSeparated = false
        var inAnswer = false

        var start = lines.size
        for (i in lines.indices) {
            val t = lines[i]
            if (t.isBlank() || isSectionTitle(t)) continue
            if (isQuestionStart(t) || t.contains("（") || t.contains("(") || isOptionLine(t)) {
                start = i
                break
            }
        }
        if (start == lines.size) start = 0

        fun looksLikeQuestionBoundary(index: Int): Boolean {
            if (!isQuestionStart(lines[index])) return false
            val currentLine = lines[index].trim()
            if (QuestionSyntax.strongNumberedQuestion.containsMatchIn(currentLine)) return true
            if (currentLine.endsWith("?") || currentLine.endsWith("？")) return true

            var inspectedContentLines = 0
            for (nextIndex in (index + 1) until lines.size) {
                val next = lines[nextIndex].trim()
                if (next.isBlank()) return false
                if (isOptionLine(next) || isAnswerStart(next)) return true
                if (isQuestionStart(next)) return false
                if (!isSectionTitle(next)) {
                    inspectedContentLines++
                    if (inspectedContentLines >= 4) return false
                }
            }
            return false
        }

        fun flush() {
            if (current.isNotEmpty()) {
                if (!(current.size == 1 && !current[0].contains("（") && !current[0].contains("(") && !isOptionLine(current[0]))) {
                    chunks.add(current.joinToString("\n"))
                }
                current = mutableListOf()
            }
            inAnswer = false
        }

        for (i in lines.indices) {
            val t = lines[i].trim()
            if (i < start && !isQuestionStart(t) && !isOptionLine(t)) continue
            if (t.isBlank()) {
                flush()
                blankSeparated = true
                continue
            }
            if (isSectionTitle(t) && !inAnswer) continue
            if (inAnswer && Regex("^\\d+/\\d+$").matches(t)) continue
            when {
                isAnswerStart(t) -> {
                    current.add(t)
                    inAnswer = true
                }
                isQuestionStart(t) && (!inAnswer || looksLikeQuestionBoundary(i)) -> {
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
