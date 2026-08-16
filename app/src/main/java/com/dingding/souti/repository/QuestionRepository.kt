package com.dingding.souti.repository

import com.dingding.souti.model.Bank
import com.dingding.souti.model.Question

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 题库存取：只负责把题库、题目、启用状态读写到 SharedPreferences。
 * 不参与 OCR、匹配或界面逻辑。
 */
class QuestionRepository(context: Context) {
    private val prefs = context.getSharedPreferences("souti_bank", Context.MODE_PRIVATE)

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
                sourceModifiedAt = o.optLong("sourceModifiedAt", 0),
                type = o.optString("type", "manual")
            )
        }
    }

    private fun saveBanks(list: List<Bank>) {
        val arr = JSONArray()
        list.forEach { b ->
            arr.put(JSONObject().apply {
                put("id", b.id)
                put("name", b.name)
                put("sourceFile", b.sourceFile)
                put("createdAt", b.createdAt)
                put("sourceModifiedAt", b.sourceModifiedAt)
                put("type", b.type)
            })
        }
        prefs.edit().putString("banks", arr.toString()).apply()
    }

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

    fun deleteBank(id: Long) {
        saveBanks(loadBanks().filter { it.id != id })
        saveQuestions(loadAllQuestions().filter { it.bankId != id })
    }

    fun loadQuestions(bankId: Long): List<Question> =
        loadAllQuestions().filter { it.bankId == bankId }

    fun questionCount(bankId: Long): Int = loadQuestions(bankId).size

    fun deleteQuestion(id: Long) {
        saveQuestions(loadAllQuestions().filter { it.id != id })
    }

    fun importBank(name: String, sourceFile: String, sourceModifiedAt: Long = 0, type: String, questions: List<Question>): Bank {
        val bankId = System.currentTimeMillis()
        val bank = Bank(
            id = bankId,
            name = name,
            sourceFile = sourceFile,
            createdAt = bankId,
            sourceModifiedAt = sourceModifiedAt,
            type = type
        )
        val withBankId = questions.map { it.copy(bankId = bankId) }
        saveBanks(loadBanks() + bank)
        saveQuestions(loadAllQuestions() + withBankId)
        return bank
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun getActiveBankIds(): Set<String> =
        prefs.getStringSet("activeBankIds", emptySet()) ?: emptySet()

    fun setActiveBankIds(ids: Set<String>) {
        prefs.edit().putStringSet("activeBankIds", HashSet(ids)).apply()
    }

    fun toggleActive(bankId: Long) {
        val cur = getActiveBankIds().toMutableSet()
        val key = bankId.toString()
        if (cur.contains(key)) cur.remove(key) else cur.add(key)
        setActiveBankIds(cur)
    }
}
