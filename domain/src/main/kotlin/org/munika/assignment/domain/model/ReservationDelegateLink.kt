package org.munika.assignment.domain.model

import java.time.Instant

/**
 * Current consumption of one reservation by one delegate.
 *
 * A delegate may consume at most one reservation in a conference. Manual links represent organizer
 * intent, while delegation-auto links are maintained by reconciliation.
 */
class ReservationDelegateLink(
    override val id: ReservationDelegateLinkId,
    val reservation: Reservation,
    val delegate: Delegate,
    val source: ReservationLinkSource,
    val createdAt: Instant,
    val updatedAt: Instant,
) : DomainObject<ReservationDelegateLinkId> {
    init {
        require(reservation.conference === delegate.conference) {
            "Reservation and delegate must belong to the same conference."
        }
    }

    /** Marks this link as the delegate's current reservation link. Callers must also add it to [reservation]'s links. */
    fun attachToDelegate() {
        delegate.reservationLink = this
    }
}
