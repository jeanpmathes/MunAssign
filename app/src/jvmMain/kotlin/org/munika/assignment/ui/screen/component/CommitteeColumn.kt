package org.munika.assignment.ui.screen.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.munika.assignment.domain.model.CommitteeMode
import org.munika.assignment.ui.viewmodel.CommitteeBoardUiState
import org.munika.assignment.ui.viewmodel.SlotBoardStatus

@Composable
fun CommitteeColumn(
    committee: CommitteeBoardUiState,
    loading: Boolean,
    listState: LazyListState,
    onSetSlotsPerPosition: (String, Int) -> Unit,
    onSetCommitteeMode: (String, CommitteeMode) -> Unit,
    onSetPositionMappedCommittee: (String, String?) -> Unit,
    onAssignDelegateToSlot: (slotId: String, delegateId: String, moveExistingPending: Boolean) -> Unit,
    onClearPendingSlot: (slotId: String) -> Unit,
    onOpenDelegateDetail: (String) -> Unit,
    onOpenReservationManager: () -> Unit,
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
                onSetMode = { mode -> onSetCommitteeMode(committee.id, mode) },
                onIncrease = { onSetSlotsPerPosition(committee.id, committee.slotsPerPosition + 1) },
                onOpenReservationManager = onOpenReservationManager,
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
                        onSetPositionMappedCommittee = onSetPositionMappedCommittee,
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
    onSetMode: (CommitteeMode) -> Unit,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onOpenReservationManager: () -> Unit,
) {
    val highSchoolColor = Color(0xFF8E24AA)
    val headerBrush = when (committee.mode) {
        CommitteeMode.HighSchool -> Brush.linearGradient(listOf(highSchoolColor.copy(alpha = 0.14f), highSchoolColor.copy(alpha = 0.08f)))
        CommitteeMode.Mapped -> Brush.linearGradient(
            colorStops = arrayOf(
                0.00f to MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f),
                0.38f to MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f),
                0.38f to highSchoolColor.copy(alpha = 0.13f),
                0.58f to highSchoolColor.copy(alpha = 0.13f),
                0.58f to MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f),
                1.00f to MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f),
            ),
            start = Offset.Zero,
            end = Offset(30f, 30f),
            tileMode = TileMode.Repeated,
        )
        CommitteeMode.University -> Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
            ),
        )
    }
    var settingsOpen by remember(committee.id) { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(headerBrush)
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
            BoardTooltip("Open committee settings.") {
                IconButton(
                    onClick = { settingsOpen = true },
                    enabled = !loading,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = "Committee settings", modifier = Modifier.size(18.dp))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CommitteeMetric(label = "Open", value = committee.openSlotCount, tooltip = "Empty slots in this committee.")
            CommitteeMetric(label = "Pending", value = committee.pendingSlotCount, tooltip = "Slots assigned in MunAssign but not yet confirmed by a MyMUN update.")
            CommitteeMetric(label = "Assigned", value = committee.assignedSlotCount, tooltip = "Slots confirmed by MyMUN update data.")
            when (committee.mode) {
                CommitteeMode.HighSchool -> if (committee.universityAssignedCount > 0) {
                    CommitteeMetric(
                        label = "Uni",
                        value = committee.universityAssignedCount,
                        tooltip = "University-level delegates assigned in this high-school committee.",
                        warning = true,
                    )
                }
                CommitteeMode.University -> if (committee.highSchoolerAssignedCount > 0) {
                    CommitteeMetric(
                        label = "HS",
                        value = committee.highSchoolerAssignedCount,
                        tooltip = "High-school delegates assigned in this university committee.",
                        warning = true,
                    )
                }
                CommitteeMode.Mapped -> Unit
            }
        }
        Box(modifier = Modifier.height(22.dp)) {
            if (committee.reservationReminders.isNotEmpty()) {
                val reminderText = committee.reservationReminders.joinToString(" · ") { reminder ->
                    "${reminder.label}: ${reminder.remainingCount} open"
                }
                BoardTooltip("Open reservation manager. These reservations reserve acceptance capacity for this committee type, but do not attach to specific slots.") {
                    Text(
                        text = reminderText,
                        style = if (committee.mode == CommitteeMode.Mapped) {
                            MaterialTheme.typography.labelSmall
                        } else {
                            MaterialTheme.typography.labelMedium
                        },
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clickable(onClick = onOpenReservationManager)
                            .padding(vertical = 2.dp),
                    )
                }
            }
        }
    }

    if (settingsOpen) {
        CommitteeSettingsDialog(
            committee = committee,
            loading = loading,
            onDismiss = { settingsOpen = false },
            onSetMode = onSetMode,
            onIncrease = onIncrease,
            onDecrease = onDecrease,
        )
    }
}

@Composable
private fun CommitteeSettingsDialog(
    committee: CommitteeBoardUiState,
    loading: Boolean,
    onDismiss: () -> Unit,
    onSetMode: (CommitteeMode) -> Unit,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${committee.name} settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.width(320.dp)) {
                CommitteeModeDropdown(
                    mode = committee.mode,
                    enabled = !loading,
                    onSetMode = onSetMode,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Slots per position", style = MaterialTheme.typography.bodyMedium)
                    SlotCountStepper(
                        value = committee.slotsPerPosition,
                        enabled = !loading,
                        onIncrease = onIncrease,
                        onDecrease = onDecrease,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommitteeModeDropdown(
    mode: CommitteeMode,
    enabled: Boolean,
    onSetMode: (CommitteeMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { if (enabled) expanded = !expanded },
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = mode.label,
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                label = { Text("Type") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                singleLine = true,
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = enabled)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                CommitteeMode.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            expanded = false
                            if (option != mode) {
                                onSetMode(option)
                            }
                        },
                    )
                }
            }
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

private val CommitteeMode.label: String
    get() = when (this) {
        CommitteeMode.HighSchool -> "High school"
        CommitteeMode.University -> "University"
        CommitteeMode.Mapped -> "Mapped"
    }

@Composable
private fun CommitteeMetric(label: String, value: Int, tooltip: String, warning: Boolean = false) {
    val valueColor = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val labelColor = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    BoardTooltip(tooltip) {
        Column(
            modifier = Modifier.width(70.dp).padding(vertical = 2.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = valueColor,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
            )
        }
    }
}
