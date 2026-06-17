package org.munika.assignment.domain.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
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
        val position = Position(PositionId("ipc-position"), ipc, "UNSC", mappedCommittee = target)

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

    private fun conference(): Conference =
        Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)

    private fun committee(conference: Conference, id: String, mode: CommitteeMode): Committee =
        Committee(CommitteeId(id), conference, id.uppercase(), slotsPerPosition = 1, mode = mode).also {
            conference.committees += it
        }
}
