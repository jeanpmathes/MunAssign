package org.munika.assignment.domain.repository

import org.munika.assignment.domain.model.DelegateId

/** One empty workbook preference rank that still needs an organizer choice. */
data class MissingPreference(
    val delegateId: DelegateId,
    val rank: Int,
) {
    init {
        require(rank > 0) { "Preference rank must be positive." }
    }
}
