package org.munika.assignment.domain.model

internal fun normalizeAnswer(value: String?): String =
    value?.trim()?.lowercase().orEmpty()
