package org.munika.assignment.domain.model

/**
 * Built-in question handling types seeded for each conference.
 */
object StandardQuestionHandlingTypes {
    val Default = QuestionHandlingType("default", "Default")
    val ExpectedTypedAnswer = QuestionHandlingType("expected_typed_answer", "Expected typed answer")
    val CommitteeChoiceExplanation = QuestionHandlingType("committee_choice_explanation", "Committee choice explanation")
    val VisaSupport = QuestionHandlingType("visa_support", "Visa support")
    val DelegationMembership = QuestionHandlingType("delegation_membership", "Delegation membership")

    val all: List<QuestionHandlingType> = listOf(
        Default,
        ExpectedTypedAnswer,
        CommitteeChoiceExplanation,
        VisaSupport,
        DelegationMembership,
    )

    fun fromId(id: String?): QuestionHandlingType =
        all.firstOrNull { it.id == id } ?: Default
}
