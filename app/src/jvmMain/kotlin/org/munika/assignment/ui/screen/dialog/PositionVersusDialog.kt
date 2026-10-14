package org.munika.assignment.ui.screen.dialog

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.munika.assignment.ui.screen.component.FlagAwareText
import org.munika.assignment.ui.viewmodel.PositionVersusCandidateUiState
import org.munika.assignment.ui.viewmodel.PositionVersusUiState

@Composable
fun PositionVersusDialog(
    versus: PositionVersusUiState,
    onDismiss: () -> Unit,
    onAssignSelected: (List<String>) -> Unit,
) {
    var selectedIds by remember(versus.positionId, versus.candidates) {
        mutableStateOf(versus.candidates.filter { it.pending }.mapTo(linkedSetOf()) { it.id }.toSet())
    }
    val horizontalScroll = rememberScrollState()
    val verticalScroll = rememberScrollState()
    val selectionComplete = selectedIds.size == versus.requiredSelectionCount

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.88f).widthIn(max = 1_500.dp),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                FlagAwareText(
                    text = "Delegate comparison for ${versus.positionName}",
                    flagAssetName = versus.flagAssetName,
                    style = MaterialTheme.typography.headlineSmall,
                )
                HorizontalDivider()
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val candidateColumnWidth = candidateColumnWidth(
                        availableWidth = maxWidth,
                        candidateCount = versus.candidates.size,
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(verticalScroll)
                            .horizontalScroll(horizontalScroll),
                    ) {
                        VersusHeader(versus, candidateColumnWidth)
                        versus.rows.forEach { row ->
                            VersusTableRow(
                                label = row.label,
                                values = versus.candidates.map { candidate -> row.valuesByDelegateId.getValue(candidate.id) },
                                candidateColumnWidth = candidateColumnWidth,
                            )
                        }
                        SelectionRow(
                            candidates = versus.candidates,
                            selectedIds = selectedIds,
                            requiredSelectionCount = versus.requiredSelectionCount,
                            candidateColumnWidth = candidateColumnWidth,
                            onToggle = { candidate ->
                                selectedIds = if (candidate.id in selectedIds) {
                                    selectedIds - candidate.id
                                } else {
                                    selectedIds + candidate.id
                                }
                            },
                        )
                    }
                    HorizontalScrollbar(
                        adapter = androidx.compose.foundation.rememberScrollbarAdapter(horizontalScroll),
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${selectedIds.size} of ${versus.requiredSelectionCount} selected",
                        color = if (selectionComplete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Button(
                            enabled = selectionComplete,
                            onClick = {
                                onAssignSelected(
                                    versus.candidates.map { it.id }.filter { it in selectedIds },
                                )
                            },
                        ) {
                            Text("Assign selected")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VersusHeader(versus: PositionVersusUiState, candidateColumnWidth: Dp) {
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        VersusCell(text = "", width = LabelColumnWidth, emphasized = true)
        versus.candidates.forEach { candidate ->
            VersusCell(
                text = if (candidate.pending) "${candidate.name}\nPending occupant" else candidate.name,
                width = candidateColumnWidth,
                emphasized = true,
            )
        }
    }
}

@Composable
private fun VersusTableRow(label: String, values: List<String>, candidateColumnWidth: Dp) {
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        VersusCell(text = label, width = LabelColumnWidth, emphasized = true)
        values.forEach { value -> VersusCell(text = value, width = candidateColumnWidth) }
    }
}

@Composable
private fun SelectionRow(
    candidates: List<PositionVersusCandidateUiState>,
    selectedIds: Set<String>,
    requiredSelectionCount: Int,
    candidateColumnWidth: Dp,
    onToggle: (PositionVersusCandidateUiState) -> Unit,
) {
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        VersusCell(text = "Selection", width = LabelColumnWidth, emphasized = true)
        candidates.forEach { candidate ->
            val selected = candidate.id in selectedIds
            val enabled = selected || selectedIds.size < requiredSelectionCount
            Box(
                modifier = Modifier
                    .width(candidateColumnWidth)
                    .fillMaxHeight()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    .padding(10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Checkbox(
                    checked = selected,
                    enabled = enabled,
                    onCheckedChange = { onToggle(candidate) },
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
private fun VersusCell(text: String, width: Dp, emphasized: Boolean = false) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(10.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Text(
            text = text,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

private val LabelColumnWidth = 190.dp
private val MinimumDelegateColumnWidth = 280.dp

private fun candidateColumnWidth(availableWidth: Dp, candidateCount: Int): Dp {
    if (candidateCount == 0) return MinimumDelegateColumnWidth
    return ((availableWidth - LabelColumnWidth) / candidateCount).coerceAtLeast(MinimumDelegateColumnWidth)
}
