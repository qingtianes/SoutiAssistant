package com.dingding.souti

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class QuestionBankModelsTest {

    @Test
    fun `bank formatting uses the documented chinese date format`() {
        val originalTimeZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
            val bank = Bank(
                id = 1L,
                name = "示例题库",
                sourceFile = "sample.txt",
                createdAt = 0L,
                sourceModifiedAt = 60 * 60 * 1000L,
                type = "manual"
            )

            assertEquals("1970-01-01 08:00", bank.formattedTime())
            assertEquals("1970-01-01 09:00", bank.formattedSourceTime())
            assertEquals("", bank.copy(sourceModifiedAt = 0L).formattedSourceTime())
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun `question and search result retain structured values`() {
        val question = Question(7L, 3L, "题干", listOf("A. 是", "B. 否"), "A", "source.txt")
        val result = SearchResult(question, "题库", 103)

        assertEquals(question, result.question)
        assertEquals(listOf("A. 是", "B. 否"), result.question.options)
        assertEquals("A", result.question.answer)
        assertEquals("题库", result.bankName)
        assertEquals(103, result.score)
    }
}
