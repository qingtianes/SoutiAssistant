package com.dingding.souti.repository

import com.dingding.souti.model.Question
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionSearchEngineTest {
    @Test
    fun `legacy whole stem is adapted before matching`() {
        val legacy = question(
            id = 101,
            stem = """
                某装置启动前应执行哪项操作？
                A. 直接升压
                B. 先确认联锁状态
                答案：B
            """.trimIndent()
        )

        val byStem = search("某装置启动前应执行哪项操作", legacy).single()
        val byCorrectOption = search("先确认联锁状态", legacy).single()

        assertEquals(legacy.id, byStem.question.id)
        assertEquals(legacy.id, byCorrectOption.question.id)
        assertEquals("某装置启动前应执行哪项操作？", byStem.question.stem)
        assertEquals(listOf("A. 直接升压", "B. 先确认联锁状态"), byStem.question.options)
        assertEquals("B", byStem.question.answer)
    }

    @Test
    fun `decimal point is significant so 1 point 2 is not equivalent to 12`() {
        val decimal = question(id = 201, stem = "系统正常压力为1.2MPa")
        val integer = question(id = 202, stem = "系统正常压力为12MPa")
        val query = "系统正常压力为1.2MPa"

        val exactScore = QuestionMatcher.score(query, decimal)
        val collapsedScore = QuestionMatcher.score(query, integer)
        val results = search(query, decimal, integer)

        assertEquals(100, exactScore)
        assertTrue("移除小数点后的题目不应与精确题目同分", collapsedScore < exactScore)
        assertEquals(decimal.id, results.first().question.id)
    }

    @Test
    fun `less than or equal and greater than or equal are not equivalent`() {
        val upperBound = question(id = 301, stem = "运行温度应≤10℃")
        val lowerBound = question(id = 302, stem = "运行温度应≥10℃")
        val query = "运行温度应≤10℃"

        val exactScore = QuestionMatcher.score(query, upperBound)
        val oppositeScore = QuestionMatcher.score(query, lowerBound)
        val results = search(query, lowerBound, upperBound)

        assertEquals(100, exactScore)
        assertTrue("相反的不等号不应与精确题目同分", oppositeScore < exactScore)
        assertEquals(upperBound.id, results.first().question.id)
    }

    @Test
    fun `answer letter maps to the corresponding option body`() {
        val choice = choiceQuestion()

        val result = search("先核对联锁许可", choice).single()

        assertEquals(choice.id, result.question.id)
        assertTrue("正确选项正文应形成高置信答案反查", result.score >= HIGH_CONFIDENCE_SCORE)
    }

    @Test
    fun `incorrect distractor is not treated as a high confidence answer lookup`() {
        val choice = choiceQuestion()

        val correctScore = QuestionMatcher.score("先核对联锁许可", choice)
        val distractorScore = QuestionMatcher.score("立即打开旁通阀", choice)
        val distractorResults = search("立即打开旁通阀", choice)

        assertTrue("正确答案正文应比错误干扰项得分高", correctScore > distractorScore)
        assertTrue("错误干扰项不能形成高置信答案反查", distractorScore < HIGH_CONFIDENCE_SCORE)
        assertTrue(
            "搜索结果中错误干扰项不能达到高置信分数",
            distractorResults.none { it.question.id == choice.id && it.score >= HIGH_CONFIDENCE_SCORE }
        )
    }

    private fun choiceQuestion() = question(
        id = 401,
        stem = "装置启动前的正确操作是什么？",
        options = listOf(
            "A. 立即打开旁通阀",
            "B. 先核对联锁许可",
            "C. 跳过现场确认"
        ),
        answer = "B"
    )

    private fun search(query: String, vararg questions: Question) =
        QuestionSearchEngine.search(
            query = query,
            questions = questions.toList(),
            bankNames = mapOf(BANK_ID to "回归测试题库")
        )

    private fun question(
        id: Long,
        stem: String,
        options: List<String> = emptyList(),
        answer: String = ""
    ) = Question(
        id = id,
        bankId = BANK_ID,
        stem = stem,
        options = options,
        answer = answer,
        source = "regression-test"
    )

    private companion object {
        const val BANK_ID = 7L
        const val HIGH_CONFIDENCE_SCORE = 80
    }
}
