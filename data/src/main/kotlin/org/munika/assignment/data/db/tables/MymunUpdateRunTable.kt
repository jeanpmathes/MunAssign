package org.munika.assignment.data.db.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object MymunUpdateRunTable : Table("mymun_update_runs") {
    val id = varchar("id", 36)
    val conferenceId = varchar("conference_id", 36).references(ConferenceTable.id, onDelete = ReferenceOption.CASCADE)
    val startedAtEpochMillis = long("started_at_epoch_millis")
    val completedAtEpochMillis = long("completed_at_epoch_millis").nullable()
    val sourceFilename = varchar("source_filename", 255).nullable()

    override val primaryKey = PrimaryKey(id)
}
