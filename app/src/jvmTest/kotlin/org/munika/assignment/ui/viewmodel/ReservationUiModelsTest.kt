package org.munika.assignment.ui.viewmodel

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
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

class ReservationUiModelsTest {
    @Test
    fun `manager lists consumers and reports remaining counts`() {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val delegation = Delegation(DelegationId("delegation"), conference, "Alpha Delegation", "Finland", 4, 2)
        val reservation = Reservation(
            id = ReservationId("reservation"),
            conference = conference,
            name = "Alpha Hold",
            highSchoolCount = 2,
            universityCount = 2,
            delegation = delegation,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        val manual = delegate(conference, "manual", "Manual", "Consumer", isHighSchooler = true)
        val automatic = delegate(conference, "automatic", "Automatic", "Consumer", isHighSchooler = false)
        val inactive = delegate(conference, "inactive", "Inactive", "Consumer", isHighSchooler = false, active = false)
        conference.delegations += delegation
        conference.reservations += reservation
        conference.delegates += listOf(manual, automatic, inactive)
        link(reservation, manual, ReservationLinkSource.Manual)
        link(reservation, automatic, ReservationLinkSource.DelegationAuto)
        link(reservation, inactive, ReservationLinkSource.Manual)

        val manager = conference.toReservationManagerUiState()
        val summary = manager.reservations.single()

        assertEquals("reservation", summary.id)
        assertEquals("Alpha Hold", summary.name)
        assertEquals(2, summary.highSchoolCount)
        assertEquals(2, summary.universityCount)
        assertEquals(1, summary.remainingHighSchoolCount)
        assertEquals(1, summary.remainingUniversityCount)
        assertEquals("delegation", summary.delegationId)
        assertEquals("Alpha Delegation - Finland", summary.delegationName)
        assertEquals(listOf("Automatic Consumer", "Manual Consumer"), summary.consumers.map { it.delegateName })
        assertEquals(listOf("Manual Consumer"), summary.manualConsumers.map { it.delegateName })
        assertEquals(listOf("Automatic Consumer"), summary.automaticConsumers.map { it.delegateName })
        assertEquals("Manual", summary.manualConsumers.single().sourceLabel)
        assertEquals("Delegation", summary.automaticConsumers.single().sourceLabel)
    }

    @Test
    fun `manager sorts reservations and delegation options by display label`() {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val zed = Delegation(DelegationId("zed"), conference, "Zed Delegation", "Norway", null, null)
        val alpha = Delegation(DelegationId("alpha"), conference, "Alpha Delegation", "Finland", null, null)
        val betaReservation = Reservation(ReservationId("beta"), conference, "Beta Hold", 1, 0, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val alphaReservation = Reservation(ReservationId("alpha"), conference, "Alpha Hold", 0, 1, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        conference.delegations += listOf(zed, alpha)
        conference.reservations += listOf(betaReservation, alphaReservation)

        val manager = conference.toReservationManagerUiState()

        assertEquals(listOf("Alpha Hold", "Beta Hold"), manager.reservations.map { it.name })
        assertEquals(listOf("Alpha Delegation - Finland", "Zed Delegation - Norway"), manager.delegations.map { it.label })
        assertNull(manager.reservations.single { it.id == "alpha" }.delegationId)
    }

    @Test
    fun `quick reservation draft assigns the full expected count to the delegation level`() {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val university = Delegation(DelegationId("university"), conference, "University Team", null, 6, null)
        val highSchool = Delegation(
            DelegationId("high-school"),
            conference,
            "School Team",
            null,
            4,
            null,
            isHighSchoolDelegation = true,
        )
        conference.reservations += Reservation(
            ReservationId("existing"),
            conference,
            "Existing",
            highSchoolCount = 0,
            universityCount = 6,
            delegation = university,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )

        val universityDraft = university.toQuickReservationDraftUiState()
        val highSchoolDraft = highSchool.toQuickReservationDraftUiState()

        assertEquals("University Team", universityDraft.name)
        assertEquals("university", universityDraft.delegationId)
        assertEquals(0, universityDraft.highSchoolCount)
        assertEquals(6, universityDraft.universityCount)
        assertEquals(4, highSchoolDraft.highSchoolCount)
        assertEquals(0, highSchoolDraft.universityCount)
    }

    @Test
    fun `quick reservation draft uses zero when expected count is missing or negative`() {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val missing = Delegation(DelegationId("missing"), conference, "Missing", null, null, null)
        val negative = Delegation(DelegationId("negative"), conference, "Negative", null, -2, null, isHighSchoolDelegation = true)

        val missingDraft = missing.toQuickReservationDraftUiState()
        val negativeDraft = negative.toQuickReservationDraftUiState()

        assertEquals(0, missingDraft.highSchoolCount)
        assertEquals(0, missingDraft.universityCount)
        assertEquals(0, negativeDraft.highSchoolCount)
        assertEquals(0, negativeDraft.universityCount)
        assertTrue(missingDraft.delegationId.isNotBlank())
    }

    private fun delegate(
        conference: Conference,
        id: String,
        firstName: String,
        lastName: String,
        isHighSchooler: Boolean,
        active: Boolean = true,
    ): Delegate {
        val delegate = Delegate(
            id = DelegateId(id),
            conference = conference,
            name = PersonName(firstName, lastName),
            email = "$id@example.test",
            profile = DelegateProfile(null, null, null, null, null, null, null, LocalDate.of(2006, 1, 1), null, "Accepted"),
            paymentStatus = PaymentStatus.Paid,
            active = active,
            isHeadDelegate = false,
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
        reservation.delegateLinks += ReservationDelegateLink(
            id = ReservationDelegateLinkId("${reservation.id.value}-${delegate.id.value}"),
            reservation = reservation,
            delegate = delegate,
            source = source,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
    }
}
