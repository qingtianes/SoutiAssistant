package com.dingding.souti.import

import com.dingding.souti.model.Question
import com.dingding.souti.repository.QuestionMatcher

/** 导入完成后的纯数据质量检查；只提示风险，不擅自删除源题目。 */
object ImportQualityAnalyzer {
    data class Report(
        val duplicateCount: Int,
        val emptyAnswerCount: Int,
        val suspiciousOptionCount: Int,
        val longStemCount: Int,
        val warnings: List<String>
    )

    fun analyze(questions: List<Question>): Report {
        val fingerprints = HashSet<String>()
        var duplicates = 0
        var emptyAnswers = 0
        var suspiciousOptions = 0
        var longStems = 0

        questions.forEach { question ->
            val fingerprint = QuestionMatcher.contentFingerprint(question)
            if (fingerprint.isNotBlank() && !fingerprints.add(fingerprint)) duplicates++
            if (question.answer.isBlank()) emptyAnswers++
            if (question.options.isNotEmpty() && question.options.size !in 2..8) suspiciousOptions++
            if (question.stem.length > 500) longStems++
        }

        val warnings = buildList {
            if (duplicates > 0) add("检测到 $duplicates 道内容完全重复的题目（保留源文件原貌，未自动删除）")
            if (emptyAnswers > 0) add("有 $emptyAnswers 道题未识别到明确答案；可能是源文件无答案或答案标记格式特殊")
            if (suspiciousOptions > 0) add("有 $suspiciousOptions 道选择题的选项数量异常，请抽查题库格式")
            if (longStems > 0) add("有 $longStems 道题干异常偏长，可能发生了相邻题目合并")
        }
        return Report(duplicates, emptyAnswers, suspiciousOptions, longStems, warnings)
    }
}
