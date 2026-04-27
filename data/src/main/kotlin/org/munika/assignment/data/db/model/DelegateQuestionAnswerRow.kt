package org.munika.assignment.data.db.model

data class DelegateQuestionAnswerRow(
    val id: String,
    val delegateId: String,
    val questionId: String,
    val answer: String?,
)
