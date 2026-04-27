package org.munika.assignment.domain.model

/**
 * One delegate's raw answer to one imported MyMUN question.
 *
 * Typed interpretation and warnings are delegated to the question configuration.
 */
class QuestionAnswer(
    override val id: QuestionAnswerId,
    val delegate: Delegate,
    val question: Question,
    val rawAnswer: String?,
) : DomainObject<QuestionAnswerId> {
    val issues: List<AnswerIssue>
        get() = question.issuesFor(rawAnswer)
}
