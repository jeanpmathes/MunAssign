package org.munika.assignment.ui.viewmodel

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.munika.assignment.domain.model.AnswerIssueResolution
import org.munika.assignment.domain.model.AnswerIssueResolutionId
import org.munika.assignment.domain.model.AssignmentSource
import org.munika.assignment.domain.model.Committee
import org.munika.assignment.domain.model.CommitteeId
import org.munika.assignment.domain.model.CommitteeMode
import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.ConferenceId
import org.munika.assignment.domain.model.Delegate
import org.munika.assignment.domain.model.DelegateId
import org.munika.assignment.domain.model.DelegateProfile
import org.munika.assignment.domain.model.HighSchoolerAnswerMapping
import org.munika.assignment.domain.model.HighSchoolerStatus
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
import org.munika.assignment.domain.model.Reservation
import org.munika.assignment.domain.model.ReservationDelegateLink
import org.munika.assignment.domain.model.ReservationDelegateLinkId
import org.munika.assignment.domain.model.ReservationId
import org.munika.assignment.domain.model.ReservationLinkSource
import org.munika.assignment.domain.model.Slot
import org.munika.assignment.domain.model.SlotId
import org.munika.assignment.domain.model.StandardQuestionHandlingTypes
import org.munika.assignment.domain.model.StandardQuestionRoles
import org.munika.assignment.domain.model.TypedAnswerMappings

class AssignmentBoardUiModelsTest {
    @Test
    fun `interested delegates hide unpaid and inactive delegates and name confirmed assignments elsewhere`() {
        val board = board()
        board.germanySlot.assign(board.confirmedElsewhere, AssignmentSource.Mymun, Instant.EPOCH)

        val france = board.conference.toAssignmentBoardUiState()
            .committees.single()
            .positions.single { it.name == "France" }

        assertEquals(listOf("Available Delegate", "Confirmed Elsewhere"), france.interestedDelegates.map { it.name })
        assertNull(france.interestedDelegates.single { it.name == "Available Delegate" }.confirmedAssignmentLabel)
        assertEquals("GA (Germany)", france.interestedDelegates.single { it.name == "Confirmed Elsewhere" }.confirmedAssignmentLabel)
    }

    @Test
    fun `slot states map to empty pending and assigned display states`() {
        val board = board(slotCount = 3)
        board.france.slots[1].assign(board.available, AssignmentSource.Application, Instant.EPOCH)
        board.france.slots[2].assign(board.confirmedElsewhere, AssignmentSource.Mymun, Instant.EPOCH)

        val france = board.conference.toAssignmentBoardUiState()
            .committees.single()
            .positions.single { it.name == "France" }

        assertEquals(
            listOf(SlotBoardStatus.Empty, SlotBoardStatus.Pending, SlotBoardStatus.Assigned),
            france.slots.map { it.status },
        )
    }

    @Test
    fun `position state exposes decorated country display name while keeping raw name`() {
        val board = board()

        val france = board.conference.toAssignmentBoardUiState()
            .committees.single()
            .positions.single { it.name == "France" }

        assertEquals("France", france.name)
        assertEquals("France", france.displayName)
        assertEquals("1f1eb-1f1f7", france.flagAssetName)
    }

    @Test
    fun `interested delegates remain visible while a multi slot position has an open slot`() {
        val board = board(slotCount = 2)
        board.france.slots[0].assign(board.available, AssignmentSource.Application, Instant.EPOCH)

        val france = board.conference.toAssignmentBoardUiState()
            .committees.single()
            .positions.single { it.name == "France" }
        val available = france.interestedDelegates.single { it.name == "Available Delegate" }

        assertTrue(france.shouldShowInterestedDelegates)
        assertEquals("GA (France)", available.pendingAssignmentLabel)
        assertEquals("france-1", available.pendingAssignmentSlotId)
        assertNull(available.confirmedAssignmentLabel)
        assertNull(available.confirmedAssignmentSlotId)
    }

    @Test
    fun `interested delegates are hidden when a multi slot position has no open slot`() {
        val board = board(slotCount = 2)
        board.france.slots[0].assign(board.available, AssignmentSource.Application, Instant.EPOCH)
        board.france.slots[1].assign(board.confirmedElsewhere, AssignmentSource.Mymun, Instant.EPOCH)

        val france = board.conference.toAssignmentBoardUiState()
            .committees.single()
            .positions.single { it.name == "France" }
        val confirmed = france.interestedDelegates.single { it.name == "Confirmed Elsewhere" }

        assertFalse(france.shouldShowInterestedDelegates)
        assertEquals("GA (France)", confirmed.confirmedAssignmentLabel)
        assertEquals("france-2", confirmed.confirmedAssignmentSlotId)
        assertFalse(confirmed.canAssign)
    }

