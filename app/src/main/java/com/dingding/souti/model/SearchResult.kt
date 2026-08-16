package com.dingding.souti.model

data class SearchResult(
    val question: Question,
    val bankName: String,
    val score: Int
)
