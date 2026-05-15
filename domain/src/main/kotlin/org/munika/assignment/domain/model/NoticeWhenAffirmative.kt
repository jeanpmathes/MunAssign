package org.munika.assignment.domain.model

/**
 * Produces an operational notice only when an answer looks like an affirmative free-text response.
 *
 * This is used for answers such as visa-support requests where blanks, dashes, and explicit no
 * answers should not be treated as something organizers need to act on.
 */
data class NoticeWhenAffirmative(val message: String) : AnswerRule {
    override fun evaluate(answer: String?): AnswerIssue? {
        val normalized = normalizeAnswer(answer)
        return if (normalized.isNotBlank() && normalized !in negativeAnswers) {
            AnswerIssue(message, AnswerIssueSeverity.Notice)
        } else {
            null
        }
    }

    private companion object {
        val negativeAnswers = setOf(
            "-",
            "--",
            "/",
            "n",
            "n a",
            "n/a",
            "na",
            "nein",
            "no",
            "none",
            "nope",
            "not applicable",
        )
    }
}
