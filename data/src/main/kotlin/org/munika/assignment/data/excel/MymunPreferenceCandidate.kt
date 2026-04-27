package org.munika.assignment.data.excel

/**
 * Parsed preference value before it is validated and persisted.
 */
data class MymunPreferenceCandidate(
    val delegateRowNumber: Int,
    val rank: Int,
    val rawPreferenceText: String,
    val parsedCommitteeName: String?,
    val parsedPositionName: String?,
    val resolvedCountryRowNumber: Int?,
    val location: CellLocation,
) : MymunStagingObject() {
    override fun displayString(): String =
        "P$rank $rawPreferenceText${if (resolvedCountryRowNumber == null) " (unresolved)" else ""}"
}
