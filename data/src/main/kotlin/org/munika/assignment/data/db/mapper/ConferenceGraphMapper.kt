package org.munika.assignment.data.db.mapper

import java.time.Instant
import java.time.LocalDate
import org.munika.assignment.data.db.model.ConferenceGraphRows
import org.munika.assignment.data.db.model.DelegateQuestionRuleRow
import org.munika.assignment.data.db.model.DelegateRow
import org.munika.assignment.domain.model.AnswerIssueResolution
import org.munika.assignment.domain.model.AnswerIssueResolutionId
import org.munika.assignment.domain.model.AnswerIssueSeverity
import org.munika.assignment.domain.model.AnswerRule
import org.munika.assignment.domain.model.AssignmentSource
import org.munika.assignment.domain.model.Committee
import org.munika.assignment.domain.model.CommitteeId
import org.munika.assignment.domain.model.CommitteeMode
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
import org.munika.assignment.domain.model.MymunUpdateFilenamePart
import org.munika.assignment.domain.model.MymunUpdateFilenameSignature
import org.munika.assignment.domain.model.MymunUpdateFilenameSignatureId
import org.munika.assignment.domain.model.MunExperienceAnswerMapping
import org.munika.assignment.domain.model.MunExperienceLevel
import org.munika.assignment.domain.model.NoticeWhenAffirmative
import org.munika.assignment.domain.model.NoticeWhenNonBlank
import org.munika.assignment.domain.model.PaymentStatus
import org.munika.assignment.domain.model.PaymentTier
import org.munika.assignment.domain.model.PersonName
import org.munika.assignment.domain.model.Position
import org.munika.assignment.domain.model.PositionId
import org.munika.assignment.domain.model.Preference
import org.munika.assignment.domain.model.PreferenceId
import org.munika.assignment.domain.model.PreferenceSource
import org.munika.assignment.domain.model.Question
import org.munika.assignment.domain.model.QuestionAnswer
import org.munika.assignment.domain.model.QuestionAnswerId
import org.munika.assignment.domain.model.QuestionId
import org.munika.assignment.domain.model.Reservation
import org.munika.assignment.domain.model.ReservationDelegateLink
import org.munika.assignment.domain.model.ReservationDelegateLinkId
import org.munika.assignment.domain.model.ReservationId
import org.munika.assignment.domain.model.ReservationLinkSource
import org.munika.assignment.domain.model.RequiredAnswer
import org.munika.assignment.domain.model.Slot
import org.munika.assignment.domain.model.SlotId
import org.munika.assignment.domain.model.StandardQuestionHandlingTypes
import org.munika.assignment.domain.model.StandardQuestionRoles
import org.munika.assignment.domain.model.TypedAnswerMappings
import org.munika.assignment.domain.repository.PreferenceValueOrigin

/**
 * Rehydrates table-shaped rows into a connected domain object graph.
 *
 * Missing references indicate corrupt persisted data and fail fast with row-specific messages.
 */
