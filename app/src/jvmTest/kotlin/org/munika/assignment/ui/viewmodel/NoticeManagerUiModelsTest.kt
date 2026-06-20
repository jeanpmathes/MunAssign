package org.munika.assignment.ui.viewmodel

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import org.munika.assignment.domain.model.AnswerIssue
import org.munika.assignment.domain.model.AnswerIssueResolution
import org.munika.assignment.domain.model.AnswerIssueResolutionId
import org.munika.assignment.domain.model.AnswerIssueSeverity
import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.ConferenceId
import org.munika.assignment.domain.model.Delegate
import org.munika.assignment.domain.model.DelegateId
import org.munika.assignment.domain.model.DelegateProfile
import org.munika.assignment.domain.model.HighSchoolerAnswerMapping
import org.munika.assignment.domain.model.HighSchoolerStatus
import org.munika.assignment.domain.model.NoticeWhenNonBlank
import org.munika.assignment.domain.model.PaymentStatus
import org.munika.assignment.domain.model.PersonName
import org.munika.assignment.domain.model.Question
import org.munika.assignment.domain.model.QuestionAnswer
import org.munika.assignment.domain.model.QuestionAnswerId
import org.munika.assignment.domain.model.QuestionId
import org.munika.assignment.domain.model.StandardQuestionHandlingTypes
import org.munika.assignment.domain.model.StandardQuestionRoles
import org.munika.assignment.domain.model.TypedAnswerMappings

class NoticeManagerUiModelsTest {
    @Test
    fun `notice manager counts open issues and orders resolved last`() {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val delegate = Delegate(
            id = DelegateId("delegate"),
            conference = conference,
            name = PersonName("Alex", "Kim"),
            email = "alex@example.test",
            profile = DelegateProfile(null, null, null, null, null, null, null, null, null, null),
            paymentStatus = PaymentStatus.Paid,
            isHeadDelegate = false,
        )
        val firstQuestion = Question(
            id = QuestionId("first"),
            questionText = "First",
            rules = listOf(NoticeWhenNonBlank("First notice")),
        )
        val secondQuestion = Question(
            id = QuestionId("second"),
            questionText = "Second",
            rules = listOf(NoticeWhenNonBlank("Second notice")),
        )
        val firstAnswer = QuestionAnswer(QuestionAnswerId("first-answer"), delegate, firstQuestion, "Yes")
        val secondAnswer = QuestionAnswer(QuestionAnswerId("second-answer"), delegate, secondQuestion, "Yes")
        val firstIssue = AnswerIssue("First notice", AnswerIssueSeverity.Notice)
        conference.delegates += delegate
        conference.questions += listOf(firstQuestion, secondQuestion)
        delegate.answers += listOf(firstAnswer, secondAnswer)
        delegate.issueResolutions += AnswerIssueResolution(
            id = AnswerIssueResolutionId("resolution"),
            delegate = delegate,
            questionId = firstQuestion.id,
            issueKey = firstIssue.key,
            issueMessage = firstIssue.message,
            severity = firstIssue.severity,
            justification = "Done",
            resolvedAt = Instant.EPOCH,
        )

        val state = conference.toNoticeManagerUiState()

        assertEquals(1, state.openNoticeCount)
        assertEquals(0, state.openWarningCount)
        assertEquals(listOf(false, true), state.notices.map { it.resolved })
        assertEquals(listOf("Second notice", "First notice"), state.notices.map { it.message })
        assertEquals("Done", state.notices.last().justification)

        val scoped = conference.toNoticeManagerUiState(delegateId = "delegate")

        assertEquals("Alex Kim", scoped.filteredDelegateName)
        assertEquals(listOf(false, true), scoped.notices.map { it.resolved })
        assertEquals(listOf("Second notice", "First notice"), scoped.notices.map { it.message })
    }

    @Test
    fun `issue manager rows expose validation labels and labeled text fields`() {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val delegate = Delegate(
            id = DelegateId("delegate"),
            conference = conference,
            name = PersonName("Alex", "Kim"),
            email = "alex@example.test",
            profile = DelegateProfile(null, null, null, null, null, null, null, LocalDate.of(2002, 1, 1), null, null),
            paymentStatus = PaymentStatus.Paid,
            isHeadDelegate = false,
        )
        val highSchoolerQuestion = Question(
            id = QuestionId("school"),
            questionText = "School?",
            role = StandardQuestionRoles.HighSchoolerStatus,
            handling = StandardQuestionHandlingTypes.ExpectedTypedAnswer,
            mappings = TypedAnswerMappings(highSchooler = listOf(HighSchoolerAnswerMapping("Yes", HighSchoolerStatus.Yes))),
        )
        val answerQuestion = Question(
            id = QuestionId("visa"),
            questionText = "Question: Visa support?",
            organizerLabel = "Visa",
            rules = listOf(NoticeWhenNonBlank("Visa requested")),
        )
        conference.delegates += delegate
        conference.questions += listOf(highSchoolerQuestion, answerQuestion)
        delegate.answers += QuestionAnswer(QuestionAnswerId("school-answer"), delegate, highSchoolerQuestion, "Yes")
        delegate.answers += QuestionAnswer(QuestionAnswerId("visa-answer"), delegate, answerQuestion, "Yes")

        val rows = conference.toIssueManagerUiState().issues

        val questionRow = rows.single { it.questionId == "visa" }
        assertEquals("Question validation", questionRow.validationLabel)
        assertEquals("Visa", questionRow.questionText)
        assertEquals("Yes", questionRow.answerText)
        assertEquals("Visa requested", questionRow.reasonText)
        assertEquals(null, questionRow.justificationText)
        assertEquals(true, questionRow.delegateIsHighSchooler)

        val profileRow = rows.single { it.questionId == null }
        assertEquals("Profile validation", profileRow.validationLabel)
        assertEquals(null, profileRow.questionText)
        assertEquals(null, profileRow.answerText)
        assertEquals("Delegate is marked as a high schooler but is 24 years old.", profileRow.reasonText)
        assertEquals(true, profileRow.delegateIsHighSchooler)
    }
}
