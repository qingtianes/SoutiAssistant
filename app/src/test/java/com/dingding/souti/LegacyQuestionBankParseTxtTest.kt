package com.dingding.souti

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyQuestionBankParseTxtTest {

    @Test
    fun `parseTxt reads multiple blank separated questions`() {
        val text = """
            1. 下列哪项是正确的？
            A. 第一项
            B、第二项
            C) 第三项
            D）第四项
            答案：B

            2、第二道题干
            A. 是
            B. 否
            答案:A
        """.trimIndent()

        val questions = QuestionBank.parseTxt(text, "sample.txt")

        assertEquals(2, questions.size)
        assertEquals("1. 下列哪项是正确的？", questions[0].stem)
        assertEquals(listOf("A. 第一项", "B、第二项", "C) 第三项", "D）第四项"), questions[0].options)
        assertEquals("B", questions[0].answer)
        assertEquals("sample.txt", questions[0].source)
        assertEquals(0L, questions[0].bankId)
        assertEquals("2、第二道题干", questions[1].stem)
        assertEquals(listOf("A. 是", "B. 否"), questions[1].options)
        assertEquals("A", questions[1].answer)
        assertEquals(questions[0].id + 1, questions[1].id)
    }

    @Test
    fun `parseTxt keeps current bom behavior and multiline stem in each block`() {
        val text = "\uFEFF题目第一行\n题目第二行\n答案：正确"

        val questions = QuestionBank.parseTxt(text, "bom.txt")

        assertEquals(1, questions.size)
        assertEquals("\uFEFF题目第一行\n题目第二行", questions[0].stem)
        assertEquals("正确", questions[0].answer)
    }

    @Test
    fun `parseTxt ignores blank blocks and trims surrounding whitespace`() {
        val text = "\n\n  题干内容  \n  A. 选项  \n\n\n"

        val questions = QuestionBank.parseTxt(text, "trim.txt")

        assertEquals(1, questions.size)
        assertEquals("题干内容", questions[0].stem)
        assertEquals(listOf("A. 选项"), questions[0].options)
    }

    @Test
    fun `parseTxt returns an empty list for blank input`() {
        assertTrue(QuestionBank.parseTxt("\n  \n", "empty.txt").isEmpty())
    }
}
