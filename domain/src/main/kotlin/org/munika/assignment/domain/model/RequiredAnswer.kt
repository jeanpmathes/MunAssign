package org.munika.assignment.domain.model

data class RequiredAnswer(val message: String) : AnswerRule {
    override fun evaluate(answer: String?): AnswerIssue? =
        if (answer.isNullOrBlank()) AnswerIssue(message, AnswerIssueSeverity.Warning) else null
}
