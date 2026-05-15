package org.munika.assignment.domain.model

/**
 * Imported MyMUN question text plus organizer-managed handling configuration.
 *
 * The question identity is stable; roles, handling, rules, visibility, labels, and mappings are
 * local configuration that can change without recreating imported answers.
 */
class Question(
    override val id: QuestionId,
    val questionText: String,
    val role: QuestionRole? = null,
    val handling: QuestionHandlingType = StandardQuestionHandlingTypes.Default,
    val visibleInDelegateDetail: Boolean = true,
    val organizerLabel: String? = null,
    val displayOrder: Int = 0,
    val rules: List<AnswerRule> = emptyList(),
    val mappings: TypedAnswerMappings = TypedAnswerMappings.defaults(),
) : DomainObject<QuestionId> {
    init {
        require(questionText.isNotBlank()) { "Question text must not be blank." }
    }

    val displayLabel: String
        get() = organizerLabel?.takeIf { it.isNotBlank() } ?: questionText

    fun issuesFor(answer: String?): List<AnswerIssue> =
        rules.mapNotNull { it.evaluate(answer) }

    fun mapMunExperience(rawAnswer: String?): MunExperienceLevel? {
        if (role != StandardQuestionRoles.ExperienceLevel) return null
        return mappings.munExperience.firstOrNull { normalizeAnswer(it.rawValue) == normalizeAnswer(rawAnswer) }?.value
    }

    fun mapHighSchoolerStatus(rawAnswer: String?): HighSchoolerStatus? {
        if (role != StandardQuestionRoles.HighSchoolerStatus) return null
        return mappings.highSchooler.firstOrNull { normalizeAnswer(it.rawValue) == normalizeAnswer(rawAnswer) }?.value
    }

    fun mapLanguageLevel(rawAnswer: String?): LanguageLevel? {
        if (role != StandardQuestionRoles.LanguageLevel) return null
        return mappings.languageLevel.firstOrNull { normalizeAnswer(it.rawValue) == normalizeAnswer(rawAnswer) }?.value
    }
}
