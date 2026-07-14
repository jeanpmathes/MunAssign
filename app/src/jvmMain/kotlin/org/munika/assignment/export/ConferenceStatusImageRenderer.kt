package org.munika.assignment.export

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt
import org.munika.assignment.domain.model.CommitteeMode

/** Renders a fixed-size, phone-friendly square conference status card. */
internal object ConferenceStatusImageRenderer {
    const val ImageSize: Int = 1080

    fun render(report: ConferenceStatusReport): BufferedImage {
        val image = BufferedImage(ImageSize, ImageSize, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.color = Background
            graphics.fillRect(0, 0, ImageSize, ImageSize)
            graphics.drawHeader(report)
            graphics.drawCapacitySection(report)
            graphics.drawCommitteeSection(report)
            graphics.drawFooter(report)
        } finally {
            graphics.dispose()
        }
        return image
    }

    private fun Graphics2D.drawHeader(report: ConferenceStatusReport) {
        drawFittedText(report.conferenceName, 48, 82, 984, font(42, Font.BOLD), Ink)
    }

    private fun Graphics2D.drawCapacitySection(report: ConferenceStatusReport) {
        val capacities = CapacityBucket.entries.map { bucket ->
            report.capacities.firstOrNull { it.bucket == bucket } ?: CapacityStatus(bucket, 0, 0, 0, 0, 0, 0, 0)
        }
        drawCapacityCard(capacities[0], 48, 120, 480, 245)
        drawCapacityCard(capacities[1], 552, 120, 480, 245)
    }

    private fun Graphics2D.drawCapacityCard(status: CapacityStatus, x: Int, y: Int, width: Int, height: Int) {
        drawCard(x, y, width, height)
        val label = when (status.bucket) {
            CapacityBucket.HighSchool -> "HIGH SCHOOL"
            CapacityBucket.University -> "UNIVERSITY"
        }
        val bucketColor = when (status.bucket) {
            CapacityBucket.HighSchool -> HighSchool
            CapacityBucket.University -> BrandBlue
        }
        drawText(label, x + 24, y + 37, font(14, Font.BOLD), bucketColor)
        drawText("${status.committedSlots} / ${status.totalSlots} committed", x + 24, y + 82, font(27, Font.BOLD), Ink)
        if (status.overflow > 0) {
            drawPill(
                text = "+${status.overflow} over",
                right = x + width - 24,
                baseline = y + 77,
                background = ErrorBackground,
                foreground = ErrorInk,
            )
        }

        drawCapacityBar(status, x + 24, y + 106, width - 48, 24)
        val legends = listOf(
            LegendItem("Assigned", status.assigned, Assigned),
            LegendItem("Confirmed", status.confirmed, Confirmed),
            LegendItem("Accepted", status.accepted, Accepted),
            LegendItem("Reserved", status.reserved, Reserved),
        )
        legends.forEachIndexed { index, item ->
            val column = index % 2
            drawLegendItem(item, x + 24 + column * 218, y + 172 + (index / 2) * 42)
        }
    }

    private fun Graphics2D.drawCapacityBar(status: CapacityStatus, x: Int, y: Int, width: Int, height: Int) {
        val barShape = RoundRectangle2D.Float(x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat(), 12f, 12f)
        color = Track
        fill(barShape)
        if (status.totalSlots > 0) {
            val segments = listOf(
                status.assigned to Assigned,
                status.confirmed to Confirmed,
                status.accepted to Accepted,
                status.reserved to Reserved,
                status.freeSlots to Free,
            )
            val previousClip: Shape? = clip
            clip = barShape
            var cursor = x
            segments.forEach { (count, segmentColor) ->
                val segmentWidth = (width * count.toDouble() / status.totalSlots).roundToInt()
                    .coerceAtLeast(0)
                    .coerceAtMost(x + width - cursor)
                if (segmentWidth > 0) {
                    color = segmentColor
                    fillRect(cursor, y, segmentWidth, height)
                    cursor += segmentWidth
                }
            }
            clip = previousClip
        }
        color = Border
        stroke = BasicStroke(1f)
        draw(barShape)
    }

    private fun Graphics2D.drawCommitteeSection(report: ConferenceStatusReport) {
        drawText("Committee allocations", 48, 422, font(20, Font.BOLD), Ink)
        if (report.committees.isEmpty()) {
            drawCard(48, 448, 984, 100)
            drawText("No committees available", 540, 507, font(18, Font.PLAIN), MutedInk, alignCenter = true)
            return
        }

        val committees = report.committees.take(MaxCommittees)
        val rowCount = ceil(committees.size / 2.0).toInt().coerceAtLeast(1)
        val gridTop = 448
        val gridBottom = 1004
        val rowGap = 12
        val cardHeight = ((gridBottom - gridTop - rowGap * (rowCount - 1)) / rowCount).coerceAtMost(82)
        committees.forEachIndexed { index, committee ->
            val column = index % 2
            val row = index / 2
            drawCommitteeCard(
                committee = committee,
                x = 48 + column * 504,
                y = gridTop + row * (cardHeight + rowGap),
                width = 480,
                height = cardHeight,
            )
        }
        if (report.committees.size > MaxCommittees) {
            drawPill(
                text = "+${report.committees.size - MaxCommittees} committees not shown",
                right = 1032,
                baseline = 422,
                background = WarningBackground,
                foreground = WarningInk,
            )
        }
    }

