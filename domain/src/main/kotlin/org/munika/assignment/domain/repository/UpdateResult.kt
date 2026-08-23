package org.munika.assignment.domain.repository

/**
 * Outcome of applying one MyMUN workbook update to a conference.
 *
 * A failed result means blocking issues were found and no workbook-managed conference data was
 * written. Update run metadata and issues may still be persisted for diagnostics.
 */
data class UpdateResult(
    val success: Boolean,
    val issues: List<UpdateIssue>,
    val summary: UpdateSummary = UpdateSummary(),
    val preferenceRanks: List<Int> = emptyList(),
    val missingPreferences: List<MissingPreference> = emptyList(),
)
