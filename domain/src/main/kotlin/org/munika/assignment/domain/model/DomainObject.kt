package org.munika.assignment.domain.model

/**
 * Marker for domain objects that carry stable application IDs.
 */
interface DomainObject<out ID> {
    val id: ID
}
