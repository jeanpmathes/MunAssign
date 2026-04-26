package org.munika.assignment.data.db.tables

import org.jetbrains.exposed.v1.core.Table

object ConferenceTable : Table("conferences") {
    val id = varchar("id", 36)
    val name = varchar("name", 255)

    override val primaryKey = PrimaryKey(id)
}
