package org.munika.assignment.domain.repository

import org.munika.assignment.domain.model.AnswerIssueResolutionId
import org.munika.assignment.domain.model.CommitteeId
import org.munika.assignment.domain.model.CommitteeMode
import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.ConferenceId
import org.munika.assignment.domain.model.DelegateCommentId
import org.munika.assignment.domain.model.DelegateId
import org.munika.assignment.domain.model.DelegationId
import org.munika.assignment.domain.model.MymunUpdateFilenameSignatureId
import org.munika.assignment.domain.model.PaymentTier
import org.munika.assignment.domain.model.PositionId
import org.munika.assignment.domain.model.QuestionConfiguration
import org.munika.assignment.domain.model.QuestionId
import org.munika.assignment.domain.model.ReservationId
import org.munika.assignment.domain.model.SlotId

/**
 * Persistence boundary for conference aggregates.
 *
 * Implementations belong outside `:domain` and must return complete domain graphs.
 */
interface ConferenceRepository {
    suspend fun listConferences(): List<Conference>

    suspend fun createConference(name: String): Conference

    suspend fun renameConference(conferenceId: ConferenceId, newName: String): Conference

    suspend fun deleteConference(conferenceId: ConferenceId)

    suspend fun reorderConferences(conferenceIdsInOrder: List<ConferenceId>)

    /** Deletes one organizer-trusted MyMUN update filename signature from a conference. */
    suspend fun deleteMymunUpdateFilenameSignature(
        conferenceId: ConferenceId,
        signatureId: MymunUpdateFilenameSignatureId,
    ): Conference

    /**
     * Sets the uniform slot count for every position in one committee.
     *
     * Implementations must create missing empty slots when increasing the count and reject
     * reductions that would remove occupied slots.
     */
    suspend fun setCommitteeSlotsPerPosition(
        conferenceId: ConferenceId,
        committeeId: CommitteeId,
        slotsPerPosition: Int,
    ): Conference

    /**
     * Marks whether one committee is intended for high-school delegates.
     *
     * This organizer-managed status is assignment guidance and must be preserved across MyMUN
     * updates for retained committees.
     */
    suspend fun setCommitteeHighSchoolStatus(
        conferenceId: ConferenceId,
        committeeId: CommitteeId,
        isHighSchoolCommittee: Boolean,
    ): Conference

    /**
     * Sets the organizer-managed committee assignment mode.
     *
     * Switching a committee away from [CommitteeMode.Mapped] clears its position mappings.
     * Switching a committee to [CommitteeMode.Mapped] must be rejected while other IPC positions
     * still map to it.
     */
    suspend fun setCommitteeMode(
        conferenceId: ConferenceId,
        committeeId: CommitteeId,
        mode: CommitteeMode,
    ): Conference

    /** Sets one committee's organizer-managed Full or Reduced assignment tier. */
    suspend fun setCommitteePaymentTier(
        conferenceId: ConferenceId,
        committeeId: CommitteeId,
        paymentTier: PaymentTier,
    ): Conference

    /** Classifies one MyMUN-paid delegate as Full or Reduced for assignment guidance. */
    suspend fun setDelegatePaymentTier(
        conferenceId: ConferenceId,
        delegateId: DelegateId,
        paymentTier: PaymentTier,
    ): Conference

    /**
     * Sets or clears the non-mapped committee that an IPC position corresponds to.
     *
     * Implementations must reject mappings for positions whose owning committee is not mapped,
     * mappings to committees from another conference, and mappings to mapped committees.
     */
    suspend fun setPositionMappedCommittee(
        conferenceId: ConferenceId,
        positionId: PositionId,
        mappedCommitteeId: CommitteeId?,
    ): Conference

    /**
     * Marks whether one delegation should prefill high-school reservation capacity.
     *
     * This organizer-managed setting must be preserved across MyMUN updates for retained
     * delegations and does not change delegate classifications or existing reservations.
     */
    suspend fun setDelegationHighSchoolStatus(
        conferenceId: ConferenceId,
        delegationId: DelegationId,
        isHighSchoolDelegation: Boolean,
    ): Conference

    /** Clears the persistent review marker from every delegation in one conference. */
    suspend fun markDelegationsReviewed(conferenceId: ConferenceId): Conference

    /**
     * Creates or replaces a pending MunAssign assignment for one slot.
     *
     * Implementations must never overwrite MyMUN-confirmed assignments. If the chosen delegate
     * already has a pending assignment elsewhere, [moveExistingPending] controls whether that
     * existing pending assignment is moved or rejected.
     */
    suspend fun assignDelegateToSlot(
        conferenceId: ConferenceId,
        slotId: SlotId,
        delegateId: DelegateId,
        moveExistingPending: Boolean,
    ): Conference

    /**
     * Replaces the complete pending selection for one position without changing confirmed slots.
     *
     * [selectedDelegateIds] must contain exactly one delegate for every non-confirmed slot. Existing
     * selected pending occupants retain their slots; new occupants fill the remaining slots in the
     * supplied order.
     */
    suspend fun setPositionPendingAssignments(
        conferenceId: ConferenceId,
        positionId: PositionId,
        selectedDelegateIds: List<DelegateId>,
    ): Conference

    /**
     * Clears a pending MunAssign assignment from one slot.
     *
     * MyMUN-confirmed assignments are controlled only by later MyMUN updates and must be rejected.
     */
    suspend fun clearPendingSlot(conferenceId: ConferenceId, slotId: SlotId): Conference

    /**
     * Deactivates one delegate for local assignment workflows.
     *
     * Implementations must reject delegates with MyMUN-confirmed assignments. Pending MunAssign
     * assignments are organizer-managed and should be cleared as part of deactivation.
     */
    suspend fun deactivateDelegate(conferenceId: ConferenceId, delegateId: DelegateId): Conference

