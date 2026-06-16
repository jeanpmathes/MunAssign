package org.munika.assignment.ui.viewmodel

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.munika.assignment.domain.model.AnswerIssue
import org.munika.assignment.domain.model.AnswerIssueResolution
import org.munika.assignment.domain.model.AnswerIssueResolutionId
import org.munika.assignment.domain.model.AnswerIssueSeverity
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
import org.munika.assignment.domain.model.Reservation
import org.munika.assignment.domain.model.ReservationDelegateLink
import org.munika.assignment.domain.model.ReservationDelegateLinkId
import org.munika.assignment.domain.model.ReservationId
import org.munika.assignment.domain.model.ReservationLinkSource
import org.munika.assignment.domain.model.Slot
import org.munika.assignment.domain.model.SlotId
import org.munika.assignment.domain.model.StandardQuestionRoles
import org.munika.assignment.domain.model.TypedAnswerMappings

class DelegateDetailUiModelsTest {
    @Test
    fun `delegate detail includes profile assignment preferences answers comments and delegation`() {
        val board = board()
        board.franceSlot.assign(board.delegate, AssignmentSource.Application, Instant.EPOCH)

        val detail = board.conference.toDelegateDetailUiState(board.delegate.id.value)

        assertNotNull(detail)
        assertEquals("Alex Kim", detail.name)
        assertEquals("Paid", detail.paymentStatus.label)
        assertEquals(DelegateStatusTone.Green, detail.paymentStatus.tone)
        assertEquals("Accepted", detail.applicationStatus?.label)
        assertEquals(DelegateStatusTone.Blue, detail.applicationStatus?.tone)
        assertEquals("alex@example.test", detail.email)
        assertEquals("+49", detail.phone)
        assertEquals("Germany, 10115 Berlin, Street 1", detail.address)
        assertEquals("German", detail.nationality)
        assertEquals("Vegan", detail.diet)
        assertEquals("Intermediate", detail.experience?.label)
        assertEquals(3, detail.experience?.starCount)
        assertEquals("B1/B2", detail.language?.label)
        assertTrue(detail.highSchooler)
        assertEquals("GA (France) slot 1", detail.assignment?.label)
        assertEquals("1f1eb-1f1f7", detail.assignment?.flagAssetName)
        assertEquals("committee", detail.assignment?.committeeId)
        assertEquals("france", detail.assignment?.positionId)
        assertTrue(detail.canDeactivate)
        assertEquals(listOf("#1 GA (France)", "#2 GA (Germany)"), detail.preferences.map { "#${it.rank} ${it.label}" })
        assertEquals(listOf("1f1eb-1f1f7", "1f1e9-1f1ea"), detail.preferences.map { it.flagAssetName })
        assertTrue(detail.preferences.first().isCurrentAssignment)
        assertEquals("Pending", detail.preferences.first().status.label)
        assertEquals(DelegateStatusTone.Yellow, detail.preferences.first().status.tone)
        assertEquals("Open", detail.preferences[1].status.label)
        assertEquals(DelegateStatusTone.Green, detail.preferences[1].status.tone)
        assertEquals(listOf("Motivation", "Visa"), detail.answers.map { it.label })
        assertEquals("No answer", detail.answers.single { it.label == "Motivation" }.answer)
        assertEquals("Yes", detail.answers.single { it.label == "Visa" }.answer)
        assertEquals(1, detail.profileWarningCount)
        assertEquals(0, detail.answerWarningCount)
        assertEquals(1, detail.answerNoticeCount)
        assertEquals(1, detail.resolvedAnswerIssueCount)
        assertEquals(0, detail.resolvedProfileIssueCount)
        assertEquals(listOf("Required"), detail.answers.single { it.label == "Motivation" }.resolvedIssues.map { it.message })
        assertEquals(listOf("question-visible"), detail.answers.single { it.label == "Motivation" }.resolvedIssues.map { it.questionId })
        assertEquals(listOf("answer-resolution"), detail.answers.single { it.label == "Motivation" }.resolvedIssues.map { it.resolutionId })
        assertEquals("Done", detail.answers.single { it.label == "Motivation" }.resolvedIssues.single().justification)
        assertEquals(listOf("Delegate is marked as a high schooler but is 21 years old."), detail.profileIssues.map { it.message })
        assertEquals(
            listOf("First note", "Resolved \"Required\". Justification: Done"),
            detail.comments.map { it.body },
        )
        assertEquals("answer-resolution", detail.comments.single { it.readOnly }.issueResolutionId)
        assertEquals("Delegation A - Finland", detail.delegation?.label)
    }

