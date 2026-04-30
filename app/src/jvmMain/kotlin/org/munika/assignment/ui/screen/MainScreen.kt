@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.munika.assignment.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.Delegate
import org.munika.assignment.domain.model.PaymentStatus
import org.munika.assignment.domain.repository.UpdateIssue
import org.munika.assignment.domain.repository.UpdateIssueSeverity
import org.munika.assignment.domain.repository.UpdateSummary
import org.munika.assignment.ui.screen.component.AssignmentBoard
import org.munika.assignment.ui.viewmodel.MainUiState
import org.munika.assignment.ui.viewmodel.toAssignmentBoardUiState

@Composable
fun MainScreen(
    state: MainUiState,
    onSelectConference: (String) -> Unit,
    onCreateConference: (String) -> Boolean,
    onRenameConference: (String, String) -> Boolean,
    onDeleteConference: (String) -> Unit,
    onReorderConferences: (List<String>) -> Boolean,
    onSetCommitteeSlotsPerPosition: (String, Int) -> Unit,
    onOpenWorkbook: () -> Unit,
    onClearConferenceActionError: () -> Unit,
    onClearBoardActionError: () -> Unit,
) {
    var createDialogOpen by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Conference?>(null) }
    var managementOpen by remember { mutableStateOf(false) }
    val selectedConference = state.selectedConference

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                state = state,
                onSelectConference = onSelectConference,
                onOpenCreate = {
                    onClearConferenceActionError()
                    createDialogOpen = true
                },
                onOpenRename = { conference ->
                    onClearConferenceActionError()
                    renameTarget = conference
                },
                onOpenManagement = {
                    onClearConferenceActionError()
                    managementOpen = true
                },
                onOpenWorkbook = onOpenWorkbook,
            )
            Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                when {
                    state.loading && state.conferences.isEmpty() -> LoadingState()
                    state.conferences.isEmpty() -> NoConferenceState(
                        onCreate = {
                            onClearConferenceActionError()
                            createDialogOpen = true
                        },
                    )
                    selectedConference == null -> LoadingState()
                    selectedConference.committees.isEmpty() -> EmptyConferenceWorkspace(
                        loading = state.loading,
                        summary = state.updateSummary,
                        issues = state.updateIssues,
                        onOpenWorkbook = onOpenWorkbook,
                    )
                    else -> ConferenceWorkspace(
                        conference = selectedConference,
                        loading = state.loading,
                        summary = state.updateSummary,
                        issues = state.updateIssues,
                        boardActionError = state.boardActionError,
                        boardScrollRequest = state.boardScrollRequest,
                        onSetCommitteeSlotsPerPosition = onSetCommitteeSlotsPerPosition,
                        onClearBoardActionError = onClearBoardActionError,
                    )
                }
            }
        }
    }

    if (createDialogOpen) {
        ConferenceNameDialog(
            title = "Create conference",
            confirmLabel = "Create",
            initialName = "",
            error = state.conferenceActionError,
            onDismiss = {
                createDialogOpen = false
                onClearConferenceActionError()
            },
            onConfirm = { name ->
                if (onCreateConference(name)) {
                    createDialogOpen = false
                }
            },
        )
    }

    renameTarget?.let { conference ->
        ConferenceNameDialog(
            title = "Rename conference",
            confirmLabel = "Rename",
            initialName = conference.name,
            error = state.conferenceActionError,
            onDismiss = {
                renameTarget = null
                onClearConferenceActionError()
            },
            onConfirm = { name ->
                if (onRenameConference(conference.id.value, name)) {
                    renameTarget = null
                }
            },
        )
    }

    if (managementOpen) {
        ConferenceManagementDialog(
            state = state,
            onDismiss = {
                managementOpen = false
                onClearConferenceActionError()
            },
            onCreateConference = onCreateConference,
            onRenameConference = onRenameConference,
            onDeleteConference = onDeleteConference,
            onReorderConferences = onReorderConferences,
            onClearConferenceActionError = onClearConferenceActionError,
        )
    }
}

