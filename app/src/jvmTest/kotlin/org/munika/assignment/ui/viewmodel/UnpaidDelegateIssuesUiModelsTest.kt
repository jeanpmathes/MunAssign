package org.munika.assignment.ui.viewmodel

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.munika.assignment.domain.model.AnswerIssueResolution
import org.munika.assignment.domain.model.AnswerIssueResolutionId
import org.munika.assignment.domain.model.AnswerIssueSeverity
import org.munika.assignment.domain.model.AssignmentSource
import org.munika.assignment.domain.model.Committee
import org.munika.assignment.domain.model.CommitteeId
import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.ConferenceId
import org.munika.assignment.domain.model.Delegate
import org.munika.assignment.domain.model.DelegateId
import org.munika.assignment.domain.model.DelegateProfile
import org.munika.assignment.domain.model.NoticeWhenAffirmative
import org.munika.assignment.domain.model.PaymentStatus
import org.munika.assignment.domain.model.PersonName
import org.munika.assignment.domain.model.Position
import org.munika.assignment.domain.model.PositionId
import org.munika.assignment.domain.model.Question
import org.munika.assignment.domain.model.QuestionAnswer
import org.munika.assignment.domain.model.QuestionAnswerId
import org.munika.assignment.domain.model.QuestionId
import org.munika.assignment.domain.model.Slot
import org.munika.assignment.domain.model.SlotId
import org.munika.assignment.domain.model.StandardQuestionHandlingTypes

class UnpaidDelegateIssuesUiModelsTest {
    @Test
    fun `issue manager warns for unpaid occupant instead of visa support`() {
        val delegate = unpaidVisaDelegate()
        val state = delegate.conference.toIssueManagerUiState()

        assertEquals(0, state.openNoticeCount)
        assertEquals(1, state.openWarningCount)
        val row = state.issues.single()
        assertEquals("Warning:unpaid_delegate_assigned", row.issueKey)
        assertEquals("Unpaid delegate has an assignment.", row.reasonText)
        assertEquals("Assignment validation", row.validationLabel)
        assertEquals(null, row.questionId)
        assertTrue(row.canResolve)
    }

    @Test
    fun `unpaid visa answer remains visible without contributing to question or delegate counts`() {
        val delegate = unpaidVisaDelegate()
        val conference = delegate.conference
        val detail = assertNotNull(conference.toDelegateDetailUiState(delegate.id.value))

        assertEquals("Yes", detail.answers.single().answer)
        assertTrue(detail.answers.single().issues.isEmpty())
        assertEquals(0, detail.answerNoticeCount)
        assertEquals("Warning:unpaid_delegate_assigned", detail.unpaidAssignmentIssues.single().issueKey)
        val summary = conference.toQuestionConfigurationListUiState().questions.single()
        assertEquals(0, summary.warningCount)
        assertEquals(0, summary.noticeCount)
        val row = conference.toDelegateListUiState(
            DelegateListControlsUiState(includeUnpaid = true, issuesOnly = true),
        ).rows.single()
        assertEquals(1, row.warningCount)
        assertEquals(0, row.noticeCount)
        assertEquals(
            delegate.id.value,
            conference.toAssignmentBoardUiState().committees.single().positions.single().slots.single().delegateId,
        )
    }

    @Test
    fun `acknowledgements hide unpaid visa entries and resolve unpaid assignment across views`() {
        val delegate = unpaidVisaDelegate()
        val visa = delegate.answers.single().question
        delegate.issueResolutions += listOf(
            AnswerIssueResolution(
                id = AnswerIssueResolutionId("visa-resolution"),
                delegate = delegate,
                questionId = visa.id,
                issueKey = "Notice:Visa support requested.",
                issueMessage = "Visa support requested.",
                severity = AnswerIssueSeverity.Notice,
                justification = "Handled.",
                resolvedAt = Instant.EPOCH,
            ),
            AnswerIssueResolution(
                id = AnswerIssueResolutionId("assignment-resolution"),
                delegate = delegate,
                questionId = null,
                issueKey = "Warning:unpaid_delegate_assigned",
                issueMessage = "Unpaid delegate has an assignment.",
                severity = AnswerIssueSeverity.Warning,
                justification = "Handled.",
                resolvedAt = Instant.EPOCH,
            ),
        )

        val manager = delegate.conference.toIssueManagerUiState()
        assertEquals(0, manager.openWarningCount)
        assertEquals(0, manager.openNoticeCount)
        assertTrue(manager.issues.single().resolved)
        assertEquals("Warning:unpaid_delegate_assigned", manager.issues.single().issueKey)
        val detail = assertNotNull(delegate.conference.toDelegateDetailUiState(delegate.id.value))
        assertTrue(detail.unpaidAssignmentIssues.isEmpty())
        assertEquals("Handled.", detail.resolvedUnpaidAssignmentIssues.single().justification)
        assertTrue(detail.answers.single().resolvedIssues.isEmpty())
        assertEquals(0, detail.resolvedAnswerIssueCount)
        assertEquals(2, detail.comments.count { it.readOnly })
        assertEquals(2, delegate.issueResolutions.size)
        assertTrue(
            delegate.conference.toDelegateListUiState(
                DelegateListControlsUiState(includeUnpaid = true, issuesOnly = true),
            ).rows.isEmpty(),
        )
    }

    private fun unpaidVisaDelegate(): Delegate {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val delegate = Delegate(
            id = DelegateId("delegate"),
            conference = conference,
            name = PersonName("Alex", "Kim"),
            email = null,
            profile = DelegateProfile(null, null, null, null, null, null, null, null, null, "Assigned"),
            paymentStatus = PaymentStatus.Unpaid,
            isHeadDelegate = false,
        )
        conference.delegates += delegate
        val committee = Committee(CommitteeId("committee"), conference, "GA", slotsPerPosition = 1)
        conference.committees += committee
        val position = Position(PositionId("position"), committee, "France")
        committee.positions += position
        val slot = Slot(SlotId("slot"), position, 1, Instant.EPOCH)
        position.slots += slot
        slot.assign(delegate, AssignmentSource.Mymun, Instant.EPOCH)
        val question = Question(
            id = QuestionId("visa"),
            questionText = "Visa support?",
            handling = StandardQuestionHandlingTypes.VisaSupport,
            rules = listOf(NoticeWhenAffirmative("Visa support requested.")),
        )
        conference.questions += question
        delegate.answers += QuestionAnswer(QuestionAnswerId("answer"), delegate, question, "Yes")
        return delegate
    }
}
