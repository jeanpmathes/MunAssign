package org.munika.assignment.domain.repository

import org.munika.assignment.domain.model.Conference

/**
 * Persistence boundary for conference aggregates.
 *
 * Implementations belong outside `:domain` and must return complete domain graphs.
 */
interface ConferenceRepository {
    suspend fun listConferences(): List<Conference>
}
