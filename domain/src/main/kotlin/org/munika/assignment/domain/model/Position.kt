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

    val countryFlagEmoji: String?
        get() = CountryFlagEmojiResolver.emojiFor(name)

    val countryFlagAssetName: String?
        get() = CountryFlagEmojiResolver.assetNameFor(name)

    val displayName: String
        get() = CountryFlagEmojiResolver.displayNameFor(name)

    val assignedDelegates: List<Delegate>
        get() = slots.mapNotNull { it.delegate }

    val interestedDelegates: List<Delegate>
        get() = conference.delegates.filter { delegate -> delegate.preferences.any { it.position === this } }

    val hasAnyAssignment: Boolean
        get() = slots.any { it.delegate != null }
}
