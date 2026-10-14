package org.munika.assignment.ui.screen.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.munika.assignment.domain.model.CommitteeMode
import org.munika.assignment.domain.model.PaymentTier
import org.munika.assignment.ui.viewmodel.AssignmentBoardUiState
import org.munika.assignment.ui.viewmodel.BoardScrollRequest

@Composable
fun AssignmentBoard(
    board: AssignmentBoardUiState,
    loading: Boolean,
    actionError: String?,
    scrollRequest: BoardScrollRequest?,
    onSetCommitteeSlotsPerPosition: (String, Int) -> Unit,
    onSetCommitteeMode: (String, CommitteeMode) -> Unit,
    onSetCommitteePaymentTier: (String, PaymentTier) -> Unit,
    onSetPositionMappedCommittee: (String, String?) -> Unit,
    onAssignDelegateToSlot: (slotId: String, delegateId: String, moveExistingPending: Boolean) -> Unit,
    onSetPositionPendingAssignments: (positionId: String, delegateIds: List<String>) -> Unit,
    onClearPendingSlot: (slotId: String) -> Unit,
    onOpenDelegateDetail: (String) -> Unit,
    onOpenReservationManager: () -> Unit,
    onClearActionError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val horizontalState = remember(board.conferenceId) { LazyListState() }
    val columnListStates = remember(board.conferenceId) { mutableMapOf<String, LazyListState>() }
    var highlightedPositionId by remember(board.conferenceId) { mutableStateOf<String?>(null) }

    LaunchedEffect(scrollRequest?.requestId) {
        val request = scrollRequest ?: return@LaunchedEffect
        val committeeIndex = board.committees.indexOfFirst { it.id == request.committeeId }
        if (committeeIndex < 0) return@LaunchedEffect
        horizontalState.animateScrollToItem(committeeIndex)
        val positionId = request.positionId ?: return@LaunchedEffect
        val committee = board.committees[committeeIndex]
        val positionIndex = committee.positions.indexOfFirst { it.id == positionId }
        if (positionIndex >= 0) {
            columnListStates.getOrPut(committee.id) { LazyListState() }.animateScrollToItem(positionIndex)
        }
        highlightedPositionId = positionId
        delay(1200)
        if (highlightedPositionId == positionId) {
            highlightedPositionId = null
        }
    }

    Column(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        actionError?.let { message ->
            Card(
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClearActionError) {
                        Text("Dismiss")
                    }
                }
            }
        }

        LazyRow(
            state = horizontalState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(board.committees, key = { it.id }) { committee ->
                CommitteeColumn(
                    committee = committee,
                    loading = loading,
                    listState = columnListStates.getOrPut(committee.id) { LazyListState() },
                    onSetSlotsPerPosition = onSetCommitteeSlotsPerPosition,
                    onSetCommitteeMode = onSetCommitteeMode,
                    onSetCommitteePaymentTier = onSetCommitteePaymentTier,
                    onSetPositionMappedCommittee = onSetPositionMappedCommittee,
                    onAssignDelegateToSlot = onAssignDelegateToSlot,
                    onSetPositionPendingAssignments = onSetPositionPendingAssignments,
                    onClearPendingSlot = onClearPendingSlot,
                    onOpenDelegateDetail = onOpenDelegateDetail,
                    onOpenReservationManager = onOpenReservationManager,
                    highlightedPositionId = highlightedPositionId,
                )
            }
        }
        HorizontalScrollbar(
            adapter = rememberScrollbarAdapter(horizontalState),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
