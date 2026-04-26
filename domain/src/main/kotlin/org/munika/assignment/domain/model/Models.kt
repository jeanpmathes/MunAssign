package org.munika.assignment.domain.model

data class Conference(
    val id: String,
    val name: String,
)

data class Committee(
    val id: String,
    val conferenceId: String,
    val name: String,
    val abbreviation: String?,
)

data class Position(
    val id: String,
    val committeeId: String,
    val name: String,
    val slotCount: Int,
)

data class Slot(
    val id: String,
    val positionId: String,
    val status: SlotStatus,
    val delegateId: String?,
)

enum class SlotStatus {
    Empty,
    Pending,
    Assigned,
}

data class Delegate(
    val id: String,
    val conferenceId: String,
    val name: String,
    val email: String,
    val comments: String,
    val active: Boolean,
    val paid: Boolean,
    val difficultyScore: Int,
)

data class Preference(
    val id: String,
    val delegateId: String,
    val positionId: String,
    val rank: Int,
)
