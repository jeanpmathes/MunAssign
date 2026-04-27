package org.munika.assignment.data.excel

/**
 * Structured validation issue found while parsing or preparing a MyMUN update.
 */
data class MymunValidationIssue(
    val severity: MymunValidationSeverity,
    val code: String,
    val message: String,
    val location: CellLocation?,
) : MymunStagingObject() {
    override fun displayString(): String =
        "$severity $code at ${location?.shortName ?: "<workbook>"}"
}
