package org.munika.assignment.data.excel

import java.time.LocalDate

/**
 * Parsed assignable delegate row from the MyMUN `Delegates` sheet.
 */
data class MymunDelegateRow(
    val rowNumber: Int,
    val firstName: String,
    val lastName: String,
    val email: String,
    val normalizedEmail: String,
    val applicationStatus: String?,
    val submittedAt: LocalDate?,
    val createdAt: LocalDate?,
    val feePaid: Boolean,
    val country: String?,
    val city: String?,
    val streetAddress: String?,
    val zipCode: String?,
    val diet: String?,
    val phone: String?,
    val nationality: String?,
    val dateOfBirth: LocalDate?,
    val delegationName: String?,
    val isHeadDelegate: Boolean,
    val mymunAssignedPositionName: String?,
    val mymunAssignedCommitteeName: String?,
) : MymunStagingObject() {
    val displayName: String = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")

    override fun displayString(): String {
        val assignment = mymunAssignedCommitteeName?.let { committee ->
            mymunAssignedPositionName?.let { position -> "$committee ($position)" }
        }
        return listOfNotNull(
            displayName.ifBlank { "<blank delegate>" },
            applicationStatus,
            assignment,
        ).joinToString(" | ")
    }
}
