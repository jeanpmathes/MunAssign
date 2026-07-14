package org.munika.assignment.domain.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class ReservationReconcilerTest {
    @Test
    fun `manual link consumes active delegate regardless of payment and accepted status`() {
        val fixture = fixture()
        val delegate = fixture.delegate(
            id = "manual",
            paymentStatus = PaymentStatus.Unpaid,
            applicationStatus = "Rejected",
            highSchooler = true,
        )
        val reservation = fixture.reservation(id = "reservation", highSchoolCount = 1, universityCount = 0)
        val link = fixture.link("manual-link", reservation, delegate, ReservationLinkSource.Manual)

        val result = ReservationReconciler.reconcile(
            conference = fixture.conference,
            existingLinks = listOf(link),
            now = Later,
            idFactory = fixture.idFactory(),
        )

        assertEquals(listOf(link.id), result.links.map { it.id })
        assertEquals(1, reservation.usedHighSchoolCount)
        assertEquals(0, reservation.remainingHighSchoolCount)
        assertSame(link, delegate.reservationLink)
        assertEquals(emptyList(), result.removedLinkIds)
    }

    @Test
    fun `deactivated delegate does not consume manual reservation and link is removed`() {
        val fixture = fixture()
        val delegate = fixture.delegate(id = "inactive", active = false, highSchooler = true)
        val reservation = fixture.reservation(id = "reservation", highSchoolCount = 1, universityCount = 0)
        val link = fixture.link("manual-link", reservation, delegate, ReservationLinkSource.Manual)

        val result = ReservationReconciler.reconcile(
            conference = fixture.conference,
            existingLinks = listOf(link),
            now = Later,
            idFactory = fixture.idFactory(),
        )

        assertEquals(emptyList(), result.links)
        assertEquals(listOf(link.id), result.removedLinkIds)
        assertEquals(0, reservation.usedHighSchoolCount)
        assertEquals(1, reservation.remainingHighSchoolCount)
        assertNull(delegate.reservationLink)
    }

    @Test
    fun `delegation auto links active accepted delegates regardless of payment by submission date`() {
        val fixture = fixture()
        val delegation = fixture.delegation()
        val later = fixture.delegate(id = "later", delegation = delegation, submittedAt = Instant.ofEpochMilli(20))
        val earlier = fixture.delegate(id = "earlier", delegation = delegation, submittedAt = Instant.ofEpochMilli(10))
        val noSubmission = fixture.delegate(id = "no-submission", delegation = delegation, submittedAt = null)
        val unpaid = fixture.delegate(
            id = "unpaid",
            delegation = delegation,
            paymentStatus = PaymentStatus.Unpaid,
            submittedAt = Instant.ofEpochMilli(1),
        )
        fixture.delegate(id = "rejected", delegation = delegation, applicationStatus = "Rejected", submittedAt = Instant.ofEpochMilli(2))
        val reservation = fixture.reservation(
            id = "reservation",
            highSchoolCount = 4,
            universityCount = 0,
            delegation = delegation,
        )

        val result = ReservationReconciler.reconcile(
            conference = fixture.conference,
            existingLinks = emptyList(),
            now = Later,
            idFactory = fixture.idFactory("auto-"),
        )

        assertEquals(listOf(unpaid.id, earlier.id, later.id, noSubmission.id), result.links.map { it.delegate.id })
        assertEquals(List(4) { ReservationLinkSource.DelegationAuto }, result.links.map { it.source })
        assertEquals(listOf("auto-1", "auto-2", "auto-3", "auto-4"), result.links.map { it.id.value })
        assertEquals(4, reservation.usedHighSchoolCount)
        assertSame(reservation, earlier.reservationLink?.reservation)
    }

    @Test
    fun `delegation auto links checked-in delegates the same as accepted`() {
        val fixture = fixture()
        val delegation = fixture.delegation()
        val checkedIn = fixture.delegate(
            id = "checked-in",
            delegation = delegation,
            applicationStatus = "Checked In",
            submittedAt = Instant.ofEpochMilli(1),
        )
        val reservation = fixture.reservation(
            id = "reservation",
            highSchoolCount = 1,
            universityCount = 0,
            delegation = delegation,
        )

        val result = ReservationReconciler.reconcile(
            conference = fixture.conference,
            existingLinks = emptyList(),
            now = Later,
            idFactory = fixture.idFactory("auto-"),
        )

        assertEquals(listOf(checkedIn.id), result.links.map { it.delegate.id })
        assertEquals(1, reservation.usedHighSchoolCount)
    }

    @Test
    fun `delegation auto links multiple high school and university delegates in separate buckets`() {
        val fixture = fixture()
        val delegation = fixture.delegation()
        val firstHighSchool = fixture.delegate(id = "hs-1", delegation = delegation, submittedAt = Instant.ofEpochMilli(1))
        val secondHighSchool = fixture.delegate(id = "hs-2", delegation = delegation, submittedAt = Instant.ofEpochMilli(2))
        val firstUniversity = fixture.delegate(id = "uni-1", delegation = delegation, highSchooler = false, submittedAt = Instant.ofEpochMilli(3))
        val secondUniversity = fixture.delegate(id = "uni-2", delegation = delegation, highSchooler = false, submittedAt = Instant.ofEpochMilli(4))
        val reservation = fixture.reservation(
            id = "reservation",
            highSchoolCount = 5,
            universityCount = 5,
            delegation = delegation,
        )

        val result = ReservationReconciler.reconcile(
            conference = fixture.conference,
            existingLinks = emptyList(),
            now = Later,
            idFactory = fixture.idFactory("auto-"),
        )

        assertEquals(
            listOf(firstHighSchool.id, secondHighSchool.id, firstUniversity.id, secondUniversity.id),
            result.links.map { it.delegate.id },
        )
        assertEquals(2, reservation.usedHighSchoolCount)
        assertEquals(2, reservation.usedUniversityCount)
        assertEquals(3, reservation.remainingHighSchoolCount)
        assertEquals(3, reservation.remainingUniversityCount)
    }

    @Test
    fun `auto fill skips delegate already consuming another reservation`() {
        val fixture = fixture()
        val delegation = fixture.delegation()
        val alreadyManual = fixture.delegate(id = "manual", delegation = delegation, submittedAt = Instant.ofEpochMilli(1))
        val eligible = fixture.delegate(id = "eligible", delegation = delegation, submittedAt = Instant.ofEpochMilli(2))
        val manualReservation = fixture.reservation(id = "manual-reservation", highSchoolCount = 1, universityCount = 0)
        val autoReservation = fixture.reservation(
            id = "auto-reservation",
            highSchoolCount = 1,
            universityCount = 0,
            delegation = delegation,
        )
        val manualLink = fixture.link("manual-link", manualReservation, alreadyManual, ReservationLinkSource.Manual)

        val result = ReservationReconciler.reconcile(
            conference = fixture.conference,
            existingLinks = listOf(manualLink),
            now = Later,
            idFactory = fixture.idFactory("auto-"),
        )

        assertEquals(listOf(manualLink.id, ReservationDelegateLinkId("auto-1")), result.links.map { it.id })
        assertSame(manualReservation, alreadyManual.reservationLink?.reservation)
        assertSame(autoReservation, eligible.reservationLink?.reservation)
        assertEquals(1, autoReservation.usedHighSchoolCount)
    }

    @Test
    fun `valid reduction trims excess automatic links`() {
        val fixture = fixture()
        val delegation = fixture.delegation()
        val first = fixture.delegate(id = "first", delegation = delegation, submittedAt = Instant.ofEpochMilli(1))
        val second = fixture.delegate(id = "second", delegation = delegation, submittedAt = Instant.ofEpochMilli(2))
        val third = fixture.delegate(id = "third", delegation = delegation, submittedAt = Instant.ofEpochMilli(3))
        val reservation = fixture.reservation(
            id = "reservation",
            highSchoolCount = 2,
            universityCount = 0,
            delegation = delegation,
        )
        val existingLinks = listOf(
            fixture.link("auto-first", reservation, first, ReservationLinkSource.DelegationAuto),
            fixture.link("auto-second", reservation, second, ReservationLinkSource.DelegationAuto),
            fixture.link("auto-third", reservation, third, ReservationLinkSource.DelegationAuto),
        )

        val result = ReservationReconciler.reconcile(
            conference = fixture.conference,
            existingLinks = existingLinks,
            now = Later,
            idFactory = fixture.idFactory("new-"),
        )

        assertEquals(listOf("auto-first", "auto-second"), result.links.map { it.id.value })
        assertEquals(listOf(ReservationDelegateLinkId("auto-third")), result.removedLinkIds)
        assertEquals(2, reservation.usedHighSchoolCount)
        assertEquals(0, reservation.remainingHighSchoolCount)
        assertNull(third.reservationLink)
    }

    @Test
    fun `oldest linked reservation consumes delegation first regardless of list order`() {
        val fixture = fixture()
        val delegation = fixture.delegation()
        val delegate = fixture.delegate(id = "delegate", delegation = delegation)
        val newest = fixture.reservation(
            id = "newest",
            highSchoolCount = 1,
            universityCount = 0,
            delegation = delegation,
            createdAt = Instant.ofEpochMilli(30),
        )
        val oldestByLaterId = fixture.reservation(
            id = "z-oldest",
            highSchoolCount = 1,
            universityCount = 0,
            delegation = delegation,
            createdAt = Instant.ofEpochMilli(10),
        )
        val oldestByFirstId = fixture.reservation(
            id = "a-oldest",
            highSchoolCount = 1,
            universityCount = 0,
            delegation = delegation,
            createdAt = Instant.ofEpochMilli(10),
        )
        fixture.conference.reservations.clear()
        fixture.conference.reservations += listOf(newest, oldestByLaterId, oldestByFirstId)

        ReservationReconciler.reconcile(
            conference = fixture.conference,
            existingLinks = emptyList(),
            now = Later,
            idFactory = fixture.idFactory("auto-"),
        )

        assertSame(oldestByFirstId, delegate.reservationLink?.reservation)
    }

    private fun fixture(): Fixture =
        Fixture(
            Conference(
                id = ConferenceId("conference"),
                name = "KAMUN",
                createdAt = Now,
                updatedAt = Now,
            ),
        )

    private class Fixture(val conference: Conference) {
        private val highSchoolerQuestion = Question(
            id = QuestionId("high-schooler"),
            questionText = "Are you a high schooler?",
            role = StandardQuestionRoles.HighSchoolerStatus,
            handling = StandardQuestionHandlingTypes.ExpectedTypedAnswer,
        )

        fun delegation(id: String = "delegation"): Delegation =
            Delegation(
                id = DelegationId(id),
                conference = conference,
                name = "Delegation $id",
                country = "France",
                expectedDelegateCount = null,
                currentDelegateCountFromMymun = null,
            ).also { conference.delegations += it }

        fun delegate(
            id: String,
            active: Boolean = true,
            paymentStatus: PaymentStatus = PaymentStatus.Paid,
            applicationStatus: String? = "Accepted",
            highSchooler: Boolean = true,
            delegation: Delegation? = null,
            submittedAt: Instant? = Now,
        ): Delegate =
            Delegate(
                id = DelegateId(id),
                conference = conference,
                name = PersonName("Delegate", id),
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
                    delegationNameFromMymun = delegation?.name,
                    applicationStatus = applicationStatus,
                ),
                paymentStatus = paymentStatus,
                active = active,
                isHeadDelegate = false,
                delegation = delegation,
                submittedAt = submittedAt,
            ).also { delegate ->
                delegate.answers += QuestionAnswer(
                    id = QuestionAnswerId("answer-$id"),
                    delegate = delegate,
                    question = highSchoolerQuestion,
                    rawAnswer = if (highSchooler) "Yes" else "No",
                )
                conference.delegates += delegate
            }

        fun reservation(
            id: String,
            highSchoolCount: Int,
            universityCount: Int,
            delegation: Delegation? = null,
            createdAt: Instant = Now,
        ): Reservation =
            Reservation(
                id = ReservationId(id),
                conference = conference,
                name = "Reservation $id",
                highSchoolCount = highSchoolCount,
                universityCount = universityCount,
                delegation = delegation,
                createdAt = createdAt,
                updatedAt = Now,
            ).also { conference.reservations += it }

        fun link(
            id: String,
            reservation: Reservation,
            delegate: Delegate,
            source: ReservationLinkSource,
        ): ReservationDelegateLink =
            ReservationDelegateLink(
                id = ReservationDelegateLinkId(id),
                reservation = reservation,
                delegate = delegate,
                source = source,
                createdAt = Now,
                updatedAt = Now,
            )

        fun idFactory(prefix: String = "id-"): () -> ReservationDelegateLinkId {
            var next = 1
            return { ReservationDelegateLinkId("$prefix${next++}") }
        }
    }

    private companion object {
        val Now: Instant = Instant.ofEpochMilli(100)
        val Later: Instant = Instant.ofEpochMilli(200)
    }
}
