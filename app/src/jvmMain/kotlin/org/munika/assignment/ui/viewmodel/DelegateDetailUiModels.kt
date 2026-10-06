package org.munika.assignment.ui.viewmodel

import java.time.format.DateTimeFormatter
import org.munika.assignment.domain.model.AnswerIssueSeverity
import org.munika.assignment.domain.model.AssignmentStatus
import org.munika.assignment.domain.model.CommitteeMode
import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.Delegate
import org.munika.assignment.domain.model.LanguageLevel
import org.munika.assignment.domain.model.MunExperienceLevel
import org.munika.assignment.domain.model.PaymentStatus
import org.munika.assignment.domain.model.PaymentTier
import org.munika.assignment.domain.model.Position
import org.munika.assignment.domain.model.Preference
import org.munika.assignment.domain.model.PreferenceSource
import org.munika.assignment.domain.model.ResolvedAnswerIssue
import org.munika.assignment.domain.model.StandardQuestionRoles

data class DelegateDetailUiState(
    val id: String,
    val name: String,
    val active: Boolean,
    val paymentStatus: DelegateStatusChipUiState,
    val paymentTier: PaymentTier?,
    val paymentTierStatus: DelegateStatusChipUiState?,
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
    val assignmentOptions: DelegateAssignmentOptionsUiState?,
    val hasPreferenceCorrections: Boolean,
    val answers: List<DelegateAnswerUiState>,
    val profileIssues: List<DelegateAnswerIssueUiState>,
    val resolvedProfileIssues: List<DelegateResolvedIssueUiState>,
    val profileWarningCount: Int,
    val profileNoticeCount: Int,
    val resolvedProfileIssueCount: Int,
    val answerWarningCount: Int,
    val answerNoticeCount: Int,
    val resolvedAnswerIssueCount: Int,
    val comments: List<DelegateCommentUiState>,
    val delegation: DelegateDelegationUiState?,
    val reservation: DelegateReservationUiState?,
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
    Notice,
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
    val flagAssetName: String?,
    val committeeId: String,
    val positionId: String,
    val tone: DelegateStatusTone,
)

data class DelegatePreferenceUiState(
    val rank: Int,
    val label: String,
    val flagAssetName: String?,
    val committeeId: String,
    val positionId: String,
    val isCurrentAssignment: Boolean,
    val status: PreferenceSlotStatusUiState,
    val openSlotId: String?,
    val openSlotCount: Int,
    val replaceablePendingSlots: List<DelegatePendingSlotUiState>,
    val organizerCompleted: Boolean = false,
    val organizerReordered: Boolean = false,
) {
    val canAssign: Boolean = openSlotId != null || replaceablePendingSlots.isNotEmpty()
}

data class DelegatePendingSlotUiState(
    val id: String,
    val index: Int,
    val delegateName: String,
)

data class DelegateAssignmentOptionsUiState(
    val showAssignOther: Boolean,
    val committees: List<DelegateAssignmentCommitteeUiState>,
)

data class DelegateAssignmentCommitteeUiState(
    val id: String,
    val name: String,
    val preferenceRanks: List<Int>,
    val hasFullyFittingOpenPosition: Boolean,
    val positions: List<DelegateAssignmentPositionUiState>,
)

data class DelegateAssignmentPositionUiState(
    val id: String,
    val label: String,
    val flagAssetName: String?,
    val openSlotId: String,
    val openSlotCount: Int,
    val fullyFits: Boolean,
    val paymentTierMismatch: PaymentTierMismatchUiState?,
    val paymentTierKnown: Boolean,
    val schoolFit: DelegateSchoolFit,
)

enum class DelegateSchoolFit {
    Match,
    Mismatch,
    Unknown,
}

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
    val resolvedIssues: List<DelegateResolvedIssueUiState>,
)

data class DelegateAnswerIssueUiState(
    val questionId: String?,
    val issueKey: String,
    val message: String,
    val severity: String,
    val isWarning: Boolean,
)

data class DelegateResolvedIssueUiState(
    val questionId: String?,
    val resolutionId: String,
    val issueKey: String,
    val message: String,
    val severity: String,
    val isWarning: Boolean,
    val justification: String?,
)

data class DelegateCommentUiState(
    val id: String,
    val body: String,
    val createdAt: String,
    val updatedAt: String,
    val readOnly: Boolean = false,
    val issueResolutionId: String? = null,
)

