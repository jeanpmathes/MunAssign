package org.munika.assignment.ui.viewmodel

import java.time.LocalDate
import org.munika.assignment.domain.model.AnswerIssueSeverity
import org.munika.assignment.domain.model.AssignmentStatus
import org.munika.assignment.domain.model.Committee
import org.munika.assignment.domain.model.CommitteeMode
import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.Delegate
import org.munika.assignment.domain.model.DelegateId
import org.munika.assignment.domain.model.PaymentTier
import org.munika.assignment.domain.model.Position
import org.munika.assignment.domain.model.Reservation
import org.munika.assignment.domain.model.Slot
import org.munika.assignment.domain.model.StandardQuestionRoles

data class AssignmentBoardUiState(
    val conferenceId: String,
    val committees: List<CommitteeBoardUiState>,
)

data class CommitteeBoardUiState(
    val id: String,
    val name: String,
    val slotsPerPosition: Int,
    val mode: CommitteeMode,
    val paymentTier: PaymentTier,
    val isHighSchoolCommittee: Boolean,
    val openSlotCount: Int,
    val pendingSlotCount: Int,
    val assignedSlotCount: Int,
    val highSchoolerAssignedCount: Int,
    val universityAssignedCount: Int,
    val reservationReminders: List<ReservationReminderUiState>,
    val positions: List<PositionBoardUiState>,
) {
    val reservationReminder: ReservationReminderUiState? = reservationReminders.singleOrNull()
}

data class ReservationReminderUiState(
    val label: String,
    val totalCount: Int,
    val remainingCount: Int,
)

data class PositionBoardUiState(
    val id: String,
    val name: String,
    val displayName: String,
    val flagAssetName: String?,
    val effectiveMode: CommitteeMode?,
    val mappingIncomplete: Boolean,
    val mappedCommittee: PositionMappedCommitteeUiState?,
    val mappingOptions: List<CommitteeMappingOptionUiState>,
    val slots: List<SlotBoardUiState>,
    val interestedDelegates: List<InterestedDelegateUiState>,
    val assignmentCandidates: List<AssignmentDelegateCandidateUiState>,
    val versus: PositionVersusUiState?,
) {
    val occupiedSlotCount: Int = slots.count { it.status != SlotBoardStatus.Empty }
    val hasOccupiedSlots: Boolean = occupiedSlotCount > 0
    val shouldShowInterestedDelegates: Boolean = slots.any { it.status == SlotBoardStatus.Empty }
    val interestedDelegateCount: Int = interestedDelegates.size
}

data class PositionVersusUiState(
    val positionId: String,
    val positionName: String,
    val flagAssetName: String?,
    val requiredSelectionCount: Int,
    val candidates: List<PositionVersusCandidateUiState>,
    val rows: List<PositionVersusRowUiState>,
)

data class PositionVersusCandidateUiState(
    val id: String,
    val name: String,
    val pending: Boolean,
)

data class PositionVersusRowUiState(
    val id: String,
    val label: String,
    val valuesByDelegateId: Map<String, String>,
)

data class CommitteeMappingOptionUiState(
    val id: String,
    val name: String,
    val mode: CommitteeMode,
)

data class PositionMappedCommitteeUiState(
    val id: String,
    val name: String,
    val mode: CommitteeMode,
)

data class SlotBoardUiState(
    val id: String,
    val index: Int,
    val status: SlotBoardStatus,
    val delegateName: String?,
    val delegateId: String?,
    val delegateIsHighSchooler: Boolean,
    val matchesDelegatePreference: Boolean,
    val positionName: String,
    val paymentTierMismatch: PaymentTierMismatchUiState?,
)

data class InterestedDelegateUiState(
    val id: String,
    val name: String,
    val isHighSchooler: Boolean,
    val preferenceRank: Int,
    val pendingAssignmentLabel: String?,
    val pendingAssignmentSlotId: String?,
    val confirmedAssignmentLabel: String?,
    val confirmedAssignmentSlotId: String?,
) {
    val canAssign: Boolean = confirmedAssignmentSlotId == null
}

data class AssignmentDelegateCandidateUiState(
    val id: String,
    val name: String,
    val preferenceRank: Int?,
    val pendingAssignmentLabel: String?,
    val confirmedAssignmentLabel: String?,
    val isHighSchooler: Boolean,
    val warningCount: Int,
    val noticeCount: Int,
    val paymentTierMismatch: PaymentTierMismatchUiState?,
) {
    val canAssign: Boolean = confirmedAssignmentLabel == null
    val isPendingElsewhere: Boolean = pendingAssignmentLabel != null
}

data class PaymentTierMismatchUiState(
    val direction: PaymentTierMismatchDirection,
    val acknowledged: Boolean,
)

