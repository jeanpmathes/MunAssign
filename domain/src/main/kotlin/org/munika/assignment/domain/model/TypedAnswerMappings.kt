package org.munika.assignment.domain.model

/**
 * Role-specific mappings from raw MyMUN answers to typed domain values.
 *
 * Separate lists prevent accidentally mapping values across incompatible question roles.
 */
data class TypedAnswerMappings(
    val munExperience: List<MunExperienceAnswerMapping> = DefaultTypedAnswerMappings.munExperience,
    val highSchooler: List<HighSchoolerAnswerMapping> = DefaultTypedAnswerMappings.highSchooler,
    val languageLevel: List<LanguageLevelAnswerMapping> = DefaultTypedAnswerMappings.languageLevel,
) {
    companion object {
        fun defaults(): TypedAnswerMappings = TypedAnswerMappings()
    }
}
