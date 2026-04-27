package org.munika.assignment.domain.usecase

import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.repository.ConferenceRepository

/**
 * Lists conferences available in local storage.
 */
class ListConferencesUseCase(
    private val conferenceRepository: ConferenceRepository,
) {
    suspend operator fun invoke(): List<Conference> =
        conferenceRepository.listConferences()
}
