package com.dingding.souti.import

import android.content.Context
import android.net.Uri

/**
 * 题库导入入口：按文件类型分发给对应的解析器。
 * 各格式解析器已拆分到 TxtBankParser / DocxBankParser / PdfBankParser / XlsBankParser。
 */
object Importer {
    data class ParseResult(
        val chunks: List<String>,
        val hasNumbered: Boolean = false,
        val noNumberWithOption: Boolean = false,
        val blankSeparated: Boolean = false,
        val sourceLength: Int = 0,
        val parsedLength: Int = 0,
        val error: String? = null
    ) {
        fun coverage(): Int =
            if (sourceLength > 0) ((parsedLength.toDouble() / sourceLength * 100).toInt()) else 100

        fun lowCoverage(threshold: Int = 60): Boolean = coverage() < threshold
    }

    fun detectFileFormat(mimeType: String?, fileName: String): FileFormat =
        FileFormatDetector.detect(mimeType, fileName)

    fun parse(context: Context, uri: Uri, fileName: String): ParseResult {
        val resolver = context.contentResolver
        return when (detectFileFormat(resolver.getType(uri), fileName)) {
            FileFormat.PDF -> PdfBankParser.parse(context, resolver.openInputStream(uri))
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

    fun isSectionTitle(t: String): Boolean = BankChunker.isSectionTitle(t)
    fun isQuestionStart(t: String): Boolean = BankChunker.isQuestionStart(t)
    fun isOptionLine(t: String): Boolean = BankChunker.isOptionLine(t)
    fun chunkLines(txts: List<String>): ParseResult = BankChunker.chunkLines(txts)
}
