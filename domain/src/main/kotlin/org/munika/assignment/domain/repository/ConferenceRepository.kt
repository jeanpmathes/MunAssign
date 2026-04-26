package org.munika.assignment.domain.repository

import org.munika.assignment.domain.model.Conference

interface ConferenceRepository {
    suspend fun listConferences(): List<Conference>
}
