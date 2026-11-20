package org.munika.assignment.domain.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QuestionAnswerValidationTest {
    @Test
    fun `visa support notice is only relevant to paid accepted delegates`() {
        val question = visaQuestion()

        listOf("Accepted", "Assigned", "Checked in", " ACCEPTED ").forEach { status ->
            assertEquals(listOf(visaIssue), answer(question, status, PaymentStatus.Paid).issues, status)
            assertTrue(answer(question, status, PaymentStatus.Unpaid).issues.isEmpty(), status)
        }
        listOf(null, "Submitted", "Rejected", "Withdrawn").forEach { status ->
            PaymentStatus.entries.forEach { payment ->
                assertTrue(answer(question, status, payment).issues.isEmpty(), "$status $payment")
            }
        }
    }

    @Test
    fun `visa filtering keeps unrelated answer validation and raw text`() {
        val customNotice = NoticeWhenAffirmative("Custom follow-up.")
        val question = visaQuestion(listOf(NoticeWhenAffirmative("Visa support requested."), customNotice))
        val answer = answer(question, "Accepted", PaymentStatus.Unpaid)

        assertEquals("Yes", answer.rawAnswer)
        assertEquals(listOf(AnswerIssue("Custom follow-up.", AnswerIssueSeverity.Notice)), answer.issues)
        val defaultQuestion = Question(QuestionId("default"), "Other question", rules = question.rules)
        assertEquals(question.issuesFor("Yes"), answer(defaultQuestion, "Submitted", PaymentStatus.Unpaid).issues)
    }

    @Test
    fun `committee explanation length warning is only relevant before acceptance`() {
        val question = explanationQuestion()

        listOf("Accepted", "Assigned", "Checked in", " ACCEPTED ").forEach { status ->
            PaymentStatus.entries.forEach { payment ->
                assertTrue(answer(question, status, payment, "Short").issues.isEmpty(), "$status $payment")
            }
        }
        listOf(null, "Submitted", "Rejected", "Withdrawn").forEach { status ->
            assertEquals(1, answer(question, status, PaymentStatus.Unpaid, "Short").issues.size, status)
        }
    }

    @Test
    fun `accepted explanation keeps custom rules and sentence rules in other handling`() {
        val question = explanationQuestion(listOf(MinimumSentenceCount(3), RequiredAnswer("Answer required.")))

        assertEquals(
            listOf(AnswerIssue("Answer required.", AnswerIssueSeverity.Warning)),
            answer(question, "Accepted", PaymentStatus.Paid, null).issues,
        )
        val defaultQuestion = Question(QuestionId("default"), "Other question", rules = listOf(MinimumSentenceCount(3)))
        assertEquals(1, answer(defaultQuestion, "Accepted", PaymentStatus.Paid, "Short").issues.size)
    }

    @Test
    fun `visa acknowledgement is hidden while unpaid and restored after payment`() {
        val paid = answer(visaQuestion(), "Accepted", PaymentStatus.Paid)
        acknowledge(paid, visaIssue)
        val unpaid = rebuild(paid, "Accepted", PaymentStatus.Unpaid)
        val paidAgain = rebuild(unpaid, "Accepted", PaymentStatus.Paid)

        assertTrue(unpaid.openIssues.isEmpty())
        assertTrue(unpaid.resolvedIssues.isEmpty())
        assertEquals(1, unpaid.delegate.issueResolutions.size)
        assertTrue(paidAgain.openIssues.isEmpty())
        assertEquals(visaIssue, paidAgain.resolvedIssues.single().issue)
    }

    @Test
    fun `legacy sentence acknowledgement returns when delegate is no longer accepted`() {
        val submitted = answer(explanationQuestion(), "Submitted", PaymentStatus.Unpaid, "Short")
        val legacyIssue = AnswerIssue("Expected at least 3 sentences.", AnswerIssueSeverity.Warning)
        acknowledge(submitted, legacyIssue)
        val accepted = rebuild(submitted, "Accepted", PaymentStatus.Unpaid)
        val submittedAgain = rebuild(accepted, "Submitted", PaymentStatus.Unpaid)

        assertTrue(accepted.resolvedIssues.isEmpty())
        assertEquals(1, accepted.delegate.issueResolutions.size)
        assertTrue(submittedAgain.openIssues.isEmpty())
        assertEquals(legacyIssue.key, submittedAgain.resolvedIssues.single().issue.key)
    }

    private fun visaQuestion(rules: List<AnswerRule> = listOf(NoticeWhenAffirmative("Visa support requested."))): Question =
        Question(QuestionId("visa"), "Visa?", handling = StandardQuestionHandlingTypes.VisaSupport, rules = rules)

    private fun explanationQuestion(rules: List<AnswerRule> = listOf(MinimumSentenceCount(3))): Question =
        Question(
            QuestionId("explanation"),
            "Why this committee?",
            handling = StandardQuestionHandlingTypes.CommitteeChoiceExplanation,
            rules = rules,
        )

    private fun answer(
        question: Question,
        applicationStatus: String?,
        paymentStatus: PaymentStatus,
        rawAnswer: String? = "Yes",
    ): QuestionAnswer {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val delegate = Delegate(
            DelegateId("delegate"),
            conference,
            PersonName("Alex", "Kim"),
            email = null,
            profile = DelegateProfile(null, null, null, null, null, null, null, null, null, applicationStatus),
            paymentStatus = paymentStatus,
            isHeadDelegate = false,
        )
        return QuestionAnswer(QuestionAnswerId("answer"), delegate, question, rawAnswer)
    }

    private fun acknowledge(answer: QuestionAnswer, issue: AnswerIssue) {
        answer.delegate.issueResolutions += AnswerIssueResolution(
            AnswerIssueResolutionId("resolution"),
            answer.delegate,
            answer.question.id,
            issue.key,
            issue.message,
            issue.severity,
            justification = "Handled.",
            resolvedAt = Instant.EPOCH,
        )
    }

    private fun rebuild(previous: QuestionAnswer, status: String, payment: PaymentStatus): QuestionAnswer =
        answer(previous.question, status, payment, previous.rawAnswer).also { rebuilt ->
            rebuilt.delegate.issueResolutions += previous.delegate.issueResolutions.map { it.copy(delegate = rebuilt.delegate) }
        }

    private val visaIssue = AnswerIssue("Visa support requested.", AnswerIssueSeverity.Notice)
}
