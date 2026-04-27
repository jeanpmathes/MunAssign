package org.munika.assignment.data.excel

data class CellLocation(
    val sheetName: String,
    val rowNumber: Int,
    val columnNumber: Int,
    val columnName: String,
) : MymunStagingObject() {
    val shortName: String = "$sheetName!${columnNumber.toExcelColumnName()}$rowNumber"

    override fun displayString(): String =
        shortName
}
