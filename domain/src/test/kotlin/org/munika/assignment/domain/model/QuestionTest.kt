package org.munika.assignment.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuestionTest {
    @Test
    fun `question handling can change without changing question identity`() {
        val question = Question(
            id = QuestionId("question"),
            questionText = "Question: Do you need visa support?",
            handling = StandardQuestionHandlingTypes.VisaSupport,
            rules = listOf(NoticeWhenNonBlank("Visa support requested.")),
        )

        val reconfigured = Question(
            id = question.id,
            questionText = question.questionText,
            handling = StandardQuestionHandlingTypes.Default,
        )

        assertEquals(question.id, reconfigured.id)
        assertEquals(StandardQuestionHandlingTypes.Default, reconfigured.handling)
    }

    @Test
    fun `committee choice explanation can produce warning issue`() {
        val question = Question(
            id = QuestionId("question"),
            questionText = "Why did you choose this committee?",
            handling = StandardQuestionHandlingTypes.CommitteeChoiceExplanation,
            rules = listOf(MinimumSentenceCount(3)),
        )

        val issues = question.issuesFor("Because I like it.")

        assertEquals(listOf(AnswerIssue("Expected at least 3 sentences.", AnswerIssueSeverity.Warning)), issues)
    }

    @Test
    fun `visa support can produce notice issue`() {
        val question = Question(
            id = QuestionId("question"),
            questionText = "Do you need visa support?",
            handling = StandardQuestionHandlingTypes.VisaSupport,
            rules = listOf(NoticeWhenNonBlank("Visa support requested.")),
        )

        assertTrue(question.issuesFor(null).isEmpty())
        assertEquals(listOf(AnswerIssue("Visa support requested.", AnswerIssueSeverity.Notice)), question.issuesFor("Yes"))
    }
}
