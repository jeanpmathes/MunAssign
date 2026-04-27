package org.munika.assignment.domain.model

import java.time.Instant

/**
 * Top-level aggregate for one organizer's assignment work.
 *
 * A conference scopes MyMUN updates, organizer-managed state, assignments, and `.munassign`
 * transfer. Derived collections intentionally read through child objects instead of storing
 * duplicate counters.
 */
class Conference(
    override val id: ConferenceId,
    val name: String,
    val committees: MutableList<Committee> = mutableListOf(),
    val delegations: MutableList<Delegation> = mutableListOf(),
    val delegates: MutableList<Delegate> = mutableListOf(),
    val questions: MutableList<Question> = mutableListOf(),
    val createdAt: Instant,
    var updatedAt: Instant,
    val lastMymunUpdateAt: Instant? = null,
) : DomainObject<ConferenceId> {
    init {
        require(name.isNotBlank()) { "Conference name must not be blank." }
    }

    val positions: List<Position>
        get() = committees.flatMap { it.positions }

    val slots: List<Slot>
        get() = positions.flatMap { it.slots }

    val openSlots: List<Slot>
        get() = slots.filter { it.status == AssignmentStatus.Empty }

    val pendingSlots: List<Slot>
        get() = slots.filter { it.status == AssignmentStatus.Pending }

    val assignedSlots: List<Slot>
        get() = slots.filter { it.status == AssignmentStatus.Assigned }

    val assignedDelegates: Set<Delegate>
        get() = assignedSlots.mapNotNullTo(mutableSetOf()) { it.delegate }

    val pendingDelegates: Set<Delegate>
        get() = pendingSlots.mapNotNullTo(mutableSetOf()) { it.delegate }

    val occupiedDelegates: Set<Delegate>
        get() = slots.mapNotNullTo(mutableSetOf()) { it.delegate }

    val unassignedDelegates: List<Delegate>
        get() = delegates.filterNot { it in occupiedDelegates }
}
