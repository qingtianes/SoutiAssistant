package com.dingding.souti.repository

import com.dingding.souti.import.QuestionChunkParser
import com.dingding.souti.model.Question
import com.dingding.souti.model.SearchResult

/** 不依赖 Android 的纯搜索引擎，供浮窗、读屏和摄像头统一使用。 */
object QuestionSearchEngine {
    const val MIN_RESULT_SCORE = 45

    fun search(
        query: String,
        questions: List<Question>,
        bankNames: Map<Long, String>,
        limit: Int = 5
    ): List<SearchResult> {
        if (query.isBlank() || limit <= 0) return emptyList()

        return questions.asSequence()
            .map(::adaptLegacyQuestion)
            .map { question ->
                SearchResult(
                    question = question,
                    bankName = bankNames[question.bankId] ?: "未知题库",
                    score = QuestionMatcher.score(query, question)
                )
            }
            .filter { it.score >= MIN_RESULT_SCORE }
            .sortedWith(
                compareByDescending<SearchResult> { it.score }
                    .thenBy { it.question.stem.length }
                    .thenBy { it.question.id }
            )
            .distinctBy { QuestionMatcher.contentFingerprint(it.question) }
            .take(limit)
            .toList()
    }

    /**
     * v1.1.1 及更早版本曾把整道题块保存在 stem 中。这里只做内存适配，
     * 不静默改写用户题库；成功拆分后沿用原 id/bankId/source。
     */
    internal fun adaptLegacyQuestion(question: Question): Question {
        if (question.options.isNotEmpty() || question.answer.isNotBlank()) return question
        val raw = question.rawText.ifBlank { question.stem }
        val hasAnswerMarker = Regex("(?m)^\\s*(答案|正确答案|参考答案|标准答案)\\s*[:：]").containsMatchIn(raw)
        val optionCount = Regex("(?m)^\\s*[A-Ha-h]\\s*[.、．:：)）]").findAll(raw).count()
        if (!hasAnswerMarker && optionCount < 2) return question

        return QuestionChunkParser.parse(raw, question.source, question.id)
            ?.copy(bankId = question.bankId, source = question.source, rawText = raw)
            ?: question
    }
}
