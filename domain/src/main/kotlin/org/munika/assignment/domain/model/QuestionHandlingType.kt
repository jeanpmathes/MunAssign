package org.munika.assignment.domain.model

/**
 * Behavior category for rendering and evaluating a MyMUN question.
 *
 * The [id] is persisted in the database and should be treated as stable.
 */
data class QuestionHandlingType(
    val id: String,
    val name: String,
)
