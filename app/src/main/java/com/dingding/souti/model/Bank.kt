package com.dingding.souti.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Bank(
    val id: Long,
    val name: String,
    val sourceFile: String,
    val createdAt: Long,
    val sourceModifiedAt: Long = 0,
    val type: String
) {
    fun formattedTime(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(createdAt))
    fun formattedSourceTime(): String =
        if (sourceModifiedAt > 0) SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(sourceModifiedAt))
        else ""
}
