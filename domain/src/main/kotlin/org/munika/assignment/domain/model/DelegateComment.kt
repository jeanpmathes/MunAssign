package org.munika.assignment.domain.model

import java.time.Instant

/**
 * Organizer-managed note attached to a delegate.
 *
 * Comments are preserved across MyMUN updates while the delegate still exists.
 */
class DelegateComment(
    override val id: DelegateCommentId,
    val delegate: Delegate,
    val body: String,
    val createdAt: Instant,
    var updatedAt: Instant,
) : DomainObject<DelegateCommentId> {
    init {
        require(body.isNotBlank()) { "Comment body must not be blank." }
    }
}
