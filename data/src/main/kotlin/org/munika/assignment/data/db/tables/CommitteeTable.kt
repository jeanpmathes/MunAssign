package org.munika.assignment.data.db.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.munika.assignment.domain.model.CommitteeMode
import org.munika.assignment.domain.model.PaymentTier

object CommitteeTable : Table("committees") {
    val id = varchar("id", 36)
    val conferenceId = varchar("conference_id", 36).references(ConferenceTable.id, onDelete = ReferenceOption.CASCADE)
    val mymunName = varchar("mymun_name", 255)
    val normalizedMymunName = varchar("normalized_mymun_name", 255)
    val alias = varchar("alias", 255).nullable()
    val slotsPerPosition = integer("slots_per_position")
    val isHighSchoolCommittee = bool("is_high_school_committee").default(false)
    val mode = varchar("mode", 32).default(CommitteeMode.University.name)
    val paymentTier = varchar("payment_tier", 32).default(PaymentTier.Full.name)

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("ux_committees_conference_normalized_name", conferenceId, normalizedMymunName)
    }
}
