package org.munika.assignment.data.db.model

data class MunExperienceAnswerMappingRow(
    val id: String,
    val questionId: String,
    val rawValue: String,
    val mappedValue: String,
)
