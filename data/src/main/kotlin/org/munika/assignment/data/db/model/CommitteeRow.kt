package org.munika.assignment.data.db.model

import org.munika.assignment.domain.model.PaymentTier

data class CommitteeRow(
    val id: String,
    val conferenceId: String,
    val mymunName: String,
    val alias: String?,
    val slotsPerPosition: Int,
    val isHighSchoolCommittee: Boolean,
    val mode: String,
    val paymentTier: String = PaymentTier.Full.name,
)
