package org.munika.assignment.data.db.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object SlotTable : Table("slots") {
    val id = varchar("id", 36)
    val positionId = varchar("position_id", 36).references(PositionTable.id, onDelete = ReferenceOption.CASCADE)
    val slotIndex = integer("slot_index")
    val assignedDelegateId = varchar("assigned_delegate_id", 36)
        .references(DelegateTable.id, onDelete = ReferenceOption.SET_NULL)
        .nullable()
    val assignmentSource = varchar("assignment_source", 32).nullable()
    val updatedAtEpochMillis = long("updated_at_epoch_millis")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("ux_slots_position_slot_index", positionId, slotIndex)
        uniqueIndex("ux_slots_assigned_delegate", assignedDelegateId)
    }
}
