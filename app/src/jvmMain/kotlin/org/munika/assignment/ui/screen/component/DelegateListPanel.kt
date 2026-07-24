@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package org.munika.assignment.ui.screen.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.munika.assignment.ui.viewmodel.DelegateListAssignmentStatus
import org.munika.assignment.ui.viewmodel.DelegateListControlsUiState
import org.munika.assignment.ui.viewmodel.DelegateListPreset
import org.munika.assignment.ui.viewmodel.DelegateListRowUiState
import org.munika.assignment.ui.viewmodel.DelegateListSchoolLevel
import org.munika.assignment.ui.viewmodel.DelegateListPaymentTier
import org.munika.assignment.ui.viewmodel.DelegateListSort
import org.munika.assignment.ui.viewmodel.DelegateListUiState
import org.munika.assignment.ui.viewmodel.DelegateStatusTone

@Composable
fun DelegateListPanel(
    list: DelegateListUiState,
    controls: DelegateListControlsUiState,
    onControlsChange: (DelegateListControlsUiState) -> Unit,
    onApplyPreset: (DelegateListPreset) -> Unit,
    onOpenDelegateDetail: (String) -> Unit,
    onNavigateToPosition: (committeeId: String, positionId: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(controls) {
        listState.scrollToItem(0)
    }

    Surface(
        modifier = modifier.width(780.dp).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            Header(onClose)
            HorizontalDivider()
            Controls(
                list = list,
                controls = controls,
                onControlsChange = onControlsChange,
                onApplyPreset = onApplyPreset,
            )
            HorizontalDivider()
            TableHeader()
            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                if (list.rows.isEmpty()) {
                    item {
                        Text(
                            "No delegates match the current filters",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                        )
                    }
                }
                items(list.rows, key = { it.id }) { row ->
                    DelegateRow(
                        row = row,
                        onOpenDelegateDetail = onOpenDelegateDetail,
                        onNavigateToPosition = onNavigateToPosition,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                }
            }
        }
    }
}

@Composable
private fun Header(onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.People, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text("Delegates", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        BoardTooltip("Close delegate list") {
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Close delegate list")
            }
        }
    }
}

@Composable
private fun Controls(
    list: DelegateListUiState,
    controls: DelegateListControlsUiState,
    onControlsChange: (DelegateListControlsUiState) -> Unit,
    onApplyPreset: (DelegateListPreset) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = controls.searchQuery,
                onValueChange = { onControlsChange(controls.copy(searchQuery = it)) },
                label = { Text("Search name") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            SortDropdown(controls, onControlsChange)
            FilterMenu(list, controls, onControlsChange)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PresetChip("Default", "Restore active paid delegates and submission-date sorting.") {
                onApplyPreset(DelegateListPreset.Default)
            }
            PresetChip("Assignment", "Show unassigned active paid delegates by difficulty.") {
                onApplyPreset(DelegateListPreset.Assignment)
            }
            PresetChip("Issues", "Show delegates with warnings or notices.") {
                onApplyPreset(DelegateListPreset.Issues)
            }
            PresetChip("Assigned", "Show confirmed and pending delegates, confirmed first.") {
                onApplyPreset(DelegateListPreset.Assigned)
            }
            PresetChip("All", "Include unpaid and deactivated delegates.") {
                onApplyPreset(DelegateListPreset.All)
            }
        }
    }
}

@Composable
private fun SortDropdown(
    controls: DelegateListControlsUiState,
    onControlsChange: (DelegateListControlsUiState) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = controls.sort.label,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Sort") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true).width(210.dp),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.exposedDropdownSize()) {
            DelegateListSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(sort.label) },
                    onClick = {
                        expanded = false
                        onControlsChange(controls.copy(sort = sort))
                    },
                )
            }
        }
    }
}

