package org.munika.assignment.data.db.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object PreferenceTable : Table("preferences") {
    val id = varchar("id", 36)
    val delegateId = varchar("delegate_id", 36).references(DelegateTable.id, onDelete = ReferenceOption.CASCADE)
    val positionId = varchar("position_id", 36).references(PositionTable.id, onDelete = ReferenceOption.CASCADE)
    val rank = integer("rank")
    val rawPreferenceText = text("raw_preference_text")

    override val primaryKey = PrimaryKey(id)
}
