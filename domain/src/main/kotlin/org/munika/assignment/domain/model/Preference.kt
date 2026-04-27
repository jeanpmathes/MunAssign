package org.munika.assignment.domain.model

class Preference(
    override val id: PreferenceId,
    val delegate: Delegate,
    val position: Position,
    val rank: Int,
    val rawPreferenceText: String,
) : DomainObject<PreferenceId> {
    init {
        require(rank > 0) { "Preference rank must be positive." }
    }

    val committee: Committee
        get() = position.committee
}
