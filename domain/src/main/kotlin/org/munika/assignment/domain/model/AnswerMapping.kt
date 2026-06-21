package org.munika.assignment.domain.model

/** Normalizes raw MyMUN answer text for mapping lookup and uniqueness checks. */
fun normalizeAnswer(value: String?): String =
    value?.trim()?.lowercase().orEmpty()
