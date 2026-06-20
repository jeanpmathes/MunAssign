package org.munika.assignment.domain.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class QuestionTest {
    @Test
    fun `role-backed question requires expected typed-answer handling`() {
        assertFailsWith<IllegalArgumentException> {
            Question(
                id = QuestionId("question"),
                questionText = "Are you a high-schooler and do you need visa support?",
                role = StandardQuestionRoles.HighSchoolerStatus,
                handling = StandardQuestionHandlingTypes.VisaSupport,
            )
        }
    }

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
            rules = listOf(NoticeWhenAffirmative("Visa support requested.")),
        )

        assertTrue(question.issuesFor(null).isEmpty())
        assertTrue(question.issuesFor("   ").isEmpty())
        assertTrue(question.issuesFor("-").isEmpty())
        assertTrue(question.issuesFor("No").isEmpty())
        assertEquals(listOf(AnswerIssue("Visa support requested.", AnswerIssueSeverity.Notice)), question.issuesFor("Yes"))
    }

    @Test
    fun `answer issue key is stable for matching severity and message`() {
        val first = AnswerIssue(" Visa support requested. ", AnswerIssueSeverity.Notice)
        val second = AnswerIssue("Visa support requested.", AnswerIssueSeverity.Notice)

        assertEquals(second.key, first.key)
        assertEquals("Notice:Visa support requested.", first.key)
    }

    @Test
    fun `question answer separates open and resolved issues`() {
        val conference = Conference(
            id = ConferenceId("conference"),
            name = "KAMUN",
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        val delegate = Delegate(
            id = DelegateId("delegate"),
            conference = conference,
            name = PersonName("Alex", "Kim"),
            email = null,
            profile = DelegateProfile(null, null, null, null, null, null, null, null, null, null),
            paymentStatus = PaymentStatus.Paid,
            isHeadDelegate = false,
        )
        val question = Question(
            id = QuestionId("visa"),
            questionText = "Visa?",
            rules = listOf(NoticeWhenNonBlank("Visa support requested.")),
        )
        val answer = QuestionAnswer(QuestionAnswerId("answer"), delegate, question, "Yes")
        delegate.answers += answer
        delegate.issueResolutions += AnswerIssueResolution(
            id = AnswerIssueResolutionId("resolution"),
            delegate = delegate,
            questionId = question.id,
            issueKey = answer.issues.single().key,
            issueMessage = "Visa support requested.",
            severity = AnswerIssueSeverity.Notice,
            justification = "Handled by visa team.",
            resolvedAt = Instant.EPOCH,
        )

        assertTrue(answer.openIssues.isEmpty())
        assertEquals("Handled by visa team.", answer.resolvedIssues.single().resolution.justification)
    }
}