data class DelegateDelegationUiState(
    val label: String,
    val tooltip: String,
)

data class DelegateReservationUiState(
    val id: String,
    val name: String,
    val sourceLabel: String,
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
        .sortedWith(compareBy<org.munika.assignment.domain.model.QuestionAnswer> { it.question.displayOrder }.thenBy { it.question.questionText })
        .map { answer ->
            DelegateAnswerUiState(
                label = answer.question.displayLabel,
                answer = answer.rawAnswer?.takeIf { it.isNotBlank() } ?: "No answer",
                issues = answer.openIssues.map { issue ->
                    DelegateAnswerIssueUiState(
                        questionId = answer.question.id.value,
                        issueKey = issue.key,
                        message = issue.message,
                        severity = issue.severity.name,
                        isWarning = issue.severity == AnswerIssueSeverity.Warning,
                    )
                },
                resolvedIssues = answer.resolvedIssues.map { resolved ->
                    resolved.toDelegateResolvedIssueUiState(answer.question.id.value)
                },
            )
        }
    val profileIssues = delegate.openProfileIssues().map { issue ->
        DelegateAnswerIssueUiState(
            questionId = null,
            issueKey = issue.key,
            message = issue.message,
            severity = issue.severity.name,
            isWarning = issue.severity == AnswerIssueSeverity.Warning,
        )
    }
    val resolvedProfileIssues = delegate.resolvedProfileIssues().map { resolved ->
        resolved.toDelegateResolvedIssueUiState(questionId = null)
    }
    val commentRows = delegate.comments
        .sortedWith(compareBy<org.munika.assignment.domain.model.DelegateComment> { it.createdAt }.thenBy { it.id.value })
        .map { comment ->
            DelegateCommentUiState(
                id = comment.id.value,
                body = comment.body,
                createdAt = comment.createdAt.toString(),
                updatedAt = comment.updatedAt.toString(),
            )
        } + delegate.issueResolutions
        .filter { resolution -> resolution.justification != null }
        .sortedWith(compareBy { it.resolvedAt })
        .map { resolution ->
            DelegateCommentUiState(
                id = "issue-resolution-${resolution.id.value}",
                body = resolution.commentBody(),
                createdAt = resolution.resolvedAt.toString(),
                updatedAt = resolution.resolvedAt.toString(),
                readOnly = true,
                issueResolutionId = resolution.id.value,
            )
        }
    return DelegateDetailUiState(
        id = delegate.id.value,
        name = delegate.displayName,
        active = delegate.active,
        paymentStatus = delegate.paymentStatusChip(),
        paymentTier = delegate.paymentTier,
        paymentTierStatus = if (delegate.paymentStatus == PaymentStatus.Paid) {
            when (delegate.paymentTier) {
                PaymentTier.Full -> DelegateStatusChipUiState(
                    label = "Full tier",
                    tone = DelegateStatusTone.Blue,
                    tooltip = "Organizer-recorded full payment tier. Click to change it.",
                )
                PaymentTier.Reduced -> DelegateStatusChipUiState(
                    label = "Reduced tier",
                    tone = DelegateStatusTone.Notice,
                    tooltip = "Organizer-recorded reduced payment tier. Click to change it.",
                )
                null -> DelegateStatusChipUiState(
                    label = "Tier unassigned",
                    tone = DelegateStatusTone.Yellow,
                    tooltip = "Choose the delegate's Full or Reduced payment tier.",
                )
            }
        } else {
            null
        },
        applicationStatus = delegate.profile.applicationStatus?.takeIf { it.isNotBlank() }?.let {
            DelegateStatusChipUiState(
                label = it,
                tone = DelegateStatusTone.Blue,
                tooltip = "Application status reported by the latest update.",
            )
        },
        inactiveMarker = if (delegate.active) {
            null
        } else {
            DelegateStatusChipUiState(
                label = "Deactivated",
                tone = DelegateStatusTone.Neutral,
                tooltip = "This delegate is unavailable for assignment and management. Reactivate them from the All view in the delegate list.",
            )
        },
        highSchooler = delegate.isHighSchooler,
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
                    ?: "Age from the application date of birth.",
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
                flagAssetName = slot.position.countryFlagAssetName,
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
        assignmentOptions = delegate.toAssignmentOptionsUiState(currentSlot),
        hasPreferenceCorrections = delegate.hasPreferenceCorrections,
        answers = displayAnswers,
        profileIssues = profileIssues,
        resolvedProfileIssues = resolvedProfileIssues,
        profileWarningCount = profileIssues.count { it.isWarning },
        profileNoticeCount = profileIssues.count { !it.isWarning },
        resolvedProfileIssueCount = resolvedProfileIssues.size,
        answerWarningCount = displayAnswers.sumOf { answer -> answer.issues.count { it.isWarning } },
        answerNoticeCount = displayAnswers.sumOf { answer -> answer.issues.count { !it.isWarning } },
        resolvedAnswerIssueCount = displayAnswers.sumOf { answer -> answer.resolvedIssues.size },
        comments = commentRows,
        delegation = delegate.delegation?.let { delegation ->
            DelegateDelegationUiState(
                label = delegation.displayLabel,
                tooltip = "Delegation associated with this delegate in the latest update.",
            )
        },
        reservation = delegate.reservationLink?.let { link ->
            DelegateReservationUiState(
                id = link.reservation.id.value,
                name = link.reservation.name,
                sourceLabel = requireNotNull(delegate.reservationSourceLabel()),
            )
        },
    )
}

