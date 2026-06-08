package org.munika.assignment.ui.screen.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.munika.assignment.ui.viewmodel.DelegateStatusTone

@Composable
internal fun DelegateStatusTone.containerColor(): Color =
    when (this) {
        DelegateStatusTone.Green -> Color(0xFFDFF5DF)
        DelegateStatusTone.Red -> MaterialTheme.colorScheme.errorContainer
        DelegateStatusTone.Blue -> MaterialTheme.colorScheme.primaryContainer
        DelegateStatusTone.Yellow -> Color(0xFFFFE2A8)
        DelegateStatusTone.Notice -> Color(0xFFD8F0F5)
        DelegateStatusTone.Neutral -> MaterialTheme.colorScheme.surfaceVariant
    }

@Composable
internal fun DelegateStatusTone.contentColor(): Color =
    when (this) {
        DelegateStatusTone.Green -> Color(0xFF163F1A)
        DelegateStatusTone.Red -> MaterialTheme.colorScheme.onErrorContainer
        DelegateStatusTone.Blue -> MaterialTheme.colorScheme.onPrimaryContainer
        DelegateStatusTone.Yellow -> Color(0xFF4A2B00)
        DelegateStatusTone.Notice -> Color(0xFF0B4F66)
        DelegateStatusTone.Neutral -> MaterialTheme.colorScheme.onSurfaceVariant
    }
