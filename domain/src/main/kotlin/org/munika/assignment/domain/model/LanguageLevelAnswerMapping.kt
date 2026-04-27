package org.munika.assignment.domain.model

/**
 * Maps a raw MyMUN answer to a language-level value.
 */
data class LanguageLevelAnswerMapping(
    val rawValue: String,
    val value: LanguageLevel,
)