enum class PaymentTierMismatchDirection {
    ReducedIntoFull,
    FullIntoReduced,
}

enum class SlotBoardStatus {
    Empty,
    Pending,
    Assigned,
}

data class BoardScrollRequest(
    val committeeId: String,
    val positionId: String? = null,
    val requestId: Long = System.nanoTime(),
)

private data class DelegateIssueCounts(val warningCount: Int, val noticeCount: Int)

fun Conference.toAssignmentBoardUiState(today: LocalDate = LocalDate.now()): AssignmentBoardUiState {
    val slotByDelegateId: Map<DelegateId, Slot> = slots.mapNotNull { slot -> slot.delegate?.let { it.id to slot } }.toMap()
    val issueCountsByDelegateId: Map<DelegateId, DelegateIssueCounts> = delegates.associate { delegate ->
        delegate.id to DelegateIssueCounts(warningCount = delegate.warningCount(), noticeCount = delegate.noticeCount())
    }

    return AssignmentBoardUiState(
        conferenceId = id.value,
        committees = committees.map { committee ->
            val reservationReminders = when (committee.mode) {
                CommitteeMode.HighSchool -> listOfNotNull(reservations.toReservationReminderUiState(isHighSchool = true))
                CommitteeMode.University -> listOfNotNull(reservations.toReservationReminderUiState(isHighSchool = false))
                CommitteeMode.Mapped -> emptyList()
            }
            CommitteeBoardUiState(
                id = committee.id.value,
                name = committee.displayName,
                slotsPerPosition = committee.slotsPerPosition,
                mode = committee.mode,
                paymentTier = committee.paymentTier,
                isHighSchoolCommittee = committee.isHighSchoolCommittee,
                openSlotCount = committee.openSlotCount,
                pendingSlotCount = committee.pendingSlotCount,
                assignedSlotCount = committee.assignedSlotCount,
                highSchoolerAssignedCount = committee.slots.count { it.delegate?.isHighSchooler == true },
                universityAssignedCount = committee.slots.count { it.delegate != null && it.delegate?.isHighSchooler != true },
                reservationReminders = reservationReminders,
                positions = committee.positions.map { it.toBoardUiState(slotByDelegateId, issueCountsByDelegateId, today) },
            )
        },
    )
}

private fun List<Reservation>.toReservationReminderUiState(isHighSchool: Boolean): ReservationReminderUiState? {
    val totalCount = if (isHighSchool) {
        sumOf { it.highSchoolCount }
    } else {
        sumOf { it.universityCount }
    }
    if (totalCount == 0) return null

    return ReservationReminderUiState(
        label = if (isHighSchool) "HS reservations" else "Uni reservations",
        totalCount = totalCount,
        remainingCount = if (isHighSchool) {
            sumOf { it.remainingHighSchoolCount }
        } else {
            sumOf { it.remainingUniversityCount }
        },
    )
}

private fun Position.toBoardUiState(
    slotByDelegateId: Map<DelegateId, Slot>,
    issueCountsByDelegateId: Map<DelegateId, DelegateIssueCounts>,
    today: LocalDate,
): PositionBoardUiState {
    val occupiedDelegateIds = slots.mapNotNull { it.delegate?.id }.toSet()

    return PositionBoardUiState(
        id = id.value,
        name = name,
        displayName = name,
        flagAssetName = countryFlagAssetName,
        effectiveMode = effectiveCommitteeMode,
        mappingIncomplete = mappingIncomplete,
        mappedCommittee = mappedCommittee?.toMappedCommitteeUiState(),
        mappingOptions = if (committee.mode == CommitteeMode.Mapped) {
            conference.committees.toMappingOptions(position = this)
        } else {
            emptyList()
        },
        slots = slots.sortedBy { it.index }.map { it.toBoardUiState() },
        interestedDelegates = interestedDelegates
            .filter { it.visibleOnAssignmentBoard() }
            .filter { it.id !in occupiedDelegateIds }
            .mapNotNull { delegate ->
                val rank = delegate.preferences.firstOrNull { it.position === this }?.rank ?: return@mapNotNull null
                val currentSlot = slotByDelegateId[delegate.id]
                InterestedDelegateUiState(
                    id = delegate.id.value,
                    name = delegate.displayName,
                    isHighSchooler = delegate.isHighSchooler,
                    preferenceRank = rank,
                    pendingAssignmentLabel = delegate.pendingAssignmentLabel(currentSlot),
                    pendingAssignmentSlotId = currentSlot?.takeIf { it.isPending }?.id?.value,
                    confirmedAssignmentLabel = delegate.confirmedAssignmentLabel(currentSlot),
                    confirmedAssignmentSlotId = currentSlot?.takeIf { it.isAssigned }?.id?.value,
                )
            }
            .sortedWith(compareBy<InterestedDelegateUiState> { it.preferenceRank }.thenBy { it.name }),
        assignmentCandidates = conference.delegates
            .filter { it.visibleOnAssignmentBoard() }
            .filter { slotByDelegateId[it.id]?.isAssigned != true }
            .map { delegate ->
                val currentSlot = slotByDelegateId[delegate.id]
                val issueCounts = issueCountsByDelegateId[delegate.id]
                AssignmentDelegateCandidateUiState(
                    id = delegate.id.value,
                    name = delegate.displayName,
                    preferenceRank = delegate.preferences.firstOrNull { it.position === this }?.rank,
                    pendingAssignmentLabel = delegate.pendingAssignmentLabel(currentSlot),
                    confirmedAssignmentLabel = delegate.confirmedAssignmentLabel(currentSlot),
                    isHighSchooler = delegate.isHighSchooler,
                    warningCount = issueCounts?.warningCount ?: 0,
                    noticeCount = issueCounts?.noticeCount ?: 0,
                    paymentTierMismatch = delegate.paymentTierMismatchFor(committee),
                )
            }
            .sortedWith(
                compareBy<AssignmentDelegateCandidateUiState> { it.assignmentSortRank }
                    .thenBy { candidate ->
                        conference.delegates.single { it.id.value == candidate.id }.paymentTierFitSortRank(committee)
                    }
                    .thenBy { it.highSchoolFitSortRank(effectiveCommitteeMode) }
                    .thenBy { it.preferenceRank ?: Int.MAX_VALUE }
                    .thenBy { it.name },
            ),
        versus = toVersusUiState(slotByDelegateId, today),
    )
}