    /**
     * Reactivates one delegate for local assignment workflows.
     *
     * Reactivation restores eligibility but must not recreate any previously cleared assignment.
     */
    suspend fun reactivateDelegate(conferenceId: ConferenceId, delegateId: DelegateId): Conference

    /**
     * Creates an organizer-managed reservation for high-school and/or university capacity.
     *
     * If [delegationId] is present, implementations must verify that the delegation belongs to
     * the same conference and reconcile automatic delegate links after persisting the reservation.
     */
    suspend fun createReservation(
        conferenceId: ConferenceId,
        name: String,
        highSchoolCount: Int,
        universityCount: Int,
        delegationId: DelegationId?,
    ): Conference

    /**
     * Updates an organizer-managed reservation and reconciles its delegate consumption.
     *
     * Implementations must reject reductions below existing manual high-school or university
     * delegate usage because manual links represent explicit organizer intent.
     */
    suspend fun updateReservation(
        conferenceId: ConferenceId,
        reservationId: ReservationId,
        name: String,
        highSchoolCount: Int,
        universityCount: Int,
        delegationId: DelegationId?,
    ): Conference

    /**
     * Deletes an organizer-managed reservation.
     *
     * Persisted delegate links for that reservation should be removed by the data store cascade,
     * followed by reconciliation of remaining reservations.
     */
    suspend fun deleteReservation(conferenceId: ConferenceId, reservationId: ReservationId): Conference

    /**
     * Manually consumes one reservation for one active delegate.
     *
     * Implementations must replace any previous reservation link for the delegate, enforce bucket
     * capacity for the delegate's high-school/university status, and reconcile automatic links.
     */
    suspend fun assignDelegateReservation(
        conferenceId: ConferenceId,
        delegateId: DelegateId,
        reservationId: ReservationId,
    ): Conference

    /**
     * Removes the manual reservation link for one delegate if present.
     *
     * Automatic delegation links are maintained by reconciliation and should be rejected rather
     * than deleted directly.
     */
    suspend fun removeDelegateReservation(conferenceId: ConferenceId, delegateId: DelegateId): Conference

    /**
     * Adds an organizer-managed note to one delegate.
     */
    suspend fun addDelegateComment(conferenceId: ConferenceId, delegateId: DelegateId, body: String): Conference

    /**
     * Updates an organizer-managed delegate note.
     */
    suspend fun updateDelegateComment(conferenceId: ConferenceId, commentId: DelegateCommentId, body: String): Conference

    /**
     * Deletes an organizer-managed delegate note.
     */
    suspend fun deleteDelegateComment(conferenceId: ConferenceId, commentId: DelegateCommentId): Conference

    /**
     * Updates organizer-managed handling for one imported MyMUN question.
     *
     * Implementations must preserve the imported question text and answers. Only local
     * configuration such as role, handling, visibility, label, rules, and typed mappings changes.
     */
    suspend fun updateQuestionConfiguration(
        conferenceId: ConferenceId,
        configuration: QuestionConfiguration,
    ): Conference

    /**
     * Updates every given question's configuration in one transaction and reorders them by their
     * display order.
     *
     * Implementations must apply all configurations and the reorder atomically, so a mid-batch
     * failure leaves no partially-saved configuration.
     */
    suspend fun updateQuestionConfigurations(
        conferenceId: ConferenceId,
        configurations: List<QuestionConfiguration>,
    ): Conference

    /**
     * Replaces the display order for imported questions in one conference.
     *
     * The provided list must contain every question in that conference exactly once.
     */
    suspend fun reorderQuestionConfigurations(
        conferenceId: ConferenceId,
        questionIdsInDisplayOrder: List<QuestionId>,
    ): Conference

    /**
     * Marks one currently derived delegate answer or delegate-level issue as resolved.
     *
     * Passing `null` for [questionId] resolves a delegate-level issue. Blank justification is
     * stored as absent text.
     */
    suspend fun resolveAnswerIssue(
        conferenceId: ConferenceId,
        delegateId: DelegateId,
        questionId: QuestionId?,
        issueKey: String,
        justification: String?,
    ): Conference

    /**
     * Updates the organizer justification on an existing resolved answer or delegate-level issue.
     */
    suspend fun updateAnswerIssueResolutionJustification(
        conferenceId: ConferenceId,
        resolutionId: AnswerIssueResolutionId,
        justification: String?,
    ): Conference

    /**
     * Reopens a previously resolved answer or delegate-level issue by deleting its resolution row.
     */
    suspend fun unresolveAnswerIssue(conferenceId: ConferenceId, resolutionId: AnswerIssueResolutionId): Conference

    suspend fun getConference(conferenceId: ConferenceId): Conference?

    /** Saves organizer choices for empty MyMUN preference ranks in one transaction. */
    suspend fun savePreferenceCompletions(
        conferenceId: ConferenceId,
        selections: List<PreferenceCompletionSelection>,
    ): Conference

    /** Clears one organizer-completed preference so a later empty update offers it again. */
    suspend fun clearPreferenceCompletion(
        conferenceId: ConferenceId,
        delegateId: DelegateId,
        rank: Int,
    ): Conference

    /** Atomically saves complete corrected preference sequences for the supplied delegates. */
    suspend fun savePreferenceCorrections(
        conferenceId: ConferenceId,
        selections: List<PreferenceCorrectionSelection>,
    ): Conference

    /** Removes every organizer preference completion, empty acknowledgement, and rank correction. */
    suspend fun resetPreferenceCorrections(conferenceId: ConferenceId): Conference
}
