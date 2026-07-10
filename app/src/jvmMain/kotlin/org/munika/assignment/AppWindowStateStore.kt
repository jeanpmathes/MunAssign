package org.munika.assignment

import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

private const val NormalWidthKey = "normalWidth"
private const val NormalHeightKey = "normalHeight"
private const val NormalXKey = "normalX"
private const val NormalYKey = "normalY"
private const val MaximizedKey = "maximized"

private val DefaultNormalSize = Dimension(1600, 1000)
private val MinimumNormalSize = Dimension(1100, 720)

/**
 * Machine-local desktop window state saved outside the conference database.
 */
data class AppWindowState(
    val normalSize: Dimension = Dimension(DefaultNormalSize),
    val normalLocation: Point? = null,
    val maximized: Boolean = false,
) {
    val normalBounds: Rectangle?
        get() = normalLocation?.let { Rectangle(it, normalSize) }
}

/**
 * Persists and validates MunAssign's main desktop window size, position, and maximized state.
 */
class AppWindowStateStore(
    private val path: Path,
    private val visibleAreas: () -> List<Rectangle> = ::currentScreenBounds,
) {
    fun load(): AppWindowState {
        val properties = Properties()
        runCatching {
            Files.newInputStream(path).use(properties::load)
        }.getOrElse {
            return AppWindowState()
        }

        val size = readSize(properties)
        val location = readLocation(properties)
            ?.takeIf { Rectangle(it, size).isVisibleInAny(visibleAreas()) }
        return AppWindowState(
            normalSize = size,
            normalLocation = location,
            maximized = properties.getProperty(MaximizedKey).toBooleanStrictOrNull() ?: false,
        )
    }

    fun save(state: AppWindowState) {
        val properties = Properties().apply {
            setProperty(NormalWidthKey, state.normalSize.width.toString())
            setProperty(NormalHeightKey, state.normalSize.height.toString())
            state.normalLocation?.let { location ->
                setProperty(NormalXKey, location.x.toString())
                setProperty(NormalYKey, location.y.toString())
            }
            setProperty(MaximizedKey, state.maximized.toString())
        }
        path.parent?.let(Files::createDirectories)
        Files.newOutputStream(path).use { output ->
            properties.store(output, "MunAssign window state")
        }
    }

    private fun readSize(properties: Properties): Dimension {
        val width = properties.getProperty(NormalWidthKey)?.toIntOrNull()
        val height = properties.getProperty(NormalHeightKey)?.toIntOrNull()
        return if (width != null && height != null && width >= MinimumNormalSize.width && height >= MinimumNormalSize.height) {
            Dimension(width, height)
        } else {
            Dimension(DefaultNormalSize)
        }
    }

    private fun readLocation(properties: Properties): Point? {
        val x = properties.getProperty(NormalXKey)?.toIntOrNull() ?: return null
        val y = properties.getProperty(NormalYKey)?.toIntOrNull() ?: return null
        return Point(x, y)
    }
}

internal fun AppWindowState.withNormalBounds(bounds: Rectangle): AppWindowState =
    copy(
        normalSize = Dimension(bounds.width, bounds.height),
        normalLocation = Point(bounds.x, bounds.y),
    )

internal fun Rectangle.isValidNormalWindowBounds(): Boolean =
    width >= MinimumNormalSize.width && height >= MinimumNormalSize.height

private fun Rectangle.isVisibleInAny(visibleAreas: List<Rectangle>): Boolean =
    visibleAreas.any { area -> intersects(area) }

private fun currentScreenBounds(): List<Rectangle> =
    if (GraphicsEnvironment.isHeadless()) {
        emptyList()
    } else {
        GraphicsEnvironment.getLocalGraphicsEnvironment()
            .screenDevices
            .map { device -> device.defaultConfiguration.bounds }
    }
