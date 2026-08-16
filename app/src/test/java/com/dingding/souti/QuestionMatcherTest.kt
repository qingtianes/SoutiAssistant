package com.dingding.souti

import com.dingding.souti.repository.QuestionMatcher

import org.junit.Assert.assertEquals
import org.junit.Test

class QuestionMatcherTest {

    @Test
    fun `blank input scores zero`() {
        assertEquals(0, QuestionMatcher.score("", ""))
        assertEquals(0, QuestionMatcher.score("   ", "题目"))
        assertEquals(0, QuestionMatcher.score("题目", " \n\t "))
    }

    @Test
    fun `brackets and whitespace are ignored during matching`() {
        assertEquals(
            QuestionMatcher.score("下列是线型橡胶", "下列是线型橡胶"),
            QuestionMatcher.score("下列（ ）是线型橡胶", "下列是 线型橡胶")
        )
    }

    @Test
    fun `full containment adds one hundred points`() {
        val score = QuestionMatcher.score("下列是线型橡胶", "下列是线型橡胶的主要性能")
        assertEquals(true, score >= 100)
    }

    @Test
    fun `common substring shorter than three characters scores zero`() {
        assertEquals(0, QuestionMatcher.score("甲乙", "甲乙丙丁"))
    }

    @Test
    fun `common substring of three or more characters scores its length`() {
        assertEquals(3, QuestionMatcher.score("甲乙丙", "甲乙丙丁"))
    }

    @Test
    fun `overlapping common substring returns the longest length`() {
        assertEquals(4, QuestionMatcher.score("ababcd", "zababw"))
    }
}
