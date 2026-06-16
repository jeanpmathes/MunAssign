package org.munika.assignment.ui.screen

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import org.munika.assignment.domain.model.Committee
import org.munika.assignment.domain.model.CommitteeId
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
import org.munika.assignment.domain.model.StandardQuestionRoles
import org.munika.assignment.domain.model.TypedAnswerMappings

class ConferenceStatsTest {
    @Test
    fun `acceptance capacity subtracts active paid accepted delegates and remaining reservations by bucket`() {
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

        assertEquals(-1, capacity.highSchoolCount)
        assertEquals(0, capacity.universityCount)
    }

    private fun committee(
        conference: Conference,
        id: String,
        isHighSchool: Boolean,
        positionCount: Int,
    ): Committee {
        val committee = Committee(
            id = CommitteeId(id),
            conference = conference,
            mymunName = id.uppercase(),
            slotsPerPosition = 1,
            isHighSchoolCommittee = isHighSchool,
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