    @Test
    fun `assignment candidates include assignable active paid delegates and mark pending elsewhere`() {
        val board = board()
        board.france.slots.single().assign(board.available, AssignmentSource.Application, Instant.EPOCH)
        board.germanySlot.assign(board.confirmedElsewhere, AssignmentSource.Mymun, Instant.EPOCH)

        val germany = board.conference.toAssignmentBoardUiState()
            .committees.single()
            .positions.single { it.name == "Germany" }

        assertEquals(listOf("Neutral Delegate", "Available Delegate"), germany.assignmentCandidates.map { it.name })
        assertEquals("GA (France)", germany.assignmentCandidates.single { it.name == "Available Delegate" }.pendingAssignmentLabel)
    }

    @Test
    fun `submitted delegates are not assignment candidates or interested delegates`() {
        val board = board()
        val submitted = delegate(
            conference = board.conference,
            id = "submitted",
            firstName = "Submitted",
            lastName = "Delegate",
            paymentStatus = PaymentStatus.Paid,
            active = true,
            applicationStatus = "Submitted",
        )
        board.conference.delegates += submitted
        submitted.preferences += Preference(
            id = PreferenceId("preference-submitted"),
            delegate = submitted,
            position = board.france,
            rank = 1,
            rawPreferenceText = "GA (France)",
        )

        val ui = board.conference.toAssignmentBoardUiState().committees.single()
        val france = ui.positions.single { it.name == "France" }
        val germany = ui.positions.single { it.name == "Germany" }

        assertFalse(france.interestedDelegates.any { it.name == "Submitted Delegate" })
        assertFalse(germany.assignmentCandidates.any { it.name == "Submitted Delegate" })
    }

    @Test
    fun `assignment candidates include answer warning and notice counts`() {
        val board = board()
        val warningQuestion = Question(QuestionId("warning"), "Warning", rules = listOf(RequiredAnswer("Missing")))
        val noticeQuestion = Question(QuestionId("notice"), "Notice", rules = listOf(org.munika.assignment.domain.model.NoticeWhenNonBlank("Check")))
        board.conference.questions += listOf(warningQuestion, noticeQuestion)
        board.available.answers += QuestionAnswer(QuestionAnswerId("warning-answer"), board.available, warningQuestion, null)
        board.available.answers += QuestionAnswer(QuestionAnswerId("notice-answer"), board.available, noticeQuestion, "yes")

        val germany = board.conference.toAssignmentBoardUiState()
            .committees.single()
            .positions.single { it.name == "Germany" }
        val candidate = germany.assignmentCandidates.single { it.name == "Available Delegate" }

        assertEquals(1, candidate.warningCount)
        assertEquals(1, candidate.noticeCount)
    }

    @Test
    fun `assignment candidates ignore resolved answer and profile issue counts`() {
        val board = board()
        val warningQuestion = Question(QuestionId("warning"), "Warning", rules = listOf(RequiredAnswer("Missing")))
        board.conference.questions += warningQuestion
        board.available.answers += QuestionAnswer(QuestionAnswerId("warning-answer"), board.available, warningQuestion, null)
        markHighSchooler(board.available)
        board.available.issueResolutions += board.available.answers
            .single { it.id.value == "warning-answer" }
            .issues
            .single()
            .let { issue ->
                AnswerIssueResolution(
                    id = AnswerIssueResolutionId("answer-resolution"),
                    delegate = board.available,
                    questionId = warningQuestion.id,
                    issueKey = issue.key,
                    issueMessage = issue.message,
                    severity = issue.severity,
                    justification = "Reviewed.",
                    resolvedAt = Instant.EPOCH,
                )
            }
        board.available.issueResolutions += board.available.profileIssues().single().let { issue ->
            AnswerIssueResolution(
                id = AnswerIssueResolutionId("profile-resolution"),
                delegate = board.available,
                questionId = null,
                issueKey = issue.key,
                issueMessage = issue.message,
                severity = issue.severity,
                justification = null,
                resolvedAt = Instant.EPOCH,
            )
        }

        val germany = board.conference.toAssignmentBoardUiState()
            .committees.single()
            .positions.single { it.name == "Germany" }
        val candidate = germany.assignmentCandidates.single { it.name == "Available Delegate" }

        assertEquals(0, candidate.warningCount)
        assertEquals(0, candidate.noticeCount)
    }

    @Test
    fun `committee state exposes high school mismatch counts`() {
        val board = board()
        board.germanySlot.assign(board.available, AssignmentSource.Application, Instant.EPOCH)
        markHighSchooler(board.available)

        val committee = board.conference.toAssignmentBoardUiState().committees.single()

        assertEquals(false, committee.isHighSchoolCommittee)
        assertEquals(1, committee.highSchoolerAssignedCount)
        assertEquals(0, committee.universityAssignedCount)
    }

