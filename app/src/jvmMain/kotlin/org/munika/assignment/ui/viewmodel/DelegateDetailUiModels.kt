package org.munika.assignment.ui.viewmodel

import java.time.format.DateTimeFormatter
import org.munika.assignment.domain.model.AnswerIssueSeverity
import org.munika.assignment.domain.model.AssignmentStatus
import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.Delegate
import org.munika.assignment.domain.model.HighSchoolerStatus
import org.munika.assignment.domain.model.LanguageLevel
import org.munika.assignment.domain.model.MunExperienceLevel
import org.munika.assignment.domain.model.PaymentStatus
import org.munika.assignment.domain.model.Position
import org.munika.assignment.domain.model.Preference
import org.munika.assignment.domain.model.StandardQuestionRoles

data class DelegateDetailUiState(
    val id: String,
    val name: String,
    val active: Boolean,
    val paymentStatus: DelegateStatusChipUiState,
    val applicationStatus: DelegateStatusChipUiState?,
    val inactiveMarker: DelegateStatusChipUiState?,
    val highSchooler: Boolean,
    val isHeadDelegate: Boolean,
    val email: String?,
    val phone: String?,
    val address: String?,
    val nationality: String?,
    val age: DelegateAgeUiState?,
    val diet: String?,
    val experience: DelegateExperienceUiState?,
    val language: DelegateLanguageUiState?,
    val assignment: DelegateAssignmentUiState?,
    val canDeactivate: Boolean,
    val preferences: List<DelegatePreferenceUiState>,
    val answers: List<DelegateAnswerUiState>,
    val answerWarningCount: Int,
    val answerNoticeCount: Int,
    val comments: List<DelegateCommentUiState>,
    val delegation: DelegateDelegationUiState?,
)

data class DelegateStatusChipUiState(
    val label: String,
    val tone: DelegateStatusTone,
    val tooltip: String,
)

enum class DelegateStatusTone {
    Green,
    Red,
    Blue,
    Yellow,
    Neutral,
}

data class DelegateAgeUiState(
    val label: String,
    val dateOfBirthTooltip: String,
)

data class DelegateExperienceUiState(
    val label: String,
    val starCount: Int,
    val tooltip: String,
)

data class DelegateLanguageUiState(
    val label: String,
    val tone: DelegateStatusTone,
    val tooltip: String,
)

data class DelegateAssignmentUiState(
    val status: String,
    val label: String,
    val committeeId: String,
    val positionId: String,
    val tone: DelegateStatusTone,
)

data class DelegatePreferenceUiState(
    val rank: Int,
    val label: String,
    val committeeId: String,
    val positionId: String,
    val isCurrentAssignment: Boolean,
    val status: PreferenceSlotStatusUiState,
)

data class PreferenceSlotStatusUiState(
    val label: String,
    val detail: String?,
    val tone: DelegateStatusTone,
    val tooltip: String,
)

data class DelegateAnswerUiState(
    val label: String,
    val answer: String,
    val issues: List<DelegateAnswerIssueUiState>,
)

data class DelegateAnswerIssueUiState(
    val message: String,
    val severity: String,
    val isWarning: Boolean,
)

data class DelegateCommentUiState(
    val id: String,
    val body: String,
    val createdAt: String,
    val updatedAt: String,
)

data class DelegateDelegationUiState(
    val label: String,
    val tooltip: String,
)