@Composable
private fun TopBar(
    state: MainUiState,
    onSelectConference: (String) -> Unit,
    onOpenCreate: () -> Unit,
    onOpenRename: (Conference) -> Unit,
    onOpenManagement: () -> Unit,
    onOpenWorkbook: (() -> Unit)? = null,
) {
    Surface(shadowElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ConferenceDropdown(
                    conferences = state.conferences,
                    selectedConferenceId = state.selectedConferenceId,
                    enabled = !state.loading,
                    onSelectConference = onSelectConference,
                    onOpenCreate = onOpenCreate,
                    onOpenRename = onOpenRename,
                )
                TooltipIconButton(
                    tooltip = "Manage conferences",
                    icon = Icons.Filled.ManageAccounts,
                    onClick = onOpenManagement,
                    enabled = !state.loading,
                )
            }

            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                WithTooltip("Select a MyMUN Excel workbook to update the selected conference.") {
                    Button(
                        onClick = { onOpenWorkbook?.invoke() },
                        enabled = !state.loading && state.selectedConferenceId != null && onOpenWorkbook != null,
                    ) {
                        Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Update")
                    }
                }
            }

            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                if (state.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Updating", style = MaterialTheme.typography.bodyMedium)
                } else {
                    state.selectedConference?.let { ConferenceStats(it) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConferenceDropdown(
    conferences: List<Conference>,
    selectedConferenceId: String?,
    enabled: Boolean,
    onSelectConference: (String) -> Unit,
    onOpenCreate: () -> Unit,
    onOpenRename: (Conference) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = conferences.firstOrNull { it.id.value == selectedConferenceId }?.name ?: "Select conference"
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = { Text("Conference") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier.menuAnchor().width(300.dp),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.exposedDropdownSize(),
        ) {
            conferences.forEach { conference ->
                DropdownMenuItem(
                    text = {
                        Text(conference.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    onClick = {
                        expanded = false
                        onSelectConference(conference.id.value)
                    },
                    trailingIcon = {
                        TooltipIconButton(
                            tooltip = "Rename ${conference.name}",
                            icon = Icons.Filled.Edit,
                            onClick = {
                                expanded = false
                                onOpenRename(conference)
                            },
                            modifier = Modifier.size(32.dp),
                        )
                    },
                )
            }
            if (conferences.isNotEmpty()) {
                HorizontalDivider()
            }
            DropdownMenuItem(
                text = { Text("Add conference") },
                onClick = {
                    expanded = false
                    onOpenCreate()
                },
                trailingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
            )
        }
    }
}

@Composable
private fun ConferenceStats(conference: Conference) {
    val acceptedApplications = conference.delegates.filter { it.hasActionableApplicationStatus() }
    val submittedApplications = conference.delegates.count { it.submittedAt != null && !it.hasActionableApplicationStatus() }
    val paidApplications = acceptedApplications.count { it.paymentStatus == PaymentStatus.Paid }
    val unpaidApplications = acceptedApplications.count { it.paymentStatus == PaymentStatus.Unpaid }
    val activePaidDelegates = conference.delegates.count { it.active && it.paymentStatus == PaymentStatus.Paid }
    val assignedActivePaidDelegates = conference.delegates.count {
        it.active && it.paymentStatus == PaymentStatus.Paid && it.currentSlot != null
    }
    val totalSlots = conference.slots.size
    val filledSlots = conference.pendingSlots.size + conference.assignedSlots.size

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        StatGroup(
            label = "Applications",
            value = "${acceptedApplications.size} accepted · $paidApplications/$unpaidApplications paid/unpaid · $submittedApplications submitted",
            tooltip = "Accepted means MyMUN applications with Accepted or Assigned status. Paid/unpaid is counted within accepted applications. Submitted counts applications with a submission date that are not accepted yet.",
        )
        StatGroup(
            label = "Delegates",
            value = "$assignedActivePaidDelegates / $activePaidDelegates assigned",
            tooltip = "Active paid delegates assigned to any slot out of all active paid delegates.",
        )
        StatGroup(
            label = "Slots",
            value = "$filledSlots / $totalSlots filled",
            tooltip = "Slots with pending app assignments or confirmed MyMUN assignments out of all available slots.",
        )
    }
}

@Composable
private fun StatGroup(label: String, value: String, tooltip: String) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState(),
    ) {
        Column(
            modifier = Modifier
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TooltipIconButton(
    tooltip: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = modifier) {
            Icon(icon, contentDescription = tooltip, tint = if (enabled) tint else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f))
        }
    }
}

