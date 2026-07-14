package org.munika.assignment.export

import java.awt.Color
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.munika.assignment.domain.model.CommitteeMode

class ConferenceStatusImageRendererTest {
    @Test
    fun `renderer creates an opaque square status card with twelve committees`() {
        val report = ConferenceStatusReport(
            conferenceName = "KAMUN: The Black Forest Summit 2026",
            generatedAt = Instant.parse("2026-07-13T12:34:00Z"),
            lastMymunUpdateAt = Instant.parse("2026-07-12T18:20:00Z"),
            totalSlots = 64,
            occupiedSlots = 39,
            capacities = listOf(
                CapacityStatus(CapacityBucket.HighSchool, 24, 14, 2, 1, 3, 4, 0),
                CapacityStatus(CapacityBucket.University, 40, 25, 8, 4, 5, 0, 2),
            ),
            committees = List(12) { index ->
                CommitteeStatus(
                    name = "Committee ${index + 1} with a readable long name",
                    mode = when (index % 3) {
                        0 -> CommitteeMode.HighSchool
                        1 -> CommitteeMode.University
                        else -> CommitteeMode.Mapped
                    },
                    totalSlots = 8 + index,
                    occupiedSlots = 3 + index,
                )
            },
            incompleteMappedSlots = 2,
        )

        val image = ConferenceStatusImageRenderer.render(report)

        assertEquals(1080, image.width)
        assertEquals(1080, image.height)
        assertFalse(image.colorModel.hasAlpha())
        assertEquals(Color(0xF4, 0xF6, 0xFA).rgb, image.getRGB(0, 0))
    }

    @Test
    fun `renderer handles empty capacity buckets`() {
        val report = ConferenceStatusReport(
            conferenceName = "Empty conference",
            generatedAt = Instant.EPOCH,
            lastMymunUpdateAt = null,
            totalSlots = 0,
            occupiedSlots = 0,
            capacities = CapacityBucket.entries.map { bucket ->
                CapacityStatus(bucket, 0, 0, 0, 0, 0, 0, 0)
            },
            committees = emptyList(),
            incompleteMappedSlots = 0,
        )

        val image = ConferenceStatusImageRenderer.render(report)

        assertEquals(1080, image.width)
        assertEquals(1080, image.height)
    }
}
