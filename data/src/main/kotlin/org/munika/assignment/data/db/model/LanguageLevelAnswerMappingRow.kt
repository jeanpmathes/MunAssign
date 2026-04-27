package org.munika.assignment.data.db.model

data class LanguageLevelAnswerMappingRow(
    val id: String,
    val questionId: String,
    val rawValue: String,
    val mappedValue: String,
)
