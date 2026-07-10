package org.munika.assignment.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
            handling = StandardQuestionHandlingTypes.ExpectedTypedAnswer,
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
    fun `profile issues warn when language level is beginner`() {
        val delegate = delegateWithLanguageLevel(LanguageLevel.BeginnerA1A2)

        assertEquals(
            listOf(
                AnswerIssue(
                    message = "Delegate language level is A1/A2.",
                    severity = AnswerIssueSeverity.Warning,
                    key = "Warning:profile_low_language_level",
                ),
            ),
            delegate.profileIssues(Today),
        )
    }

    @Test
    fun `profile issues do not warn for non beginner missing or unmapped language levels`() {
        assertTrue(delegateWithLanguageLevel(LanguageLevel.IndependentUserB1B2).profileIssues(Today).isEmpty())
        assertTrue(delegateWithLanguageLevel(LanguageLevel.ProficientUserC1C2).profileIssues(Today).isEmpty())
        assertTrue(delegateWithLanguageLevel(null).profileIssues(Today).isEmpty())
        assertTrue(delegateWithUnmappedLanguageAnswer().profileIssues(Today).isEmpty())
    }

    @Test
    fun `resolved beginner language profile issue is not open`() {
        val delegate = delegateWithLanguageLevel(LanguageLevel.BeginnerA1A2)
        val issue = delegate.profileIssues(Today).single()
        delegate.issueResolutions += AnswerIssueResolution(
            id = AnswerIssueResolutionId("language-resolution"),
            delegate = delegate,
            questionId = null,
            issueKey = issue.key,
            issueMessage = issue.message,
            severity = issue.severity,
            justification = "Language checked manually.",
            resolvedAt = Instant.EPOCH,
        )

        assertTrue(delegate.openProfileIssues(Today).isEmpty())
        assertEquals("Language checked manually.", delegate.resolvedProfileIssues(Today).single().resolution.justification)
    }

    @Test
    fun `application submitted text uses singular day and clamps clock skew to zero`() {
        val conference = Conference(ConferenceId("conference"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val zoneId = ZoneId.of("UTC")
        val now = Instant.parse("2026-05-15T00:00:00Z")

        fun delegateSubmittedAt(instant: Instant) = Delegate(
            id = DelegateId("delegate"),
            conference = conference,
            name = PersonName("Ada", "Lovelace"),
            email = "ada@example.test",
            profile = DelegateProfile(null, null, null, null, null, null, null, null, null, null),
            paymentStatus = PaymentStatus.Paid,
            isHeadDelegate = false,
            submittedAt = instant,
        )

        assertEquals(
            "Application submitted 1 day ago",
            delegateSubmittedAt(now.minus(1, java.time.temporal.ChronoUnit.DAYS)).applicationSubmittedText(now, zoneId),
        )
        assertEquals(
            "Application submitted 3 days ago",
            delegateSubmittedAt(now.minus(3, java.time.temporal.ChronoUnit.DAYS)).applicationSubmittedText(now, zoneId),
        )
        assertEquals(
            "Application submitted 0 days ago",
            delegateSubmittedAt(now.plus(1, java.time.temporal.ChronoUnit.DAYS)).applicationSubmittedText(now, zoneId),
        )
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
            handling = StandardQuestionHandlingTypes.ExpectedTypedAnswer,
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

    private fun delegateWithLanguageLevel(level: LanguageLevel?): Delegate {
        val conference = Conference(ConferenceId("conference-language-$level"), "KAMUN", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
        val delegate = Delegate(
            id = DelegateId("delegate-language-$level"),
            conference = conference,
            name = PersonName("Ada", "Lovelace"),
            email = "ada@example.test",
            profile = DelegateProfile(null, null, null, null, null, null, null, null, null, null),
            paymentStatus = PaymentStatus.Paid,
            isHeadDelegate = false,
        )
        val mappedLevel = level ?: return delegate
        val question = Question(
            id = QuestionId("question-language-$level"),
            questionText = "Language",
            role = StandardQuestionRoles.LanguageLevel,
            handling = StandardQuestionHandlingTypes.ExpectedTypedAnswer,
            mappings = TypedAnswerMappings(languageLevel = listOf(LanguageLevelAnswerMapping("Answer", mappedLevel))),
        )
        delegate.answers += QuestionAnswer(
            id = QuestionAnswerId("answer-language-$level"),
            delegate = delegate,
            question = question,
            rawAnswer = "Answer",
        )
        return delegate
    }

    private fun delegateWithUnmappedLanguageAnswer(): Delegate {
        val delegate = delegateWithLanguageLevel(LanguageLevel.BeginnerA1A2)
        delegate.answers.clear()
        val question = Question(
            id = QuestionId("question-language-unmapped"),
            questionText = "Language",
            role = StandardQuestionRoles.LanguageLevel,
            handling = StandardQuestionHandlingTypes.ExpectedTypedAnswer,
            mappings = TypedAnswerMappings(languageLevel = listOf(LanguageLevelAnswerMapping("Mapped", LanguageLevel.BeginnerA1A2))),
        )
        delegate.answers += QuestionAnswer(
            id = QuestionAnswerId("answer-language-unmapped"),
            delegate = delegate,
            question = question,
            rawAnswer = "Different",
        )
        return delegate
    }

    private companion object {
        val Today: LocalDate = LocalDate.of(2026, 5, 15)
    }
}
