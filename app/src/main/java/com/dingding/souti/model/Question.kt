package com.dingding.souti.model

data class Question(
    val id: Long,
    val bankId: Long,
    val stem: String,
    val options: List<String>,
    val answer: String,
    val source: String,
    /** 导入时保留的原始题块；旧版本数据没有该字段时自动为空。 */
    val rawText: String = ""
)
