package org.munika.assignment.domain.model

class QuestionAnswer(
    override val id: QuestionAnswerId,
    val delegate: Delegate,
    val question: Question,
    val rawAnswer: String?,
) : DomainObject<QuestionAnswerId> {
    val issues: List<AnswerIssue>
        get() = question.issuesFor(rawAnswer)
}
