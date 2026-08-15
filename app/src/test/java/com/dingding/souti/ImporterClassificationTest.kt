package com.dingding.souti

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImporterClassificationTest {

    @Test
    fun `isSectionTitle recognizes supported section and footer forms`() {
        val titles = listOf(
            "一、单选题",
            "安全作业",
            "1.动火作业",
            "1/105",
            "二、判断题，共206道。",
            ""
        )

        titles.forEach { assertTrue("Expected section title: [$it]", Importer.isSectionTitle(it)) }
        assertFalse(Importer.isSectionTitle("1. 下列哪项是正确的？"))
        assertFalse(Importer.isSectionTitle("动火作业（多选）"))
    }

    @Test
    fun `isQuestionStart requires a number delimiter and a meaningful stem`() {
        assertTrue(Importer.isQuestionStart("1. 下列哪项是正确的？"))
        assertTrue(Importer.isQuestionStart("2、（ ）属于安全措施"))
        assertTrue(Importer.isQuestionStart("003. 这是一个较长的题干"))

        assertFalse(Importer.isQuestionStart("题干没有序号"))
        assertFalse(Importer.isQuestionStart("1. 短"))
        assertFalse(Importer.isQuestionStart("1- 不是支持的分隔符"))
    }

    @Test
    fun `isOptionLine recognizes latin option labels and punctuation variants`() {
        listOf("A. 选项", "b、选项", "C．选项", "d）选项", "A)选项").forEach {
            assertTrue("Expected option line: [$it]", Importer.isOptionLine(it))
        }
        listOf("AA. 不是单个选项", "E. 超出范围", "1. 题目", "A 不是选项").forEach {
            assertFalse("Expected non-option line: [$it]", Importer.isOptionLine(it))
        }
    }
}
