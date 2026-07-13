package org.munika.assignment.ui.viewmodel

import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.Delegate
import org.munika.assignment.domain.model.Delegation
import org.munika.assignment.domain.model.Reservation
import org.munika.assignment.domain.model.ReservationDelegateLink
import org.munika.assignment.domain.model.ReservationLinkSource

data class ReservationManagerUiState(
    val reservations: List<ReservationSummaryUiState>,
    val delegations: List<ReservationDelegationOptionUiState>,
)

data class ReservationSummaryUiState(
    val id: String,
    val name: String,
    val highSchoolCount: Int,
    val universityCount: Int,
    val remainingHighSchoolCount: Int,
    val remainingUniversityCount: Int,
    val delegationId: String?,
    val delegationName: String?,
    val consumers: List<ReservationConsumerUiState>,
    val manualConsumers: List<ReservationConsumerUiState>,
    val automaticConsumers: List<ReservationConsumerUiState>,
)

data class ReservationConsumerUiState(
    val delegateId: String,
    val delegateName: String,
    val isHighSchooler: Boolean,
    val sourceLabel: String,
)

data class ReservationDelegationOptionUiState(
    val id: String,
    val label: String,
)

data class DelegationReservationDraftUiState(
    val name: String,
    val highSchoolCount: Int,
    val universityCount: Int,
    val delegationId: String,
)

fun Conference.toReservationManagerUiState(): ReservationManagerUiState =
    ReservationManagerUiState(
        reservations = reservations
            .sortedBy { it.name }
            .map { it.toSummaryUiState() },
        delegations = delegations
            .map { delegation ->
                ReservationDelegationOptionUiState(
                    id = delegation.id.value,
                    label = delegation.displayLabel,
                )
            }
            .sortedBy { it.label },
    )

fun Delegation.toQuickReservationDraftUiState(): DelegationReservationDraftUiState {
    val count = expectedDelegateCount?.coerceAtLeast(0) ?: 0
    return DelegationReservationDraftUiState(
        name = name,
        highSchoolCount = if (isHighSchoolDelegation) count else 0,
        universityCount = if (isHighSchoolDelegation) 0 else count,
        delegationId = id.value,
    )
}

private fun Reservation.toSummaryUiState(): ReservationSummaryUiState =
    ReservationSummaryUiState(
        id = id.value,
        name = name,
        highSchoolCount = highSchoolCount,
        universityCount = universityCount,
        remainingHighSchoolCount = remainingHighSchoolCount,
        remainingUniversityCount = remainingUniversityCount,
        delegationId = delegation?.id?.value,
        delegationName = delegation?.displayLabel,
        consumers = activeLinks
            .map { it.toConsumerUiState() }
            .sortedBy { it.delegateName },
        manualConsumers = consumers(ReservationLinkSource.Manual),
        automaticConsumers = consumers(ReservationLinkSource.DelegationAuto),
    )

private fun Reservation.consumers(source: ReservationLinkSource): List<ReservationConsumerUiState> =
    activeLinks
        .filter { it.source == source }
        .map { it.toConsumerUiState() }
        .sortedBy { it.delegateName }

private fun ReservationDelegateLink.toConsumerUiState(): ReservationConsumerUiState =
    ReservationConsumerUiState(
        delegateId = delegate.id.value,
        delegateName = delegate.displayName,
        isHighSchooler = delegate.isHighSchooler,
        sourceLabel = source.toReservationSourceLabel(),
    )

private fun ReservationLinkSource.toReservationSourceLabel(): String =
    when (this) {
        ReservationLinkSource.Manual -> "Manual"
        ReservationLinkSource.DelegationAuto -> "Delegation"
    }

internal fun Delegate.reservationSourceLabel(): String? =
    reservationLink?.source?.toReservationSourceLabel()
