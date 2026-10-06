package org.munika.assignment.ui.screen.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.munika.assignment.ui.viewmodel.DelegateAssignmentCommitteeUiState
import org.munika.assignment.ui.viewmodel.DelegateAssignmentPositionUiState
import org.munika.assignment.ui.viewmodel.DelegatePreferenceUiState
import org.munika.assignment.ui.viewmodel.DelegateSchoolFit

@Composable
internal fun ReplacePendingPreferenceDialog(
    preference: DelegatePreferenceUiState,
    onDismiss: () -> Unit,
    onReplace: (String) -> Unit,
) {
    var selectedSlotId by remember(preference.positionId) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Replace pending assignment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose the pending assignment to replace in ${preference.label}. The displaced delegate will become unassigned.")
                preference.replaceablePendingSlots.forEach { slot ->
                    TextButton(
                        onClick = { selectedSlotId = slot.id },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        RadioButton(selected = selectedSlotId == slot.id, onClick = null)
                        Text("Slot ${slot.index}: ${slot.delegateName}", modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedSlotId?.let(onReplace) },
                enabled = selectedSlotId != null,
            ) {
                Text("Replace")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
internal fun DelegateAssignmentChooserDialog(
    committees: List<DelegateAssignmentCommitteeUiState>,
    onDismiss: () -> Unit,
    onAssign: (String) -> Unit,
) {
    var selectedCommitteeId by remember { mutableStateOf<String?>(null) }
    val selectedCommittee = committees.firstOrNull { it.id == selectedCommitteeId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (selectedCommittee == null) "Assign other: choose committee" else selectedCommittee.name) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (selectedCommittee == null) {
                    items(committees, key = { it.id }) { committee ->
                        AssignmentCommitteeRow(
                            committee = committee,
                            onClick = { selectedCommitteeId = committee.id },
                        )
                    }
                } else {
                    items(selectedCommittee.positions, key = { it.id }) { position ->
                        AssignmentPositionRow(position = position, onClick = { onAssign(position.openSlotId) })
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selectedCommittee != null) {
                    TextButton(onClick = { selectedCommitteeId = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Back")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
    )
}

@Composable
private fun AssignmentCommitteeRow(
    committee: DelegateAssignmentCommitteeUiState,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(committee.name, fontWeight = FontWeight.SemiBold)
            Text(
                buildString {
                    append("${committee.positions.size} open position${if (committee.positions.size == 1) "" else "s"}")
                    if (committee.preferenceRanks.isNotEmpty()) {
                        append(" | Preferences ")
                        append(committee.preferenceRanks.joinToString { "#$it" })
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (committee.hasFullyFittingOpenPosition) {
            Text("Fit", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(6.dp))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
    }
}

@Composable
private fun AssignmentPositionRow(
    position: DelegateAssignmentPositionUiState,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FlagAwareText(
                text = position.label,
                flagAssetName = position.flagAssetName,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${position.openSlotCount} open slot${if (position.openSlotCount == 1) "" else "s"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        position.paymentTierMismatch?.let { PaymentTierMismatchIcon(it) }
        if (!position.paymentTierKnown) {
            BoardTooltip("The delegate's payment tier is not classified.") {
                Icon(Icons.Filled.Warning, contentDescription = "Payment tier unassigned", tint = MaterialTheme.colorScheme.error)
            }
        }
        when (position.schoolFit) {
            DelegateSchoolFit.Match -> Unit
            DelegateSchoolFit.Mismatch -> BoardTooltip("The delegate and position school levels do not match.") {
                Icon(Icons.Filled.School, contentDescription = "School-level mismatch", tint = MaterialTheme.colorScheme.error)
            }
            DelegateSchoolFit.Unknown -> BoardTooltip("This mapped position has no effective school level yet.") {
                Icon(Icons.Filled.Warning, contentDescription = "School level unknown", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
