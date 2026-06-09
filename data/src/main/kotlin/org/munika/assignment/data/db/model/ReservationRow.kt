package org.munika.assignment.data.db.model

/**
 * Table-shaped reservation data loaded before graph mapping.
 */
data class ReservationRow(
    val id: String,
    val conferenceId: String,
    val name: String,
    val highSchoolCount: Int,
    val universityCount: Int,
    val delegationId: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
