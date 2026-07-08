package org.munika.assignment.domain.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class ReservationTest {
    @Test
    fun `remaining counts floor at zero instead of going negative when over-consumed`() {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val question = Question(
            id = QuestionId("high-school-question"),
            questionText = "High schooler?",
            role = StandardQuestionRoles.HighSchoolerStatus,
            handling = StandardQuestionHandlingTypes.ExpectedTypedAnswer,
            mappings = TypedAnswerMappings(highSchooler = listOf(HighSchoolerAnswerMapping("Yes", HighSchoolerStatus.Yes))),
        )
        conference.questions += question

        val first = highSchoolDelegate(conference, question, "first")
        val second = highSchoolDelegate(conference, question, "second")

        val overConsumed = Reservation(
            id = ReservationId("over-consumed"),
            conference = conference,
            name = "Alpha Hold",
            highSchoolCount = 1,
            universityCount = 0,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        conference.reservations += overConsumed
        link(overConsumed, first, "link-1")
        link(overConsumed, second, "link-2")

        val withCapacity = Reservation(
            id = ReservationId("with-capacity"),
            conference = conference,
            name = "Beta Hold",
            highSchoolCount = 3,
            universityCount = 0,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        conference.reservations += withCapacity

        assertEquals(2, overConsumed.usedHighSchoolCount)
        assertEquals(0, overConsumed.remainingHighSchoolCount)
        assertEquals(3, conference.remainingHighSchoolReservationCount)
    }

    private fun highSchoolDelegate(conference: Conference, question: Question, id: String): Delegate {
        val delegate = Delegate(
            id = DelegateId(id),
            conference = conference,
            name = PersonName(id, "Delegate"),
            email = "$id@example.test",
            profile = DelegateProfile(null, null, null, null, null, null, null, null, null, "Accepted"),
            paymentStatus = PaymentStatus.Paid,
            active = true,
            isHeadDelegate = false,
        )
        delegate.answers += QuestionAnswer(QuestionAnswerId("$id-answer"), delegate, question, "Yes")
        conference.delegates += delegate
        return delegate
    }

    private fun link(reservation: Reservation, delegate: Delegate, id: String) {
        val link = ReservationDelegateLink(
            id = ReservationDelegateLinkId(id),
            reservation = reservation,
            delegate = delegate,
            source = ReservationLinkSource.Manual,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        reservation.delegateLinks += link
    }
}