private fun Position.toVersusUiState(
    slotByDelegateId: Map<DelegateId, Slot>,
    today: LocalDate,
): PositionVersusUiState? {
    val pendingDelegates = slots
        .filter { it.isPending }
        .sortedBy { it.index }
        .map { requireNotNull(it.delegate) }
    val pendingIds = pendingDelegates.mapTo(mutableSetOf()) { it.id }
    val unassignedInterested = interestedDelegates
        .filter { it.isAssignmentEligible && slotByDelegateId[it.id] == null }
        .sortedWith(
            compareBy<Delegate> { delegate ->
                delegate.preferences.first { it.position === this }.rank
            }.thenBy { it.displayName },
        )
    val candidates = (pendingDelegates + unassignedInterested)
        .distinctBy { it.id }
    val requiredSelectionCount = slots.count { !it.isAssigned }
    if (requiredSelectionCount == 0 || candidates.size <= requiredSelectionCount) return null

    val candidateStates = candidates.map { delegate ->
        PositionVersusCandidateUiState(
            id = delegate.id.value,
            name = delegate.displayName,
            pending = delegate.id in pendingIds,
        )
    }
    val rows = buildList {
        add(
            PositionVersusRowUiState(
                id = "preference",
                label = "Preference",
                valuesByDelegateId = candidates.associate { delegate ->
                    delegate.id.value to delegate.preferences
                        .firstOrNull { it.position === this@toVersusUiState }
                        ?.rank
                        ?.toOrdinal()
                        .orEmpty()
                        .ifEmpty { "Not ranked" }
                },
            ),
        )
        add(
            PositionVersusRowUiState(
                id = "age",
                label = "Age",
                valuesByDelegateId = candidates.associate { delegate ->
                    delegate.id.value to (delegate.age(today)?.toString() ?: MissingVersusValue)
                },
            ),
        )
        conference.questions
            .filter { it.visibleInVersusView }
            .sortedWith(compareBy({ it.displayOrder }, { it.displayLabel }))
            .forEach { question ->
                add(
                    PositionVersusRowUiState(
                        id = question.id.value,
                        label = question.displayLabel,
                        valuesByDelegateId = candidates.associate { delegate ->
                            val answer = delegate.answers.firstOrNull { it.question === question }?.rawAnswer
                            val value = if (question.role == StandardQuestionRoles.ExperienceLevel) {
                                question.mapMunExperience(answer)?.versusLabel
                            } else {
                                answer?.takeIf { it.isNotBlank() }
                            }
                            delegate.id.value to (value ?: MissingVersusValue)
                        },
                    ),
                )
            }
    }
    return PositionVersusUiState(
        positionId = id.value,
        positionName = name,
        flagAssetName = countryFlagAssetName,
        requiredSelectionCount = requiredSelectionCount,
        candidates = candidateStates,
        rows = rows,
    )
}

