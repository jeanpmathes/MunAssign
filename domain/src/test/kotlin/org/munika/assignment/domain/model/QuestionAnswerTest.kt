package org.munika.assignment.domain.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuestionAnswerTest {
    @Test
    fun `paid visa and unpaid default handling retain the support notice`() {
        val visa = visaQuestion()
        val default = Question(
            id = visa.id,
            questionText = visa.questionText,
            rules = visa.rules,
        )
        val expected = listOf(AnswerIssue("Visa support requested.", AnswerIssueSeverity.Notice))

        assertEquals(expected, answer(PaymentStatus.Paid, visa).openIssues)
        assertEquals(expected, answer(PaymentStatus.Unpaid, default).openIssues)
    }

    @Test
    fun `unpaid visa suppression preserves stored acknowledgement for later payment`() {
        val question = visaQuestion()
        for (paymentStatus in PaymentStatus.entries) {
            val answer = answer(paymentStatus, question)
            val resolution = AnswerIssueResolution(
                id = AnswerIssueResolutionId("resolution"),
                delegate = answer.delegate,
                questionId = question.id,
                issueKey = "Notice:Visa support requested.",
                issueMessage = "Visa support requested.",
                severity = AnswerIssueSeverity.Notice,
                justification = "Invitation sent.",
                resolvedAt = Instant.EPOCH,
            )
            answer.delegate.issueResolutions += resolution

            assertTrue(answer.openIssues.isEmpty())
            assertEquals(listOf(resolution), answer.delegate.issueResolutions)
            assertEquals("Yes", answer.rawAnswer)
            if (paymentStatus == PaymentStatus.Unpaid) {
                assertTrue(answer.issues.isEmpty())
                assertTrue(answer.resolvedIssues.isEmpty())
            } else {
                assertEquals(resolution, answer.resolvedIssues.single().resolution)
            }
        }
    }

    @Test
    fun `unpaid visa answer suppresses only the standard support notice`() {
        val question = Question(
            id = QuestionId("visa"),
            questionText = "Visa support?",
            handling = StandardQuestionHandlingTypes.VisaSupport,
            rules = listOf(
                NoticeWhenAffirmative("Visa support requested."),
                MinimumTextLength(20),
                NoticeWhenNonBlank("Contact organizer."),
            ),
        )
        val answer = answer(PaymentStatus.Unpaid, question)

        assertEquals(
            listOf(
                AnswerIssue("Expected at least 20 characters.", AnswerIssueSeverity.Warning),
                AnswerIssue("Contact organizer.", AnswerIssueSeverity.Notice),
            ),
            answer.issues,
        )
        assertEquals(answer.issues, answer.openIssues)
        assertEquals("Yes", answer.rawAnswer)
    }

    private fun answer(paymentStatus: PaymentStatus, question: Question): QuestionAnswer {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val delegate = Delegate(
            id = DelegateId("delegate"),
            conference = conference,
            name = PersonName("Alex", "Kim"),
            email = null,
            profile = DelegateProfile(null, null, null, null, null, null, null, null, null, "Accepted"),
            paymentStatus = paymentStatus,
            isHeadDelegate = false,
        )
        return QuestionAnswer(QuestionAnswerId("answer"), delegate, question, "Yes")
    }

    private fun visaQuestion(): Question = Question(
        id = QuestionId("visa"),
        questionText = "Visa support?",
        handling = StandardQuestionHandlingTypes.VisaSupport,
        rules = listOf(NoticeWhenAffirmative("Visa support requested.")),
    )
}
