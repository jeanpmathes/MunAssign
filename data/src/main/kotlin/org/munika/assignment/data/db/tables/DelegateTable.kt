package org.munika.assignment.data.db.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object DelegateTable : Table("delegates") {
    val id = varchar("id", 36)
    val conferenceId = varchar("conference_id", 36).references(ConferenceTable.id, onDelete = ReferenceOption.CASCADE)
    val delegationId = varchar("delegation_id", 36).nullable()
    val firstName = varchar("first_name", 255)
    val lastName = varchar("last_name", 255)
    val normalizedFirstName = varchar("normalized_first_name", 255)
    val normalizedLastName = varchar("normalized_last_name", 255)
    val email = varchar("email", 320).nullable()
    val normalizedEmail = varchar("normalized_email", 320).nullable()
    val country = varchar("country", 255).nullable()
    val city = varchar("city", 255).nullable()
    val streetAddress = text("street_address").nullable()
    val zipCode = varchar("zip_code", 64).nullable()
    val diet = varchar("diet", 255).nullable()
    val phone = varchar("phone", 128).nullable()
    val nationality = varchar("nationality", 255).nullable()
    val dateOfBirth = varchar("date_of_birth", 10).nullable()
    val delegationNameFromMymun = varchar("delegation_name_from_mymun", 255).nullable()
    val applicationStatus = varchar("application_status", 255).nullable()
    val paymentStatus = varchar("payment_status", 32)
    val paymentTier = varchar("payment_tier", 32).nullable()
    val active = bool("active")
    val isHeadDelegate = bool("is_head_delegate")
    val createdAtFromMymunEpochMillis = long("created_at_from_mymun_epoch_millis").nullable()
    val submittedAtEpochMillis = long("submitted_at_epoch_millis").nullable()

    override val primaryKey = PrimaryKey(id)
}
