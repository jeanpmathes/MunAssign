package org.munika.assignment.ui.screen.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.munika.assignment.ui.viewmodel.DelegateSchoolFit
import org.munika.assignment.ui.viewmodel.PendingAssignmentSwapUiState
import org.munika.assignment.ui.viewmodel.SwapDelegateUiState

@Composable
fun PendingAssignmentSwapDialog(
    opportunities: List<PendingAssignmentSwapUiState>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (PendingAssignmentSwapUiState) -> Unit,
) {
    var selectedSlotId by remember(opportunities) { mutableStateOf<String?>(null) }
    val selected = opportunities.firstOrNull { it.targetSlotId == selectedSlotId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review swap") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Choose a partner for ${opportunities.first().source.name}. Both assignments will remain pending.")
                opportunities.forEach { opportunity ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !loading) {
                            selectedSlotId = opportunity.targetSlotId
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = opportunity.targetSlotId == selectedSlotId,
                            onClick = { selectedSlotId = opportunity.targetSlotId },
                            enabled = !loading,
                        )
                        Column {
                            Text(opportunity.target.name)
                            Text("Preference #${opportunity.preferenceRank}: ${opportunity.source.afterLabel}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                selected?.let { swap ->
                    SwapDelegateReview(swap.source)
                    SwapDelegateReview(swap.target)
                }
            }
        },
        confirmButton = {
            Button(enabled = selected != null && !loading, onClick = { selected?.let(onConfirm) }) {
                Text("Apply swap")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SwapDelegateReview(delegate: SwapDelegateUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(delegate.name, style = MaterialTheme.typography.titleSmall)
        Text("From: ${delegate.beforeLabel}")
        Text("To: ${delegate.afterLabel}")
        Text(if (delegate.heartbrokenAfter) "Still assigned outside preferences." else "Receives a ranked preference.")
        if (delegate.schoolFit == DelegateSchoolFit.Mismatch) {
            Text("Warning: school level does not match this position.", color = MaterialTheme.colorScheme.error)
        }
        if (!delegate.paymentTierKnown) {
            Text("Warning: payment tier has not been classified.", color = MaterialTheme.colorScheme.error)
        }
        delegate.paymentTierMismatch?.let { mismatch ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                PaymentTierMismatchIcon(mismatch)
                Text("Payment tier differs from this committee.")
            }
        }
    }
}
