package org.munika.assignment.domain.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PendingAssignmentSwapTest {
    @Test
    fun `one way swap improves source without requiring partner preference`() {
        val board = Board()
        val alice = board.occupant("alice", "Canada")
        val bob = board.occupant("bob", "France")
        board.prefer(alice, requireNotNull(bob.currentPosition), 1)

        val swaps = board.conference.pendingAssignmentSwaps()

        assertEquals(listOf(bob), swaps.getValue(alice.id).map { it.targetDelegate })
        assertEquals(alice.currentSlot, swaps.getValue(alice.id).single().sourceSlot)
        assertTrue(swaps[bob.id].isNullOrEmpty())
    }

    @Test
    fun `mutual preference gives each person a swap opportunity`() {
        val board = Board()
        val alice = board.occupant("alice", "Canada")
        val bob = board.occupant("bob", "France")
        board.prefer(alice, requireNotNull(bob.currentPosition), 1)
        board.prefer(bob, requireNotNull(alice.currentPosition), 1)

        val swaps = board.conference.pendingAssignmentSwaps()

        assertEquals(bob, swaps.getValue(alice.id).single().targetDelegate)
        assertEquals(alice, swaps.getValue(bob.id).single().targetDelegate)
    }

    @Test
    fun `partners use preference rank then name then stable id across all slots`() {
        val board = Board()
        val alice = board.occupant("alice", "Canada")
        val zoe = board.occupant("zoe", "France")
        val amySecond = board.occupant("amy-b", "France", "Amy")
        val amyFirst = board.occupant("amy-a", "France", "Amy")
        val aaron = board.occupant("aaron", "Germany")
        board.prefer(alice, requireNotNull(zoe.currentPosition), 1)
        board.prefer(alice, requireNotNull(aaron.currentPosition), 2)

        assertEquals(
            listOf(amyFirst, amySecond, zoe, aaron),
            board.conference.pendingAssignmentSwaps().getValue(alice.id).map { it.targetDelegate },
        )
    }

    @Test
    fun `confirmed ineligible and satisfied occupants never participate`() {
        val board = Board()
        val alice = board.occupant("alice", "Canada")
        val confirmed = board.occupant("confirmed", "France", source = AssignmentSource.Mymun)
        val inactive = board.occupant("inactive", "Germany").also { it.active = false }
        val unpaid = board.occupant("unpaid", "Italy", paid = false)
        val unusable = board.occupant("unusable", "Spain", applicationStatus = "Submitted")
        val satisfied = board.occupant("satisfied", "Austria")
        board.prefer(satisfied, requireNotNull(satisfied.currentPosition), 1)
        listOf(confirmed, inactive, unpaid, unusable, satisfied).forEachIndexed { index, delegate ->
            board.prefer(alice, requireNotNull(delegate.currentPosition), index + 1)
            if (delegate !== satisfied) board.prefer(delegate, requireNotNull(alice.currentPosition), 1)
        }

        assertTrue(board.conference.pendingAssignmentSwaps().values.all { it.isEmpty() })
    }

    private class Board {
        val conference = Conference(ConferenceId("conf"), "Conference", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        private val committee = Committee(CommitteeId("committee"), conference, "GA", slotsPerPosition = 4)

        init { conference.committees += committee }

        fun occupant(
            id: String,
            positionName: String,
            firstName: String = id,
            source: AssignmentSource = AssignmentSource.Application,
            paid: Boolean = true,
            applicationStatus: String = "Accepted",
        ): Delegate {
            val position = committee.positions.find { it.name == positionName } ?: Position(
                PositionId(positionName), committee, positionName,
            ).also { committee.positions += it }
            val slot = Slot(SlotId(id), position, position.slots.size + 1, Instant.EPOCH).also { position.slots += it }
            return Delegate(
                DelegateId(id), conference, PersonName(firstName, "Person"), "$id@example.test",
                DelegateProfile(null, null, null, null, null, null, null, null, null, applicationStatus),
                if (paid) PaymentStatus.Paid else PaymentStatus.Unpaid, isHeadDelegate = false,
            ).also { conference.delegates += it; slot.assign(it, source, Instant.EPOCH) }
        }

        fun prefer(delegate: Delegate, position: Position, rank: Int) {
            delegate.preferences += Preference(PreferenceId("${delegate.id.value}-$rank"), delegate, position, rank, position.name)
        }
    }
}
