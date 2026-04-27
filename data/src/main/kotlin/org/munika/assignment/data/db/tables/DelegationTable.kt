package org.munika.assignment.data.db.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object DelegationTable : Table("delegations") {
    val id = varchar("id", 36)
    val conferenceId = varchar("conference_id", 36).references(ConferenceTable.id, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 255)
    val normalizedName = varchar("normalized_name", 255)
    val country = varchar("country", 255).nullable()
    val expectedDelegateCount = integer("expected_delegate_count").nullable()
    val currentDelegateCountFromMymun = integer("current_delegate_count_from_mymun").nullable()
    val headDelegateId = varchar("head_delegate_id", 36).nullable()

    override val primaryKey = PrimaryKey(id)
}
