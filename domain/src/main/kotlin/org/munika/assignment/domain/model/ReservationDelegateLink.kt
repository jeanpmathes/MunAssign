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
        delegate.reservationLink = this
    }
}
