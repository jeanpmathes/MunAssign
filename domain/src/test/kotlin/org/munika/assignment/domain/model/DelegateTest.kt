package org.munika.assignment.domain.model

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DelegateTest {
    @Test
    fun `typed language answer is exposed on delegate`() {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val delegate = Delegate(
            id = DelegateId("delegate"),
            conference = conference,
            name = PersonName("Ada", "Lovelace"),
            email = "ada@example.test",
            profile = DelegateProfile(null, null, null, null, null, null, null, null, null, null),
            paymentStatus = PaymentStatus.Paid,
            isHeadDelegate = false,
        )
        val question = Question(
            id = QuestionId("question-language"),
            questionText = "Question: What is your level of English?",
            role = StandardQuestionRoles.LanguageLevel,
        )

        delegate.answers += QuestionAnswer(
            id = QuestionAnswerId("answer-language"),
            delegate = delegate,
            question = question,
            rawAnswer = " A1/A2 (Beginner) ",
        )

        assertEquals(LanguageLevel.BeginnerA1A2, delegate.languageLevel)
    }

    @Test
    fun `profile issues warn when high school status does not match age`() {
        assertTrue(delegateWithAgeAndHighSchoolStatus(17, HighSchoolerStatus.Yes).profileIssues(Today).isEmpty())
        assertTrue(delegateWithAgeAndHighSchoolStatus(18, HighSchoolerStatus.Yes).profileIssues(Today).isEmpty())
        assertEquals(
            listOf(
                AnswerIssue(
                    message = "Delegate is marked as a high schooler but is 19 years old.",
                    severity = AnswerIssueSeverity.Warning,
                    key = "Warning:profile_high_schooler_over_age",
                ),
            ),
            delegateWithAgeAndHighSchoolStatus(19, HighSchoolerStatus.Yes).profileIssues(Today),
        )
        assertEquals(
            listOf(
                AnswerIssue(
                    message = "Delegate is 17 years old but is not marked as a high schooler.",
                    severity = AnswerIssueSeverity.Warning,
                    key = "Warning:profile_minor_not_high_schooler",
                ),
            ),
            delegateWithAgeAndHighSchoolStatus(17, HighSchoolerStatus.No).profileIssues(Today),
        )
        assertEquals(
            listOf(
                AnswerIssue(
                    message = "Delegate is 17 years old but is not marked as a high schooler.",
                    severity = AnswerIssueSeverity.Warning,
                    key = "Warning:profile_minor_not_high_schooler",
                ),
            ),
            delegateWithAgeAndHighSchoolStatus(17, null).profileIssues(Today),
        )
        assertTrue(delegateWithAgeAndHighSchoolStatus(null, HighSchoolerStatus.Yes).profileIssues(Today).isEmpty())
    }

    @Test
    fun `resolved profile issues stay resolved when age-specific message changes`() {
        val delegate = delegateWithAgeAndHighSchoolStatus(19, HighSchoolerStatus.Yes)
        val issue = delegate.profileIssues(Today).single()
        delegate.issueResolutions += AnswerIssueResolution(
            id = AnswerIssueResolutionId("resolution"),
            delegate = delegate,
            questionId = null,
            issueKey = issue.key,
            issueMessage = issue.message,
            severity = issue.severity,
            justification = "Confirmed manually.",
            resolvedAt = Instant.EPOCH,
        )

        val nextYear = Today.plusYears(1)

        assertTrue(delegate.openProfileIssues(nextYear).isEmpty())
        assertEquals("Delegate is marked as a high schooler but is 20 years old.", delegate.resolvedProfileIssues(nextYear).single().issue.message)
        assertEquals("Confirmed manually.", delegate.resolvedProfileIssues(nextYear).single().resolution.justification)
    }

    private fun delegateWithAgeAndHighSchoolStatus(age: Int?, status: HighSchoolerStatus?): Delegate {
        val conference = Conference(ConferenceId("conference-$age-$status"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val delegate = Delegate(
            id = DelegateId("delegate-$age-$status"),
            conference = conference,
            name = PersonName("Ada", "Lovelace"),
            email = "ada@example.test",
            profile = DelegateProfile(
                country = null,
                city = null,
                streetAddress = null,
                zipCode = null,
                diet = null,
                phone = null,
                nationality = null,
                dateOfBirth = age?.let { Today.minusYears(it.toLong()) },
                delegationNameFromMymun = null,
                applicationStatus = null,
            ),
            paymentStatus = PaymentStatus.Paid,
            isHeadDelegate = false,
        )
        val mappedStatus = status ?: return delegate
        val question = Question(
            id = QuestionId("question-hs-$age-$status"),
            questionText = "High schooler",
            role = StandardQuestionRoles.HighSchoolerStatus,
            mappings = TypedAnswerMappings(highSchooler = listOf(HighSchoolerAnswerMapping("Answer", mappedStatus))),
        )
        delegate.answers += QuestionAnswer(
            id = QuestionAnswerId("answer-hs-$age-$status"),
            delegate = delegate,
            question = question,
            rawAnswer = "Answer",
        )
        return delegate
    }

    private companion object {
        val Today: LocalDate = LocalDate.of(2026, 5, 15)
    }
}
