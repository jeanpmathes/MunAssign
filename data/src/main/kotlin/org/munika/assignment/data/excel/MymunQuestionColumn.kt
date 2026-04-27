package org.munika.assignment.data.excel

/**
 * Dynamic MyMUN question column discovered in the `Delegates` sheet.
 */
data class MymunQuestionColumn(
    val sheetName: String,
    val columnNumber: Int,
    val displayOrder: Int,
    val questionText: String,
) : MymunStagingObject() {
    override fun displayString(): String =
        "${sheetName}!${columnNumber.toExcelColumnName()}: ${questionText.removePrefix("Question:").trim().shortForDebugger()}"
}
