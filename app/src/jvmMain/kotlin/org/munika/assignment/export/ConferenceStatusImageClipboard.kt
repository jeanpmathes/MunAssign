package org.munika.assignment.export

import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.time.Instant
import org.munika.assignment.domain.model.Conference

/** Renders the current aggregate snapshot and publishes it as an image on the desktop clipboard. */
internal class ConferenceStatusImageClipboard(
    private val clipboardProvider: () -> Clipboard = { Toolkit.getDefaultToolkit().systemClipboard },
    private val now: () -> Instant = { Instant.now() },
) {
    /**
     * Copies one privacy-safe conference status card.
     *
     * Clipboard ownership and rendering failures propagate so the UI can report them without
     * claiming that an image was copied.
     */
    fun copy(conference: Conference): Unit {
        val report = ConferenceStatusReport.from(conference, generatedAt = now())
        val image = ConferenceStatusImageRenderer.render(report)
        clipboardProvider().setContents(ImageTransferable(image), null)
    }
}

private class ImageTransferable(private val image: Image) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.imageFlavor

    override fun getTransferData(flavor: DataFlavor): Any {
        if (!isDataFlavorSupported(flavor)) throw UnsupportedFlavorException(flavor)
        return image
    }
}
