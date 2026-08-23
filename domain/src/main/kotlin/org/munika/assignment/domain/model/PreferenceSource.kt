package org.munika.assignment.domain.model

/** Identifies whether an effective preference came directly from MyMUN or a local correction. */
enum class PreferenceSource {
    Mymun,
    OrganizerCompletion,
    OrganizerCorrection,
}
