package org.munika.assignment.ui.viewmodel

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.munika.assignment.domain.model.AnswerIssueSeverity
import org.munika.assignment.domain.model.AssignmentStatus
import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.Delegate
import org.munika.assignment.domain.model.PaymentStatus
import org.munika.assignment.domain.model.Position

data class DelegateListControlsUiState(
    val searchQuery: String = "",
    val includeUnpaid: Boolean = false,
    val includeDeactivated: Boolean = false,
    val issuesOnly: Boolean = false,
    val includedAssignmentStatuses: Set<DelegateListAssignmentStatus> = DelegateListAssignmentStatus.entries.toSet(),
    val includedSchoolLevels: Set<DelegateListSchoolLevel> = DelegateListSchoolLevel.entries.toSet(),
    val committeeId: String? = null,
    val sort: DelegateListSort = DelegateListSort.ApplicationSubmitted,
)

enum class DelegateListPreset {
    Default,
    Assignment,
    Issues,
    Assigned,
    All,
}

enum class DelegateListSort {
    ApplicationSubmitted,
    Name,
    Difficulty,
    AssignmentStatus,
    Issues,
}

enum class DelegateListAssignmentStatus {
    Unassigned,
    Pending,
    Confirmed,
}

enum class DelegateListSchoolLevel {
    HighSchool,
    University,
}

data class DelegateListUiState(
    val rows: List<DelegateListRowUiState>,
    val committees: List<DelegateListCommitteeFilterUiState>,
)

data class DelegateListCommitteeFilterUiState(
    val id: String,
    val name: String,
)

data class DelegateListRowUiState(
    val id: String,
    val name: String,
    val isHighSchooler: Boolean,
    val assignmentStatus: DelegateListAssignmentStatus,
    val assignmentStatusLabel: String,
    val paymentActiveLabel: String,
    val paymentActiveTone: DelegateStatusTone,
    val warningCount: Int,
    val noticeCount: Int,
    val applicationAgeLabel: String,
    val submittedAt: Instant?,
    val delegationName: String?,
    val quickNavigation: DelegateListNavigationTargetUiState?,
)

data class DelegateListNavigationTargetUiState(
    val committeeId: String,
    val positionId: String,
    val tooltip: String,
)

fun Conference.toDelegateListUiState(
    controls: DelegateListControlsUiState,
    now: Instant = Instant.now(),
): DelegateListUiState {
    val difficultyScores = delegateDifficultyScores()
    val today = now.atZone(ZoneId.systemDefault()).toLocalDate()
    val rows = delegates
        .filter { it.matchesControls(controls, today) }
        .map { delegate -> delegate.toDelegateListRowUiState(now, today) }
        .sortedWith(delegateListComparator(controls.sort, difficultyScores))
    return DelegateListUiState(
        rows = rows,
        committees = committees.map { committee ->
            DelegateListCommitteeFilterUiState(
                id = committee.id.value,
                name = committee.displayName,
            )
        },
    )
}

fun DelegateListControlsUiState.applyPreset(preset: DelegateListPreset): DelegateListControlsUiState =
    when (preset) {
        DelegateListPreset.Default -> DelegateListControlsUiState()
        DelegateListPreset.Assignment -> DelegateListControlsUiState(
            includedAssignmentStatuses = setOf(DelegateListAssignmentStatus.Unassigned),
            sort = DelegateListSort.Difficulty,
        )
        DelegateListPreset.Issues -> DelegateListControlsUiState(
            issuesOnly = true,
            sort = DelegateListSort.Issues,
        )
        DelegateListPreset.Assigned -> DelegateListControlsUiState(
            includedAssignmentStatuses = setOf(DelegateListAssignmentStatus.Pending, DelegateListAssignmentStatus.Confirmed),
            sort = DelegateListSort.AssignmentStatus,
        )
        DelegateListPreset.All -> DelegateListControlsUiState(
            includeUnpaid = true,
            includeDeactivated = true,
        )
    }

private fun Delegate.matchesControls(controls: DelegateListControlsUiState, today: java.time.LocalDate): Boolean {
    if (!controls.includeUnpaid && paymentStatus != PaymentStatus.Paid) return false
    if (!controls.includeDeactivated && !active) return false
    if (toDelegateListAssignmentStatus() !in controls.includedAssignmentStatuses) return false
    if (toDelegateListSchoolLevel() !in controls.includedSchoolLevels) return false
    if (controls.searchQuery.isNotBlank() && !displayName.contains(controls.searchQuery.trim(), ignoreCase = true)) return false
    val committeeId = controls.committeeId
    if (committeeId != null && preferences.none { it.position.committee.id.value == committeeId }) return false
    if (controls.issuesOnly && issueCount(today) == 0) return false
    return true
}

