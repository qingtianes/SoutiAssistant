package com.dingding.souti.import

import org.junit.Assert.assertEquals
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
        assertEquals(listOf("选项甲", "选项乙"), question?.options)
        assertEquals("1、停止进料\n2、关闭阀门", question?.answer)
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
}
