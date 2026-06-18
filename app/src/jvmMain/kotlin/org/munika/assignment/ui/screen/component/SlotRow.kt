package org.munika.assignment.ui.screen.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.munika.assignment.ui.viewmodel.SlotBoardStatus
import org.munika.assignment.ui.viewmodel.SlotBoardUiState

@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun SlotRow(
    slot: SlotBoardUiState,
    onOpenContextMenu: () -> Unit,
    onOpenAssignmentDialog: () -> Unit,
    onClearPendingAssignment: () -> Unit,
    onOpenDelegateDetail: (String) -> Unit,
) {
    val style = slot.status.style()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .pointerInput(slot.id, slot.status) {
                if (slot.status == SlotBoardStatus.Assigned) return@pointerInput
                detectTapGestures(onDoubleTap = { onOpenAssignmentDialog() })
            }
            .pointerInput(slot.id) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Release && event.button == PointerButton.Secondary) {
                            event.changes.forEach { it.consume() }
                            onOpenContextMenu()
                        }
                    }
                }
            }
            .border(1.dp, style.borderColor, RoundedCornerShape(6.dp))
            .background(style.containerColor, RoundedCornerShape(6.dp))
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(5.dp).fillMaxHeight()
                .background(style.accentColor, RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp)),
        )
        Spacer(Modifier.width(8.dp))
        BoardTooltip(style.tooltip) {
            Icon(style.icon, contentDescription = style.tooltip, modifier = Modifier.size(16.dp), tint = style.accentColor)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = slot.delegateName ?: style.emptyText,
            modifier = Modifier
                .weight(1f)
                .pointerInput(slot.delegateId) {
                    val delegateId = slot.delegateId ?: return@pointerInput
                    detectTapGestures(onTap = { onOpenDelegateDetail(delegateId) })
                },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (slot.delegateName == null) FontWeight.Normal else FontWeight.SemiBold,
            color = if (slot.delegateName == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (slot.delegateIsHighSchooler) {
            BoardTooltip("High school delegate.") {
                Icon(
                    Icons.Filled.School,
                    contentDescription = "High school delegate",
                    tint = HighSchoolColor,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            when (slot.status) {
                SlotBoardStatus.Empty -> {
                    BoardTooltip("Assign a delegate to this open slot.") {
                        IconButton(onClick = onOpenAssignmentDialog, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Add, contentDescription = "Assign delegate", modifier = Modifier.size(16.dp))
                        }
                    }
                }
                SlotBoardStatus.Pending -> {
                    BoardTooltip("Replace this pending assignment.") {
                        IconButton(onClick = onOpenAssignmentDialog, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Edit, contentDescription = "Replace pending assignment", modifier = Modifier.size(16.dp))
                        }
                    }
                    BoardTooltip("Clear this pending assignment.") {
                        IconButton(onClick = onClearPendingAssignment, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear pending assignment", modifier = Modifier.size(16.dp))
                        }
                    }
                }
                SlotBoardStatus.Assigned -> Unit
            }
            BoardTooltip(style.tooltip) {
                StatusPill(style.label, style.pillColor, style.pillTextColor)
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, containerColor: Color, contentColor: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = contentColor,
        modifier = Modifier
            .background(containerColor, RoundedCornerShape(999.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
private fun SlotBoardStatus.style(): SlotStyle =
    when (this) {
        SlotBoardStatus.Empty -> SlotStyle(
            label = "Open",
            emptyText = "Available",
            tooltip = "Open slot with no delegate assigned.",
            icon = Icons.Filled.RadioButtonUnchecked,
            accentColor = MaterialTheme.colorScheme.outline,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            containerColor = MaterialTheme.colorScheme.surface,
            pillColor = MaterialTheme.colorScheme.surfaceVariant,
            pillTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SlotBoardStatus.Pending -> SlotStyle(
            label = "Pending",
            emptyText = "",
            tooltip = "Pending assignment created in MunAssign and waiting for MyMUN confirmation.",
            icon = Icons.Filled.HourglassEmpty,
            accentColor = Color(0xFFB26A00),
            borderColor = Color(0xFFB26A00).copy(alpha = 0.46f),
            containerColor = Color(0xFFFFF4D6),
            pillColor = Color(0xFFFFE2A8),
            pillTextColor = Color(0xFF4A2B00),
        )
        SlotBoardStatus.Assigned -> SlotStyle(
            label = "Confirmed",
            emptyText = "",
            tooltip = "Confirmed assignment from MyMUN update data.",
            icon = Icons.Filled.CheckCircle,
            accentColor = MaterialTheme.colorScheme.primary,
            borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f),
            pillColor = MaterialTheme.colorScheme.primaryContainer,
            pillTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }

private data class SlotStyle(
    val label: String,
    val emptyText: String,
    val tooltip: String,
    val icon: ImageVector,
    val accentColor: Color,
    val borderColor: Color,
    val containerColor: Color,
    val pillColor: Color,
    val pillTextColor: Color,
)

private val HighSchoolColor = Color(0xFF8E24AA)
