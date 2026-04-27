package org.munika.assignment.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.temporal.ChronoUnit

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

    val languageLevel: LanguageLevel?
        get() = answers.firstNotNullOfOrNull { answer ->
            answer.question.mapLanguageLevel(answer.rawAnswer)
        }
}
