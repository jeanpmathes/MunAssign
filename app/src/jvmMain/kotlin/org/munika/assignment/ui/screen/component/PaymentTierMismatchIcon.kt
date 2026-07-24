package org.munika.assignment.ui.screen.component

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.munika.assignment.ui.viewmodel.PaymentTierMismatchDirection
import org.munika.assignment.ui.viewmodel.PaymentTierMismatchUiState

@Composable
fun PaymentTierMismatchIcon(
    mismatch: PaymentTierMismatchUiState,
    modifier: Modifier = Modifier,
) {
    val reducedIntoFull = mismatch.direction == PaymentTierMismatchDirection.ReducedIntoFull
    val baseTooltip = if (reducedIntoFull) {
        "Reduced-tier delegate in a Full-tier committee."
    } else {
        "Full-tier delegate in a Reduced-tier committee."
    }
    val tooltip = if (mismatch.acknowledged) "$baseTooltip Acknowledged in the issue manager." else baseTooltip
    val color = if (reducedIntoFull) ReducedIntoFullColor else FullIntoReducedColor
    BoardTooltip(tooltip) {
        Icon(
            imageVector = if (reducedIntoFull) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
            contentDescription = tooltip,
            tint = if (mismatch.acknowledged) color.copy(alpha = 0.42f) else color,
            modifier = modifier.then(Modifier.size(18.dp)),
        )
    }
}

private val ReducedIntoFullColor = Color(0xFFB3261E)
private val FullIntoReducedColor = Color(0xFFB26A00)
