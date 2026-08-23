package org.munika.assignment.domain.repository

import org.munika.assignment.domain.model.DelegateId
import org.munika.assignment.domain.model.PositionId

/** Organizer selection used to complete one empty MyMUN preference rank. */
data class PreferenceCompletionSelection(
    val delegateId: DelegateId,
    val rank: Int,
    val positionId: PositionId,
) {
    init {
        require(rank > 0) { "Preference rank must be positive." }
    }
}
