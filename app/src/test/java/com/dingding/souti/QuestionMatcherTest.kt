package com.dingding.souti

import com.dingding.souti.model.Question
import com.dingding.souti.repository.QuestionMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionMatcherTest {
    @Test
    fun `blank input scores zero`() {
        assertEquals(0, QuestionMatcher.score("", ""))
        assertEquals(0, QuestionMatcher.score("   ", "题目"))
        assertEquals(0, QuestionMatcher.score("题目", " \n\t "))
    }

    @Test
    fun `fullwidth punctuation brackets and whitespace are ignored`() {
        val plain = QuestionMatcher.score("SBS聚合温度20C", "SBS聚合温度20C")
        val formatted = QuestionMatcher.score("ＳＢＳ（ ）聚合温度：２０Ｃ", "SBS 聚合温度 20C")
        assertEquals(plain, formatted)
    }

    @Test
    fun `containment is a strong match`() {
        assertTrue(QuestionMatcher.score("下列是线型橡胶", "下列是线型橡胶的主要性能") >= 80)
    }

    @Test
    fun `unrelated short text does not match`() {
        assertEquals(0, QuestionMatcher.score("甲乙", "甲乙丙丁"))
        assertEquals(0, QuestionMatcher.score("停车处理", "天气预报"))
    }

    @Test
    fun `one OCR error still matches a long stem`() {
        assertTrue(QuestionMatcher.score("造成胶罐压力高的工艺原困有哪些", "造成胶罐压力高的工艺原因有哪些") > 0)
    }

    @Test
    fun `answer fragment can reverse lookup a question`() {
        val question = question(
            stem = "造成胶罐压力高的工艺原因有哪些？",
            answer = "1、聚合转化率低；2、来料胶液温度高；3、聚合压胶进入胶罐氮气量多。"
        )
        assertTrue(QuestionMatcher.score("来料胶液温度高", question) >= 70)
    }

    @Test
    fun `shuffled option text matches independent of option order`() {
        val question = question(
            stem = "下列哪些属于正确操作？",
            options = listOf("先关闭进料阀", "再检查设备状态", "通知岗位人员")
        )
        assertTrue(QuestionMatcher.score("通知岗位人员 先关闭进料阀 再检查设备状态", question) >= 80)
    }

    @Test
    fun `direct stem match outranks incidental long answer match`() {
        val direct = question(stem = "聚合停车后的处理步骤有哪些？", answer = "关闭阀门")
        val incidental = question(stem = "设备巡检有哪些要求？", answer = "出现异常时执行聚合停车后的处理步骤并关闭相关阀门")
        assertTrue(
            QuestionMatcher.score("聚合停车后的处理步骤有哪些", direct) >
                QuestionMatcher.score("聚合停车后的处理步骤有哪些", incidental)
        )
    }

    @Test
    fun `answer fragment outranks incidental short option contained in the query`() {
        val expected = question(
            stem = "装置停循环水，聚合应急处置？",
            answer = "1、立即停止聚合反应釜投料，关闭相关阀门并通知岗位人员"
        )
        val incidental = question(
            stem = "通过计量罐直接加到聚合釜中的物料是（ ）",
            options = listOf("停止投料", "继续投料", "提高温度")
        )
        val query = "1、立即停止聚合反应釜投料，关闭相关阀门"
        assertTrue(
            QuestionMatcher.score(query, expected) > QuestionMatcher.score(query, incidental)
        )
    }

    @Test
    fun `long OCR context does not strongly match a short incidental candidate`() {
        assertEquals(
            0,
            QuestionMatcher.score(
                "立即停止聚合反应釜投料关闭阀门并通知岗位人员",
                "停止投料"
            )
        )
    }

    private fun question(
        stem: String,
        options: List<String> = emptyList(),
        answer: String = ""
    ) = Question(1L, 1L, stem, options, answer, "test")
}
