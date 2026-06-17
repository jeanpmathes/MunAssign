package org.munika.assignment.ui.viewmodel

import org.munika.assignment.domain.model.AnswerIssueSeverity
import org.munika.assignment.domain.model.AssignmentStatus
import org.munika.assignment.domain.model.Committee
import org.munika.assignment.domain.model.CommitteeMode
import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.Delegate
import org.munika.assignment.domain.model.PaymentStatus
import org.munika.assignment.domain.model.Position
import org.munika.assignment.domain.model.Reservation
import org.munika.assignment.domain.model.Slot

data class AssignmentBoardUiState(
    val conferenceId: String,
    val committees: List<CommitteeBoardUiState>,
)

data class CommitteeBoardUiState(
    val id: String,
    val name: String,
    val slotsPerPosition: Int,
    val mode: CommitteeMode,
    val isHighSchoolCommittee: Boolean,
    val openSlotCount: Int,
    val pendingSlotCount: Int,
    val assignedSlotCount: Int,
    val highSchoolerAssignedCount: Int,
    val universityAssignedCount: Int,
    val reservationReminder: ReservationReminderUiState?,
    val positions: List<PositionBoardUiState>,
)

data class ReservationReminderUiState(
    val label: String,
    val totalCount: Int,
    val remainingCount: Int,
)

data class PositionBoardUiState(
    val id: String,
    val name: String,
    val displayName: String,
    val flagAssetName: String?,
    val effectiveMode: CommitteeMode?,
    val mappingIncomplete: Boolean,
    val mappedCommittee: PositionMappedCommitteeUiState?,
    val mappingOptions: List<CommitteeMappingOptionUiState>,
    val slots: List<SlotBoardUiState>,
    val interestedDelegates: List<InterestedDelegateUiState>,
    val assignmentCandidates: List<AssignmentDelegateCandidateUiState>,
) {
    val occupiedSlotCount: Int = slots.count { it.status != SlotBoardStatus.Empty }
    val shouldShowInterestedDelegates: Boolean = slots.any { it.status == SlotBoardStatus.Empty }
    val interestedDelegateCount: Int = interestedDelegates.size
}

data class CommitteeMappingOptionUiState(
    val id: String,
    val name: String,
    val mode: CommitteeMode,
)

data class PositionMappedCommitteeUiState(
    val id: String,
    val name: String,
    val mode: CommitteeMode,
)

data class SlotBoardUiState(
    val id: String,
    val index: Int,
    val status: SlotBoardStatus,
    val effectiveMode: CommitteeMode?,
    val delegateName: String?,
    val delegateId: String?,
    val delegateIsHighSchooler: Boolean,
    val matchesDelegatePreference: Boolean,
    val positionName: String,
)

data class InterestedDelegateUiState(
    val id: String,
    val name: String,
    val isHighSchooler: Boolean,
    val preferenceRank: Int,
    val pendingAssignmentLabel: String?,
    val pendingAssignmentSlotId: String?,
    val confirmedAssignmentLabel: String?,
    val confirmedAssignmentSlotId: String?,
) {
    val canAssign: Boolean = confirmedAssignmentSlotId == null
}

data class AssignmentDelegateCandidateUiState(
    val id: String,
    val name: String,
    val preferenceRank: Int?,
    val pendingAssignmentLabel: String?,
    val confirmedAssignmentLabel: String?,
    val isHighSchooler: Boolean,
    val warningCount: Int,
    val noticeCount: Int,
) {
    val canAssign: Boolean = confirmedAssignmentLabel == null
    val isPendingElsewhere: Boolean = pendingAssignmentLabel != null
}

enum class SlotBoardStatus {
    Empty,
    Pending,
    Assigned,
}

data class BoardScrollRequest(
    val committeeId: String,
    val positionId: String? = null,
    val requestId: Long = System.nanoTime(),
)

