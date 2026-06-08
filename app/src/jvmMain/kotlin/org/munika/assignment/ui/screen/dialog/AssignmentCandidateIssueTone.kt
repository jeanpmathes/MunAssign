package org.munika.assignment.ui.screen.dialog

import org.munika.assignment.ui.viewmodel.DelegateStatusTone

internal data class AssignmentCandidateIssueBadgeUiState(
    val label: String,
    val tone: DelegateStatusTone,
    val filled: Boolean,
)

internal fun assignmentCandidateIssueBadge(count: Int, isWarning: Boolean): AssignmentCandidateIssueBadgeUiState =
    AssignmentCandidateIssueBadgeUiState(
        label = "$count ${if (isWarning) "W" else "N"}",
        tone = if (isWarning) DelegateStatusTone.Yellow else DelegateStatusTone.Notice,
        filled = true,
    )
