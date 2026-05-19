package org.munika.assignment.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Assignable participant from the MyMUN Delegates sheet.
 *
 * Profile and payment fields are MyMUN-managed during updates. Comments, active state, and pending
 * assignments are organizer-managed state preserved when possible.
 */
class Delegate(
    override val id: DelegateId,
    val conference: Conference,
    val name: PersonName,
    val email: String?,
    val profile: DelegateProfile,
    val paymentStatus: PaymentStatus,
    var active: Boolean = true,
    val isHeadDelegate: Boolean,
    delegation: Delegation? = null,
    val preferences: MutableList<Preference> = mutableListOf(),
    val comments: MutableList<DelegateComment> = mutableListOf(),
    val answers: MutableList<QuestionAnswer> = mutableListOf(),
    val createdAtFromMymun: Instant? = null,
    val submittedAt: Instant? = null,
) : DomainObject<DelegateId> {
    var delegation: Delegation? = null
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

    fun age(today: LocalDate = LocalDate.now()): Int? =
        profile.dateOfBirth?.let { Period.between(it, today).years }

    fun applicationSubmittedText(now: Instant = Instant.now(), zoneId: ZoneId = ZoneId.systemDefault()): String? {
        val submittedDate = submittedAt?.atZone(zoneId)?.toLocalDate() ?: return null
        val days = ChronoUnit.DAYS.between(submittedDate, now.atZone(zoneId).toLocalDate())
        return "Application submitted $days days ago"
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
        val age = age(today) ?: return emptyList()
        return when {
            isHighSchooler && age >= 19 -> listOf(
                AnswerIssue("Delegate is marked as a high schooler but is $age years old.", AnswerIssueSeverity.Warning),
            )
            !isHighSchooler && age <= 17 -> listOf(
                AnswerIssue("Delegate is $age years old but is not marked as a high schooler.", AnswerIssueSeverity.Warning),
            )
            else -> emptyList()
        }
    }
}
