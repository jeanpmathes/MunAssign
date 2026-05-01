package org.munika.assignment.ui.screen.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.munika.assignment.ui.viewmodel.CommitteeBoardUiState
import org.munika.assignment.ui.viewmodel.SlotBoardStatus

@Composable
fun CommitteeColumn(
    committee: CommitteeBoardUiState,
    loading: Boolean,
    listState: LazyListState,
    onSetSlotsPerPosition: (String, Int) -> Unit,
    onAssignDelegateToSlot: (slotId: String, delegateId: String, moveExistingPending: Boolean) -> Unit,
    onClearPendingSlot: (slotId: String) -> Unit,
    onOpenDelegateDetail: (String) -> Unit,
    highlightedPositionId: String?,
) {
    var blockedReductionMessage by remember(committee.id, committee.slotsPerPosition) { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.width(360.dp).fillMaxHeight(),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            CommitteeHeader(
                committee = committee,
                loading = loading,
                onIncrease = { onSetSlotsPerPosition(committee.id, committee.slotsPerPosition + 1) },
                onDecrease = {
                    val target = committee.slotsPerPosition - 1
                    val blockedPositions = committee.positions
                        .filter { position -> position.slots.any { it.index > target && it.status != SlotBoardStatus.Empty } }
                        .map { it.name }
                    if (blockedPositions.isEmpty()) {
                        onSetSlotsPerPosition(committee.id, target)
                    } else {
                        blockedReductionMessage = "Cannot reduce ${committee.name} to $target slots per position because trailing slots are occupied in ${blockedPositions.joinToString(limit = 3)}."
                    }
                },
            )
            HorizontalDivider()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxHeight().padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(committee.positions, key = { it.id }) { position ->
                    PositionCard(
                        position = position,
                        onAssignDelegateToSlot = onAssignDelegateToSlot,
                        onClearPendingSlot = onClearPendingSlot,
                        onOpenDelegateDetail = onOpenDelegateDetail,
                        highlighted = position.id == highlightedPositionId,
                    )
                }
            }
        }
    }

    blockedReductionMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { blockedReductionMessage = null },
            title = { Text("Slot count cannot be reduced") },
            text = { Text(message) },
            confirmButton = {
                Button(onClick = { blockedReductionMessage = null }) {
                    Text("OK")
                }
            },
        )
    }
}

@Composable
private fun CommitteeHeader(
    committee: CommitteeBoardUiState,
    loading: Boolean,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = committee.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${committee.positions.size} positions",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SlotCountStepper(
                value = committee.slotsPerPosition,
                enabled = !loading,
                onIncrease = onIncrease,
                onDecrease = onDecrease,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CommitteeMetric(label = "Open", value = committee.openSlotCount, tooltip = "Empty slots in this committee.")
            CommitteeMetric(label = "Pending", value = committee.pendingSlotCount, tooltip = "Slots assigned in MunAssign but not yet confirmed by a MyMUN update.")
            CommitteeMetric(label = "Assigned", value = committee.assignedSlotCount, tooltip = "Slots confirmed by MyMUN update data.")
        }
    }
}

@Composable
private fun SlotCountStepper(
    value: Int,
    enabled: Boolean,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
    ) {
        BoardTooltip("Decrease slots per position. If trailing slots are occupied, MunAssign will explain why it cannot reduce the count.") {
            IconButton(
                onClick = onDecrease,
                enabled = enabled && value > 1,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease slots per position", modifier = Modifier.size(16.dp))
            }
        }
        BoardTooltip("Slots per position for this committee.") {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(28.dp),
            )
        }
        BoardTooltip("Increase slots per position for every position in this committee.") {
            IconButton(
                onClick = onIncrease,
                enabled = enabled,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Increase slots per position", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun CommitteeMetric(label: String, value: Int, tooltip: String) {
    BoardTooltip(tooltip) {
        Column(
            modifier = Modifier.width(70.dp).padding(vertical = 2.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
