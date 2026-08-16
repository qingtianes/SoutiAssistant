package com.dingding.souti.model

data class Question(
    val id: Long,
    val bankId: Long,
    val stem: String,
    val options: List<String>,
    val answer: String,
    val source: String
)
