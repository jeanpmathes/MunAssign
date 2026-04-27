package org.munika.assignment.data.db.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object DelegateQuestionAnswerTable : Table("delegate_question_answers") {
    val id = varchar("id", 36)
    val delegateId = varchar("delegate_id", 36).references(DelegateTable.id, onDelete = ReferenceOption.CASCADE)
    val questionId = varchar("question_id", 36).references(DelegateQuestionTable.id, onDelete = ReferenceOption.CASCADE)
    val answer = text("answer").nullable()

    override val primaryKey = PrimaryKey(id)
}
