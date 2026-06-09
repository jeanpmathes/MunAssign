package org.munika.assignment.data.db.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

/**
 * Organizer-managed reservation capacity owned by one conference.
 *
 * Optional delegation links mirror MyMUN delegation IDs and are cleared when the delegation no
 * longer exists.
 */
object ReservationTable : Table("reservations") {
    val id = varchar("id", 36)
    val conferenceId = varchar("conference_id", 36).references(ConferenceTable.id, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 255)
    val highSchoolCount = integer("high_school_count")
    val universityCount = integer("university_count")
    val delegationId = varchar("delegation_id", 36)
        .references(DelegationTable.id, onDelete = ReferenceOption.SET_NULL)
        .nullable()
    val createdAtEpochMillis = long("created_at_epoch_millis")
    val updatedAtEpochMillis = long("updated_at_epoch_millis")

    override val primaryKey = PrimaryKey(id)
}
