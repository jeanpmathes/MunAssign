package org.munika.assignment.data.db.model

data class DelegateQuestionRow(
    val id: String,
    val conferenceId: String,
    val questionText: String,
    val roleId: String?,
    val handlingTypeId: String,
    val visibleInDelegateDetail: Boolean,
    val organizerLabel: String?,
)
