package org.munika.assignment.data.db.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

/**
 * Current reservation consumption by delegates.
 *
 * The delegate unique index enforces the domain rule that one delegate can consume at most one
 * reservation at a time.
 */
object ReservationDelegateLinkTable : Table("reservation_delegate_links") {
    val id = varchar("id", 36)
    val reservationId = varchar("reservation_id", 36).references(ReservationTable.id, onDelete = ReferenceOption.CASCADE)
    val delegateId = varchar("delegate_id", 36).references(DelegateTable.id, onDelete = ReferenceOption.CASCADE)
    val linkSource = varchar("source", 32)
    val createdAtEpochMillis = long("created_at_epoch_millis")
    val updatedAtEpochMillis = long("updated_at_epoch_millis")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("ux_reservation_delegate_links_delegate", delegateId)
    }
}
