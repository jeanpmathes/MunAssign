package org.munika.assignment.domain.model

/**
 * Ranked expression of interest from a delegate toward a resolved position.
 *
 * [rawPreferenceText] preserves the workbook value for diagnostics and validation messages.
 */
class Preference(
    override val id: PreferenceId,
    val delegate: Delegate,
    val position: Position,
    val rank: Int,
    val rawPreferenceText: String,
    val source: PreferenceSource = PreferenceSource.Mymun,
) : DomainObject<PreferenceId> {
    init {
        require(rank > 0) { "Preference rank must be positive." }
    }

    val committee: Committee
        get() = position.committee
}
