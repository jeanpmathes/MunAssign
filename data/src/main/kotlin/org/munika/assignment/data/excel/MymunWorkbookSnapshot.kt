package org.munika.assignment.data.excel

/**
 * Parsed workbook staging data.
 *
 * Parsing is intentionally separate from persistence so validation can abort a MyMUN update before
 * any conference data is written.
 */
data class MymunWorkbookSnapshot(
    val workbookName: String?,
    val delegates: List<MymunDelegateRow>,
    val countryPositions: List<MymunCountryPositionRow>,
    val delegations: List<MymunDelegationRow>,
    val questionColumns: List<MymunQuestionColumn>,
    val questionAnswers: List<MymunQuestionAnswerCell>,
    val preferenceCandidates: List<MymunPreferenceCandidate>,
    val confirmedAssignmentCandidates: List<MymunConfirmedAssignmentCandidate>,
    val issues: List<MymunValidationIssue>,
    val preferenceRanks: List<Int> = emptyList(),
    val missingPreferences: List<MymunMissingPreference> = emptyList(),
) : MymunStagingObject() {
    override fun displayString(): String =
        "Workbook ${workbookName ?: "<stream>"}: ${delegates.size} delegates, ${countryPositions.size} positions, ${issues.count { it.severity == MymunValidationSeverity.Error }} errors"
}
