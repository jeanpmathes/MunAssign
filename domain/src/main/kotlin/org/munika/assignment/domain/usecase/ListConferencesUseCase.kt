package org.munika.assignment.domain.usecase

import org.munika.assignment.domain.repository.ConferenceRepository

class ListConferencesUseCase(
    private val conferenceRepository: ConferenceRepository,
) {
    suspend operator fun invoke() = conferenceRepository.listConferences()
}
