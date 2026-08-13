package org.munika.assignment.domain.model

/**
 * Produces an operational notice when a meaningful free-text answer is not recognizably negative.
 *
 * This is used for answers such as visa-support requests where blanks, placeholders, and answers
 * beginning with a complete recognized negative form should not require organizer action.
 */
data class NoticeWhenAffirmative(val message: String) : AnswerRule {
    override fun evaluate(answer: String?): AnswerIssue? {
        val normalized = normalizeAnswer(answer)
        return if (normalized.isNotBlank() && !normalized.isNegativeAnswer()) {
            AnswerIssue(message, AnswerIssueSeverity.Notice)
        } else {
            null
        }
    }

    private fun String.isNegativeAnswer(): Boolean =
        this in exactNegativeAnswers || leadingNegativeAnswers.any { startsWithCompleteForm(it) }

    private fun String.startsWithCompleteForm(form: String): Boolean =
        startsWith(form) && (length == form.length || !this[form.length].isLetterOrDigit())

    private companion object {
        val exactNegativeAnswers = setOf(
            "-",
            "--",
            "/",
            "n",
        )

        val leadingNegativeAnswers = setOf(
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
