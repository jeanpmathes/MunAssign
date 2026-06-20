package org.munika.assignment.ui.viewmodel

import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.munika.assignment.domain.model.AnswerIssue
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
import org.munika.assignment.domain.model.Delegation
import org.munika.assignment.domain.model.DelegationId
import org.munika.assignment.domain.model.HighSchoolerAnswerMapping
import org.munika.assignment.domain.model.HighSchoolerStatus
import org.munika.assignment.domain.model.NoticeWhenNonBlank
import org.munika.assignment.domain.model.PaymentStatus
import org.munika.assignment.domain.model.PersonName
import org.munika.assignment.domain.model.Position
import org.munika.assignment.domain.model.PositionId
import org.munika.assignment.domain.model.Preference
import org.munika.assignment.domain.model.PreferenceId
import org.munika.assignment.domain.model.Question
import org.munika.assignment.domain.model.QuestionAnswer
import org.munika.assignment.domain.model.QuestionAnswerId
import org.munika.assignment.domain.model.QuestionId
import org.munika.assignment.domain.model.RequiredAnswer
import org.munika.assignment.domain.model.Slot
import org.munika.assignment.domain.model.SlotId
import org.munika.assignment.domain.model.StandardQuestionHandlingTypes
import org.munika.assignment.domain.model.StandardQuestionRoles
import org.munika.assignment.domain.model.TypedAnswerMappings

class DelegateListUiModelsTest {
    @Test
    fun `default list shows active paid delegates sorted by submission date`() {
        val board = board()

        val list = board.conference.toDelegateListUiState(DelegateListControlsUiState(), Now)

        assertEquals(listOf("Easy Delegate", "Contested Delegate", "Assigned Delegate", "Pending Delegate"), list.rows.map { it.name })
        assertEquals("9 days", list.rows.first().applicationAgeLabel)
    }

    @Test
    fun `presets apply expected filters and sorts`() {
        val board = board()

        val assignment = board.conference.toDelegateListUiState(
            DelegateListControlsUiState().applyPreset(DelegateListPreset.Assignment),
            Now,
        )
        val issues = board.conference.toDelegateListUiState(
            DelegateListControlsUiState().applyPreset(DelegateListPreset.Issues),
            Now,
        )
        val assigned = board.conference.toDelegateListUiState(
            DelegateListControlsUiState().applyPreset(DelegateListPreset.Assigned),
            Now,
        )
        val all = board.conference.toDelegateListUiState(
            DelegateListControlsUiState().applyPreset(DelegateListPreset.All),
            Now,
        )

        assertEquals(listOf("Easy Delegate", "Contested Delegate"), assignment.rows.map { it.name })
        assertEquals(listOf("Pending Delegate"), issues.rows.map { it.name })
        assertEquals(listOf("Assigned Delegate", "Pending Delegate"), assigned.rows.map { it.name })
        assertEquals(
            listOf("Inactive Delegate", "Unpaid Delegate", "Easy Delegate", "Contested Delegate", "Assigned Delegate", "Pending Delegate"),
            all.rows.map { it.name },
        )
    }

    @Test
    fun `filters support search status combinations payment active and committee interest`() {
        val board = board()

        val controls = DelegateListControlsUiState(
            searchQuery = "delegate",
            includeUnpaid = true,
            includeDeactivated = true,
            includedAssignmentStatuses = setOf(DelegateListAssignmentStatus.Unassigned),
            committeeId = board.specialCommittee.id.value,
            sort = DelegateListSort.Name,
        )
        val list = board.conference.toDelegateListUiState(controls, Now)

        assertEquals(listOf("Contested Delegate", "Easy Delegate", "Inactive Delegate", "Unpaid Delegate"), list.rows.map { it.name })
    }

    @Test
    fun `filters support high school and university level delegates`() {
        val board = board()
        val rows = board.conference.toDelegateListUiState(
            DelegateListControlsUiState(
                includedSchoolLevels = setOf(DelegateListSchoolLevel.HighSchool),
                sort = DelegateListSort.Name,
            ),
            Now,
        ).rows

        assertEquals(listOf("Contested Delegate"), rows.map { it.name })
        assertEquals(true, rows.single().isHighSchooler)

        val universityRows = board.conference.toDelegateListUiState(
            DelegateListControlsUiState(
                includedSchoolLevels = setOf(DelegateListSchoolLevel.University),
                sort = DelegateListSort.Name,
            ),
            Now,
        ).rows

        assertEquals(listOf("Assigned Delegate", "Easy Delegate", "Pending Delegate"), universityRows.map { it.name })
    }

