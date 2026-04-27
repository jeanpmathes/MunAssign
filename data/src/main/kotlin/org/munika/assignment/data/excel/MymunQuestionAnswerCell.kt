package org.munika.assignment.data.excel

/**
 * Raw answer cell for a delegate/question pair.
 */
data class MymunQuestionAnswerCell(
    val delegateRowNumber: Int,
    val questionColumn: MymunQuestionColumn,
    val answer: String?,
    val location: CellLocation,
) : MymunStagingObject() {
    override fun displayString(): String =
        "${location.shortName}: ${answer?.shortForDebugger() ?: "<blank>"}"
}
