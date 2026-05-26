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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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
import org.munika.assignment.ui.viewmodel.DelegateStatusChipUiState
import org.munika.assignment.ui.viewmodel.DelegateStatusTone

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
    modifier: Modifier = Modifier,
) {
    var deactivateDialogOpen by remember(detail.id) { mutableStateOf(false) }
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
                    FoldableSection(
                        title = "Profile",
                        expanded = sectionExpanded["Profile"] == true,
                        onToggle = { sectionExpanded["Profile"] = sectionExpanded["Profile"] != true },
                    ) {
                        ProfileContent(detail)
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
                                CountChip("${detail.answerNoticeCount} notices", DelegateStatusTone.Neutral)
                            }
                        },
                    ) {
                        AnswersContent(detail)
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
                Text("This hides the delegate from assignment views. If they have a pending MunAssign assignment, it will be cleared.")
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
}

@Composable
private fun Header(detail: DelegateDetailUiState, onClose: () -> Unit) {
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
            detail.applicationStatus?.let { StatusChip(it) }
            detail.inactiveMarker?.let { StatusChip(it) }
            if (detail.highSchooler) {
                BoardTooltip("Typed high schooler status from the configured high-schooler question.") {
                    Icon(
                        Icons.Filled.School,
                        contentDescription = "High school delegate",
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
                        tooltip = "This delegate is marked as a head delegate in MyMUN.",
                    ),
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
private fun ProfileContent(detail: DelegateDetailUiState) {
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
        Text("No imported profile details", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        detail.age?.let {
            BoardTooltip(it.dateOfBirthTooltip) {
                MetricBlock("Age", it.label, Modifier.weight(1f))
            }
        }
        detail.nationality?.let {
            BoardTooltip("Nationality from MyMUN.") {
                MetricBlock("Nationality", it, Modifier.weight(1f))
            }
        }
        detail.diet?.let {
            BoardTooltip("Dietary information from MyMUN.") {
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
            BoardTooltip("Email address from MyMUN.") {
                EmailBlock(
                    email = it,
                    modifier = Modifier.weight(1f),
                    onClick = { uriHandler.openUri(URI("mailto", it, null).toString()) },
                )
            }
        }
        detail.phone?.let {
            BoardTooltip("Phone number from MyMUN.") {
                MetricBlock("Phone", it, Modifier.weight(1f))
            }
        }
    }
    detail.address?.let {
        BoardTooltip("Address combined from imported country, zip code, city, and street fields.") {
            MetricBlock("Address", it, Modifier.fillMaxWidth())
        }
    }
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
private fun AnswersContent(detail: DelegateDetailUiState) {
    if (detail.answers.isEmpty() && detail.profileIssues.isEmpty()) {
        Text("No configured answers", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    if (detail.profileIssues.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f), RoundedCornerShape(8.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                .padding(9.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                "Profile checks",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            detail.profileIssues.forEach { issue -> AnswerIssuePill(issue) }
        }
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
            answer.issues.forEach { issue -> AnswerIssuePill(issue) }
        }
    }
}

@Composable
private fun CommentsContent(
    detail: DelegateDetailUiState,
    onAddComment: (delegateId: String, body: String) -> Unit,
    onUpdateComment: (commentId: String, body: String) -> Unit,
    onDeleteComment: (commentId: String) -> Unit,
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
                    "Deactivate this delegate and clear any pending assignment."
                } else {
                    "Confirmed MyMUN-assigned delegates cannot be deactivated."
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
            BoardTooltip("Reactivate this delegate for assignment workflows.") {
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
                Text("Updated ${comment.updatedAt}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun AnswerIssuePill(issue: DelegateAnswerIssueUiState) {
    val tone = if (issue.isWarning) DelegateStatusTone.Yellow else DelegateStatusTone.Neutral
    Text(
        "${issue.severity}: ${issue.message}",
        style = MaterialTheme.typography.labelSmall,
        color = tone.contentColor(),
        modifier = Modifier.background(tone.containerColor(), RoundedCornerShape(999.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
