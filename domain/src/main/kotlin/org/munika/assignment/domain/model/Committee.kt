package org.munika.assignment.domain.model

/**
 * Committee imported from MyMUN and displayed as one board column.
 *
 * [mymunName] remains the update-matching key. [alias] is organizer-managed display text only.
 * [slotsPerPosition] applies uniformly to every position in the committee. [mode] and
 * [paymentTier] are organizer-managed assignment guidance preserved across MyMUN updates.
 */
class Committee(
    override val id: CommitteeId,
    val conference: Conference,
    val mymunName: String,
    val alias: String? = null,
    val slotsPerPosition: Int,
    val mode: CommitteeMode = CommitteeMode.University,
    val paymentTier: PaymentTier = PaymentTier.Full,
    val positions: MutableList<Position> = mutableListOf(),
) : DomainObject<CommitteeId> {
    init {
        require(mymunName.isNotBlank()) { "Committee MyMUN name must not be blank." }
        require(slotsPerPosition >= 1) { "Committee slot count must be at least 1." }
    }

    val displayName: String
        get() = alias?.takeIf { it.isNotBlank() } ?: mymunName

    val isHighSchoolCommittee: Boolean
        get() = mode == CommitteeMode.HighSchool

    val slots: List<Slot>
        get() = positions.flatMap { it.slots }

    val openSlotCount: Int
        get() = slots.count { it.status == AssignmentStatus.Empty }

    val pendingSlotCount: Int
        get() = slots.count { it.status == AssignmentStatus.Pending }

    val assignedSlotCount: Int
        get() = slots.count { it.status == AssignmentStatus.Assigned }
}
