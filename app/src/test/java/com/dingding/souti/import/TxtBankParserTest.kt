package com.dingding.souti.import

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream

class TxtBankParserTest {

    @Test
    fun `txt parser chunks numbered questions`() {
        val text = "1.第一道题目\nA.甲\n答案:A\n\n2.第二道题目\nB.乙\n答案:B"
        val result = TxtBankParser.parse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
        assertNull(result.error)
        assertEquals(2, result.chunks.size)
        assertEquals(true, result.hasNumbered)
    }

    @Test
    fun `txt parser strips utf8 bom`() {
        val body = "1.第一道题目\nA.甲\n答案:A"
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + body.toByteArray(Charsets.UTF_8)
        val result = TxtBankParser.parse(ByteArrayInputStream(bytes))
        assertNull(result.error)
        assertEquals(1, result.chunks.size)
        assertEquals(false, result.chunks[0].startsWith("﻿"))
    }

    @Test
    fun `txt parser rejects null input`() {
        val result = TxtBankParser.parse(null)
        assertEquals("无法读取文件", result.error)
    }
}
