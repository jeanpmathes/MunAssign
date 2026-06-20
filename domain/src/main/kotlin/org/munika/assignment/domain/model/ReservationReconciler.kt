package org.munika.assignment.domain.model

import java.time.Instant

/**
 * Result of rebuilding the current reservation delegate links for a conference.
 */
data class ReservationReconciliationResult(
    val links: List<ReservationDelegateLink>,
    val removedLinkIds: List<ReservationDelegateLinkId>,
)

/**
 * Rebuilds manual and delegation-automatic reservation consumption from the current conference graph.
 */
object ReservationReconciler {
    /**
     * Rebuilds reservation delegate links and updates in-memory reservation/delegate back-references.
     *
     * Manual links are preserved only for active delegates, automatic links are regenerated from
     * linked delegations, and returned removed IDs identify persisted rows no longer present.
     */
    fun reconcile(
        conference: Conference,
        existingLinks: List<ReservationDelegateLink>,
        now: Instant,
        idFactory: () -> ReservationDelegateLinkId,
    ): ReservationReconciliationResult {
        conference.delegates.forEach { it.reservationLink = null }
        conference.reservations.forEach { it.delegateLinks.clear() }

        val nextLinks = mutableListOf<ReservationDelegateLink>()
        val consumedDelegateIds = mutableSetOf<DelegateId>()
        val retainedLinkIds = mutableSetOf<ReservationDelegateLinkId>()
        val conferenceReservations = conference.reservations.toSet()
        val conferenceDelegates = conference.delegates.toSet()

        existingLinks
            .asSequence()
            .filter { it.source == ReservationLinkSource.Manual }
            .filter { it.reservation in conferenceReservations }
            .filter { it.delegate in conferenceDelegates }
            .filter { it.delegate.active }
            .forEach { link ->
                if (consumedDelegateIds.add(link.delegate.id)) {
                    nextLinks += link
                    retainedLinkIds += link.id
                }
            }

        val reusableAutomaticLinks = existingLinks
            .filter { it.source == ReservationLinkSource.DelegationAuto }
            .associateBy { it.reservation.id to it.delegate.id }

        conference.reservations
            .sortedWith(compareBy<Reservation>({ it.createdAt }, { it.id.value }))
            .forEach { reservation ->
            val delegation = reservation.delegation ?: return@forEach
            var usedHighSchool = nextLinks.count { it.reservation === reservation && it.delegate.isHighSchooler }
            var usedUniversity = nextLinks.count { it.reservation === reservation && !it.delegate.isHighSchooler }

            eligibleDelegates(conference, delegation, consumedDelegateIds).forEach { delegate ->
                val isHighSchooler = delegate.isHighSchooler
                if (isHighSchooler && usedHighSchool >= reservation.highSchoolCount) return@forEach
                if (!isHighSchooler && usedUniversity >= reservation.universityCount) return@forEach

                val link = reusableAutomaticLinks[reservation.id to delegate.id]?.let { existing ->
                    ReservationDelegateLink(
                        id = existing.id,
                        reservation = reservation,
                        delegate = delegate,
                        source = ReservationLinkSource.DelegationAuto,
                        createdAt = existing.createdAt,
                        updatedAt = now,
                    )
                } ?: ReservationDelegateLink(
                    id = idFactory(),
                    reservation = reservation,
                    delegate = delegate,
                    source = ReservationLinkSource.DelegationAuto,
                    createdAt = now,
                    updatedAt = now,
                )

                nextLinks += link
                retainedLinkIds += link.id
                consumedDelegateIds += delegate.id
                if (isHighSchooler) {
                    usedHighSchool += 1
                } else {
                    usedUniversity += 1
                }
            }
        }

        nextLinks.forEach { link ->
            link.reservation.delegateLinks += link
            link.delegate.reservationLink = link
        }

        val removedLinkIds = existingLinks
            .map { it.id }
            .filterNot { it in retainedLinkIds }

        return ReservationReconciliationResult(nextLinks, removedLinkIds)
    }

    private fun eligibleDelegates(
        conference: Conference,
        delegation: Delegation,
        consumedDelegateIds: Set<DelegateId>,
    ): List<Delegate> =
        conference.delegates
            .asSequence()
            .filter { it.delegation === delegation }
            .filter { it.id !in consumedDelegateIds }
            .filter { it.active }
            .filter { it.paymentStatus == PaymentStatus.Paid }
            .filter { it.profile.applicationStatus.isAcceptedOrAssigned() }
            .sortedWith(EligibleDelegateComparator)
            .toList()

    private fun String?.isAcceptedOrAssigned(): Boolean {
        val normalized = this?.trim() ?: return false
        return normalized.equals("Accepted", ignoreCase = true) ||
            normalized.equals("Assigned", ignoreCase = true)
    }

    private val EligibleDelegateComparator = compareBy<Delegate>(
        { it.submittedAt == null },
        { it.submittedAt ?: Instant.MAX },
        { it.displayName },
        { it.id.value },
    )
}
