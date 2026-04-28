package org.munika.assignment.data.db.model

data class ConferenceRow(
    val id: String,
    val name: String,
    val displayOrder: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val lastMymunUpdateAtEpochMillis: Long?,
)
