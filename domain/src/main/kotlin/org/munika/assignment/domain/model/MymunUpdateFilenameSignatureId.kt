package org.munika.assignment.domain.model

/** Stable local identifier for one trusted MyMUN update filename signature. */
@JvmInline
value class MymunUpdateFilenameSignatureId(val value: String) {
    init {
        require(value.isNotBlank()) { "MyMUN update filename signature ID must not be blank." }
    }
}
