package org.munika.assignment.domain.model

import java.time.Instant

/**
 * Organizer-managed reservation of high-school and university accept capacity.
 *
 * Reservations are owned by a conference and may optionally be linked to one MyMUN delegation for
 * automatic consumption by eligible delegates.
 */
class Reservation(
    override val id: ReservationId,
    val conference: Conference,
    val name: String,
    val highSchoolCount: Int,
    val universityCount: Int,
    val delegation: Delegation? = null,
    val delegateLinks: MutableList<ReservationDelegateLink> = mutableListOf(),
    val createdAt: Instant,
    var updatedAt: Instant,
) : DomainObject<ReservationId> {
    init {
        require(name.isNotBlank()) { "Reservation name must not be blank." }
        require(highSchoolCount >= 0) { "High-school reservation count must not be negative." }
        require(universityCount >= 0) { "University reservation count must not be negative." }
        require(highSchoolCount > 0 || universityCount > 0) {
            "Reservation must reserve at least one high-school or university delegate."
        }
        require(delegation == null || delegation.conference === conference) {
            "Reservation delegation must belong to the reservation conference."
        }
    }

    val activeLinks: List<ReservationDelegateLink>
        get() = delegateLinks.filter { it.delegate.active }

    val usedHighSchoolCount: Int
        get() = activeLinks.count { it.delegate.isHighSchooler }

    val usedUniversityCount: Int
        get() = activeLinks.count { !it.delegate.isHighSchooler }

    val remainingHighSchoolCount: Int
        get() = (highSchoolCount - usedHighSchoolCount).coerceAtLeast(0)

    val remainingUniversityCount: Int
        get() = (universityCount - usedUniversityCount).coerceAtLeast(0)
}
