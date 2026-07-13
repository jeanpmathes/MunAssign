package org.munika.assignment.domain.model

/** One meaningful, displayable block extracted from a MyMUN update filename. */
data class MymunUpdateFilenamePart(
    val displayValue: String,
    val normalizedValue: String,
) {
    init {
        require(displayValue.isNotBlank()) { "MyMUN update filename part must not be blank." }
        require(normalizedValue.isNotBlank()) { "Normalized MyMUN update filename part must not be blank." }
    }
}
