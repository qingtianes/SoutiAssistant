package com.dingding.souti

/**
 * 题库切块核心：把文档解析出的纯文本行，按题目边界切成“每块一道题”。
 * 只做行级分类和切块，不负责读取文件。
 */
object BankChunker {

    fun isSectionTitle(t: String): Boolean {
        if (t.isBlank()) return true
        // 一、单选题
        if (Regex("^[一二三四五六七八九十]+、").containsMatchIn(t)) return true
        // 章节名（含"作业"且短且无括号）
        if (t.contains("作业") && t.length < 10 && !t.contains("（") && !t.contains("(") &&
            !Regex("^\\d+[.、]").containsMatchIn(t)) return true
        // 1.动火作业 短标题
        if (Regex("^\\d+[.、]\\S{1,6}$").containsMatchIn(t) && !t.contains("（") && !t.contains("(")) return true
        // 页脚 1/105
        if (Regex("^\\d+/\\d+$").containsMatchIn(t)) return true
        // 一、判断题，共206道。
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

    fun chunkLines(txts: List<String>): Importer.ParseResult {
        val chunks = mutableListOf<String>()
        var current = mutableListOf<String>()
        var hasNumbered = false
        var noNumberWithOption = false
        var blankSeparated = false

        // 预扫描：是否有序号题
        hasNumbered = txts.any { isQuestionStart(it) }

        // 跳过文档头部说明，找到第一个有题目特征的行
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
                // 过滤孤立标题（单行且无括号且非选项）
                if (!(current.size == 1 && !current[0].contains("（") && !current[0].contains("(") && !isOptionLine(current[0]))) {
                    chunks.add(current.joinToString("\n"))
                }
                current = mutableListOf()
            }
        }

        for (i in txts.indices) {
            val t = txts[i]
            if (i < start && !isQuestionStart(t) && !isOptionLine(t)) continue  // 跳过头部说明
            if (t.isBlank()) {
                // 空行 → 边界
                flush()
                blankSeparated = true
                continue
            }
            if (isSectionTitle(t)) continue
            if (isQuestionStart(t)) {
                flush()
                current.add(t)
            } else if (isOptionLine(t) && current.isNotEmpty()) {
                current.add(t)
                if (current.none { isQuestionStart(it) }) noNumberWithOption = true
            } else {
                if (current.isEmpty()) {
                    current.add(t)
                } else {
                    current.add(t)
                }
            }
        }
        flush()

        // 最坏情况甄别：无序号 + 无空行 + 无选项对齐
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
