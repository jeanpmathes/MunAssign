package org.munika.assignment.data.db.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

/** Ordered readable and normalized parts belonging to one trusted filename signature. */
object MymunUpdateFilenamePartTable : Table("mymun_update_filename_signature_parts") {
    val signatureId = varchar("signature_id", 36).references(
        MymunUpdateFilenameSignatureTable.id,
        onDelete = ReferenceOption.CASCADE,
    )
    val displayOrder = integer("display_order")
    val displayValue = varchar("display_value", 255)
    val normalizedValue = varchar("normalized_value", 255)

    init {
        uniqueIndex(signatureId, displayOrder)
        uniqueIndex(signatureId, normalizedValue)
    }
}
