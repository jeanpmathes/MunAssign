package org.munika.assignment.data.db.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object PositionTable : Table("positions") {
    val id = varchar("id", 36)
    val conferenceId = varchar("conference_id", 36).references(ConferenceTable.id, onDelete = ReferenceOption.CASCADE)
    val committeeId = varchar("committee_id", 36).references(CommitteeTable.id, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 255)
    val normalizedName = varchar("normalized_name", 255)

    override val primaryKey = PrimaryKey(id)
}
