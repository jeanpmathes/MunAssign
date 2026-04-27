package org.munika.assignment.domain.model

/**
 * Warns when an answer contains fewer sentence-like fragments than [count].
 */
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
