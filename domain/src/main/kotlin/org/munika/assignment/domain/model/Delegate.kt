package org.munika.assignment.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Assignable participant from the MyMUN Delegates sheet.
 *
 * Profile and [paymentStatus] are MyMUN-managed during updates. [paymentTier], comments, active
 * state, and pending assignments are organizer-managed state preserved when possible.
 */
class Delegate(
    override val id: DelegateId,
    val conference: Conference,
    val name: PersonName,
    val email: String?,
    val profile: DelegateProfile,
    val paymentStatus: PaymentStatus,
    val paymentTier: PaymentTier? = null,
    var active: Boolean = true,
    val isHeadDelegate: Boolean,
    delegation: Delegation? = null,
    val preferences: MutableList<Preference> = mutableListOf(),
    val comments: MutableList<DelegateComment> = mutableListOf(),
    val answers: MutableList<QuestionAnswer> = mutableListOf(),
    val issueResolutions: MutableList<AnswerIssueResolution> = mutableListOf(),
    val createdAtFromMymun: Instant? = null,
    val submittedAt: Instant? = null,
) : DomainObject<DelegateId> {
    var delegation: Delegation? = null
        internal set

    var reservationLink: ReservationDelegateLink? = null
        internal set

    init {
        delegation?.addDelegate(this)
    }

    val displayName: String
        get() = name.displayName

    val currentSlot: Slot?
        get() = conference.slots.firstOrNull { it.delegate === this }

    val assignmentStatus: AssignmentStatus
        get() = currentSlot?.status ?: AssignmentStatus.Empty

    val currentPosition: Position?
        get() = currentSlot?.position

    /** Whether the delegate may appear in assignment workflows. */
    val isAssignmentEligible: Boolean
        get() = active && paymentStatus == PaymentStatus.Paid && profile.isUsableForAssignment

    fun age(today: LocalDate = LocalDate.now()): Int? =
        profile.dateOfBirth?.let { Period.between(it, today).years }

    fun applicationSubmittedText(now: Instant = Instant.now(), zoneId: ZoneId = ZoneId.systemDefault()): String? {
        val submittedDate = submittedAt?.atZone(zoneId)?.toLocalDate() ?: return null
        val days = ChronoUnit.DAYS.between(submittedDate, now.atZone(zoneId).toLocalDate()).coerceAtLeast(0)
        return if (days == 1L) "Application submitted 1 day ago" else "Application submitted $days days ago"
    }

    fun resolutionFor(questionId: QuestionId?, issue: AnswerIssue): AnswerIssueResolution? =
        issueResolutions.firstOrNull { resolution ->
            resolution.questionId == questionId && resolution.issueKey == issue.key
        }

    val experienceLevel: MunExperienceLevel?
        get() = answers.firstNotNullOfOrNull { answer ->
            answer.question.mapMunExperience(answer.rawAnswer)
        }

    val highSchoolerStatus: HighSchoolerStatus?
        get() = answers.firstNotNullOfOrNull { answer ->
            answer.question.mapHighSchoolerStatus(answer.rawAnswer)
        }

    val isHighSchooler: Boolean
        get() = highSchoolerStatus == HighSchoolerStatus.Yes

    val languageLevel: LanguageLevel?
        get() = answers.firstNotNullOfOrNull { answer ->
            answer.question.mapLanguageLevel(answer.rawAnswer)
        }

    fun profileIssues(today: LocalDate = LocalDate.now()): List<AnswerIssue> {
        val issues = mutableListOf<AnswerIssue>()
        if (languageLevel == LanguageLevel.BeginnerA1A2) {
            issues += AnswerIssue(
                message = "Delegate language level is A1/A2.",
                severity = AnswerIssueSeverity.Warning,
                key = ProfileLowLanguageLevelIssueKey,
            )
        }

        val age = age(today) ?: return issues
        when {
            isHighSchooler && age >= 19 -> issues +=
                AnswerIssue(
                    message = "Delegate is marked as a high schooler but is $age years old.",
                    severity = AnswerIssueSeverity.Warning,
                    key = ProfileHighSchoolerOverAgeIssueKey,
                )
            !isHighSchooler && age <= 17 -> issues +=
                AnswerIssue(
                    message = "Delegate is $age years old but is not marked as a high schooler.",
                    severity = AnswerIssueSeverity.Warning,
                    key = ProfileMinorNotHighSchoolerIssueKey,
                )
        }
        return issues
    }

    fun openProfileIssues(today: LocalDate = LocalDate.now()): List<AnswerIssue> =
        profileIssues(today).filterNot { issue -> resolutionFor(questionId = null, issue) != null }

    fun resolvedProfileIssues(today: LocalDate = LocalDate.now()): List<ResolvedAnswerIssue> =
        profileIssues(today).mapNotNull { issue ->
            resolutionFor(questionId = null, issue)?.let { resolution -> ResolvedAnswerIssue(issue, resolution) }
        }

    /** Returns current payment-tier classification and assignment compatibility issues. */
    fun paymentTierIssues(): List<AnswerIssue> {
        if (!isAssignmentEligible) return emptyList()
        if (paymentTier == null) return listOf(
            AnswerIssue(
                message = "Paid delegate has no payment tier.",
                severity = AnswerIssueSeverity.Warning,
                key = PaymentTierMissingIssueKey,
            ),
        )
        val committee = currentPosition?.committee ?: return emptyList()
        return listOfNotNull(paymentTierIssueFor(committee))
    }

    /** Returns the issue that assigning this delegate to [committee] would create, if any. */
    fun paymentTierIssueFor(committee: Committee): AnswerIssue? {
        require(committee.conference === conference) { "Committee must belong to the delegate's conference." }
        if (!isAssignmentEligible) return null
        val delegateTier = paymentTier ?: return AnswerIssue(
            message = "Paid delegate has no payment tier.",
            severity = AnswerIssueSeverity.Warning,
            key = PaymentTierMissingIssueKey,
        )
        return when {
            delegateTier == PaymentTier.Reduced && committee.paymentTier == PaymentTier.Full ->
                AnswerIssue(
                    message = "Reduced-tier delegate is assigned to Full-tier committee ${committee.displayName}.",
                    severity = AnswerIssueSeverity.Warning,
                    key = "Warning:payment_tier_reduced_in_full:${committee.id.value}",
                )
            delegateTier == PaymentTier.Full && committee.paymentTier == PaymentTier.Reduced ->
                AnswerIssue(
                    message = "Full-tier delegate is assigned to Reduced-tier committee ${committee.displayName}.",
                    severity = AnswerIssueSeverity.Notice,
                    key = "Notice:payment_tier_full_in_reduced:${committee.id.value}",
                )
            else -> null
        }
    }

    /** Returns unresolved payment issues, always retaining the non-dismissible missing-tier warning. */
    fun openPaymentTierIssues(): List<AnswerIssue> =
        paymentTierIssues().filter { issue ->
            issue.key == PaymentTierMissingIssueKey || resolutionFor(questionId = null, issue) == null
        }

    /** Returns acknowledged assignment mismatches that are still present. */
    fun resolvedPaymentTierIssues(): List<ResolvedAnswerIssue> =
        paymentTierIssues()
            .filterNot { issue -> issue.key == PaymentTierMissingIssueKey }
            .mapNotNull { issue ->
                resolutionFor(questionId = null, issue)?.let { resolution -> ResolvedAnswerIssue(issue, resolution) }
            }

    companion object {
        /** Stable key for the missing-tier warning used by persistence and inline classification. */
        const val PaymentTierMissingIssueKey = "Warning:payment_tier_missing"

        private const val ProfileLowLanguageLevelIssueKey = "Warning:profile_low_language_level"
        private const val ProfileHighSchoolerOverAgeIssueKey = "Warning:profile_high_schooler_over_age"
        private const val ProfileMinorNotHighSchoolerIssueKey = "Warning:profile_minor_not_high_schooler"
    }
}
