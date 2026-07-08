package org.munika.assignment.domain.model

import java.time.LocalDate

/**
 * MyMUN-managed profile fields for a delegate.
 */
data class DelegateProfile(
    val country: String?,
    val city: String?,
    val streetAddress: String?,
    val zipCode: String?,
    val diet: String?,
    val phone: String?,
    val nationality: String?,
    val dateOfBirth: LocalDate?,
    val delegationNameFromMymun: String?,
    val applicationStatus: String?,
) {
    /**
     * Whether [applicationStatus] represents a delegate who can be assigned, counted toward
     * capacity, and consume reservation capacity.
     */
    val isUsableForAssignment: Boolean
        get() = isUsableApplicationStatus(applicationStatus)
}

private val UsableApplicationStatuses = setOf("accepted", "assigned", "checked in")

/**
 * Whether a raw MyMUN application status represents a usable (assignable) delegate.
 *
 * Checked-in delegates come after assigned in the MyMUN pipeline and remain usable.
 */
fun isUsableApplicationStatus(status: String?): Boolean =
    status?.trim()?.lowercase() in UsableApplicationStatuses
