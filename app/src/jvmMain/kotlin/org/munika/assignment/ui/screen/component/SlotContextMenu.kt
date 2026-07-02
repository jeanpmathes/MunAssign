package org.munika.assignment.ui.screen.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.munika.assignment.ui.viewmodel.InterestedDelegateUiState
import org.munika.assignment.ui.viewmodel.SlotBoardStatus
import org.munika.assignment.ui.viewmodel.SlotBoardUiState

@Composable
fun SlotContextMenu(
    expanded: Boolean,
    slot: SlotBoardUiState,
    interestedDelegates: List<InterestedDelegateUiState>,
    onAssignInterestedDelegate: (InterestedDelegateUiState) -> Unit,
    onOpenManualAssignment: () -> Unit,
    onClearPendingAssignment: () -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        when (slot.status) {
            SlotBoardStatus.Empty -> {
                val assignableInterestedDelegates = interestedDelegates.filter { it.canAssign }
                if (assignableInterestedDelegates.isNotEmpty()) {
                    assignableInterestedDelegates.forEach { delegate ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = "#${delegate.preferenceRank} ${delegate.name}",
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (delegate.isHighSchooler) {
                                        Icon(
                                            Icons.Filled.School,
                                            contentDescription = "High-school delegate",
                                            tint = Color(0xFF8E24AA),
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onDismiss()
                                onAssignInterestedDelegate(delegate)
                            },
                        )
                    }
                    HorizontalDivider()
                }
                DropdownMenuItem(
                    text = { Text("Assign delegate...") },
                    onClick = {
                        onDismiss()
                        onOpenManualAssignment()
                    },
                )
            }
            SlotBoardStatus.Pending -> {
                DropdownMenuItem(
                    text = { Text("Replace pending assignment...") },
                    onClick = {
                        onDismiss()
                        onOpenManualAssignment()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Clear pending assignment") },
                    onClick = {
                        onDismiss()
                        onClearPendingAssignment()
                    },
                )
            }
            SlotBoardStatus.Assigned -> {
                DropdownMenuItem(
                    text = { Text("Confirmed assignment") },
                    enabled = false,
                    onClick = {},
                )
            }
        }
    }
}
