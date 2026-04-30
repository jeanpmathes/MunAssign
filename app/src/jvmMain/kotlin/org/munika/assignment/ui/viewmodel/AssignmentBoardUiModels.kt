package org.munika.assignment.ui.viewmodel

import org.munika.assignment.domain.model.AssignmentStatus
import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.Delegate
import org.munika.assignment.domain.model.PaymentStatus
import org.munika.assignment.domain.model.Position
import org.munika.assignment.domain.model.Slot

data class AssignmentBoardUiState(
    val conferenceId: String,
    val committees: List<CommitteeBoardUiState>,
)

data class CommitteeBoardUiState(
    val id: String,
    val name: String,
    val slotsPerPosition: Int,
    val openSlotCount: Int,
    val pendingSlotCount: Int,
    val assignedSlotCount: Int,
    val positions: List<PositionBoardUiState>,
)

data class PositionBoardUiState(
    val id: String,
    val name: String,
    val slots: List<SlotBoardUiState>,
    val interestedDelegates: List<InterestedDelegateUiState>,
) {
    val occupiedSlotCount: Int = slots.count { it.status != SlotBoardStatus.Empty }
    val shouldShowInterestedDelegates: Boolean = occupiedSlotCount == 0
    val interestedDelegateCount: Int = interestedDelegates.size
}

data class SlotBoardUiState(
    val id: String,
    val index: Int,
    val status: SlotBoardStatus,
    val delegateName: String?,
    val matchesDelegatePreference: Boolean,
)

data class InterestedDelegateUiState(
    val id: String,
    val name: String,
    val preferenceRank: Int,
    val confirmedAssignmentLabel: String?,
)

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
            CommitteeBoardUiState(
                id = committee.id.value,
                name = committee.displayName,
                slotsPerPosition = committee.slotsPerPosition,
                openSlotCount = committee.openSlotCount,
                pendingSlotCount = committee.pendingSlotCount,
                assignedSlotCount = committee.assignedSlotCount,
                positions = committee.positions.map { it.toBoardUiState() },
            )
        },
    )

private fun Position.toBoardUiState(): PositionBoardUiState =
    PositionBoardUiState(
        id = id.value,
        name = name,
        slots = slots.sortedBy { it.index }.map { it.toBoardUiState() },
        interestedDelegates = interestedDelegates
            .filter { it.visibleOnAssignmentBoard() }
            .mapNotNull { delegate ->
                val rank = delegate.preferences.firstOrNull { it.position === this }?.rank ?: return@mapNotNull null
                InterestedDelegateUiState(
                    id = delegate.id.value,
                    name = delegate.displayName,
                    preferenceRank = rank,
                    confirmedAssignmentLabel = delegate.confirmedAssignmentLabelUnless(this),
                )
            }
            .sortedWith(compareBy<InterestedDelegateUiState> { it.preferenceRank }.thenBy { it.name }),
    )

private fun Slot.toBoardUiState(): SlotBoardUiState =
    SlotBoardUiState(
        id = id.value,
        index = index,
        status = when (status) {
            AssignmentStatus.Empty -> SlotBoardStatus.Empty
            AssignmentStatus.Pending -> SlotBoardStatus.Pending
            AssignmentStatus.Assigned -> SlotBoardStatus.Assigned
        },
        delegateName = delegate?.displayName,
        matchesDelegatePreference = matchesDelegatePreference,
    )

private fun Delegate.visibleOnAssignmentBoard(): Boolean =
    active && paymentStatus == PaymentStatus.Paid

private fun Delegate.confirmedAssignmentLabelUnless(position: Position): String? {
    val assignedPosition = currentPosition ?: return null
    if (assignedPosition === position || currentSlot?.isAssigned != true) return null
    return "${assignedPosition.committee.displayName} (${assignedPosition.name})"
}
