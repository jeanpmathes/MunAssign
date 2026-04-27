package org.munika.assignment.domain.model

sealed interface AnswerRule {
    fun evaluate(answer: String?): AnswerIssue?
}
