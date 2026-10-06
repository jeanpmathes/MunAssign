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
import org.munika.assignment.domain.model.MunExperienceAnswerMapping
import org.munika.assignment.domain.model.MunExperienceLevel
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
import org.munika.assignment.domain.model.RequiredAnswer
import org.munika.assignment.domain.model.Reservation
import org.munika.assignment.domain.model.ReservationDelegateLink
import org.munika.assignment.domain.model.ReservationDelegateLinkId
import org.munika.assignment.domain.model.ReservationId
import org.munika.assignment.domain.model.ReservationLinkSource
import org.munika.assignment.domain.model.Slot
import org.munika.assignment.domain.model.SlotId
import org.munika.assignment.domain.model.StandardQuestionHandlingTypes
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
        assertEquals(PaymentTier.Reduced, detail.paymentTier)
        assertEquals("Reduced tier", detail.paymentTierStatus?.label)
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
        assertFalse(detail.preferences.first().organizerCompleted)
        assertTrue(detail.preferences[1].organizerCompleted)
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
        val link = ReservationDelegateLink(
            id = ReservationDelegateLinkId("reservation-link"),
            reservation = reservation,
            delegate = board.delegate,
            source = ReservationLinkSource.DelegationAuto,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        reservation.delegateLinks += link
        link.attachToDelegate()

        val detail = requireNotNull(board.conference.toDelegateDetailUiState(board.delegate.id.value))

        assertEquals("reservation", detail.reservation?.id)
        assertEquals("Alpha Hold", detail.reservation?.name)
        assertEquals("Delegation", detail.reservation?.sourceLabel)
    }

    @Test
    fun `missing delegate detail returns null`() {
        assertNull(board().conference.toDelegateDetailUiState("missing"))
    }

    @Test
    fun `preference availability uses every slot and chooses the lowest open index`() {
        val board = board()
        val france = board.conference.positions.single { it.id.value == "france" }
        val occupant = board.addEligibleDelegate("occupant", "Blake")
        board.franceSlot.assign(occupant, AssignmentSource.Application, Instant.EPOCH)
        france.slots += Slot(SlotId("france-slot-3"), france, 3, Instant.EPOCH)
        france.slots += Slot(SlotId("france-slot-2"), france, 2, Instant.EPOCH)

        val detail = requireNotNull(board.conference.toDelegateDetailUiState(board.delegate.id.value))
        val preference = detail.preferences.single { it.positionId == "france" }

        assertEquals("Open", preference.status.label)
        assertEquals(2, preference.openSlotCount)
        assertEquals("france-slot-2", preference.openSlotId)
        assertTrue(preference.replaceablePendingSlots.isNotEmpty())
        assertTrue(preference.canAssign)
    }

    @Test
    fun `full preference exposes pending occupants but never confirmed occupants for replacement`() {
        val board = board()
        val france = board.conference.positions.single { it.id.value == "france" }
        val pending = board.addEligibleDelegate("pending", "Blake")
        val confirmed = board.addEligibleDelegate("confirmed", "Casey")
        board.franceSlot.assign(pending, AssignmentSource.Application, Instant.EPOCH)
        val confirmedSlot = Slot(SlotId("france-slot-2"), france, 2, Instant.EPOCH)
        france.slots += confirmedSlot
        confirmedSlot.assign(confirmed, AssignmentSource.Mymun, Instant.EPOCH)

        val detail = requireNotNull(board.conference.toDelegateDetailUiState(board.delegate.id.value))
        val preference = detail.preferences.single { it.positionId == "france" }

        assertEquals("Pending", preference.status.label)
        assertEquals(0, preference.openSlotCount)
        assertEquals(listOf("france-slot"), preference.replaceablePendingSlots.map { it.id })
        assertEquals(listOf("Blake Delegate"), preference.replaceablePendingSlots.map { it.delegateName })
        assertFalse(preference.replaceablePendingSlots.any { it.id == confirmedSlot.id.value })
    }

    @Test
    fun `assignment controls are available only to unassigned eligible delegates`() {
        val board = board()

        assertNotNull(board.conference.toDelegateDetailUiState(board.delegate.id.value)?.assignmentOptions)

        board.delegate.active = false
        assertNull(board.conference.toDelegateDetailUiState(board.delegate.id.value)?.assignmentOptions)

        board.delegate.active = true
        board.franceSlot.assign(board.delegate, AssignmentSource.Application, Instant.EPOCH)

        assertNull(board.conference.toDelegateDetailUiState(board.delegate.id.value)?.assignmentOptions)
    }

    @Test
    fun `assign other is shown without preferences and disabled when no positions are open`() {
        val board = board()
        board.delegate.preferences.clear()
        board.conference.positions.forEachIndexed { index, position ->
            val occupant = board.addEligibleDelegate("occupant-$index", "Occupant$index")
            position.slots.single().assign(occupant, AssignmentSource.Mymun, Instant.EPOCH)
        }

        val options = requireNotNull(
            board.conference.toDelegateDetailUiState(board.delegate.id.value)?.assignmentOptions,
        )

        assertTrue(options.showAssignOther)
        assertTrue(options.committees.isEmpty())
    }

    @Test
    fun `assignment committees sort by combined fit then preference then name`() {
        val fixture = assignmentOptionsFixture()

        val options = requireNotNull(
            fixture.conference.toDelegateDetailUiState(fixture.delegate.id.value)?.assignmentOptions,
        )

        assertTrue(options.showAssignOther)
        assertEquals(
            listOf("Zulu Fit", "Beta Preferred", "Alpha Preferred", "Aardvark Other", "Mapped"),
            options.committees.map { it.name },
        )
        assertEquals(listOf(1, 2), options.committees.single { it.name == "Beta Preferred" }.preferenceRanks)
    }

    @Test
    fun `open positions sort by combined fit then name and unknown school is not a fit`() {
        val fixture = assignmentOptionsFixture()
        val mapped = fixture.conference.committees.single { it.displayName == "Mapped" }
        val universityTarget = fixture.conference.committees.single { it.displayName == "Zulu Fit" }
        val highSchoolTarget = fixture.conference.committees.single { it.displayName == "Beta Preferred" }
        mapped.positions.single { it.name == "Charlie" }.setMappedCommittee(universityTarget)
        mapped.positions.single { it.name == "Zulu" }.setMappedCommittee(highSchoolTarget)

        val positions = requireNotNull(
            fixture.conference.toDelegateDetailUiState(fixture.delegate.id.value)?.assignmentOptions,
        ).committees.single { it.name == "Mapped" }.positions

        assertEquals(listOf("Charlie", "Alpha", "Zulu"), positions.map { it.label })
        assertEquals(
            listOf(DelegateSchoolFit.Match, DelegateSchoolFit.Unknown, DelegateSchoolFit.Mismatch),
            positions.map { it.schoolFit },
        )
        assertEquals(listOf(true, false, false), positions.map { it.fullyFits })
    }

    @Test
    fun `missing payment tier keeps positions selectable but outside the fit rank`() {
        val fixture = assignmentOptionsFixture(paymentTier = null)

        val positions = requireNotNull(
            fixture.conference.toDelegateDetailUiState(fixture.delegate.id.value)?.assignmentOptions,
        ).committees.flatMap { it.positions }

        assertTrue(positions.isNotEmpty())
        assertTrue(positions.none { it.fullyFits })
        assertTrue(positions.none { it.paymentTierKnown })
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
            paymentTier = PaymentTier.Reduced,
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
            handling = StandardQuestionHandlingTypes.ExpectedTypedAnswer,
            mappings = TypedAnswerMappings(
                munExperience = listOf(MunExperienceAnswerMapping("Some", MunExperienceLevel.Intermediate)),
            ),
        )
        val languageQuestion = Question(
            id = QuestionId("question-language"),
            questionText = "Language",
            role = StandardQuestionRoles.LanguageLevel,
            handling = StandardQuestionHandlingTypes.ExpectedTypedAnswer,
            mappings = TypedAnswerMappings(
                languageLevel = listOf(LanguageLevelAnswerMapping("B1", LanguageLevel.IndependentUserB1B2)),
            ),
        )
        val highSchoolerQuestion = Question(
            id = QuestionId("question-high-schooler"),
            questionText = "High schooler",
            role = StandardQuestionRoles.HighSchoolerStatus,
            handling = StandardQuestionHandlingTypes.ExpectedTypedAnswer,
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
        delegate.preferences += Preference(
            PreferenceId("preference-2"),
            delegate,
            germany,
            2,
            "GA (Germany)",
            PreferenceSource.OrganizerCompletion,
        )
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

    private fun assignmentOptionsFixture(paymentTier: PaymentTier? = PaymentTier.Full): AssignmentOptionsFixture {
        val conference = Conference(ConferenceId("options"), "Options", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val delegate = Delegate(
            id = DelegateId("options-delegate"),
            conference = conference,
            name = PersonName("Alex", "Kim"),
            email = null,
            profile = eligibleProfile(),
            paymentStatus = PaymentStatus.Paid,
            paymentTier = paymentTier,
            isHeadDelegate = false,
        )
        conference.delegates += delegate

        val fit = conference.addCommittee("fit", "Zulu Fit", CommitteeMode.University)
        fit.addOpenPosition("fit-position", "Open")
        val beta = conference.addCommittee("beta", "Beta Preferred", CommitteeMode.HighSchool)
        val betaFirst = beta.addOpenPosition("beta-first", "Preferred one")
        val betaSecond = beta.addOpenPosition("beta-second", "Preferred two")
        beta.addOpenPosition("beta-open", "Other")
        val alpha = conference.addCommittee("alpha", "Alpha Preferred", CommitteeMode.HighSchool)
        val alphaPreferred = alpha.addOpenPosition("alpha-preferred", "Preferred")
        alpha.addOpenPosition("alpha-open", "Other")
        conference.addCommittee("other", "Aardvark Other", CommitteeMode.HighSchool)
            .addOpenPosition("other-open", "Other")
        val mapped = conference.addCommittee("mapped", "Mapped", CommitteeMode.Mapped)
        mapped.addOpenPosition("mapped-unknown", "Alpha")
        mapped.addOpenPosition("mapped-fit", "Charlie")
        mapped.addOpenPosition("mapped-mismatch", "Zulu")

        delegate.preferences += Preference(PreferenceId("beta-pref-1"), delegate, betaFirst, 1, "")
        delegate.preferences += Preference(PreferenceId("beta-pref-2"), delegate, betaSecond, 2, "")
        delegate.preferences += Preference(PreferenceId("alpha-pref"), delegate, alphaPreferred, 3, "")
        occupy(betaFirst, conference, "beta-occupant-1")
        occupy(betaSecond, conference, "beta-occupant-2")
        occupy(alphaPreferred, conference, "alpha-occupant")

        return AssignmentOptionsFixture(conference, delegate)
    }

    private fun Conference.addCommittee(id: String, name: String, mode: CommitteeMode): Committee =
        Committee(CommitteeId(id), this, name, slotsPerPosition = 1, mode = mode).also { committees += it }

    private fun Committee.addOpenPosition(id: String, name: String): Position =
        Position(PositionId(id), this, name).also { position ->
            positions += position
            position.slots += Slot(SlotId("$id-slot"), position, 1, Instant.EPOCH)
        }

    private fun occupy(position: Position, conference: Conference, id: String) {
        val occupant = Delegate(
            id = DelegateId(id),
            conference = conference,
            name = PersonName(id, "Delegate"),
            email = null,
            profile = eligibleProfile(),
            paymentStatus = PaymentStatus.Paid,
            paymentTier = PaymentTier.Full,
            isHeadDelegate = false,
        )
        conference.delegates += occupant
        position.slots.single().assign(occupant, AssignmentSource.Application, Instant.EPOCH)
    }

    private data class Board(
        val conference: Conference,
        val delegate: Delegate,
        val franceSlot: Slot,
    )

    private fun Board.addEligibleDelegate(id: String, firstName: String): Delegate =
        Delegate(
            id = DelegateId(id),
            conference = conference,
            name = PersonName(firstName, "Delegate"),
            email = null,
            profile = eligibleProfile(),
            paymentStatus = PaymentStatus.Paid,
            paymentTier = PaymentTier.Full,
            isHeadDelegate = false,
        ).also { conference.delegates += it }

    private data class AssignmentOptionsFixture(
        val conference: Conference,
        val delegate: Delegate,
    )

    private fun eligibleProfile(): DelegateProfile =
        DelegateProfile(
            country = null,
            city = null,
            streetAddress = null,
            zipCode = null,
            diet = null,
            phone = null,
            nationality = null,
            dateOfBirth = null,
            delegationNameFromMymun = null,
            applicationStatus = "Accepted",
        )
}
