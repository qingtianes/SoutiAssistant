package com.dingding.souti

import org.junit.Assert.assertEquals
import org.junit.Test

class ImporterFileFormatTest {

    @Test
    fun `specific extension wins over generic mime type`() {
        assertEquals(
            FileFormat.XLS,
            Importer.detectFileFormat("application/octet-stream", "题库.XLS")
        )
        assertEquals(
            FileFormat.DOCX,
            Importer.detectFileFormat("application/octet-stream", "题库.docx")
        )
        assertEquals(
            FileFormat.PDF,
            Importer.detectFileFormat("application/octet-stream", "题库.pdf")
        )
    }

    @Test
    fun `known mime types work when file extension is hidden`() {
        assertEquals(
            FileFormat.XLSX_UNSUPPORTED,
            Importer.detectFileFormat(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "下载文件"
            )
        )
        assertEquals(
            FileFormat.DOCX,
            Importer.detectFileFormat(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "下载文件"
            )
        )
        assertEquals(
            FileFormat.PDF,
            Importer.detectFileFormat("application/pdf", "下载文件")
        )
    }


    @Test
    fun `xlsx is identified but not advertised as supported xls`() {
        assertEquals(
            FileFormat.XLSX_UNSUPPORTED,
            Importer.detectFileFormat("application/octet-stream", "题库.xlsx")
        )
    }

    @Test
    fun `generic and text mime types fall back to txt`() {
        assertEquals(
            FileFormat.TXT,
            Importer.detectFileFormat("application/octet-stream", "无扩展名")
        )
        assertEquals(
            FileFormat.TXT,
            Importer.detectFileFormat("text/plain", "无扩展名")
        )
        assertEquals(
            FileFormat.TXT,
            Importer.detectFileFormat(null, "无扩展名")
        )
    }

    @Test
    fun `unknown binary format is rejected`() {
        assertEquals(
            FileFormat.UNSUPPORTED,
            Importer.detectFileFormat("application/zip", "archive.zip")
        )
    }
}
