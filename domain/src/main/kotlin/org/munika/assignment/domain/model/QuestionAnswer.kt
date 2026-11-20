package org.munika.assignment.domain.model

/**
 * One delegate's raw answer to one imported MyMUN question.
 *
 * Question rules interpret the raw answer. Delegate payment and application status determine
 * whether operational visa notices and pre-acceptance explanation warnings are relevant.
 */
class QuestionAnswer(
    override val id: QuestionAnswerId,
    val delegate: Delegate,
    val question: Question,
    val rawAnswer: String?,
) : DomainObject<QuestionAnswerId> {
    val issues: List<AnswerIssue>
        get() {
            val irrelevantIssueKeys = buildSet {
                if (
                    question.handling == StandardQuestionHandlingTypes.VisaSupport &&
                    (delegate.paymentStatus != PaymentStatus.Paid || !delegate.profile.isUsableForAssignment)
                ) {
                    add(VisaSupportNoticeIssueKey)
                }
                if (
                    question.handling == StandardQuestionHandlingTypes.CommitteeChoiceExplanation &&
                    delegate.profile.isUsableForAssignment
                ) {
                    question.rules.filterIsInstance<MinimumSentenceCount>().forEach { add(it.issueKey) }
                }
            }
            return question.issuesFor(rawAnswer).filterNot { it.key in irrelevantIssueKeys }
        }

    val openIssues: List<AnswerIssue>
        get() = issues.filter { issue -> delegate.resolutionFor(question.id, issue) == null }

    val resolvedIssues: List<ResolvedAnswerIssue>
        get() = issues.mapNotNull { issue ->
            delegate.resolutionFor(question.id, issue)?.let { resolution -> ResolvedAnswerIssue(issue, resolution) }
        }

    private companion object {
        /** Existing persisted key for the standard visa notice; custom validation remains applicable. */
        const val VisaSupportNoticeIssueKey = "Notice:Visa support requested."
    }
}
