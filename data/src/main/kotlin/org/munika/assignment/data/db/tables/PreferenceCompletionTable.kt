package org.munika.assignment.data.db.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

/** Organizer-corrected preference sequences, including reordered and explicitly empty ranks. */
object PreferenceCompletionTable : Table("preference_completions") {
    val id = varchar("id", 36)
    val delegateId = varchar("delegate_id", 36).references(DelegateTable.id, onDelete = ReferenceOption.CASCADE)
    val positionId = varchar("position_id", 36)
        .references(PositionTable.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val rank = integer("rank")
    val origin = varchar("origin", 32).nullable()
    val emptyAcknowledged = bool("empty_acknowledged").default(false)
    val baselinePositionId = varchar("baseline_position_id", 36)
        .references(PositionTable.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val baselineRawPreferenceText = text("baseline_raw_preference_text").nullable()
    val detectedRanks = varchar("detected_ranks", 512).nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("ux_preference_completions_delegate_rank", delegateId, rank)
        uniqueIndex("ux_preference_completions_delegate_position", delegateId, positionId)
    }
}
