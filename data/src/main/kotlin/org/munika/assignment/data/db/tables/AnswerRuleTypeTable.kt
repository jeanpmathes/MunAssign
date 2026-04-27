package org.munika.assignment.data.db.tables

import org.jetbrains.exposed.v1.core.Table

object AnswerRuleTypeTable : Table("answer_rule_types") {
    val id = varchar("id", 64)
    val name = varchar("name", 255)

    override val primaryKey = PrimaryKey(id)
}
