package org.munika.assignment.export

import java.time.Instant
import org.munika.assignment.domain.model.AssignmentStatus
import org.munika.assignment.domain.model.CommitteeMode
import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.Delegate
import org.munika.assignment.domain.model.PaymentStatus

internal enum class CapacityBucket {
    HighSchool,
    University,
}

/** Mutually exclusive commitments against one school-level slot-capacity bucket. */
internal data class CapacityStatus(
    val bucket: CapacityBucket,
    val totalSlots: Int,
    val assigned: Int,
    val confirmed: Int,
    val accepted: Int,
    val reserved: Int,
    val freeSlots: Int,
    val overflow: Int,
) {
    val committedSlots: Int
        get() = assigned + confirmed + accepted + reserved
}

/** Compact fill status for one committee on the shareable conference card. */
internal data class CommitteeStatus(
    val name: String,
    val mode: CommitteeMode,
    val totalSlots: Int,
    val occupiedSlots: Int,
)

/**
 * Privacy-safe, presentation-neutral snapshot used to render a conference status image.
 *
 * Delegates are reduced to aggregate counts at this boundary so the renderer cannot accidentally
 * place names, email addresses, or profile answers on a shareable image.
 */
internal data class ConferenceStatusReport(
    val conferenceName: String,
    val generatedAt: Instant,
    val lastMymunUpdateAt: Instant?,
    val totalSlots: Int,
    val occupiedSlots: Int,
    val capacities: List<CapacityStatus>,
    val committees: List<CommitteeStatus>,
    val incompleteMappedSlots: Int,
) {
    companion object {
        fun from(conference: Conference, generatedAt: Instant): ConferenceStatusReport =
            ConferenceStatusReport(
                conferenceName = conference.name,
                generatedAt = generatedAt,
                lastMymunUpdateAt = conference.lastMymunUpdateAt,
                totalSlots = conference.slots.size,
                occupiedSlots = conference.slots.count { it.status != AssignmentStatus.Empty },
                capacities = CapacityBucket.entries.map { bucket -> conference.capacityStatus(bucket) },
                committees = conference.committees.map { committee ->
                    CommitteeStatus(
                        name = committee.displayName,
                        mode = committee.mode,
                        totalSlots = committee.slots.size,
                        occupiedSlots = committee.slots.count { it.status != AssignmentStatus.Empty },
                    )
                },
                incompleteMappedSlots = conference.positions
                    .filter { it.mappingIncomplete }
                    .sumOf { it.slots.size },
            )
    }
}

private fun Conference.capacityStatus(bucket: CapacityBucket): CapacityStatus {
    val bucketSlots = positions
        .filter { position -> bucket.matches(position.effectiveCommitteeMode) }
        .flatMap { it.slots }
    val assigned = bucketSlots.count { it.status != AssignmentStatus.Empty }
    val acceptedUnallocated = delegates.filter { delegate ->
        delegate.active &&
            delegate.currentSlot == null &&
            delegate.profile.isUsableForAssignment &&
            bucket.matches(delegate)
    }
    val confirmed = acceptedUnallocated.count { it.paymentStatus == PaymentStatus.Paid }
    val accepted = acceptedUnallocated.count { it.paymentStatus == PaymentStatus.Unpaid }
    val otherReservationConsumers = reservations
        .flatMap { it.activeLinks }
        .map { it.delegate }
        .distinctBy { it.id }
        .count { delegate ->
            bucket.matches(delegate) &&
                delegate.currentSlot == null &&
                !delegate.profile.isUsableForAssignment
        }
    val unusedReservation = when (bucket) {
        CapacityBucket.HighSchool -> remainingHighSchoolReservationCount
        CapacityBucket.University -> remainingUniversityReservationCount
    }
    val reserved = otherReservationConsumers + unusedReservation
    val committedSlots = assigned + confirmed + accepted + reserved

    return CapacityStatus(
        bucket = bucket,
        totalSlots = bucketSlots.size,
        assigned = assigned,
        confirmed = confirmed,
        accepted = accepted,
        reserved = reserved,
        freeSlots = (bucketSlots.size - committedSlots).coerceAtLeast(0),
        overflow = (committedSlots - bucketSlots.size).coerceAtLeast(0),
    )
}

private fun CapacityBucket.matches(mode: CommitteeMode?): Boolean =
    when (this) {
        CapacityBucket.HighSchool -> mode == CommitteeMode.HighSchool
        CapacityBucket.University -> mode == CommitteeMode.University
    }

private fun CapacityBucket.matches(delegate: Delegate): Boolean =
    when (this) {
        CapacityBucket.HighSchool -> delegate.isHighSchooler
        CapacityBucket.University -> !delegate.isHighSchooler
    }
