package org.munika.assignment.ui.screen.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.munika.assignment.ui.viewmodel.SlotBoardStatus
import org.munika.assignment.ui.viewmodel.SlotBoardUiState

@Composable
fun SlotRow(slot: SlotBoardUiState) {
    val style = slot.status.style()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
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
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (slot.delegateName == null) FontWeight.Normal else FontWeight.SemiBold,
            color = if (slot.delegateName == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
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
            accentColor = MaterialTheme.colorScheme.tertiary,
            borderColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f),
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.28f),
            pillColor = MaterialTheme.colorScheme.tertiaryContainer,
            pillTextColor = MaterialTheme.colorScheme.onTertiaryContainer,
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