private fun ResolvedAnswerIssue.toDelegateResolvedIssueUiState(questionId: String?): DelegateResolvedIssueUiState =
    DelegateResolvedIssueUiState(
        questionId = questionId,
        resolutionId = resolution.id.value,
        issueKey = issue.key,
        message = issue.message,
        severity = issue.severity.name,
        isWarning = issue.severity == AnswerIssueSeverity.Warning,
        justification = resolution.justification,
    )

private fun org.munika.assignment.domain.model.AnswerIssueResolution.commentBody(): String =
    buildString {
        append("Resolved ")
        append("\"")
        append(issueMessage.trimEnd('.', '!', '?'))
        append("\"")
        justification?.takeIf { it.isNotBlank() }?.let { text ->
            append(". Justification: ")
            append(text)
        }
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
        tooltip = "Experience level from the delegate's application answers.",
    )

private fun LanguageLevel.toUiState(): DelegateLanguageUiState =
    DelegateLanguageUiState(
        label = when (this) {
            LanguageLevel.BeginnerA1A2 -> "A1/A2"
            LanguageLevel.IndependentUserB1B2 -> "B1/B2"
            LanguageLevel.ProficientUserC1C2 -> "C1/C2"
        },
        tone = if (this == LanguageLevel.BeginnerA1A2) DelegateStatusTone.Red else DelegateStatusTone.Green,
        tooltip = "Language level from the delegate's application answers.",
    )

