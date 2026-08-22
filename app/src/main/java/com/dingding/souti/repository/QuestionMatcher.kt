package com.dingding.souti.repository

import com.dingding.souti.model.Question
import java.text.Normalizer
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 题目结构化匹配器，所有公开分数都统一为 0..100：
 * - 题干是主要证据；
 * - 同时命中多个选项时支持选项乱序；
 * - 长度足够的答案片段可以反查题目；
 * - 选择题答案字母会先解析为正确选项正文，避免把干扰项当答案。
 */
object QuestionMatcher {
    private const val MIN_TEXT_LENGTH = 3
    private val optionMarker = Regex("(?i)(?:^|\\s)([A-H])\\s*[.、．:：)）]\\s*")
    private val optionLabel = Regex("(?i)^\\s*([A-H])\\s*[.、．:：)）]\\s*(.*)$")
    private val answerLetters = Regex("(?i)^\\s*[A-H](?:[\\s,，、;/和及]+[A-H]|[A-H])*\\s*$")

    data class OptionEvidence(val score: Int, val matchedCount: Int)

    /** 兼容旧调用：比较两段文本，返回 0..100 的相关度。 */
    fun score(query: String, candidate: String): Int = textSimilarity(query, candidate)

    /** 对结构化题目打分，返回 0..100。 */
    fun score(query: String, question: Question): Int {
        if (query.isBlank()) return 0

        val queryOptions = extractOptions(query)
        val queryStem = if (queryOptions.isNotEmpty()) {
            query.substring(0, optionMarker.find(query)?.range?.first ?: query.length).trim()
        } else {
            query
        }
        val normalizedQuery = normalize(query)
        if (normalizedQuery.length < 2) return 0

        val stemScore = textSimilarity(queryStem.ifBlank { query }, question.stem)
        val optionEvidence = scoreOptions(query, queryOptions, question.options)
        val answerScore = scoreAnswer(query, question)
        val correctOptionScore = resolvedCorrectOptionBodies(question)
            .maxOfOrNull { textSimilarity(query, it) }
            ?: 0

        // OCR 中同时出现至少两个选项时，按“整道选择题”处理；选项顺序不参与评分。
        if (optionEvidence.matchedCount >= 2) {
            val combined = if (stemScore > 0 && queryStem.isNotBlank()) {
                (stemScore * 0.68 + optionEvidence.score * 0.32).roundToInt()
            } else {
                optionEvidence.score
            }
            return maxOf(combined, stemScore).coerceIn(0, 100)
        }

        // 普通题干/简答查询：答案反查略低于同质量题干，单个选项只作为弱线索。
        val answerLookup = if (normalizedQuery.length >= 6) (answerScore * 0.86).roundToInt() else 0
        val correctOptionLookup = if (normalizedQuery.length >= 2) (correctOptionScore * 0.88).roundToInt() else 0
        val singleOptionLookup = (optionEvidence.score * 0.55).roundToInt()
        return maxOf(stemScore, answerLookup, correctOptionLookup, singleOptionLookup).coerceIn(0, 100)
    }

    /** 用题目语义做跨题库显示级去重，不改变源题库。 */
    fun contentFingerprint(question: Question): String {
        val optionBodies = question.options.map(::stripOptionLabel).map(::normalize)
            .filter { it.isNotBlank() }.sorted()
        val semanticAnswer = resolvedCorrectOptionBodies(question)
            .map(::normalize).filter { it.isNotBlank() }.sorted()
            .ifEmpty { listOf(normalize(question.answer)) }
        return buildString {
            append(normalize(question.stem))
            append('|')
            append(optionBodies.joinToString("|"))
            append('|')
            append(semanticAnswer.joinToString("|"))
        }
    }

