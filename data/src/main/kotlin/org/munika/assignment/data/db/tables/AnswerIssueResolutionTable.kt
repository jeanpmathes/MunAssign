package org.munika.assignment.data.db.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object AnswerIssueResolutionTable : Table("answer_issue_resolutions") {
    val id = varchar("id", 36)
    val conferenceId = varchar("conference_id", 36).references(ConferenceTable.id, onDelete = ReferenceOption.CASCADE)
    val delegateId = varchar("delegate_id", 36).references(DelegateTable.id, onDelete = ReferenceOption.CASCADE)
    val questionId = varchar("question_id", 36).references(DelegateQuestionTable.id, onDelete = ReferenceOption.CASCADE).nullable()
    val issueKey = varchar("issue_key", 512)
    val issueMessage = text("issue_message")
    val severity = varchar("severity", 16)
    val justification = text("justification").nullable()
    val resolvedAtEpochMillis = long("resolved_at_epoch_millis")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(
            "ux_answer_issue_resolutions_scope",
            conferenceId,
            delegateId,
            questionId,
            issueKey,
        )
    }
}