    @Test
    fun `delegate detail keeps profile validation counts and comments separate from answers`() {
        val board = board()
        val profileIssue = board.delegate.profileIssues(LocalDate.of(2026, 1, 1)).single()
        board.delegate.issueResolutions += AnswerIssueResolution(
            id = AnswerIssueResolutionId("profile-resolution"),
            delegate = board.delegate,
            questionId = null,
            issueKey = profileIssue.key,
            issueMessage = profileIssue.message,
            severity = profileIssue.severity,
            justification = "School status verified.",
            resolvedAt = Instant.EPOCH,
        )

        val detail = requireNotNull(board.conference.toDelegateDetailUiState(board.delegate.id.value))

        assertEquals(0, detail.profileWarningCount)
        assertEquals(1, detail.resolvedProfileIssueCount)
        assertEquals(0, detail.answerWarningCount)
        assertEquals(1, detail.answerNoticeCount)
        assertEquals(1, detail.resolvedAnswerIssueCount)
        assertTrue(detail.profileIssues.isEmpty())
        assertEquals(listOf("School status verified."), detail.resolvedProfileIssues.map { it.justification })
        assertEquals(listOf(null), detail.resolvedProfileIssues.map { it.questionId })
        assertEquals(listOf("profile-resolution"), detail.resolvedProfileIssues.map { it.resolutionId })
        assertTrue(detail.answers.all { answer -> answer.issues.none { it.message.contains("high schooler") } })
        assertEquals(
            listOf(
                "First note",
                "Resolved \"Required\". Justification: Done",
                "Resolved \"Delegate is marked as a high schooler but is 21 years old\". Justification: School status verified.",
            ),
            detail.comments.map { it.body },
        )
        assertTrue(detail.comments.single { it.issueResolutionId == "profile-resolution" }.readOnly)
    }

    @Test
    fun `hidden questions are omitted from delegate detail`() {
        val board = board()

        val detail = requireNotNull(board.conference.toDelegateDetailUiState(board.delegate.id.value))

        assertEquals(listOf("Motivation", "Visa"), detail.answers.map { it.label })
        assertFalse(detail.answers.any { it.label == "Hidden" })
        assertFalse(detail.answers.any { it.label == "Experience" || it.label == "Language" || it.label == "High schooler" })
    }

    @Test
    fun `confirmed delegate cannot be deactivated in delegate detail`() {
        val board = board()
        board.franceSlot.assign(board.delegate, AssignmentSource.Mymun, Instant.EPOCH)

        val detail = requireNotNull(board.conference.toDelegateDetailUiState(board.delegate.id.value))

        assertFalse(detail.canDeactivate)
    }

