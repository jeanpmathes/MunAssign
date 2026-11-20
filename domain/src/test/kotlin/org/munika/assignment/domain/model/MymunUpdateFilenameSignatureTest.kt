package org.munika.assignment.domain.model

import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MymunUpdateFilenameSignatureTest {
    @Test
    fun `export date is parsed strictly from MyMUN prefix without changing identity`() {
        val candidate = MymunUpdateFilenameCandidate.fromFilename("MYMUN-2024-02-29-0853-KAMUN.xlsx")

        assertEquals(LocalDate.of(2024, 2, 29), candidate.exportDate)
        assertEquals(setOf("kamun"), candidate.normalizedParts)
        assertNull(MymunUpdateFilenameCandidate.fromFilename("mymun-2026-02-29-0853-KAMUN.xlsx").exportDate)
        assertNull(MymunUpdateFilenameCandidate.fromFilename("mymun-2026-13-01-0853-KAMUN.xlsx").exportDate)
        assertNull(MymunUpdateFilenameCandidate.fromFilename("KAMUN-2026-11-18.xlsx").exportDate)
        assertNull(MymunUpdateFilenameCandidate.fromFilename("KAMUN.xlsx").exportDate)
    }

    @Test
    fun `example filename produces one conference signature part`() {
        val candidate = MymunUpdateFilenameCandidate.fromFilename(
            "mymun-2026-07-10-0853-KAMUN_ The Black Forest Summit 2026-Applications-Export.xlsx",
        )

        assertEquals(listOf("KAMUN The Black Forest Summit 2026"), candidate.parts.map { it.displayValue })
    }

    @Test
    fun `normalization removes underscores and ignores boilerplate numeric blocks and duplicate parts`() {
        val candidate = MymunUpdateFilenameCandidate.fromFilename(
            "MYMUN-2026-North__Star- northstar -Applications-EXPORT.xlsm",
        )

        assertEquals(listOf("NorthStar"), candidate.parts.map { it.displayValue })
        assertEquals(setOf("northstar"), candidate.normalizedParts)
    }

    @Test
    fun `matching uses exact unordered normalized part sets`() {
        val trusted = signature(
            id = "trusted",
            filename = "mymun-CAMUN-The Summit-Applications-Export.xlsx",
            trustedAt = Instant.parse("2026-01-01T00:00:00Z"),
        )

        assertIs<MymunUpdateFilenameAssessment.Match>(
            MymunUpdateFilenameCandidate.fromFilename("the summit-camun.xlsx").assess(
                trustedSignatures = listOf(trusted),
            ),
        )
        assertIs<MymunUpdateFilenameAssessment.Mismatch>(
            MymunUpdateFilenameCandidate.fromFilename("the summit-camun-extra.xlsx").assess(
                trustedSignatures = listOf(trusted),
            ),
        )
    }

    @Test
    fun `mismatch compares with closest signature and prefers newest on ties`() {
        val older = signature(
            id = "older",
            filename = "CAMUN-Black Forest.xlsx",
            trustedAt = Instant.parse("2026-01-01T00:00:00Z"),
        )
        val newer = signature(
            id = "newer",
            filename = "CAMUN-Spring Summit.xlsx",
            trustedAt = Instant.parse("2026-02-01T00:00:00Z"),
        )

        val assessment = assertIs<MymunUpdateFilenameAssessment.Mismatch>(
            MymunUpdateFilenameCandidate.fromFilename("CAMUN-Autumn Summit.xlsx").assess(
                trustedSignatures = listOf(older, newer),
            ),
        )

        assertEquals("newer", assessment.closestSignature.id.value)
        assertEquals(listOf("Spring Summit"), assessment.missingParts)
        assertEquals(listOf("Autumn Summit"), assessment.unexpectedParts)
    }

    @Test
    fun `empty candidates always require confirmation`() {
        val candidate = MymunUpdateFilenameCandidate.fromFilename(
            "mymun-2026-07-10-0853-Applications-Export.xlsx",
        )

        assertTrue(candidate.parts.isEmpty())
        assertIs<MymunUpdateFilenameAssessment.Empty>(
            candidate.assess(trustedSignatures = emptyList()),
        )
    }

    private fun signature(
        id: String,
        filename: String,
        trustedAt: Instant,
    ): MymunUpdateFilenameSignature =
        MymunUpdateFilenameCandidate.fromFilename(filename).withIdentity(
            id = MymunUpdateFilenameSignatureId(id),
            trustedAt = trustedAt,
        )
}
