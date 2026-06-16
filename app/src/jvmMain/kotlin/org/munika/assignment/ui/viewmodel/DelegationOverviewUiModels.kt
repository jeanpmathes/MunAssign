package org.munika.assignment.ui.viewmodel

import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.Delegate
import org.munika.assignment.domain.model.Delegation
import org.munika.assignment.domain.model.PaymentStatus
import org.munika.assignment.domain.model.Reservation

data class DelegationOverviewUiState(
    val delegations: List<DelegationOverviewRowUiState>,
)

data class DelegationOverviewRowUiState(
    val id: String,
    val name: String,
    val country: String?,
    val expectedDelegateCount: Int?,
    val currentDelegateCountFromMymun: Int?,
    val activePaidAcceptedDelegateCount: Int,
    val linkedReservations: List<DelegationReservationUiState>,
    val acceptedDelegates: List<DelegationDelegateUiState>,
    val submittedDelegates: List<DelegationDelegateUiState>,
)

data class DelegationReservationUiState(
    val id: String,
    val name: String,
    val usedHighSchoolCount: Int,
    val totalHighSchoolCount: Int,
    val usedUniversityCount: Int,
    val totalUniversityCount: Int,
)

data class DelegationDelegateUiState(
    val id: String,
    val name: String,
    val reservationName: String?,
    val assignmentLabel: String?,
    val isHighSchooler: Boolean,
)

fun Conference.toDelegationOverviewUiState(): DelegationOverviewUiState =
    DelegationOverviewUiState(
        delegations = delegations
            .sortedBy { it.name }
            .map { delegation -> delegation.toOverviewRowUiState(this) },
    )

private fun Delegation.toOverviewRowUiState(conference: Conference): DelegationOverviewRowUiState =
    DelegationOverviewRowUiState(
        id = id.value,
        name = name,
        country = country,
        expectedDelegateCount = expectedDelegateCount,
        currentDelegateCountFromMymun = currentDelegateCountFromMymun,
        activePaidAcceptedDelegateCount = delegates.count { it.isActivePaidAcceptedOrAssigned() },
        linkedReservations = conference.reservations
            .filter { it.delegation === this }
            .sortedBy { it.name }
            .map { it.toDelegationReservationUiState() },
        acceptedDelegates = delegates
            .filter { it.profile.applicationStatus.isAcceptedOrAssigned() }
            .sortedBy { it.displayName }
            .map { it.toDelegationDelegateUiState() },
        submittedDelegates = delegates
            .filter { it.profile.applicationStatus.isSubmitted() }
            .sortedBy { it.displayName }
            .map { it.toDelegationDelegateUiState() },
    )

private fun Reservation.toDelegationReservationUiState(): DelegationReservationUiState =
    DelegationReservationUiState(
        id = id.value,
        name = name,
        usedHighSchoolCount = usedHighSchoolCount,
        totalHighSchoolCount = highSchoolCount,
        usedUniversityCount = usedUniversityCount,
        totalUniversityCount = universityCount,
    )

private fun Delegate.toDelegationDelegateUiState(): DelegationDelegateUiState =
    DelegationDelegateUiState(
        id = id.value,
        name = displayName,
        reservationName = reservationLink?.reservation?.name,
        assignmentLabel = currentPosition?.let { position -> "${position.committee.displayName} (${position.name})" },
        isHighSchooler = isHighSchooler,
    )

private fun Delegate.isActivePaidAcceptedOrAssigned(): Boolean =
    active &&
        paymentStatus == PaymentStatus.Paid &&
        profile.applicationStatus.isAcceptedOrAssigned()

private fun String?.isAcceptedOrAssigned(): Boolean {
    val normalized = this?.trim() ?: return false
    return normalized.equals("Accepted", ignoreCase = true) ||
        normalized.equals("Assigned", ignoreCase = true)
}

private fun String?.isSubmitted(): Boolean =
    this?.trim()?.equals("Submitted", ignoreCase = true) == true