    @Test
    fun `delegate detail shows current reservation with source label`() {
        val board = board()
        val reservation = Reservation(
            id = ReservationId("reservation"),
            conference = board.conference,
            name = "Alpha Hold",
            highSchoolCount = 1,
            universityCount = 1,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        board.conference.reservations += reservation
        reservation.delegateLinks += ReservationDelegateLink(
            id = ReservationDelegateLinkId("reservation-link"),
            reservation = reservation,
            delegate = board.delegate,
            source = ReservationLinkSource.DelegationAuto,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )

        val detail = requireNotNull(board.conference.toDelegateDetailUiState(board.delegate.id.value))

        assertEquals("reservation", detail.reservation?.id)
        assertEquals("Alpha Hold", detail.reservation?.name)
        assertEquals("Delegation", detail.reservation?.sourceLabel)
    }

    @Test
    fun `missing delegate detail returns null`() {
        assertNull(board().conference.toDelegateDetailUiState("missing"))
    }

    private fun board(): Board {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val committee = Committee(CommitteeId("committee"), conference, "GA", slotsPerPosition = 1)
        val france = Position(PositionId("france"), committee, "France")
        val germany = Position(PositionId("germany"), committee, "Germany")
        val delegation = Delegation(
            id = DelegationId("delegation"),
            conference = conference,
            name = "Delegation A",
            country = "Finland",
            expectedDelegateCount = 4,
            currentDelegateCountFromMymun = 1,
        )
        val delegate = Delegate(
            id = DelegateId("delegate"),
            conference = conference,
            name = PersonName("Alex", "Kim"),
            email = "alex@example.test",
            profile = DelegateProfile(
                country = "Germany",
                city = "Berlin",
                streetAddress = "Street 1",
                zipCode = "10115",
                diet = "Vegan",
                phone = "+49",
                nationality = "German",
                dateOfBirth = LocalDate.of(2005, 1, 1),
                delegationNameFromMymun = "Delegation A",
                applicationStatus = "Accepted",
            ),
            paymentStatus = PaymentStatus.Paid,
            active = true,
            isHeadDelegate = true,
            delegation = delegation,
        )
        val visibleQuestion = Question(
            id = QuestionId("question-visible"),
            questionText = "Why?",
            organizerLabel = "Motivation",
            visibleInDelegateDetail = true,
            displayOrder = 0,
            rules = listOf(RequiredAnswer("Required")),
        )
        val visaQuestion = Question(
            id = QuestionId("question-visa"),
            questionText = "Visa?",
            organizerLabel = "Visa",
            visibleInDelegateDetail = true,
            displayOrder = 1,
            rules = listOf(NoticeWhenNonBlank("Visa support")),
        )
        val experienceQuestion = Question(
            id = QuestionId("question-experience"),
            questionText = "Experience",
            role = StandardQuestionRoles.ExperienceLevel,
            mappings = TypedAnswerMappings(
                munExperience = listOf(MunExperienceAnswerMapping("Some", MunExperienceLevel.Intermediate)),
            ),
        )
        val languageQuestion = Question(
            id = QuestionId("question-language"),
            questionText = "Language",
            role = StandardQuestionRoles.LanguageLevel,
            mappings = TypedAnswerMappings(
                languageLevel = listOf(LanguageLevelAnswerMapping("B1", LanguageLevel.IndependentUserB1B2)),
            ),
        )
        val highSchoolerQuestion = Question(
            id = QuestionId("question-high-schooler"),
            questionText = "High schooler",
            role = StandardQuestionRoles.HighSchoolerStatus,
            mappings = TypedAnswerMappings(
                highSchooler = listOf(HighSchoolerAnswerMapping("Yes", HighSchoolerStatus.Yes)),
            ),
        )
        val hiddenQuestion = Question(
            id = QuestionId("question-hidden"),
            questionText = "Hidden",
            visibleInDelegateDetail = false,
        )

        conference.committees += committee
        conference.delegations += delegation
        conference.delegates += delegate
        conference.questions += listOf(
            visibleQuestion,
            visaQuestion,
            experienceQuestion,
            languageQuestion,
            highSchoolerQuestion,
            hiddenQuestion,
        )
        committee.positions += listOf(france, germany)
        val franceSlot = Slot(SlotId("france-slot"), france, 1, Instant.EPOCH)
        france.slots += franceSlot
        germany.slots += Slot(SlotId("germany-slot"), germany, 1, Instant.EPOCH)
        delegate.preferences += Preference(PreferenceId("preference-1"), delegate, france, 1, "GA (France)")
        delegate.preferences += Preference(PreferenceId("preference-2"), delegate, germany, 2, "GA (Germany)")
        delegate.answers += QuestionAnswer(QuestionAnswerId("answer-1"), delegate, visibleQuestion, null)
        val resolvedIssue = AnswerIssue("Required", AnswerIssueSeverity.Warning)
        delegate.issueResolutions += AnswerIssueResolution(
            id = AnswerIssueResolutionId("answer-resolution"),
            delegate = delegate,
            questionId = visibleQuestion.id,
            issueKey = resolvedIssue.key,
            issueMessage = resolvedIssue.message,
            severity = resolvedIssue.severity,
            justification = "Done",
            resolvedAt = Instant.EPOCH,
        )
        delegate.answers += QuestionAnswer(QuestionAnswerId("answer-2"), delegate, hiddenQuestion, "Secret")
        delegate.answers += QuestionAnswer(QuestionAnswerId("answer-3"), delegate, visaQuestion, "Yes")
        delegate.answers += QuestionAnswer(QuestionAnswerId("answer-4"), delegate, experienceQuestion, "Some")
        delegate.answers += QuestionAnswer(QuestionAnswerId("answer-5"), delegate, languageQuestion, "B1")
        delegate.answers += QuestionAnswer(QuestionAnswerId("answer-6"), delegate, highSchoolerQuestion, "Yes")
        delegate.comments += DelegateComment(
            id = DelegateCommentId("comment"),
            delegate = delegate,
            body = "First note",
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )

        return Board(conference, delegate, franceSlot)
    }

    private data class Board(
        val conference: Conference,
        val delegate: Delegate,
        val franceSlot: Slot,
    )
}
