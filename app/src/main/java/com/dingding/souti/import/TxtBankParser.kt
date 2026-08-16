package com.dingding.souti.import

import java.io.InputStream

object TxtBankParser {
    fun parse(input: InputStream?): Importer.ParseResult {
        input ?: return Importer.ParseResult(emptyList(), error = "无法读取文件")
        val text = input.bufferedReader().use { it.readText() }
        // 兼容 BOM
        val cleaned = text.removePrefix("\uFEFF")
        val sourceLen = cleaned.replace(Regex("\\s+"), "").length
        val lines = cleaned.split("\n").map { it.trimEnd() }
        val result = BankChunker.chunkLines(lines)
        return result.copy(
            sourceLength = sourceLen,
            parsedLength = result.chunks.joinToString("").replace(Regex("\\s+"), "").length
        )
    }
}
