package org.munika.assignment.data.db.tables

import org.jetbrains.exposed.v1.core.Table

/**
 * All Exposed tables managed by the application schema.
 */
val MunAssignTables: Array<Table> = arrayOf(
    SchemaMigrationTable,
    MunAssignFileMetadataTable,
    ConferenceTable,
    MymunUpdateFilenameSignatureTable,
    MymunUpdateFilenamePartTable,
    CommitteeTable,
    PositionTable,
    DelegationTable,
    DelegateTable,
    ReservationTable,
    ReservationDelegateLinkTable,
    SlotTable,
    PreferenceTable,
    PreferenceCompletionTable,
    DelegateCommentTable,
    QuestionRoleTable,
    QuestionHandlingTypeTable,
    DelegateQuestionTable,
    AnswerRuleTypeTable,
    DelegateQuestionRuleTable,
    MunExperienceAnswerMappingTable,
    HighSchoolerAnswerMappingTable,
    LanguageLevelAnswerMappingTable,
    DelegateQuestionAnswerTable,
    AnswerIssueResolutionTable,
    MymunUpdateRunTable,
    MymunUpdateIssueTable,
)
