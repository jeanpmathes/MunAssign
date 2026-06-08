package org.munika.assignment.ui.viewmodel

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