fun Conference.toAssignmentBoardUiState(): AssignmentBoardUiState =
    AssignmentBoardUiState(
        conferenceId = id.value,
        committees = committees.map { committee ->
            val reservationReminder = reservations.toReservationReminderUiState(committee.isHighSchoolCommittee)
            CommitteeBoardUiState(
                id = committee.id.value,
                name = committee.displayName,
                slotsPerPosition = committee.slotsPerPosition,
                mode = committee.mode,
                isHighSchoolCommittee = committee.isHighSchoolCommittee,
                openSlotCount = committee.openSlotCount,
                pendingSlotCount = committee.pendingSlotCount,
                assignedSlotCount = committee.assignedSlotCount,
                highSchoolerAssignedCount = committee.slots.count { it.delegate?.isHighSchooler == true },
                universityAssignedCount = committee.slots.count { it.delegate != null && it.delegate?.isHighSchooler != true },
                reservationReminder = if (committee.mode == CommitteeMode.Mapped) null else reservationReminder,
                positions = committee.positions.map { it.toBoardUiState() },
            )
        },
    )

private fun List<Reservation>.toReservationReminderUiState(isHighSchoolCommittee: Boolean): ReservationReminderUiState? {
    val totalCount = if (isHighSchoolCommittee) {
        sumOf { it.highSchoolCount }
    } else {
        sumOf { it.universityCount }
    }
    if (totalCount == 0) return null

    return ReservationReminderUiState(
        label = if (isHighSchoolCommittee) "HS reservations" else "Uni reservations",
        totalCount = totalCount,
        remainingCount = if (isHighSchoolCommittee) {
            sumOf { it.remainingHighSchoolCount }
        } else {
            sumOf { it.remainingUniversityCount }
        },
    )
}

private fun Position.toBoardUiState(): PositionBoardUiState =
    PositionBoardUiState(
        id = id.value,
        name = name,
        displayName = name,
        flagAssetName = countryFlagAssetName,
        effectiveMode = effectiveCommitteeMode,
        mappingIncomplete = mappingIncomplete,
        mappedCommittee = mappedCommittee?.toMappedCommitteeUiState(),
        mappingOptions = if (committee.mode == CommitteeMode.Mapped) {
            conference.committees.toMappingOptions(owningCommittee = committee)
        } else {
            emptyList()
        },
        slots = slots.sortedBy { it.index }.map { it.toBoardUiState() },
        interestedDelegates = interestedDelegates
            .filter { it.visibleOnAssignmentBoard() }
            .mapNotNull { delegate ->
                val rank = delegate.preferences.firstOrNull { it.position === this }?.rank ?: return@mapNotNull null
                InterestedDelegateUiState(
                    id = delegate.id.value,
                    name = delegate.displayName,
                    isHighSchooler = delegate.isHighSchooler,
                    preferenceRank = rank,
                    pendingAssignmentLabel = delegate.pendingAssignmentLabel(),
                    pendingAssignmentSlotId = delegate.pendingAssignmentSlotId(),
                    confirmedAssignmentLabel = delegate.confirmedAssignmentLabel(),
                    confirmedAssignmentSlotId = delegate.confirmedAssignmentSlotId(),
                )
            }
            .sortedWith(compareBy<InterestedDelegateUiState> { it.preferenceRank }.thenBy { it.name }),
        assignmentCandidates = conference.delegates
            .filter { it.visibleOnAssignmentBoard() }
            .filter { it.currentSlot?.isAssigned != true }
            .map { delegate ->
                AssignmentDelegateCandidateUiState(
                    id = delegate.id.value,
                    name = delegate.displayName,
                    preferenceRank = delegate.preferences.firstOrNull { it.position === this }?.rank,
                    pendingAssignmentLabel = delegate.pendingAssignmentLabel(),
                    confirmedAssignmentLabel = delegate.confirmedAssignmentLabel(),
                    isHighSchooler = delegate.isHighSchooler,
                    warningCount = delegate.warningCount(),
                    noticeCount = delegate.noticeCount(),
                )
            }
            .sortedWith(
                compareBy<AssignmentDelegateCandidateUiState> { it.assignmentSortRank }
                    .thenBy { it.highSchoolFitSortRank(effectiveCommitteeMode) }
                    .thenBy { it.preferenceRank ?: Int.MAX_VALUE }
                    .thenBy { it.name },
            ),
    )

