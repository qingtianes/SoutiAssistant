package com.dingding.souti

import java.io.InputStream

object PdfBankParser {
    fun parse(input: InputStream?): Importer.ParseResult {
        input ?: return Importer.ParseResult(emptyList(), error = "无法读取文件")
        return try {
            val pddoc = com.tom_roush.pdfbox.pdmodel.PDDocument.load(input)
            val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
            val text = stripper.getText(pddoc)
            pddoc.close()
            if (text.isBlank()) {
                Importer.ParseResult(emptyList(), error = "扫描版 PDF（无文本层），无法提取文字，请使用文字版 PDF")
            } else {
                val sourceLen = text.replace(Regex("\\s+"), "").length
                val lines = text.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                val result = BankChunker.chunkLines(lines)
                result.copy(
                    sourceLength = sourceLen,
                    parsedLength = result.chunks.joinToString("").replace(Regex("\\s+"), "").length
                )
            }
        } catch (e: Exception) {
            Importer.ParseResult(emptyList(), error = "PDF 解析失败：${e.message}")
        }
    }
}
