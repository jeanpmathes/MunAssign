package org.munika.assignment.data.db.model

/** Table-shaped trusted MyMUN update filename signature and its ordered parts. */
data class MymunUpdateFilenameSignatureRow(
    val id: String,
    val conferenceId: String,
    val trustedAtEpochMillis: Long,
    val parts: List<MymunUpdateFilenamePartRow>,
)

data class MymunUpdateFilenamePartRow(
    val displayOrder: Int,
    val displayValue: String,
    val normalizedValue: String,
)
