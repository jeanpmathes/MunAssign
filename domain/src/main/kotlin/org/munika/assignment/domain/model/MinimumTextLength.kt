package org.munika.assignment.domain.model

/**
 * Warns when a trimmed answer is shorter than [length] characters.
 */
data class MinimumTextLength(val length: Int) : AnswerRule {
    override fun evaluate(answer: String?): AnswerIssue? =
        if (answer.orEmpty().trim().length < length) {
            AnswerIssue("Expected at least $length characters.", AnswerIssueSeverity.Warning)
        } else {
            null
        }
}
