package org.munika.assignment.data.db.tables

import org.jetbrains.exposed.v1.core.Table

object ConferenceTable : Table("conferences") {
    val id = varchar("id", 36)
    val name = varchar("name", 255)
    val displayOrder = integer("display_order")
    val createdAtEpochMillis = long("created_at_epoch_millis")
    val updatedAtEpochMillis = long("updated_at_epoch_millis")
    val lastMymunUpdateAtEpochMillis = long("last_mymun_update_at_epoch_millis").nullable()
    val lastMymunPreferenceRanks = varchar("last_mymun_preference_ranks", 512).nullable()

    override val primaryKey = PrimaryKey(id)
}
