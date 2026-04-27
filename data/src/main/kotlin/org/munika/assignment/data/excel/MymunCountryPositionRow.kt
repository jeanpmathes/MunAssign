package org.munika.assignment.data.excel

/**
 * Parsed position row from the MyMUN `Countries` sheet.
 */
data class MymunCountryPositionRow(
    val rowNumber: Int,
    val committeeName: String,
    val memberName: String,
    val delegationName: String?,
    val delegatesRaw: String?,
) : MymunStagingObject() {
    override fun displayString(): String =
        listOfNotNull("$committeeName ($memberName)", delegatesRaw?.let { "<- $it" }).joinToString(" ")
}
