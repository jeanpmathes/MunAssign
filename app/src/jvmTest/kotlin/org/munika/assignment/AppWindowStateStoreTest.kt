package org.munika.assignment

import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppWindowStateStoreTest {
    @Test
    fun `missing file returns default window state`() {
        val store = AppWindowStateStore(
            path = Files.createTempDirectory("munassign-window-state").resolve("missing.properties"),
            visibleAreas = { listOf(Rectangle(0, 0, 1920, 1080)) },
        )

        val state = store.load()

        assertEquals(Dimension(1600, 1000), state.normalSize)
        assertNull(state.normalLocation)
        assertFalse(state.maximized)
    }

    @Test
    fun `valid size position and maximized state round trip`() {
        val path = Files.createTempDirectory("munassign-window-state").resolve("window.properties")
        val store = AppWindowStateStore(
            path = path,
            visibleAreas = { listOf(Rectangle(0, 0, 1920, 1080)) },
        )

        store.save(
            AppWindowState(
                normalSize = Dimension(1400, 900),
                normalLocation = Point(120, 80),
                maximized = true,
            )
        )

        val state = store.load()
        assertEquals(Dimension(1400, 900), state.normalSize)
        assertEquals(Point(120, 80), state.normalLocation)
        assertTrue(state.maximized)
    }

    @Test
    fun `corrupt values fall back to default window state`() {
        val path = Files.createTempDirectory("munassign-window-state").resolve("window.properties")
        Files.writeString(
            path,
            """
            normalWidth=wide
            normalHeight=tall
            normalX=left
            normalY=top
            maximized=perhaps
            """.trimIndent(),
        )
        val store = AppWindowStateStore(
            path = path,
            visibleAreas = { listOf(Rectangle(0, 0, 1920, 1080)) },
        )

        val state = store.load()

        assertEquals(Dimension(1600, 1000), state.normalSize)
        assertNull(state.normalLocation)
        assertFalse(state.maximized)
    }

    @Test
    fun `tiny size falls back to default size`() {
        val path = Files.createTempDirectory("munassign-window-state").resolve("window.properties")
        Files.writeString(
            path,
            """
            normalWidth=800
            normalHeight=600
            normalX=100
            normalY=100
            maximized=false
            """.trimIndent(),
        )
        val store = AppWindowStateStore(
            path = path,
            visibleAreas = { listOf(Rectangle(0, 0, 1920, 1080)) },
        )

        val state = store.load()

        assertEquals(Dimension(1600, 1000), state.normalSize)
        assertEquals(Point(100, 100), state.normalLocation)
    }

    @Test
    fun `off-screen position falls back to centered placement`() {
        val path = Files.createTempDirectory("munassign-window-state").resolve("window.properties")
        Files.writeString(
            path,
            """
            normalWidth=1400
            normalHeight=900
            normalX=4000
            normalY=4000
            maximized=true
            """.trimIndent(),
        )
        val store = AppWindowStateStore(
            path = path,
            visibleAreas = { listOf(Rectangle(0, 0, 1920, 1080)) },
        )

        val state = store.load()

        assertEquals(Dimension(1400, 900), state.normalSize)
        assertNull(state.normalLocation)
        assertTrue(state.maximized)
    }
}
