package org.munika.assignment

import java.awt.image.BufferedImage
import javax.imageio.ImageIO

private const val AppIconResourcePath = "/icons/munassign.png"

internal fun loadAppIcon(resourcePath: String = AppIconResourcePath): BufferedImage? =
    runCatching {
        val resource = AppIconResourceAnchor::class.java.getResourceAsStream(resourcePath)
            ?: return@runCatching null
        resource.use(ImageIO::read)
    }.getOrNull()

private object AppIconResourceAnchor