    @Test
    fun `sorts by name assignment status issues and difficulty`() {
        val board = board()

        assertEquals(
            listOf("Assigned Delegate", "Contested Delegate", "Easy Delegate", "Pending Delegate"),
            board.conference.toDelegateListUiState(DelegateListControlsUiState(sort = DelegateListSort.Name), Now).rows.map { it.name },
        )
        assertEquals(
            listOf("Assigned Delegate", "Pending Delegate", "Contested Delegate", "Easy Delegate"),
            board.conference.toDelegateListUiState(DelegateListControlsUiState(sort = DelegateListSort.AssignmentStatus), Now).rows.map { it.name },
        )
        assertEquals(
            listOf("Pending Delegate", "Assigned Delegate", "Contested Delegate", "Easy Delegate"),
            board.conference.toDelegateListUiState(DelegateListControlsUiState(sort = DelegateListSort.Issues), Now).rows.map { it.name },
        )
        assertEquals(
            listOf("Easy Delegate", "Assigned Delegate", "Contested Delegate", "Pending Delegate"),
            board.conference.toDelegateListUiState(DelegateListControlsUiState(sort = DelegateListSort.Difficulty), Now).rows.map { it.name },
        )
    }

    @Test
    fun `quick navigation is only available for pending and confirmed delegates`() {
        val board = board()

        val rows = board.conference.toDelegateListUiState(DelegateListControlsUiState(), Now).rows.associateBy { it.name }

        assertNull(rows.getValue("Easy Delegate").quickNavigation)
        assertNotNull(rows.getValue("Assigned Delegate").quickNavigation)
        assertEquals("assigned-position", rows.getValue("Assigned Delegate").quickNavigation?.positionId)
        assertNotNull(rows.getValue("Pending Delegate").quickNavigation)
        assertEquals("pending-position", rows.getValue("Pending Delegate").quickNavigation?.positionId)
    }

