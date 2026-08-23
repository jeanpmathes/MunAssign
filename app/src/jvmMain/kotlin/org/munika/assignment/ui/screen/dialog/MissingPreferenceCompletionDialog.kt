package org.munika.assignment.ui.screen.dialog

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.Delegate
import org.munika.assignment.domain.model.PreferenceSource
import org.munika.assignment.domain.model.PositionId
import org.munika.assignment.domain.repository.PreferenceCorrectionSelection
import org.munika.assignment.domain.repository.PreferenceValueOrigin
import org.munika.assignment.ui.screen.component.PreferencePositionOptionUiState
import org.munika.assignment.ui.screen.component.PreferencePositionSelector
import org.munika.assignment.ui.viewmodel.PendingPreferenceCompletion
import kotlin.math.abs

@Composable
fun MissingPreferenceCompletionDialog(
    conference: Conference,
    pending: PendingPreferenceCompletion,
    loading: Boolean,
    onDismiss: () -> Unit,
    onSave: (List<PreferenceCorrectionSelection>) -> Unit,
) {
    val options = remember(conference) {
        conference.positions
            .map { position ->
                PreferencePositionOptionUiState(
                    id = position.id.value,
                    label = "${position.committee.displayName} - ${position.name}",
                )
            }
            .sortedBy { it.label.lowercase() }
    }
    val affectedDelegates = conference.delegates
        .filter { it.id.value in pending.missingRanksByDelegateId }
        .sortedBy { it.displayName.lowercase() }
    val cardsByDelegate = remember(pending, conference) {
        affectedDelegates.associate { delegate ->
            delegate.id.value to pending.preferenceRanks.map { rank ->
                val preference = delegate.preferences.firstOrNull { it.rank == rank }
                PreferenceCorrectionCardUiState(
                    positionId = preference?.position?.id?.value,
                    label = preference?.let { "${it.position.committee.displayName} - ${it.position.name}" },
                    origin = when (preference?.source) {
                        PreferenceSource.Mymun,
                        PreferenceSource.OrganizerCorrection,
                        -> PreferenceValueOrigin.Mymun
                        PreferenceSource.OrganizerCompletion -> PreferenceValueOrigin.Organizer
                        null -> null
                    },
                    emptyAcknowledged = preference == null && rank !in
                        pending.missingRanksByDelegateId[delegate.id.value].orEmpty(),
                )
            }.toMutableStateList()
        }
    }
    val tableWidth = 190.dp + (272.dp * pending.preferenceRanks.size)
    val horizontalScrollState = rememberScrollState()
    val delegateListState = rememberLazyListState()

    DialogWindow(
        onCloseRequest = { if (!loading) onDismiss() },
        state = rememberDialogState(size = DpSize(1100.dp, 720.dp)),
        title = "Complete missing preferences",
        resizable = true,
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Complete missing preferences",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Drag cards to correct their order. Complete empty ranks, choose No preference to keep one empty, " +
                        "or leave it unresolved to be asked again after the next update.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                pending.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .horizontalScroll(horizontalScrollState),
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    "Delegate",
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(190.dp).padding(vertical = 12.dp),
                                )
                                pending.preferenceRanks.forEach { rank ->
                                    Text(
                                        "Preference $rank",
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(260.dp).padding(vertical = 12.dp),
                                    )
                                }
                            }
                            HorizontalDivider()
                            LazyColumn(
                                state = delegateListState,
                                modifier = Modifier.width(tableWidth).weight(1f),
                            ) {
                                items(affectedDelegates, key = { it.id.value }) { delegate ->
                                    PreferenceCompletionRow(
                                        delegate = delegate,
                                        preferenceRanks = pending.preferenceRanks,
                                        cards = requireNotNull(cardsByDelegate[delegate.id.value]),
                                        options = options,
                                        loading = loading,
                                    )
                                    HorizontalDivider()
                                }
                            }
                        }
                        VerticalScrollbar(
                            adapter = rememberScrollbarAdapter(delegateListState),
                            modifier = Modifier.fillMaxHeight().padding(start = 8.dp),
                        )
                    }
                    HorizontalScrollbar(
                        adapter = rememberScrollbarAdapter(horizontalScrollState),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, end = 16.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(enabled = !loading, onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Button(
                        enabled = !loading,
                        onClick = {
                            onSave(
                                affectedDelegates.flatMap { delegate ->
                                    requireNotNull(cardsByDelegate[delegate.id.value]).mapIndexed { index, card ->
                                        PreferenceCorrectionSelection(
                                            delegateId = delegate.id,
                                            rank = pending.preferenceRanks[index],
                                            positionId = card.positionId?.let(::PositionId),
                                            origin = card.origin,
                                            emptyAcknowledged = card.emptyAcknowledged,
                                        )
                                    }
                                },
                            )
                        },
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
private fun PreferenceCompletionRow(
    delegate: Delegate,
    preferenceRanks: List<Int>,
    cards: MutableList<PreferenceCorrectionCardUiState>,
    options: List<PreferencePositionOptionUiState>,
    loading: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.width(190.dp).heightIn(min = 72.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(delegate.displayName, fontWeight = FontWeight.SemiBold)
        }
        preferenceRanks.forEachIndexed { index, rank ->
            val card = cards[index]
            val usedPositions = cards.mapIndexedNotNullTo(mutableSetOf()) { otherIndex, otherCard ->
                otherCard.positionId?.takeIf { otherIndex != index }
            }
            Row(
                modifier = Modifier.width(260.dp).heightIn(min = 72.dp).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PreferenceDragHandle(
                    delegateId = delegate.id.value,
                    index = index,
                    cards = cards,
                    enabled = !loading,
                )
                if (card.origin == PreferenceValueOrigin.Mymun) {
                    Text(card.label.orEmpty(), modifier = Modifier.weight(1f))
                } else {
                    PreferencePositionSelector(
                        selectedPositionId = card.positionId,
                        options = options,
                        excludedPositionIds = usedPositions,
                        label = "Preference $rank",
                        enabled = !loading,
                        onSelect = { positionId ->
                            val option = options.firstOrNull { it.id == positionId }
                            cards[index] = card.copy(
                                positionId = positionId,
                                label = option?.label,
                                origin = positionId?.let { PreferenceValueOrigin.Organizer },
                                emptyAcknowledged = false,
                            )
                        },
                        modifier = Modifier.weight(1f),
                        intentionalEmpty = card.emptyAcknowledged,
                        onSelectIntentionalEmpty = {
                            cards[index] = card.copy(
                                positionId = null,
                                label = null,
                                origin = null,
                                emptyAcknowledged = true,
                            )
                        },
                    )
                }
            }
        }
    }
}

internal data class PreferenceCorrectionCardUiState(
    val positionId: String?,
    val label: String?,
    val origin: PreferenceValueOrigin?,
    val emptyAcknowledged: Boolean,
)

@Composable
private fun PreferenceDragHandle(
    delegateId: String,
    index: Int,
    cards: MutableList<PreferenceCorrectionCardUiState>,
    enabled: Boolean,
) {
    var accumulatedDrag by remember(delegateId, index) { mutableFloatStateOf(0f) }
    Icon(
        Icons.Filled.DragIndicator,
        contentDescription = "Drag preference ${index + 1} to reorder",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(end = 6.dp).pointerInput(enabled, index, cards.size) {
            if (!enabled) return@pointerInput
            detectHorizontalDragGestures(
                onDragEnd = { accumulatedDrag = 0f },
                onDragCancel = { accumulatedDrag = 0f },
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    accumulatedDrag += dragAmount
                    if (abs(accumulatedDrag) >= 72f) {
                        val target = (index + if (accumulatedDrag > 0) 1 else -1).coerceIn(0, cards.lastIndex)
                        if (target != index) {
                            val reordered = movePreferenceCorrectionCard(cards, index, target)
                            cards.clear()
                            cards.addAll(reordered)
                        }
                        accumulatedDrag = 0f
                    }
                },
            )
        },
    )
}

internal fun movePreferenceCorrectionCard(
    cards: List<PreferenceCorrectionCardUiState>,
    fromIndex: Int,
    toIndex: Int,
): List<PreferenceCorrectionCardUiState> {
    require(fromIndex in cards.indices && toIndex in cards.indices) { "Preference card indexes must be valid." }
    return cards.toMutableList().apply {
        add(toIndex, removeAt(fromIndex))
    }
}
