package org.munika.assignment.ui.viewmodel

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.munika.assignment.domain.model.AssignmentSource
import org.munika.assignment.domain.model.Committee
import org.munika.assignment.domain.model.CommitteeId
import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.ConferenceId
import org.munika.assignment.domain.model.Delegate
import org.munika.assignment.domain.model.DelegateId
import org.munika.assignment.domain.model.DelegateProfile
import org.munika.assignment.domain.model.PaymentStatus
import org.munika.assignment.domain.model.PersonName
import org.munika.assignment.domain.model.Position
import org.munika.assignment.domain.model.PositionId
import org.munika.assignment.domain.model.Preference
import org.munika.assignment.domain.model.PreferenceId
import org.munika.assignment.domain.model.Slot
import org.munika.assignment.domain.model.SlotId

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

    private fun board(slotCount: Int = 1): Board {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val committee = Committee(CommitteeId("committee"), conference, "GA", slotsPerPosition = slotCount)
        val france = Position(PositionId("france"), committee, "France")
        val germany = Position(PositionId("germany"), committee, "Germany")
        val available = delegate(conference, "available", "Available", "Delegate", PaymentStatus.Paid, active = true)
        val unpaid = delegate(conference, "unpaid", "Unpaid", "Delegate", PaymentStatus.Unpaid, active = true)
        val inactive = delegate(conference, "inactive", "Inactive", "Delegate", PaymentStatus.Paid, active = false)
        val confirmedElsewhere = delegate(conference, "confirmed", "Confirmed", "Elsewhere", PaymentStatus.Paid, active = true)

        conference.committees += committee
        committee.positions += listOf(france, germany)
        conference.delegates += listOf(available, unpaid, inactive, confirmedElsewhere)
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

    private fun delegate(
        conference: Conference,
        id: String,
        firstName: String,
        lastName: String,
        paymentStatus: PaymentStatus,
        active: Boolean,
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
        )

    private data class Board(
        val conference: Conference,
        val france: Position,
        val germanySlot: Slot,
        val available: Delegate,
        val confirmedElsewhere: Delegate,
    )
}
