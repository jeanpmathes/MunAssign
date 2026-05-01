package org.munika.assignment.ui.screen.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.munika.assignment.ui.viewmodel.AssignmentDelegateCandidateUiState
import org.munika.assignment.ui.viewmodel.SlotBoardUiState

@Composable
fun AddDelegateToSlotDialog(
    slot: SlotBoardUiState,
    candidates: List<AssignmentDelegateCandidateUiState>,
    onDismiss: () -> Unit,
    onAssign: (delegateId: String, moveExistingPending: Boolean) -> Unit,
) {
    var query by remember(slot.id) { mutableStateOf("") }
    var pendingMoveCandidate by remember(slot.id) { mutableStateOf<AssignmentDelegateCandidateUiState?>(null) }
    val filteredCandidates = candidates
        .filter { candidate -> query.isBlank() || candidate.name.contains(query.trim(), ignoreCase = true) }
        .sortedBy { candidate -> candidate.id == slot.delegateId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign ${slot.positionName} slot ${slot.index}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search delegates") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(filteredCandidates, key = { it.id }) { candidate ->
                        DelegateCandidateRow(
                            candidate = candidate,
                            alreadyInSlot = candidate.id == slot.delegateId,
                            onClick = {
                                when {
                                    candidate.id == slot.delegateId -> Unit
                                    !candidate.canAssign -> Unit
                                    candidate.isPendingElsewhere -> pendingMoveCandidate = candidate
                                    else -> {
                                        onAssign(candidate.id, false)
                                        onDismiss()
                                    }
                                }
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )

    pendingMoveCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { pendingMoveCandidate = null },
            title = { Text("Move pending assignment?") },
            text = {
                Text(
                    "${candidate.name} is already pending for ${candidate.pendingAssignmentLabel}. Move that pending assignment to ${slot.positionName} slot ${slot.index}?",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAssign(candidate.id, true)
                        pendingMoveCandidate = null
                        onDismiss()
                    },
                ) {
                    Text("Move")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingMoveCandidate = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun DelegateCandidateRow(
    candidate: AssignmentDelegateCandidateUiState,
    alreadyInSlot: Boolean,
    onClick: () -> Unit,
) {
    val disabled = !candidate.canAssign || alreadyInSlot
    val subdued = disabled || candidate.isPendingElsewhere
    TextButton(
        onClick = onClick,
        enabled = !disabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = candidate.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (subdued) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f) else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (disabled) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                candidate.preferenceRank?.let { rank ->
                    Text("Preference #$rank", style = MaterialTheme.typography.labelMedium)
                }
            }
            val detail = if (alreadyInSlot) {
                "Already pending here"
            } else {
                candidate.confirmedAssignmentLabel?.let { "Confirmed for $it" }
                ?: candidate.pendingAssignmentLabel?.let { "Pending for $it" }
                ?: "Available"
            }
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
        }
    }
}
