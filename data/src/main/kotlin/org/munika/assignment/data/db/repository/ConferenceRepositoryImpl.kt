package org.munika.assignment.data.db.repository

import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.repository.ConferenceRepository

class ConferenceRepositoryImpl : ConferenceRepository {
    override suspend fun listConferences(): List<Conference> = emptyList()
}
