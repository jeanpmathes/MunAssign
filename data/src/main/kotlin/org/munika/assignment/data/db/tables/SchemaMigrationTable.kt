package org.munika.assignment.data.db.tables

import org.jetbrains.exposed.v1.core.Table

object SchemaMigrationTable : Table("schema_migrations") {
    val version = integer("version")
    val name = varchar("name", 255)
    val appliedAtEpochMillis = long("applied_at_epoch_millis")

    override val primaryKey = PrimaryKey(version)
}
