package org.munika.assignment.domain.model

import java.time.Instant

/**
 * Organizer-managed resolution for a derived answer or delegate-level issue.
 *
 * Question-backed issues use [questionId]. Delegate-level checks use `null` and are matched by
 * delegate plus issue key.
 */
data class AnswerIssueResolution(
    override val id: AnswerIssueResolutionId,
    val delegate: Delegate,
    val questionId: QuestionId?,
    val issueKey: String,
    val issueMessage: String,
    val severity: AnswerIssueSeverity,
    val justification: String?,
    val resolvedAt: Instant,
) : DomainObject<AnswerIssueResolutionId>

/**
 * Derived answer issue paired with the organizer resolution that closes it.
 */
data class ResolvedAnswerIssue(
    val issue: AnswerIssue,
    val resolution: AnswerIssueResolution,
)
