package com.dingding.souti.import

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocxBankParserTest {

    @Test
    fun `docx parser joins multiple runs in one paragraph`() {
        val docx = createDocx(
            paragraph("1. 第一", "道题干"),
            paragraph("A. 甲"),
            paragraph("B. 乙"),
            paragraph("答案：A")
        )

        val result = DocxBankParser.parse(ByteArrayInputStream(docx))

        assertNull(result.error)
        assertEquals(1, result.chunks.size)
        assertTrue(result.chunks.single().contains("1. 第一道题干"))
    }

    @Test
    fun `docx parser preserves empty paragraph as question boundary`() {
        val docx = createDocx(
            paragraph("第一道简答题题干是什么？"),
            paragraph("答案：第一题答案"),
            emptyParagraph(),
            paragraph("第二道简答题题干是什么？"),
            paragraph("答案：第二题答案")
        )

        val result = DocxBankParser.parse(ByteArrayInputStream(docx))

        assertNull(result.error)
        assertEquals(2, result.chunks.size)
        assertTrue(result.blankSeparated)
        assertTrue(result.chunks[0].contains("第一道简答题"))
        assertTrue(result.chunks[1].contains("第二道简答题"))
    }

    @Test
    fun `docx parser splits numbered question immediately after answer`() {
        val docx = createDocx(
            paragraph("1. 第一道选择题题干"),
            paragraph("A. 甲"),
            paragraph("B. 乙"),
            paragraph("答案：A"),
            paragraph("2. 第二道选择题题干"),
            paragraph("A. 丙"),
            paragraph("B. 丁"),
            paragraph("答案：B")
        )

        val result = DocxBankParser.parse(ByteArrayInputStream(docx))

        assertNull(result.error)
        assertEquals(2, result.chunks.size)
        assertTrue(result.chunks[0].contains("第一道选择题"))
        assertTrue(result.chunks[1].contains("第二道选择题"))
    }


    @Test
    fun `docx parser preserves self closing empty paragraph as boundary`() {
        val docx = createDocx(
            paragraph("第一道简答题题干是什么？"),
            paragraph("答案：第一题答案"),
            "<w:p/>",
            paragraph("第二道简答题题干是什么？"),
            paragraph("答案：第二题答案")
        )

        val result = DocxBankParser.parse(ByteArrayInputStream(docx))

        assertNull(result.error)
        assertEquals(2, result.chunks.size)
        assertTrue(result.blankSeparated)
    }
    private fun createDocx(vararg paragraphs: String): ByteArray {
        val documentXml = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
            append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">")
            append("<w:body>")
            paragraphs.forEach(::append)
            append("<w:sectPr/>")
            append("</w:body></w:document>")
        }

        return ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("word/document.xml"))
                zip.write(documentXml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            output.toByteArray()
        }
    }

    private fun paragraph(vararg runs: String): String = buildString {
        append("<w:p>")
        runs.forEach { text ->
            append("<w:r><w:t>")
            append(escapeXml(text))
            append("</w:t></w:r>")
        }
        append("</w:p>")
    }

    private fun emptyParagraph(): String = "<w:p></w:p>"

    private fun escapeXml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
