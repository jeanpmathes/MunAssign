package org.munika.assignment.ui.screen.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

private val FlagEmojiPattern = Regex("\uD83C[\uDDE6-\uDDFF]\uD83C[\uDDE6-\uDDFF]")
private const val FlagAssetRoot = "/flags/twemoji-72x72"

@Composable
fun FlagAwareText(
    text: String,
    flagAssetName: String?,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val directPainter = remember(flagAssetName) { flagAssetName?.loadFlagPainterByAssetName() }
    if (flagAssetName != null && directPainter != null) {
        FlagImageText(
            painter = directPainter,
            text = text,
            modifier = modifier,
            color = color,
            style = style,
            fontWeight = fontWeight,
            maxLines = maxLines,
            overflow = overflow,
        )
        return
    }

    val match = FlagEmojiPattern.find(text)
    val painter = remember(match?.value) { match?.value?.loadFlagPainter() }
    if (match == null) {
        Text(
            text = text,
            modifier = modifier,
            color = color,
            style = style,
            fontWeight = fontWeight,
            maxLines = maxLines,
            overflow = overflow,
        )
        return
    }
    if (painter == null) {
        Text(
            text = text.removeRange(match.range).replace(Regex("\\s+"), " ").trim(),
            modifier = modifier,
            color = color,
            style = style,
            fontWeight = fontWeight,
            maxLines = maxLines,
            overflow = overflow,
        )
        return
    }

    val before = text.substring(0, match.range.first)
    val after = text.substring(match.range.last + 1)
    FlagImageText(
        painter = painter,
        text = "$before$after".replace(Regex("\\s+"), " ").trim(),
        modifier = modifier,
        color = color,
        style = style,
        fontWeight = fontWeight,
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
private fun FlagImageText(
    painter: BitmapPainter,
    text: String,
    modifier: Modifier,
    color: Color,
    style: TextStyle,
    fontWeight: FontWeight?,
    maxLines: Int,
    overflow: TextOverflow,
) {
    val resolvedColor = if (color == Color.Unspecified) LocalContentColor.current else color
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = text,
            color = resolvedColor,
            style = style,
            fontWeight = fontWeight,
            maxLines = maxLines,
            overflow = overflow,
        )
        Image(
            painter = painter,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun String.loadFlagPainter(): BitmapPainter? {
    return twemojiAssetName().loadFlagPainterByAssetName()
}

private fun String.loadFlagPainterByAssetName(): BitmapPainter? {
    val path = "$FlagAssetRoot/$this.png"
    val stream = FlagAwareTextResource::class.java.getResourceAsStream(path) ?: return null
    val image: BufferedImage = stream.use { ImageIO.read(it) } ?: return null
    return BitmapPainter(image.toComposeImageBitmap())
}

private fun String.twemojiAssetName(): String {
    val codepoints = mutableListOf<String>()
    var index = 0
    while (index < length) {
        val codepoint = Character.codePointAt(this, index)
        codepoints += codepoint.toString(16)
        index += Character.charCount(codepoint)
    }
    return codepoints.joinToString("-")
}

private object FlagAwareTextResource
