package org.munika.assignment.data.db.model

/**
 * Table-shaped reservation delegate link data loaded before graph mapping.
 */
data class ReservationDelegateLinkRow(
    val id: String,
    val reservationId: String,
    val delegateId: String,
    val source: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
