package com.dingding.souti.import

import android.content.Context
import android.net.Uri

/**
 * 题库导入入口：按文件类型分发给对应的解析器。
 * 各格式解析器已拆分到 TxtBankParser / DocxBankParser / PdfBankParser / XlsBankParser。
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

    fun detectFileFormat(mimeType: String?, fileName: String): FileFormat =
        FileFormatDetector.detect(mimeType, fileName)

    fun parse(context: Context, uri: Uri, fileName: String): ParseResult {
        val resolver = context.contentResolver
        return when (detectFileFormat(resolver.getType(uri), fileName)) {
            FileFormat.PDF -> PdfBankParser.parse(resolver.openInputStream(uri))
            FileFormat.DOCX -> DocxBankParser.parse(resolver.openInputStream(uri))
            FileFormat.XLS -> XlsBankParser.parse(resolver.openInputStream(uri))
            FileFormat.XLSX_UNSUPPORTED -> ParseResult(
                emptyList(),
                error = "暂不支持 .xlsx，请先在表格软件中另存为 .xls 后再导入"
            )
            FileFormat.TXT -> TxtBankParser.parse(resolver.openInputStream(uri))
            FileFormat.UNSUPPORTED -> ParseResult(
                emptyList(),
                error = "不支持的文件格式：$fileName（支持 .txt/.docx/.pdf/.xls）"
            )
        }
    }

    // ============ 切块核心（已拆到 BankChunker，保留同名入口兼容旧调用） ============

    fun isSectionTitle(t: String): Boolean = BankChunker.isSectionTitle(t)

    fun isQuestionStart(t: String): Boolean = BankChunker.isQuestionStart(t)

    fun isOptionLine(t: String): Boolean = BankChunker.isOptionLine(t)

    fun chunkLines(txts: List<String>): ParseResult = BankChunker.chunkLines(txts)
}