    private fun board(): Board {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val general = Committee(CommitteeId("general"), conference, "GA", slotsPerPosition = 1)
        val special = Committee(CommitteeId("special"), conference, "Special", slotsPerPosition = 1)
        val easyPosition = Position(PositionId("easy-position"), general, "Easy")
        val contestedPosition = Position(PositionId("contested-position"), general, "Contested")
        val assignedPosition = Position(PositionId("assigned-position"), special, "Assigned")
        val pendingPosition = Position(PositionId("pending-position"), special, "Pending")
        val fullPosition = Position(PositionId("full-position"), special, "Full")
        conference.committees += listOf(general, special)
        general.positions += listOf(easyPosition, contestedPosition)
        special.positions += listOf(assignedPosition, pendingPosition, fullPosition)
        listOf(easyPosition, contestedPosition, assignedPosition, pendingPosition, fullPosition).forEach { position ->
            position.slots += Slot(SlotId("${position.id.value}-slot"), position, 1, Instant.EPOCH)
        }

        val easy = delegate(conference, "easy", "Easy", "Delegate", PaymentStatus.Paid, true, submittedDaysAgo = 9)
        val contested = delegate(conference, "contested", "Contested", "Delegate", PaymentStatus.Paid, true, submittedDaysAgo = 8)
        val assigned = delegate(conference, "assigned", "Assigned", "Delegate", PaymentStatus.Paid, true, submittedDaysAgo = 7)
        val pending = delegate(conference, "pending", "Pending", "Delegate", PaymentStatus.Paid, true, submittedDaysAgo = 6)
        val unpaid = delegate(conference, "unpaid", "Unpaid", "Delegate", PaymentStatus.Unpaid, true, submittedDaysAgo = 10)
        val inactive = delegate(conference, "inactive", "Inactive", "Delegate", PaymentStatus.Paid, false, submittedDaysAgo = 11)
        val filler = delegate(conference, "filler", "Filler", "Delegate", PaymentStatus.Paid, true, submittedDaysAgo = 12)
        val delegation = Delegation(DelegationId("delegation"), conference, "Delegation A", "Finland", null, null)
        conference.delegations += delegation
        delegation.addDelegate(easy)
        conference.delegates += listOf(easy, contested, assigned, pending, unpaid, inactive)

        easy.preferences += Preference(PreferenceId("easy-1"), easy, easyPosition, 1, "GA (Easy)")
        easy.preferences += Preference(PreferenceId("easy-2"), easy, fullPosition, 2, "Special (Full)")
        contested.preferences += Preference(PreferenceId("contested-1"), contested, fullPosition, 1, "Special (Full)")
        contested.preferences += Preference(PreferenceId("contested-2"), contested, contestedPosition, 2, "GA (Contested)")
        assigned.preferences += Preference(PreferenceId("assigned-1"), assigned, contestedPosition, 1, "GA (Contested)")
        pending.preferences += Preference(PreferenceId("pending-1"), pending, contestedPosition, 1, "GA (Contested)")
        unpaid.preferences += Preference(PreferenceId("unpaid-1"), unpaid, easyPosition, 1, "GA (Easy)")
        unpaid.preferences += Preference(PreferenceId("unpaid-2"), unpaid, fullPosition, 2, "Special (Full)")
        inactive.preferences += Preference(PreferenceId("inactive-1"), inactive, easyPosition, 1, "GA (Easy)")
        inactive.preferences += Preference(PreferenceId("inactive-2"), inactive, fullPosition, 2, "Special (Full)")

        fullPosition.slots.single().assign(filler, AssignmentSource.Mymun, Instant.EPOCH)
        assignedPosition.slots.single().assign(assigned, AssignmentSource.Mymun, Instant.EPOCH)
        pendingPosition.slots.single().assign(pending, AssignmentSource.Application, Instant.EPOCH)

        val warningQuestion = Question(QuestionId("warning"), "Warning", rules = listOf(RequiredAnswer("Missing")))
        val noticeQuestion = Question(QuestionId("notice"), "Notice", rules = listOf(NoticeWhenNonBlank("Check")))
        val highSchoolerQuestion = Question(
            QuestionId("high-schooler"),
            "High schooler",
            role = StandardQuestionRoles.HighSchoolerStatus,
            handling = StandardQuestionHandlingTypes.ExpectedTypedAnswer,
            mappings = TypedAnswerMappings(highSchooler = listOf(HighSchoolerAnswerMapping("Yes", HighSchoolerStatus.Yes))),
        )
        conference.questions += listOf(warningQuestion, noticeQuestion, highSchoolerQuestion)
        contested.answers += QuestionAnswer(QuestionAnswerId("contested-warning"), contested, warningQuestion, null)
        val contestedWarning = AnswerIssue("Missing", AnswerIssueSeverity.Warning)
        contested.issueResolutions += AnswerIssueResolution(
            id = AnswerIssueResolutionId("contested-warning-resolution"),
            delegate = contested,
            questionId = warningQuestion.id,
            issueKey = contestedWarning.key,
            issueMessage = contestedWarning.message,
            severity = contestedWarning.severity,
            justification = "Done",
            resolvedAt = Instant.EPOCH,
        )
        contested.answers += QuestionAnswer(QuestionAnswerId("contested-high-schooler"), contested, highSchoolerQuestion, "Yes")
        pending.answers += QuestionAnswer(QuestionAnswerId("pending-notice"), pending, noticeQuestion, "yes")

        return Board(conference, special)
    }

    private fun delegate(
        conference: Conference,
        id: String,
        firstName: String,
        lastName: String,
        paymentStatus: PaymentStatus,
        active: Boolean,
        submittedDaysAgo: Long,
    ): Delegate =
        Delegate(
            id = DelegateId(id),
            conference = conference,
            name = PersonName(firstName, lastName),
            email = "$id@example.test",
            profile = DelegateProfile(null, null, null, null, null, null, null, null, null, null),
            paymentStatus = paymentStatus,
            active = active,
            isHeadDelegate = false,
            submittedAt = Now.minus(submittedDaysAgo, ChronoUnit.DAYS),
        )

    private data class Board(
        val conference: Conference,
        val specialCommittee: Committee,
    )

    private companion object {
        val Now: Instant = Instant.parse("2026-05-02T12:00:00Z")
    }
}
