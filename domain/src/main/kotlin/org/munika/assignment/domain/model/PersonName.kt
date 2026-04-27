package org.munika.assignment.domain.model

/**
 * Delegate name as provided by MyMUN.
 */
data class PersonName(
    val firstName: String,
    val lastName: String,
) {
    init {
        require(firstName.isNotBlank()) { "First name must not be blank." }
        require(lastName.isNotBlank()) { "Last name must not be blank." }
    }

    val displayName: String
        get() = "$firstName $lastName"
}
