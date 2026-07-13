package org.munika.assignment.domain.model

import java.time.Instant
import java.util.Locale

/** Normalized filename parts extracted before a MyMUN workbook update is allowed to proceed. */
data class MymunUpdateFilenameCandidate(
    val parts: List<MymunUpdateFilenamePart>,
) {
    val normalizedParts: Set<String>
        get() = parts.mapTo(linkedSetOf()) { it.normalizedValue }

    /** Compares this candidate against every trusted signature for the selected conference. */
    fun assess(trustedSignatures: List<MymunUpdateFilenameSignature>): MymunUpdateFilenameAssessment {
        if (parts.isEmpty()) {
            return MymunUpdateFilenameAssessment.Empty(this)
        }
        if (trustedSignatures.isEmpty()) {
            return MymunUpdateFilenameAssessment.FirstUse(this)
        }
        trustedSignatures.firstOrNull { it.normalizedParts == normalizedParts }?.let { signature ->
            return MymunUpdateFilenameAssessment.Match(this, signature)
        }

        val closest = trustedSignatures.sortedWith(
            compareBy<MymunUpdateFilenameSignature> { signature ->
                (signature.normalizedParts - normalizedParts).size + (normalizedParts - signature.normalizedParts).size
            }.thenByDescending { it.trustedAt },
        ).first()
        return MymunUpdateFilenameAssessment.Mismatch(
            candidate = this,
            closestSignature = closest,
            missingParts = closest.parts
                .filter { it.normalizedValue !in normalizedParts }
                .map { it.displayValue },
            unexpectedParts = parts
                .filter { it.normalizedValue !in closest.normalizedParts }
                .map { it.displayValue },
        )
    }

    /** Attaches persistence identity after this candidate has been accepted by a successful update. */
    fun withIdentity(
        id: MymunUpdateFilenameSignatureId,
        trustedAt: Instant,
    ): MymunUpdateFilenameSignature = MymunUpdateFilenameSignature(id, parts, trustedAt)

    companion object {
        private val workbookExtension = Regex("\\.(xlsx|xlsm|xls)$", RegexOption.IGNORE_CASE)
        private val whitespace = Regex("\\s+")
        private val numericBlock = Regex("\\d+")
        private val ignoredBlocks = setOf("mymun", "applications", "export")

        /** Extracts meaningful parts without retaining the complete source filename. */
        fun fromFilename(filename: String): MymunUpdateFilenameCandidate {
            val stem = filename.replace(workbookExtension, "")
            val partsByNormalizedValue = linkedMapOf<String, MymunUpdateFilenamePart>()
            stem.split('-').forEach { rawBlock ->
                val displayValue = whitespace.replace(rawBlock.replace("_", "").trim(), " ")
                val normalizedValue = displayValue.lowercase(Locale.ROOT)
                if (
                    displayValue.isNotEmpty() &&
                    !numericBlock.matches(displayValue) &&
                    normalizedValue !in ignoredBlocks
                ) {
                    partsByNormalizedValue.putIfAbsent(
                        normalizedValue,
                        MymunUpdateFilenamePart(displayValue, normalizedValue),
                    )
                }
            }
            return MymunUpdateFilenameCandidate(partsByNormalizedValue.values.toList())
        }
    }
}