private fun Committee.toMappedCommitteeUiState(): PositionMappedCommitteeUiState =
    PositionMappedCommitteeUiState(
        id = id.value,
        name = displayName,
        mode = mode,
    )

private fun List<Committee>.toMappingOptions(owningCommittee: Committee): List<CommitteeMappingOptionUiState> =
    filter { it !== owningCommittee && it.mode != CommitteeMode.Mapped }
        .map {
            CommitteeMappingOptionUiState(
                id = it.id.value,
                name = it.displayName,
                mode = it.mode,
            )
        }

private fun Slot.toBoardUiState(): SlotBoardUiState =
    SlotBoardUiState(
        id = id.value,
        index = index,
        status = when (status) {
            AssignmentStatus.Empty -> SlotBoardStatus.Empty
            AssignmentStatus.Pending -> SlotBoardStatus.Pending
            AssignmentStatus.Assigned -> SlotBoardStatus.Assigned
        },
        effectiveMode = position.effectiveCommitteeMode,
        delegateName = delegate?.displayName,
        delegateId = delegate?.id?.value,
        delegateIsHighSchooler = delegate?.isHighSchooler == true,
        matchesDelegatePreference = matchesDelegatePreference,
        positionName = position.name,
    )

private fun Delegate.visibleOnAssignmentBoard(): Boolean =
    active &&
        paymentStatus == PaymentStatus.Paid &&
        profile.applicationStatus.isAcceptedOrAssigned()

private fun String?.isAcceptedOrAssigned(): Boolean {
    val normalized = this?.trim() ?: return false
    return normalized.equals("Accepted", ignoreCase = true) ||
        normalized.equals("Assigned", ignoreCase = true)
}

private fun Delegate.confirmedAssignmentLabel(): String? {
    val assignedPosition = currentPosition ?: return null
    if (currentSlot?.isAssigned != true) return null
    return "${assignedPosition.committee.displayName} (${assignedPosition.name})"
}

private fun Delegate.confirmedAssignmentSlotId(): String? =
    currentSlot?.takeIf { it.isAssigned }?.id?.value

private fun Delegate.pendingAssignmentLabel(): String? {
    val assignedPosition = currentPosition ?: return null
    if (currentSlot?.isPending != true) return null
    return "${assignedPosition.committee.displayName} (${assignedPosition.name})"
}

private fun Delegate.pendingAssignmentSlotId(): String? =
    currentSlot?.takeIf { it.isPending }?.id?.value

private fun Delegate.warningCount(): Int =
    answers.sumOf { answer -> answer.openIssues.count { it.severity == AnswerIssueSeverity.Warning } } +
        openProfileIssues().count { it.severity == AnswerIssueSeverity.Warning }

private fun Delegate.noticeCount(): Int =
    answers.sumOf { answer -> answer.openIssues.count { it.severity == AnswerIssueSeverity.Notice } } +
        openProfileIssues().count { it.severity == AnswerIssueSeverity.Notice }

private val AssignmentDelegateCandidateUiState.assignmentSortRank: Int
    get() = when {
        confirmedAssignmentLabel != null -> 2
        pendingAssignmentLabel != null -> 1
        else -> 0
    }

private fun AssignmentDelegateCandidateUiState.highSchoolFitSortRank(mode: CommitteeMode?): Int =
    when {
        mode == null -> 0
        mode == CommitteeMode.HighSchool && isHighSchooler -> 0
        mode == CommitteeMode.University && !isHighSchooler -> 0
        else -> 1
    }
