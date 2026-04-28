package org.munika.assignment.domain.repository

/**
 * Change summary for a MyMUN update.
 *
 * Created, updated, and removed counters describe persisted row deltas. An `Updated` counter only
 * increments when at least one MyMUN-managed stored value changed, not merely because a row was
 * retained. `preferencesWritten` and `answersWritten` count workbook-managed child rows rewritten
 * for the successful update.
 */
data class UpdateSummary(
    val blockingErrorCount: Int = 0,
    val warningCount: Int = 0,
    val delegatesCreated: Int = 0,
    val delegatesUpdated: Int = 0,
    val delegatesRemoved: Int = 0,
    val committeesCreated: Int = 0,
    val committeesUpdated: Int = 0,
    val committeesRemoved: Int = 0,
    val positionsCreated: Int = 0,
    val positionsUpdated: Int = 0,
    val positionsRemoved: Int = 0,
    val slotsCreated: Int = 0,
    val assignmentsConfirmed: Int = 0,
    val pendingAssignmentsPreserved: Int = 0,
    val pendingAssignmentsCleared: Int = 0,
    val questionsCreated: Int = 0,
    val questionsUpdated: Int = 0,
    val questionsRemoved: Int = 0,
    val delegationsCreated: Int = 0,
    val delegationsUpdated: Int = 0,
    val delegationsRemoved: Int = 0,
    val preferencesWritten: Int = 0,
    val answersWritten: Int = 0,
)
