package org.munika.assignment.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MinimumSentenceCountTest {
    @Test
    fun `thirty words satisfy three sentence rule without punctuation`() {
        val rule = MinimumSentenceCount(3)

        assertNotNull(rule.evaluate(List(29) { "word" }.joinToString(" ")))
        assertNull(rule.evaluate(List(30) { "word" }.joinToString(" ")))
    }

    @Test
    fun `short answers still pass with three sentence fragments`() {
        val rule = MinimumSentenceCount(3)

        assertNotNull(rule.evaluate("One. Two."))
        assertNull(rule.evaluate("One! Two? Three"))
    }

    @Test
    fun `only words count toward punctuation alternative`() {
        val rule = MinimumSentenceCount(3)

        assertNotNull(rule.evaluate(List(29) { "word" }.joinToString(" ") + " -- / :"))
        assertNull(rule.evaluate(List(30) { "word" }.joinToString("\t\n")))
        assertNotNull(rule.evaluate(null))
        assertNotNull(rule.evaluate(" \t\n"))
    }

    @Test
    fun `word alternative scales with configured sentence count and preserves acknowledgement key`() {
        val rule = MinimumSentenceCount(2)

        assertNotNull(rule.evaluate(List(19) { "word" }.joinToString(" ")))
        assertNull(rule.evaluate(List(20) { "word" }.joinToString(" ")))
        val issue = assertNotNull(rule.evaluate("Short"))
        assertEquals("Expected at least 2 sentences or 20 words.", issue.message)
        assertEquals("Warning:Expected at least 2 sentences.", issue.key)
    }
}
