package org.munika.assignment.domain.model

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

    val assignedDelegates: List<Delegate>
        get() = slots.mapNotNull { it.delegate }

    val interestedDelegates: List<Delegate>
        get() = conference.delegates.filter { delegate -> delegate.preferences.any { it.position === this } }

    val hasAnyAssignment: Boolean
        get() = slots.any { it.delegate != null }
}
