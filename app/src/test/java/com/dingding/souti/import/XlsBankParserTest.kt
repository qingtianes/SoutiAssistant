package com.dingding.souti.import

import org.junit.Assert.assertEquals
import org.junit.Test

class XlsBankParserTest {
    @Test
    fun `splits semicolon tab and newline option exports`() {
        assertEquals(listOf("甲", "乙", "丙", "丁"), XlsBankParser.splitOptions("甲;乙；丙\t丁"))
        assertEquals(listOf("甲", "乙", "丙", "丁"), XlsBankParser.splitOptions("甲\n乙\r\n丙\n丁"))
    }
}