private fun Delegate.paymentStatusChip(): DelegateStatusChipUiState =
    when (paymentStatus) {
        PaymentStatus.Paid -> DelegateStatusChipUiState(
            label = "Paid",
            tone = DelegateStatusTone.Green,
            tooltip = "Payment is marked as paid in the latest update.",
        )
        PaymentStatus.Unpaid -> DelegateStatusChipUiState(
            label = "Unpaid",
            tone = DelegateStatusTone.Red,
            tooltip = "Payment is marked as unpaid in the latest update.",
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
        flagAssetName = position.countryFlagAssetName,
        committeeId = position.committee.id.value,
        positionId = position.id.value,
        isCurrentAssignment = position === currentPosition,
        status = position.preferenceStatus(),
        openSlotId = position.slots.filter { it.isEmpty }.minByOrNull { it.index }?.id?.value,
        openSlotCount = position.slots.count { it.isEmpty },
        replaceablePendingSlots = position.slots
            .filter { it.isPending }
            .sortedBy { it.index }
            .map { slot ->
                DelegatePendingSlotUiState(
                    id = slot.id.value,
                    index = slot.index,
                    delegateName = requireNotNull(slot.delegate).displayName,
                )
            },
        organizerCompleted = source == PreferenceSource.OrganizerCompletion,
        organizerReordered = source == PreferenceSource.OrganizerCorrection,
    )

private fun Position.preferenceStatus(): PreferenceSlotStatusUiState {
    val openSlotCount = slots.count { it.isEmpty }
    val pendingSlots = slots.filter { it.isPending }
    val confirmedSlots = slots.filter { it.isAssigned }
    return when {
        openSlotCount > 0 -> PreferenceSlotStatusUiState(
            label = "Open",
            detail = if (slots.size > 1) "$openSlotCount of ${slots.size} slots open" else null,
            tone = DelegateStatusTone.Green,
            tooltip = if (openSlotCount == slots.size) {
                "No delegate is assigned to this position."
            } else {
                "$openSlotCount of ${slots.size} slots are open."
            },
        )
        pendingSlots.isNotEmpty() -> PreferenceSlotStatusUiState(
            label = "Pending",
            detail = pendingSlots.joinToString(prefix = "Pending: ") { requireNotNull(it.delegate).displayName },
            tone = DelegateStatusTone.Yellow,
            tooltip = if (confirmedSlots.isEmpty()) {
                "Every slot has a pending assignment."
            } else {
                "Every slot is occupied; ${pendingSlots.size} pending assignment${if (pendingSlots.size == 1) " is" else "s are"} replaceable."
            },
        )
        confirmedSlots.size == slots.size -> PreferenceSlotStatusUiState(
            label = "Confirmed",
            detail = confirmedSlots.joinToString(prefix = "Assigned: ") { requireNotNull(it.delegate).displayName },
            tone = DelegateStatusTone.Blue,
            tooltip = "Every slot has a MyMUN-confirmed assignment.",
        )
        else -> PreferenceSlotStatusUiState(
            label = "Occupied",
            detail = slots.mapNotNull { it.delegate?.displayName }.joinToString(),
            tone = DelegateStatusTone.Neutral,
            tooltip = "This position is occupied.",
        )
    }
}

private fun Delegate.toAssignmentOptionsUiState(currentSlot: org.munika.assignment.domain.model.Slot?): DelegateAssignmentOptionsUiState? {
    if (!isAssignmentEligible || currentSlot != null) return null

    val preferenceRanksByCommittee = preferences
        .groupBy { it.position.committee }
        .mapValues { (_, preferences) -> preferences.map { it.rank }.sorted() }
    val committees = conference.committees.mapNotNull { committee ->
        val positions = committee.positions.mapNotNull { position ->
            val openSlots = position.slots.filter { it.isEmpty }.sortedBy { it.index }
            val openSlot = openSlots.firstOrNull() ?: return@mapNotNull null
            val schoolFit = schoolFitFor(position)
            val fullyFits = paymentTier == committee.paymentTier && schoolFit == DelegateSchoolFit.Match
            DelegateAssignmentPositionUiState(
                id = position.id.value,
                label = position.displayName,
                flagAssetName = position.countryFlagAssetName,
                openSlotId = openSlot.id.value,
                openSlotCount = openSlots.size,
                fullyFits = fullyFits,
                paymentTierMismatch = paymentTierMismatchFor(committee),
                paymentTierKnown = paymentTier != null,
                schoolFit = schoolFit,
            )
        }.sortedWith(
            compareByDescending<DelegateAssignmentPositionUiState> { it.fullyFits }
                .thenBy { it.label.lowercase() }
                .thenBy { it.label },
        )
        if (positions.isEmpty()) return@mapNotNull null
        DelegateAssignmentCommitteeUiState(
            id = committee.id.value,
            name = committee.displayName,
            preferenceRanks = preferenceRanksByCommittee[committee].orEmpty(),
            hasFullyFittingOpenPosition = positions.any { it.fullyFits },
            positions = positions,
        )
    }.sortedWith(
        compareByDescending<DelegateAssignmentCommitteeUiState> { it.hasFullyFittingOpenPosition }
            .thenBy { it.preferenceRanks.firstOrNull() ?: Int.MAX_VALUE }
            .thenBy { it.name.lowercase() }
            .thenBy { it.name },
    )

    return DelegateAssignmentOptionsUiState(
        showAssignOther = preferences.none { preference -> preference.position.slots.any { it.isEmpty } },
        committees = committees,
    )
}

private fun Delegate.schoolFitFor(position: Position): DelegateSchoolFit =
    when (position.effectiveCommitteeMode) {
        CommitteeMode.HighSchool -> if (isHighSchooler) DelegateSchoolFit.Match else DelegateSchoolFit.Mismatch
        CommitteeMode.University -> if (isHighSchooler) DelegateSchoolFit.Mismatch else DelegateSchoolFit.Match
        CommitteeMode.Mapped, null -> DelegateSchoolFit.Unknown
    }
