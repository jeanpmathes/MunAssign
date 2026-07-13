package org.munika.assignment.ui.viewmodel

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.ConferenceId
import org.munika.assignment.domain.model.Delegate
import org.munika.assignment.domain.model.DelegateId
import org.munika.assignment.domain.model.DelegateProfile
import org.munika.assignment.domain.model.Delegation
import org.munika.assignment.domain.model.DelegationId
import org.munika.assignment.domain.model.HighSchoolerAnswerMapping
import org.munika.assignment.domain.model.HighSchoolerStatus
import org.munika.assignment.domain.model.PaymentStatus
import org.munika.assignment.domain.model.PersonName
import org.munika.assignment.domain.model.Question
import org.munika.assignment.domain.model.QuestionAnswer
import org.munika.assignment.domain.model.QuestionAnswerId
import org.munika.assignment.domain.model.QuestionId
import org.munika.assignment.domain.model.Reservation
import org.munika.assignment.domain.model.ReservationDelegateLink
import org.munika.assignment.domain.model.ReservationDelegateLinkId
import org.munika.assignment.domain.model.ReservationId
import org.munika.assignment.domain.model.ReservationLinkSource
import org.munika.assignment.domain.model.StandardQuestionHandlingTypes
import org.munika.assignment.domain.model.StandardQuestionRoles
import org.munika.assignment.domain.model.TypedAnswerMappings

class DelegationOverviewUiModelsTest {
    @Test
    fun `overview shows linked reservations active paid accepted count and delegate reservations`() {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val delegation = Delegation(
            DelegationId("delegation"),
            conference,
            "Alpha Delegation",
            "Finland",
            4,
            3,
            isHighSchoolDelegation = true,
            needsReview = true,
        )
        val otherDelegation = Delegation(DelegationId("other"), conference, "Beta Delegation", "Norway", 2, 1)
        val manual = delegate(conference, "manual", "Manual", "Delegate", delegation, "Accepted", PaymentStatus.Paid, true, true)
        val automatic = delegate(conference, "automatic", "Automatic", "Delegate", delegation, "Assigned", PaymentStatus.Paid, true, false)
        val unpaid = delegate(conference, "unpaid", "Unpaid", "Delegate", delegation, "Accepted", PaymentStatus.Unpaid, true, false)
        val inactive = delegate(conference, "inactive", "Inactive", "Delegate", delegation, "Accepted", PaymentStatus.Paid, false, false)
        val submitted = delegate(conference, "submitted", "Submitted", "Delegate", delegation, "Submitted", PaymentStatus.Paid, true, false)
        val reservation = Reservation(
            id = ReservationId("reservation"),
            conference = conference,
            name = "Alpha Hold",
            highSchoolCount = 1,
            universityCount = 2,
            delegation = delegation,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        val otherReservation = Reservation(
            id = ReservationId("other-reservation"),
            conference = conference,
            name = "Other Hold",
            highSchoolCount = 1,
            universityCount = 0,
            delegation = otherDelegation,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        conference.delegations += listOf(otherDelegation, delegation)
        conference.delegates += listOf(manual, automatic, unpaid, inactive, submitted)
        conference.reservations += listOf(otherReservation, reservation)
        link(reservation, manual, ReservationLinkSource.Manual)
        link(reservation, automatic, ReservationLinkSource.DelegationAuto)

        val overview = conference.toDelegationOverviewUiState()
        val row = overview.delegations.single { it.id == "delegation" }

        assertEquals("Alpha Delegation", row.name)
        assertEquals("Finland", row.country)
        assertEquals(4, row.expectedDelegateCount)
        assertEquals(3, row.currentDelegateCountFromMymun)
        assertEquals(true, row.isHighSchoolDelegation)
        assertEquals(true, row.needsReview)
        assertEquals(2, row.activePaidAcceptedDelegateCount)
        assertEquals(listOf("Alpha Hold"), row.linkedReservations.map { it.name })
        assertEquals(1, row.linkedReservations.single().usedHighSchoolCount)
        assertEquals(1, row.linkedReservations.single().totalHighSchoolCount)
        assertEquals(1, row.linkedReservations.single().usedUniversityCount)
        assertEquals(2, row.linkedReservations.single().totalUniversityCount)
        assertEquals(listOf("Automatic Delegate", "Inactive Delegate", "Manual Delegate", "Unpaid Delegate"), row.acceptedDelegates.map { it.name })
        assertEquals(listOf("Submitted Delegate"), row.submittedDelegates.map { it.name })
        assertEquals("Alpha Hold", row.acceptedDelegates.single { it.id == "manual" }.reservationName)
        assertEquals("Alpha Hold", row.acceptedDelegates.single { it.id == "automatic" }.reservationName)
    }

    @Test
    fun `overview sorts delegations by name`() {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        conference.delegations += listOf(
            Delegation(DelegationId("zed"), conference, "Zed Delegation", null, null, null),
            Delegation(DelegationId("alpha"), conference, "Alpha Delegation", null, null, null),
        )

        val overview = conference.toDelegationOverviewUiState()

        assertEquals(listOf("Alpha Delegation", "Zed Delegation"), overview.delegations.map { it.name })
    }

    private fun delegate(
        conference: Conference,
        id: String,
        firstName: String,
        lastName: String,
        delegation: Delegation,
        applicationStatus: String,
        paymentStatus: PaymentStatus,
        active: Boolean,
        isHighSchooler: Boolean,
    ): Delegate {
        val delegate = Delegate(
            id = DelegateId(id),
            conference = conference,
            name = PersonName(firstName, lastName),
            email = "$id@example.test",
            profile = DelegateProfile(null, null, null, null, null, null, null, LocalDate.of(2006, 1, 1), delegation.name, applicationStatus),
            paymentStatus = paymentStatus,
            active = active,
            isHeadDelegate = false,
            delegation = delegation,
        )
        if (isHighSchooler) {
            val question = Question(
                QuestionId("high-schooler-$id"),
                "High schooler",
                role = StandardQuestionRoles.HighSchoolerStatus,
                handling = StandardQuestionHandlingTypes.ExpectedTypedAnswer,
                mappings = TypedAnswerMappings(highSchooler = listOf(HighSchoolerAnswerMapping("Yes", HighSchoolerStatus.Yes))),
            )
            delegate.answers += QuestionAnswer(QuestionAnswerId("high-schooler-answer-$id"), delegate, question, "Yes")
        }
        return delegate
    }

    private fun link(
        reservation: Reservation,
        delegate: Delegate,
        source: ReservationLinkSource,
    ) {
        val reservationLink = ReservationDelegateLink(
            id = ReservationDelegateLinkId("${reservation.id.value}-${delegate.id.value}"),
            reservation = reservation,
            delegate = delegate,
            source = source,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        reservation.delegateLinks += reservationLink
        reservationLink.attachToDelegate()
    }
}
