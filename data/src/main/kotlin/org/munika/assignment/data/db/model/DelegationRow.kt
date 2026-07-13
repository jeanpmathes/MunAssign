package org.munika.assignment.data.db.model

data class DelegationRow(
    val id: String,
    val conferenceId: String,
    val name: String,
    val country: String?,
    val expectedDelegateCount: Int?,
    val currentDelegateCountFromMymun: Int?,
    val headDelegateId: String?,
    val isHighSchoolDelegation: Boolean = false,
    val needsReview: Boolean = false,
)
