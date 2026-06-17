package org.munika.assignment.domain.model

/**
 * Organizer-managed assignment guidance for a committee.
 *
 * `Mapped` is used by IPC-style committees whose positions inherit their school level from
 * another non-mapped committee.
 */
enum class CommitteeMode {
    HighSchool,
    University,
    Mapped,
}
