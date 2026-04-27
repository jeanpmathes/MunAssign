package org.munika.assignment.data.db.mapper

import java.time.Instant
import java.time.LocalDate
import org.munika.assignment.data.db.model.ConferenceGraphRows
import org.munika.assignment.data.db.model.DelegateQuestionRuleRow
import org.munika.assignment.data.db.model.DelegateRow
import org.munika.assignment.domain.model.AnswerRule
import org.munika.assignment.domain.model.AssignmentSource
import org.munika.assignment.domain.model.Committee
import org.munika.assignment.domain.model.CommitteeId
import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.ConferenceId
import org.munika.assignment.domain.model.Delegate
import org.munika.assignment.domain.model.DelegateComment
import org.munika.assignment.domain.model.DelegateCommentId
import org.munika.assignment.domain.model.DelegateId
import org.munika.assignment.domain.model.DelegateProfile
import org.munika.assignment.domain.model.Delegation
import org.munika.assignment.domain.model.DelegationId
import org.munika.assignment.domain.model.HighSchoolerAnswerMapping
import org.munika.assignment.domain.model.HighSchoolerStatus
import org.munika.assignment.domain.model.LanguageLevel
import org.munika.assignment.domain.model.LanguageLevelAnswerMapping
import org.munika.assignment.domain.model.MinimumSentenceCount
import org.munika.assignment.domain.model.MinimumTextLength
import org.munika.assignment.domain.model.MunExperienceAnswerMapping
import org.munika.assignment.domain.model.MunExperienceLevel
import org.munika.assignment.domain.model.NoticeWhenNonBlank
import org.munika.assignment.domain.model.PaymentStatus
import org.munika.assignment.domain.model.PersonName
import org.munika.assignment.domain.model.Position
import org.munika.assignment.domain.model.PositionId
import org.munika.assignment.domain.model.Preference
import org.munika.assignment.domain.model.PreferenceId
import org.munika.assignment.domain.model.Question
import org.munika.assignment.domain.model.QuestionAnswer
import org.munika.assignment.domain.model.QuestionAnswerId
import org.munika.assignment.domain.model.QuestionId
import org.munika.assignment.domain.model.RequiredAnswer
import org.munika.assignment.domain.model.Slot
import org.munika.assignment.domain.model.SlotId
import org.munika.assignment.domain.model.StandardQuestionHandlingTypes
import org.munika.assignment.domain.model.StandardQuestionRoles
import org.munika.assignment.domain.model.TypedAnswerMappings

