package org.munika.assignment.domain.model

/**
 * Warns when an expected answer is blank or missing.
 */
data class RequiredAnswer(val message: String) : AnswerRule {
    override fun evaluate(answer: String?): AnswerIssue? =
        if (answer.isNullOrBlank()) AnswerIssue(message, AnswerIssueSeverity.Warning) else null
}
