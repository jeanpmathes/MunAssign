package org.munika.assignment.data.excel

data class MymunConfirmedAssignmentCandidate(
    val countryRowNumber: Int,
    val committeeName: String,
    val positionName: String,
    val slotIndex: Int,
    val delegateDisplayName: String,
    val rawDelegatesText: String,
) : MymunStagingObject() {
    override fun displayString(): String =
        "$delegateDisplayName -> $committeeName ($positionName) #$slotIndex"
}
