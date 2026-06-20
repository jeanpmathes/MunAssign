package org.munika.assignment.domain.model

import kotlin.test.Test
import kotlin.test.assertFailsWith

class QuestionConfigurationTest {
    @Test
    fun `configuration rejects duplicate normalized typed mappings`() {
        assertFailsWith<IllegalArgumentException> {
            QuestionConfiguration(
                questionId = QuestionId("question"),
                questionText = "Question: MUN experience",
                role = StandardQuestionRoles.ExperienceLevel,
                handling = StandardQuestionHandlingTypes.ExpectedTypedAnswer,
                visibleInDelegateDetail = true,
                organizerLabel = null,
                displayOrder = 0,
                rules = emptyList(),
                mappings = TypedAnswerMappings(
                    munExperience = listOf(
                        MunExperienceAnswerMapping("Advanced", MunExperienceLevel.Advanced),
                        MunExperienceAnswerMapping(" advanced ", MunExperienceLevel.Advanced),
                    ),
                ),
            )
        }
    }

    @Test
    fun `configuration rejects invalid rule parameters`() {
        assertFailsWith<IllegalArgumentException> {
            QuestionConfiguration(
                questionId = QuestionId("question"),
                questionText = "Question: Why this committee?",
                role = null,
                handling = StandardQuestionHandlingTypes.CommitteeChoiceExplanation,
                visibleInDelegateDetail = true,
                organizerLabel = null,
                displayOrder = 0,
                rules = listOf(MinimumSentenceCount(0)),
                mappings = TypedAnswerMappings.defaults(),
            )
        }
    }

    @Test
    fun `role backed configuration rejects general handling archetype`() {
        assertFailsWith<IllegalArgumentException> {
            QuestionConfiguration(
                questionId = QuestionId("question"),
                questionText = "Question: MUN experience",
                role = StandardQuestionRoles.ExperienceLevel,
                handling = StandardQuestionHandlingTypes.VisaSupport,
                visibleInDelegateDetail = true,
                organizerLabel = null,
                displayOrder = 0,
                rules = emptyList(),
                mappings = TypedAnswerMappings.defaults(),
            )
        }
    }
}
