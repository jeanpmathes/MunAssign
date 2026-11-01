package org.munika.assignment.domain.model

/**
 * One delegate's raw answer to one imported MyMUN question.
 *
 * Typed interpretation and warnings are delegated to the question configuration. For visa-support
 * handling, the standard request notice applies only while paid; other rules still apply.
 */
class QuestionAnswer(
    override val id: QuestionAnswerId,
    val delegate: Delegate,
    val question: Question,
    val rawAnswer: String?,
) : DomainObject<QuestionAnswerId> {
    val issues: List<AnswerIssue>
        get() {
            val issues = question.issuesFor(rawAnswer)
            return if (delegate.paymentStatus == PaymentStatus.Unpaid &&
                question.handling == StandardQuestionHandlingTypes.VisaSupport
            ) {
                issues.filterNot { it.key == "Notice:Visa support requested." }
            } else {
                issues
            }
        }

    val openIssues: List<AnswerIssue>
        get() = issues.filter { issue -> delegate.resolutionFor(question.id, issue) == null }

    val resolvedIssues: List<ResolvedAnswerIssue>
        get() = issues.mapNotNull { issue ->
            delegate.resolutionFor(question.id, issue)?.let { resolution -> ResolvedAnswerIssue(issue, resolution) }
        }
}
