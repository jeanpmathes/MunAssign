package org.munika.assignment.export

import java.awt.Image
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.DataFlavor
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.munika.assignment.domain.model.Conference
import org.munika.assignment.domain.model.ConferenceId

class ConferenceStatusImageClipboardTest {
    @Test
    fun `copy places a square image on the clipboard`() {
        val clipboard = Clipboard("status-image-test")
        val conference = Conference(
            id = ConferenceId("conference"),
            name = "KAMUN 2026",
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
        val service = ConferenceStatusImageClipboard(
            clipboardProvider = { clipboard },
            now = { Instant.parse("2026-07-13T12:34:00Z") },
        )

        service.copy(conference)

        val contents = clipboard.getContents(null)
        assertTrue(contents.isDataFlavorSupported(DataFlavor.imageFlavor))
        val image = contents.getTransferData(DataFlavor.imageFlavor) as Image
        assertEquals(1080, image.getWidth(null))
        assertEquals(1080, image.getHeight(null))
    }
}
