package org.munika.assignment.ui.screen

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
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
import org.munika.assignment.domain.model.Question
import org.munika.assignment.domain.model.QuestionAnswer
import org.munika.assignment.domain.model.QuestionAnswerId
import org.munika.assignment.domain.model.QuestionId
import org.munika.assignment.domain.model.Reservation
import org.munika.assignment.domain.model.ReservationId
import org.munika.assignment.domain.model.Slot
import org.munika.assignment.domain.model.SlotId
import org.munika.assignment.domain.model.StandardQuestionHandlingTypes
import org.munika.assignment.domain.model.StandardQuestionRoles
import org.munika.assignment.domain.model.TypedAnswerMappings

class ConferenceStatsTest {
    @Test
    fun `acceptance capacity subtracts active accepted delegates and remaining reservations by bucket`() {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val highSchoolCommittee = committee(conference, "hs", isHighSchool = true, positionCount = 2)
        val universityCommittee = committee(conference, "uni", isHighSchool = false, positionCount = 3)
        conference.committees += listOf(highSchoolCommittee, universityCommittee)
        val reservation = Reservation(
            id = ReservationId("reservation"),
            conference = conference,
            name = "Alpha Hold",
            highSchoolCount = 1,
            universityCount = 2,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        conference.reservations += reservation
        conference.delegates += listOf(
            delegate(conference, "hs-accepted", isHighSchool = true, status = "Accepted", paymentStatus = PaymentStatus.Paid),
            delegate(conference, "hs-assigned", isHighSchool = true, status = "Assigned", paymentStatus = PaymentStatus.Paid),
            delegate(conference, "hs-unpaid", isHighSchool = true, status = "Accepted", paymentStatus = PaymentStatus.Unpaid),
            delegate(conference, "hs-inactive", isHighSchool = true, status = "Accepted", paymentStatus = PaymentStatus.Paid, active = false),
            delegate(conference, "uni-accepted", isHighSchool = false, status = "Accepted", paymentStatus = PaymentStatus.Paid),
            delegate(conference, "uni-submitted", isHighSchool = false, status = "Submitted", paymentStatus = PaymentStatus.Paid),
        )

        val capacity = conference.toAcceptanceCapacityUiState()

        assertEquals(-2, capacity.highSchoolCount)
        assertEquals(0, capacity.universityCount)
    }

    @Test
    fun `acceptance capacity counts checked-in delegates the same as accepted`() {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val universityCommittee = committee(conference, "uni", isHighSchool = false, positionCount = 3)
        conference.committees += universityCommittee
        conference.delegates += listOf(
            delegate(conference, "uni-accepted", isHighSchool = false, status = "Accepted", paymentStatus = PaymentStatus.Paid),
            delegate(conference, "uni-checked-in", isHighSchool = false, status = "Checked In", paymentStatus = PaymentStatus.Paid),
        )

        val capacity = conference.toAcceptanceCapacityUiState()

        assertEquals(1, capacity.universityCount)
    }

    @Test
    fun `acceptance capacity counts mapped slots by mapped target mode`() {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val target = committee(conference, "target", isHighSchool = true, positionCount = 1)
        val ipc = committee(conference, "ipc", isHighSchool = false, positionCount = 1, mode = CommitteeMode.Mapped)
        conference.committees += listOf(target, ipc)
        ipc.positions.single().setMappedCommittee(target)

        val capacity = conference.toAcceptanceCapacityUiState()

        assertEquals(2, capacity.highSchoolCount)
        assertEquals(0, capacity.universityCount)
    }

    @Test
    fun `acceptance capacity charges mismatched occupants to their positions for both assignment sources`() {
        for (source in AssignmentSource.entries) {
            for (isHighSchool in listOf(true, false)) {
                val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
                val highSchool = committee(conference, "hs", isHighSchool = true, positionCount = 2)
                val university = committee(conference, "uni", isHighSchool = false, positionCount = 3)
                conference.committees += listOf(highSchool, university)
                val occupant = delegate(conference, "occupant", isHighSchool, "Accepted", PaymentStatus.Paid)
                conference.delegates += occupant
                val destination = if (isHighSchool) university else highSchool
                destination.slots.first().assign(occupant, source, Instant.EPOCH)

                val capacity = conference.toAcceptanceCapacityUiState()

                assertEquals(if (isHighSchool) 2 else 1, capacity.highSchoolCount, "$source, HS=$isHighSchool")
                assertEquals(if (isHighSchool) 2 else 3, capacity.universityCount, "$source, HS=$isHighSchool")
            }
        }
    }

    @Test
    fun `acceptance capacity charges mapped occupants to the target bucket`() {
        for (source in AssignmentSource.entries) {
            for (targetIsHighSchool in listOf(true, false)) {
                val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
                val target = committee(conference, "target", targetIsHighSchool, positionCount = 2)
                val ipc = committee(conference, "ipc", isHighSchool = false, positionCount = 1, mode = CommitteeMode.Mapped)
                conference.committees += listOf(target, ipc)
                ipc.positions.single().setMappedCommittee(target)
                val occupant = delegate(conference, "occupant", !targetIsHighSchool, "Assigned", PaymentStatus.Paid)
                conference.delegates += occupant
                ipc.slots.single().assign(occupant, source, Instant.EPOCH)

                val capacity = conference.toAcceptanceCapacityUiState()

                assertEquals(if (targetIsHighSchool) 2 else 0, capacity.highSchoolCount)
                assertEquals(if (targetIsHighSchool) 0 else 2, capacity.universityCount)
            }
        }
    }

    @Test
    fun `acceptance capacity excludes occupants of incompletely mapped positions from both buckets`() {
        for (source in AssignmentSource.entries) {
            for (isHighSchool in listOf(true, false)) {
                val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
                val highSchool = committee(conference, "hs", isHighSchool = true, positionCount = 1)
                val university = committee(conference, "uni", isHighSchool = false, positionCount = 1)
                val ipc = committee(conference, "ipc", isHighSchool = false, positionCount = 1, mode = CommitteeMode.Mapped)
                conference.committees += listOf(highSchool, university, ipc)
                val occupant = delegate(conference, "occupant", isHighSchool, "Accepted", PaymentStatus.Paid)
                conference.delegates += occupant
                ipc.slots.single().assign(occupant, source, Instant.EPOCH)

                assertEquals(AcceptanceCapacityUiState(1, 1), conference.toAcceptanceCapacityUiState())
            }
        }
    }

    @Test
    fun `unassigned acceptance commitments use personal school level regardless of payment`() {
        for (paymentStatus in PaymentStatus.entries) {
            for (isHighSchool in listOf(true, false)) {
                val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
                conference.committees += listOf(
                    committee(conference, "hs", isHighSchool = true, positionCount = 2),
                    committee(conference, "uni", isHighSchool = false, positionCount = 3),
                )
                conference.delegates += delegate(conference, "unassigned", isHighSchool, "Accepted", paymentStatus)

                val capacity = conference.toAcceptanceCapacityUiState()

                assertEquals(if (isHighSchool) 1 else 2, capacity.highSchoolCount)
                assertEquals(if (isHighSchool) 3 else 2, capacity.universityCount)
            }
        }
    }

    @Test
    fun `acceptance capacity excludes inactive and non-actionable occupants`() {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val highSchool = committee(conference, "hs", isHighSchool = true, positionCount = 1)
        val university = committee(conference, "uni", isHighSchool = false, positionCount = 1)
        conference.committees += listOf(highSchool, university)
        val inactive = delegate(conference, "inactive", true, "Accepted", PaymentStatus.Paid, active = false)
        val submitted = delegate(conference, "submitted", false, "Submitted", PaymentStatus.Paid)
        conference.delegates += listOf(inactive, submitted)
        university.slots.single().assign(inactive, AssignmentSource.Mymun, Instant.EPOCH)
        highSchool.slots.single().assign(submitted, AssignmentSource.Mymun, Instant.EPOCH)

        assertEquals(AcceptanceCapacityUiState(1, 1), conference.toAcceptanceCapacityUiState())
    }

    private fun committee(
        conference: Conference,
        id: String,
        isHighSchool: Boolean,
        positionCount: Int,
        mode: CommitteeMode = if (isHighSchool) CommitteeMode.HighSchool else CommitteeMode.University,
    ): Committee {
        val committee = Committee(
            id = CommitteeId(id),
            conference = conference,
            mymunName = id.uppercase(),
            slotsPerPosition = 1,
            mode = mode,
        )
        repeat(positionCount) { index ->
            val position = Position(PositionId("$id-position-$index"), committee, "Position $index")
            position.slots += Slot(SlotId("$id-slot-$index"), position, 1, Instant.EPOCH)
            committee.positions += position
        }
        return committee
    }

    private fun delegate(
        conference: Conference,
        id: String,
        isHighSchool: Boolean,
        status: String,
        paymentStatus: PaymentStatus,
        active: Boolean = true,
    ): Delegate {
        val delegate = Delegate(
            id = DelegateId(id),
            conference = conference,
            name = PersonName(id, "Delegate"),
            email = "$id@example.test",
            profile = DelegateProfile(
                country = null,
                city = null,
                streetAddress = null,
                zipCode = null,
                diet = null,
                phone = null,
                nationality = null,
                dateOfBirth = null,
                delegationNameFromMymun = null,
                applicationStatus = status,
            ),
            paymentStatus = paymentStatus,
            active = active,
            isHeadDelegate = false,
        )
        val question = Question(
            id = QuestionId("$id-high-school-question"),
            questionText = "High schooler?",
            role = StandardQuestionRoles.HighSchoolerStatus,
            handling = StandardQuestionHandlingTypes.ExpectedTypedAnswer,
            mappings = TypedAnswerMappings(
                highSchooler = listOf(
                    HighSchoolerAnswerMapping("Yes", HighSchoolerStatus.Yes),
                    HighSchoolerAnswerMapping("No", HighSchoolerStatus.No),
                ),
            ),
        )
        delegate.answers += QuestionAnswer(
            id = QuestionAnswerId("$id-high-school-answer"),
            delegate = delegate,
            question = question,
            rawAnswer = if (isHighSchool) "Yes" else "No",
        )
        return delegate
    }
}
