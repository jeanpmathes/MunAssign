package org.munika.assignment.domain.model

/**
 * Organizer-managed price category used to guide committee assignments.
 *
 * The labels intentionally carry no monetary amount or currency. Persisted enum names are part of
 * the `.munassign` transfer contract.
 */
enum class PaymentTier {
    Full,
    Reduced,
}