    private fun Graphics2D.drawCommitteeCard(
        committee: CommitteeStatus,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        drawCard(x, y, width, height, radius = 14)
        val nameFont = font(17, Font.BOLD)
        font = nameFont
        val fittedName = committee.name.ellipsize(fontMetrics, width - 132)
        val titleColor = when (committee.mode) {
            CommitteeMode.HighSchool -> HighSchool
            CommitteeMode.University -> UniversityCommittee
            CommitteeMode.Mapped -> MappedCommittee
        }
        drawText(fittedName, x + 18, y + 34, nameFont, titleColor)
        drawText(
            "${committee.occupiedSlots} / ${committee.totalSlots}",
            x + width - 18,
            y + 34,
            font(17, Font.BOLD),
            Ink,
            alignRight = true,
        )
        drawProgressBar(
            x = x + 18,
            y = y + height - 18,
            width = width - 36,
            height = 9,
            fraction = committee.occupiedSlots.fractionOf(committee.totalSlots),
            fill = Assigned,
        )
    }

    private fun Graphics2D.drawFooter(report: ConferenceStatusReport) {
        drawText(DateFormatter.format(report.generatedAt), 1032, 1044, font(14, Font.PLAIN), MutedInk, alignRight = true)
    }

    private fun Graphics2D.drawLegendItem(item: LegendItem, x: Int, baseline: Int) {
        color = item.color
        fillRoundRect(x, baseline - 12, 10, 10, 5, 5)
        drawFittedText("${item.label} ${item.count}", x + 17, baseline - 2, 123, font(13, Font.PLAIN), MutedInk)
    }

    private fun Graphics2D.drawCard(x: Int, y: Int, width: Int, height: Int, radius: Int = 22) {
        color = Shadow
        fillRoundRect(x, y + 3, width, height, radius, radius)
        color = Surface
        fillRoundRect(x, y, width, height, radius, radius)
        color = Border
        stroke = BasicStroke(1f)
        drawRoundRect(x, y, width, height, radius, radius)
    }

    private fun Graphics2D.drawProgressBar(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        fraction: Double,
        fill: Color,
    ) {
        val trackShape = RoundRectangle2D.Float(x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat(), height.toFloat(), height.toFloat())
        color = Track
        fill(trackShape)
        val fillWidth = (width * fraction.coerceIn(0.0, 1.0)).roundToInt()
        if (fillWidth > 0) {
            val previousClip = clip
            clip = trackShape
            color = fill
            fillRect(x, y, fillWidth, height)
            clip = previousClip
        }
    }

    private fun Graphics2D.drawPill(
        text: String,
        right: Int,
        baseline: Int,
        background: Color,
        foreground: Color,
    ) {
        font = font(13, Font.BOLD)
        val width = fontMetrics.stringWidth(text) + 24
        color = background
        fillRoundRect(right - width, baseline - 20, width, 28, 14, 14)
        drawText(text, right - 12, baseline, font, foreground, alignRight = true)
    }

    private fun Graphics2D.drawFittedText(
        text: String,
        x: Int,
        baseline: Int,
        maxWidth: Int,
        textFont: Font,
        textColor: Color,
    ) {
        font = textFont
        val fitted = text.ellipsize(fontMetrics, maxWidth)
        drawText(fitted, x, baseline, textFont, textColor)
    }

    private fun Graphics2D.drawText(
        text: String,
        x: Int,
        baseline: Int,
        textFont: Font,
        textColor: Color,
        alignRight: Boolean = false,
        alignCenter: Boolean = false,
    ) {
        font = textFont
        color = textColor
        val width = fontMetrics.stringWidth(text)
        val drawX = when {
            alignRight -> x - width
            alignCenter -> x - width / 2
            else -> x
        }
        drawString(text, drawX, baseline)
    }

    private fun String.ellipsize(metrics: java.awt.FontMetrics, maxWidth: Int): String {
        if (metrics.stringWidth(this) <= maxWidth) return this
        val suffix = "..."
        var end = length
        while (end > 0 && metrics.stringWidth(substring(0, end) + suffix) > maxWidth) end -= 1
        return substring(0, end).trimEnd() + suffix
    }

    private fun Int.fractionOf(total: Int): Double = if (total <= 0) 0.0 else toDouble() / total

    private fun font(size: Int, style: Int): Font = Font(Font.SANS_SERIF, style, size)

    private data class LegendItem(val label: String, val count: Int, val color: Color)

    private const val MaxCommittees = 12
    private val DateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
        .withZone(ZoneId.systemDefault())

    private val Background = Color(0xF4, 0xF6, 0xFA)
    private val Surface = Color.WHITE
    private val Ink = Color(0x18, 0x24, 0x32)
    private val MutedInk = Color(0x5D, 0x6B, 0x7C)
    private val Border = Color(0xD9, 0xE0, 0xE8)
    private val Shadow = Color(0xE8, 0xEC, 0xF2)
    private val Track = Color(0xE7, 0xEC, 0xF2)
    private val BrandBlue = Color(0x31, 0x5E, 0xBA)
    private val HighSchool = Color(0x8E, 0x24, 0xAA)
    private val UniversityCommittee = Color(0x31, 0x5E, 0xBA)
    private val MappedCommittee = Color(0x0F, 0x76, 0x6E)
    private val Assigned = Color(0x16, 0x86, 0x6D)
    private val Confirmed = Color(0x31, 0x5E, 0xBA)
    private val Accepted = Color(0xB8, 0x58, 0x7A)
    private val Reserved = Color(0x53, 0x60, 0x6F)
    private val Free = Color(0xDD, 0xE3, 0xEA)
    private val WarningBackground = Color(0xFF, 0xF0, 0xD0)
    private val WarningInk = Color(0x8A, 0x4A, 0x00)
    private val ErrorBackground = Color(0xFD, 0xE2, 0xE1)
    private val ErrorInk = Color(0xB4, 0x23, 0x18)
}
