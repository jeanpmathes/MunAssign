package org.munika.assignment.data.excel

import java.time.LocalDate

/**
 * Parsed delegation context row from the optional MyMUN `Delegations` sheet.
 */
data class MymunDelegationRow(
    val rowNumber: Int,
    val name: String,
    val country: String?,
    val currentDelegates: Int?,
    val expectedDelegates: Int?,
    val applicationStatus: String?,
    val submittedAt: LocalDate?,
    val createdAt: LocalDate?,
) : MymunStagingObject() {
    override fun displayString(): String =
        listOfNotNull(
            name,
            country,
            currentDelegates?.let { current -> expectedDelegates?.let { expected -> "$current/$expected delegates" } },
        ).joinToString(" | ")
}
