package org.munika.assignment.domain.model

/**
 * Built-in question roles seeded for each conference and used by UI/domain helpers.
 */
object StandardQuestionRoles {
    val ExperienceLevel = QuestionRole("experience_level", "Experience level")
    val HighSchoolerStatus = QuestionRole("high_schooler_status", "High schooler status")
    val LanguageLevel = QuestionRole("language_level", "Language level")

    val all: List<QuestionRole> = listOf(ExperienceLevel, HighSchoolerStatus, LanguageLevel)

    fun fromId(id: String?): QuestionRole? =
        all.firstOrNull { it.id == id }
}
