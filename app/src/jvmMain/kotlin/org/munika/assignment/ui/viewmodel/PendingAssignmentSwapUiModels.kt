package org.munika.assignment.ui.viewmodel

import org.munika.assignment.domain.model.Delegate
import org.munika.assignment.domain.model.PendingAssignmentSwap
import org.munika.assignment.domain.model.Slot

data class PendingAssignmentSwapUiState(
    val sourceSlotId: String,
    val targetSlotId: String,
    val sourceDelegateId: String,
    val targetDelegateId: String,
    val preferenceRank: Int,
    val source: SwapDelegateUiState,
    val target: SwapDelegateUiState,
)

data class SwapDelegateUiState(
    val delegateId: String,
    val name: String,
    val beforeLabel: String,
    val afterLabel: String,
    val heartbrokenAfter: Boolean,
    val schoolFit: DelegateSchoolFit,
    val paymentTierMismatch: PaymentTierMismatchUiState?,
    val paymentTierKnown: Boolean,
)

data class HeartbrokenOccupantUiState(
    val delegateName: String,
    val status: SlotBoardStatus,
)

fun PendingAssignmentSwap.toUiState(): PendingAssignmentSwapUiState = PendingAssignmentSwapUiState(
    sourceSlotId = sourceSlot.id.value,
    targetSlotId = targetSlot.id.value,
    sourceDelegateId = sourceDelegate.id.value,
    targetDelegateId = targetDelegate.id.value,
    preferenceRank = preferenceRank,
    source = sourceDelegate.toSwapUiState(sourceSlot, targetSlot),
    target = targetDelegate.toSwapUiState(targetSlot, sourceSlot),
)

private fun Delegate.toSwapUiState(before: Slot, after: Slot): SwapDelegateUiState = SwapDelegateUiState(
    delegateId = id.value,
    name = displayName,
    beforeLabel = before.swapLabel(),
    afterLabel = after.swapLabel(),
    heartbrokenAfter = preferences.none { it.position === after.position },
    schoolFit = schoolFitFor(after.position),
    paymentTierMismatch = paymentTierMismatchFor(after.position.committee),
    paymentTierKnown = paymentTier != null,
)

private fun Slot.swapLabel(): String = "${position.committee.displayName} (${position.name}) slot $index"
