package org.munika.assignment.ui.screen.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.munika.assignment.ui.screen.dialog.AddDelegateToSlotDialog
import org.munika.assignment.ui.viewmodel.InterestedDelegateUiState
import org.munika.assignment.ui.viewmodel.PositionBoardUiState
import org.munika.assignment.ui.viewmodel.SlotBoardUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PositionCard(
    position: PositionBoardUiState,
    onAssignDelegateToSlot: (slotId: String, delegateId: String, moveExistingPending: Boolean) -> Unit,
    onClearPendingSlot: (slotId: String) -> Unit,
) {
    var menuSlot by remember(position.id) { mutableStateOf<SlotBoardUiState?>(null) }
    var assignmentSlot by remember(position.id) { mutableStateOf<SlotBoardUiState?>(null) }
    var pendingMoveRequest by remember(position.id) { mutableStateOf<PendingInterestedMoveRequest?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = position.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    BoardTooltip("Filled slots out of total slots for this position.") {
                        Text(
                            text = "${position.occupiedSlotCount}/${position.slots.size} filled",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                position.slots.forEach { slot ->
                    Box {
                        SlotRow(
                            slot = slot,
                            onOpenContextMenu = { menuSlot = slot },
                            onOpenAssignmentDialog = { assignmentSlot = slot },
                            onClearPendingAssignment = { onClearPendingSlot(slot.id) },
                        )
                        SlotContextMenu(
                            expanded = menuSlot?.id == slot.id,
                            slot = slot,
                            interestedDelegates = position.interestedDelegates,
                            onAssignInterestedDelegate = { delegate ->
                                if (delegate.pendingAssignmentLabel == null) {
                                    onAssignDelegateToSlot(slot.id, delegate.id, false)
                                } else {
                                    pendingMoveRequest = PendingInterestedMoveRequest(slot, delegate)
                                }
                            },
                            onOpenManualAssignment = { assignmentSlot = slot },
                            onClearPendingAssignment = { onClearPendingSlot(slot.id) },
                            onDismiss = { menuSlot = null },
                        )
                    }
                }
            }

            if (position.shouldShowInterestedDelegates && position.interestedDelegates.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Interested delegates",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    position.interestedDelegates.forEach { delegate ->
                        InterestedDelegateChip(
                            delegate = delegate,
                            onAssign = {
                                val slot = position.slots.firstOrNull { it.status == org.munika.assignment.ui.viewmodel.SlotBoardStatus.Empty }
                                    ?: return@InterestedDelegateChip
                                if (delegate.pendingAssignmentLabel == null) {
                                    onAssignDelegateToSlot(slot.id, delegate.id, false)
                                } else {
                                    pendingMoveRequest = PendingInterestedMoveRequest(slot, delegate)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    assignmentSlot?.let { slot ->
        AddDelegateToSlotDialog(
            slot = slot,
            candidates = position.assignmentCandidates,
            onDismiss = { assignmentSlot = null },
            onAssign = { delegateId, moveExistingPending ->
                onAssignDelegateToSlot(slot.id, delegateId, moveExistingPending)
            },
        )
    }

    pendingMoveRequest?.let { request ->
        AlertDialog(
            onDismissRequest = { pendingMoveRequest = null },
            title = { Text("Move pending assignment?") },
            text = {
                Text(
                    "${request.delegate.name} is already pending for ${request.delegate.pendingAssignmentLabel}. Move that pending assignment to ${request.slot.positionName} slot ${request.slot.index}?",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAssignDelegateToSlot(request.slot.id, request.delegate.id, true)
                        pendingMoveRequest = null
                    },
                ) {
                    Text("Move")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingMoveRequest = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

private data class PendingInterestedMoveRequest(
    val slot: SlotBoardUiState,
    val delegate: InterestedDelegateUiState,
)

@Composable
private fun InterestedDelegateChip(delegate: InterestedDelegateUiState, onAssign: () -> Unit) {
    val confirmedAssignmentLabel = delegate.confirmedAssignmentLabel
    val pendingAssignmentLabel = delegate.pendingAssignmentLabel
    val textColor = if (confirmedAssignmentLabel != null || pendingAssignmentLabel != null) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val rankStyle = delegate.rankStyle()
    val tooltip = when {
        confirmedAssignmentLabel != null ->
            "${delegate.name} ranked this position #${delegate.preferenceRank}, but is confirmed for $confirmedAssignmentLabel."
        pendingAssignmentLabel != null ->
            "${delegate.name} ranked this position #${delegate.preferenceRank} and is pending for $pendingAssignmentLabel."
        else -> "${delegate.name} ranked this position #${delegate.preferenceRank}."
    }
    BoardTooltip(tooltip) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp))
                .pointerInput(delegate.id, pendingAssignmentLabel, confirmedAssignmentLabel) {
                    if (confirmedAssignmentLabel != null) return@pointerInput
                    detectTapGestures(onDoubleTap = { onAssign() })
                }
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = "#${delegate.preferenceRank}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = rankStyle.contentColor,
                modifier = Modifier
                    .background(rankStyle.containerColor, RoundedCornerShape(999.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
            Text(
                text = delegate.name,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (confirmedAssignmentLabel != null) TextDecoration.LineThrough else TextDecoration.None,
            )
        }
    }
}

@Composable
private fun InterestedDelegateUiState.rankStyle(): RankBadgeStyle =
    when {
        confirmedAssignmentLabel != null -> RankBadgeStyle(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        pendingAssignmentLabel != null -> RankBadgeStyle(
            containerColor = Color(0xFFFFE2A8),
            contentColor = Color(0xFF4A2B00),
        )
        else -> RankBadgeStyle(
            containerColor = Color(0xFFDFF5DF),
            contentColor = Color(0xFF163F1A),
        )
    }

private data class RankBadgeStyle(
    val containerColor: Color,
    val contentColor: Color,
)
