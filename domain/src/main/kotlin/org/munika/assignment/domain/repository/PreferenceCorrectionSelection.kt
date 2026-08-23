package org.munika.assignment.domain.repository

import org.munika.assignment.domain.model.DelegateId
import org.munika.assignment.domain.model.PositionId

/** Identifies the origin of a value carried by a preference correction card. */
enum class PreferenceValueOrigin {
    Mymun,
    Organizer,
}

/** One target rank in an organizer-corrected preference sequence. */
data class PreferenceCorrectionSelection(
    val delegateId: DelegateId,
    val rank: Int,
    val positionId: PositionId?,
    val origin: PreferenceValueOrigin?,
    val emptyAcknowledged: Boolean = false,
) {
    init {
        require(rank > 0) { "Preference rank must be positive." }
        require((positionId == null) == (origin == null)) { "Preference positions and origins must be supplied together." }
        require(positionId == null || !emptyAcknowledged) {
            "Only an empty preference can be acknowledged as empty."
        }
    }
}
