package com.dingding.souti.import

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionChunkParserTest {
    @Test
    fun `parses stem options and multiline answer`() {
        val question = QuestionChunkParser.parse(
            "1. 某事件的处理步骤有哪些？\nA. 选项甲\nB. 选项乙\n答案:1、停止进料\n2、关闭阀门",
            "sample.txt",
            1L
        )

        assertEquals("某事件的处理步骤有哪些？", question?.stem)
        assertEquals(listOf("A. 选项甲", "B. 选项乙"), question?.options)
        assertEquals("1、停止进料\n2、关闭阀门", question?.answer)
        assertEquals(true, question?.rawText?.contains("A. 选项甲") == true)
    }

    @Test
    fun `does not treat answer numbered lines as options or another stem`() {
        val question = QuestionChunkParser.parse(
            "某简答题\n答案:1、原因一\n2、原因二\n3、措施三",
            "sample.txt",
            2L
        )

        assertEquals("某简答题", question?.stem)
        assertEquals(emptyList<String>(), question?.options)
        assertEquals("1、原因一\n2、原因二\n3、措施三", question?.answer)
    }

    @Test
    fun `supports wider option labels answer aliases and numbered parentheses`() {
        val question = QuestionChunkParser.parse(
            "12）计算题题干\nA：第一项\nE) 第五项\n参考答案：x=2×3",
            "sample.txt",
            3L
        )
        assertEquals("计算题题干", question?.stem)
        assertEquals(listOf("A. 第一项", "E. 第五项"), question?.options)
        assertEquals("x=2×3", question?.answer)
    }
    @Test
    fun `parses bracketed correct answer used by pdf exports`() {
        val parsed = QuestionChunkParser.parse(
            "1.（151694）活化能越大反应越敏感。\n【正确答案】√ 正确",
            "pdf",
            1L
        )!!
        assertEquals("（151694）活化能越大反应越敏感。", parsed.stem)
        assertEquals("√ 正确", parsed.answer)
    }

    @Test
    fun `does not treat answer explanation as an answer label`() {
        val parsed = QuestionChunkParser.parse(
            "题干内容\n答案解析：这里是解析正文",
            "txt",
            2L
        )!!
        assertEquals("", parsed.answer)
        assertTrue(parsed.stem.contains("答案解析"))
    }


    @Test
    fun `extracts embedded judgement answer from pdf without swallowing wrapped stem`() {
        val parsed = QuestionChunkParser.parse(
            "1.(151694)( √ )活化能越大，反应速率越敏感，题干发生换行\n后半段仍然属于题干。(1.0分)",
            "pdf",
            4L
        )!!
        assertEquals("(151694)活化能越大，反应速率越敏感，题干发生换行\n后半段仍然属于题干。(1.0分)", parsed.stem)
        assertEquals("√ 正确", parsed.answer)
    }

    @Test
    fun `splits multiple pdf options written on the same line`() {
        val parsed = QuestionChunkParser.parse(
            "2.(151696)属于开环控制的是？\nA、定值控制    B、随动控制    C、前馈控制    D、程序控制\n正确答案：C",
            "pdf",
            5L
        )!!
        assertEquals(listOf("A. 定值控制", "B. 随动控制", "C. 前馈控制", "D. 程序控制"), parsed.options)
        assertEquals("C", parsed.answer)
    }}
