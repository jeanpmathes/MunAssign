package org.munika.assignment.domain.model

object DefaultTypedAnswerMappings {
    val munExperience: List<MunExperienceAnswerMapping> = listOf(
        MunExperienceAnswerMapping("First Time", MunExperienceLevel.FirstTime),
        MunExperienceAnswerMapping("Beginner", MunExperienceLevel.Beginner),
        MunExperienceAnswerMapping("Intermediate", MunExperienceLevel.Intermediate),
        MunExperienceAnswerMapping("Advanced", MunExperienceLevel.Advanced),
    )

    val highSchooler: List<HighSchoolerAnswerMapping> = listOf(
        HighSchoolerAnswerMapping("Yes", HighSchoolerStatus.Yes),
        HighSchoolerAnswerMapping("No", HighSchoolerStatus.No),
    )

    val languageLevel: List<LanguageLevelAnswerMapping> = listOf(
        LanguageLevelAnswerMapping("A1/A2 (Beginner)", LanguageLevel.BeginnerA1A2),
        LanguageLevelAnswerMapping("B1/B2 (Independent User)", LanguageLevel.IndependentUserB1B2),
        LanguageLevelAnswerMapping("C1/C2 (Proficient User)", LanguageLevel.ProficientUserC1C2),
    )
}
