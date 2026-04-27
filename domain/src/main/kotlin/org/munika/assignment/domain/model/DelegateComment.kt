package org.munika.assignment.domain.model

import java.time.Instant

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
