package org.munika.assignment.domain.model

/**
 * Configurable validation or notice rule for a question answer.
 *
 * Rules return at most one issue and never mutate the answer or question configuration.
 */
sealed interface AnswerRule {
    fun evaluate(answer: String?): AnswerIssue?
}
