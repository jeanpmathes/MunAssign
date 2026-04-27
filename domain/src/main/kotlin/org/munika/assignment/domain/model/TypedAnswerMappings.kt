package org.munika.assignment.domain.model

data class TypedAnswerMappings(
    val munExperience: List<MunExperienceAnswerMapping> = DefaultTypedAnswerMappings.munExperience,
    val highSchooler: List<HighSchoolerAnswerMapping> = DefaultTypedAnswerMappings.highSchooler,
    val languageLevel: List<LanguageLevelAnswerMapping> = DefaultTypedAnswerMappings.languageLevel,
) {
    companion object {
        fun defaults(): TypedAnswerMappings = TypedAnswerMappings()
    }
}