    @Test
    fun `assignment candidates sort matching school level before non matching school level`() {
        val highSchoolBoard = board(isHighSchoolCommittee = true)
        markHighSchooler(highSchoolBoard.available)
        highSchoolBoard.france.slots.single().assign(highSchoolBoard.confirmedElsewhere, AssignmentSource.Mymun, Instant.EPOCH)

        val highSchoolCandidates = highSchoolBoard.conference.toAssignmentBoardUiState()
            .committees.single()
            .positions.single { it.name == "Germany" }
            .assignmentCandidates

        assertEquals(listOf("Available Delegate", "Neutral Delegate"), highSchoolCandidates.map { it.name })
        assertEquals(true, highSchoolCandidates.first().isHighSchooler)

        val universityBoard = board(isHighSchoolCommittee = false)
        markHighSchooler(universityBoard.available)
        universityBoard.france.slots.single().assign(universityBoard.confirmedElsewhere, AssignmentSource.Mymun, Instant.EPOCH)

        val universityCandidates = universityBoard.conference.toAssignmentBoardUiState()
            .committees.single()
            .positions.single { it.name == "Germany" }
            .assignmentCandidates

        assertEquals(listOf("Neutral Delegate", "Available Delegate"), universityCandidates.map { it.name })
    }

    @Test
    fun `committee state exposes matching reservation reminder for high school committee`() {
        val board = board(isHighSchoolCommittee = true)
        val reservation = Reservation(
            id = ReservationId("reservation"),
            conference = board.conference,
            name = "Alpha Hold",
            highSchoolCount = 2,
            universityCount = 3,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        markHighSchooler(board.available)
        board.conference.reservations += reservation
        link(reservation, board.available)

        val reminder = board.conference.toAssignmentBoardUiState().committees.single().reservationReminder

        assertEquals("HS reservations", reminder?.label)
        assertEquals(2, reminder?.totalCount)
        assertEquals(1, reminder?.remainingCount)
    }

    @Test
    fun `committee state exposes matching reservation reminder for university committee`() {
        val board = board(isHighSchoolCommittee = false)
        val reservation = Reservation(
            id = ReservationId("reservation"),
            conference = board.conference,
            name = "Alpha Hold",
            highSchoolCount = 2,
            universityCount = 3,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        board.conference.reservations += reservation
        link(reservation, board.available)

        val reminder = board.conference.toAssignmentBoardUiState().committees.single().reservationReminder

        assertEquals("Uni reservations", reminder?.label)
        assertEquals(3, reminder?.totalCount)
        assertEquals(2, reminder?.remainingCount)
    }

    @Test
    fun `mapped committee exposes no reservation reminder and position mapping state`() {
        val board = mappedBoard()

        val ipc = board.conference.toAssignmentBoardUiState().committees.single { it.name == "IPC" }

        assertEquals(CommitteeMode.Mapped, ipc.mode)
        assertNull(ipc.reservationReminder)
        assertEquals(listOf("UNSC"), ipc.positions.single().mappingOptions.map { it.name })
        assertEquals("UNSC", ipc.positions.single().mappedCommittee?.name)
        assertEquals(CommitteeMode.HighSchool, ipc.positions.single().effectiveMode)
    }

    @Test
    fun `mapped position candidate sorting uses mapped target mode`() {
        val board = mappedBoard()
        markHighSchooler(board.available)

        val candidates = board.conference.toAssignmentBoardUiState()
            .committees.single { it.name == "IPC" }
            .positions.single()
            .assignmentCandidates

        assertEquals(listOf("Available Delegate", "Neutral Delegate"), candidates.map { it.name })
    }

    @Test
    fun `mapped committee does not expose reservation reminders`() {
        val board = mappedBoard()
        board.conference.reservations += Reservation(
            id = ReservationId("reservation"),
            conference = board.conference,
            name = "Mixed Hold",
            highSchoolCount = 2,
            universityCount = 3,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )

        val reminders = board.conference.toAssignmentBoardUiState()
            .committees.single { it.name == "IPC" }
            .reservationReminders

        assertTrue(reminders.isEmpty())
    }

    @Test
    fun `mapped position options exclude targets used by sibling positions`() {
        val board = mappedBoard()
        val ipc = board.conference.committees.single { it.mymunName == "IPC" }
        val secondReporter = Position(PositionId("second-reporter"), ipc, "Second Reporter")
        ipc.positions += secondReporter
        secondReporter.slots += Slot(SlotId("second-reporter-1"), secondReporter, 1, Instant.EPOCH)

        val positions = board.conference.toAssignmentBoardUiState()
            .committees.single { it.name == "IPC" }
            .positions

        assertEquals(listOf("UNSC"), positions.single { it.id == "reporter" }.mappingOptions.map { it.name })
        assertTrue(positions.single { it.id == "second-reporter" }.mappingOptions.isEmpty())
    }

    private fun board(slotCount: Int = 1, isHighSchoolCommittee: Boolean = false): Board {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val committee = Committee(
            CommitteeId("committee"),
            conference,
            "GA",
            slotsPerPosition = slotCount,
            mode = if (isHighSchoolCommittee) CommitteeMode.HighSchool else CommitteeMode.University,
        )
        val france = Position(PositionId("france"), committee, "France")
        val germany = Position(PositionId("germany"), committee, "Germany")
        val available = delegate(conference, "available", "Available", "Delegate", PaymentStatus.Paid, active = true)
        val unpaid = delegate(conference, "unpaid", "Unpaid", "Delegate", PaymentStatus.Unpaid, active = true)
        val inactive = delegate(conference, "inactive", "Inactive", "Delegate", PaymentStatus.Paid, active = false)
        val confirmedElsewhere = delegate(conference, "confirmed", "Confirmed", "Elsewhere", PaymentStatus.Paid, active = true)
        val neutral = delegate(conference, "neutral", "Neutral", "Delegate", PaymentStatus.Paid, active = true)

        conference.committees += committee
        committee.positions += listOf(france, germany)
        conference.delegates += listOf(available, unpaid, inactive, confirmedElsewhere, neutral)
        france.slots += (1..slotCount).map { Slot(SlotId("france-$it"), france, it, Instant.EPOCH) }
        germany.slots += Slot(SlotId("germany-1"), germany, 1, Instant.EPOCH)
        listOf(available, unpaid, inactive, confirmedElsewhere).forEachIndexed { index, delegate ->
            delegate.preferences += Preference(
                id = PreferenceId("preference-$index"),
                delegate = delegate,
                position = france,
                rank = index + 1,
                rawPreferenceText = "GA (France)",
            )
        }

        return Board(conference, france, germany.slots.single(), available, confirmedElsewhere)
    }

    private fun mappedBoard(): Board {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val ipc = Committee(CommitteeId("ipc"), conference, "IPC", slotsPerPosition = 1, mode = CommitteeMode.Mapped)
        val target = Committee(CommitteeId("target"), conference, "UNSC", slotsPerPosition = 1, mode = CommitteeMode.HighSchool)
        val reporter = Position(PositionId("reporter"), ipc, "UNSC Reporter").also { ipc.positions += it }
        conference.committees += listOf(ipc, target)
        reporter.setMappedCommittee(target)
        val targetPosition = Position(PositionId("target-position"), target, "France")
        val available = delegate(conference, "available", "Available", "Delegate", PaymentStatus.Paid, active = true)
        val neutral = delegate(conference, "neutral", "Neutral", "Delegate", PaymentStatus.Paid, active = true)

        conference.delegates += listOf(available, neutral)
        target.positions += targetPosition
        reporter.slots += Slot(SlotId("reporter-1"), reporter, 1, Instant.EPOCH)
        targetPosition.slots += Slot(SlotId("target-1"), targetPosition, 1, Instant.EPOCH)

        return Board(conference, reporter, reporter.slots.single(), available, neutral)
    }

    private fun delegate(
        conference: Conference,
        id: String,
        firstName: String,
        lastName: String,
        paymentStatus: PaymentStatus,
        active: Boolean,
        applicationStatus: String? = "Accepted",
    ): Delegate =
        Delegate(
            id = DelegateId(id),
            conference = conference,
            name = PersonName(firstName, lastName),
            email = "$id@example.test",
            profile = DelegateProfile(null, null, null, null, null, null, null, LocalDate.of(2007, 5, 15), null, applicationStatus),
            paymentStatus = paymentStatus,
            active = active,
            isHeadDelegate = false,
        )

    private fun markHighSchooler(delegate: Delegate) {
        val question = Question(
            QuestionId("high-schooler-${delegate.id.value}"),
            "High schooler",
            role = StandardQuestionRoles.HighSchoolerStatus,
            handling = StandardQuestionHandlingTypes.ExpectedTypedAnswer,
            mappings = TypedAnswerMappings(highSchooler = listOf(HighSchoolerAnswerMapping("Yes", HighSchoolerStatus.Yes))),
        )
        delegate.answers += QuestionAnswer(QuestionAnswerId("high-schooler-answer-${delegate.id.value}"), delegate, question, "Yes")
    }

    private fun link(reservation: Reservation, delegate: Delegate) {
        reservation.delegateLinks += ReservationDelegateLink(
            id = ReservationDelegateLinkId("${reservation.id.value}-${delegate.id.value}"),
            reservation = reservation,
            delegate = delegate,
            source = ReservationLinkSource.Manual,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
    }

    private data class Board(
        val conference: Conference,
        val france: Position,
        val germanySlot: Slot,
        val available: Delegate,
        val confirmedElsewhere: Delegate,
    )
}
