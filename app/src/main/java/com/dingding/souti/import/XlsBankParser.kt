package com.dingding.souti.import

import java.io.InputStream

object XlsBankParser {
    private val optionLabel = Regex("(?i)(?:^|\\s)([A-H])\\s*[.、．:：)）]\\s*")

    /** 兼容分号、Tab、换行，以及同一单元格内连续的 A./B./C. 选项。 */
    fun splitOptions(raw: String): List<String> {
        val normalized = raw.trim()
        if (normalized.isBlank()) return emptyList()

        val explicit = normalized.split(Regex("[;；\\t\\r\\n]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (explicit.size > 1) return explicit

        val matches = optionLabel.findAll(normalized).toList()
        if (matches.size >= 2) {
            return matches.mapIndexedNotNull { index, match ->
                val start = match.range.first
                val end = if (index + 1 < matches.size) matches[index + 1].range.first else normalized.length
                normalized.substring(start, end).trim().takeIf { it.isNotEmpty() }
            }
        }
        return listOf(normalized)
    }

    fun parse(input: InputStream?): Importer.ParseResult {
        input ?: return Importer.ParseResult(emptyList(), error = "无法读取文件")
        return try {
            val wb = jxl.Workbook.getWorkbook(input)
            try {
                val chunks = mutableListOf<String>()
                var sourceChars = 0

                for (si in 0 until wb.numberOfSheets) {
                    val sh = wb.getSheet(si)
                    var headerRow = -1
                    for (r in 0 until minOf(20, sh.rows)) {
                        val rowValues = (0 until sh.columns).map { c ->
                            sh.getCell(c, r)?.contents?.trim().orEmpty()
                        }
                        if (rowValues.any { it in STEM_HEADERS || it in TYPE_HEADERS }) {
                            headerRow = r
                            break
                        }
                    }
                    if (headerRow < 0) continue

                    val cols = HashMap<String, Int>()
                    for (c in 0 until sh.columns) {
                        val value = sh.getCell(c, headerRow)?.contents?.trim().orEmpty()
                        if (value.isNotEmpty()) cols[value] = c
                    }
                    fun findColumn(names: Set<String>): Int? = names.firstNotNullOfOrNull { cols[it] }

                    val typeCol = findColumn(TYPE_HEADERS)
                    val stemCol = findColumn(STEM_HEADERS) ?: continue
                    val optCol = findColumn(OPTION_HEADERS)
                    val ansCol = findColumn(ANSWER_HEADERS)

                    for (r in (headerRow + 1) until sh.rows) {
                        val stem = sh.getCell(stemCol, r)?.contents?.trim().orEmpty()
                        if (stem.isEmpty() || stem in STEM_HEADERS) continue

                        val rawOptions = optCol?.let { sh.getCell(it, r)?.contents.orEmpty() }.orEmpty()
                        val options = splitOptions(rawOptions)
                        val ans = ansCol?.let { sh.getCell(it, r)?.contents?.trim() }.orEmpty()
                        val type = typeCol?.let { sh.getCell(it, r)?.contents?.trim() }.orEmpty()

                        val lines = mutableListOf(stem)
                        options.forEachIndexed { i, option ->
                            val labeled = Regex("(?i)^([A-H])\\s*[.、．:：)）]\\s*(.*)$").find(option)
                            val letter = labeled?.groupValues?.get(1)?.uppercase()
                                ?: ('A'.code + i).toChar().toString()
                            val clean = labeled?.groupValues?.get(2)?.trim() ?: option.trim()
                            if (clean.isNotEmpty()) lines.add("$letter. $clean")
                        }
                        val finalAnswer = normalizeAnswer(type, ans)
                        if (finalAnswer.isNotEmpty() && finalAnswer != "无") lines.add("答案:$finalAnswer")

                        sourceChars += (stem + rawOptions + ans).replace(Regex("\\s+"), "").length
                        chunks.add(lines.joinToString("\n"))
                    }
                }

                if (chunks.isEmpty()) {
                    Importer.ParseResult(emptyList(), error = "xls 文件无有效题目（未找到题干列，或表格为空）")
                } else {
                    Importer.ParseResult(
                        chunks = chunks,
                        hasNumbered = true,
                        sourceLength = sourceChars,
                        parsedLength = chunks.joinToString("").replace(Regex("\\s+"), "").length
                    )
                }
            } finally {
                wb.close()
            }
        } catch (e: Exception) {
            Importer.ParseResult(emptyList(), error = "xls 解析失败：${e.message ?: e.javaClass.simpleName}")
        } finally {
            try { input.close() } catch (_: Exception) { }
        }
    }

    private fun normalizeAnswer(type: String, answer: String): String {
        if (!type.contains("判断")) return answer
        return when (answer.trim().lowercase()) {
            "对", "正确", "√", "t", "true", "是" -> "对"
            "错", "错误", "×", "x", "f", "false", "否" -> "错"
            else -> answer
        }
    }

    private val TYPE_HEADERS = setOf("题型", "题目类型", "类型")
    private val STEM_HEADERS = setOf("题目内容", "题干", "题目", "问题")
    private val OPTION_HEADERS = setOf("可选项", "选项", "备选答案")
    private val ANSWER_HEADERS = setOf("答案", "正确答案", "参考答案", "标准答案")
}