object ConferenceGraphMapper {
    fun toDomain(rows: ConferenceGraphRows): Conference {
        val conference = Conference(
            id = ConferenceId(rows.conference.id),
            name = rows.conference.name,
            createdAt = rows.conference.createdAtEpochMillis.toInstant(),
            updatedAt = rows.conference.updatedAtEpochMillis.toInstant(),
            lastMymunUpdateAt = rows.conference.lastMymunUpdateAtEpochMillis?.toInstant(),
        )

        val committeesById = rows.committees.associate { row ->
            val committee = Committee(
                id = CommitteeId(row.id),
                conference = conference,
                mymunName = row.mymunName,
                alias = row.alias,
                slotsPerPosition = row.slotsPerPosition,
            )
            conference.committees += committee
            row.id to committee
        }

        val positionsById = rows.positions.associate { row ->
            val committee = requireNotNull(committeesById[row.committeeId]) {
                "Position ${row.id} references missing committee ${row.committeeId}."
            }
            val position = Position(
                id = PositionId(row.id),
                committee = committee,
                name = row.name,
            )
            committee.positions += position
            row.id to position
        }

        val delegationsById = rows.delegations.associate { row ->
            val delegation = Delegation(
                id = DelegationId(row.id),
                conference = conference,
                name = row.name,
                country = row.country,
                expectedDelegateCount = row.expectedDelegateCount,
                currentDelegateCountFromMymun = row.currentDelegateCountFromMymun,
            )
            conference.delegations += delegation
            row.id to delegation
        }

        val delegatesById = rows.delegates.associate { row ->
            val delegate = row.toDomain(conference, row.delegationId?.let { delegationsById[it] })
            conference.delegates += delegate
            row.id to delegate
        }

        rows.delegations.forEach { row ->
            delegationsById[row.id]?.setHeadDelegate(row.headDelegateId?.let { delegatesById[it] })
        }

        rows.preferences.forEach { row ->
            val delegate = requireNotNull(delegatesById[row.delegateId]) {
                "Preference ${row.id} references missing delegate ${row.delegateId}."
            }
            val position = requireNotNull(positionsById[row.positionId]) {
                "Preference ${row.id} references missing position ${row.positionId}."
            }
            delegate.preferences += Preference(
                id = PreferenceId(row.id),
                delegate = delegate,
                position = position,
                rank = row.rank,
                rawPreferenceText = row.rawPreferenceText,
            )
        }

        rows.comments.forEach { row ->
            val delegate = requireNotNull(delegatesById[row.delegateId]) {
                "Comment ${row.id} references missing delegate ${row.delegateId}."
            }
            delegate.comments += DelegateComment(
                id = DelegateCommentId(row.id),
                delegate = delegate,
                body = row.body,
                createdAt = row.createdAtEpochMillis.toInstant(),
                updatedAt = row.updatedAtEpochMillis.toInstant(),
            )
        }

        val rulesByQuestionId = rows.questionRules.groupBy { it.questionId }
        val questionsById = rows.questions.associate { row ->
            val rules = rulesByQuestionId[row.id].orEmpty().mapNotNull { it.toDomainRule() }
            val handling = StandardQuestionHandlingTypes.fromId(row.handlingTypeId)
            val question = Question(
                id = QuestionId(row.id),
                questionText = row.questionText,
                role = StandardQuestionRoles.fromId(row.roleId),
                handling = handling,
                visibleInDelegateDetail = row.visibleInDelegateDetail,
                organizerLabel = row.organizerLabel,
                rules = rules.ifEmpty { handling.defaultRules() },
                mappings = TypedAnswerMappings(
                    munExperience = rows.munExperienceMappings
                        .filter { it.questionId == row.id }
                        .map { MunExperienceAnswerMapping(it.rawValue, MunExperienceLevel.valueOf(it.mappedValue)) }
                        .ifEmpty { TypedAnswerMappings.defaults().munExperience },
                    highSchooler = rows.highSchoolerMappings
                        .filter { it.questionId == row.id }
                        .map { HighSchoolerAnswerMapping(it.rawValue, HighSchoolerStatus.valueOf(it.mappedValue)) }
                        .ifEmpty { TypedAnswerMappings.defaults().highSchooler },
                    languageLevel = rows.languageLevelMappings
                        .filter { it.questionId == row.id }
                        .map { LanguageLevelAnswerMapping(it.rawValue, LanguageLevel.valueOf(it.mappedValue)) }
                        .ifEmpty { TypedAnswerMappings.defaults().languageLevel },
                ),
            )
            conference.questions += question
            row.id to question
        }

        rows.answers.forEach { row ->
            val delegate = requireNotNull(delegatesById[row.delegateId]) {
                "Answer ${row.id} references missing delegate ${row.delegateId}."
            }
            val question = requireNotNull(questionsById[row.questionId]) {
                "Answer ${row.id} references missing question ${row.questionId}."
            }
            delegate.answers += QuestionAnswer(
                id = QuestionAnswerId(row.id),
                delegate = delegate,
                question = question,
                rawAnswer = row.answer,
            )
        }

        rows.slots.forEach { row ->
            val position = requireNotNull(positionsById[row.positionId]) {
                "Slot ${row.id} references missing position ${row.positionId}."
            }
            val slot = Slot(
                id = SlotId(row.id),
                position = position,
                index = row.slotIndex,
                updatedAt = row.updatedAtEpochMillis.toInstant(),
            )
            position.slots += slot
            val assignedDelegate = row.assignedDelegateId?.let { delegateId ->
                requireNotNull(delegatesById[delegateId]) {
                    "Slot ${row.id} references missing delegate $delegateId."
                }
            }
            if (assignedDelegate != null) {
                val source = requireNotNull(row.assignmentSource?.let { AssignmentSource.valueOf(it) }) {
                    "Slot ${row.id} has an assigned delegate but no assignment source."
                }
                slot.assign(assignedDelegate, source, row.updatedAtEpochMillis.toInstant())
            }
        }

        return conference
    }

    private fun DelegateRow.toDomain(conference: Conference, delegation: org.munika.assignment.domain.model.Delegation?): Delegate =
        Delegate(
            id = DelegateId(id),
            conference = conference,
            name = PersonName(firstName, lastName),
            email = email,
            profile = DelegateProfile(
                country = country,
                city = city,
                streetAddress = streetAddress,
                zipCode = zipCode,
                diet = diet,
                phone = phone,
                nationality = nationality,
                dateOfBirth = dateOfBirth?.let(LocalDate::parse),
                delegationNameFromMymun = delegationNameFromMymun,
                applicationStatus = applicationStatus,
            ),
            paymentStatus = PaymentStatus.valueOf(paymentStatus),
            active = active,
            isHeadDelegate = isHeadDelegate,
            delegation = delegation,
            createdAtFromMymun = createdAtFromMymunEpochMillis?.toInstant(),
            submittedAt = submittedAtEpochMillis?.toInstant(),
        )

    private fun DelegateQuestionRuleRow.toDomainRule(): AnswerRule? =
        when (ruleTypeId) {
            "minimum_sentence_count" -> intParameter?.let(::MinimumSentenceCount)
            "minimum_text_length" -> intParameter?.let(::MinimumTextLength)
            "notice_when_non_blank", "warn_when_non_blank" -> NoticeWhenNonBlank(textParameter ?: "Answer requires attention.")
            "required_answer" -> RequiredAnswer(textParameter ?: "Answer is required.")
            else -> null
        }

    private fun org.munika.assignment.domain.model.QuestionHandlingType.defaultRules(): List<AnswerRule> =
        when (this) {
            StandardQuestionHandlingTypes.CommitteeChoiceExplanation -> listOf(MinimumSentenceCount(3))
            StandardQuestionHandlingTypes.VisaSupport -> listOf(NoticeWhenNonBlank("Visa support requested."))
            else -> emptyList()
        }

    private fun Long.toInstant(): Instant = Instant.ofEpochMilli(this)
}
