package org.munika.assignment.ui.screen.component

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
                val assignableInterestedDelegates = interestedDelegates.filter { it.confirmedAssignmentLabel == null }
                if (assignableInterestedDelegates.isNotEmpty()) {
                    assignableInterestedDelegates.forEach { delegate ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "#${delegate.preferenceRank} ${delegate.name}",
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
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
                    text = { Text("Replace assignment...") },
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
                    text = { Text("Confirmed by MyMUN update") },
                    enabled = false,
                    onClick = {},
                )
            }
        }
    }
}
