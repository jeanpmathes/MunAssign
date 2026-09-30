package org.munika.assignment.ui.viewmodel

import org.munika.assignment.domain.model.AnswerIssueSeverity
import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.Delegate
import org.munika.assignment.domain.model.QuestionAnswer
import org.munika.assignment.domain.model.ResolvedAnswerIssue

data class IssueManagerUiState(
    val openWarningCount: Int,
    val openNoticeCount: Int,
    val issues: List<IssueUiState>,
    val filteredDelegateName: String? = null,
) {
    val notices: List<IssueUiState>
        get() = issues
}

typealias NoticeManagerUiState = IssueManagerUiState

data class IssueUiState(
    val delegateId: String,
    val delegateName: String,
    val delegateIsHighSchooler: Boolean,
    val questionId: String?,
    val resolutionId: String?,
    val validationLabel: String,
    val questionText: String?,
    val answerText: String?,
    val reasonText: String,
    val justificationText: String?,
    val issueKey: String,
    val severity: String,
    val isWarning: Boolean,
    val resolved: Boolean,
    val canResolve: Boolean,
    val canAssignPaymentTier: Boolean,
) {
    val questionLabel: String
        get() = questionText ?: validationLabel
    val answer: String
        get() = answerText.orEmpty()
    val message: String
        get() = reasonText
    val justification: String?
        get() = justificationText
}

typealias NoticeUiState = IssueUiState

fun Conference.toIssueManagerUiState(delegateId: String? = null): IssueManagerUiState {
    val filteredDelegate = delegateId?.let { id ->
        delegates.firstOrNull { delegate -> delegate.id.value == id }
    }
    val issues = delegates
        .filter { delegate -> delegateId == null || delegate.id.value == delegateId }
        .flatMap { delegate -> delegate.issueRows() }
        .sortedWith(
            compareBy<IssueUiState> { it.resolved }
                .thenByDescending { it.isWarning }
                .thenBy { it.delegateName }
                .thenBy { it.validationLabel }
                .thenBy { it.questionText },
        )
    return IssueManagerUiState(
        openWarningCount = issues.count { issue -> !issue.resolved && issue.isWarning },
        openNoticeCount = issues.count { issue -> !issue.resolved && !issue.isWarning },
        issues = issues,
        filteredDelegateName = filteredDelegate?.displayName,
    )
}

fun Conference.toNoticeManagerUiState(delegateId: String? = null): IssueManagerUiState =
    toIssueManagerUiState(delegateId)

private fun Delegate.issueRows(): List<IssueUiState> =
    answers.flatMap { answer -> answer.openIssueRows() + answer.resolvedIssueRows() } +
        openProfileIssues().map { issue ->
            IssueUiState(
                delegateId = id.value,
                delegateName = displayName,
                delegateIsHighSchooler = isHighSchooler,
                questionId = null,
                resolutionId = null,
                validationLabel = "Profile validation",
                questionText = null,
                answerText = null,
                issueKey = issue.key,
                reasonText = issue.message,
                severity = issue.severity.name,
                isWarning = issue.severity == AnswerIssueSeverity.Warning,
                resolved = false,
                justificationText = null,
                canResolve = true,
                canAssignPaymentTier = false,
            )
        } +
        resolvedProfileIssues().map { resolved ->
            resolved.toIssueUiState(
                delegate = this,
                questionId = null,
                validationLabel = "Profile validation",
                questionText = null,
                answerText = null,
            )
        } +
        openSchoolAssignmentIssues().map { issue ->
            IssueUiState(
                delegateId = id.value,
                delegateName = displayName,
                delegateIsHighSchooler = isHighSchooler,
                questionId = null,
                resolutionId = null,
                validationLabel = "Assignment validation",
                questionText = null,
                answerText = null,
                issueKey = issue.key,
                reasonText = issue.message,
                severity = issue.severity.name,
                isWarning = issue.severity == AnswerIssueSeverity.Warning,
                resolved = false,
                justificationText = null,
                canResolve = true,
                canAssignPaymentTier = false,
            )
        } +
        resolvedSchoolAssignmentIssues().map { resolved ->
            resolved.toIssueUiState(
                delegate = this,
                questionId = null,
                validationLabel = "Assignment validation",
                questionText = null,
                answerText = null,
            )
        } +
        openPaymentTierIssues().map { issue ->
            val isMissingTier = issue.key == Delegate.PaymentTierMissingIssueKey
            IssueUiState(
                delegateId = id.value,
                delegateName = displayName,
                delegateIsHighSchooler = isHighSchooler,
                questionId = null,
                resolutionId = null,
                validationLabel = "Payment tier",
                questionText = null,
                answerText = null,
                issueKey = issue.key,
                reasonText = issue.message,
                severity = issue.severity.name,
                isWarning = issue.severity == AnswerIssueSeverity.Warning,
                resolved = false,
                justificationText = null,
                canResolve = !isMissingTier,
                canAssignPaymentTier = isMissingTier,
            )
        } +
        resolvedPaymentTierIssues().map { resolved ->
            resolved.toIssueUiState(
                delegate = this,
                questionId = null,
                validationLabel = "Payment tier",
                questionText = null,
                answerText = null,
            )
        }

private fun QuestionAnswer.openIssueRows(): List<IssueUiState> =
    openIssues.map { issue ->
        IssueUiState(
            delegateId = delegate.id.value,
            delegateName = delegate.displayName,
            delegateIsHighSchooler = delegate.isHighSchooler,
            questionId = question.id.value,
            resolutionId = null,
            validationLabel = "Question validation",
            questionText = question.displayLabel,
            answerText = rawAnswer?.takeIf { it.isNotBlank() } ?: "No answer",
            issueKey = issue.key,
            reasonText = issue.message,
            severity = issue.severity.name,
            isWarning = issue.severity == AnswerIssueSeverity.Warning,
            resolved = false,
            justificationText = null,
            canResolve = true,
            canAssignPaymentTier = false,
        )
    }

private fun QuestionAnswer.resolvedIssueRows(): List<IssueUiState> =
    resolvedIssues.map { resolved ->
        resolved.toIssueUiState(
            delegate = delegate,
            questionId = question.id.value,
            validationLabel = "Question validation",
            questionText = question.displayLabel,
            answerText = rawAnswer?.takeIf { it.isNotBlank() } ?: "No answer",
        )
    }

private fun ResolvedAnswerIssue.toIssueUiState(
    delegate: Delegate,
    questionId: String?,
    validationLabel: String,
    questionText: String?,
    answerText: String?,
): IssueUiState =
    IssueUiState(
        delegateId = delegate.id.value,
        delegateName = delegate.displayName,
        delegateIsHighSchooler = delegate.isHighSchooler,
        questionId = questionId,
        resolutionId = resolution.id.value,
        validationLabel = validationLabel,
        questionText = questionText,
        answerText = answerText,
        issueKey = issue.key,
        reasonText = issue.message,
        severity = issue.severity.name,
        isWarning = issue.severity == AnswerIssueSeverity.Warning,
        resolved = true,
        justificationText = resolution.justification,
        canResolve = false,
        canAssignPaymentTier = false,
    )
