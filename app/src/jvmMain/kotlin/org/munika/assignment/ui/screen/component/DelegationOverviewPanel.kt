package org.munika.assignment.ui.screen.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.munika.assignment.ui.viewmodel.DelegationDelegateUiState
import org.munika.assignment.ui.viewmodel.DelegationOverviewRowUiState
import org.munika.assignment.ui.viewmodel.DelegationOverviewUiState
import org.munika.assignment.ui.viewmodel.DelegationReservationUiState

@Composable
fun DelegationOverviewPanel(
    overview: DelegationOverviewUiState,
    targetDelegationId: String?,
    loading: Boolean,
    onOpenReservationManager: (delegationId: String?) -> Unit,
    onCreateReservation: (delegationId: String) -> Unit,
    onSetHighSchoolStatus: (delegationId: String, isHighSchoolDelegation: Boolean) -> Unit,
    onOpenDelegateDetail: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(targetDelegationId, overview.delegations) {
        val targetIndex = overview.delegations.indexOfFirst { it.id == targetDelegationId }
        if (targetIndex >= 0) {
            listState.animateScrollToItem(targetIndex)
        }
    }

    Surface(
        modifier = modifier.width(520.dp).fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            Header(onClose)
            HorizontalDivider()
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (overview.delegations.isEmpty()) {
                    item {
                        Text(
                            "No delegations in this conference",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                        )
                    }
                }
                items(overview.delegations, key = { it.id }) { delegation ->
                    DelegationCard(
                        delegation = delegation,
                        highlighted = delegation.id == targetDelegationId,
                        loading = loading,
                        onOpenReservationManager = { onOpenReservationManager(delegation.id) },
                        onCreateReservation = { onCreateReservation(delegation.id) },
                        onSetHighSchoolStatus = { isHighSchool ->
                            onSetHighSchoolStatus(delegation.id, isHighSchool)
                        },
                        onOpenDelegateDetail = onOpenDelegateDetail,
                    )
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
        Icon(Icons.Filled.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text("Delegations", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        BoardTooltip("Close delegation overview") {
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Close delegation overview")
            }
        }
    }
}

@Composable
private fun DelegationCard(
    delegation: DelegationOverviewRowUiState,
    highlighted: Boolean,
    loading: Boolean,
    onOpenReservationManager: () -> Unit,
    onCreateReservation: () -> Unit,
    onSetHighSchoolStatus: (Boolean) -> Unit,
    onOpenDelegateDetail: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            width = if (highlighted) 2.dp else 1.dp,
            color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            delegation.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (delegation.needsReview) {
                            BoardTooltip("New delegation not yet reviewed") {
                                Icon(
                                    Icons.Filled.Warning,
                                    contentDescription = "New delegation",
                                    tint = NewDelegationColor,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                    Text(
                        delegation.country ?: "No country recorded",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val schoolTooltip = if (delegation.isHighSchoolDelegation) {
                    "Mark ${delegation.name} as a university delegation."
                } else {
                    "Mark ${delegation.name} as a high-school delegation."
                }
                BoardTooltip(schoolTooltip) {
                    IconToggleButton(
                        checked = delegation.isHighSchoolDelegation,
                        onCheckedChange = onSetHighSchoolStatus,
                        enabled = !loading,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Filled.School,
                            contentDescription = schoolTooltip,
                            tint = if (delegation.isHighSchoolDelegation) {
                                HighSchoolColor
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                BoardTooltip("Create a prefilled reservation for ${delegation.name}.") {
                    IconButton(onClick = onCreateReservation, enabled = !loading, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Add, contentDescription = "Create reservation", modifier = Modifier.size(19.dp))
                    }
                }
                BoardTooltip("Open reservation manager for ${delegation.name}.") {
                    Button(onClick = onOpenReservationManager, enabled = !loading) {
                        Text("Reservations")
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoChip("Expected ${delegation.expectedDelegateCount?.toString() ?: "-"}")
                InfoChip("Online ${delegation.currentDelegateCountFromMymun?.toString() ?: "-"}")
                InfoChip("Usable ${delegation.activePaidAcceptedDelegateCount}")
            }
            ReservationSection(delegation.linkedReservations, onOpenReservationManager)
            DelegateSection(
                title = "Accepted / assigned",
                emptyLabel = "No accepted delegates linked online",
                delegates = delegation.acceptedDelegates,
                showAssignment = true,
                onOpenDelegateDetail = onOpenDelegateDetail,
            )
            DelegateSection(
                title = "Submitted",
                emptyLabel = "No submitted delegates linked online",
                delegates = delegation.submittedDelegates,
                showAssignment = false,
                onOpenDelegateDetail = onOpenDelegateDetail,
            )
        }
    }
}

@Composable
private fun ReservationSection(
    reservations: List<DelegationReservationUiState>,
    onOpenReservationManager: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Linked reservations", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        if (reservations.isEmpty()) {
            Text("None", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            reservations.forEach { reservation ->
                BoardTooltip("Open reservation manager for ${reservation.name}.") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenReservationManager() }
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(reservation.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "HS ${reservation.usedHighSchoolCount}/${reservation.totalHighSchoolCount} · Uni ${reservation.usedUniversityCount}/${reservation.totalUniversityCount}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DelegateSection(
    title: String,
    emptyLabel: String,
    delegates: List<DelegationDelegateUiState>,
    showAssignment: Boolean,
    onOpenDelegateDetail: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        if (delegates.isEmpty()) {
            Text(emptyLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            delegates.forEach { delegate ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f), RoundedCornerShape(6.dp))
                        .clickable { onOpenDelegateDetail(delegate.id) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(delegate.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (showAssignment) {
                            Text(
                                delegate.assignmentLabel ?: "Unassigned",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    delegate.reservationName?.let { reservationName ->
                        Text(reservationName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (delegate.isHighSchooler) {
                        BoardTooltip("High-school delegate") {
                            Icon(Icons.Filled.School, contentDescription = "High-school delegate", tint = HighSchoolColor, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

private val HighSchoolColor = Color(0xFF8E24AA)
private val NewDelegationColor = Color(0xFFF9A825)
