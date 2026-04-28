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

    suspend fun getConference(conferenceId: String): Conference?
}

data class UpdateResult(
    val success: Boolean,
    val issues: List<UpdateIssue>,
)

data class UpdateIssue(
    val severity: UpdateIssueSeverity,
    val message: String,
)

enum class UpdateIssueSeverity {
    Warning,
    Error,
}
