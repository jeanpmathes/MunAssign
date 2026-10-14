package org.munika.assignment.ui.viewmodel

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.munika.assignment.domain.model.AnswerIssue
import org.munika.assignment.domain.model.AnswerIssueResolution
import org.munika.assignment.domain.model.AnswerIssueResolutionId
import org.munika.assignment.domain.model.AnswerIssueSeverity
import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.ConferenceId
import org.munika.assignment.domain.model.Delegate
import org.munika.assignment.domain.model.DelegateId
import org.munika.assignment.domain.model.DelegateProfile
import org.munika.assignment.domain.model.HighSchoolerStatus
import org.munika.assignment.domain.model.LanguageLevel
import org.munika.assignment.domain.model.LanguageLevelAnswerMapping
import org.munika.assignment.domain.model.PaymentStatus
import org.munika.assignment.domain.model.PersonName
import org.munika.assignment.domain.model.Question
import org.munika.assignment.domain.model.QuestionAnswer
import org.munika.assignment.domain.model.QuestionAnswerId
import org.munika.assignment.domain.model.QuestionId
import org.munika.assignment.domain.model.RequiredAnswer
import org.munika.assignment.domain.model.StandardQuestionHandlingTypes
import org.munika.assignment.domain.model.StandardQuestionRoles
import org.munika.assignment.domain.model.TypedAnswerMappings

class QuestionConfigurationUiModelsTest {
    @Test
    fun `editable role mappings include distinct observed nonblank answers as unmapped`() {
        val conference = conference()
        conference.addAnswer("delegate-1", " C1 ")
        conference.addAnswer("delegate-2", "c1")
        conference.addAnswer("delegate-3", "  ")

        val mappings = conference.toEditableQuestionConfigurationUiStates().single().languageLevelMappings

        assertEquals(listOf("B1", " C1 "), mappings.map { it.rawValue })
        assertEquals(LanguageLevel.IndependentUserB1B2.name, mappings[0].mappedValueId)
        assertNull(mappings[1].mappedValueId)
        assertTrue(mappings[1].isObserved)
    }

    @Test
    fun `configured mapping is not duplicated by normalized observed answer`() {
        val conference = conference()
        conference.addAnswer("delegate", " b1 ")

        val mappings = conference.toEditableQuestionConfigurationUiStates().single().languageLevelMappings

        assertEquals(listOf("B1"), mappings.map { it.rawValue })
        assertTrue(mappings.single().isObserved)
    }

    @Test
    fun `unmapped observed answer blocks validation and conversion`() {
        val conference = conference()
        conference.addAnswer("delegate", "C1")
        val editable = conference.toEditableQuestionConfigurationUiStates().single()

        assertTrue(editable.validationErrors().any { "mapping" in it.lowercase() })
        assertFailsWith<IllegalArgumentException> { editable.toConfiguration() }
    }

    @Test
    fun `removing observed mapping row cannot bypass validation`() {
        val conference = conference()
        conference.addAnswer("delegate", "C1")
        val editable = conference.toEditableQuestionConfigurationUiStates().single()
        val removed = editable.copy(
            languageLevelMappings = editable.languageLevelMappings.filterNot { it.rawValue == "C1" },
        )

        assertTrue(removed.validationErrors().any { "observed" in it.lowercase() })
        assertFailsWith<IllegalArgumentException> { removed.toConfiguration() }
    }

    @Test
    fun `renaming observed mapping row cannot bypass validation`() {
        val conference = conference()
        conference.addAnswer("delegate", "C1")
        val editable = conference.toEditableQuestionConfigurationUiStates().single()
        val renamed = editable.copy(
            languageLevelMappings = editable.languageLevelMappings.map { mapping ->
                if (mapping.rawValue == "C1") {
                    mapping.copy(
                        rawValue = "C2",
                        mappedValueId = LanguageLevel.ProficientUserC1C2.name,
                    )
                } else {
                    mapping
                }
            },
        )

        assertTrue(renamed.validationErrors().any { "observed" in it.lowercase() })
        assertFailsWith<IllegalArgumentException> { renamed.toConfiguration() }
    }

