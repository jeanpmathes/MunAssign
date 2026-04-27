package org.munika.assignment.data.db.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object MymunUpdateIssueTable : Table("mymun_update_issues") {
    val id = varchar("id", 36)
    val updateRunId = varchar("update_run_id", 36).references(MymunUpdateRunTable.id, onDelete = ReferenceOption.CASCADE)
    val severity = varchar("severity", 32)
    val message = text("message")
    val sheetName = varchar("sheet_name", 255).nullable()
    val rowNumber = integer("row_number").nullable()
    val columnName = varchar("column_name", 255).nullable()

    override val primaryKey = PrimaryKey(id)
}
