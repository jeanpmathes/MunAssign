package org.munika.assignment.domain.model

import java.time.Instant

/**
 * Conference-owned filename identity accepted by a successful MyMUN update.
 *
 * Matching is based on the unordered set of normalized parts. Display values and their source
 * order are retained only so warnings and management UI remain understandable.
 */
data class MymunUpdateFilenameSignature(
    val id: MymunUpdateFilenameSignatureId,
    val parts: List<MymunUpdateFilenamePart>,
    val trustedAt: Instant,
) {
    init {
        require(parts.isNotEmpty()) { "A trusted MyMUN update filename signature must have at least one part." }
        require(parts.map { it.normalizedValue }.distinct().size == parts.size) {
            "A trusted MyMUN update filename signature must not contain duplicate normalized parts."
        }
    }

    val normalizedParts: Set<String>
        get() = parts.mapTo(linkedSetOf()) { it.normalizedValue }
}
