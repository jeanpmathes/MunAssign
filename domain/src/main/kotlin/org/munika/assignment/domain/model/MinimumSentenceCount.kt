package org.munika.assignment.domain.model

data class MinimumSentenceCount(val count: Int) : AnswerRule {
    override fun evaluate(answer: String?): AnswerIssue? {
        val sentenceCount = answer.orEmpty().split('.', '!', '?').count { it.trim().isNotEmpty() }
        return if (sentenceCount < count) {
            AnswerIssue("Expected at least $count sentences.", AnswerIssueSeverity.Warning)
        } else {
            null
        }
    }
}
