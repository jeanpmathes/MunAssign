package org.munika.assignment.domain.model

/**
 * Accepts an answer with at least [count] sentence-like fragments or ten words per required sentence.
 * Either threshold is sufficient, so substantive answers do not depend on punctuation.
 */
data class MinimumSentenceCount(val count: Int) : AnswerRule {
    /** Preserves persisted acknowledgements when the displayed wording changes. */
    val issueKey: String
        get() = AnswerIssue.stableKey(AnswerIssueSeverity.Warning, "Expected at least $count sentences.")

    override fun evaluate(answer: String?): AnswerIssue? {
        val sentenceCount = answer.orEmpty().split('.', '!', '?').count { it.trim().isNotEmpty() }
        val minimumWordCount = count.toLong() * 10
        val wordCount = word.findAll(answer.orEmpty()).count()
        return if (sentenceCount < count && wordCount < minimumWordCount) {
            AnswerIssue(
                "Expected at least $count sentences or $minimumWordCount words.",
                AnswerIssueSeverity.Warning,
                key = issueKey,
            )
        } else {
            null
        }
    }

    private companion object {
        val word = Regex("[\\p{L}\\p{N}]+(?:['\\u2019-][\\p{L}\\p{N}]+)*")
    }
}