    internal fun normalize(text: String): String {
        if (text.isBlank()) return ""
        val compatibility = Normalizer.normalize(text, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace("≦", "<=")
            .replace("≤", "<=")
            .replace("≧", ">=")
            .replace("≥", ">=")
            .replace("≠", "!=")
            .replace("℃", "°c")
            .replace('✕', '×')
            .replace('✖', '×')
            .replace('−', '-')
            .replace('—', '-')
            .replace('–', '-')

        return buildString(compatibility.length) {
            compatibility.forEachIndexed { index, ch ->
                val previous = compatibility.getOrNull(index - 1)
                val next = compatibility.getOrNull(index + 1)
                when {
                    ch.isLetterOrDigit() -> append(ch)
                    ch == '.' && previous?.isDigit() == true && next?.isDigit() == true -> append(ch)
                    ch in charArrayOf('+', '-', '*', '/', '=', '%', '>', '<', '!', '√', '°', '×') -> append(ch)
                }
            }
        }
    }

    private fun scoreOptions(
        query: String,
        explicitQueryOptions: List<String>,
        bankOptions: List<String>
    ): OptionEvidence {
        if (bankOptions.isEmpty()) return OptionEvidence(0, 0)
        val bodies = bankOptions.map(::stripOptionLabel).filter { it.isNotBlank() }
        if (bodies.isEmpty()) return OptionEvidence(0, 0)

        if (explicitQueryOptions.size >= 2) {
            val similarities = explicitQueryOptions.map { queryOption ->
                bodies.maxOfOrNull { body -> textSimilarity(queryOption, body) } ?: 0
            }
            val matched = similarities.count { it >= 60 }
            val denominator = maxOf(explicitQueryOptions.size, bodies.size)
            val score = if (denominator == 0) 0 else similarities.sortedDescending()
                .take(minOf(explicitQueryOptions.size, bodies.size))
                .sum() / denominator
            return OptionEvidence(score.coerceIn(0, 100), matched)
        }

        // 某些 OCR 会丢掉 A/B/C/D 标签；此时只在同一查询包含多个完整选项时建立强证据。
        val normalizedQuery = normalize(query)
        val perOption = bodies.map { body ->
            val normalizedBody = normalize(body)
            when {
                normalizedBody.length >= 2 && normalizedQuery.contains(normalizedBody) -> 100
                else -> textSimilarity(query, body)
            }
        }
        val matched = perOption.count { it >= 70 }
        val score = if (matched >= 2) {
            val coverage = matched.toDouble() / bodies.size
            (perOption.filter { it >= 70 }.average() * coverage).roundToInt()
        } else {
            perOption.maxOrNull() ?: 0
        }
        return OptionEvidence(score.coerceIn(0, 100), matched)
    }

    private fun scoreAnswer(query: String, question: Question): Int {
        val answer = question.answer.trim()
        if (answer.isBlank()) return 0
        val clauses = answer.split(Regex("[\\n；;。]+|(?=\\d{1,2}[、.)）])"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        return (clauses + answer).maxOfOrNull { textSimilarity(query, it) } ?: 0
    }

    private fun extractOptions(text: String): List<String> {
        val matches = optionMarker.findAll(text).toList()
        if (matches.size < 2) return emptyList()
        return matches.mapIndexedNotNull { index, match ->
            val start = match.range.last + 1
            val end = if (index + 1 < matches.size) matches[index + 1].range.first else text.length
            text.substring(start, end).trim().takeIf { it.isNotBlank() }
        }
    }

    private fun resolvedCorrectOptionBodies(question: Question): List<String> {
        val answer = question.answer.trim()
        if (answer.isBlank() || !answerLetters.matches(answer)) return emptyList()
        val labels = Regex("(?i)[A-H]").findAll(answer).map { it.value.uppercase(Locale.ROOT) }.distinct().toList()
        if (labels.isEmpty()) return emptyList()

        val byLabel = LinkedHashMap<String, String>()
        question.options.forEachIndexed { index, option ->
            val match = optionLabel.find(option)
            val label = match?.groupValues?.get(1)?.uppercase(Locale.ROOT)
                ?: ('A'.code + index).toChar().toString()
            val body = match?.groupValues?.get(2)?.trim() ?: option.trim()
            if (body.isNotBlank()) byLabel[label] = body
        }
        return labels.mapNotNull(byLabel::get)
    }

    private fun stripOptionLabel(text: String): String =
        optionLabel.find(text)?.groupValues?.get(2)?.trim() ?: text.trim()

    /** 基础文本相关度，返回 0..100。 */
    private fun textSimilarity(queryText: String, candidateText: String): Int {
        val query = normalize(queryText)
        val candidate = normalize(candidateText)
        if (query.length < 2 || candidate.length < 2) return 0
        if (query == candidate) return 100

        if (candidate.contains(query)) {
            val coverage = query.length.toDouble() / candidate.length
            if (query.length < MIN_TEXT_LENGTH || (query.length < 6 && coverage < 0.45)) return 0
            return (78 + coverage * 22).roundToInt().coerceAtMost(99)
        }
        if (query.contains(candidate)) {
            val coverage = candidate.length.toDouble() / query.length
            if (candidate.length < 6 || coverage < 0.35) return 0
            return (70 + coverage * 30).roundToInt().coerceAtMost(98)
        }

        val dice = diceCoefficient(query, candidate)
        val contiguous = longestCommonSubstring(query, candidate).toDouble() / minOf(query.length, candidate.length)
        val similarity = dice * 0.75 + contiguous * 0.25
        val required = when {
            minOf(query.length, candidate.length) <= 5 -> 0.82
            minOf(query.length, candidate.length) <= 10 -> 0.62
            else -> 0.43
        }
        if (similarity < required) return 0
        return (similarity * 100).roundToInt().coerceIn(1, 99)
    }

    private fun diceCoefficient(a: String, b: String): Double {
        if (a.length < 2 || b.length < 2) return 0.0
        val pairs = HashMap<String, Int>()
        for (i in 0 until a.length - 1) {
            val pair = a.substring(i, i + 2)
            pairs[pair] = (pairs[pair] ?: 0) + 1
        }
        var intersection = 0
        for (i in 0 until b.length - 1) {
            val pair = b.substring(i, i + 2)
            val count = pairs[pair] ?: 0
            if (count > 0) {
                intersection++
                pairs[pair] = count - 1
            }
        }
        return 2.0 * intersection / ((a.length - 1) + (b.length - 1))
    }

    private fun longestCommonSubstring(a: String, b: String): Int {
        val previous = IntArray(b.length + 1)
        val current = IntArray(b.length + 1)
        var best = 0
        for (i in 1..a.length) {
            java.util.Arrays.fill(current, 0)
            for (j in 1..b.length) {
                if (a[i - 1] == b[j - 1]) {
                    current[j] = previous[j - 1] + 1
                    if (current[j] > best) best = current[j]
                }
            }
            System.arraycopy(current, 0, previous, 0, current.size)
        }
        return best
    }
}
