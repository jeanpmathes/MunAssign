package org.munika.assignment.data.db.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object MunExperienceAnswerMappingTable : Table("mun_experience_answer_mappings") {
    val id = varchar("id", 36)
    val questionId = varchar("question_id", 36).references(DelegateQuestionTable.id, onDelete = ReferenceOption.CASCADE)
    val rawValue = varchar("raw_value", 255)
    val normalizedRawValue = varchar("normalized_raw_value", 255)
    val mappedValue = varchar("mapped_value", 64)

    override val primaryKey = PrimaryKey(id)
}
