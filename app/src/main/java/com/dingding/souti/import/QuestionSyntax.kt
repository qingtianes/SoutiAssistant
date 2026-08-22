package com.dingding.souti.import

/** 题号、选项和答案标签的统一语法，避免各格式解析器规则漂移。 */
object QuestionSyntax {
    private const val ANSWER_NAMES = "答案|正确答案|参考答案|标准答案|答案为|正确选项"

    val questionStart = Regex("^\\s*(\\d{1,5})\\s*[.、．:：)）]\\s*(.+)$")
    val optionLine = Regex("^\\s*([A-Ha-h])\\s*[.、．:：)）]\\s*(.*)$")
    val strongNumberedQuestion = Regex(
        "^\\s*\\d{1,5}\\s*[.、．:：)）]\\s*[（(]\\s*\\d{3,}\\s*[）)]\\s*\\S+"
    )

    private val bracketedAnswer = Regex(
        "^\\s*[【\\[]\\s*($ANSWER_NAMES)\\s*[】\\]]\\s*[:：]?\\s*(.*)$",
        RegexOption.IGNORE_CASE
    )
    private val plainAnswer = Regex(
        "^\\s*($ANSWER_NAMES)(?=\\s*[:：]|\\s+|$)\\s*[:：]?\\s*(.*)$",
        RegexOption.IGNORE_CASE
    )

    /** 返回答案标签后的正文；返回 null 表示这一行不是答案起始行。 */
    fun answerPayload(line: String): String? {
        bracketedAnswer.find(line)?.let { return it.groupValues[2].trim() }
        plainAnswer.find(line)?.let { return it.groupValues[2].trim() }
        return null
    }

    fun isAnswerStart(line: String): Boolean = answerPayload(line) != null
}
