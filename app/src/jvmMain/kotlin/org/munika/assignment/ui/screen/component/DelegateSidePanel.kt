package org.munika.assignment.ui.screen.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.net.URI
import org.munika.assignment.ui.viewmodel.DelegateAnswerIssueUiState
import org.munika.assignment.ui.viewmodel.DelegateCommentUiState
import org.munika.assignment.ui.viewmodel.DelegateDetailUiState
import org.munika.assignment.ui.viewmodel.DelegatePreferenceUiState
import org.munika.assignment.ui.viewmodel.DelegateResolvedIssueUiState
import org.munika.assignment.ui.viewmodel.DelegateStatusChipUiState
import org.munika.assignment.ui.viewmodel.DelegateStatusTone
import org.munika.assignment.ui.viewmodel.ReservationSummaryUiState
import org.munika.assignment.domain.model.PaymentTier

private const val ManualReservationSourceLabel = "Manual"

@Composable
fun DelegateSidePanel(
    detail: DelegateDetailUiState,
    loading: Boolean,
    onClose: () -> Unit,
    onNavigateToPosition: (committeeId: String, positionId: String) -> Unit,
    onDeactivate: (String) -> Unit,
    onReactivate: (String) -> Unit,
    onAddComment: (delegateId: String, body: String) -> Unit,
    onUpdateComment: (commentId: String, body: String) -> Unit,
    onDeleteComment: (commentId: String) -> Unit,
    reservationOptions: List<ReservationSummaryUiState> = emptyList(),
    reservationActionError: String? = null,
    onAssignDelegateReservation: (delegateId: String, reservationId: String) -> Unit = { _, _ -> },
    onRemoveDelegateReservation: (delegateId: String) -> Unit = {},
    onOpenNotices: (delegateId: String) -> Unit,
    onResolveIssue: (delegateId: String, questionId: String?, issueKey: String, justification: String?) -> Unit,
    onUnresolveIssue: (resolutionId: String) -> Unit,
    onSetPaymentTier: (delegateId: String, paymentTier: PaymentTier) -> Unit,
    modifier: Modifier = Modifier,
) {
    var deactivateDialogOpen by remember(detail.id) { mutableStateOf(false) }
    var resolveTarget by remember(detail.id) { mutableStateOf<DelegateAnswerIssueUiState?>(null) }
    val sectionExpanded = remember(detail.id) {
        mutableStateMapOf(
            "Profile" to true,
            "Preferences" to true,
            "Answers" to false,
            "Comments" to true,
        )
    }

    Surface(
        modifier = modifier.width(430.dp).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            Header(
                detail = detail,
                loading = loading,
                onSetPaymentTier = { tier -> onSetPaymentTier(detail.id, tier) },
                onClose = onClose,
            )
            HorizontalDivider()
            LazyColumn(
                modifier = Modifier.weight(1f).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    AssignmentCard(detail, onNavigateToPosition)
                }
                item {
                    ReservationCard(
                        detail = detail,
                        loading = loading,
                        options = reservationOptions,
                        actionError = reservationActionError,
                        onAssignReservation = { reservationId -> onAssignDelegateReservation(detail.id, reservationId) },
                        onRemoveReservation = { onRemoveDelegateReservation(detail.id) },
                    )
                }
                item {
                    FoldableSection(
                        title = "Profile",
                        expanded = sectionExpanded["Profile"] == true,
                        onToggle = { sectionExpanded["Profile"] = sectionExpanded["Profile"] != true },
                        headerContent = {
                            if (detail.profileWarningCount > 0) {
                                CountChip("${detail.profileWarningCount} warnings", DelegateStatusTone.Yellow)
                            }
                            if (detail.profileNoticeCount > 0) {
                                CountChip("${detail.profileNoticeCount} notices", DelegateStatusTone.Notice)
                            }
                            if (detail.resolvedProfileIssueCount > 0) {
                                ResolvedCountText("${detail.resolvedProfileIssueCount} resolved")
                            }
                        },
                    ) {
                        ProfileContent(
                            detail = detail,
                            onOpenIssues = { onOpenNotices(detail.id) },
                            onResolveIssue = { issue -> resolveTarget = issue },
                            onUnresolveIssue = onUnresolveIssue,
                        )
                    }
                }
                item {
                    FoldableSection(
                        title = "Answers",
                        expanded = sectionExpanded["Answers"] == true,
                        onToggle = { sectionExpanded["Answers"] = sectionExpanded["Answers"] != true },
                        headerContent = {
                            if (detail.answerWarningCount > 0) {
                                CountChip("${detail.answerWarningCount} warnings", DelegateStatusTone.Yellow)
                            }
                            if (detail.answerNoticeCount > 0) {
                                CountChip("${detail.answerNoticeCount} notices", DelegateStatusTone.Notice)
                            }
                            if (detail.resolvedAnswerIssueCount > 0) {
                                ResolvedCountText("${detail.resolvedAnswerIssueCount} resolved")
                            }
                        },
                    ) {
                        AnswersContent(
                            detail = detail,
                            onOpenIssues = { onOpenNotices(detail.id) },
                            onResolveIssue = { issue -> resolveTarget = issue },
                            onUnresolveIssue = onUnresolveIssue,
                        )
                    }
                }
                item {
                    FoldableSection(
                        title = "Preferences",
                        expanded = sectionExpanded["Preferences"] == true,
                        onToggle = { sectionExpanded["Preferences"] = sectionExpanded["Preferences"] != true },
                    ) {
                        PreferencesContent(detail.preferences, onNavigateToPosition)
                    }
                }
                item {
                    FoldableSection(
                        title = "Comments",
                        expanded = sectionExpanded["Comments"] == true,
                        onToggle = { sectionExpanded["Comments"] = sectionExpanded["Comments"] != true },
                    ) {
                        CommentsContent(
                            detail = detail,
                            onAddComment = onAddComment,
                            onUpdateComment = onUpdateComment,
                            onDeleteComment = onDeleteComment,
                            onOpenIssues = { onOpenNotices(detail.id) },
                        )
                    }
                }
            }
            HorizontalDivider()
            ActionFooter(
                detail = detail,
                loading = loading,
                onOpenDeactivateDialog = { deactivateDialogOpen = true },
                onReactivate = onReactivate,
            )
        }
    }

    if (deactivateDialogOpen) {
        AlertDialog(
            onDismissRequest = { deactivateDialogOpen = false },
            title = { Text("Deactivate ${detail.name}?") },
            text = {
                Text("This prevents usage of this delegate for assignment and management, and will remove any pending assignments. Use after deleting a delegate online. To undo deactivation, find the delegate in the All view in the delegates list.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeactivate(detail.id)
                        deactivateDialogOpen = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Deactivate")
                }
            },
            dismissButton = {
                TextButton(onClick = { deactivateDialogOpen = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    resolveTarget?.let { target ->
        ResolveIssueDialog(
            issue = target,
            onDismiss = { resolveTarget = null },
            onConfirm = { justification ->
                onResolveIssue(detail.id, target.questionId, target.issueKey, justification)
                resolveTarget = null
            },
        )
    }
}

@Composable
private fun Header(
    detail: DelegateDetailUiState,
    loading: Boolean,
    onSetPaymentTier: (PaymentTier) -> Unit,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    detail.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (detail.active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    textDecoration = if (detail.active) TextDecoration.None else TextDecoration.LineThrough,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                detail.delegation?.let { delegation ->
                    BoardTooltip(delegation.tooltip) {
                        Row(
                            modifier = Modifier.clickable { }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(
                                delegation.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Close delegate panel")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusChip(detail.paymentStatus)
            detail.paymentTierStatus?.let { status ->
                PaymentTierSelector(
                    status = status,
                    currentTier = detail.paymentTier,
                    enabled = !loading,
                    onSetPaymentTier = onSetPaymentTier,
                )
            }
            detail.applicationStatus?.let { StatusChip(it) }
            detail.inactiveMarker?.let { StatusChip(it) }
            if (detail.highSchooler) {
                BoardTooltip("High-school status from the delegate's application answers.") {
                    Icon(
                        Icons.Filled.School,
                        contentDescription = "High-school delegate",
                        tint = Color(0xFF8E24AA),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            if (detail.isHeadDelegate) {
                StatusChip(
                    DelegateStatusChipUiState(
                        label = "Head delegate",
                        tone = DelegateStatusTone.Neutral,
                        tooltip = "This delegate is marked as a head delegate online.",
                    ),
                )
            }
        }
    }
}

@Composable
private fun PaymentTierSelector(
    status: DelegateStatusChipUiState,
    currentTier: PaymentTier?,
    enabled: Boolean,
    onSetPaymentTier: (PaymentTier) -> Unit,
) {
    var expanded by remember(currentTier) { mutableStateOf(false) }
    Box {
        BoardTooltip(status.tooltip) {
            TextButton(onClick = { expanded = true }, enabled = enabled) {
                Text(status.label, fontWeight = FontWeight.SemiBold)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PaymentTier.entries.forEach { tier ->
                DropdownMenuItem(
                    text = { Text(if (tier == PaymentTier.Full) "Full tier" else "Reduced tier") },
                    onClick = {
                        expanded = false
                        if (tier != currentTier) onSetPaymentTier(tier)
                    },
                )
            }
        }
    }
}

@Composable
private fun AssignmentCard(
    detail: DelegateDetailUiState,
    onNavigateToPosition: (committeeId: String, positionId: String) -> Unit,
) {
    Card(
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Assignment", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val assignment = detail.assignment
            if (assignment == null) {
                Text("Unassigned", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            } else {
                ClickableLine(
                    primary = assignment.label,
                    flagAssetName = assignment.flagAssetName,
                    secondary = assignment.status,
                    tone = assignment.tone,
                    onClick = { onNavigateToPosition(assignment.committeeId, assignment.positionId) },
                )
            }
        }
    }
}

@Composable
private fun ReservationCard(
    detail: DelegateDetailUiState,
    loading: Boolean,
    options: List<ReservationSummaryUiState>,
    actionError: String?,
    onAssignReservation: (String) -> Unit,
    onRemoveReservation: () -> Unit,
) {
    var selectionDialogOpen by remember(detail.id) { mutableStateOf(false) }
    val reservation = detail.reservation
    Card(
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Reservation", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (reservation == null) {
                Text("No reservation", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                BoardTooltip("Assign this delegate to an existing reservation.") {
                    OutlinedButton(
                        onClick = { selectionDialogOpen = true },
                        enabled = !loading,
                    ) {
                        Text("Assign")
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(reservation.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(reservation.sourceLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    BoardTooltip("Choose a different reservation for this delegate.") {
                        OutlinedButton(
                            onClick = { selectionDialogOpen = true },
                            enabled = !loading,
                        ) {
                            Text("Change")
                        }
                    }
                    if (reservation.sourceLabel == ManualReservationSourceLabel) {
                        BoardTooltip("Remove this delegate from the reservation.") {
                            TextButton(
                                onClick = onRemoveReservation,
                                enabled = !loading,
                            ) {
                                Text("Remove")
                            }
                        }
                    }
                }
            }
            actionError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (selectionDialogOpen) {
        DelegateReservationSelectionDialog(
            options = options,
            delegateIsHighSchooler = detail.highSchooler,
            currentReservationId = reservation?.id,
            actionError = actionError,
            onDismiss = { selectionDialogOpen = false },
            onSelect = { reservationId ->
                onAssignReservation(reservationId)
                selectionDialogOpen = false
            },
        )
    }
}

@Composable
private fun DelegateReservationSelectionDialog(
    options: List<ReservationSummaryUiState>,
    delegateIsHighSchooler: Boolean,
    currentReservationId: String?,
    actionError: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val sortedOptions = options.sortedWith(
        compareBy<ReservationSummaryUiState> {
            it.disabledReasonFor(delegateIsHighSchooler, currentReservationId) != null
        }.thenBy { it.name },
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select reservation") },
        text = {
            Column(modifier = Modifier.width(520.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                actionError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                if (options.isEmpty()) {
                    Text("No reservations", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    ) {
                        items(sortedOptions, key = { it.id }) { option ->
                            val disabledReason = option.disabledReasonFor(delegateIsHighSchooler, currentReservationId)
                            DelegateReservationOptionRow(
                                option = option,
                                disabledReason = disabledReason,
                                onSelect = { onSelect(option.id) },
                            )
                        }
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
}

@Composable
private fun DelegateReservationOptionRow(
    option: ReservationSummaryUiState,
    disabledReason: String?,
    onSelect: () -> Unit,
) {
    BoardTooltip(disabledReason ?: "Assign delegate to ${option.name}.") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .clickable(enabled = disabledReason == null, onClick = onSelect)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(option.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    option.delegationName ?: "Unlinked reservation",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "HS ${option.remainingHighSchoolCount}/${option.highSchoolCount} · Uni ${option.remainingUniversityCount}/${option.universityCount}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (disabledReason == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            disabledReason?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun ReservationSummaryUiState.disabledReasonFor(delegateIsHighSchooler: Boolean, currentReservationId: String?): String? =
    when {
        id == currentReservationId -> "Already assigned"
        delegateIsHighSchooler && remainingHighSchoolCount <= 0 -> "No HS capacity"
        !delegateIsHighSchooler && remainingUniversityCount <= 0 -> "No Uni capacity"
        else -> null
    }

@Composable
private fun ProfileContent(
    detail: DelegateDetailUiState,
    onOpenIssues: () -> Unit,
    onResolveIssue: (DelegateAnswerIssueUiState) -> Unit,
    onUnresolveIssue: (String) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    if (detail.email == null &&
        detail.phone == null &&
        detail.address == null &&
        detail.nationality == null &&
        detail.age == null &&
        detail.diet == null &&
        detail.experience == null &&
        detail.language == null
    ) {
        if (detail.profileIssues.isEmpty() && detail.resolvedProfileIssues.isEmpty()) {
            Text("No profile details available", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        detail.age?.let {
            BoardTooltip(it.dateOfBirthTooltip) {
                MetricBlock("Age", it.label, Modifier.weight(1f))
            }
        }
        detail.nationality?.let {
            BoardTooltip("Nationality from the application.") {
                MetricBlock("Nationality", it, Modifier.weight(1f))
            }
        }
        detail.diet?.let {
            BoardTooltip("Dietary information from the application.") {
                MetricBlock("Diet", it, Modifier.weight(1f))
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        detail.experience?.let { experience ->
            BoardTooltip(experience.tooltip) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Experience", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(experience.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        repeat(experience.starCount) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }
            }
        }
        detail.language?.let { language ->
            BoardTooltip(language.tooltip) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Language", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    StatusChip(
                        DelegateStatusChipUiState(
                            label = language.label,
                            tone = language.tone,
                            tooltip = language.tooltip,
                        ),
                    )
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        detail.email?.let {
            BoardTooltip("Email address from the application.") {
                EmailBlock(
                    email = it,
                    modifier = Modifier.weight(1f),
                    onClick = { uriHandler.openUri(URI("mailto", it, null).toString()) },
                )
            }
        }
        detail.phone?.let {
            BoardTooltip("Phone number from the application.") {
                MetricBlock("Phone", it, Modifier.weight(1f))
            }
        }
    }
    detail.address?.let {
        BoardTooltip("Address from the application profile.") {
            MetricBlock("Address", it, Modifier.fillMaxWidth())
        }
    }
    ValidationIssueBlock(
        title = "Checks",
        issues = detail.profileIssues,
        resolvedIssues = detail.resolvedProfileIssues,
        onOpenIssues = onOpenIssues,
        onResolveIssue = onResolveIssue,
        onUnresolveIssue = onUnresolveIssue,
    )
}

@Composable
private fun PreferencesContent(
    preferences: List<DelegatePreferenceUiState>,
    onNavigateToPosition: (committeeId: String, positionId: String) -> Unit,
) {
    if (preferences.isEmpty()) {
        Text("No preferences", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    preferences.forEach { preference ->
        PreferenceRow(preference, onNavigateToPosition)
    }
}

@Composable
private fun PreferenceRow(
    preference: DelegatePreferenceUiState,
    onNavigateToPosition: (committeeId: String, positionId: String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(preference.status.tone.containerColor().copy(alpha = 0.26f), RoundedCornerShape(8.dp))
            .border(1.dp, preference.status.tone.containerColor().copy(alpha = 0.52f), RoundedCornerShape(8.dp))
            .clickable { onNavigateToPosition(preference.committeeId, preference.positionId) }
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "#${preference.rank}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = preference.status.tone.contentColor(),
            modifier = Modifier
                .background(preference.status.tone.containerColor(), RoundedCornerShape(999.dp))
                .padding(horizontal = 7.dp, vertical = 3.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FlagAwareText(
                text = preference.label,
                flagAssetName = preference.flagAssetName,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            preference.status.detail?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        BoardTooltip(preference.status.tooltip) {
            StatusChip(
                DelegateStatusChipUiState(
                    label = preference.status.label,
                    tone = preference.status.tone,
                    tooltip = preference.status.tooltip,
                ),
            )
        }
    }
}

@Composable
private fun AnswersContent(
    detail: DelegateDetailUiState,
    onOpenIssues: () -> Unit,
    onResolveIssue: (DelegateAnswerIssueUiState) -> Unit,
    onUnresolveIssue: (String) -> Unit,
) {
    if (detail.answers.isEmpty()) {
        Text("No configured answers", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    detail.answers.forEach { answer ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f), RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .padding(9.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                answer.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                answer.answer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            answer.issues.forEach { issue ->
                AnswerIssuePill(
                    issue = issue,
                    onOpenIssues = onOpenIssues,
                    onResolveIssue = { onResolveIssue(issue) },
                )
            }
            answer.resolvedIssues.forEach { issue -> ResolvedAnswerIssueLine(issue, onOpenIssues, onUnresolveIssue) }
        }
    }
}

@Composable
private fun ValidationIssueBlock(
    title: String,
    issues: List<DelegateAnswerIssueUiState>,
    resolvedIssues: List<DelegateResolvedIssueUiState>,
    onOpenIssues: () -> Unit,
    onResolveIssue: (DelegateAnswerIssueUiState) -> Unit,
    onUnresolveIssue: (String) -> Unit,
) {
    if (issues.isEmpty() && resolvedIssues.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        issues.forEach { issue ->
            AnswerIssuePill(
                issue = issue,
                onOpenIssues = onOpenIssues,
                onResolveIssue = { onResolveIssue(issue) },
            )
        }
        resolvedIssues.forEach { issue -> ResolvedAnswerIssueLine(issue, onOpenIssues, onUnresolveIssue) }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun ResolvedAnswerIssueLine(
    issue: DelegateResolvedIssueUiState,
    onOpenIssues: () -> Unit,
    onUnresolveIssue: (String) -> Unit,
) {
    var menuOpen by remember(issue.resolutionId) { mutableStateOf(false) }
    BoardTooltip(issue.justification?.let { "Issue resolved, justification: $it" } ?: "Issue resolved.") {
        Box {
            Text(
                issue.message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = TextDecoration.LineThrough,
                modifier = Modifier
                    .pointerInput(issue.resolutionId) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Release && event.button == PointerButton.Secondary) {
                                    event.changes.forEach { it.consume() }
                                    menuOpen = true
                                }
                            }
                        }
                    }
                    .clickable(onClick = onOpenIssues),
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Reopen") },
                    onClick = {
                        menuOpen = false
                        onUnresolveIssue(issue.resolutionId)
                    },
                    leadingIcon = { Icon(Icons.Filled.Restore, contentDescription = null) },
                )
            }
        }
    }
}

@Composable
private fun ResolveIssueDialog(
    issue: DelegateAnswerIssueUiState,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var justification by remember(issue.issueKey) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Resolve issue") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(issue.message)
                OutlinedTextField(
                    value = justification,
                    onValueChange = { justification = it },
                    label = { Text("Justification") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(justification.takeIf { it.isNotBlank() }) }) {
                Text("Resolve")
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
private fun CommentsContent(
    detail: DelegateDetailUiState,
    onAddComment: (delegateId: String, body: String) -> Unit,
    onUpdateComment: (commentId: String, body: String) -> Unit,
    onDeleteComment: (commentId: String) -> Unit,
    onOpenIssues: () -> Unit,
) {
    var draft by remember(detail.id) { mutableStateOf("") }
    if (detail.comments.isEmpty()) {
        Text("No notes yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        detail.comments.forEach { comment ->
            CommentRow(
                comment = comment,
                onUpdateComment = onUpdateComment,
                onDeleteComment = onDeleteComment,
                onOpenIssues = onOpenIssues,
            )
        }
    }
    BoardTooltip("Type a new organizer note and press Enter to add it.") {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text("New note") },
            modifier = Modifier.fillMaxWidth().onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && event.key == Key.Enter && draft.isNotBlank()) {
                    onAddComment(detail.id, draft)
                    draft = ""
                    true
                } else {
                    false
                }
            },
            singleLine = true,
        )
    }
}

@Composable
private fun ActionFooter(
    detail: DelegateDetailUiState,
    loading: Boolean,
    onOpenDeactivateDialog: () -> Unit,
    onReactivate: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
    ) {
        if (detail.active) {
            BoardTooltip(
                if (detail.canDeactivate) {
                    "Deactivate this delegate and remove any pending assignments."
                } else {
                    "Delegates with confirmed assignments cannot be deactivated."
                },
            ) {
                OutlinedButton(
                    onClick = onOpenDeactivateDialog,
                    enabled = !loading && detail.canDeactivate,
                ) {
                    Icon(Icons.Filled.PersonOff, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Deactivate")
                }
            }
        } else {
            BoardTooltip("Reactivate this delegate for assignment and management.") {
                Button(onClick = { onReactivate(detail.id) }, enabled = !loading) {
                    Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Reactivate")
                }
            }
        }
    }
}

@Composable
private fun FoldableSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    headerContent: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            headerContent()
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse $title" else "Expand $title",
            )
        }
        if (expanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun ResolvedCountText(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CountChip(label: String, tone: DelegateStatusTone) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = tone.contentColor(),
        modifier = Modifier.background(tone.containerColor(), RoundedCornerShape(999.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
private fun StatusChip(chip: DelegateStatusChipUiState) {
    BoardTooltip(chip.tooltip) {
        Text(
            chip.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = chip.tone.contentColor(),
            modifier = Modifier.background(chip.tone.containerColor(), RoundedCornerShape(999.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun MetricBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EmailBlock(email: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Email", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            email,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable(onClick = onClick),
        )
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ClickableLine(
    primary: String,
    flagAssetName: String?,
    secondary: String?,
    tone: DelegateStatusTone = DelegateStatusTone.Blue,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tone.containerColor().copy(alpha = 0.34f), RoundedCornerShape(8.dp))
            .border(1.dp, tone.containerColor().copy(alpha = 0.70f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        FlagAwareText(
            text = primary,
            flagAssetName = flagAssetName,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        secondary?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = tone.contentColor())
        }
    }
}

@Composable
private fun CommentRow(
    comment: DelegateCommentUiState,
    onUpdateComment: (commentId: String, body: String) -> Unit,
    onDeleteComment: (commentId: String) -> Unit,
    onOpenIssues: () -> Unit,
) {
    var editing by remember(comment.id) { mutableStateOf(false) }
    var editBody by remember(comment.id, comment.body) { mutableStateOf(comment.body) }
    var deleteDialogOpen by remember(comment.id) { mutableStateOf(false) }

    Card(
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (editing) {
                OutlinedTextField(value = editBody, onValueChange = { editBody = it }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onUpdateComment(comment.id, editBody)
                            editing = false
                        },
                        enabled = editBody.isNotBlank(),
                    ) {
                        Text("Save")
                    }
                    TextButton(onClick = { editing = false }) {
                        Text("Cancel")
                    }
                }
            } else {
                Text(comment.body)
                Text(
                    if (comment.readOnly) {
                        "Resolution justification ${comment.updatedAt}"
                    } else {
                        "Updated ${comment.updatedAt}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!comment.readOnly) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        BoardTooltip("Edit this organizer note.") {
                            IconButton(onClick = { editing = true }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit note", modifier = Modifier.size(16.dp))
                            }
                        }
                        BoardTooltip("Delete this organizer note.") {
                            IconButton(onClick = { deleteDialogOpen = true }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete note", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        BoardTooltip("Open this resolved issue.") {
                            IconButton(onClick = onOpenIssues, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Open resolved issue", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (deleteDialogOpen) {
        AlertDialog(
            onDismissRequest = { deleteDialogOpen = false },
            title = { Text("Delete note?") },
            text = { Text("This removes the organizer note from this delegate.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteComment(comment.id)
                        deleteDialogOpen = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialogOpen = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun AnswerIssuePill(
    issue: DelegateAnswerIssueUiState,
    onOpenIssues: () -> Unit,
    onResolveIssue: () -> Unit,
) {
    val tone = if (issue.isWarning) DelegateStatusTone.Yellow else DelegateStatusTone.Notice
    var menuOpen by remember(issue.issueKey) { mutableStateOf(false) }
    Box {
        Text(
            "${issue.severity}: ${issue.message}",
            style = MaterialTheme.typography.labelSmall,
            color = tone.contentColor(),
            modifier = Modifier
                .background(tone.containerColor(), RoundedCornerShape(999.dp))
                .pointerInput(issue.issueKey) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Release && event.button == PointerButton.Secondary) {
                                event.changes.forEach { it.consume() }
                                menuOpen = true
                            }
                        }
                    }
                }
                .clickable(onClick = onOpenIssues)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Resolve") },
                onClick = {
                    menuOpen = false
                    onResolveIssue()
                },
                leadingIcon = { Icon(Icons.Filled.Check, contentDescription = null) },
            )
        }
    }
}
