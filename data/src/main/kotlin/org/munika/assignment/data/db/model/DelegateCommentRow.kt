package org.munika.assignment.data.db.model

data class DelegateCommentRow(
    val id: String,
    val delegateId: String,
    val body: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)
