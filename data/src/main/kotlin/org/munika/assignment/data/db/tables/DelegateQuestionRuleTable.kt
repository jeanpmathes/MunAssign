package org.munika.assignment.data.db.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object DelegateQuestionRuleTable : Table("delegate_question_rules") {
    val id = varchar("id", 36)
    val questionId = varchar("question_id", 36).references(DelegateQuestionTable.id, onDelete = ReferenceOption.CASCADE)
    val ruleTypeId = varchar("rule_type_id", 64).references(AnswerRuleTypeTable.id)
    val intParameter = integer("int_parameter").nullable()
    val textParameter = text("text_parameter").nullable()

    override val primaryKey = PrimaryKey(id)
}
