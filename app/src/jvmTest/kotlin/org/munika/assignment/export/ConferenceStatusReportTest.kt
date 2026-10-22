package org.munika.assignment.export

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
import org.munika.assignment.domain.model.ReservationDelegateLink
import org.munika.assignment.domain.model.ReservationDelegateLinkId
import org.munika.assignment.domain.model.ReservationId
import org.munika.assignment.domain.model.ReservationLinkSource
import org.munika.assignment.domain.model.Slot
import org.munika.assignment.domain.model.SlotId
import org.munika.assignment.domain.model.StandardQuestionHandlingTypes
import org.munika.assignment.domain.model.StandardQuestionRoles
import org.munika.assignment.domain.model.TypedAnswerMappings

class ConferenceStatusReportTest {
    @Test
    fun `report builds mutually exclusive high school capacity segments`() {
        val conference = conference()
        val highSchool = committee(conference, "hs", "High School General Assembly", CommitteeMode.HighSchool, 6)
        val university = committee(conference, "uni", "University Council", CommitteeMode.University, 2)
        conference.committees += listOf(highSchool, university)

        val allocated = delegate(conference, "allocated", paid = true, status = "Accepted", highSchool = true)
        val paid = delegate(conference, "paid", paid = true, status = "Accepted", highSchool = true)
        val unpaid = delegate(conference, "unpaid", paid = false, status = "Accepted", highSchool = true)
        val manuallyReserved = delegate(conference, "submitted", paid = false, status = "Submitted", highSchool = true)
        highSchool.slots.first().assign(allocated, AssignmentSource.Application, Now)

        val reservation = Reservation(
            id = ReservationId("reservation"),
            conference = conference,
            name = "School hold",
            highSchoolCount = 3,
            universityCount = 0,
            createdAt = Now,
            updatedAt = Now,
        )
        conference.reservations += reservation
        link(reservation, unpaid, "unpaid-link", ReservationLinkSource.DelegationAuto)
        link(reservation, manuallyReserved, "manual-link", ReservationLinkSource.Manual)

        val report = ConferenceStatusReport.from(conference, generatedAt = Later)

        assertEquals(
            CapacityStatus(
                bucket = CapacityBucket.HighSchool,
                totalSlots = 6,
                assigned = 1,
                confirmed = 1,
                accepted = 1,
                reserved = 2,
                freeSlots = 1,
                overflow = 0,
            ),
            report.capacities.first(),
        )
        assertEquals(
            listOf(
                CommitteeStatus("High School General Assembly", CommitteeMode.HighSchool, 6, 1),
                CommitteeStatus("University Council", CommitteeMode.University, 2, 0),
            ),
            report.committees,
        )
        assertEquals(Later, report.generatedAt)
    }

    @Test
    fun `report exposes over capacity and incomplete mapped slots`() {
        val conference = conference()
        val university = committee(conference, "uni", "University Council", CommitteeMode.University, 1)
        val mapped = committee(conference, "ipc", "IPC", CommitteeMode.Mapped, 2)
        conference.committees += listOf(university, mapped)
        repeat(3) { index ->
            conference.delegates += delegate(
                conference = conference,
                id = "accepted-$index",
                paid = index != 2,
                status = "Accepted",
                highSchool = false,
                addToConference = false,
            )
        }

        val report = ConferenceStatusReport.from(conference, generatedAt = Later)
        val capacity = report.capacities.single { it.bucket == CapacityBucket.University }

        assertEquals(1, capacity.totalSlots)
        assertEquals(2, capacity.confirmed)
        assertEquals(1, capacity.accepted)
        assertEquals(2, capacity.overflow)
        assertEquals(0, capacity.freeSlots)
    }

    @Test
    fun `report charges mismatched occupants to their positions for both assignment sources`() {
        for (source in AssignmentSource.entries) {
            for (isHighSchool in listOf(true, false)) {
                val conference = conference()
                val highSchool = committee(conference, "hs", "High School", CommitteeMode.HighSchool, 2)
                val university = committee(conference, "uni", "University", CommitteeMode.University, 3)
                conference.committees += listOf(highSchool, university)
                val occupant = delegate(conference, "occupant", paid = true, status = "Accepted", highSchool = isHighSchool)
                val destination = if (isHighSchool) university else highSchool
                destination.slots.first().assign(occupant, source, Now)

                val report = ConferenceStatusReport.from(conference, Later)
                val hs = report.capacities.single { it.bucket == CapacityBucket.HighSchool }
                val uni = report.capacities.single { it.bucket == CapacityBucket.University }

                assertEquals(if (isHighSchool) 0 else 1, hs.assigned)
                assertEquals(if (isHighSchool) 1 else 0, uni.assigned)
                assertEquals(if (isHighSchool) 2 else 1, hs.freeSlots)
                assertEquals(if (isHighSchool) 2 else 3, uni.freeSlots)
                assertEquals(1, report.capacities.sumOf { it.committedSlots })
            }
        }
    }

