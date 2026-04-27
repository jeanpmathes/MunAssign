package org.munika.assignment.domain.model

/**
 * Expected semantic role for a configurable MyMUN question.
 *
 * The [id] is persisted, so changes must remain backward-compatible with stored data.
 */
data class QuestionRole(
    val id: String,
    val name: String,
)