    @Test
    fun `selecting observed answer mapping produces typed configuration`() {
        val conference = conference()
        conference.addAnswer("delegate", "C1")
        val editable = conference.toEditableQuestionConfigurationUiStates().single()
        val selected = editable.copy(
            languageLevelMappings = editable.languageLevelMappings.map { mapping ->
                if (mapping.rawValue.trim() == "C1") {
                    mapping.copy(mappedValueId = LanguageLevel.ProficientUserC1C2.name)
                } else {
                    mapping
                }
            },
        )

        val mappings = selected.toConfiguration().mappings.languageLevel

        assertEquals(LanguageLevel.ProficientUserC1C2, mappings.single { it.rawValue == "C1" }.value)
    }

    @Test
    fun `switching role rehydrates observed answers for the selected mapping type`() {
        val conference = conference()
        conference.addAnswer("delegate", "Maybe")
        val languageState = conference.toEditableQuestionConfigurationUiStates().single()

        val highSchoolState = languageState.withRole(StandardQuestionRoles.HighSchoolerStatus.id)

        val observed = highSchoolState.highSchoolerMappings.single { it.rawValue == "Maybe" }
        assertTrue(observed.isObserved)
        assertNull(observed.mappedValueId)
        assertTrue(highSchoolState.languageLevelMappings.all { it.mappedValueId != null })
        assertTrue(highSchoolState.validationErrors().isNotEmpty())

        val selected = highSchoolState.copy(
            highSchoolerMappings = highSchoolState.highSchoolerMappings.map { mapping ->
                if (mapping.rawValue == "Maybe") mapping.copy(mappedValueId = HighSchoolerStatus.Yes.name) else mapping
            },
        )
        assertEquals(
            HighSchoolerStatus.Yes,
            selected.toConfiguration().mappings.highSchooler.single { it.rawValue == "Maybe" }.value,
        )
    }

    @Test
    fun `general question retains observed answers when switched to a role`() {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        conference.questions += Question(
            id = QuestionId("status"),
            questionText = "Question: Are you a high school student?",
        )
        conference.addAnswer("delegate", "Maybe")
        val generalState = conference.toEditableQuestionConfigurationUiStates().single()

        val highSchoolState = generalState.withRole(StandardQuestionRoles.HighSchoolerStatus.id)

        val observed = highSchoolState.highSchoolerMappings.single { it.rawValue == "Maybe" }
        assertTrue(observed.isObserved)
        assertNull(observed.mappedValueId)
        assertTrue(highSchoolState.validationErrors().any { "mapping" in it.lowercase() })
        assertFailsWith<IllegalArgumentException> { highSchoolState.toConfiguration() }

        val selected = highSchoolState.copy(
            highSchoolerMappings = highSchoolState.highSchoolerMappings.map { mapping ->
                if (mapping.rawValue == "Maybe") mapping.copy(mappedValueId = HighSchoolerStatus.Yes.name) else mapping
            },
        )
        assertEquals(
            HighSchoolerStatus.Yes,
            selected.toConfiguration().mappings.highSchooler.single { it.rawValue == "Maybe" }.value,
        )
    }

    @Test
    fun `editable question configuration validates selected mapping values`() {
        val conference = conference()
        val editable = conference.toEditableQuestionConfigurationUiStates().single().copy(
            languageLevelMappings = listOf(
                EditableAnswerMappingUiState("B1", LanguageLevel.IndependentUserB1B2.name),
                EditableAnswerMappingUiState(" b1 ", LanguageLevel.IndependentUserB1B2.name),
            ),
        )

        assertTrue(editable.validationErrors().any { "unique" in it })
    }

