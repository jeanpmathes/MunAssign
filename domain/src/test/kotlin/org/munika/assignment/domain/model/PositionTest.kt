package org.munika.assignment.domain.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PositionTest {
    @Test
    fun `normal position inherits owning committee school level`() {
        val conference = conference()
        val highSchool = committee(conference, "hs", CommitteeMode.HighSchool)
        val university = committee(conference, "uni", CommitteeMode.University)
        val hsPosition = Position(PositionId("hs-position"), highSchool, "France")
        val uniPosition = Position(PositionId("uni-position"), university, "Germany")

        assertEquals(CommitteeMode.HighSchool, hsPosition.effectiveCommitteeMode)
        assertEquals(CommitteeMode.University, uniPosition.effectiveCommitteeMode)
        assertEquals(true, hsPosition.isEffectivelyHighSchool)
        assertEquals(false, uniPosition.isEffectivelyHighSchool)
    }

    @Test
    fun `mapped position inherits mapped target school level`() {
        val conference = conference()
        val ipc = committee(conference, "ipc", CommitteeMode.Mapped)
        val target = committee(conference, "target", CommitteeMode.HighSchool)
        val position = Position(PositionId("ipc-position"), ipc, "UNSC").also { ipc.positions += it }
        position.setMappedCommittee(target)

        assertEquals(CommitteeMode.HighSchool, position.effectiveCommitteeMode)
        assertEquals(true, position.isEffectivelyHighSchool)
        assertFalse(position.mappingIncomplete)
    }

    @Test
    fun `unmapped mapped position has incomplete school level`() {
        val conference = conference()
        val ipc = committee(conference, "ipc", CommitteeMode.Mapped)
        val position = Position(PositionId("ipc-position"), ipc, "UNSC")

        assertNull(position.effectiveCommitteeMode)
        assertNull(position.isEffectivelyHighSchool)
        assertTrue(position.mappingIncomplete)
    }

    @Test
    fun `mapping requires a mapped owning committee`() {
        val conference = conference()
        val owner = committee(conference, "owner", CommitteeMode.University)
        val target = committee(conference, "target", CommitteeMode.HighSchool)
        val position = Position(PositionId("position"), owner, "France")

        assertFailsWith<IllegalArgumentException> { position.setMappedCommittee(target) }
    }

    @Test
    fun `mapping target must belong to the same conference`() {
        val conference = conference()
        val otherConference = Conference(
            ConferenceId("other"),
            "Other",
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        val owner = committee(conference, "owner", CommitteeMode.Mapped)
        val target = committee(otherConference, "target", CommitteeMode.HighSchool)
        val position = Position(PositionId("position"), owner, "France")

        assertFailsWith<IllegalArgumentException> { position.setMappedCommittee(target) }
    }

    @Test
    fun `mapping target must not be mapped`() {
        val conference = conference()
        val owner = committee(conference, "owner", CommitteeMode.Mapped)
        val target = committee(conference, "target", CommitteeMode.Mapped)
        val position = Position(PositionId("position"), owner, "France")

        assertFailsWith<IllegalArgumentException> { position.setMappedCommittee(target) }
    }

    @Test
    fun `sibling positions can share a mapping target`() {
        val conference = conference()
        val owner = committee(conference, "owner", CommitteeMode.Mapped)
        val target = committee(conference, "target", CommitteeMode.HighSchool)
        val first = Position(PositionId("first"), owner, "France").also { owner.positions += it }
        val second = Position(PositionId("second"), owner, "Germany").also { owner.positions += it }

        first.setMappedCommittee(target)
        second.setMappedCommittee(target)

        assertEquals(target, first.mappedCommittee)
        assertEquals(target, second.mappedCommittee)
        assertEquals(CommitteeMode.HighSchool, first.effectiveCommitteeMode)
        assertEquals(CommitteeMode.HighSchool, second.effectiveCommitteeMode)
    }

    @Test
    fun `mapping requires the position to be registered with its owning committee`() {
        val conference = conference()
        val owner = committee(conference, "owner", CommitteeMode.Mapped)
        val target = committee(conference, "target", CommitteeMode.HighSchool)
        val position = Position(PositionId("position"), owner, "France")

        assertFailsWith<IllegalArgumentException> { position.setMappedCommittee(target) }
    }

    @Test
    fun `mapping rejects a noncanonical target with the same id`() {
        val conference = conference()
        val owner = committee(conference, "owner", CommitteeMode.Mapped)
        committee(conference, "target", CommitteeMode.HighSchool)
        val detachedTarget = Committee(
            id = CommitteeId("target"),
            conference = conference,
            mymunName = "DETACHED",
            slotsPerPosition = 1,
            mode = CommitteeMode.HighSchool,
        )
        val position = Position(PositionId("position"), owner, "France").also { owner.positions += it }

        assertFailsWith<IllegalArgumentException> { position.setMappedCommittee(detachedTarget) }
    }

    private fun conference(): Conference =
        Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)

    private fun committee(conference: Conference, id: String, mode: CommitteeMode): Committee =
        Committee(CommitteeId(id), conference, id.uppercase(), slotsPerPosition = 1, mode = mode).also {
            conference.committees += it
        }
}
