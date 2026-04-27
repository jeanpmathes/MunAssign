package org.munika.assignment.data.db.model

data class DelegateQuestionRuleRow(
    val id: String,
    val questionId: String,
    val ruleTypeId: String,
    val intParameter: Int?,
    val textParameter: String?,
)