@Composable
private fun WithTooltip(tooltip: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState(),
    ) {
        content()
    }
}

@Composable
private fun NoConferenceState(onCreate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No conferences yet", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        WithTooltip("Create the first conference.") {
            Button(onClick = onCreate) {
                Text("Create conference")
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyConferenceWorkspace(
    loading: Boolean,
    summary: UpdateSummary?,
    issues: List<UpdateIssue>,
    onOpenWorkbook: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("No MyMUN update data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Drop an Excel workbook onto the window or open one manually to create committees, positions, delegates, and assignments.")
                WithTooltip("Select a MyMUN Excel workbook to update this conference.") {
                    Button(onClick = onOpenWorkbook, enabled = !loading) {
                        Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                    Text("Update")
                    }
                }
            }
        }
        UpdateResultSurface(summary = summary, issues = issues)
    }
}

@Composable
private fun ConferenceWorkspace(
    conference: Conference,
    loading: Boolean,
    summary: UpdateSummary?,
    issues: List<UpdateIssue>,
    boardActionError: String?,
    boardScrollRequest: org.munika.assignment.ui.viewmodel.BoardScrollRequest?,
    onSetCommitteeSlotsPerPosition: (String, Int) -> Unit,
    onClearBoardActionError: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        UpdateResultSurface(summary = summary, issues = issues)
        AssignmentBoard(
            board = conference.toAssignmentBoardUiState(),
            loading = loading,
            actionError = boardActionError,
            scrollRequest = boardScrollRequest,
            onSetCommitteeSlotsPerPosition = onSetCommitteeSlotsPerPosition,
            onClearActionError = onClearBoardActionError,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun UpdateResultSurface(summary: UpdateSummary?, issues: List<UpdateIssue>) {
    if (summary == null && issues.isEmpty()) return
    Card(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Latest MyMUN update", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            summary?.let {
                Text(
                    "${it.blockingErrorCount} errors, ${it.warningCount} warnings | " +
                        "delegates +${it.delegatesCreated}/~${it.delegatesUpdated}/-${it.delegatesRemoved} | " +
                        "positions +${it.positionsCreated}/~${it.positionsUpdated}/-${it.positionsRemoved} | " +
                        "confirmed ${it.assignmentsConfirmed}",
                )
            }
            issues.takeLast(8).forEach { issue ->
                val marker = if (issue.severity == UpdateIssueSeverity.Error) "Error" else "Warning"
                Text("$marker: ${issue.message}", color = if (issue.severity == UpdateIssueSeverity.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun ConferenceNameDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Conference name") },
                    singleLine = true,
                    isError = error != null,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name) }) {
                Text(confirmLabel)
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
private fun ConferenceManagementDialog(
    state: MainUiState,
    onDismiss: () -> Unit,
    onCreateConference: (String) -> Boolean,
    onRenameConference: (String, String) -> Boolean,
    onDeleteConference: (String) -> Unit,
    onReorderConferences: (List<String>) -> Boolean,
    onClearConferenceActionError: () -> Unit,
) {
    val orderedConferences = remember { state.conferences.toMutableStateList() }
    var createDialogOpen by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Conference?>(null) }
    var deleteTarget by remember { mutableStateOf<Conference?>(null) }

    LaunchedEffect(state.conferences) {
        orderedConferences.replaceAll(state.conferences)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage conferences") },
        text = {
            Column(modifier = Modifier.width(560.dp).fillMaxHeight(0.72f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.conferenceActionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(orderedConferences, key = { _, conference -> conference.id.value }) { index, conference ->
                        ReorderableConferenceRow(
                            conference = conference,
                            index = index,
                            conferences = orderedConferences,
                            reorderEnabled = !state.loading,
                            onRename = {
                                onClearConferenceActionError()
                                renameTarget = conference
                            },
                            onDelete = {
                                onClearConferenceActionError()
                                deleteTarget = conference
                            },
                            onMoved = {
                                onReorderConferences(orderedConferences.map { it.id.value })
                            },
                        )
                    }
                }
                WithTooltip("Create a new conference.") {
                    OutlinedButton(
                        onClick = {
                            onClearConferenceActionError()
                            createDialogOpen = true
                        },
                        enabled = !state.loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Create conference")
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

    if (createDialogOpen) {
        ConferenceNameDialog(
            title = "Create conference",
            confirmLabel = "Create",
            initialName = "",
            error = state.conferenceActionError,
            onDismiss = {
                createDialogOpen = false
                onClearConferenceActionError()
            },
            onConfirm = { name ->
                if (onCreateConference(name)) {
                    createDialogOpen = false
                }
            },
        )
    }

    renameTarget?.let { conference ->
        ConferenceNameDialog(
            title = "Rename conference",
            confirmLabel = "Rename",
            initialName = conference.name,
            error = state.conferenceActionError,
            onDismiss = {
                renameTarget = null
                onClearConferenceActionError()
            },
            onConfirm = { name ->
                if (onRenameConference(conference.id.value, name)) {
                    renameTarget = null
                }
            },
        )
    }

    deleteTarget?.let { conference ->
        DeleteConferenceDialog(
            conference = conference,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                onDeleteConference(conference.id.value)
                deleteTarget = null
            },
        )
    }
}

@Composable
private fun ReorderableConferenceRow(
    conference: Conference,
    index: Int,
    conferences: SnapshotStateList<Conference>,
    reorderEnabled: Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMoved: () -> Unit,
) {
    var accumulatedDrag by remember { mutableFloatStateOf(0f) }
    Row(
        modifier = Modifier.fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Filled.DragIndicator,
            contentDescription = "Drag to reorder ${conference.name}",
            modifier = Modifier.pointerInput(reorderEnabled, index, conferences.size) {
                if (!reorderEnabled) return@pointerInput
                detectVerticalDragGestures(
                    onDragEnd = { accumulatedDrag = 0f },
                    onDragCancel = { accumulatedDrag = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDrag += dragAmount
                        if (abs(accumulatedDrag) >= 36f) {
                            val direction = if (accumulatedDrag > 0) 1 else -1
                            val target = (index + direction).coerceIn(0, conferences.lastIndex)
                            if (target != index) {
                                conferences.move(index, target)
                                onMoved()
                            }
                            accumulatedDrag = 0f
                        }
                    },
                )
            },
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(conference.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
        }
        TooltipIconButton(
            tooltip = "Rename ${conference.name}",
            icon = Icons.Filled.Edit,
            onClick = onRename,
            enabled = reorderEnabled,
        )
        TooltipIconButton(
            tooltip = "Delete ${conference.name}",
            icon = Icons.Filled.Delete,
            onClick = onDelete,
            enabled = reorderEnabled,
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun DeleteConferenceDialog(
    conference: Conference,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete ${conference.name}?") },
        text = {
            Text(
                "This removes the conference and all of its committees, positions, slots, delegates, preferences, answers, comments, delegations, and update history.",
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

private fun SnapshotStateList<Conference>.replaceAll(conferences: List<Conference>) {
    clear()
    addAll(conferences)
}

private fun SnapshotStateList<Conference>.move(from: Int, to: Int) {
    if (from == to) return
    val item = removeAt(from)
    add(to, item)
}

private fun Delegate.hasActionableApplicationStatus(): Boolean {
    val normalizedStatus = profile.applicationStatus?.trim()?.lowercase().orEmpty()
    return normalizedStatus == "accepted" || normalizedStatus == "assigned"
}
