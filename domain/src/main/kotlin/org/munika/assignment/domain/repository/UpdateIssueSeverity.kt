package org.munika.assignment.domain.repository

/**
 * Severity of a MyMUN update issue.
 *
 * Errors block all workbook writes for the attempted update. Warnings are persisted and returned
 * with a successful update so organizers can inspect potentially surprising workbook data.
 */
enum class UpdateIssueSeverity {
    Warning,
    Error,
}