fun Conference.toDelegateDetailUiState(delegateId: String?): DelegateDetailUiState? {
    val delegate = delegates.firstOrNull { it.id.value == delegateId } ?: return null
    val currentSlot = delegate.currentSlot
    val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    val displayAnswers = delegate.answers
        .filter { it.question.visibleInDelegateDetail }
        .filterNot { answer ->
            answer.question.role in setOf(
                StandardQuestionRoles.ExperienceLevel,
                StandardQuestionRoles.HighSchoolerStatus,
                StandardQuestionRoles.LanguageLevel,
            )
        }
        .map { answer ->
            DelegateAnswerUiState(
                label = answer.question.displayLabel,
                answer = answer.rawAnswer?.takeIf { it.isNotBlank() } ?: "No answer",
                issues = answer.issues.map { issue ->
                    DelegateAnswerIssueUiState(
                        message = issue.message,
                        severity = issue.severity.name,
                        isWarning = issue.severity == AnswerIssueSeverity.Warning,
                    )
                },
            )
        }
        .sortedBy { it.label }
    return DelegateDetailUiState(
        id = delegate.id.value,
        name = delegate.displayName,
        active = delegate.active,
        paymentStatus = delegate.paymentStatusChip(),
        applicationStatus = delegate.profile.applicationStatus?.takeIf { it.isNotBlank() }?.let {
            DelegateStatusChipUiState(
                label = it,
                tone = DelegateStatusTone.Blue,
                tooltip = "MyMUN application status.",
            )
        },
        inactiveMarker = if (delegate.active) {
            null
        } else {
            DelegateStatusChipUiState(
                label = "Deactivated",
                tone = DelegateStatusTone.Neutral,
                tooltip = "This delegate is hidden from assignment views until reactivated.",
            )
        },
        highSchooler = delegate.highSchoolerStatus == HighSchoolerStatus.Yes,
        isHeadDelegate = delegate.isHeadDelegate,
        email = delegate.email?.takeIf { it.isNotBlank() },
        phone = delegate.profile.phone?.takeIf { it.isNotBlank() },
        address = delegate.addressLabel(),
        nationality = delegate.profile.nationality?.takeIf { it.isNotBlank() },
        age = delegate.age()?.let { age ->
            DelegateAgeUiState(
                label = "$age years",
                dateOfBirthTooltip = delegate.profile.dateOfBirth
                    ?.format(dateFormatter)
                    ?.let { "Date of birth: $it" }
                    ?: "Age derived from imported date of birth.",
            )
        },
        diet = delegate.profile.diet?.takeIf { it.isNotBlank() },
        experience = delegate.experienceLevel?.toUiState(),
        language = delegate.languageLevel?.toUiState(),
        assignment = currentSlot?.let { slot ->
            DelegateAssignmentUiState(
                status = when (delegate.assignmentStatus) {
                    AssignmentStatus.Empty -> "Unassigned"
                    AssignmentStatus.Pending -> "Pending"
                    AssignmentStatus.Assigned -> "Confirmed"
                },
                label = "${slot.position.committee.displayName} (${slot.position.name}) slot ${slot.index}",
                committeeId = slot.position.committee.id.value,
                positionId = slot.position.id.value,
                tone = when (delegate.assignmentStatus) {
                    AssignmentStatus.Empty -> DelegateStatusTone.Neutral
                    AssignmentStatus.Pending -> DelegateStatusTone.Yellow
                    AssignmentStatus.Assigned -> DelegateStatusTone.Blue
                },
            )
        },
        canDeactivate = currentSlot?.isAssigned != true,
        preferences = delegate.preferences
            .sortedBy { it.rank }
            .map { preference -> preference.toUiState(currentSlot?.position) },
        answers = displayAnswers,
        answerWarningCount = displayAnswers.sumOf { answer -> answer.issues.count { it.isWarning } },
        answerNoticeCount = displayAnswers.sumOf { answer -> answer.issues.count { !it.isWarning } },
        comments = delegate.comments
            .sortedWith(compareBy<org.munika.assignment.domain.model.DelegateComment> { it.createdAt }.thenBy { it.id.value })
            .map { comment ->
                DelegateCommentUiState(
                    id = comment.id.value,
                    body = comment.body,
                    createdAt = comment.createdAt.toString(),
                    updatedAt = comment.updatedAt.toString(),
                )
            },
        delegation = delegate.delegation?.let { delegation ->
            DelegateDelegationUiState(
                label = delegation.displayLabel,
                tooltip = "Delegation context from MyMUN. Delegation overview is planned as a later view.",
            )
        },
    )
}

