package org.munika.assignment.domain.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SlotTest {
    @Test
    fun `new slot starts empty`() {
        val slot = board().firstSlot

        assertEquals(AssignmentStatus.Empty, slot.status)
        assertNull(slot.delegate)
        assertNull(slot.source)
        assertTrue(slot.isEmpty)
    }

    @Test
    fun `assigning from application creates pending assignment`() {
        val board = board()
        val assignedAt = Instant.ofEpochMilli(100)

        board.firstSlot.assign(board.delegate, AssignmentSource.Application, assignedAt)

        assertEquals(AssignmentStatus.Pending, board.firstSlot.status)
        assertSame(board.delegate, board.firstSlot.delegate)
        assertEquals(AssignmentSource.Application, board.firstSlot.source)
        assertEquals(assignedAt, board.firstSlot.updatedAt)
        assertSame(board.firstSlot, board.delegate.currentSlot)
    }

    @Test
    fun `assigning from mymun creates confirmed assignment`() {
        val board = board()

        board.firstSlot.assign(board.delegate, AssignmentSource.Mymun, Instant.EPOCH)

        assertEquals(AssignmentStatus.Assigned, board.firstSlot.status)
        assertEquals(AssignmentStatus.Assigned, board.delegate.assignmentStatus)
    }

    @Test
    fun `clearing an assignment makes the slot and delegate unassigned again`() {
        val board = board()
        board.firstSlot.assign(board.delegate, AssignmentSource.Application, Instant.EPOCH)

        board.firstSlot.clearAssignment(Instant.ofEpochMilli(200))

        assertEquals(AssignmentStatus.Empty, board.firstSlot.status)
        assertNull(board.firstSlot.delegate)
        assertNull(board.firstSlot.source)
        assertNull(board.delegate.currentSlot)
        assertEquals(Instant.ofEpochMilli(200), board.firstSlot.updatedAt)
    }

    @Test
    fun `assigning a delegate already occupying another slot throws and leaves target unchanged`() {
        val board = board(slotCount = 2)
        board.firstSlot.assign(board.delegate, AssignmentSource.Application, Instant.EPOCH)

        assertFailsWith<IllegalArgumentException> {
            board.secondSlot.assign(board.delegate, AssignmentSource.Mymun, Instant.ofEpochMilli(50))
        }

        assertEquals(AssignmentStatus.Empty, board.secondSlot.status)
        assertNull(board.secondSlot.delegate)
        assertSame(board.firstSlot, board.delegate.currentSlot)
    }

    @Test
    fun `assignment requires delegate from same conference`() {
        val board = board()
        val outsider = board(conferenceId = "other").delegate

        assertFailsWith<IllegalArgumentException> {
            board.firstSlot.assign(outsider, AssignmentSource.Application, Instant.EPOCH)
        }
    }

    private fun board(conferenceId: String = "conference", slotCount: Int = 1): Board {
        val conference = Conference(ConferenceId(conferenceId), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val committee = Committee(CommitteeId("$conferenceId-committee"), conference, "UNSC", slotsPerPosition = slotCount)
        val position = Position(PositionId("$conferenceId-position"), committee, "France")
        val delegate = Delegate(
            id = DelegateId("$conferenceId-delegate"),
            conference = conference,
            name = PersonName("Ada", "Lovelace"),
            email = "ada@example.test",
            profile = DelegateProfile(null, null, null, null, null, null, null, null, null, null),
            paymentStatus = PaymentStatus.Paid,
            isHeadDelegate = false,
        )

        conference.committees += committee
        conference.delegates += delegate
        committee.positions += position
        position.slots += (1..slotCount).map { Slot(SlotId("$conferenceId-slot-$it"), position, it, Instant.EPOCH) }

        return Board(conference, delegate, position.slots[0], position.slots.getOrNull(1))
    }

    private data class Board(
        val conference: Conference,
        val delegate: Delegate,
        val firstSlot: Slot,
        val optionalSecondSlot: Slot?,
    ) {
        val secondSlot: Slot
            get() = requireNotNull(optionalSecondSlot)
    }
}
