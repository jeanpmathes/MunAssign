package org.munika.assignment.domain.model

import java.time.LocalDate

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
)
