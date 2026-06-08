package org.munika.assignment.data.db.model

data class AnswerIssueResolutionRow(
    val id: String,
    val conferenceId: String,
    val delegateId: String,
    val questionId: String?,
    val issueKey: String,
    val issueMessage: String,
    val severity: String,
    val justification: String?,
    val resolvedAtEpochMillis: Long,
)
