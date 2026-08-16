package com.dingding.souti.repository

import android.content.Context
import com.dingding.souti.model.Bank
import com.dingding.souti.model.Question
import com.dingding.souti.model.SearchResult

/**
 * 题库管理器：对上层提供题库读取、导入、启用和搜索；底层存取委托给 QuestionRepository。
 */
class QuestionBank(context: Context) {
    private val repository = QuestionRepository(context)

    fun loadBanks(): List<Bank> = repository.loadBanks()

    fun loadBanksByType(type: String): List<Bank> = loadBanks().filter { it.type == type }

    fun deleteBank(id: Long) = repository.deleteBank(id)

    fun loadAllQuestions(): List<Question> = repository.loadAllQuestions()

    fun loadQuestions(bankId: Long): List<Question> = repository.loadQuestions(bankId)

    fun questionCount(bankId: Long): Int = repository.questionCount(bankId)

    fun deleteQuestion(id: Long) = repository.deleteQuestion(id)

    fun importBank(name: String, sourceFile: String, sourceModifiedAt: Long = 0, type: String, questions: List<Question>): Bank =
        repository.importBank(name, sourceFile, sourceModifiedAt, type, questions)

    fun clear() = repository.clear()

    fun getActiveBankIds(): Set<String> = repository.getActiveBankIds()

    fun setActiveBankIds(ids: Set<String>) = repository.setActiveBankIds(ids)

    fun toggleActive(bankId: Long) = repository.toggleActive(bankId)

    /**
     * 搜索：在激活题库的所有题目里找匹配的题干
     * @return 按相关度排序的搜索结果
     */
    fun search(query: String, limit: Int = 5): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        val q = query.trim()
        val activeIds = getActiveBankIds()
        if (activeIds.isEmpty()) return emptyList()
        val banks = loadBanks().associateBy { it.id }
        return loadAllQuestions()
            .filter { activeIds.contains(it.bankId.toString()) }
            .map { it to QuestionMatcher.score(q, it.stem) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { (question, score) ->
                SearchResult(
                    question = question,
                    bankName = banks[question.bankId]?.name ?: "未知题库",
                    score = score
                )
            }
    }

    companion object {
        /**
         * 从 .txt 解析题目（不关联 bankId，importBank 时会赋值）
         * 格式：题干 + A./B./C./D. 选项 + 答案:X + 空行分隔
         */
        fun parseTxt(text: String, source: String): List<Question> {
            val questions = mutableListOf<Question>()
            val blocks = text.split(Regex("\n\\s*\n"))
            var idCounter = System.currentTimeMillis()

            for (block in blocks) {
                val lines = block.trim().lines().filter { it.isNotBlank() }
                if (lines.isEmpty()) continue

                val stem = StringBuilder()
                val options = mutableListOf<String>()
                var answer = ""
                var inStem = true

                for (line in lines) {
                    val trimmed = line.trim()
                    val optionMatch = Regex("^[A-Z][.、)）]\\s*(.+)").find(trimmed)
                    val answerMatch = Regex("^答案[:：]?\\s*(.+)", RegexOption.IGNORE_CASE).find(trimmed)

                    when {
                        answerMatch != null -> { inStem = false; answer = answerMatch.groupValues[1].trim() }
                        optionMatch != null -> { inStem = false; options.add(trimmed) }
                        inStem -> { if (stem.isNotEmpty()) stem.append("\n"); stem.append(trimmed) }
                        else -> { if (stem.isNotEmpty()) stem.append("\n"); stem.append(trimmed) }
                    }
                }

                if (stem.isNotBlank()) {
                    questions.add(Question(
                        id = idCounter++,
                        bankId = 0,
                        stem = stem.toString().trim(),
                        options = options,
                        answer = answer,
                        source = source
                    ))
                }
            }
            return questions
        }
    }
}
