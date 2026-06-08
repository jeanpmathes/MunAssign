package org.munika.assignment.data.db.model

data class ConferenceGraphRows(
    val conference: ConferenceRow,
    val committees: List<CommitteeRow>,
    val positions: List<PositionRow>,
    val slots: List<SlotRow>,
    val delegations: List<DelegationRow>,
    val delegates: List<DelegateRow>,
    val preferences: List<PreferenceRow>,
    val comments: List<DelegateCommentRow>,
    val questions: List<DelegateQuestionRow>,
    val questionRules: List<DelegateQuestionRuleRow>,
    val munExperienceMappings: List<MunExperienceAnswerMappingRow>,
    val highSchoolerMappings: List<HighSchoolerAnswerMappingRow>,
    val languageLevelMappings: List<LanguageLevelAnswerMappingRow>,
    val answers: List<DelegateQuestionAnswerRow>,
    val issueResolutions: List<AnswerIssueResolutionRow>,
)
