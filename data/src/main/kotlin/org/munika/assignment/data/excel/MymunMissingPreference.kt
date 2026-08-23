package org.munika.assignment.data.excel

/** Empty numbered preference cell retained for post-update organizer completion. */
data class MymunMissingPreference(
    val delegateRowNumber: Int,
    val rank: Int,
    val location: CellLocation,
) : MymunStagingObject() {
    override fun displayString(): String = "Delegates row $delegateRowNumber preference $rank is empty"
}
