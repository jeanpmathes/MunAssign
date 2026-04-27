package org.munika.assignment.data.db.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object CommitteeTable : Table("committees") {
    val id = varchar("id", 36)
    val conferenceId = varchar("conference_id", 36).references(ConferenceTable.id, onDelete = ReferenceOption.CASCADE)
    val mymunName = varchar("mymun_name", 255)
    val normalizedMymunName = varchar("normalized_mymun_name", 255)
    val alias = varchar("alias", 255).nullable()
    val slotsPerPosition = integer("slots_per_position")

    override val primaryKey = PrimaryKey(id)
}