@Composable
private fun FilterMenu(
    list: DelegateListUiState,
    controls: DelegateListControlsUiState,
    onControlsChange: (DelegateListControlsUiState) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        BoardTooltip("Open delegate filters.") {
            IconButton(onClick = { expanded = true }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.FilterList, contentDescription = "Open delegate filters")
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CheckboxMenuItem(
                label = "Include unpaid",
                checked = controls.includeUnpaid,
                onCheckedChange = { onControlsChange(controls.copy(includeUnpaid = it)) },
            )
            CheckboxMenuItem(
                label = "Include deactivated",
                checked = controls.includeDeactivated,
                onCheckedChange = { onControlsChange(controls.copy(includeDeactivated = it)) },
            )
            CheckboxMenuItem(
                label = "Warnings/notices only",
                checked = controls.issuesOnly,
                onCheckedChange = { onControlsChange(controls.copy(issuesOnly = it)) },
            )
            HorizontalDivider()
            Text("Assignment", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            DelegateListAssignmentStatus.entries.forEach { status ->
                CheckboxMenuItem(
                    label = status.label,
                    checked = status in controls.includedAssignmentStatuses,
                    onCheckedChange = { checked ->
                        val statuses = if (checked) {
                            controls.includedAssignmentStatuses + status
                        } else {
                            controls.includedAssignmentStatuses - status
                        }
                        onControlsChange(controls.copy(includedAssignmentStatuses = statuses))
                    },
                )
            }
            HorizontalDivider()
            Text("School level", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            DelegateListSchoolLevel.entries.forEach { level ->
                CheckboxMenuItem(
                    label = level.label,
                    checked = level in controls.includedSchoolLevels,
                    onCheckedChange = { checked ->
                        val levels = if (checked) {
                            controls.includedSchoolLevels + level
                        } else {
                            controls.includedSchoolLevels - level
                        }
                        onControlsChange(controls.copy(includedSchoolLevels = levels))
                    },
                )
            }
            HorizontalDivider()
            Text("Payment tier", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            DelegateListPaymentTier.entries.forEach { tier ->
                CheckboxMenuItem(
                    label = tier.label,
                    checked = tier in controls.includedPaymentTiers,
                    onCheckedChange = { checked ->
                        val tiers = if (checked) controls.includedPaymentTiers + tier else controls.includedPaymentTiers - tier
                        onControlsChange(controls.copy(includedPaymentTiers = tiers))
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Any committee") },
                onClick = { onControlsChange(controls.copy(committeeId = null)) },
            )
            list.committees.forEach { committee ->
                DropdownMenuItem(
                    text = { Text(committee.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = { onControlsChange(controls.copy(committeeId = committee.id)) },
                )
            }
        }
    }
}

@Composable
private fun CheckboxMenuItem(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = { onCheckedChange(!checked) },
        leadingIcon = { Checkbox(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

@Composable
private fun PresetChip(label: String, tooltip: String, onClick: () -> Unit) {
    BoardTooltip(tooltip) {
        AssistChip(onClick = onClick, label = { Text(label) })
    }
}

@Composable
private fun TableHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HeaderCell("Name", Modifier.weight(1.35f))
        HeaderCell("Status", Modifier.weight(0.8f))
        HeaderCell("Pay/Active", Modifier.weight(0.8f))
        HeaderCell("Tier", Modifier.weight(0.72f))
        HeaderCell("Issues", Modifier.weight(0.84f))
        HeaderCell("Applied", Modifier.weight(0.78f))
        HeaderCell("Delegation", Modifier.weight(1.05f))
        Spacer(Modifier.width(40.dp))
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, modifier = modifier)
}

@Composable
private fun DelegateRow(
    row: DelegateListRowUiState,
    onOpenDelegateDetail: (String) -> Unit,
    onNavigateToPosition: (committeeId: String, positionId: String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onOpenDelegateDetail(row.id) }.padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.weight(1.35f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(row.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (row.isHighSchooler) {
                BoardTooltip("High-school delegate") {
                    Icon(
                        Icons.Filled.School,
                        contentDescription = "High-school delegate",
                        tint = Color(0xFF8E24AA),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        StatusPill(row.assignmentStatusLabel, row.assignmentStatus.tone, Modifier.weight(0.8f))
        StatusPill(row.paymentActiveLabel, row.paymentActiveTone, Modifier.weight(0.8f))
        val tierLabel = row.paymentTierLabel
        if (tierLabel == null) {
            Text("-", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.72f))
        } else {
            StatusPill(
                tierLabel,
                when (tierLabel) {
                    "Full" -> DelegateStatusTone.Blue
                    "Reduced" -> DelegateStatusTone.Notice
                    else -> DelegateStatusTone.Yellow
                },
                Modifier.weight(0.72f),
            )
        }
        IssueCell(row.warningCount, row.noticeCount, Modifier.weight(0.84f))
        Text(row.applicationAgeLabel, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.78f))
        Text(row.delegationName ?: "-", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1.05f))
        if (row.quickNavigation == null) {
            Spacer(Modifier.width(40.dp))
        } else {
            BoardTooltip(row.quickNavigation.tooltip) {
                IconButton(
                    onClick = {
                        onNavigateToPosition(row.quickNavigation.committeeId, row.quickNavigation.positionId)
                    },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = "Go to assignment")
                }
            }
        }
    }
}

@Composable
private fun StatusPill(label: String, tone: DelegateStatusTone, modifier: Modifier) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = tone.contentColor(),
        modifier = modifier.background(tone.containerColor(), RoundedCornerShape(999.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun IssueCell(warningCount: Int, noticeCount: Int, modifier: Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (warningCount == 0 && noticeCount == 0) {
            IssuePill("0", DelegateStatusTone.Neutral, "No warnings or notices.")
        } else {
            if (warningCount > 0) {
                IssuePill(warningCount.toString(), DelegateStatusTone.Yellow, "$warningCount warning${if (warningCount == 1) "" else "s"}.")
            }
            if (noticeCount > 0) {
                IssuePill(noticeCount.toString(), DelegateStatusTone.Notice, "$noticeCount notice${if (noticeCount == 1) "" else "s"}.")
            }
        }
    }
}

@Composable
private fun IssuePill(label: String, tone: DelegateStatusTone, tooltip: String) {
    BoardTooltip(tooltip) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = tone.contentColor(),
            maxLines = 1,
            modifier = Modifier
                .border(1.dp, tone.containerColor(), RoundedCornerShape(6.dp))
                .background(tone.containerColor().copy(alpha = 0.72f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

private val DelegateListSort.label: String
    get() = when (this) {
        DelegateListSort.ApplicationSubmitted -> "Submitted date"
        DelegateListSort.Name -> "Name"
        DelegateListSort.Difficulty -> "Difficulty"
        DelegateListSort.AssignmentStatus -> "Assignment status"
        DelegateListSort.Issues -> "Warnings/notices"
    }

private val DelegateListAssignmentStatus.label: String
    get() = when (this) {
        DelegateListAssignmentStatus.Unassigned -> "Unassigned"
        DelegateListAssignmentStatus.Pending -> "Pending"
        DelegateListAssignmentStatus.Confirmed -> "Confirmed"
    }

private val DelegateListSchoolLevel.label: String
    get() = when (this) {
        DelegateListSchoolLevel.HighSchool -> "High-school delegates"
        DelegateListSchoolLevel.University -> "University level"
    }

private val DelegateListPaymentTier.label: String
    get() = when (this) {
        DelegateListPaymentTier.Full -> "Full"
        DelegateListPaymentTier.Reduced -> "Reduced"
        DelegateListPaymentTier.Unassigned -> "Paid, unassigned tier"
    }

private val DelegateListAssignmentStatus.tone: DelegateStatusTone
    get() = when (this) {
        DelegateListAssignmentStatus.Unassigned -> DelegateStatusTone.Neutral
        DelegateListAssignmentStatus.Pending -> DelegateStatusTone.Yellow
        DelegateListAssignmentStatus.Confirmed -> DelegateStatusTone.Blue
    }
