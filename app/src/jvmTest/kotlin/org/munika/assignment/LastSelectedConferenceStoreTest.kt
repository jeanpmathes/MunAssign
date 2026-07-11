package org.munika.assignment

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LastSelectedConferenceStoreTest {
    @Test
    fun `missing file returns no selected conference`() {
        val store = LastSelectedConferenceStore(
            path = Files.createTempDirectory("munassign-last-selected").resolve("missing.properties"),
        )

        assertNull(store.load())
    }

    @Test
    fun `conference id round trips`() {
        val path = Files.createTempDirectory("munassign-last-selected").resolve("last-selected.properties")
        val store = LastSelectedConferenceStore(path)

        store.save("conference-b")

        assertEquals("conference-b", store.load())
    }

    @Test
    fun `blank and malformed files return no selected conference`() {
        val directory = Files.createTempDirectory("munassign-last-selected")
        val blankPath = directory.resolve("blank.properties")
        val malformedPath = directory.resolve("malformed.properties")
        Files.writeString(blankPath, "conferenceId=   ")
        Files.writeString(malformedPath, "selected: conference-a")

        assertNull(LastSelectedConferenceStore(blankPath).load())
        assertNull(LastSelectedConferenceStore(malformedPath).load())
    }

    @Test
    fun `null selection clears saved conference`() {
        val path = Files.createTempDirectory("munassign-last-selected").resolve("last-selected.properties")
        val store = LastSelectedConferenceStore(path)
        store.save("conference-a")

        store.save(null)

        assertNull(store.load())
    }
}
