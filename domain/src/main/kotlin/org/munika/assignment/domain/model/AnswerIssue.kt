package org.munika.assignment.domain.model

/**
 * Notice or warning produced by applying answer rules to a raw MyMUN answer.
 */
data class AnswerIssue(
    val message: String,
    val severity: AnswerIssueSeverity,
)
