package org.munika.assignment.ui.screen.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.munika.assignment.ui.viewmodel.InterestedDelegateUiState
import org.munika.assignment.ui.viewmodel.PositionBoardUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PositionCard(position: PositionBoardUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = position.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    BoardTooltip("Filled slots out of total slots for this position.") {
                        Text(
                            text = "${position.occupiedSlotCount}/${position.slots.size} filled",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                position.slots.forEach { slot ->
                    SlotRow(slot = slot)
                }
            }

            if (position.shouldShowInterestedDelegates && position.interestedDelegates.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Interested delegates",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    position.interestedDelegates.forEach { delegate ->
                        InterestedDelegateChip(delegate = delegate)
                    }
                }
            }
        }
    }
}

@Composable
private fun InterestedDelegateChip(delegate: InterestedDelegateUiState) {
    val confirmedAssignmentLabel = delegate.confirmedAssignmentLabel
    val textColor = if (confirmedAssignmentLabel != null) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val tooltip = if (confirmedAssignmentLabel != null) {
        "${delegate.name} ranked this position #${delegate.preferenceRank}, but is confirmed for $confirmedAssignmentLabel."
    } else {
        "${delegate.name} ranked this position #${delegate.preferenceRank}."
    }
    BoardTooltip(tooltip) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(999.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = "#${delegate.preferenceRank}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(999.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
            Text(
                text = delegate.name,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (confirmedAssignmentLabel != null) TextDecoration.LineThrough else TextDecoration.None,
            )
        }
    }
}
