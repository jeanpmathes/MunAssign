package org.munika.assignment.domain.model

/**
 * Maps a raw MyMUN answer to a MUN experience value.
 */
data class MunExperienceAnswerMapping(
    val rawValue: String,
    val value: MunExperienceLevel,
)
