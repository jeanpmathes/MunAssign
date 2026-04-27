package org.munika.assignment.data.db.repository

import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.repository.ConferenceRepository

/**
 * SQLite-backed conference repository.
 *
 * The implementation is still a placeholder until conference CRUD is wired to Exposed.
 */
class ConferenceRepositoryImpl : ConferenceRepository {
    override suspend fun listConferences(): List<Conference> = emptyList()
}
