@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.munika.assignment.ui.screen.dialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.munika.assignment.ui.screen.component.BoardTooltip
import org.munika.assignment.ui.viewmodel.ReservationConsumerUiState
import org.munika.assignment.ui.viewmodel.ReservationDelegationOptionUiState
import org.munika.assignment.ui.viewmodel.ReservationManagerUiState
import org.munika.assignment.ui.viewmodel.ReservationSummaryUiState

@Composable
fun ReservationManagerDialog(
    manager: ReservationManagerUiState,
    initialDelegationId: String?,
    reservationActionError: String?,
    onCreateReservation: (name: String, highSchoolCount: Int, universityCount: Int, delegationId: String?) -> Unit,
    onUpdateReservation: (reservationId: String, name: String, highSchoolCount: Int, universityCount: Int, delegationId: String?) -> Unit,
    onDeleteReservation: (reservationId: String) -> Unit,
    onOpenDelegation: (delegationId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var createOpen by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<ReservationSummaryUiState?>(null) }
    var deleteTarget by remember { mutableStateOf<ReservationSummaryUiState?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reservation manager") },
        text = {
            Column(modifier = Modifier.width(680.dp).heightIn(max = 620.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                reservationActionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (manager.reservations.isEmpty()) {
                        item {
                            Text("No reservations yet", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(10.dp))
                        }
                    }
                    items(manager.reservations, key = { it.id }) { reservation ->
                        ReservationCard(
                            reservation = reservation,
                            onEdit = { editTarget = reservation },
                            onDelete = { deleteTarget = reservation },
                            onOpenDelegation = onOpenDelegation,
                        )
                    }
                }
                BoardTooltip("Reserve high-school and/or university capacity for accepted delegates.") {
                    OutlinedButton(onClick = { createOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.ConfirmationNumber, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Create reservation")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        },
    )

    if (createOpen) {
        ReservationEditorDialog(
            title = "Create reservation",
            confirmLabel = "Create",
            delegations = manager.delegations,
            initialName = "",
            initialHighSchoolCount = 0,
            initialUniversityCount = 0,
            initialDelegationId = initialDelegationId,
            error = reservationActionError,
            onDismiss = { createOpen = false },
            onSubmit = { name, highSchoolCount, universityCount, delegationId ->
                onCreateReservation(name, highSchoolCount, universityCount, delegationId)
                createOpen = false
            },
        )
    }

    editTarget?.let { reservation ->
        ReservationEditorDialog(
            title = "Edit reservation",
            confirmLabel = "Save",
            delegations = manager.delegations,
            initialName = reservation.name,
            initialHighSchoolCount = reservation.highSchoolCount,
            initialUniversityCount = reservation.universityCount,
            initialDelegationId = reservation.delegationId,
            error = reservationActionError,
            onDismiss = { editTarget = null },
            onSubmit = { name, highSchoolCount, universityCount, delegationId ->
                onUpdateReservation(reservation.id, name, highSchoolCount, universityCount, delegationId)
                editTarget = null
            },
        )
    }

    deleteTarget?.let { reservation ->
        DeleteReservationDialog(
            reservation = reservation,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                onDeleteReservation(reservation.id)
                deleteTarget = null
            },
        )
    }
}

@Composable
private fun ReservationCard(
    reservation: ReservationSummaryUiState,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenDelegation: (delegationId: String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(reservation.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    ReservationDelegationLink(reservation, onOpenDelegation)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("HS ${reservation.highSchoolUsedCount()}/${reservation.highSchoolCount}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Text("Uni ${reservation.universityUsedCount()}/${reservation.universityCount}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BoardTooltip("Edit ${reservation.name}.") {
                        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit reservation", modifier = Modifier.size(17.dp))
                        }
                    }
                    BoardTooltip("Delete ${reservation.name}.") {
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete reservation", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(17.dp))
                        }
                    }
                }
            }
            ConsumerSection(reservation.consumers)
        }
    }
}

@Composable
private fun ReservationDelegationLink(
    reservation: ReservationSummaryUiState,
    onOpenDelegation: (delegationId: String) -> Unit,
) {
    val delegationId = reservation.delegationId
    val delegationName = reservation.delegationName
    if (delegationId == null || delegationName == null) {
        Text(
            "Unlinked reservation",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }

    BoardTooltip("Open $delegationName in the delegation overview.") {
        Text(
            delegationName,
            modifier = Modifier.clickable { onOpenDelegation(delegationId) },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ConsumerSection(consumers: List<ReservationConsumerUiState>) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("Reserved delegates", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        if (consumers.isEmpty()) {
            Text("None", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                consumers.forEach { consumer ->
                    ConsumerPill(consumer)
                }
            }
        }
    }
}

@Composable
private fun ConsumerPill(consumer: ReservationConsumerUiState) {
    Row(
        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(999.dp)).padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        val suffix = if (consumer.sourceLabel == "Delegation") " (by delegation)" else ""
        Text("${consumer.delegateName}$suffix", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (consumer.isHighSchooler) {
            Icon(Icons.Filled.School, contentDescription = "High-school delegate", tint = HighSchoolColor, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
fun ReservationEditorDialog(
    title: String,
    confirmLabel: String,
    delegations: List<ReservationDelegationOptionUiState>,
    initialName: String,
    initialHighSchoolCount: Int,
    initialUniversityCount: Int,
    initialDelegationId: String?,
    error: String?,
    submitting: Boolean = false,
    onDismiss: () -> Unit,
    onSubmit: (name: String, highSchoolCount: Int, universityCount: Int, delegationId: String?) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var highSchoolCount by remember(initialHighSchoolCount) { mutableStateOf(initialHighSchoolCount.toString()) }
    var universityCount by remember(initialUniversityCount) { mutableStateOf(initialUniversityCount.toString()) }
    var selectedDelegationId by remember(initialDelegationId) { mutableStateOf(initialDelegationId) }
    val highSchoolNumber = highSchoolCount.toIntOrNull()
    val universityNumber = universityCount.toIntOrNull()
    val countError = highSchoolNumber == null || universityNumber == null ||
        highSchoolNumber < 0 || universityNumber < 0 ||
        (highSchoolNumber == 0 && universityNumber == 0)

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = name.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = highSchoolCount,
                        onValueChange = { highSchoolCount = it.filter(Char::isDigit) },
                        label = { Text("High-school count") },
                        singleLine = true,
                        isError = countError,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = universityCount,
                        onValueChange = { universityCount = it.filter(Char::isDigit) },
                        label = { Text("University count") },
                        singleLine = true,
                        isError = countError,
                        modifier = Modifier.weight(1f),
                    )
                }
                DelegationDropdown(
                    delegations = delegations,
                    selectedDelegationId = selectedDelegationId,
                    onSelectDelegation = { selectedDelegationId = it },
                )
                if (countError) {
                    Text("Enter zero or more for each count, with at least one reserved delegate.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmit(name.trim(), highSchoolNumber ?: 0, universityNumber ?: 0, selectedDelegationId)
                },
                enabled = name.isNotBlank() && !countError && !submitting,
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !submitting) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun DeleteReservationDialog(
    reservation: ReservationSummaryUiState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${reservation.name}?") },
        text = {
            Text(
                "This removes the reservation and its current delegate links. Delegates and delegations remain in the conference.",
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Delete")
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
private fun DelegationDropdown(
    delegations: List<ReservationDelegationOptionUiState>,
    selectedDelegationId: String?,
    onSelectDelegation: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = delegations.firstOrNull { it.id == selectedDelegationId }?.label ?: "Unlinked"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Delegation") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.exposedDropdownSize()) {
            DropdownMenuItem(
                text = { Text("Unlinked") },
                onClick = {
                    expanded = false
                    onSelectDelegation(null)
                },
            )
            if (delegations.isNotEmpty()) {
                HorizontalDivider()
            }
            delegations.forEach { delegation ->
                DropdownMenuItem(
                    text = { Text(delegation.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        expanded = false
                        onSelectDelegation(delegation.id)
                    },
                )
            }
        }
    }
}

private fun ReservationSummaryUiState.highSchoolUsedCount(): Int = highSchoolCount - remainingHighSchoolCount

private fun ReservationSummaryUiState.universityUsedCount(): Int = universityCount - remainingUniversityCount

private val HighSchoolColor = Color(0xFF8E24AA)
