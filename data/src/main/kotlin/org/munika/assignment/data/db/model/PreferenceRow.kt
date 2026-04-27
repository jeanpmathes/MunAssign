package org.munika.assignment.data.db.model

data class PreferenceRow(
    val id: String,
    val delegateId: String,
    val positionId: String,
    val rank: Int,
    val rawPreferenceText: String,
)
