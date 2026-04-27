package org.munika.assignment.domain.model

import java.time.Instant

/**
 * One assignable seat inside a position.
 *
 * App-created assignments use [AssignmentSource.Application] and remain pending until a later
 * MyMUN update confirms them with [AssignmentSource.Mymun].
 */
class Slot(
    override val id: SlotId,
    val position: Position,
    val index: Int,
    updatedAt: Instant,
) : DomainObject<SlotId> {
    var delegate: Delegate? = null
        private set

    var source: AssignmentSource? = null
        private set

    var updatedAt: Instant = updatedAt
        private set

    init {
        require(index >= 1) { "Slot index must be positive." }
    }

    val conference: Conference
        get() = position.conference

    val status: AssignmentStatus
        get() = when {
            delegate == null -> AssignmentStatus.Empty
            source == AssignmentSource.Application -> AssignmentStatus.Pending
            source == AssignmentSource.Mymun -> AssignmentStatus.Assigned
            else -> error("A non-empty slot must have an assignment source.")
        }

    val isEmpty: Boolean
        get() = status == AssignmentStatus.Empty

    val isPending: Boolean
        get() = status == AssignmentStatus.Pending

    val isAssigned: Boolean
        get() = status == AssignmentStatus.Assigned

    val matchesDelegatePreference: Boolean
        get() = delegate?.preferences?.any { it.position === position } == true

    fun assign(delegate: Delegate, source: AssignmentSource, updatedAt: Instant = Instant.now()) {
        require(delegate.conference === conference) { "Delegate must belong to the same conference as the slot." }
        val currentSlot = delegate.currentSlot
        require(currentSlot == null || currentSlot === this) {
            "A delegate can occupy at most one slot per conference."
        }

        this.delegate = delegate
        this.source = source
        this.updatedAt = updatedAt
    }

    fun clearAssignment(updatedAt: Instant = Instant.now()) {
        delegate = null
        source = null
        this.updatedAt = updatedAt
    }
}
