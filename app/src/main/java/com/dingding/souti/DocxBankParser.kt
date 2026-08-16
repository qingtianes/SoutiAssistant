package com.dingding.souti

import java.io.InputStream
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

object DocxBankParser {
    fun parse(input: InputStream?): Importer.ParseResult {
        input ?: return Importer.ParseResult(emptyList(), error = "无法读取文件")
        return try {
            val paragraphs = extractDocxParagraphs(input)
            if (paragraphs.isEmpty()) {
                Importer.ParseResult(emptyList(), error = "docx 未提取到文本（可能已损坏或加密）")
            } else {
                val sourceLen = paragraphs.joinToString("").replace(Regex("\\s+"), "").length
                val result = BankChunker.chunkLines(paragraphs)
                result.copy(
                    sourceLength = sourceLen,
                    parsedLength = result.chunks.joinToString("").replace(Regex("\\s+"), "").length
                )
            }
        } catch (e: Exception) {
            Importer.ParseResult(emptyList(), error = "docx 解析失败：${e.message}")
        }
    }

    private fun extractDocxParagraphs(input: InputStream): List<String> {
        val paragraphs = mutableListOf<String>()
        val zis = ZipInputStream(input)
        var entry = zis.nextEntry
        while (entry != null) {
            if (entry.name == "word/document.xml") {
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
            return extractTextSimple(xml)
        }
        return paragraphs
    }

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
}
