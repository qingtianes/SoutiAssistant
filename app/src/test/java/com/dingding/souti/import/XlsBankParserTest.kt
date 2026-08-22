package com.dingding.souti.import

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import jxl.Workbook
import jxl.write.Label
import org.junit.Assert.assertEquals
import org.junit.Test

class XlsBankParserTest {
    @Test
    fun `splits semicolon tab and newline option exports`() {
        assertEquals(listOf("甲", "乙", "丙", "丁"), XlsBankParser.splitOptions("甲;乙；丙\t丁"))
        assertEquals(listOf("甲", "乙", "丙", "丁"), XlsBankParser.splitOptions("甲\n乙\r\n丙\n丁"))
        assertEquals(
            listOf("A. 甲选项", "B. 乙选项", "C. 丙选项"),
            XlsBankParser.splitOptions("A. 甲选项 B. 乙选项 C. 丙选项")
        )
    }

    @Test
    fun `imports questions from every sheet`() {
        val result = parseWorkbook(
            sheet(
                "第一套",
                listOf("题目内容", "可选项", "答案"),
                listOf("第一张表的题目", "A. 甲 B. 乙", "A")
            ),
            sheet(
                "第二套",
                listOf("题干", "选项", "正确答案"),
                listOf("第二张表的题目", "A. 丙 B. 丁", "B")
            )
        )

        assertEquals(null, result.error)
        assertEquals(
            listOf(
                "第一张表的题目\nA. 甲\nB. 乙\n答案:A",
                "第二张表的题目\nA. 丙\nB. 丁\n答案:B"
            ),
            result.chunks
        )
    }

    @Test
    fun `preserves out of order option labels and their source order`() {
        val result = parseWorkbook(
            sheet(
                "乱序选项",
                listOf("题目内容", "可选项", "答案"),
                listOf("乱序标签应原样保留", "D. 丁选项 B. 乙选项 A. 甲选项 C. 丙选项", "D")
            )
        )

        assertEquals(null, result.error)
        assertEquals(
            listOf("乱序标签应原样保留\nD. 丁选项\nB. 乙选项\nA. 甲选项\nC. 丙选项\n答案:D"),
            result.chunks
        )
    }

    @Test
    fun `keeps questions with the same long prefix and different suffixes`() {
        val commonPrefix = "这是用于验证相同长前缀题目不会被错误去重的共同题干内容，只有最后的场景后缀不同："
        val firstStem = commonPrefix + "设备启动前检查"
        val secondStem = commonPrefix + "设备停止后检查"
        val result = parseWorkbook(
            sheet(
                "长前缀",
                listOf("题目内容", "可选项", "答案"),
                listOf(firstStem, "A. 正确 B. 错误", "A"),
                listOf(secondStem, "A. 正确 B. 错误", "B")
            )
        )

        assertEquals(null, result.error)
        assertEquals(2, result.chunks.size)
        assertEquals(firstStem, result.chunks[0].lineSequence().first())
        assertEquals(secondStem, result.chunks[1].lineSequence().first())
    }

    @Test
    fun `splits consecutive options stored in one cell`() {
        val result = parseWorkbook(
            sheet(
                "连续选项",
                listOf("题目内容", "可选项", "答案"),
                listOf("同一单元格中的选项应逐项导入", "A、甲选项 B：乙选项 C）丙选项 D．丁选项", "C")
            )
        )

        assertEquals(null, result.error)
        assertEquals(
            listOf("同一单元格中的选项应逐项导入\nA. 甲选项\nB. 乙选项\nC. 丙选项\nD. 丁选项\n答案:C"),
            result.chunks
        )
    }

    private fun sheet(name: String, vararg rows: List<String>): SheetFixture =
        SheetFixture(name, rows.toList())

    private fun parseWorkbook(vararg sheets: SheetFixture): Importer.ParseResult {
        val output = ByteArrayOutputStream()
        val workbook = Workbook.createWorkbook(output)
        try {
            sheets.forEachIndexed { sheetIndex, fixture ->
                val writableSheet = workbook.createSheet(fixture.name, sheetIndex)
                fixture.rows.forEachIndexed { rowIndex, row ->
                    row.forEachIndexed { columnIndex, value ->
                        writableSheet.addCell(Label(columnIndex, rowIndex, value))
                    }
                }
            }
            workbook.write()
        } finally {
            workbook.close()
        }
        return XlsBankParser.parse(ByteArrayInputStream(output.toByteArray()))
    }

    private data class SheetFixture(
        val name: String,
        val rows: List<List<String>>
    )
}
