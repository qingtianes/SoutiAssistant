package com.dingding.souti.import

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BankChunkerRegressionTest {
    @Test
    fun `numbered answer steps stay inside one blank separated question`() {
        val result = BankChunker.chunkLines(
            listOf(
                "题目数：2",
                "---",
                "聚合停车后的处理步骤有哪些？",
                "答案:1、停止相关进料",
                "2、关闭相关阀门",
                "3、检查设备状态",
                "",
                "第二道题的题干是什么？",
                "答案:A"
            )
        )

        assertEquals(2, result.chunks.size)
        assertEquals(
            "聚合停车后的处理步骤有哪些？\n答案:1、停止相关进料\n2、关闭相关阀门\n3、检查设备状态",
            result.chunks[0]
        )
        assertTrue(result.chunks[1].contains("第二道题"))
    }

    @Test
    fun `answer block mode preserves the real sample shape`() {
        val result = BankChunker.chunkLines(
            listOf(
                "搜题助手 - 题库：示例",
                "题目数：2",
                "---",
                "第一道选择题",
                "A. 甲",
                "B. 乙",
                "答案:A",
                "",
                "第二道简答题",
                "答案:1、第一步",
                "2、第二步"
            )
        )

        assertEquals(2, result.chunks.size)
        assertEquals("第二道简答题\n答案:1、第一步\n2、第二步", result.chunks[1])
    }
}
