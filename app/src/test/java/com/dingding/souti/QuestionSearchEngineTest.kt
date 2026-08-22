package com.dingding.souti

import com.dingding.souti.model.Question
import com.dingding.souti.repository.QuestionSearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionSearchEngineTest {
    @Test
    fun `search covers stem option and answer fields`() {
        val questions = listOf(
            q(1, 10, "造成胶罐压力高的原因有哪些", answer = "聚合转化率低，来料胶液温度高"),
            q(2, 10, "正确操作是什么", options = listOf("关闭进料阀", "检查设备状态")),
            q(3, 10, "无关题目")
        )
        val names = mapOf(10L to "题库")

        assertEquals(1L, QuestionSearchEngine.search("造成胶罐压力高", questions, names).first().question.id)
        assertEquals(1L, QuestionSearchEngine.search("来料胶液温度高", questions, names).first().question.id)
        assertEquals(2L, QuestionSearchEngine.search("检查设备状态 关闭进料阀", questions, names).first().question.id)
    }

    @Test
    fun `same content selected from overlapping banks is shown once`() {
        val questions = listOf(
            q(1, 10, "重复题干", answer = "同一答案"),
            q(2, 20, "重复题干", answer = "同一答案")
        )
        val results = QuestionSearchEngine.search("重复题干", questions, mapOf(10L to "TXT", 20L to "XLS"), 5)
        assertEquals(1, results.size)
    }

    @Test
    fun `higher weighted stem result sorts first`() {
        val direct = q(1, 10, "聚合停车后的处理步骤有哪些", answer = "关闭阀门")
        val answerOnly = q(2, 10, "其他问题", answer = "需要掌握聚合停车后的处理步骤")
        val results = QuestionSearchEngine.search("聚合停车后的处理步骤有哪些", listOf(answerOnly, direct), mapOf(10L to "题库"))
        assertEquals(1L, results.first().question.id)
        assertTrue(results.first().score > results.last().score)
    }

    private fun q(
        id: Long,
        bankId: Long,
        stem: String,
        options: List<String> = emptyList(),
        answer: String = ""
    ) = Question(id, bankId, stem, options, answer, "test")
}
