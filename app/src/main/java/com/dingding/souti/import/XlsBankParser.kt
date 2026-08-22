package com.dingding.souti.import

import java.io.InputStream

object XlsBankParser {
    private val optionLabel = Regex("^[A-Za-z]\\s*[.、．)）]\\s*.*$")

    /** 兼容常见 Excel 导出：分号、中文分号、Tab，以及独立换行选项。 */
    fun splitOptions(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        val explicit = raw.split(Regex("[;；\\t]+")).flatMap { segment ->
            val lines = segment.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.size > 1 && lines.all(optionLabel::matches)) lines else listOf(segment.trim())
        }.map { it.trim() }.filter { it.isNotEmpty() }
        if (explicit.size > 1) return explicit

        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return if (lines.size > 1) lines else explicit
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
                    for (r in 0 until minOf(10, sh.rows)) {
                        var found = false
                        for (c in 0 until minOf(sh.columns, 14)) {
                            val v = sh.getCell(c, r)?.contents?.trim() ?: ""
                            if (v == "题型" || v == "题目内容" || v == "题干") {
                                found = true
                                break
                            }
                        }
                        if (found) {
                            headerRow = r
                            break
                        }
                    }
                    if (headerRow < 0) headerRow = 2

                    val cols = HashMap<String, Int>()
                    for (c in 0 until minOf(sh.columns, 14)) {
                        val v = sh.getCell(c, headerRow)?.contents?.trim() ?: ""
                        if (v.isNotEmpty()) cols[v] = c
                    }
                    val typeCol = cols["题型"]
                    val stemCol = cols["题目内容"] ?: cols["题干"]
                    val optCol = cols["可选项"]
                    val ansCol = cols["答案"] ?: cols["正确答案"]

                    for (r in (headerRow + 1) until sh.rows) {
                        val stem = stemCol?.let { sh.getCell(it, r)?.contents?.trim() } ?: ""
                        if (stem.isEmpty() || stem == "题目内容" || stem == "题干") continue

                        val rawOptions = optCol?.let { sh.getCell(it, r)?.contents ?: "" } ?: ""
                        val options = splitOptions(rawOptions)
                        val ans = ansCol?.let { sh.getCell(it, r)?.contents?.trim() } ?: ""
                        val type = typeCol?.let { sh.getCell(it, r)?.contents?.trim() } ?: ""

                        val lines = mutableListOf(stem)
                        options.forEachIndexed { i, opt ->
                            val letter = ('A'.code + i).toChar().toString()
                            val optClean = Regex("^[A-Za-z]\\s*[.、．)）]\\s*").replace(opt, "")
                            lines.add("$letter. ${optClean.trim()}")
                        }
                        val finalAns = if (type.contains("判断")) {
                            when (ans) {
                                "对", "正确", "√", "T", "true" -> "对"
                                "错", "错误", "×", "F", "false" -> "错"
                                else -> ans
                            }
                        } else ans
                        if (finalAns.isNotEmpty() && finalAns != "无") lines.add("答案:$finalAns")

                        val cellText = stem + rawOptions + ans
                        sourceChars += cellText.replace(Regex("\\s+"), "").length
                        chunks.add(lines.joinToString("\n"))
                    }
                }

                if (chunks.isEmpty()) {
                    Importer.ParseResult(emptyList(), error = "xls 文件无有效题目（可能为空或表头格式不对）")
                } else {
                    Importer.ParseResult(
                        chunks = chunks,
                        hasNumbered = true,
                        noNumberWithOption = false,
                        blankSeparated = false,
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
            try {
                input.close()
            } catch (_: Exception) {
                // 输入流由调用方提供，关闭失败不应覆盖解析结果。
            }
        }
    }
}