private fun Int.toOrdinal(): String {
    val suffix = if (this % 100 in 11..13) {
        "th"
    } else {
        when (this % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
    }
    return "$this$suffix"
}

private val org.munika.assignment.domain.model.MunExperienceLevel.versusLabel: String
    get() = when (this) {
        org.munika.assignment.domain.model.MunExperienceLevel.FirstTime -> "First time"
        org.munika.assignment.domain.model.MunExperienceLevel.Beginner -> "Beginner"
        org.munika.assignment.domain.model.MunExperienceLevel.Intermediate -> "Intermediate"
        org.munika.assignment.domain.model.MunExperienceLevel.Advanced -> "Advanced"
    }

private const val MissingVersusValue = "\u2014"

private fun Committee.toMappedCommitteeUiState(): PositionMappedCommitteeUiState =
    PositionMappedCommitteeUiState(
        id = id.value,
        name = displayName,
        mode = mode,
    )

private fun List<Committee>.toMappingOptions(position: Position): List<CommitteeMappingOptionUiState> =
    filter {
        it !== position.committee &&
            it.mode != CommitteeMode.Mapped
    }
        .map {
            CommitteeMappingOptionUiState(
                id = it.id.value,
                name = it.displayName,
                mode = it.mode,
            )
        }

private fun Slot.toBoardUiState(): SlotBoardUiState =
    SlotBoardUiState(
        id = id.value,
        index = index,
        status = when (status) {
            AssignmentStatus.Empty -> SlotBoardStatus.Empty
            AssignmentStatus.Pending -> SlotBoardStatus.Pending
            AssignmentStatus.Assigned -> SlotBoardStatus.Assigned
        },
        delegateName = delegate?.displayName,
        delegateId = delegate?.id?.value,
        delegateIsHighSchooler = delegate?.isHighSchooler == true,
        matchesDelegatePreference = matchesDelegatePreference,
        positionName = position.name,
        paymentTierMismatch = delegate?.paymentTierMismatchFor(position.committee),
    )

private fun Delegate.visibleOnAssignmentBoard(): Boolean =
    isAssignmentEligible

private fun Delegate.confirmedAssignmentLabel(currentSlot: Slot?): String? {
    if (currentSlot?.isAssigned != true) return null
    val position = currentSlot.position
    return "${position.committee.displayName} (${position.name})"
}

private fun Delegate.pendingAssignmentLabel(currentSlot: Slot?): String? {
    if (currentSlot?.isPending != true) return null
    val position = currentSlot.position
    return "${position.committee.displayName} (${position.name})"
}

private fun Delegate.warningCount(): Int =
    answers.sumOf { answer -> answer.openIssues.count { it.severity == AnswerIssueSeverity.Warning } } +
        openProfileIssues().count { it.severity == AnswerIssueSeverity.Warning } +
        openSchoolAssignmentIssues().count { it.severity == AnswerIssueSeverity.Warning } +
        openUnpaidAssignmentIssues().size +
        openPaymentTierIssues().count { it.severity == AnswerIssueSeverity.Warning }

private fun Delegate.noticeCount(): Int =
    answers.sumOf { answer -> answer.openIssues.count { it.severity == AnswerIssueSeverity.Notice } } +
        openProfileIssues().count { it.severity == AnswerIssueSeverity.Notice } +
        openPaymentTierIssues().count { it.severity == AnswerIssueSeverity.Notice }

internal fun Delegate.paymentTierMismatchFor(committee: Committee): PaymentTierMismatchUiState? {
    val delegateTier = paymentTier ?: return null
    val direction = when {
        delegateTier == PaymentTier.Reduced && committee.paymentTier == PaymentTier.Full ->
            PaymentTierMismatchDirection.ReducedIntoFull
        delegateTier == PaymentTier.Full && committee.paymentTier == PaymentTier.Reduced ->
            PaymentTierMismatchDirection.FullIntoReduced
        else -> return null
    }
    val issue = requireNotNull(paymentTierIssueFor(committee))
    return PaymentTierMismatchUiState(
        direction = direction,
        acknowledged = resolutionFor(questionId = null, issue = issue) != null,
    )
}

private fun Delegate.paymentTierFitSortRank(committee: Committee): Int =
    when {
        paymentTier == committee.paymentTier -> 0
        paymentTier == PaymentTier.Full && committee.paymentTier == PaymentTier.Reduced -> 1
        else -> 2
    }

private val AssignmentDelegateCandidateUiState.assignmentSortRank: Int
    get() = when {
        confirmedAssignmentLabel != null -> 2
        pendingAssignmentLabel != null -> 1
        else -> 0
    }

private fun AssignmentDelegateCandidateUiState.highSchoolFitSortRank(mode: CommitteeMode?): Int =
    when {
        mode == null -> 0
        mode == CommitteeMode.HighSchool && isHighSchooler -> 0
        mode == CommitteeMode.University && !isHighSchooler -> 0
        else -> 1
    }
