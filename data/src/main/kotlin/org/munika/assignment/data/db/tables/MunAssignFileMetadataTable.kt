package org.munika.assignment.data.db.tables

import org.jetbrains.exposed.v1.core.Table

/** Identifies the purpose and stable identity of one MunAssign SQLite file. */
object MunAssignFileMetadataTable : Table("munassign_file_metadata") {
    val singleton = integer("singleton")
    val fileId = varchar("file_id", 36).uniqueIndex()
    val kind = varchar("kind", 64)

    override val primaryKey = PrimaryKey(singleton)
}
