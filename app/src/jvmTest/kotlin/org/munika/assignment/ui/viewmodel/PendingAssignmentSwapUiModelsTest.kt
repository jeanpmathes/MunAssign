package org.munika.assignment.ui.viewmodel

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.munika.assignment.domain.model.AssignmentSource
import org.munika.assignment.domain.model.Committee
import org.munika.assignment.domain.model.CommitteeId
import org.munika.assignment.domain.model.CommitteeMode
import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.ConferenceId
import org.munika.assignment.domain.model.Delegate
import org.munika.assignment.domain.model.DelegateId
import org.munika.assignment.domain.model.DelegateProfile
import org.munika.assignment.domain.model.PaymentStatus
import org.munika.assignment.domain.model.PaymentTier
import org.munika.assignment.domain.model.PersonName
import org.munika.assignment.domain.model.Position
import org.munika.assignment.domain.model.PositionId
import org.munika.assignment.domain.model.Preference
import org.munika.assignment.domain.model.PreferenceId
import org.munika.assignment.domain.model.Slot
import org.munika.assignment.domain.model.SlotId

class PendingAssignmentSwapUiModelsTest {
    @Test
    fun `swap review shows both moves resulting heartbreak and destination mismatches`() {
        val fixture = Fixture()
        val source = fixture.position("source", CommitteeMode.HighSchool, PaymentTier.Reduced)
        val target = fixture.position("target", CommitteeMode.University, PaymentTier.Full)
        val alice = fixture.delegate("alice", PaymentTier.Reduced)
        val bob = fixture.delegate("bob", PaymentTier.Full)
        alice.rank(target.position, 1)
        source.assign(alice, AssignmentSource.Application)
        target.assign(bob, AssignmentSource.Application)

        val swap = requireNotNull(fixture.conference.toDelegateDetailUiState("alice")).swapOpportunities.single()

        assertEquals("source-slot", swap.sourceSlotId)
        assertEquals("target-slot", swap.targetSlotId)
        assertEquals("alice", swap.sourceDelegateId)
        assertEquals("bob", swap.targetDelegateId)
        assertEquals("source (source) slot 1", swap.source.beforeLabel)
        assertEquals("target (target) slot 1", swap.source.afterLabel)
        assertFalse(swap.source.heartbrokenAfter)
        assertTrue(swap.target.heartbrokenAfter)
        assertEquals(DelegateSchoolFit.Match, swap.source.schoolFit)
        assertEquals(DelegateSchoolFit.Mismatch, swap.target.schoolFit)
        assertEquals(PaymentTierMismatchDirection.ReducedIntoFull, swap.source.paymentTierMismatch?.direction)
        assertEquals(PaymentTierMismatchDirection.FullIntoReduced, swap.target.paymentTierMismatch?.direction)
        val slots = fixture.conference.toAssignmentBoardUiState().committees.flatMap { it.positions }.flatMap { it.slots }
        assertTrue(slots.single { it.id == source.id.value }.hasSwapOpportunity)
        assertFalse(slots.single { it.id == target.id.value }.hasSwapOpportunity)
    }

    @Test
    fun `preference heartbreak identifies pending and fixed confirmed occupants for unassigned delegate`() {
        val fixture = Fixture()
        val target = fixture.position("target")
        val confirmedSlot = Slot(SlotId("target-slot-2"), target.position, 2, Instant.EPOCH)
        target.position.slots += confirmedSlot
        val alice = fixture.delegate("alice")
        alice.rank(target.position, 1)
        target.assign(fixture.delegate("bob"), AssignmentSource.Application)
        confirmedSlot.assign(fixture.delegate("charlie"), AssignmentSource.Mymun)

        val detail = requireNotNull(fixture.conference.toDelegateDetailUiState("alice"))
        val preference = detail.preferences.single()

        assertTrue(detail.swapOpportunities.isEmpty())
        assertEquals(listOf("bob Delegate", "charlie Delegate"), preference.heartbrokenOccupants.map { it.delegateName })
        assertEquals(listOf(SlotBoardStatus.Pending, SlotBoardStatus.Assigned), preference.heartbrokenOccupants.map { it.status })
        assertTrue(requireNotNull(preference.heartbreakTooltip).contains("Confirmed (fixed)"))
    }

    @Test
    fun `swap review lists partners by preference rank then name`() {
        val fixture = Fixture()
        val source = fixture.position("source")
        val first = fixture.position("first")
        val second = fixture.position("second")
        val alice = fixture.delegate("alice")
        alice.rank(second.position, 2)
        alice.rank(first.position, 1)
        source.assign(alice, AssignmentSource.Application)
        first.assign(fixture.delegate("zulu"), AssignmentSource.Application)
        second.assign(fixture.delegate("alpha"), AssignmentSource.Application)
        val extra = Slot(SlotId("first-slot-2"), first.position, 2, Instant.EPOCH)
        first.position.slots += extra
        extra.assign(fixture.delegate("beta"), AssignmentSource.Application)

        val swaps = requireNotNull(fixture.conference.toDelegateDetailUiState("alice")).swapOpportunities

        assertEquals(listOf("beta", "zulu", "alpha"), swaps.map { it.targetDelegateId })
        assertEquals(listOf(1, 1, 2), swaps.map { it.preferenceRank })
    }

    private class Fixture {
        val conference = Conference(ConferenceId("conference"), "Conference", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)

        fun position(id: String, mode: CommitteeMode = CommitteeMode.University, tier: PaymentTier = PaymentTier.Full): Slot {
            val committee = Committee(CommitteeId(id), conference, id, slotsPerPosition = 1, mode = mode, paymentTier = tier)
            val position = Position(PositionId(id), committee, id)
            val slot = Slot(SlotId("$id-slot"), position, 1, Instant.EPOCH)
            conference.committees += committee
            committee.positions += position
            position.slots += slot
            return slot
        }

        fun delegate(id: String, tier: PaymentTier = PaymentTier.Full): Delegate = Delegate(
            id = DelegateId(id), conference = conference, name = PersonName(id, "Delegate"), email = null,
            profile = DelegateProfile(null, null, null, null, null, null, null, null, null, "Accepted"),
            paymentStatus = PaymentStatus.Paid, paymentTier = tier, isHeadDelegate = false,
        ).also { conference.delegates += it }
    }

    private fun Delegate.rank(position: Position, rank: Int) {
        preferences += Preference(PreferenceId("${id.value}-$rank"), this, position, rank, position.name)
    }
}
