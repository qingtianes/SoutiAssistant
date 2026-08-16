package com.dingding.souti.import

import java.io.InputStream

object XlsBankParser {
    fun parse(input: InputStream?): Importer.ParseResult {
        input ?: return Importer.ParseResult(emptyList(), error = "无法读取文件")
        return try {
            val wb = jxl.Workbook.getWorkbook(input)
            val chunks = mutableListOf<String>()
            val seen = HashSet<String>()
            var sourceChars = 0

            for (si in 0 until wb.numberOfSheets) {
                val sh = wb.getSheet(si)
                var headerRow = -1
                for (r in 0 until minOf(10, sh.rows)) {
                    var found = false
                    for (c in 0 until minOf(sh.columns, 14)) {
                        val v = sh.getCell(c, r)?.contents?.trim() ?: ""
                        if (v == "题型" || v == "题目内容" || v == "题干") {
                            found = true; break
                        }
                    }
                    if (found) { headerRow = r; break }
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
                    val key = stem.take(40)
                    if (seen.contains(key)) continue
                    seen.add(key)

                    var cellText = stem
                    if (optCol != null) cellText += sh.getCell(optCol, r)?.contents ?: ""
                    if (ansCol != null) cellText += sh.getCell(ansCol, r)?.contents ?: ""
                    sourceChars += cellText.replace(Regex("\\s+"), "").length

                    val type = typeCol?.let { sh.getCell(it, r)?.contents?.trim() } ?: ""

                    val options = mutableListOf<String>()
                    if (optCol != null) {
                        val raw = sh.getCell(optCol, r)?.contents ?: ""
                        for (part in raw.split(Regex("[;；\t]+"))) {
                            val p = part.trim()
                            if (p.isNotEmpty()) options.add(p)
                        }
                    }

                    val ans = ansCol?.let { sh.getCell(it, r)?.contents?.trim() } ?: ""

                    val lines = mutableListOf(stem)
                    for ((i, opt) in options.withIndex()) {
                        val letter = ('A' + i).toChar().toString()
                        val optClean = Regex("^[A-Za-z][.、．)）]\\s*").replace(opt, "")
                        lines.add("$letter. $optClean")
                    }
                    val finalAns = if (type.contains("判断")) {
                        when (ans) {
                            "对", "正确", "√", "T", "true" -> "对"
                            "错", "错误", "×", "F", "false" -> "错"
                            else -> ans
                        }
                    } else ans
                    if (finalAns.isNotEmpty() && finalAns != "无") {
                        lines.add("答案:$finalAns")
                    }
                    chunks.add(lines.joinToString("\n"))
                }
            }
            wb.close()

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
        } catch (e: Exception) {
            Importer.ParseResult(emptyList(), error = "xls 解析失败：${e.message}")
        }
    }
}
