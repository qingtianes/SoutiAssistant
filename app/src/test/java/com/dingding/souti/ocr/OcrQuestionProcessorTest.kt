package com.dingding.souti.ocr

import com.dingding.souti.model.Question
import com.dingding.souti.model.SearchResult

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OcrQuestionProcessorTest {

    @Test
    fun `blank scan text produces no processing result`() {
        assertNull(OcrQuestionProcessor.processScanText(" \n\t ", search = { _, _ -> emptyList() }))
        assertEquals(emptyList<String>(), OcrQuestionProcessor.extractScanQueries(" \n\t "))
    }

    @Test
    fun `normalization folds line breaks tabs and repeated spaces`() {
        assertEquals(
            "第一行 第二行 第三行",
            OcrQuestionProcessor.normalizeText("  第一行\n\n第二行\t  第三行  ")
        )
    }

    @Test
    fun `scan queries support chinese and english parentheses and answer colons`() {
        assertEquals(
            listOf("第一题题干（ ） A.甲", "第二题题干( ) B.乙"),
            OcrQuestionProcessor.extractScanQueries(
                "第一题题干（ ） A.甲 答案：A 第二题题干( ) B.乙 答案:B"
            )
        )
    }

    @Test
    fun `scan results are deduplicated by question id and retain the highest score`() {
        val first = questionResult(id = 1, score = 20, stem = "旧匹配")
        val betterDuplicate = questionResult(id = 1, score = 90, stem = "更高分匹配")
        val second = questionResult(id = 2, score = 70, stem = "第二题")
        val third = questionResult(id = 3, score = 60, stem = "第三题")
        var call = 0

        val processed = OcrQuestionProcessor.processScanText(
            "第一查询（ ） 答案：A 第二查询（ ） 答案：B",
            search = { _, limit ->
                assertEquals(5, limit)
                call += 1
                if (call == 1) listOf(first, second) else listOf(betterDuplicate, third)
            },
            resultLimit = 2
        )!!

        assertEquals(3, processed.uniqueMatchCount)
        assertEquals(listOf(1L, 2L), processed.matches.map { it.question.id })
        assertEquals(90, processed.matches.first().score)
        assertEquals("更高分匹配", processed.matches.first().question.stem)
    }

    @Test
    fun `equal scores retain their first seen order`() {
        val first = questionResult(id = 10, score = 80, stem = "先出现")
        val second = questionResult(id = 20, score = 80, stem = "后出现")

        val processed = OcrQuestionProcessor.processScanText(
            "第一查询（ ） 答案：A 第二查询（ ） 答案：B",
            search = { query, _ ->
                if (query.startsWith("第一")) listOf(first) else listOf(second)
            }
        )!!

        assertEquals(listOf(10L, 20L), processed.matches.map { it.question.id })
    }

    @Test
    fun `screen read splitting supports both colon styles and removes previous answer letters`() {
        assertEquals(
            listOf("第一题 A.甲", "第二题 B.乙"),
            OcrQuestionProcessor.splitScreenReadQuestions(
                "第一题 A.甲 答案：A 第二题 B.乙 答案:B"
            )
        )
    }

    @Test
    fun `screen read splitting falls back to the original text for one question`() {
        val text = "只有一道题（ ） A.甲 B.乙"
        assertEquals(listOf(text), OcrQuestionProcessor.splitScreenReadQuestions(text))
    }

    @Test
    fun `screen read query preserves rule prefix parentheses and options for field matching`() {
        assertEquals(
            "依据《受限空间安全规范》涂刷具有挥发性的涂料时（正确做法）应保持通风 A.是 B.否",
            OcrQuestionProcessor.extractScreenReadStem(
                "依据《受限空间安全规范》涂刷具有挥发性的涂料时（正确做法）应保持通风 A.是 B.否"
            )
        )
    }

    @Test
    fun `screen read never removes real latin initials`() {
        assertEquals("DCS系统联锁的作用是什么", OcrQuestionProcessor.extractScreenReadStem("DCS系统联锁的作用是什么"))
        assertEquals("A型材料应如何保存", OcrQuestionProcessor.extractScreenReadStem("A型材料应如何保存"))
    }

    @Test
    fun `numbered lines split multiple questions without answer labels`() {
        assertEquals(
            listOf("1. 第一题题干 A.甲 B.乙", "2. 第二题题干 A.丙 B.丁"),
            OcrQuestionProcessor.extractScanQueries(
                "1. 第一题题干\nA.甲\nB.乙\n2. 第二题题干\nA.丙\nB.丁"
            )
        )
    }


    @Test
    fun `processing preserves line boundaries when splitting numbered screen questions`() {
        val seen = mutableListOf<String>()
        OcrQuestionProcessor.processScanText(
            "1. 第一题题干\nA.甲\nB.乙\n2. 第二题题干\nA.丙\nB.丁",
            search = { query, _ -> seen += query; emptyList() }
        )
        assertEquals(listOf("1. 第一题题干 A.甲 B.乙", "2. 第二题题干 A.丙 B.丁"), seen)
    }
    private fun questionResult(id: Long, score: Int, stem: String): SearchResult =
        SearchResult(
            question = Question(id, 1L, stem, emptyList(), "", "test"),
            bankName = "测试题库",
            score = score
        )
}
