package com.dingding.souti

import android.content.Context
import android.net.Uri
import java.io.InputStream
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * 题库导入解析器（Kotlin 版，纯本地离线运行）
 *
 * 支持：
 *  - .txt   纯文本（有序号 / 无序号空行分隔 / 无序号选项对齐）
 *  - .docx  Word（解 zip 提取文本后切块）
 *  - .pdf   文字版 PDF（PDFBox 提取文本后切块；扫描版判定失败）
 *
 * 切块规则（与 tools/convert_bank.py 一致）：
 *  1. 数字.开头 → 新题开始
 *  2. 空行 → 题目边界（无序号时）
 *  3. 选项行（A./B./C./D. 开头）→ 归属当前题
 *  4. 最坏情况（无序号无空行纯文本流）→ 判定失败并提示
 */
object Importer {

    data class ParseResult(
        val chunks: List<String>,          // 每块 = 一道题整块文本
        val hasNumbered: Boolean = false,
        val noNumberWithOption: Boolean = false,
        val blankSeparated: Boolean = false,
        val error: String? = null          // 非 null = 解析失败
    )

    // ============ 入口：按扩展名分发 ============

    fun parse(context: Context, uri: Uri, fileName: String): ParseResult {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "txt" -> parseTxt(context.contentResolver.openInputStream(uri))
            "docx" -> parseDocx(context.contentResolver.openInputStream(uri))
            "pdf" -> parsePdf(context.contentResolver.openInputStream(uri))
            "xls", "xlsx" -> ParseResult(emptyList(), error = "Excel 格式请在电脑端用转换工具转成 txt 后导入（支持 .txt/.docx/.pdf）")
            else -> ParseResult(emptyList(), error = "不支持的文件格式：.$ext（支持 .txt/.docx/.pdf）")
        }
    }

    // ============ 各格式解析 ============

    fun parseTxt(input: InputStream?): ParseResult {
        input ?: return ParseResult(emptyList(), error = "无法读取文件")
        val text = input.bufferedReader().use { it.readText() }
        // 兼容 BOM
        val cleaned = text.removePrefix("\uFEFF")
        val lines = cleaned.split("\n").map { it.trimEnd() }
        return chunkLines(lines)
    }

    /** docx：解 zip 提取段落文本 */
    fun parseDocx(input: InputStream?): ParseResult {
        input ?: return ParseResult(emptyList(), error = "无法读取文件")
        return try {
            val paragraphs = extractDocxParagraphs(input)
            if (paragraphs.isEmpty()) {
                ParseResult(emptyList(), error = "docx 未提取到文本（可能已损坏或加密）")
            } else {
                chunkLines(paragraphs)
            }
        } catch (e: Exception) {
            ParseResult(emptyList(), error = "docx 解析失败：${e.message}")
        }
    }

    /** pdf：PDFBox 提取文本（仅文字版） */
    fun parsePdf(input: InputStream?): ParseResult {
        input ?: return ParseResult(emptyList(), error = "无法读取文件")
        return try {
            val pddoc = com.tom_roush.pdfbox.pdmodel.PDDocument.load(input)
            val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
            val text = stripper.getText(pddoc)
            pddoc.close()
            if (text.isBlank()) {
                ParseResult(emptyList(), error = "扫描版 PDF（无文本层），无法提取文字，请使用文字版 PDF")
            } else {
                val lines = text.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                chunkLines(lines)
            }
        } catch (e: Exception) {
            ParseResult(emptyList(), error = "PDF 解析失败：${e.message}")
        }
    }

    // ============ docx zip 提取 ============

    private fun extractDocxParagraphs(input: InputStream): List<String> {
        val paragraphs = mutableListOf<String>()
        val zis = ZipInputStream(input)
        var entry = zis.nextEntry
        while (entry != null) {
            if (entry.name == "word/document.xml") {
                // 读取 XML
                val xml = zis.readBytes().toString(Charsets.UTF_8)
                paragraphs.addAll(parseWordXml(xml))
                break
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
        zis.close()
        return paragraphs.map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** 从 document.xml 提取段落文本（每个 <w:p> 是一个段落） */
    private fun parseWordXml(xml: String): List<String> {
        val paragraphs = mutableListOf<String>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(xml.reader())
            var currentParagraph = StringBuilder()
            var inText = false
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name
                        if (name == "p") {
                            currentParagraph = StringBuilder()
                        } else if (name == "t" || name == "instrText") {
                            inText = true
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inText) currentParagraph.append(parser.text)
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "t", "instrText" -> inText = false
                            "p" -> {
                                val s = currentParagraph.toString().trim()
                                if (s.isNotEmpty()) paragraphs.add(s)
                            }
                        }
                    }
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            // XML 解析失败时退回简单正则提取
            return extractTextSimple(xml)
        }
        return paragraphs
    }

    /** 兜底：正则提取 w:t 文本（按段落重组） */
    private fun extractTextSimple(xml: String): List<String> {
        val paragraphs = mutableListOf<String>()
        val pRegex = Regex("<w:p[ >].*?</w:p>", RegexOption.DOT_MATCHES_ALL)
        val tRegex = Regex("<w:t[^>]*>(.*?)</w:t>", RegexOption.DOT_MATCHES_ALL)
        for (pm in pRegex.findAll(xml)) {
            val sb = StringBuilder()
            for (tm in tRegex.findAll(pm.value)) {
                sb.append(tm.groupValues[1])
            }
            val s = sb.toString().trim()
            if (s.isNotEmpty()) paragraphs.add(s)
        }
        return paragraphs
    }

    // ============ 切块核心（与 Python 版一致） ============

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

    fun chunkLines(txts: List<String>): ParseResult {
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
                noNumberWithOption = true
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
            return ParseResult(emptyList(), error = "题库题目无序号且无空行分隔，无法可靠识别题目边界。请为每道题添加序号或用空行分隔后重新导入。")
        }
        if (chunks.isEmpty()) {
            return ParseResult(emptyList(), error = "未识别到任何题目")
        }
        return ParseResult(
            chunks = chunks,
            hasNumbered = hasNumbered,
            noNumberWithOption = noNumberWithOption,
            blankSeparated = blankSeparated
        )
    }
}