    @Test
    fun `editable question configuration converts enabled controls to domain configuration`() {
        val conference = conference()
        val editable = conference.toEditableQuestionConfigurationUiStates().single().copy(
            organizerLabel = " English ",
        )

        val configuration = editable.toConfiguration()

        assertEquals("English", configuration.organizerLabel)
        assertEquals(StandardQuestionRoles.LanguageLevel, configuration.role)
        assertEquals(StandardQuestionHandlingTypes.ExpectedTypedAnswer, configuration.handling)
        assertEquals(emptyList(), configuration.rules)
    }

    @Test
    fun `question ordering does not use organizer label`() {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        conference.questions += Question(
            id = QuestionId("b"),
            questionText = "Question: B",
            organizerLabel = "A label",
            displayOrder = 0,
        )
        conference.questions += Question(
            id = QuestionId("a"),
            questionText = "Question: A",
            organizerLabel = "Z label",
            displayOrder = 0,
        )

        val ordered = conference.toEditableQuestionConfigurationUiStates().map { it.questionText }

        assertEquals(listOf("Question: A", "Question: B"), ordered)
    }

    @Test
    fun `question summary counts only open issues`() {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val question = Question(
            id = QuestionId("visa"),
            questionText = "Question: Visa",
            rules = listOf(RequiredAnswer("Missing visa info")),
        )
        val delegate = Delegate(
            id = DelegateId("delegate"),
            conference = conference,
            name = PersonName("Ada", "Lovelace"),
            email = "ada@example.test",
            profile = DelegateProfile(null, null, null, null, null, null, null, null, null, null),
            paymentStatus = PaymentStatus.Paid,
            isHeadDelegate = false,
            submittedAt = Instant.EPOCH,
        )
        val issue = AnswerIssue("Missing visa info", AnswerIssueSeverity.Warning)
        conference.questions += question
        conference.delegates += delegate
        delegate.answers += QuestionAnswer(QuestionAnswerId("answer"), delegate, question, null)
        delegate.issueResolutions += AnswerIssueResolution(
            id = AnswerIssueResolutionId("resolution"),
            delegate = delegate,
            questionId = question.id,
            issueKey = issue.key,
            issueMessage = issue.message,
            severity = issue.severity,
            justification = "Checked manually",
            resolvedAt = Instant.EPOCH,
        )

        val summary = conference.toQuestionConfigurationListUiState().questions.single()

        assertEquals(0, summary.warningCount)
        assertEquals(0, summary.noticeCount)
    }

    @Test
    fun `versus visibility round trips independently of role changes`() {
        val conference = conference()
        val editable = conference.toEditableQuestionConfigurationUiStates().single()
            .copy(visibleInVersusView = true)
            .withRole(null)

        val configuration = editable.toConfiguration()

        assertTrue(configuration.visibleInVersusView)
        assertTrue(configuration.toEditableUiState().visibleInVersusView)
    }

    private fun conference(): Conference {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        conference.questions += Question(
            id = QuestionId("language"),
            questionText = "Question: What is your level of English?",
            role = StandardQuestionRoles.LanguageLevel,
            handling = StandardQuestionHandlingTypes.ExpectedTypedAnswer,
            mappings = TypedAnswerMappings(
                languageLevel = listOf(LanguageLevelAnswerMapping("B1", LanguageLevel.IndependentUserB1B2)),
            ),
        )
        return conference
    }

    private fun Conference.addAnswer(delegateId: String, rawAnswer: String?) {
        val question = questions.single()
        val delegate = Delegate(
            id = DelegateId(delegateId),
            conference = this,
            name = PersonName("Test", delegateId),
            email = "$delegateId@example.test",
            profile = DelegateProfile(null, null, null, null, null, null, null, null, null, null),
            paymentStatus = PaymentStatus.Paid,
            isHeadDelegate = false,
            submittedAt = Instant.EPOCH,
        )
        delegates += delegate
        delegate.answers += QuestionAnswer(QuestionAnswerId("answer-$delegateId"), delegate, question, rawAnswer)
    }
}
