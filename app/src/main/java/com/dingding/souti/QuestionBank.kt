package com.dingding.souti

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 搜索结果（含题库名+分数）
 */
data class SearchResult(
    val question: Question,
    val bankName: String,
    val score: Int
)

/**
 * 题库（一个导入文件 = 一个题库）
 */
data class Bank(
    val id: Long,
    val name: String,
    val sourceFile: String,
    val createdAt: Long,
    val type: String // "manual" 或 "ai"
) {
    fun formattedTime(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(createdAt))
}

/**
 * 题目
 */
data class Question(
    val id: Long,
    val bankId: Long,
    val stem: String,
    val options: List<String>,
    val answer: String,
    val source: String
)

/**
 * 题库管理器
 */
class QuestionBank(context: Context) {
    private val prefs = context.getSharedPreferences("souti_bank", Context.MODE_PRIVATE)

    // ===== 题库操作 =====
    fun loadBanks(): List<Bank> {
        val raw = prefs.getString("banks", "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Bank(
                id = o.getLong("id"),
                name = o.getString("name"),
                sourceFile = o.optString("sourceFile", ""),
                createdAt = o.optLong("createdAt", 0),
                type = o.optString("type", "manual")
            )
        }
    }

    fun loadBanksByType(type: String): List<Bank> = loadBanks().filter { it.type == type }

    fun deleteBank(id: Long) {
        saveBanks(loadBanks().filter { it.id != id })
        saveQuestions(loadAllQuestions().filter { it.bankId != id })
    }

    // ===== 题目操作 =====
    fun loadAllQuestions(): List<Question> {
        val raw = prefs.getString("questions", "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Question(
                id = o.getLong("id"),
                bankId = o.optLong("bankId", 0),
                stem = o.getString("stem"),
                options = o.optJSONArray("options")?.let { ja ->
                    (0 until ja.length()).map { ja.getString(it) }
                } ?: emptyList(),
                answer = o.optString("answer", ""),
                source = o.optString("source", "")
            )
        }
    }

    fun loadQuestions(bankId: Long): List<Question> =
        loadAllQuestions().filter { it.bankId == bankId }

    fun questionCount(bankId: Long): Int = loadQuestions(bankId).size

    fun deleteQuestion(id: Long) {
        saveQuestions(loadAllQuestions().filter { it.id != id })
    }

    // ===== 导入（创建题库 + 添加题目）=====
    fun importBank(name: String, sourceFile: String, type: String, questions: List<Question>): Bank {
        val bankId = System.currentTimeMillis()
        val bank = Bank(
            id = bankId,
            name = name,
            sourceFile = sourceFile,
            createdAt = System.currentTimeMillis(),
            type = type
        )
        // 给题目关联 bankId
        val withBankId = questions.map { it.copy(bankId = bankId) }
        // 保存
        saveBanks(loadBanks() + bank)
        saveQuestions(loadAllQuestions() + withBankId)
        return bank
    }

    private fun saveBanks(list: List<Bank>) {
        val arr = JSONArray()
        list.forEach { b ->
            arr.put(JSONObject().apply {
                put("id", b.id)
                put("name", b.name)
                put("sourceFile", b.sourceFile)
                put("createdAt", b.createdAt)
                put("type", b.type)
            })
        }
        prefs.edit().putString("banks", arr.toString()).apply()
    }

    private fun saveQuestions(list: List<Question>) {
        val arr = JSONArray()
        list.forEach { q ->
            val opts = JSONArray()
            q.options.forEach { opts.put(it) }
            arr.put(JSONObject().apply {
                put("id", q.id)
                put("bankId", q.bankId)
                put("stem", q.stem)
                put("options", opts)
                put("answer", q.answer)
                put("source", q.source)
            })
        }
        prefs.edit().putString("questions", arr.toString()).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    // ===== 激活题库（被勾选用于搜题的）=====
    fun getActiveBankIds(): Set<String> =
        prefs.getStringSet("activeBankIds", emptySet()) ?: emptySet()

    fun setActiveBankIds(ids: Set<String>) {
        // 必须新建 set 再存（SharedPreferences 内部用同一对象，不重建会保存失败）
        prefs.edit().putStringSet("activeBankIds", HashSet(ids)).apply()
    }

    fun toggleActive(bankId: Long) {
        val cur = getActiveBankIds().toMutableSet()
        val key = bankId.toString()
        if (cur.contains(key)) cur.remove(key) else cur.add(key)
        setActiveBankIds(cur)
    }

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
            .map { it to scoreMatch(q, it.stem) }
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

    /**
     * 最长公共子串长度（Longest Common Substring）
     * OCR 截屏可能比题库题干短（被截断），用 LCS 找最长匹配段
     */
    private fun lcsLen(a: String, b: String): Int {
        if (a.isEmpty() || b.isEmpty()) return 0
        val m = a.length
        val n = b.length
        var maxLen = 0
        // O(m*n) DP 找最长公共子串（只保留上一行，节省内存）
        val prev = IntArray(n + 1)
        val curr = IntArray(n + 1)
        for (i in 1..m) {
            for (j in 1..n) {
                curr[j] = if (a[i - 1] == b[j - 1]) {
                    (if (j > 1) prev[j - 1] else 0) + 1
                } else 0
                if (curr[j] > maxLen) maxLen = curr[j]
            }
            // 滚动：curr → prev
            for (j in 0..n) prev[j] = curr[j]
        }
        return maxLen
    }

    /**
     * 匹配打分：基于最长公共子串（OCR 截屏可能比题库短，contains 失效）
     * - 完全包含 +100 分
     * - LCS 长度加分（每字符 +1）
     * - LCS 太短（< 3）视为不匹配
     */
    private fun scoreMatch(query: String, stem: String): Int {
        if (query.isBlank() || stem.isBlank()) return 0
        var score = 0
        // 完全包含 → 满分
        if (query.length >= 4 && (stem.contains(query) || query.contains(stem))) score += 100
        // LCS 加分
        val lcs = lcsLen(query, stem)
        if (lcs >= 3) score += lcs
        return score
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
