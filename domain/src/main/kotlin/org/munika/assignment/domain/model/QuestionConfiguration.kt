package org.munika.assignment.domain.model

/**
 * Organizer-managed configuration for an imported MyMUN question.
 *
 * The question text comes from MyMUN and is included so callers can show/edit one complete
 * configuration object without taking ownership of the imported text.
 */
data class QuestionConfiguration(
    val questionId: QuestionId,
    val questionText: String,
    val role: QuestionRole?,
    val handling: QuestionHandlingType,
    val visibleInDelegateDetail: Boolean,
    val organizerLabel: String?,
    val displayOrder: Int,
    val rules: List<AnswerRule>,
    val mappings: TypedAnswerMappings,
) {
    init {
        require(questionText.isNotBlank()) { "Question text must not be blank." }
        require(displayOrder >= 0) { "Question display order must not be negative." }
        require(role == null || role in StandardQuestionRoles.all) { "Unknown question role: ${role?.id}." }
        require(handling in StandardQuestionHandlingTypes.all) { "Unknown question handling type: ${handling.id}." }
        require(role == null || handling == StandardQuestionHandlingTypes.ExpectedTypedAnswer) {
            "Role-backed questions must use expected typed-answer handling."
        }
        validateRules()
        validateMappings("MUN experience", mappings.munExperience.map { it.rawValue })
        validateMappings("High schooler", mappings.highSchooler.map { it.rawValue })
        validateMappings("Language level", mappings.languageLevel.map { it.rawValue })
    }

    private fun validateRules() {
        rules.forEach { rule ->
            when (rule) {
                is MinimumSentenceCount -> require(rule.count > 0) {
                    "Minimum sentence count must be greater than zero."
                }
                is MinimumTextLength -> require(rule.length > 0) {
                    "Minimum text length must be greater than zero."
                }
                is NoticeWhenNonBlank -> require(rule.message.isNotBlank()) {
                    "Notice message must not be blank."
                }
                is NoticeWhenAffirmative -> require(rule.message.isNotBlank()) {
                    "Notice message must not be blank."
                }
                is RequiredAnswer -> require(rule.message.isNotBlank()) {
                    "Required-answer message must not be blank."
                }
            }
        }
    }

    private fun validateMappings(label: String, rawValues: List<String>) {
        rawValues.forEach { rawValue ->
            require(rawValue.isNotBlank()) { "$label mapping raw value must not be blank." }
        }
        val normalizedValues = rawValues.map(::normalizeAnswer)
        require(normalizedValues.size == normalizedValues.toSet().size) {
            "$label mapping raw values must be unique."
        }
    }
}

fun Question.toConfiguration(): QuestionConfiguration =
    QuestionConfiguration(
        questionId = id,
        questionText = questionText,
        role = role,
        handling = handling,
        visibleInDelegateDetail = visibleInDelegateDetail,
        organizerLabel = organizerLabel,
        displayOrder = displayOrder,
        rules = rules,
        mappings = mappings,
    )
