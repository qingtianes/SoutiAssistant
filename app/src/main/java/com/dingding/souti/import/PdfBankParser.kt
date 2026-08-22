package com.dingding.souti.import

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.io.InputStream

object PdfBankParser {
    fun parse(context: Context, input: InputStream?): Importer.ParseResult {
        input ?: return Importer.ParseResult(emptyList(), error = "无法读取文件")
        return try {
            PDFBoxResourceLoader.init(context.applicationContext)
            input.use { stream ->
                com.tom_roush.pdfbox.pdmodel.PDDocument.load(stream).use { document ->
                    val stripper = com.tom_roush.pdfbox.text.PDFTextStripper().apply {
                        sortByPosition = true
                        suppressDuplicateOverlappingText = true
                    }
                    val text = stripper.getText(document)
                    if (text.isBlank()) {
                        Importer.ParseResult(emptyList(), error = "扫描版 PDF（无文本层），无法提取文字，请使用文字版 PDF")
                    } else {
                        val sourceLen = text.replace(Regex("\\s+"), "").length
                        val lines = text.lineSequence().map { it.trimEnd() }.toList()
                        val result = BankChunker.chunkLines(lines)
                        result.copy(
                            sourceLength = sourceLen,
                            parsedLength = result.chunks.joinToString("").replace(Regex("\\s+"), "").length
                        )
                    }
                }
            }
        } catch (e: Throwable) {
            Importer.ParseResult(
                emptyList(),
                error = "PDF 解析失败：${e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName}"
            )
        }
    }
}
