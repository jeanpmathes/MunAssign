package org.munika.assignment.data.excel

data class MymunValidationIssue(
    val severity: MymunValidationSeverity,
    val code: String,
    val message: String,
    val location: CellLocation?,
) : MymunStagingObject() {
    override fun displayString(): String =
        "$severity $code at ${location?.shortName ?: "<workbook>"}"
}
