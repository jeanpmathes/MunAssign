package org.munika.assignment.domain.repository

import org.munika.assignment.domain.model.Conference

/**
 * Persistence boundary for conference aggregates.
 *
 * Implementations belong outside `:domain` and must return complete domain graphs.
 */
interface ConferenceRepository {
    suspend fun listConferences(): List<Conference>

    suspend fun createConference(name: String): Conference

    suspend fun renameConference(conferenceId: String, newName: String): Conference

    suspend fun deleteConference(conferenceId: String)

    suspend fun reorderConferences(conferenceIdsInOrder: List<String>)

    /**
     * Sets the uniform slot count for every position in one committee.
     *
     * Implementations must create missing empty slots when increasing the count and reject
     * reductions that would remove occupied slots.
     */
    suspend fun setCommitteeSlotsPerPosition(
        conferenceId: String,
        committeeId: String,
        slotsPerPosition: Int,
    ): Conference

    suspend fun getConference(conferenceId: String): Conference?
}
