package com.dingding.souti

enum class FileFormat { TXT, DOCX, PDF, XLS, XLSX_UNSUPPORTED, UNSUPPORTED }

/**
 * 文件格式识别：根据文件名和 MIME 类型决定用哪个解析器。
 * 扩展名优先于 application/octet-stream 等通用 MIME，避免 Excel 被误当成纯文本。
 */
object FileFormatDetector {
    fun detect(mimeType: String?, fileName: String): FileFormat {
        val mime = mimeType.orEmpty().lowercase()
        val fn = fileName.lowercase()

        return when {
            fn.endsWith(".pdf") -> FileFormat.PDF
            fn.endsWith(".docx") -> FileFormat.DOCX
            fn.endsWith(".xlsx") -> FileFormat.XLSX_UNSUPPORTED
            fn.endsWith(".xls") -> FileFormat.XLS
            fn.endsWith(".txt") -> FileFormat.TXT
            mime == "application/pdf" || mime.endsWith("/pdf") -> FileFormat.PDF
            mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                || mime.endsWith("/openxmlformats-officedocument.wordprocessingml.document") -> FileFormat.DOCX
            mime == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> FileFormat.XLSX_UNSUPPORTED
            mime == "application/vnd.ms-excel" -> FileFormat.XLS
            mime.startsWith("text/") || mime == "application/octet-stream" || mime.isEmpty() -> FileFormat.TXT
            else -> FileFormat.UNSUPPORTED
        }
    }
}
