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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
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
import org.munika.assignment.domain.model.CommitteeMode
import org.munika.assignment.ui.screen.dialog.AddDelegateToSlotDialog
import org.munika.assignment.ui.viewmodel.InterestedDelegateUiState
import org.munika.assignment.ui.viewmodel.PositionBoardUiState
import org.munika.assignment.ui.viewmodel.SlotBoardUiState

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PositionCard(
    position: PositionBoardUiState,
    onAssignDelegateToSlot: (slotId: String, delegateId: String, moveExistingPending: Boolean) -> Unit,
    onClearPendingSlot: (slotId: String) -> Unit,
    onOpenDelegateDetail: (String) -> Unit,
    onSetPositionMappedCommittee: (positionId: String, mappedCommitteeId: String?) -> Unit,
    highlighted: Boolean,
) {
    var menuSlot by remember(position.id) { mutableStateOf<SlotBoardUiState?>(null) }
    var assignmentSlot by remember(position.id) { mutableStateOf<SlotBoardUiState?>(null) }
    var pendingMoveRequest by remember(position.id) { mutableStateOf<PendingInterestedMoveRequest?>(null) }
    val mappedTone = position.mappedPositionTone()

    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(
            width = if (highlighted) 2.dp else 1.dp,
            color = if (highlighted) MaterialTheme.colorScheme.primary else mappedTone?.borderColor
                ?: MaterialTheme.colorScheme.outlineVariant,
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
            } else {
                mappedTone?.containerColor ?: MaterialTheme.colorScheme.surface
            },
        ),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (highlighted) 5.dp else 1.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    FlagAwareText(
                        text = position.displayName,
                        flagAssetName = position.flagAssetName,
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
                mappedTone?.let { tone ->
                    PositionModeBadge(tone)
                }
            }

            if (position.showsMappingSelector) {
                MappedCommitteeSelector(
                    position = position,
                    onSetPositionMappedCommittee = onSetPositionMappedCommittee,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                position.slots.forEach { slot ->
                    Box {
                        SlotRow(
                            slot = slot,
                            onOpenContextMenu = { menuSlot = slot },
                            onOpenAssignmentDialog = { assignmentSlot = slot },
                            onClearPendingAssignment = { onClearPendingSlot(slot.id) },
                            onOpenDelegateDetail = onOpenDelegateDetail,
                        )
                        SlotContextMenu(
                            expanded = menuSlot?.id == slot.id,
                            slot = slot,
                            interestedDelegates = position.interestedDelegates,
                            onAssignInterestedDelegate = { delegate ->
                                assignInterestedDelegate(
                                    slot = slot,
                                    delegate = delegate,
                                    onMovePending = { pendingMoveRequest = PendingInterestedMoveRequest(slot, delegate) },
                                    onAssign = { onAssignDelegateToSlot(slot.id, delegate.id, false) },
                                )
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
                                assignInterestedDelegate(
                                    slot = slot,
                                    delegate = delegate,
                                    onMovePending = { pendingMoveRequest = PendingInterestedMoveRequest(slot, delegate) },
                                    onAssign = { onAssignDelegateToSlot(slot.id, delegate.id, false) },
                                )
                            },
                            onOpenDelegateDetail = { onOpenDelegateDetail(delegate.id) },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MappedCommitteeSelector(
    position: PositionBoardUiState,
    onSetPositionMappedCommittee: (positionId: String, mappedCommitteeId: String?) -> Unit,
) {
    var expanded by remember(position.id) { mutableStateOf(false) }
    val mappedCommittee = position.mappedCommittee
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = mappedCommittee?.let { "${it.name} (${it.mode.label})" }.orEmpty(),
                onValueChange = {},
                readOnly = true,
                label = { Text("Corresponding committee") },
                placeholder = { Text("Select correspondence") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                singleLine = true,
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                position.mappingOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text("${option.name} (${option.mode.label})") },
                        onClick = {
                            expanded = false
                            onSetPositionMappedCommittee(position.id, option.id)
                        },
                    )
                }
                if (mappedCommittee != null) {
                    DropdownMenuItem(
                        text = { Text("Clear correspondence") },
                        onClick = {
                            expanded = false
                            onSetPositionMappedCommittee(position.id, null)
                        },
                    )
                }
            }
        }
        if (position.mappingIncomplete) {
            Text(
                text = "Correspondence missing",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PositionBoardUiState.mappedPositionTone(): PositionModeTone? {
    if (!showsMappingSelector) return null
    return when (effectiveMode) {
        CommitteeMode.HighSchool -> PositionModeTone(
            label = "HS",
            containerColor = HighSchoolColor.copy(alpha = 0.07f),
            borderColor = HighSchoolColor.copy(alpha = 0.50f),
            badgeColor = HighSchoolColor.copy(alpha = 0.16f),
            contentColor = HighSchoolColor,
        )
        CommitteeMode.University -> PositionModeTone(
            label = "Uni",
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
            borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.42f),
            badgeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            contentColor = MaterialTheme.colorScheme.primary,
        )
        CommitteeMode.Mapped,
        null,
        -> null
    }
}

@Composable
private fun PositionModeBadge(tone: PositionModeTone) {
    BoardTooltip("${tone.label} position inherited from the corresponding committee.") {
        Text(
            text = tone.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = tone.contentColor,
            modifier = Modifier
                .background(tone.badgeColor, RoundedCornerShape(999.dp))
                .padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

private data class PositionModeTone(
    val label: String,
    val containerColor: Color,
    val borderColor: Color,
    val badgeColor: Color,
    val contentColor: Color,
)

private data class PendingInterestedMoveRequest(
    val slot: SlotBoardUiState,
    val delegate: InterestedDelegateUiState,
)

private fun assignInterestedDelegate(
    slot: SlotBoardUiState,
    delegate: InterestedDelegateUiState,
    onMovePending: () -> Unit,
    onAssign: () -> Unit,
) {
    when {
        !delegate.canAssign -> Unit
        delegate.pendingAssignmentSlotId == slot.id -> Unit
        delegate.pendingAssignmentSlotId != null -> onMovePending()
        else -> onAssign()
    }
}

@Composable
private fun InterestedDelegateChip(
    delegate: InterestedDelegateUiState,
    onAssign: () -> Unit,
    onOpenDelegateDetail: () -> Unit,
) {
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
                    detectTapGestures(
                        onTap = { onOpenDelegateDetail() },
                        onDoubleTap = {
                            if (confirmedAssignmentLabel == null) {
                                onAssign()
                            }
                        },
                    )
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
            if (delegate.isHighSchooler) {
                BoardTooltip("High school delegate.") {
                    Icon(
                        Icons.Filled.School,
                        contentDescription = "High school delegate",
                        tint = Color(0xFF8E24AA),
                        modifier = Modifier.height(14.dp),
                    )
                }
            }
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

private val PositionBoardUiState.showsMappingSelector: Boolean
    get() = mappingOptions.isNotEmpty() || mappedCommittee != null || mappingIncomplete

private val CommitteeMode.label: String
    get() = when (this) {
        CommitteeMode.HighSchool -> "HS"
        CommitteeMode.University -> "Uni"
        CommitteeMode.Mapped -> "Mapped"
    }

private val HighSchoolColor = Color(0xFF8E24AA)
