package org.munika.assignment.domain.repository

import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.QuestionConfiguration

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

    /**
     * Marks whether one committee is intended for high-school delegates.
     *
     * This organizer-managed status is assignment guidance and must be preserved across MyMUN
     * updates for retained committees.
     */
    suspend fun setCommitteeHighSchoolStatus(
        conferenceId: String,
        committeeId: String,
        isHighSchoolCommittee: Boolean,
    ): Conference

    /**
     * Creates or replaces a pending MunAssign assignment for one slot.
     *
     * Implementations must never overwrite MyMUN-confirmed assignments. If the chosen delegate
     * already has a pending assignment elsewhere, [moveExistingPending] controls whether that
     * existing pending assignment is moved or rejected.
     */
    suspend fun assignDelegateToSlot(
        conferenceId: String,
        slotId: String,
        delegateId: String,
        moveExistingPending: Boolean,
    ): Conference

    /**
     * Clears a pending MunAssign assignment from one slot.
     *
     * MyMUN-confirmed assignments are controlled only by later MyMUN updates and must be rejected.
     */
    suspend fun clearPendingSlot(conferenceId: String, slotId: String): Conference

    /**
     * Deactivates one delegate for local assignment workflows.
     *
     * Implementations must reject delegates with MyMUN-confirmed assignments. Pending MunAssign
     * assignments are organizer-managed and should be cleared as part of deactivation.
     */
    suspend fun deactivateDelegate(conferenceId: String, delegateId: String): Conference

    /**
     * Reactivates one delegate for local assignment workflows.
     *
     * Reactivation restores eligibility but must not recreate any previously cleared assignment.
     */
    suspend fun reactivateDelegate(conferenceId: String, delegateId: String): Conference

    /**
     * Adds an organizer-managed note to one delegate.
     */
    suspend fun addDelegateComment(conferenceId: String, delegateId: String, body: String): Conference

    /**
     * Updates an organizer-managed delegate note.
     */
    suspend fun updateDelegateComment(conferenceId: String, commentId: String, body: String): Conference

    /**
     * Deletes an organizer-managed delegate note.
     */
    suspend fun deleteDelegateComment(conferenceId: String, commentId: String): Conference

    /**
     * Updates organizer-managed handling for one imported MyMUN question.
     *
     * Implementations must preserve the imported question text and answers. Only local
     * configuration such as role, handling, visibility, label, rules, and typed mappings changes.
     */
    suspend fun updateQuestionConfiguration(
        conferenceId: String,
        configuration: QuestionConfiguration,
    ): Conference

    /**
     * Replaces the display order for imported questions in one conference.
     *
     * The provided list must contain every question in that conference exactly once.
     */
    suspend fun reorderQuestionConfigurations(
        conferenceId: String,
        questionIdsInDisplayOrder: List<String>,
    ): Conference

    suspend fun getConference(conferenceId: String): Conference?
}
