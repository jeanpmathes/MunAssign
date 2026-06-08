package org.munika.assignment.domain.model

/**
 * Notice or warning produced by applying answer rules to a raw MyMUN answer.
 */
data class AnswerIssue(
    val message: String,
    val severity: AnswerIssueSeverity,
    val key: String = stableKey(severity, message),
) {
    companion object {
        fun stableKey(severity: AnswerIssueSeverity, message: String): String =
            "${severity.name}:${message.trim().replace(Regex("\\s+"), " ")}"
    }
}