private fun MunExperienceLevel.toUiState(): DelegateExperienceUiState =
    DelegateExperienceUiState(
        label = when (this) {
            MunExperienceLevel.FirstTime -> "First time"
            MunExperienceLevel.Beginner -> "Beginner"
            MunExperienceLevel.Intermediate -> "Intermediate"
            MunExperienceLevel.Advanced -> "Advanced"
        },
        starCount = when (this) {
            MunExperienceLevel.FirstTime -> 1
            MunExperienceLevel.Beginner -> 2
            MunExperienceLevel.Intermediate -> 3
            MunExperienceLevel.Advanced -> 4
        },
        tooltip = "Typed MUN experience derived from the configured experience question.",
    )

private fun LanguageLevel.toUiState(): DelegateLanguageUiState =
    DelegateLanguageUiState(
        label = when (this) {
            LanguageLevel.BeginnerA1A2 -> "A1/A2"
            LanguageLevel.IndependentUserB1B2 -> "B1/B2"
            LanguageLevel.ProficientUserC1C2 -> "C1/C2"
        },
        tone = if (this == LanguageLevel.BeginnerA1A2) DelegateStatusTone.Red else DelegateStatusTone.Green,
        tooltip = "Typed language level derived from the configured language question.",
    )

private fun Delegate.paymentStatusChip(): DelegateStatusChipUiState =
    when (paymentStatus) {
        PaymentStatus.Paid -> DelegateStatusChipUiState(
            label = "Paid",
            tone = DelegateStatusTone.Green,
            tooltip = "Payment status from MyMUN: paid.",
        )
        PaymentStatus.Unpaid -> DelegateStatusChipUiState(
            label = "Unpaid",
            tone = DelegateStatusTone.Red,
            tooltip = "Payment status from MyMUN: unpaid.",
        )
    }

private fun Delegate.addressLabel(): String? {
    val cityLine = listOfNotNull(
        profile.zipCode?.takeIf { it.isNotBlank() },
        profile.city?.takeIf { it.isNotBlank() },
    ).joinToString(" ").takeIf { it.isNotBlank() }
    return listOfNotNull(
        profile.country?.takeIf { it.isNotBlank() },
        cityLine,
        profile.streetAddress?.takeIf { it.isNotBlank() },
    ).joinToString(", ").takeIf { it.isNotBlank() }
}

private fun Preference.toUiState(currentPosition: Position?): DelegatePreferenceUiState =
    DelegatePreferenceUiState(
        rank = rank,
        label = "${position.committee.displayName} (${position.name})",
        committeeId = position.committee.id.value,
        positionId = position.id.value,
        isCurrentAssignment = position === currentPosition,
        status = position.preferenceStatus(),
    )

private fun Position.preferenceStatus(): PreferenceSlotStatusUiState {
    val occupiedSlot = slots.firstOrNull { it.delegate != null }
    return when {
        occupiedSlot == null -> PreferenceSlotStatusUiState(
            label = "Open",
            detail = null,
            tone = DelegateStatusTone.Green,
            tooltip = "No delegate is assigned to this position.",
        )
        occupiedSlot.isPending -> PreferenceSlotStatusUiState(
            label = "Pending",
            detail = "Pending: ${occupiedSlot.delegate?.displayName.orEmpty()}",
            tone = DelegateStatusTone.Yellow,
            tooltip = "This position has a pending MunAssign assignment.",
        )
        occupiedSlot.isAssigned -> PreferenceSlotStatusUiState(
            label = "Confirmed",
            detail = "Assigned: ${occupiedSlot.delegate?.displayName.orEmpty()}",
            tone = DelegateStatusTone.Blue,
            tooltip = "This position has a MyMUN-confirmed assignment.",
        )
        else -> PreferenceSlotStatusUiState(
            label = "Occupied",
            detail = occupiedSlot.delegate?.displayName,
            tone = DelegateStatusTone.Neutral,
            tooltip = "This position is occupied.",
        )
    }
}
