package org.munika.assignment.data.db.model

data class SlotRow(
    val id: String,
    val positionId: String,
    val slotIndex: Int,
    val assignedDelegateId: String?,
    val assignmentSource: String?,
    val updatedAtEpochMillis: Long,
)
