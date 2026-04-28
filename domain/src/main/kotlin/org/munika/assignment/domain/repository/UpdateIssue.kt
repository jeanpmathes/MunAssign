package org.munika.assignment.domain.repository

/**
 * User-facing validation or reconciliation issue from a MyMUN update.
 *
 * Location fields use one-based Excel row and column numbers when the issue can be tied to a
 * workbook cell. `code` is stable enough for persistence, tests, and UI grouping.
 */
data class UpdateIssue(
    val severity: UpdateIssueSeverity,
    val message: String,
    val code: String? = null,
    val sheetName: String? = null,
    val rowNumber: Int? = null,
    val columnNumber: Int? = null,
    val columnName: String? = null,
)