private fun Delegate.toDelegateListRowUiState(now: Instant, today: java.time.LocalDate): DelegateListRowUiState {
    val currentSlot = currentSlot
    val assignmentStatus = toDelegateListAssignmentStatus()
    return DelegateListRowUiState(
        id = id.value,
        name = displayName,
        isHighSchooler = isHighSchooler,
        assignmentStatus = assignmentStatus,
        assignmentStatusLabel = when (assignmentStatus) {
            DelegateListAssignmentStatus.Unassigned -> "Unassigned"
            DelegateListAssignmentStatus.Pending -> "Pending"
            DelegateListAssignmentStatus.Confirmed -> "Confirmed"
        },
        paymentActiveLabel = paymentActiveLabel(),
        paymentActiveTone = paymentActiveTone(),
        warningCount = warningCount(today),
        noticeCount = noticeCount(today),
        applicationAgeLabel = applicationAgeLabel(now),
        submittedAt = submittedAt,
        delegationName = delegation?.displayLabel,
        quickNavigation = currentSlot?.takeIf { it.isPending || it.isAssigned }?.let { slot ->
            DelegateListNavigationTargetUiState(
                committeeId = slot.position.committee.id.value,
                positionId = slot.position.id.value,
                tooltip = "Go to ${slot.position.committee.displayName} (${slot.position.name}).",
            )
        },
    )
}

private fun Delegate.toDelegateListAssignmentStatus(): DelegateListAssignmentStatus =
    when (assignmentStatus) {
        AssignmentStatus.Empty -> DelegateListAssignmentStatus.Unassigned
        AssignmentStatus.Pending -> DelegateListAssignmentStatus.Pending
        AssignmentStatus.Assigned -> DelegateListAssignmentStatus.Confirmed
    }

private fun Delegate.toDelegateListSchoolLevel(): DelegateListSchoolLevel =
    if (isHighSchooler) DelegateListSchoolLevel.HighSchool else DelegateListSchoolLevel.University

private fun Delegate.paymentActiveLabel(): String =
    when {
        !active && paymentStatus == PaymentStatus.Unpaid -> "Inactive unpaid"
        !active -> "Inactive"
        paymentStatus == PaymentStatus.Unpaid -> "Unpaid"
        else -> "Paid"
    }

private fun Delegate.paymentActiveTone(): DelegateStatusTone =
    when {
        !active -> DelegateStatusTone.Neutral
        paymentStatus == PaymentStatus.Unpaid -> DelegateStatusTone.Red
        else -> DelegateStatusTone.Green
    }

private fun Delegate.warningCount(today: java.time.LocalDate): Int =
    answers.sumOf { answer -> answer.openIssues.count { it.severity == AnswerIssueSeverity.Warning } } +
        openProfileIssues(today).count { it.severity == AnswerIssueSeverity.Warning }

private fun Delegate.noticeCount(today: java.time.LocalDate): Int =
    answers.sumOf { answer -> answer.openIssues.count { it.severity == AnswerIssueSeverity.Notice } } +
        openProfileIssues(today).count { it.severity == AnswerIssueSeverity.Notice }

private fun Delegate.issueCount(today: java.time.LocalDate): Int = warningCount(today) + noticeCount(today)

private fun Delegate.applicationAgeLabel(now: Instant): String {
    val submitted = submittedAt ?: return "No submission"
    val days = ChronoUnit.DAYS.between(submitted, now).coerceAtLeast(0)
    return when (days) {
        0L -> "Today"
        1L -> "1 day"
        else -> "$days days"
    }
}

private fun Conference.delegateDifficultyScores(): Map<String, Int> {
    val activePaidDelegates = delegates.filter { it.active && it.paymentStatus == PaymentStatus.Paid }
    val firstAvailablePreferenceByDelegate = activePaidDelegates.associateWith { delegate ->
        delegate.preferences
            .sortedBy { it.rank }
            .firstOrNull { preference -> preference.position.hasOpenSlot() }
            ?.position
    }
    return activePaidDelegates.associate { delegate ->
        val position = firstAvailablePreferenceByDelegate[delegate]
        val score = if (position == null) {
            positions.size + 1
        } else {
            firstAvailablePreferenceByDelegate.count { (otherDelegate, otherPosition) ->
                otherDelegate !== delegate && otherPosition === position
            }
        }
        delegate.id.value to score
    }
}

private fun Position.hasOpenSlot(): Boolean =
    slots.any { it.status == AssignmentStatus.Empty }

private fun delegateListComparator(
    sort: DelegateListSort,
    difficultyScores: Map<String, Int>,
): Comparator<DelegateListRowUiState> =
    when (sort) {
        DelegateListSort.ApplicationSubmitted -> compareBy<DelegateListRowUiState> { it.submittedAt ?: Instant.MAX }
            .thenBy { it.name }
        DelegateListSort.Name -> compareBy { it.name }
        DelegateListSort.Difficulty -> compareBy<DelegateListRowUiState> { difficultyScores[it.id] ?: Int.MAX_VALUE }
            .thenBy { it.name }
        DelegateListSort.AssignmentStatus -> compareBy<DelegateListRowUiState> { it.assignmentStatus.sortRank }
            .thenBy { it.name }
        DelegateListSort.Issues -> compareByDescending<DelegateListRowUiState> { it.warningCount }
            .thenByDescending { it.noticeCount }
            .thenBy { it.name }
    }

private val DelegateListAssignmentStatus.sortRank: Int
    get() = when (this) {
        DelegateListAssignmentStatus.Confirmed -> 0
        DelegateListAssignmentStatus.Pending -> 1
        DelegateListAssignmentStatus.Unassigned -> 2
    }
