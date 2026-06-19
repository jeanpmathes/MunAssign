@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.munika.assignment.ui.screen.dialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
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
import org.munika.assignment.ui.viewmodel.IssueManagerUiState
import org.munika.assignment.ui.viewmodel.IssueUiState

@Composable
fun IssueManagerPanel(
    manager: IssueManagerUiState,
    onClose: () -> Unit,
    onResolve: (delegateId: String, questionId: String?, issueKey: String, justification: String?) -> Unit,
    onUpdateJustification: (resolutionId: String, justification: String?) -> Unit,
    onUnresolve: (resolutionId: String) -> Unit,
    onOpenDelegate: (delegateId: String) -> Unit,
    onClearFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var resolveTarget by remember { mutableStateOf<IssueUiState?>(null) }
    var editTarget by remember { mutableStateOf<IssueUiState?>(null) }
    Surface(
        modifier = modifier.width(460.dp).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            IssueManagerHeader(onClose)
            HorizontalDivider()
            IssueManagerSummary(manager, onClearFilter)
            HorizontalDivider()
            LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                if (manager.issues.isEmpty()) {
                    item {
                        Text(
                            "No issues",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                        )
                    }
                }
                itemsIndexed(
                    items = manager.issues,
                    key = { _, issue -> "${issue.delegateId}:${issue.questionId}:${issue.issueKey}:${issue.resolved}" },
                ) { index, issue ->
                    IssueRow(
                        issue = issue,
                        onResolve = { resolveTarget = issue },
                        onEdit = { editTarget = issue },
                        onUnresolve = { issue.resolutionId?.let(onUnresolve) },
                        onOpenDelegate = { onOpenDelegate(issue.delegateId) },
                    )
                    if (index < manager.issues.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
                    }
                }
            }
        }
    }

    resolveTarget?.let { target ->
        ResolveIssueDialog(
            issue = target,
            initialJustification = "",
            title = "Resolve issue",
            confirmLabel = "Resolve",
            onDismiss = { resolveTarget = null },
            onConfirm = { justification ->
                onResolve(target.delegateId, target.questionId, target.issueKey, justification)
                resolveTarget = null
            },
        )
    }

    editTarget?.let { target ->
        target.resolutionId?.let { resolutionId ->
            ResolveIssueDialog(
                issue = target,
                initialJustification = target.justificationText.orEmpty(),
                title = "Edit justification",
                confirmLabel = "Save",
                onDismiss = { editTarget = null },
                onConfirm = { justification ->
                    onUpdateJustification(resolutionId, justification)
                    editTarget = null
                },
            )
        }
    }
}

@Composable
private fun IssueManagerHeader(onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text("Issue manager", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        TooltipIconButton("Close issue manager.") {
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Close issue manager")
            }
        }
    }
}

@Composable
private fun IssueManagerSummary(manager: IssueManagerUiState, onClearFilter: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SeverityText("${manager.openWarningCount} warnings", isWarning = true)
            SeverityText("${manager.openNoticeCount} notices", isWarning = false)
        }
        manager.filteredDelegateName?.let { delegateName ->
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Showing issues for $delegateName",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = onClearFilter) {
                    Text("Show all")
                }
            }
        }
    }
}

@Composable
private fun IssueRow(
    issue: IssueUiState,
    onResolve: () -> Unit,
    onEdit: () -> Unit,
    onUnresolve: () -> Unit,
    onOpenDelegate: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .then(
                if (issue.resolved) {
                    Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SeverityText(issue.severity, issue.isWarning, resolved = issue.resolved)
                Text(issue.validationLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    issue.delegateName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(onClick = onOpenDelegate),
                )
                if (issue.delegateIsHighSchooler) {
                    TooltipIcon("High school delegate.") {
                        Icon(Icons.Filled.School, contentDescription = "High school delegate", modifier = Modifier.size(16.dp), tint = HighSchoolColor)
                    }
                }
            }
            issue.questionText?.let { question ->
                LabeledIssueText(label = "Q", tooltip = "Question", text = question)
            }
            issue.answerText?.let { answer ->
                LabeledIssueText(label = "A", tooltip = "Answer", text = answer)
            }
            LabeledIssueText(label = "R", tooltip = "Reason", text = issue.reasonText)
            issue.justificationText?.let { justification ->
                LabeledIssueText(label = "J", tooltip = "Justification", text = justification)
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            TooltipIconButton("Open delegate profile.") {
                IconButton(onClick = onOpenDelegate, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Open delegate profile", modifier = Modifier.size(17.dp))
                }
            }
            if (issue.resolved) {
                TooltipIconButton("Edit justification.") {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp), enabled = issue.resolutionId != null) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit justification", modifier = Modifier.size(17.dp))
                    }
                }
                TooltipIconButton("Reopen issue.") {
                    IconButton(onClick = onUnresolve, modifier = Modifier.size(32.dp), enabled = issue.resolutionId != null) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Reopen issue", modifier = Modifier.size(17.dp))
                    }
                }
            } else {
                TooltipIconButton("Resolve issue.") {
                    IconButton(onClick = onResolve, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Check, contentDescription = "Resolve issue", modifier = Modifier.size(17.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SeverityText(text: String, isWarning: Boolean, resolved: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (resolved) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else if (isWarning) {
            WarningTextColor
        } else {
            NoticeTextColor
        },
        textDecoration = if (resolved) TextDecoration.LineThrough else TextDecoration.None,
    )
}

@Composable
private fun LabeledIssueText(label: String, tooltip: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
        TooltipIcon(tooltip) {
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        Text(text, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TooltipIcon(tooltip: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState(),
    ) {
        content()
    }
}

@Composable
private fun TooltipIconButton(tooltip: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState(),
    ) {
        content()
    }
}

@Composable
private fun ResolveIssueDialog(
    issue: IssueUiState,
    initialJustification: String,
    title: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var justification by remember(issue.issueKey, issue.resolutionId) { mutableStateOf(initialJustification) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(issue.reasonText)
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
            Button(onClick = { onConfirm(justification) }) {
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

private val WarningTextColor = Color(0xFF8A4A00)
private val NoticeTextColor = Color(0xFF0B4F66)
private val HighSchoolColor = Color(0xFF8E24AA)
