package org.munika.assignment.domain.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
