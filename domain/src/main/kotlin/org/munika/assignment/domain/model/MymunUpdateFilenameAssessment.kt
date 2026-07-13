package org.munika.assignment.domain.model

/** Result of comparing one selected workbook filename with a conference's trusted signatures. */
sealed interface MymunUpdateFilenameAssessment {
    val candidate: MymunUpdateFilenameCandidate

    data class FirstUse(
        override val candidate: MymunUpdateFilenameCandidate,
    ) : MymunUpdateFilenameAssessment

    data class Match(
        override val candidate: MymunUpdateFilenameCandidate,
        val signature: MymunUpdateFilenameSignature,
    ) : MymunUpdateFilenameAssessment

    data class Empty(
        override val candidate: MymunUpdateFilenameCandidate,
    ) : MymunUpdateFilenameAssessment

    data class Mismatch(
        override val candidate: MymunUpdateFilenameCandidate,
        val closestSignature: MymunUpdateFilenameSignature,
        val missingParts: List<String>,
        val unexpectedParts: List<String>,
    ) : MymunUpdateFilenameAssessment
}
