package org.munika.assignment.data.db.model

data class PreferenceCompletionRow(
    val id: String,
    val delegateId: String,
    val positionId: String?,
    val rank: Int,
    val origin: String?,
    val emptyAcknowledged: Boolean,
    val baselinePositionId: String?,
    val baselineRawPreferenceText: String?,
    val detectedRanks: String?,
)
