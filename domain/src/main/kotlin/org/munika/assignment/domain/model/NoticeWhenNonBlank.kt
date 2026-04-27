package org.munika.assignment.domain.model

data class NoticeWhenNonBlank(val message: String) : AnswerRule {
    override fun evaluate(answer: String?): AnswerIssue? =
        if (answer?.isNotBlank() == true) AnswerIssue(message, AnswerIssueSeverity.Notice) else null
}