object ConferenceGraphMapper {
    fun toDomain(rows: ConferenceGraphRows): Conference {
        val conference = Conference(
            id = ConferenceId(rows.conference.id),
            name = rows.conference.name,
            displayOrder = rows.conference.displayOrder,
            createdAt = rows.conference.createdAtEpochMillis.toInstant(),
            updatedAt = rows.conference.updatedAtEpochMillis.toInstant(),
            lastMymunUpdateAt = rows.conference.lastMymunUpdateAtEpochMillis?.toInstant(),
            mymunUpdateFilenameSignatures = rows.mymunUpdateFilenameSignatures
                .sortedByDescending { it.trustedAtEpochMillis }
                .mapTo(mutableListOf()) { signature ->
                    MymunUpdateFilenameSignature(
                        id = MymunUpdateFilenameSignatureId(signature.id),
                        parts = signature.parts.sortedBy { it.displayOrder }.map { part ->
                            MymunUpdateFilenamePart(part.displayValue, part.normalizedValue)
                        },
                        trustedAt = signature.trustedAtEpochMillis.toInstant(),
                    )
                },
        )

        val committeesById = rows.committees.associate { row ->
            val committee = Committee(
                id = CommitteeId(row.id),
                conference = conference,
                mymunName = row.mymunName,
                alias = row.alias,
                slotsPerPosition = row.slotsPerPosition,
                mode = CommitteeMode.valueOf(row.mode),
                paymentTier = PaymentTier.valueOf(row.paymentTier),
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

        rows.positions.forEach { row ->
            val position = requireNotNull(positionsById[row.id]) {
                "Position ${row.id} was not mapped."
            }
            position.setMappedCommittee(row.mappedCommitteeId?.let { mappedCommitteeId ->
                requireNotNull(committeesById[mappedCommitteeId]) {
                    "Position ${row.id} references missing mapped committee $mappedCommitteeId."
                }
            })
        }

        val delegationsById = rows.delegations.associate { row ->
            val delegation = Delegation(
                id = DelegationId(row.id),
                conference = conference,
                name = row.name,
                country = row.country,
                expectedDelegateCount = row.expectedDelegateCount,
                currentDelegateCountFromMymun = row.currentDelegateCountFromMymun,
                isHighSchoolDelegation = row.isHighSchoolDelegation,
                needsReview = row.needsReview,
            )
            conference.delegations += delegation
            row.id to delegation
        }

        val correctedDelegateIds = rows.preferenceCompletions
            .filter { correction ->
                correction.emptyAcknowledged ||
                    correction.origin == PreferenceValueOrigin.Organizer.name ||
                    correction.positionId != correction.baselinePositionId
            }
            .mapTo(mutableSetOf()) { it.delegateId }
        val delegatesById = rows.delegates.associate { row ->
            val delegate = row.toDomain(
                conference = conference,
                delegation = row.delegationId?.let { delegationsById[it] },
                hasPreferenceCorrections = row.id in correctedDelegateIds,
            )
            conference.delegates += delegate
            row.id to delegate
        }

        rows.delegations.forEach { row ->
            delegationsById[row.id]?.setHeadDelegate(row.headDelegateId?.let { delegatesById[it] })
        }

        val reservationsById = rows.reservations.associate { row ->
            val delegation = row.delegationId?.let { delegationId ->
                requireNotNull(delegationsById[delegationId]) {
                    "Reservation ${row.id} references missing delegation $delegationId."
                }
            }
            val reservation = Reservation(
                id = ReservationId(row.id),
                conference = conference,
                name = row.name,
                highSchoolCount = row.highSchoolCount,
                universityCount = row.universityCount,
                delegation = delegation,
                createdAt = row.createdAtEpochMillis.toInstant(),
                updatedAt = row.updatedAtEpochMillis.toInstant(),
            )
            conference.reservations += reservation
            row.id to reservation
        }

        val correctionsByDelegateAndRank = rows.preferenceCompletions.associateBy { it.delegateId to it.rank }
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
                source = correctionsByDelegateAndRank[row.delegateId to row.rank]?.let { correction ->
                    when {
                        correction.origin == PreferenceValueOrigin.Organizer.name -> PreferenceSource.OrganizerCompletion
                        correction.baselinePositionId != row.positionId -> PreferenceSource.OrganizerCorrection
                        else -> PreferenceSource.Mymun
                    }
                } ?: PreferenceSource.Mymun,
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

        rows.issueResolutions.forEach { row ->
            val delegate = requireNotNull(delegatesById[row.delegateId]) {
                "Issue resolution ${row.id} references missing delegate ${row.delegateId}."
            }
            delegate.issueResolutions += AnswerIssueResolution(
                id = AnswerIssueResolutionId(row.id),
                delegate = delegate,
                questionId = row.questionId?.let(::QuestionId),
                issueKey = row.issueKey,
                issueMessage = row.issueMessage,
                severity = AnswerIssueSeverity.valueOf(row.severity),
                justification = row.justification,
                resolvedAt = row.resolvedAtEpochMillis.toInstant(),
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
                displayOrder = row.displayOrder,
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
                visibleInVersusView = row.visibleInVersusView,
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

        rows.reservationLinks.forEach { row ->
            val reservation = requireNotNull(reservationsById[row.reservationId]) {
                "Reservation link ${row.id} references missing reservation ${row.reservationId}."
            }
            val delegate = requireNotNull(delegatesById[row.delegateId]) {
                "Reservation link ${row.id} references missing delegate ${row.delegateId}."
            }
            val link = ReservationDelegateLink(
                id = ReservationDelegateLinkId(row.id),
                reservation = reservation,
                delegate = delegate,
                source = ReservationLinkSource.valueOf(row.source),
                createdAt = row.createdAtEpochMillis.toInstant(),
                updatedAt = row.updatedAtEpochMillis.toInstant(),
            )
            reservation.delegateLinks += link
            link.attachToDelegate()
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

    private fun DelegateRow.toDomain(
        conference: Conference,
        delegation: org.munika.assignment.domain.model.Delegation?,
        hasPreferenceCorrections: Boolean,
    ): Delegate =
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
            paymentTier = paymentTier?.let(PaymentTier::valueOf),
            active = active,
            isHeadDelegate = isHeadDelegate,
            delegation = delegation,
            createdAtFromMymun = createdAtFromMymunEpochMillis?.toInstant(),
            submittedAt = submittedAtEpochMillis?.toInstant(),
            hasPreferenceCorrections = hasPreferenceCorrections,
        )

    private fun DelegateQuestionRuleRow.toDomainRule(): AnswerRule? =
        when (ruleTypeId) {
            "minimum_sentence_count" -> intParameter?.let(::MinimumSentenceCount)
            "minimum_text_length" -> intParameter?.let(::MinimumTextLength)
            "notice_when_affirmative" -> NoticeWhenAffirmative(textParameter ?: "Answer requires attention.")
            "notice_when_non_blank", "warn_when_non_blank" -> NoticeWhenNonBlank(textParameter ?: "Answer requires attention.")
            "required_answer" -> RequiredAnswer(textParameter ?: "Answer is required.")
            else -> null
        }

    private fun org.munika.assignment.domain.model.QuestionHandlingType.defaultRules(): List<AnswerRule> =
        when (this) {
            StandardQuestionHandlingTypes.CommitteeChoiceExplanation -> listOf(MinimumSentenceCount(3))
            StandardQuestionHandlingTypes.VisaSupport -> listOf(NoticeWhenAffirmative("Visa support requested."))
            else -> emptyList()
        }

    private fun Long.toInstant(): Instant = Instant.ofEpochMilli(this)
}
