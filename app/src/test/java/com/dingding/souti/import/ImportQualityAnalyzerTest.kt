package com.dingding.souti.import

import com.dingding.souti.model.Question
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportQualityAnalyzerTest {
    @Test
    fun `reports duplicates empty answers and suspicious merged stems without deleting data`() {
        val questions = listOf(
            Question(1, 0, "重复题目", emptyList(), "答案", "test"),
            Question(2, 0, "重复题目", emptyList(), "答案", "test"),
            Question(3, 0, "无答案题", emptyList(), "", "test"),
            Question(4, 0, "长".repeat(501), listOf("唯一选项"), "A", "test")
        )
        val report = ImportQualityAnalyzer.analyze(questions)
        assertEquals(1, report.duplicateCount)
        assertEquals(1, report.emptyAnswerCount)
        assertEquals(1, report.suspiciousOptionCount)
        assertEquals(1, report.longStemCount)
        assertTrue(report.warnings.size >= 4)
    }
}
