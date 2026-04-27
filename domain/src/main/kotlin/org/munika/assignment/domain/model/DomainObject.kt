package org.munika.assignment.domain.model

interface DomainObject<out ID> {
    val id: ID
}
