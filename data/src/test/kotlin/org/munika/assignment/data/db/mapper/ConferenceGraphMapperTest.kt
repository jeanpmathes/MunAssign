package org.munika.assignment.data.db.mapper

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import org.munika.assignment.data.db.model.CommitteeRow
import org.munika.assignment.data.db.model.ConferenceGraphRows
import org.munika.assignment.data.db.model.ConferenceRow
import org.munika.assignment.data.db.model.DelegateQuestionAnswerRow
import org.munika.assignment.data.db.model.DelegateQuestionRow
import org.munika.assignment.data.db.model.DelegateRow
import org.munika.assignment.data.db.model.DelegationRow
import org.munika.assignment.data.db.model.LanguageLevelAnswerMappingRow
import org.munika.assignment.data.db.model.PositionRow
import org.munika.assignment.data.db.model.PreferenceRow
import org.munika.assignment.data.db.model.SlotRow
import org.munika.assignment.domain.model.AssignmentStatus
import org.munika.assignment.domain.model.CommitteeMode
import org.munika.assignment.domain.model.LanguageLevel

class ConferenceGraphMapperTest {
    @Test
    fun `maps database rows into a connected conference graph`() {
        val conference = ConferenceGraphMapper.toDomain(realisticConferenceRows())

        val committee = conference.committees.single()
        val position = committee.positions.single()
        val delegateAda = conference.delegates.single { it.id.value == "delegate-ada" }
        val delegateGrace = conference.delegates.single { it.id.value == "delegate-grace" }
        val delegation = conference.delegations.single()
        val pendingSlot = position.slots.single { it.id.value == "slot-1" }
        val assignedSlot = position.slots.single { it.id.value == "slot-2" }

        assertSame(conference, committee.conference)
        assertEquals(true, committee.isHighSchoolCommittee)
        assertSame(committee, position.committee)
        assertSame(position, pendingSlot.position)
        assertSame(delegateAda, pendingSlot.delegate)
        assertSame(delegateGrace, assignedSlot.delegate)
        assertEquals(AssignmentStatus.Pending, pendingSlot.status)
        assertEquals(AssignmentStatus.Assigned, assignedSlot.status)
        assertSame(pendingSlot, delegateAda.currentSlot)
        assertSame(delegation, delegateAda.delegation)
        assertSame(delegateAda, delegation.headDelegate)
        assertEquals(LanguageLevel.BeginnerA1A2, delegateAda.languageLevel)
        assertSame(position, delegateAda.preferences.single().position)
    }

    private fun realisticConferenceRows(): ConferenceGraphRows =
        ConferenceGraphRows(
            conference = ConferenceRow(
                id = "conference",
                name = "KAMUN",
                displayOrder = 0,
                createdAtEpochMillis = 0,
                updatedAtEpochMillis = 0,
                lastMymunUpdateAtEpochMillis = null,
            ),
            committees = listOf(
                CommitteeRow(
                    id = "committee",
                    conferenceId = "conference",
                    mymunName = "Security Council",
                    alias = "UNSC",
                    slotsPerPosition = 2,
                    isHighSchoolCommittee = true,
                    mode = CommitteeMode.HighSchool.name,
                ),
            ),
            positions = listOf(
                PositionRow(
                    id = "position-france",
                    conferenceId = "conference",
                    committeeId = "committee",
                    mappedCommitteeId = null,
                    name = "France",
                ),
            ),
            slots = listOf(
                SlotRow(
                    id = "slot-1",
                    positionId = "position-france",
                    slotIndex = 1,
                    assignedDelegateId = "delegate-ada",
                    assignmentSource = "Application",
                    updatedAtEpochMillis = 10,
                ),
                SlotRow(
                    id = "slot-2",
                    positionId = "position-france",
                    slotIndex = 2,
                    assignedDelegateId = "delegate-grace",
                    assignmentSource = "Mymun",
                    updatedAtEpochMillis = 20,
                ),
            ),
            delegations = listOf(
                DelegationRow(
                    id = "delegation",
                    conferenceId = "conference",
                    name = "Team Ada",
                    country = "UK",
                    expectedDelegateCount = 2,
                    currentDelegateCountFromMymun = 2,
                    headDelegateId = "delegate-ada",
                ),
            ),
            delegates = listOf(
                delegateRow("delegate-ada", "Ada", "Lovelace", delegationId = "delegation", isHeadDelegate = true),
                delegateRow("delegate-grace", "Grace", "Hopper", delegationId = "delegation", isHeadDelegate = false),
            ),
            preferences = listOf(
                PreferenceRow(
                    id = "preference",
                    delegateId = "delegate-ada",
                    positionId = "position-france",
                    rank = 1,
                    rawPreferenceText = "Security Council (France)",
                ),
            ),
            comments = emptyList(),
            questions = listOf(
                DelegateQuestionRow(
                    id = "question-language",
                    conferenceId = "conference",
                    questionText = "Question: What is your level of English?",
                    roleId = "language_level",
                    handlingTypeId = "expected_typed_answer",
                    visibleInDelegateDetail = true,
                    organizerLabel = null,
                    displayOrder = 0,
                ),
            ),
            reservations = emptyList(),
            reservationLinks = emptyList(),
            questionRules = emptyList(),
            munExperienceMappings = emptyList(),
            highSchoolerMappings = emptyList(),
            languageLevelMappings = listOf(
                LanguageLevelAnswerMappingRow(
                    id = "mapping-language",
                    questionId = "question-language",
                    rawValue = "A1/A2 (Beginner)",
                    mappedValue = "BeginnerA1A2",
                ),
            ),
            answers = listOf(
                DelegateQuestionAnswerRow(
                    id = "answer-language",
                    delegateId = "delegate-ada",
                    questionId = "question-language",
                    answer = "A1/A2 (Beginner)",
                ),
            ),
            issueResolutions = emptyList(),
        )

    private fun delegateRow(id: String, firstName: String, lastName: String, delegationId: String, isHeadDelegate: Boolean): DelegateRow =
        DelegateRow(
            id = id,
            conferenceId = "conference",
            delegationId = delegationId,
            firstName = firstName,
            lastName = lastName,
            email = "${firstName.lowercase()}@example.test",
            country = null,
            city = null,
            streetAddress = null,
            zipCode = null,
            diet = null,
            phone = null,
            nationality = null,
            dateOfBirth = null,
            delegationNameFromMymun = "Team Ada",
            applicationStatus = "Accepted",
            paymentStatus = "Paid",
            active = true,
            isHeadDelegate = isHeadDelegate,
            createdAtFromMymunEpochMillis = null,
            submittedAtEpochMillis = null,
        )
}
