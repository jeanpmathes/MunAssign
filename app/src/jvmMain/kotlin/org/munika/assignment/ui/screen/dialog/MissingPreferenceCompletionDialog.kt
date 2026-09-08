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
import androidx.compose.runtime.mutableStateMapOf
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
    val swapNoticesByDelegate = remember(pending, conference) {
        affectedDelegates.associate { delegate ->
            delegate.id.value to mutableStateMapOf<Int, Int>()
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
                                        swapNotices = requireNotNull(swapNoticesByDelegate[delegate.id.value]),
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
    swapNotices: MutableMap<Int, Int>,
    options: List<PreferencePositionOptionUiState>,
    loading: Boolean,
) {
    val rankedOptions = preferencePositionOptionsWithCurrentRanks(options, cards, preferenceRanks)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.width(190.dp).heightIn(min = 72.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(delegate.displayName, fontWeight = FontWeight.SemiBold)
        }
        preferenceRanks.forEachIndexed { index, rank ->
            val card = cards[index]
            Row(
                modifier = Modifier.width(260.dp).heightIn(min = 72.dp).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PreferenceDragHandle(
                    delegateId = delegate.id.value,
                    index = index,
                    cardCount = cards.size,
                    enabled = !loading,
                    onMove = { fromIndex, toIndex ->
                        val reordered = movePreferenceCorrectionCard(cards, fromIndex, toIndex)
                        cards.clear()
                        cards.addAll(reordered)
                        val updatedNotices = clearPreferenceSwapNoticesForMove(
                            current = swapNotices,
                            preferenceRanks = preferenceRanks,
                            fromIndex = fromIndex,
                            toIndex = toIndex,
                        )
                        swapNotices.clear()
                        swapNotices.putAll(updatedNotices)
                    },
                )
                if (card.origin == PreferenceValueOrigin.Mymun) {
                    Text(card.label.orEmpty(), modifier = Modifier.weight(1f))
                } else {
                    PreferencePositionSelector(
                        selectedPositionId = card.positionId,
                        options = rankedOptions,
                        editingRank = rank,
                        label = "Preference $rank",
                        enabled = !loading,
                        onSelect = { option ->
                            if (option == null) {
                                swapNotices.remove(rank)
                                cards[index] = card.copy(
                                    positionId = null,
                                    label = null,
                                    origin = null,
                                    emptyAcknowledged = false,
                                )
                            } else {
                                val selection = selectPreferencePosition(
                                    cards = cards,
                                    preferenceRanks = preferenceRanks,
                                    targetIndex = index,
                                    option = option,
                                )
                                if (selection.changed) {
                                    cards.clear()
                                    cards.addAll(selection.cards)
                                    val updatedNotices = updatePreferenceSwapNotices(
                                        current = swapNotices,
                                        editedRank = rank,
                                        swappedFromRank = selection.swappedFromRank,
                                    )
                                    swapNotices.clear()
                                    swapNotices.putAll(updatedNotices)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        intentionalEmpty = card.emptyAcknowledged,
                        swapNotice = swapNotices[rank]?.let { swappedWithRank ->
                            "Swapped with preference $swappedWithRank"
                        },
                        onEdit = { swapNotices.remove(rank) },
                        onSelectIntentionalEmpty = {
                            swapNotices.remove(rank)
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

internal data class PreferenceCardSelectionResult(
    val cards: List<PreferenceCorrectionCardUiState>,
    val swappedFromRank: Int?,
    val changed: Boolean,
)

internal fun preferencePositionOptionsWithCurrentRanks(
    options: List<PreferencePositionOptionUiState>,
    cards: List<PreferenceCorrectionCardUiState>,
    preferenceRanks: List<Int>,
): List<PreferencePositionOptionUiState> {
    require(cards.size == preferenceRanks.size) { "Preference cards and ranks must have the same size." }
    val currentRankByPositionId = cards.mapIndexedNotNull { index, card ->
        card.positionId?.let { it to preferenceRanks[index] }
    }.toMap()
    return options.map { option -> option.copy(currentRank = currentRankByPositionId[option.id]) }
}

internal fun selectPreferencePosition(
    cards: List<PreferenceCorrectionCardUiState>,
    preferenceRanks: List<Int>,
    targetIndex: Int,
    option: PreferencePositionOptionUiState,
): PreferenceCardSelectionResult {
    require(cards.size == preferenceRanks.size) { "Preference cards and ranks must have the same size." }
    require(targetIndex in cards.indices) { "Target preference card index must be valid." }
    val sourceIndex = option.currentRank?.let { currentRank ->
        preferenceRanks.indexOf(currentRank).takeIf { it >= 0 }
            ?: error("Current preference rank must belong to the displayed sequence.")
    }
    if (sourceIndex == targetIndex) {
        return PreferenceCardSelectionResult(cards, swappedFromRank = null, changed = false)
    }

    val updated = cards.toMutableList()
    if (sourceIndex == null) {
        updated[targetIndex] = PreferenceCorrectionCardUiState(
            positionId = option.id,
            label = option.label,
            origin = PreferenceValueOrigin.Organizer,
            emptyAcknowledged = false,
        )
    } else {
        updated[targetIndex] = cards[sourceIndex]
        updated[sourceIndex] = cards[targetIndex]
    }
    return PreferenceCardSelectionResult(
        cards = updated,
        swappedFromRank = sourceIndex?.let(preferenceRanks::get),
        changed = true,
    )
}

internal fun updatePreferenceSwapNotices(
    current: Map<Int, Int>,
    editedRank: Int,
    swappedFromRank: Int?,
): Map<Int, Int> = current.toMutableMap().apply {
    remove(editedRank)
    swappedFromRank?.let { sourceRank ->
        remove(sourceRank)
        this[sourceRank] = editedRank
    }
}

internal fun clearPreferenceSwapNoticesForMove(
    current: Map<Int, Int>,
    preferenceRanks: List<Int>,
    fromIndex: Int,
    toIndex: Int,
): Map<Int, Int> {
    require(fromIndex in preferenceRanks.indices && toIndex in preferenceRanks.indices) {
        "Preference card indexes must be valid."
    }
    val movedRange = minOf(fromIndex, toIndex)..maxOf(fromIndex, toIndex)
    val movedRanks = movedRange.mapTo(mutableSetOf()) { preferenceRanks[it] }
    return current.filterKeys { it !in movedRanks }
}

@Composable
private fun PreferenceDragHandle(
    delegateId: String,
    index: Int,
    cardCount: Int,
    enabled: Boolean,
    onMove: (Int, Int) -> Unit,
) {
    var accumulatedDrag by remember(delegateId, index) { mutableFloatStateOf(0f) }
    Icon(
        Icons.Filled.DragIndicator,
        contentDescription = "Drag preference ${index + 1} to reorder",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(end = 6.dp).pointerInput(enabled, index, cardCount) {
            if (!enabled) return@pointerInput
            detectHorizontalDragGestures(
                onDragEnd = { accumulatedDrag = 0f },
                onDragCancel = { accumulatedDrag = 0f },
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    accumulatedDrag += dragAmount
                    if (abs(accumulatedDrag) >= 72f) {
                        val target = (index + if (accumulatedDrag > 0) 1 else -1).coerceIn(0, cardCount - 1)
                        if (target != index) {
                            onMove(index, target)
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
