package org.munika.assignment.data.db.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

/** Conference-owned trusted identities extracted from successful MyMUN update filenames. */
object MymunUpdateFilenameSignatureTable : Table("mymun_update_filename_signatures") {
    val id = varchar("id", 36)
    val conferenceId = varchar("conference_id", 36).references(ConferenceTable.id, onDelete = ReferenceOption.CASCADE)
    val trustedAtEpochMillis = long("trusted_at_epoch_millis")

    override val primaryKey = PrimaryKey(id)
}
