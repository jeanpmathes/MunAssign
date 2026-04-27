package org.munika.assignment.domain.model

/**
 * Maps a raw MyMUN answer to a high-schooler value.
 */
data class HighSchoolerAnswerMapping(
    val rawValue: String,
    val value: HighSchoolerStatus,
)
