package org.munika.assignment.ui.screen.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
    assignmentEnabled: Boolean,
    onAssignDelegateToSlot: (slotId: String, delegateId: String, moveExistingPending: Boolean) -> Unit,
    onClearPendingSlot: (slotId: String) -> Unit,
    onOpenDelegateDetail: (String) -> Unit,
    onSetPositionMappedCommittee: (positionId: String, mappedCommitteeId: String?) -> Unit,
    highlighted: Boolean,
) {
    var menuSlot by remember(position.id) { mutableStateOf<SlotBoardUiState?>(null) }
    var assignmentSlot by remember(position.id) { mutableStateOf<SlotBoardUiState?>(null) }
    var pendingMoveRequest by remember(position.id) { mutableStateOf<PendingInterestedMoveRequest?>(null) }
    var interestedDelegatesExpanded by remember(position.id) {
        mutableStateOf(position.shouldShowInterestedDelegates)
    }
    val highSchoolTone = position.highSchoolPositionTone()

    LaunchedEffect(position.shouldShowInterestedDelegates) {
        interestedDelegatesExpanded = position.shouldShowInterestedDelegates
    }

    LaunchedEffect(assignmentEnabled) {
        if (!assignmentEnabled) {
            menuSlot = null
            assignmentSlot = null
            pendingMoveRequest = null
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(
            width = if (highlighted) 2.dp else 1.dp,
            color = if (highlighted) MaterialTheme.colorScheme.primary else highSchoolTone?.borderColor
                ?: MaterialTheme.colorScheme.outlineVariant,
        ),
        color = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
        } else {
            highSchoolTone?.containerColor ?: MaterialTheme.colorScheme.surface
        },
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 0.dp,
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (position.hasOccupiedSlots) {
                        InterestedDelegateBadge(
                            count = position.interestedDelegateCount,
                            expanded = interestedDelegatesExpanded,
                            onToggle = { interestedDelegatesExpanded = !interestedDelegatesExpanded },
                        )
                    }
                    if (highSchoolTone != null) {
                        HighSchoolPositionIcon()
                    }
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
                            assignmentEnabled = assignmentEnabled,
                            onOpenContextMenu = { menuSlot = slot },
                            onOpenAssignmentDialog = { assignmentSlot = slot },
                            onClearPendingAssignment = { onClearPendingSlot(slot.id) },
                            onOpenDelegateDetail = onOpenDelegateDetail,
                        )
                        SlotContextMenu(
                            expanded = menuSlot?.id == slot.id,
                            slot = slot,
                            assignmentEnabled = assignmentEnabled,
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

            val showInterestedDelegates = position.interestedDelegates.isNotEmpty() &&
                (!position.hasOccupiedSlots || interestedDelegatesExpanded)
            if (showInterestedDelegates) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (position.hasOccupiedSlots) "Other interested delegates" else "Interested delegates",
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
                            assignmentEnabled = assignmentEnabled,
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
                    "${request.delegate.name} already has a pending assignment for ${request.delegate.pendingAssignmentLabel}. Move it to ${request.slot.positionName} slot ${request.slot.index} instead?",
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

@Composable
private fun InterestedDelegateBadge(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val enabled = count > 0
    val containerColor = if (enabled) InterestBadgeContainerColor else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (enabled) InterestBadgeContentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
    val delegateLabel = if (count == 1) "delegate" else "delegates"
    val tooltip = when {
        count == 0 -> "No other delegates ranked this position."
        expanded -> "$count other interested $delegateLabel. Click to hide them."
        else -> "$count other interested $delegateLabel. Click to show them."
    }

    BoardTooltip(tooltip) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable(enabled = enabled, onClick = onToggle)
                .background(containerColor)
                .padding(horizontal = 7.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.People,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
            )
        }
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
                value = mappedCommittee?.name.orEmpty(),
                onValueChange = {},
                readOnly = true,
                label = { Text("Linked committee") },
                placeholder = { Text("Select linked committee") },
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (mappedCommittee?.mode == CommitteeMode.HighSchool) {
                            Icon(
                                Icons.Filled.School,
                                contentDescription = "High-school committee",
                                tint = HighSchoolColor,
                            )
                        }
                        if (mappedCommittee != null) {
                            BoardTooltip("Clear linked committee") {
                                IconButton(
                                    onClick = { onSetPositionMappedCommittee(position.id, null) },
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Clear linked committee",
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        } else {
                            BoardTooltip("Select the committee this mapped position should count toward.") {
                                Icon(
                                    Icons.Filled.Warning,
                                    contentDescription = "Linked committee missing",
                                    tint = MissingMappingColor,
                                )
                            }
                        }
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    }
                },
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
                        text = { Text(option.name) },
                        trailingIcon = if (option.mode == CommitteeMode.HighSchool) {
                            {
                                Icon(
                                    Icons.Filled.School,
                                    contentDescription = "High-school committee",
                                    tint = HighSchoolColor,
                                )
                            }
                        } else {
                            null
                        },
                        onClick = {
                            expanded = false
                            onSetPositionMappedCommittee(position.id, option.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PositionBoardUiState.highSchoolPositionTone(): PositionModeTone? =
    if (effectiveMode == CommitteeMode.HighSchool) {
        PositionModeTone(
            containerColor = HighSchoolColor.copy(alpha = 0.07f),
            borderColor = HighSchoolColor.copy(alpha = 0.50f),
        )
    } else {
        null
    }

@Composable
private fun HighSchoolPositionIcon() {
    BoardTooltip("Counts as high-school because of the linked committee.") {
        Icon(
            Icons.Filled.School,
            contentDescription = "High-school position",
            tint = HighSchoolColor,
            modifier = Modifier.height(18.dp),
        )
    }
}

private data class PositionModeTone(
    val containerColor: Color,
    val borderColor: Color,
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
    assignmentEnabled: Boolean,
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
        !assignmentEnabled -> "Wait for the current change to finish before assigning ${delegate.name}."
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
                .alpha(if (assignmentEnabled) 1f else 0.55f)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp))
                .pointerInput(delegate.id, pendingAssignmentLabel, confirmedAssignmentLabel, assignmentEnabled) {
                    detectTapGestures(
                        onTap = { onOpenDelegateDetail() },
                        onDoubleTap = {
                            if (assignmentEnabled && confirmedAssignmentLabel == null) {
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
                BoardTooltip("High-school delegate") {
                    Icon(
                        Icons.Filled.School,
                        contentDescription = "High-school delegate",
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

private val HighSchoolColor = Color(0xFF8E24AA)
private val MissingMappingColor = Color(0xFFF9A825)
private val InterestBadgeContainerColor = Color(0xFFE0F2F1)
private val InterestBadgeContentColor = Color(0xFF00695C)
