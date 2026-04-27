package org.munika.assignment.data.db.tables

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object DelegateQuestionTable : Table("delegate_questions") {
    val id = varchar("id", 36)
    val conferenceId = varchar("conference_id", 36).references(ConferenceTable.id, onDelete = ReferenceOption.CASCADE)
    val questionText = text("question_text")
    val normalizedQuestionText = text("normalized_question_text")
    val roleId = varchar("role_id", 64).nullable()
    val handlingTypeId = varchar("handling_type_id", 64).references(QuestionHandlingTypeTable.id)
    val visibleInDelegateDetail = bool("visible_in_delegate_detail")
    val organizerLabel = varchar("organizer_label", 255).nullable()

    override val primaryKey = PrimaryKey(id)
}
