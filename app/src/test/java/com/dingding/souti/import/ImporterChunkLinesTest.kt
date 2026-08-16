package com.dingding.souti.import

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImporterChunkLinesTest {

    @Test
    fun `chunkLines groups numbered questions and skips section titles`() {
        val result = Importer.chunkLines(
            listOf(
                "题库说明",
                "一、单选题",
                "1. 第一题题干内容较长",
                "A. 选项一",
                "B. 选项二",
                "2、第二题题干内容较长",
                "A. 选项甲",
                "1/105"
            )
        )

        assertEquals(2, result.chunks.size)
        assertTrue(result.hasNumbered)
        assertFalse(result.noNumberWithOption)
        assertFalse(result.blankSeparated)
        assertEquals("1. 第一题题干内容较长\nA. 选项一\nB. 选项二", result.chunks[0])
        assertEquals("2、第二题题干内容较长\nA. 选项甲", result.chunks[1])
    }

    @Test
    fun `chunkLines uses blank lines to separate unnumbered questions`() {
        val result = Importer.chunkLines(
            listOf(
                "题库说明",
                "（ ）第一道判断题",
                "正确",
                "",
                "（ ）第二道判断题",
                "错误"
            )
        )

        assertEquals(2, result.chunks.size)
        assertFalse(result.hasNumbered)
        assertFalse(result.noNumberWithOption)
        assertTrue(result.blankSeparated)
        assertEquals("（ ）第一道判断题\n正确", result.chunks[0])
        assertEquals("（ ）第二道判断题\n错误", result.chunks[1])
    }

    @Test
    fun `chunkLines flags unnumbered question with aligned options`() {
        val result = Importer.chunkLines(
            listOf(
                "（ ）这是题干",
                "A. 选项一",
                "B. 选项二"
            )
        )

        assertEquals(1, result.chunks.size)
        assertFalse(result.hasNumbered)
        assertTrue(result.noNumberWithOption)
        assertFalse(result.blankSeparated)
    }

    @Test
    fun `chunkLines rejects an unstructured text stream`() {
        val result = Importer.chunkLines(listOf("没有题号", "也没有分隔线", "无法判断边界"))

        assertTrue(result.chunks.isEmpty())
        assertTrue(result.error?.contains("无法可靠识别题目") == true)
    }

    @Test
    fun `parse result calculates coverage and low coverage threshold`() {
        val result = Importer.ParseResult(
            chunks = listOf("题目"),
            sourceLength = 10,
            parsedLength = 6
        )

        assertEquals(60, result.coverage())
        assertFalse(result.lowCoverage())
        assertTrue(result.lowCoverage(61))
        assertEquals(100, Importer.ParseResult(emptyList()).coverage())
    }
}
