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
        val sourceLength: Int = 0,         // ★ 源文件内容字数（验证覆盖率用）
        val parsedLength: Int = 0,         // ★ 解析后题目总字数
        val error: String? = null          // 非 null = 解析失败
    ) {
        /** ★ 覆盖率：解析字数 / 源字数 × 100 */
        fun coverage(): Int =
            if (sourceLength > 0) ((parsedLength.toDouble() / sourceLength * 100).toInt())
            else 100

        /** ★ 覆盖率是否低于阈值（默认 60，pdf 用 55） */
        fun lowCoverage(threshold: Int = 60): Boolean = coverage() < threshold
    }

    // ============ 入口：按扩展名分发 ============

    enum class FileFormat { TXT, DOCX, PDF, XLS, XLSX_UNSUPPORTED, UNSUPPORTED }

    /**
     * 根据文件名和 MIME 类型确定解析器。
     * 扩展名优先于 application/octet-stream 等通用 MIME，避免 Excel 被误当成纯文本。
     */
    fun detectFileFormat(mimeType: String?, fileName: String): FileFormat {
        val mime = mimeType.orEmpty().lowercase()
        val fn = fileName.lowercase()

        return when {
            fn.endsWith(".pdf") -> FileFormat.PDF
            fn.endsWith(".docx") -> FileFormat.DOCX
            fn.endsWith(".xlsx") -> FileFormat.XLSX_UNSUPPORTED
            fn.endsWith(".xls") -> FileFormat.XLS
            fn.endsWith(".txt") -> FileFormat.TXT
            mime == "application/pdf" || mime.endsWith("/pdf") -> FileFormat.PDF
            mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                || mime.endsWith("/openxmlformats-officedocument.wordprocessingml.document") -> FileFormat.DOCX
            mime == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> FileFormat.XLSX_UNSUPPORTED
            mime == "application/vnd.ms-excel" -> FileFormat.XLS
            mime.startsWith("text/") || mime == "application/octet-stream" || mime.isEmpty() -> FileFormat.TXT
            else -> FileFormat.UNSUPPORTED
        }
    }

    fun parse(context: Context, uri: Uri, fileName: String): ParseResult {
        val resolver = context.contentResolver
        return when (detectFileFormat(resolver.getType(uri), fileName)) {
            FileFormat.PDF -> parsePdf(resolver.openInputStream(uri))
            FileFormat.DOCX -> parseDocx(resolver.openInputStream(uri))
            FileFormat.XLS -> parseXls(resolver.openInputStream(uri))
            FileFormat.XLSX_UNSUPPORTED -> ParseResult(
                emptyList(),
                error = "暂不支持 .xlsx，请先在表格软件中另存为 .xls 后再导入"
            )
            FileFormat.TXT -> parseTxt(resolver.openInputStream(uri))
            FileFormat.UNSUPPORTED -> ParseResult(
                emptyList(),
                error = "不支持的文件格式：$fileName（支持 .txt/.docx/.pdf/.xls）"
            )
        }
    }

    // ============ 各格式解析 ============

    fun parseTxt(input: InputStream?): ParseResult {
        input ?: return ParseResult(emptyList(), error = "无法读取文件")
        val text = input.bufferedReader().use { it.readText() }
        // 兼容 BOM
        val cleaned = text.removePrefix("\uFEFF")
        val sourceLen = cleaned.replace(Regex("\\s+"), "").length
        val lines = cleaned.split("\n").map { it.trimEnd() }
        val result = chunkLines(lines)
        return result.copy(
            sourceLength = sourceLen,
            parsedLength = result.chunks.joinToString("").replace(Regex("\\s+"), "").length
        )
    }

    /** docx：解 zip 提取段落文本 */
    fun parseDocx(input: InputStream?): ParseResult {
        input ?: return ParseResult(emptyList(), error = "无法读取文件")
        return try {
            val paragraphs = extractDocxParagraphs(input)
            if (paragraphs.isEmpty()) {
                ParseResult(emptyList(), error = "docx 未提取到文本（可能已损坏或加密）")
            } else {
                val sourceLen = paragraphs.joinToString("").replace(Regex("\\s+"), "").length
                val result = chunkLines(paragraphs)
                result.copy(
                    sourceLength = sourceLen,
                    parsedLength = result.chunks.joinToString("").replace(Regex("\\s+"), "").length
                )
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
                val sourceLen = text.replace(Regex("\\s+"), "").length
                val lines = text.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                val result = chunkLines(lines)
                result.copy(
                    sourceLength = sourceLen,
                    parsedLength = result.chunks.joinToString("").replace(Regex("\\s+"), "").length
                )
            }
        } catch (e: Exception) {
            ParseResult(emptyList(), error = "PDF 解析失败：${e.message}")
        }
    }

    /** xls：jxl 解析（列式：题型/题干/可选项(分号)/答案） */
    fun parseXls(input: InputStream?): ParseResult {
        input ?: return ParseResult(emptyList(), error = "无法读取文件")
        return try {
            val wb = jxl.Workbook.getWorkbook(input)
            val chunks = mutableListOf<String>()
            val seen = HashSet<String>()
            var sourceChars = 0  // ★ 跨所有 sheet 累加（声明在 sheet 循环外）

            for (si in 0 until wb.numberOfSheets) {
                val sh = wb.getSheet(si)
                // 找表头行（含"题型"或"题目内容"）
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

                // 找列索引
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

                    // ★ 源字数：题干 + 选项 + 答案 的去空白长度
                    var cellText = stem
                    if (optCol != null) cellText += sh.getCell(optCol, r)?.contents ?: ""
                    if (ansCol != null) cellText += sh.getCell(ansCol, r)?.contents ?: ""
                    sourceChars += cellText.replace(Regex("\\s+"), "").length

                    // 题型
                    val type = typeCol?.let { sh.getCell(it, r)?.contents?.trim() } ?: ""

                    // 选项（分号/制表符分隔）
                    val options = mutableListOf<String>()
                    if (optCol != null) {
                        val raw = sh.getCell(optCol, r)?.contents ?: ""
                        for (part in raw.split(Regex("[;；\t]+"))) {
                            val p = part.trim()
                            if (p.isNotEmpty()) options.add(p)
                        }
                    }

                    // 答案
                    val ans = ansCol?.let { sh.getCell(it, r)?.contents?.trim() } ?: ""

                    // 组装题干块
                    val lines = mutableListOf(stem)
                    for ((i, opt) in options.withIndex()) {
                        val letter = ('A' + i).toChar().toString()
                        // 去掉选项自带字母前缀（避免 "A. A. 一级用火"）
                        val optClean = Regex("^[A-Za-z][.、．)）]\\s*").replace(opt, "")
                        lines.add("$letter. $optClean")
                    }
                    // 判断题答案转换（×/√ → 错/对）
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
                ParseResult(emptyList(), error = "xls 文件无有效题目（可能为空或表头格式不对）")
            } else {
                // xls 是三列模式 → hasNumbered=true（题目内有序号）
                // ★ 源字数 = 所有单元格内容总和（不含表头）
                ParseResult(
                    chunks = chunks,
                    hasNumbered = true,
                    noNumberWithOption = false,
                    blankSeparated = false,
                    sourceLength = sourceChars,
                    parsedLength = chunks.joinToString("").replace(Regex("\\s+"), "").length
                )
            }
        } catch (e: Exception) {
            ParseResult(emptyList(), error = "xls 解析失败：${e.message}")
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

    // ============ 切块核心（已拆到 BankChunker，保留同名入口兼容旧调用） ============

    fun isSectionTitle(t: String): Boolean = BankChunker.isSectionTitle(t)

    fun isQuestionStart(t: String): Boolean = BankChunker.isQuestionStart(t)

    fun isOptionLine(t: String): Boolean = BankChunker.isOptionLine(t)

    fun chunkLines(txts: List<String>): ParseResult = BankChunker.chunkLines(txts)
}
