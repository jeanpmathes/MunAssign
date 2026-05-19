package org.munika.assignment.data.db.model

data class CommitteeRow(
    val id: String,
    val conferenceId: String,
    val mymunName: String,
    val alias: String?,
    val slotsPerPosition: Int,
    val isHighSchoolCommittee: Boolean,
)
