package org.munika.assignment.domain.model

import org.munika.assignment.domain.display.CountryFlagEmojiResolver

/**
 * Assignable country or role inside a committee.
 *
 * Positions come from the MyMUN `Countries` sheet, including rows with no confirmed delegate.
 * Interest is derived from delegate preferences instead of stored on the position.
 */
class Position(
    override val id: PositionId,
    val committee: Committee,
    val name: String,
    val slots: MutableList<Slot> = mutableListOf(),
) : DomainObject<PositionId> {
    init {
        require(name.isNotBlank()) { "Position name must not be blank." }
    }

    val conference: Conference
        get() = committee.conference

    var mappedCommittee: Committee? = null
        private set

    /**
     * Sets this position's correspondence target while preserving mapped-committee invariants.
     * Passing null clears an existing correspondence so persistence mapping and mode changes can
     * detach a target safely.
     */
    fun setMappedCommittee(target: Committee?): Unit {
        if (target != null) {
            require(committee.mode == CommitteeMode.Mapped) {
                "Only positions owned by mapped committees can have a mapped target."
            }
            require(target.conference === conference) {
                "Mapped target committee must belong to the same conference."
            }
            require(target.mode != CommitteeMode.Mapped) {
                "Mapped target committee must not itself be mapped."
            }
            require(committee.positions.none { sibling ->
                sibling !== this && sibling.mappedCommittee === target
            }) {
                "Mapped target committee is already used by another position in this committee."
            }
        }
        mappedCommittee = target
    }

    val countryFlagEmoji: String?
        get() = CountryFlagEmojiResolver.emojiFor(name)

    val countryFlagAssetName: String?
        get() = CountryFlagEmojiResolver.assetNameFor(name)

    val displayName: String
        get() = CountryFlagEmojiResolver.displayNameFor(name)

    val effectiveCommitteeMode: CommitteeMode?
        get() = when (committee.mode) {
            CommitteeMode.HighSchool -> CommitteeMode.HighSchool
            CommitteeMode.University -> CommitteeMode.University
            CommitteeMode.Mapped -> mappedCommittee?.mode?.takeIf { it != CommitteeMode.Mapped }
        }

    val isEffectivelyHighSchool: Boolean?
        get() = effectiveCommitteeMode?.let { it == CommitteeMode.HighSchool }

    val mappingIncomplete: Boolean
        get() = committee.mode == CommitteeMode.Mapped && mappedCommittee == null

    val assignedDelegates: List<Delegate>
        get() = slots.mapNotNull { it.delegate }

    val interestedDelegates: List<Delegate>
        get() = conference.delegates.filter { delegate -> delegate.preferences.any { it.position === this } }

    val hasAnyAssignment: Boolean
        get() = slots.any { it.delegate != null }
}