    @Test
    fun `report charges mapped occupants only when their target mode is known`() {
        for (source in AssignmentSource.entries) {
            for (targetMode in listOf(CommitteeMode.HighSchool, CommitteeMode.University, null)) {
                for (isHighSchool in listOf(true, false)) {
                    val conference = conference()
                    val highSchool = committee(conference, "hs", "High School", CommitteeMode.HighSchool, 1)
                    val university = committee(conference, "uni", "University", CommitteeMode.University, 1)
                    val ipc = committee(conference, "ipc", "IPC", CommitteeMode.Mapped, 1)
                    conference.committees += listOf(highSchool, university, ipc)
                    ipc.positions.single().setMappedCommittee(
                        when (targetMode) {
                            CommitteeMode.HighSchool -> highSchool
                            CommitteeMode.University -> university
                            else -> null
                        },
                    )
                    val occupant = delegate(conference, "occupant", paid = true, status = "Assigned", highSchool = isHighSchool)
                    ipc.slots.single().assign(occupant, source, Now)

                    val report = ConferenceStatusReport.from(conference, Later)
                    val hs = report.capacities.single { it.bucket == CapacityBucket.HighSchool }
                    val uni = report.capacities.single { it.bucket == CapacityBucket.University }

                    assertEquals(if (targetMode == CommitteeMode.HighSchool) 1 else 0, hs.assigned)
                    assertEquals(if (targetMode == CommitteeMode.University) 1 else 0, uni.assigned)
                    assertEquals(1, hs.freeSlots)
                    assertEquals(1, uni.freeSlots)
                    assertEquals(if (targetMode == null) 1 else 0, report.incompleteMappedSlots)
                }
            }
        }
    }

    private fun conference(): Conference =
        Conference(
            id = ConferenceId("conference"),
            name = "KAMUN 2026",
            createdAt = Now,
            updatedAt = Now,
            lastMymunUpdateAt = Instant.ofEpochMilli(50),
        )

    private fun committee(
        conference: Conference,
        id: String,
        name: String,
        mode: CommitteeMode,
        slots: Int,
    ): Committee {
        val committee = Committee(
            id = CommitteeId(id),
            conference = conference,
            mymunName = name,
            slotsPerPosition = 1,
            mode = mode,
        )
        repeat(slots) { index ->
            val position = Position(PositionId("$id-position-$index"), committee, "Position $index")
            position.slots += Slot(SlotId("$id-slot-$index"), position, 1, Now)
            committee.positions += position
        }
        return committee
    }

    private fun delegate(
        conference: Conference,
        id: String,
        paid: Boolean,
        status: String,
        highSchool: Boolean,
        addToConference: Boolean = true,
    ): Delegate {
        val delegate = Delegate(
            id = DelegateId(id),
            conference = conference,
            name = PersonName("Delegate", id),
            email = null,
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
            paymentStatus = if (paid) PaymentStatus.Paid else PaymentStatus.Unpaid,
            isHeadDelegate = false,
        )
        val question = Question(
            id = QuestionId("question-$id"),
            questionText = "High schooler $id",
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
            id = QuestionAnswerId("answer-$id"),
            delegate = delegate,
            question = question,
            rawAnswer = if (highSchool) "Yes" else "No",
        )
        if (addToConference) conference.delegates += delegate
        return delegate
    }

    private fun link(
        reservation: Reservation,
        delegate: Delegate,
        id: String,
        source: ReservationLinkSource,
    ) {
        val link = ReservationDelegateLink(
            id = ReservationDelegateLinkId(id),
            reservation = reservation,
            delegate = delegate,
            source = source,
            createdAt = Now,
            updatedAt = Now,
        )
        reservation.delegateLinks += link
        link.attachToDelegate()
    }

    private companion object {
        val Now: Instant = Instant.ofEpochMilli(100)
        val Later: Instant = Instant.ofEpochMilli(200)
    }
}
