package org.munika.assignment

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppIconTest {
    @Test
    fun `bundled app icon has expected dimensions and transparency`() {
        val icon = assertNotNull(loadAppIcon())

        assertEquals(256, icon.width)
        assertEquals(256, icon.height)
        assertTrue(icon.colorModel.hasAlpha())
    }

    @Test
    fun `missing app icon falls back without failing startup`() {
        assertNull(loadAppIcon("/icons/missing.png"))
    }
}
